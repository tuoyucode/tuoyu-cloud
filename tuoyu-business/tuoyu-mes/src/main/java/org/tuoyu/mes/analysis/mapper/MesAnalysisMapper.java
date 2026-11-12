package org.tuoyu.mes.analysis.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.mes.analysis.domain.AnalysisScope;
import org.tuoyu.mes.analysis.domain.row.DefectGroupRow;
import org.tuoyu.mes.analysis.domain.row.DowntimeGroupRow;
import org.tuoyu.mes.analysis.domain.row.FirstPassRow;
import org.tuoyu.mes.analysis.domain.row.ProgressStatusRow;
import org.tuoyu.mes.analysis.domain.row.ReportTimeRow;
import org.tuoyu.mes.analysis.domain.row.ReportTotalRow;
import org.tuoyu.mes.plan.domain.MesOperationTask;
import org.tuoyu.mes.plan.domain.MesWorkorder;

import java.util.Collection;
import java.util.List;

/**
 * 分析报表的聚合查询（只读，没有对应的实体，所以不继承 BaseMapper）。
 * <p>
 * 写法约束（两种数据库都能执行，租户与数据权限插件都能正确改写）：
 * <ul>
 *     <li>只用 MySQL 与 PostgreSQL 都支持的语法：{@code cast(x as date)} 按日分组，{@code coalesce}、{@code case when}、
 *     {@code count(distinct)}；不用反引号、{@code ifnull}、{@code date_format}、{@code group_concat}；
 *     查询列除聚合外都出现在 GROUP BY 中（PostgreSQL 的要求）；周、月由服务层按日归并；</li>
 *     <li>不用子查询，关联一律是按被关联表主键的 JOIN：租户插件给 FROM 的表加 WHERE 条件、给 JOIN 的表加 ON 条件，
 *     每张表都带租户；数据权限条件加在最外层的 WHERE 上，列名带表别名，按单据（报工、工单、不良、安灯）的创建部门与创建者过滤；</li>
 *     <li>逻辑删除不会自动加在手写 SQL 上，主表都写明 {@code del_flag = '0'}；关联的主数据不按删除标志过滤，
 *     主数据删除后历史单据仍然计入；</li>
 *     <li>状态列表由服务层传入（非空），不在 SQL 中写死状态值。</li>
 * </ul>
 *
 * @author tuoyu
 */
public interface MesAnalysisMapper {

    /**
     * 生产进度的工单范围：计划开始时间（没有时为创建时间）在统计范围内、状态在给定列表中的工单
     */
    String PROGRESS_FROM = """
         from mes_workorder w
        where w.del_flag = '0'
          and w.workorder_status in
          <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
          and coalesce(w.plan_start_time, w.create_time) >= #{q.beginTime}
          and coalesce(w.plan_start_time, w.create_time) &lt; #{q.endTime}
          <if test="q.factoryId != null"> and w.factory_id = #{q.factoryId}</if>
          <if test="q.workshopId != null"> and w.workshop_id = #{q.workshopId}</if>
          <if test="q.lineId != null"> and w.line_id = #{q.lineId}</if>
          <if test="q.materialId != null"> and w.material_id = #{q.materialId}</if>
        """;

    String PROGRESS_COLUMNS = """
        select w.workorder_id, w.workorder_code, w.workorder_status, w.material_id, w.factory_id, w.workshop_id,
               w.line_id, w.plan_qty, w.completed_qty, w.defect_qty, w.stocked_qty, w.plan_start_time,
               w.plan_end_time, w.actual_start_time, w.actual_end_time, w.create_time
        """;

    String PROGRESS_ORDER = """
         order by coalesce(w.plan_start_time, w.create_time) desc, w.workorder_id desc
        """;

    /**
     * 统计范围内、状态有效、未删除的报工（关联工序任务取工序，关联车间按工厂过滤）
     */
    String REPORT_FROM = """
          from mes_production_report r
         inner join mes_operation_task t on t.task_id = r.task_id
         inner join mes_workshop ws on ws.workshop_id = r.workshop_id
         where r.del_flag = '0'
           and r.report_status in
           <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
           and r.report_time >= #{q.beginTime}
           and r.report_time &lt; #{q.endTime}
           <if test="q.factoryId != null"> and ws.factory_id = #{q.factoryId}</if>
           <if test="q.workshopId != null"> and r.workshop_id = #{q.workshopId}</if>
           <if test="q.lineId != null"> and r.line_id = #{q.lineId}</if>
           <if test="q.materialId != null"> and r.material_id = #{q.materialId}</if>
        """;

