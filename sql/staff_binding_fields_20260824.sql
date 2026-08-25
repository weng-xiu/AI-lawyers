-- 业务表补充处理人/受理人/回访人 ID 字段，关联 sys_user.user_id
ALTER TABLE ai_callback ADD COLUMN visit_by_id bigint(20) DEFAULT NULL COMMENT '回访人用户ID' AFTER visit_by;
ALTER TABLE ai_return_visit_task ADD COLUMN assignee_id bigint(20) DEFAULT NULL COMMENT '受理人用户ID' AFTER assignee;
ALTER TABLE ai_paid_legal_service ADD COLUMN handler_id bigint(20) DEFAULT NULL COMMENT '处理人用户ID' AFTER handler_name;
ALTER TABLE ai_risk_warning ADD COLUMN handler_id bigint(20) DEFAULT NULL COMMENT '处理人用户ID' AFTER handler_name;
