-- V8：菜单与字典的种子数据在 MySQL、PostgreSQL 中保持一致。可重复执行
-- “流程设计”页面（隐藏菜单）：显示状态与菜单状态按 '0' / '1' 存储（visible '1' 为隐藏，status '0' 为正常）。
-- PostgreSQL 基线中写成了 'N' / 'Y'，菜单被当作停用，路由中没有该页面，流程设计器无法打开
update sys_menu set visible = '1', status = '0' where menu_id = 1761400000000011700 and (visible <> '1' or status <> '0');

-- 流程表达式的按钮名称与页面名称一致
update sys_menu set menu_name = '流程表达式查询' where menu_id = 1761400000000011802 and menu_name in ('流程达式定义查询', '流程spel表达式定义查询');
update sys_menu set menu_name = '流程表达式新增' where menu_id = 1761400000000011803 and menu_name in ('流程达式定义新增', '流程spel表达式定义新增');
update sys_menu set menu_name = '流程表达式修改' where menu_id = 1761400000000011804 and menu_name in ('流程达式定义修改', '流程spel表达式定义修改');
update sys_menu set menu_name = '流程表达式删除' where menu_id = 1761400000000011805 and menu_name in ('流程达式定义删除', '流程spel表达式定义删除');

-- 授权类型字典的样式类 el-check-tag 是 Element Plus 的组件类名，前端（Ant Design Vue）不使用，清空
update sys_dict_data set css_class = '' where dict_code between 1761600000000000030 and 1761600000000000034 and css_class = 'el-check-tag';

-- 多租户（V3）新增的列与表补上列注释，与 MySQL 一致（代码生成从列注释生成字段说明与表单标签）
comment on column sys_user.tenant_id                 is '租户编码';
comment on column sys_role.tenant_id                 is '租户编码';
comment on column sys_dept.tenant_id                 is '租户编码';
comment on column sys_post.tenant_id                 is '租户编码';
comment on column sys_notice.tenant_id               is '租户编码';
comment on column sys_message.tenant_id              is '租户编码';
comment on column sys_oper_log.tenant_id             is '租户编码';
comment on column sys_login_info.tenant_id           is '租户编码';
comment on column sys_social.tenant_id               is '租户编码';
comment on column sys_oss.tenant_id                  is '租户编码';
comment on column sys_tenant.tenant_id               is '租户编码';
comment on column sys_tenant.tenant_name             is '租户名称';
comment on column sys_tenant.contact_user            is '联系人';
comment on column sys_tenant.contact_phone           is '联系电话';
comment on column sys_tenant.domain                  is '绑定的访问域名';
comment on column sys_tenant.package_id              is '租户套餐';
comment on column sys_tenant.expire_time             is '过期时间（为空表示不过期）';
comment on column sys_tenant.account_count           is '用户数上限（为空表示不限）';
comment on column sys_tenant.status                  is '状态（0正常 1停用）';
comment on column sys_tenant.del_flag                is '删除标志（0存在 1删除）';
comment on column sys_tenant.remark                  is '备注';
comment on column sys_tenant.create_dept             is '创建部门';
comment on column sys_tenant.create_by               is '创建者';
comment on column sys_tenant.create_time             is '创建时间';
comment on column sys_tenant.update_by               is '更新者';
comment on column sys_tenant.update_time             is '更新时间';
comment on column sys_tenant_package.package_id      is '套餐编号';
comment on column sys_tenant_package.package_name    is '套餐名称';
comment on column sys_tenant_package.status          is '状态（0正常 1停用）';
comment on column sys_tenant_package.del_flag        is '删除标志（0存在 1删除）';
comment on column sys_tenant_package.remark          is '备注';
comment on column sys_tenant_package.create_dept     is '创建部门';
comment on column sys_tenant_package.create_by       is '创建者';
comment on column sys_tenant_package.create_time     is '创建时间';
comment on column sys_tenant_package.update_by       is '更新者';
comment on column sys_tenant_package.update_time     is '更新时间';
comment on column sys_tenant_package_menu.package_id is '套餐编号';
comment on column sys_tenant_package_menu.menu_id    is '菜单编号';
