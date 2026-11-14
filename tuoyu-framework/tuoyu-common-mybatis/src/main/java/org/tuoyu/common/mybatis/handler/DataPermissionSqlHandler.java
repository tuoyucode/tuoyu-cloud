package org.tuoyu.common.mybatis.handler;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.core.constant.HttpStatus;
import org.tuoyu.common.core.context.AccessRequirement;
import org.tuoyu.common.core.context.CurrentRole;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.PermissionContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.SpringUtils;
import org.tuoyu.common.core.utils.StringUtils;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.enums.DataScopeType;
import org.tuoyu.common.mybatis.helper.DataPermissionHelper;
import org.tuoyu.common.mybatis.service.SysDataScopeService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * 数据权限条件：按当前方法的 {@link DataPermission} 注解、当前用户的角色与接口的访问要求，生成加到 WHERE 中的条件。
 * <p>
 * 计算过程：
 * <ol>
 *     <li>没有注解时拒绝执行（拦截器只在有注解时调用本类，没有说明生命周期出了问题）；没有当前用户时拒绝执行（401）；
 *     超级管理员不加条件；</li>
 *     <li>参与计算的角色：接口没有访问要求时是用户的全部角色；有访问要求时只取为它授权的角色（按权限码的授权表、
 *     按角色标识的完整匹配），按角色 ID 去重。没有参与计算的角色时条件为 {@code 1 = 0}，不返回任何数据；</li>
 *     <li>每个角色按数据范围生成一个条件：任一角色为“全部数据”时整条不加条件；需要的列在注解中没有时为 {@code 1 = 0}；
 *     部门范围取自名为 {@code sdss} 的 {@link SysDataScopeService}（取的时候忽略数据权限），必须是逗号分隔的整数；</li>
 *     <li>相同的条件只保留一个。查询取各条件的并集（OR），修改与删除要求同时满足（AND，含 OR 的条件先加括号）；</li>
 *     <li>条件整体加括号后与原条件用 AND 连接；原条件的顶层是 OR / XOR 时先给原条件加括号，
 *     否则 AND 优先，满足 OR 前半部分的行会绕过数据权限。</li>
 * </ol>
 * 条件只用 {@code =}、{@code IN}、{@code OR}、{@code AND} 与整数字面量，MySQL 与 PostgreSQL 写法相同。
 * 注解中的列名原样拼进条件（开发者写在代码中的常量），用户与部门 ID 是数字，部门范围拼接前逐项校验。
 * <p>
 * 本类没有可变状态，可以被多个线程同时使用；构造时只保存参数，使用时才从容器取 Bean。
 *
 * @author tuoyu
 */
