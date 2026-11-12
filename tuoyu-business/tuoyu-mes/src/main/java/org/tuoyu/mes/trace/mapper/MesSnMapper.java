package org.tuoyu.mes.trace.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tuoyu.common.mybatis.annotation.DataColumn;
import org.tuoyu.common.mybatis.annotation.DataPermission;
import org.tuoyu.common.mybatis.core.page.PageParam;
import org.tuoyu.mes.trace.domain.MesSn;

import java.util.List;

/**
 * 产品序列号
 * <p>
 * 名称带 {@code InScope} 的方法按序列号自己的创建部门与创建者限定在当前用户的数据权限内，供列表、导出、详情与报废使用；
 * 数据权限由 Spring 代理在调用这些方法时设置，方法内转调 {@code BaseMapper} 的方法时一直有效。
 * {@code BaseMapper} 自带的方法不带数据权限，供工单完工、追溯、标签打印等单据之间的内部读取与按条件的写入使用
 *
 * @author tuoyu
 */
public interface MesSnMapper extends BaseMapper<MesSn> {

    /**
     * 分页查询（数据权限内）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<MesSn> selectPageInScope(Page<MesSn> page, Wrapper<MesSn> wrapper) {
        return this.selectPage(page, wrapper);
    }

    /**
     * 列表查询（数据权限内），用于导出
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<MesSn> selectListInScope(Wrapper<MesSn> wrapper) {
        return this.selectList(wrapper);
    }

    /**
     * 统计行数（数据权限内），用于导出前的行数检查。分页插件统计时去掉 ORDER BY，各数据库都能执行
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default long countInScope(Wrapper<MesSn> wrapper) {
        return this.selectPage(PageParam.<MesSn>countOnly(), wrapper).getTotal();
    }

    /**
     * 按 ID 查询（数据权限内）：不在数据权限内时为 null，调用方按“不存在”处理
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "create_dept"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default MesSn selectByIdInScope(Long snId) {
        return this.selectById(snId);
    }

}
