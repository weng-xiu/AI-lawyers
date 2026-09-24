-- =====================================================================
-- P3-D5：语音留言回放与转工单 —— ai_missed_call 增加录音文件 URL 字段
-- 日期: 2026-09-25
-- 说明: 向前兼容、可重复执行（ALTER 做存在性判断）。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_missed_call' AND column_name = 'voice_file_url');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_missed_call ADD COLUMN voice_file_url VARCHAR(512) DEFAULT NULL COMMENT ''语音留言录音文件 URL/路径'' AFTER voice_duration',
    'SELECT ''ai_missed_call.voice_file_url already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