public class DataPermissionSqlHandler {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionSqlHandler.class);

    /**
     * 部门列的占位名
     */
    private static final String DEPT_KEY = "deptName";

    /**
     * 创建人列的占位名
     */
    private static final String USER_KEY = "userName";

    /**
     * 不返回任何数据的条件
     */
    private static final String DENY = "1 = 0";

    /**
     * 用户或部门 ID 为空时代入的值（不对应任何记录）
     */
    private static final long MISSING_ID = -1L;

    private static final String SCOPE_SERVICE_BEAN = "sdss";

    private final ObjectProvider<UserContextProvider> userContextProvider;

    private final ObjectProvider<PermissionContext> permissionContextProvider;

    /**
     * @param userContextProvider       当前用户的来源
     * @param permissionContextProvider 当前接口的访问要求的来源
     */
    public DataPermissionSqlHandler(ObjectProvider<UserContextProvider> userContextProvider,
                                    ObjectProvider<PermissionContext> permissionContextProvider) {
        this.userContextProvider = userContextProvider;
        this.permissionContextProvider = permissionContextProvider;
    }

    /**
     * 生成加上数据权限条件之后的 WHERE。
     *
     * @param where    语句中已有的 WHERE，可能为 null
     * @param isSelect true 表示查询，false 表示修改或删除
     * @return 新的 WHERE；不需要加条件时返回传入的 {@code where}（可能为 null）
     * @throws ServiceException 缺少注解、没有当前用户（401）、数据范围或注解配置异常、部门范围异常、条件无法解析时
     */
    public Expression getSqlSegment(Expression where, boolean isSelect) {
        DataPermission permission = DataPermissionHelper.getPermission();
        if (permission == null) {
            throw new ServiceException("数据权限上下文缺失，请检查 @DataPermission 注解处理生命周期");
        }
        CurrentUser user = requireUser();
        if (user.isSuperAdmin()) {
            return where;
        }
        String condition = buildCondition(permission, user, isSelect);
        if (condition == null) {
            return where;
        }
        Expression scoped = new ParenthesedExpressionList<>(List.of(parse(condition)));
        if (where == null) {
            return scoped;
        }
        Expression original = where instanceof OrExpression || where instanceof XorExpression
            ? new ParenthesedExpressionList<>(List.of(where))
            : where;
        return new AndExpression(original, scoped);
    }

    private CurrentUser requireUser() {
        UserContextProvider provider = userContextProvider.getIfAvailable();
        Optional<CurrentUser> user = provider == null ? Optional.empty() : provider.currentUser();
        if (user == null || user.isEmpty()) {
            throw new ServiceException("认证失败，无法访问系统资源", HttpStatus.UNAUTHORIZED);
        }
        return user.get();
    }

    /**
     * @return 条件文本；不需要加条件时为 null
     */
    private String buildCondition(DataPermission permission, CurrentUser user, boolean isSelect) {
        Map<String, String> columns = columns(permission);
        String deptColumn = columns.get(DEPT_KEY);
        String userColumn = columns.get(USER_KEY);
        List<CurrentRole> roles = participatingRoles(user, accessRequirement());
        if (roles.isEmpty()) {
            return DENY;
        }
        // 条件文本 -> 是否在顶层含 OR；按首次出现的顺序去重
        Map<String, Boolean> conditions = new LinkedHashMap<>();
        for (CurrentRole role : roles) {
            DataScopeType type = DataScopeType.findCode(role.getDataScope());
            if (type == null) {
                throw new ServiceException("角色数据范围异常 => " + role.getDataScope());
            }
            if (type == DataScopeType.ALL) {
                return null;
            }
            Condition condition = condition(type, role, user, deptColumn, userColumn);
            conditions.putIfAbsent(condition.text(), condition.disjunctive());
        }
        if (isSelect) {
            return String.join(" OR ", conditions.keySet());
        }
        if (conditions.size() == 1) {
            return conditions.keySet().iterator().next();
        }
        StringJoiner joined = new StringJoiner(" AND ");
        conditions.forEach((text, disjunctive) -> joined.add(disjunctive ? "(" + text + ")" : text));
        return joined.toString();
    }

    /**
     * @return 占位名到列名的对应关系，同一个占位名以最后一次为准
     */
    private static Map<String, String> columns(DataPermission permission) {
        Map<String, String> columns = new HashMap<>();
        for (DataColumn column : permission.value()) {
            String[] keys = column.key();
            String[] values = column.value();
            if (keys.length != values.length) {
                throw new ServiceException("角色数据范围异常 => key与value长度不匹配");
            }
            for (int i = 0; i < keys.length; i++) {
                columns.put(keys[i], values[i]);
            }
        }
        return columns;
    }

    private AccessRequirement accessRequirement() {
        AccessRequirement explicit = DataPermissionHelper.getAccess();
        if (explicit != null) {
            return explicit;
        }
        PermissionContext context = permissionContextProvider.getIfAvailable();
        AccessRequirement current = context == null ? null : context.currentRequirement();
        return current == null ? AccessRequirement.NONE : current;
    }

    /**
     * 参与计算的角色：没有访问要求时为全部角色；有访问要求时为授权给它的角色，按首次出现的顺序、按角色 ID 去重
     */
    private static List<CurrentRole> participatingRoles(CurrentUser user, AccessRequirement access) {
        List<CurrentRole> roles = new ArrayList<>();
        if (user.getRoles() != null) {
            user.getRoles().stream().filter(Objects::nonNull).forEach(roles::add);
        }
        if (!access.constrained() || roles.isEmpty()) {
            return roles;
        }
        Map<Long, CurrentRole> selected = new LinkedHashMap<>();
        Map<String, List<Long>> grants = user.getDataScopeRoleMap() == null ? Map.of() : user.getDataScopeRoleMap();
        for (String permission : access.permissions()) {
            List<Long> roleIds = grants.get(permission);
            if (roleIds == null) {
                continue;
            }
            for (Long roleId : roleIds) {
                if (roleId == null) {
                    continue;
                }
                for (CurrentRole role : roles) {
                    if (roleId.equals(role.getRoleId())) {
                        selected.putIfAbsent(role.getRoleId(), role);
                    }
                }
            }
        }
        if (!access.roleKeys().isEmpty()) {
            for (CurrentRole role : roles) {
                if (matchesRoleKey(role, access) && !selected.containsKey(role.getRoleId())) {
                    selected.put(role.getRoleId(), role);
                }
            }
        }
        return new ArrayList<>(selected.values());
    }

    private static boolean matchesRoleKey(CurrentRole role, AccessRequirement access) {
        if (role.getRoleKey() == null) {
            return false;
        }
        for (String key : StringUtils.splitList(role.getRoleKey())) {
            if (access.roleKeys().contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一个角色的条件；需要的列在注解中没有时为 {@link #DENY}
     */
    private static Condition condition(DataScopeType type, CurrentRole role, CurrentUser user,
                                       String deptColumn, String userColumn) {
        long userId = user.getUserId() == null ? MISSING_ID : user.getUserId();
        long deptId = user.getDeptId() == null ? MISSING_ID : user.getDeptId();
        return switch (type) {
            case CUSTOM -> deptColumn == null ? Condition.deny()
                : Condition.of(deptColumn + " IN (" + deptRange(() -> scopeService().getRoleCustom(role.getRoleId()),
                "角色 " + role.getRoleId() + " 的自定义部门") + ")");
            case DEPT -> deptColumn == null ? Condition.deny() : Condition.of(deptColumn + " = " + deptId);
            case DEPT_AND_CHILD -> deptColumn == null ? Condition.deny()
                : Condition.of(deptColumn + " IN (" + deptAndChild(deptId) + ")");
            case SELF -> userColumn == null ? Condition.deny() : Condition.of(userColumn + " = " + userId);
            case DEPT_AND_CHILD_OR_SELF -> {
                if (deptColumn == null && userColumn == null) {
                    yield Condition.deny();
                }
                if (deptColumn == null) {
                    yield Condition.of(userColumn + " = " + userId);
                }
                String deptPart = deptColumn + " IN (" + deptAndChild(deptId) + ")";
                yield userColumn == null ? Condition.of(deptPart)
                    : new Condition(deptPart + " OR " + userColumn + " = " + userId, true);
            }
            // 全部数据在调用前已经处理；其他情况不会出现，按不返回数据处理
            default -> Condition.deny();
        };
    }

    private static String deptAndChild(long deptId) {
        return deptRange(() -> scopeService().getDeptAndChild(deptId), "部门 " + deptId + " 及以下");
    }

    private static SysDataScopeService scopeService() {
        return SpringUtils.getBean(SCOPE_SERVICE_BEAN);
    }

    /**
     * 取部门范围并校验：必须是逗号分隔的整数（每项可以带负号、两侧可以有空白）。
     * 取的时候忽略数据权限：查询部门的 Mapper 本身也标注了数据权限，不忽略会递归改写。
     *
     * @param lookup 取部门范围
     * @param source 用于日志的来源说明
     * @return 规范化后的部门范围（逗号分隔，没有空白）
     */
    private static String deptRange(Supplier<String> lookup, String source) {
        String value = DataPermissionHelper.ignore(lookup);
        if (value == null || value.isBlank()) {
            log.warn("数据权限部门范围为空：{}", source);
            throw new ServiceException("数据权限部门范围异常");
        }
        StringJoiner normalized = new StringJoiner(",");
        for (String part : value.split(",", -1)) {
            String item = part.strip();
            if (!isInteger(item)) {
                log.warn("数据权限部门范围不是逗号分隔的整数：{} => {}", source, value);
                throw new ServiceException("数据权限部门范围异常");
            }
            normalized.add(item);
        }
        return normalized.toString();
    }

    private static boolean isInteger(String text) {
        int start = text.startsWith("-") ? 1 : 0;
        if (text.length() <= start) {
            return false;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static Expression parse(String condition) {
        try {
            // 不允许只解析前半部分：条件必须整体是一个表达式
            return CCJSqlParserUtil.parseExpression(condition, false);
        } catch (JSQLParserException | RuntimeException e) {
            log.error("数据权限条件解析失败：{}", condition, e);
            throw new ServiceException("数据权限解析异常");
        }
    }

    /**
     * @param text        条件文本
     * @param disjunctive 顶层是否含 OR（与其他条件用 AND 连接时要先加括号）
     */
    private record Condition(String text, boolean disjunctive) {

        static Condition of(String text) {
            return new Condition(text, false);
        }

        static Condition deny() {
            return new Condition(DENY, false);
        }

    }

}
