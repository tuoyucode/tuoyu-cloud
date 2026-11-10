package org.tuoyu.mes.equipment.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.equipment.domain.MesCheckRecord;

import java.util.List;

/**
 * 点检记录
 * <p>
 * 用户发起的查询与按 ID 的读取用带 {@code InScope} 后缀的方法，按创建部门与创建者限定在当前用户的数据权限内；
 * 数据权限由 Spring 代理在调用这些方法时设置，方法内转调 {@code BaseMapper} 的方法时一直有效。
 * {@code BaseMapper} 自带的方法不带数据权限，供按条件的写入（写入前已按 ID 确认在数据权限内）与后台任务使用
 *
 * @author tuoyu
 */
public interface MesCheckRecordMapper extends BaseMapper<MesCheckRecord> {

    /**
     * 分页查询（数据权限内）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesCheckRecord> selectPageInScope(Page<MesCheckRecord> page, Wrapper<MesCheckRecord> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限内），用于导出
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesCheckRecord> selectListInScope(Wrapper<MesCheckRecord> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 统计行数（数据权限内），用于导出前的行数检查。分页插件统计时去掉 ORDER BY，各数据库都能执行
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countInScope(Wrapper<MesCheckRecord> wrapper) {
        return this.selectPage(PageParam.<MesCheckRecord>countOnly(), wrapper).getTotal();
    }

    /**
     * 按 ID 查询（数据权限内）：不在数据权限内时为 null，调用方按“不存在”处理
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesCheckRecord selectByIdInScope(Long id) {
        return this.selectById(id);
    }

}
