package org.tuoyu.mes.material.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.material.domain.MesStockIn;

import java.util.Collection;
import java.util.List;

/**
 * 完工入库单
 * <p>
 * 用户发起的查询与按 ID 的读取用带 {@code InScope} 后缀的方法，按创建部门与创建者限定在当前用户的数据权限内；
 * 数据权限由 Spring 代理在调用这些方法时设置，方法内转调 {@code BaseMapper} 的方法时一直有效。
 * {@code BaseMapper} 自带的方法不带数据权限，供单据之间的内部读取与按条件的写入使用
 *
 * @author tuoyu
 */
public interface MesStockInMapper extends BaseMapper<MesStockIn> {

    /**
     * 分页查询（数据权限内）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesStockIn> selectPageInScope(Page<MesStockIn> page, Wrapper<MesStockIn> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限内），用于导出
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesStockIn> selectListInScope(Wrapper<MesStockIn> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 统计行数（数据权限内），用于导出前的行数检查。分页插件统计时去掉 ORDER BY，各数据库都能执行
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countInScope(Wrapper<MesStockIn> wrapper) {
        return this.selectPage(PageParam.<MesStockIn>countOnly(), wrapper).getTotal();
    }

    /**
     * 按 ID 查询（数据权限内）：不在数据权限内时为 null，调用方按“不存在”处理
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesStockIn selectByIdInScope(Long id) {
        return this.selectById(id);
    }

    /**
     * 按 ID 批量查询（数据权限内）：不在数据权限内的不返回
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesStockIn> selectByIdsInScope(Collection<Long> ids) {
        return this.selectByIds(ids);
    }

}
