-- =====================================================================
-- P3-E3（一阶段）：话后 AI 通话小结 —— ai_call_record 增大小结字段
-- 日期: 2026-09-25
-- 说明: 向前兼容、可重复执行（ALTER 做存在性判断）。
--       ai_summary_status: 0=待生成 1=生成中 2=已生成 3=失败
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- AI 通话小结正文（结构化文本：案情摘要/争议焦点/法律意见/待办/回访建议）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record' AND column_name = 'ai_summary');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_record ADD COLUMN ai_summary MEDIUMTEXT DEFAULT NULL COMMENT ''AI通话小结（案情摘要/法律意见/待办/回访建议）'' AFTER transcript',
    'SELECT ''ai_call_record.ai_summary already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 小结生成状态：0待生成 1生成中 2已生成 3失败
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record' AND column_name = 'ai_summary_status');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_record ADD COLUMN ai_summary_status CHAR(1) DEFAULT ''0'' COMMENT ''AI小结状态 0待生成 1生成中 2已生成 3失败'' AFTER ai_summary',
    'SELECT ''ai_call_record.ai_summary_status already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 小结生成时间
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record' AND column_name = 'ai_summary_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_record ADD COLUMN ai_summary_time DATETIME DEFAULT NULL COMMENT ''AI小结生成时间'' AFTER ai_summary_status',
    'SELECT ''ai_call_record.ai_summary_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 失败原因（模型异常/无可用文本等，便于排障与重试）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record' AND column_name = 'ai_summary_fail_reason');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_record ADD COLUMN ai_summary_fail_reason VARCHAR(500) DEFAULT NULL COMMENT ''AI小结失败原因'' AFTER ai_summary_time',
    'SELECT ''ai_call_record.ai_summary_fail_reason already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
