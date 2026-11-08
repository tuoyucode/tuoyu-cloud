package org.tuoyu.common.mybatis.handler;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.ObjectProvider;
import org.tuoyu.common.core.context.CurrentUser;
import org.tuoyu.common.core.context.TenantContext;
import org.tuoyu.common.core.context.UserContextProvider;
import org.tuoyu.common.core.exception.ServiceException;
import org.tuoyu.common.core.utils.ObjectUtils;
import org.tuoyu.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * MP注入处理器。当前用户来自 {@link UserContextProvider}；没有用户时（后台任务等）按系统用户 -1 填充
 *
 * @author Lion Li
 * @date 2021/4/25
 */
@Slf4j
public class InjectionMetaObjectHandler implements MetaObjectHandler {

    /**
     * 如果用户不存在默认注入-1代表无用户
     */
    private static final Long DEFAULT_USER_ID = -1L;

    private static final String TENANT_ID = "tenantId";

    private final ObjectProvider<UserContextProvider> userContext;

    private final ObjectProvider<TenantContext> tenantContext;

    public InjectionMetaObjectHandler(ObjectProvider<UserContextProvider> userContext, ObjectProvider<TenantContext> tenantContext) {
        this.userContext = userContext;
        this.tenantContext = tenantContext;
    }

    /**
     * 插入填充方法，用于在插入数据时自动填充实体对象中的创建时间、更新时间、创建人、更新人等信息
     *
     * @param metaObject 元对象，用于获取原始对象并进行填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            fillTenantOnInsert(metaObject);
            if (ObjectUtil.isNotNull(metaObject) && metaObject.getOriginalObject() instanceof BaseEntity baseEntity) {
                // 获取当前时间作为创建时间和更新时间，如果创建时间不为空，则使用创建时间，否则使用当前时间
                LocalDateTime current = ObjectUtils.notNull(baseEntity.getCreateTime(), LocalDateTime.now());
                baseEntity.setCreateTime(current);
                baseEntity.setUpdateTime(current);

                // 如果创建人为空，则填充当前登录用户的信息
                if (ObjectUtil.isNull(baseEntity.getCreateBy())) {
                    CurrentUser loginUser = getLoginUser();
                    if (ObjectUtil.isNotNull(loginUser)) {
                        Long userId = loginUser.getUserId();
                        // 填充创建人、更新人和创建部门信息
                        baseEntity.setCreateBy(userId);
                        baseEntity.setUpdateBy(userId);
                        baseEntity.setCreateDept(ObjectUtils.notNull(baseEntity.getCreateDept(), loginUser.getDeptId()));
                    } else {
                        // 填充创建人、更新人和创建部门信息
                        baseEntity.setCreateBy(DEFAULT_USER_ID);
                        baseEntity.setUpdateBy(DEFAULT_USER_ID);
                        baseEntity.setCreateDept(ObjectUtils.notNull(baseEntity.getCreateDept(), DEFAULT_USER_ID));
                    }
                }
            } else {
                LocalDateTime date = LocalDateTime.now();
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, date);
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, date);
                Date legacyDate = new Date();
                this.strictInsertFill(metaObject, "createTime", Date.class, legacyDate);
                this.strictInsertFill(metaObject, "updateTime", Date.class, legacyDate);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw fillFailure(e);
        }
    }

    /**
     * 更新填充方法，用于在更新数据时自动填充实体对象中的更新时间和更新人信息
     *
     * @param metaObject 元对象，用于获取原始对象并进行填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // 租户字段不在这里处理：TenantEntity.tenantId 的更新策略为 NEVER，按实体更新不会写入 tenant_id
        try {
            if (ObjectUtil.isNotNull(metaObject) && metaObject.getOriginalObject() instanceof BaseEntity baseEntity) {
                // 获取当前时间作为更新时间，无论原始对象中的更新时间是否为空都填充
                LocalDateTime current = LocalDateTime.now();
                baseEntity.setUpdateTime(current);

                // 获取当前登录用户的ID，并填充更新人信息
                CurrentUser loginUser = getLoginUser();
                Long userId = ObjectUtil.isNotNull(loginUser) ? loginUser.getUserId() : DEFAULT_USER_ID;
                baseEntity.setUpdateBy(userId);
            } else {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
            }
        } catch (ServiceException e) {
            throw e;
        } catch (RuntimeException e) {
            throw fillFailure(e);
        }
    }

    /**
     * 填充失败（读取当前用户、租户等出错）：业务异常（例如缺少租户上下文，400）在上面原样抛出，保留它的状态与提示；
     * 其他异常的消息可能带内部信息（例如 Redis 地址），不放进异常消息，只作为 cause 保留，
     * 由异常处理按未预期的异常返回 500 与错误编号，详情按编号记在日志中
     */
    private static IllegalStateException fillFailure(RuntimeException cause) {
        return new IllegalStateException("自动填充审计字段失败", cause);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前登录用户的信息，如果用户未登录则返回 null
     */
    private CurrentUser getLoginUser() {
        UserContextProvider context = userContext.getIfAvailable();
        return context == null ? null : context.currentUser().orElse(null);
    }

    /**
     * 开启多租户、有当前租户时，插入的数据一律属于当前租户：
     * 实体中填了其他租户也会被改写，防止借插入把数据写进其他租户。忽略租户的代码块中保留实体中的值。
     * 开启多租户、不在忽略租户的代码块中却没有当前租户时拒绝（失败闭合）：插入语句总带着 tenant_id，
     * 租户插件不会再检查，不拒绝就会插入 null，只能靠数据库的非空约束挡住，报错也看不出原因。
     * <p>
     * 关闭多租户时，实体中没有值的填默认租户：tenantId 是插入时填充的字段，MyBatis-Plus 总会把它写进插入语句
     * （不按“非空才插入”的策略省略），不填就会插入 null，违反 tenant_id 的非空约束
     * <p>
     * 租户表本身（tenantId 是主键）不是按租户隔离的数据，不处理：否则平台新增租户时主键会被改写为当前租户
     */
    private void fillTenantOnInsert(MetaObject metaObject) {
        if (metaObject == null || !metaObject.hasSetter(TENANT_ID) || isTenantPrimaryKey(metaObject.getOriginalObject())) {
            return;
        }
        TenantContext context = tenantContext.getIfAvailable();
        if (context != null && context.enabled()) {
            String tenantId = context.currentTenantId();
            if (tenantId != null) {
                metaObject.setValue(TENANT_ID, tenantId);
            } else if (!context.ignoring()) {
                throw new ServiceException("缺少租户上下文，拒绝写入按租户隔离的数据。后台任务请使用 @TenantJob 或 TenantHelper 指定租户");
            }
        } else if (metaObject.getValue(TENANT_ID) == null) {
            metaObject.setValue(TENANT_ID, context == null ? TenantContext.DEFAULT_TENANT_ID : context.defaultTenantId());
        }
    }

    /**
     * tenantId 是否为主键（与租户 SQL 改写判断按租户隔离的表一致：只看主键以外的字段）
     */
    private static boolean isTenantPrimaryKey(Object entity) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo != null && TENANT_ID.equals(tableInfo.getKeyProperty());
    }

}
