-- =====================================================================
-- F3 风险联动转办建议 —— 数据库迁移脚本（第十五部分 F3 设计第 3 点，V2.12）
-- 日期: 2026-09-19
-- 说明: 高风险诉求（家暴/欠薪/工伤等）命中风险规则后自动给出转办条线建议，
--       坐席在风险预警页一键确认，系统自动建工单并复用既有 F3 转办流水发起转出。
--       全部向前兼容、可重复执行（ALTER 均做 information_schema 存在性判断）。
-- 执行前提: 已执行 ai_system_f_group_20260913.sql（ai_ticket_transfer/ai_external_org/字典 ai_external_type）。
-- =====================================================================

-- 一、风险预警规则：配置"建议转办条线"与默认建议机构（均可空，空=该规则不给出转办建议）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning_rule' AND column_name = 'suggest_transfer_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_risk_warning_rule ADD COLUMN suggest_transfer_type VARCHAR(20) DEFAULT NULL COMMENT ''建议转办条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345，空=不建议'' AFTER keywords',
    'SELECT ''ai_risk_warning_rule.suggest_transfer_type already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning_rule' AND column_name = 'suggest_org_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_risk_warning_rule ADD COLUMN suggest_org_id BIGINT DEFAULT NULL COMMENT ''默认建议协同机构ID（ai_external_org.org_id），空=坐席按条线自选'' AFTER suggest_transfer_type',
    'SELECT ''ai_risk_warning_rule.suggest_org_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 二、风险预警记录：命中规则、建议快照（预警发生时点的建议，规则事后修改不影响历史预警）、已发起的转办流水
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning' AND column_name = 'rule_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_risk_warning ADD COLUMN rule_id BIGINT DEFAULT NULL COMMENT ''命中的风险预警规则ID（ai_risk_warning_rule.rule_id），空=手工/其他来源'' AFTER source_id',
    'SELECT ''ai_risk_warning.rule_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning' AND column_name = 'suggest_transfer_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_risk_warning ADD COLUMN suggest_transfer_type VARCHAR(20) DEFAULT NULL COMMENT ''建议转办条线快照，预警生成时由命中规则写入'' AFTER rule_id',
    'SELECT ''ai_risk_warning.suggest_transfer_type already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning' AND column_name = 'transfer_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_risk_warning ADD COLUMN transfer_id BIGINT DEFAULT NULL COMMENT ''一键转办生成的转办流水ID（ai_ticket_transfer.transfer_id），非空=已转办'' AFTER suggest_transfer_type',
    'SELECT ''ai_risk_warning.transfer_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 三、按预警检索转办流水的索引（预警详情反查 + 防重复转办）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_risk_warning' AND index_name = 'idx_risk_warning_transfer');
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_risk_warning_transfer ON ai_risk_warning (transfer_id)',
    'SELECT ''idx_risk_warning_transfer already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 说明：高风险规则的建议条线属于业务配置（如 家暴→LEGAL_AID/MEDIATION、欠薪→LEGAL_AID、工伤→LEGAL_AID），
--       由管理员在"风险预警-预警规则"页维护，不在迁移脚本中预置，避免覆盖现场配置。
