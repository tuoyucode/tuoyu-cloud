package org.tuoyu.mes.andon.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.common.mybatis.core.query.QueryBuilder;
import org.tuoyu.mes.andon.domain.MesAndonEvent;
import org.tuoyu.mes.andon.escalation.AndonEscalationRules;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 安灯事件
 * <p>
 * 页面上的列表、导出、看板统计与按 ID 查看走带数据权限的 {@code *InScope} 方法（按创建部门与创建者，即上报人所在部门与上报人）。
 * 数据权限由 Spring 代理在调用这些方法时设置，方法内部转调的 {@code selectPage}、{@code selectList}、{@code selectById}
 * 在同一次调用中执行，同样受约束。BaseMapper 自带的方法不带数据权限，供引用校验（删除设备、停机原因前检查是否被使用）、
 * 按条件更新状态（更新前已按 ID 确认在数据权限内）使用。
 * <p>
 * 超时检查（后台任务，没有登录用户）用 {@link #selectDeadlinePending(Collection, int)}、
 * {@link #selectOverdue(Collection, LocalDateTime, int)} 找出要处理的事件，只取主键与租户。两者按主键排序、每次只取一批，
 * 开启多租户时由调用方传入启用的租户，在查询中排除停用或过期租户的事件，否则这些事件会一直占住前面的批次。
 *
 * @author tuoyu
 */
public interface MesAndonEventMapper extends BaseMapper<MesAndonEvent> {

    /**
     * 分页查询数据权限范围内的安灯事件
     *
     * @param page    分页参数
     * @param wrapper 查询条件
     * @return 分页结果
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesAndonEvent> selectPageInScope(Page<MesAndonEvent> page, Wrapper<MesAndonEvent> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 查询数据权限范围内的安灯事件（导出）
     *
     * @param wrapper 查询条件
     * @return 安灯事件
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesAndonEvent> selectListInScope(Wrapper<MesAndonEvent> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 统计数据权限范围内的安灯事件（导出前的行数），条件与 {@link #selectListInScope(Wrapper)} 一致
     *
     * @param wrapper 查询条件
     * @return 行数
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countInScope(Wrapper<MesAndonEvent> wrapper) {
        // 分页插件统计行数时会去掉 ORDER BY，各数据库都能执行
        return this.selectPage(PageParam.<MesAndonEvent>countOnly(), wrapper).getTotal();
    }

    /**
     * 按 ID 查询数据权限范围内的安灯事件
     *
     * @param eventId 事件 ID
     * @return 范围外或不存在时为 null
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesAndonEvent selectByIdInScope(Long eventId) {
        return this.selectById(eventId);
    }

    /**
     * 截止时间待计算的未解决事件（上报后、升级规则变化后），按主键排序，只取主键与租户
     *
     * @param tenantIds 只取这些租户的事件（非空）；为 null 时不按租户过滤（未开启多租户）
     * @param limit     最多取几条
     * @return 事件（只有 eventId、tenantId）
     */
    default List<MesAndonEvent> selectDeadlinePending(Collection<String> tenantIds, int limit) {
        return this.selectPage(new Page<>(1, limit, false), QueryBuilder.lambda(MesAndonEvent.class)
            .select(MesAndonEvent::getEventId, MesAndonEvent::getTenantId)
            .in(tenantIds != null, MesAndonEvent::getTenantId, tenantIds)
            .eq(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.UNCHECKED)
            .in(MesAndonEvent::getEventStatus, AndonEscalationRules.RESOLVE_WATCHED)
            .orderByAsc(MesAndonEvent::getEventId)
            .build()).getRecords();
    }

    /**
     * 有时限已超时、还没有为它升级的事件：未开始处理且超过响应截止时间，或未解决且超过解决截止时间。按主键排序，只取主键与租户
     *
     * @param tenantIds 只取这些租户的事件（非空）；为 null 时不按租户过滤（未开启多租户）
     * @param now       当前时间
     * @param limit     最多取几条
     * @return 事件（只有 eventId、tenantId）
     */
    default List<MesAndonEvent> selectOverdue(Collection<String> tenantIds, LocalDateTime now, int limit) {
        return this.selectPage(new Page<>(1, limit, false), QueryBuilder.lambda(MesAndonEvent.class)
            .select(MesAndonEvent::getEventId, MesAndonEvent::getTenantId)
            .in(tenantIds != null, MesAndonEvent::getTenantId, tenantIds)
            .eq(MesAndonEvent::getDeadlineChecked, AndonEscalationRules.CHECKED)
            .and(overdue -> overdue
                .nested(response -> response
                    .in(MesAndonEvent::getEventStatus, AndonEscalationRules.RESPONSE_WATCHED)
                    .isNull(MesAndonEvent::getResponseOverdueTime)
                    .le(MesAndonEvent::getResponseDueTime, now))
                .or(resolve -> resolve
                    .in(MesAndonEvent::getEventStatus, AndonEscalationRules.RESOLVE_WATCHED)
                    .isNull(MesAndonEvent::getResolveOverdueTime)
                    .le(MesAndonEvent::getResolveDueTime, now)))
            .orderByAsc(MesAndonEvent::getEventId)
            .build()).getRecords();
    }

}
