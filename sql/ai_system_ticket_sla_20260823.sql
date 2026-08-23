-- =====================================================================
-- 工单 SLA 超时提醒数据库迁移
-- 日期: 2026-08-23
-- 说明: 为 ai_call_ticket 增加 SLA 截止时间、超时标志、最后提醒时间字段，
--       配合 TicketSlaScheduleTask 每 5 分钟扫描超时工单并推送提醒。
-- =====================================================================

ALTER TABLE ai_call_ticket ADD COLUMN due_time DATETIME COMMENT 'SLA截止时间' AFTER priority;

ALTER TABLE ai_call_ticket ADD COLUMN overtime_flag TINYINT DEFAULT 0 COMMENT '是否超时 0否 1是' AFTER due_time;

ALTER TABLE ai_call_ticket ADD COLUMN remind_time DATETIME COMMENT '最后提醒时间' AFTER overtime_flag;
