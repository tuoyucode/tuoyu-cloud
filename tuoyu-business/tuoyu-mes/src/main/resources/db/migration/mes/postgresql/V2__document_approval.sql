-- V7：不良处置评审与补料申请接入工作流审批，与 mysql/V7__document_approval.sql 相同的改动
-- 1. 不良记录、领退料单记录当前（最近一次）审批的业务 ID，审批结果按它匹配单据
-- 2. 状态新增 approving（审批中），只更新字段说明

alter table mes_defect_record add column if not exists approval_business_id varchar(40) default null;
comment on column mes_defect_record.approval_business_id is '当前（最近一次）审批的业务 ID';
comment on column mes_defect_record.handle_status is '处置状态（pending 待处置、approving 审批中、handled 已处置）';

alter table mes_material_request add column if not exists approval_business_id varchar(40) default null;
comment on column mes_material_request.approval_business_id is '当前（最近一次）审批的业务 ID';
comment on column mes_material_request.request_status is '单据状态（draft 草稿、approving 审批中、submitted 已提交、completed 已完成、cancelled 已取消）';
