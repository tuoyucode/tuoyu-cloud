package org.tuoyu.system.mapper;

import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tuoyu.system.domain.SysTenant;
/**
 * 租户 数据层（sys_tenant 平台共享，主键是 tenant_id，不按租户隔离）
 *
 * @author tuoyu
 */
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /**
     * 统计租户编码是否用过（包括已删除的租户：已删除租户的数据仍带着它的编码，不能复用）
     *
     * @param tenantId 租户编码
     * @return 数量
     */
    @Select("select count(*) from sys_tenant where tenant_id = #{tenantId}")
    long countIncludingDeleted(String tenantId);

}