    /**
     * 报工的分组列（服务层只传 {@code date、product、line、user、team、operation}）
     */
    String REPORT_GROUP_SELECT = """
        <choose>
          <when test="groupBy == 'date'">cast(r.report_time as date) as report_date</when>
          <when test="groupBy == 'product'">r.material_id</when>
          <when test="groupBy == 'line'">r.line_id</when>
          <when test="groupBy == 'user'">r.report_user_id as user_id</when>
          <when test="groupBy == 'team'">r.team_id</when>
          <otherwise>t.operation_code, t.operation_name</otherwise>
        </choose>
        """;

    String REPORT_GROUP_BY = """
        <choose>
          <when test="groupBy == 'date'">cast(r.report_time as date)</when>
          <when test="groupBy == 'product'">r.material_id</when>
          <when test="groupBy == 'line'">r.line_id</when>
          <when test="groupBy == 'user'">r.report_user_id</when>
          <when test="groupBy == 'team'">r.team_id</when>
          <otherwise>t.operation_code, t.operation_name</otherwise>
        </choose>
        """;

    /**
     * 不良记录的分组列（服务层只传 {@code level、source、handleMethod、product、operation}）
     */
    String DEFECT_GROUP_SELECT = """
        <choose>
          <when test="groupBy == 'level'">d.defect_level</when>
          <when test="groupBy == 'source'">d.source_type</when>
          <when test="groupBy == 'handleMethod'">d.handle_method</when>
          <when test="groupBy == 'product'">d.material_id</when>
          <otherwise>t.operation_code, t.operation_name</otherwise>
        </choose>
        """;

    String DEFECT_GROUP_BY = DEFECT_GROUP_SELECT;

    /**
     * 停机事件的分组列（服务层只传 {@code date、line、reason}）
     */
    String DOWNTIME_GROUP_SELECT = """
        <choose>
          <when test="groupBy == 'date'">cast(e.report_time as date) as event_date</when>
          <when test="groupBy == 'line'">e.line_id</when>
          <otherwise>e.downtime_reason_id as reason_id</otherwise>
        </choose>
        """;

    String DOWNTIME_GROUP_BY = """
        <choose>
          <when test="groupBy == 'date'">cast(e.report_time as date)</when>
          <when test="groupBy == 'line'">e.line_id</when>
          <otherwise>e.downtime_reason_id</otherwise>
        </choose>
        """;

