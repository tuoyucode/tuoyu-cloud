-- V7：不良处置评审与补料申请接入工作流审批
-- 1. 不良记录、领退料单记录当前（最近一次）审批的业务 ID，审批结果按它匹配单据
-- 2. 状态新增 approving（审批中），只更新字段说明

alter table mes_defect_record add column approval_business_id varchar(40) default null comment '当前（最近一次）审批的业务 ID' after handle_remark;
alter table mes_defect_record modify column handle_status varchar(32) not null default 'pending' comment '处置状态（pending 待处置、approving 审批中、handled 已处置）';

alter table mes_material_request add column approval_business_id varchar(40) default null comment '当前（最近一次）审批的业务 ID' after operator_user_id;
alter table mes_material_request modify column request_status varchar(32) not null default 'draft' comment '单据状态（draft 草稿、approving 审批中、submitted 已提交、completed 已完成、cancelled 已取消）';
