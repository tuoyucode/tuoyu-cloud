package org.tuoyu.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户套餐与菜单的关联
 *
 * @author tuoyu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_tenant_package_menu")
public class SysTenantPackageMenu {

    private Long packageId;

    private Long menuId;

}