    /**
     * 生产进度：工单明细（分页）
     *
     * @param page     分页
     * @param q        统计范围
     * @param statuses 计入的工单状态
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "w.create_dept"),
        @DataColumn(key = "userName", value = "w.create_by")
    })
    @Select({"<script>", PROGRESS_COLUMNS, PROGRESS_FROM, PROGRESS_ORDER, "</script>"})
    Page<MesWorkorder> selectProgressPage(Page<MesWorkorder> page, @Param("q") AnalysisScope q,
                                          @Param("statuses") Collection<String> statuses);

    /**
     * 生产进度：工单明细（不分页，用于导出；导出前用 {@link #selectProgressPage} 统计行数）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "w.create_dept"),
        @DataColumn(key = "userName", value = "w.create_by")
    })
    @Select({"<script>", PROGRESS_COLUMNS, PROGRESS_FROM, PROGRESS_ORDER, "</script>"})
    List<MesWorkorder> selectProgressList(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses);

    /**
     * 生产进度：按状态汇总工单数、逾期数、计划数与完工数。
     * <p>
     * 逾期：有计划完成时间，且尚未完工（没有实际完成时间）时计划完成时间早于统计时刻，
     * 或已完工时实际完成时间晚于计划完成时间。与服务层逐行判断的口径一致
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "w.create_dept"),
        @DataColumn(key = "userName", value = "w.create_by")
    })
    @Select({"<script>", """
        select w.workorder_status,
               count(*) as workorder_count,
               sum(case when w.plan_end_time is not null
                         and ((w.actual_end_time is null and w.plan_end_time &lt; #{q.now})
                              or w.actual_end_time > w.plan_end_time)
                        then 1 else 0 end) as overdue_count,
               sum(w.plan_qty) as plan_qty,
               sum(w.completed_qty) as completed_qty
        """, PROGRESS_FROM, " group by w.workorder_status", "</script>"})
    List<ProgressStatusRow> selectProgressStatus(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses);

    /**
     * 在制品：给定状态（生产中、已暂停）、在统计范围结束前已开工的工单的全部工序任务，按工单与工序顺序排列。
     * 在制数由服务层按工序的前后关系计算（需要上一道工序的合格数，SQL 中不好表达）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "w.create_dept"),
        @DataColumn(key = "userName", value = "w.create_by")
    })
    @Select({"<script>", """
        select t.task_id, t.workorder_id, t.seq_no, t.operation_code, t.operation_name, t.line_id, t.plan_qty,
               t.qualified_qty, t.defect_qty, t.task_status
          from mes_operation_task t
         inner join mes_workorder w on w.workorder_id = t.workorder_id
         where t.del_flag = '0'
           and w.del_flag = '0'
           and w.workorder_status in
           <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
           and w.actual_start_time &lt; #{q.endTime}
           <if test="q.factoryId != null"> and w.factory_id = #{q.factoryId}</if>
           <if test="q.workshopId != null"> and w.workshop_id = #{q.workshopId}</if>
           <if test="q.lineId != null"> and t.line_id = #{q.lineId}</if>
           <if test="q.materialId != null"> and w.material_id = #{q.materialId}</if>
         order by t.workorder_id, t.seq_no, t.task_id
        """, "</script>"})
    List<MesOperationTask> selectWipTasks(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses);

    /**
     * 报工按一个维度汇总：报工数、合格数、不良数、填写的工时、缺少工时的报工数、报工人数
     *
     * @param groupBy 维度编码：date、product、line、user、team、operation
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "r.create_dept"),
        @DataColumn(key = "userName", value = "r.create_by")
    })
    @Select({"<script>", "select ", REPORT_GROUP_SELECT, """
               , count(*) as report_count,
               sum(r.report_qty) as report_qty,
               sum(r.qualified_qty) as qualified_qty,
               sum(r.defect_qty) as defect_qty,
               sum(coalesce(r.work_minutes, 0)) as work_minutes,
               sum(case when r.work_minutes is null then 1 else 0 end) as minutes_missing,
               count(distinct r.report_user_id) as user_count
        """, REPORT_FROM, " group by ", REPORT_GROUP_BY, "</script>"})
    List<ReportTotalRow> selectReportTotals(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses,
                                            @Param("groupBy") String groupBy);

    /**
     * 没有填写实际工时、但开始与结束时间都有的报工（工时由服务层按两者之差计算，两种数据库的时间差函数不同）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "r.create_dept"),
        @DataColumn(key = "userName", value = "r.create_by")
    })
    @Select({"<script>", """
        select r.report_user_id as user_id, r.team_id, t.operation_code, t.operation_name, r.material_id, r.line_id,
               r.start_time, r.end_time
        """, REPORT_FROM, """
           and r.work_minutes is null
           and r.start_time is not null
           and r.end_time is not null
        """, "</script>"})
    List<ReportTimeRow> selectReportTimes(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses);

    /**
     * 直通率的计算单元：按工单与工序任务（按日期统计时再按日）汇总报工数与合格数
     *
     * @param byDay 是否再按报工日期分组
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "r.create_dept"),
        @DataColumn(key = "userName", value = "r.create_by")
    })
    @Select({"<script>", """
        select <if test="byDay">cast(r.report_time as date) as report_date,</if>
               r.workorder_id, r.task_id, t.seq_no, r.material_id, r.line_id,
               sum(r.report_qty) as report_qty,
               sum(r.qualified_qty) as qualified_qty
        """, REPORT_FROM, """
         group by <if test="byDay">cast(r.report_time as date),</if>
               r.workorder_id, r.task_id, t.seq_no, r.material_id, r.line_id
        """, "</script>"})
    List<FirstPassRow> selectFirstPass(@Param("q") AnalysisScope q, @Param("statuses") Collection<String> statuses,
                                       @Param("byDay") boolean byDay);

    /**
     * 不良记录按一个维度汇总记录数与不良数量。时间按不良记录的登记时间（创建时间）；
     * 关联工单按工厂、车间、产线过滤，关联工序任务取工序（手工登记的不良可以没有工序）
     *
     * @param groupBy 维度编码：operation、level、source、handleMethod、product
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "d.create_dept"),
        @DataColumn(key = "userName", value = "d.create_by")
    })
    @Select({"<script>", "select ", DEFECT_GROUP_SELECT, """
               , count(*) as record_count,
               sum(d.defect_qty) as defect_qty
          from mes_defect_record d
         inner join mes_workorder w on w.workorder_id = d.workorder_id
          left join mes_operation_task t on t.task_id = d.task_id
         where d.del_flag = '0'
           and d.create_time >= #{q.beginTime}
           and d.create_time &lt; #{q.endTime}
           <if test="q.factoryId != null"> and w.factory_id = #{q.factoryId}</if>
           <if test="q.workshopId != null"> and w.workshop_id = #{q.workshopId}</if>
           <if test="q.lineId != null"> and w.line_id = #{q.lineId}</if>
           <if test="q.materialId != null"> and d.material_id = #{q.materialId}</if>
         group by
        """, DEFECT_GROUP_BY, "</script>"})
    List<DefectGroupRow> selectDefectGroups(@Param("q") AnalysisScope q, @Param("groupBy") String groupBy);

    /**
     * 停机事件按一个维度汇总次数、未解决数、已解决数与已解决事件的停机时长。
     * <p>
     * 停机事件：停线级别、或填写了停机原因的安灯事件（与安灯解决时记录停机时长的规则一致），按上报时间统计；
     * 未解决按状态判断（状态在 {@code openStatuses} 中）；已解决为有解决时间的事件，停机时长只取这些事件解决时记录的停机分钟数。
     * 未经解决直接关闭（误报）的事件只计入次数，既不算未解决，也不计时长。
     * 按产品过滤时关联工单（没有关联工单的事件此时不计入）
     *
     * @param criticalLevel 停线级别的编码
     * @param openStatuses  未解决的状态（已上报、已派单、处理中）
     * @param groupBy       维度编码：reason、line、date
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "e.create_dept"),
        @DataColumn(key = "userName", value = "e.create_by")
    })
    @Select({"<script>", "select ", DOWNTIME_GROUP_SELECT, """
               , count(*) as event_count,
               sum(case when e.event_status in
                   <foreach collection="openStatuses" item="status" open="(" separator="," close=")">#{status}</foreach>
                   then 1 else 0 end) as open_count,
               sum(case when e.resolve_time is not null then 1 else 0 end) as resolved_count,
               sum(case when e.resolve_time is not null then coalesce(e.downtime_minutes, 0) else 0 end) as downtime_minutes
          from mes_andon_event e
         inner join mes_workshop ws on ws.workshop_id = e.workshop_id
          left join mes_workorder w on w.workorder_id = e.workorder_id
         where e.del_flag = '0'
           and e.report_time >= #{q.beginTime}
           and e.report_time &lt; #{q.endTime}
           and (e.event_level = #{criticalLevel} or e.downtime_reason_id is not null)
           <if test="q.factoryId != null"> and ws.factory_id = #{q.factoryId}</if>
           <if test="q.workshopId != null"> and e.workshop_id = #{q.workshopId}</if>
           <if test="q.lineId != null"> and e.line_id = #{q.lineId}</if>
           <if test="q.materialId != null"> and w.material_id = #{q.materialId}</if>
         group by
        """, DOWNTIME_GROUP_BY, "</script>"})
    List<DowntimeGroupRow> selectDowntimeGroups(@Param("q") AnalysisScope q, @Param("criticalLevel") String criticalLevel,
                                                @Param("openStatuses") Collection<String> openStatuses,
                                                @Param("groupBy") String groupBy);

}
