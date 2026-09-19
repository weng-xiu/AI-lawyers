-- =====================================================================
-- F3 联调发现的 schema 缺口修复（V2.12，2026-09-19）
-- 说明: 本地/存量环境执行 F3（ai_system_risk_transfer_20260919.sql）联调时
--       暴露两处建表语句与代码不一致的历史缺口，生产部署 F3 前必须执行：
--       1) ai_ticket_transfer 缺 remark 列 —— AiTicketTransfer 继承 RuoYi
--          BaseEntity（含 remark），mapper resultMap/selectVo 均引用 remark，
--          缺列导致转办重试任务 TicketTransferRetryTask 周期性 BadSqlGrammar；
--       2) ai_call_ticket.record_id 为 NOT NULL 无默认值 —— F3 风险预警一键
--          转办对"非通话来源/通话记录缺失"的预警自动建工单时不写 record_id，
--          缺省直接 insert 报 Field 'record_id' doesn't have a default value。
--       全部幂等可重复执行（information_schema 存在性判断）。
-- 执行前提: 已执行 ai_system_f_group_20260913.sql（ai_ticket_transfer 建表）。
-- =====================================================================

-- 一、ai_ticket_transfer 补 remark 列（与 BaseEntity/BaseMapper 口径一致 VARCHAR(500)）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_ticket_transfer' AND column_name = 'remark');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_ticket_transfer ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT ''备注'' AFTER update_time',
    'SELECT ''ai_ticket_transfer.remark already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 二、ai_call_ticket.record_id 放开非空约束（F3 自动建单场景允许无通话记录；
--     通话来源的工单仍由业务代码正常回填 record_id，不受影响）
SET @col_nullable = (SELECT IS_NULLABLE FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'record_id');
SET @ddl = IF(@col_nullable = 'NO',
    'ALTER TABLE ai_call_ticket MODIFY COLUMN record_id BIGINT DEFAULT NULL COMMENT ''通话记录ID''',
    'SELECT ''ai_call_ticket.record_id already nullable, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
