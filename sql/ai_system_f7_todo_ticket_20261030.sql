-- =====================================================================
-- F7：待办内嵌工单处理 —— ai_todo 增加业务关联字段
-- 日期: 2026-10-30
-- 说明: 待办可通过 biz_type/biz_id 关联业务对象（当前仅 ticket 工单），
--       前端待办卡片据此内嵌受理/转办/办结，无需跳转工单管理。
--       向前兼容、可重复执行（ALTER 做存在性判断）。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_todo' AND column_name = 'biz_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_todo ADD COLUMN biz_type VARCHAR(32) DEFAULT NULL COMMENT ''关联业务类型（ticket=工单）'' AFTER user_id',
    'SELECT ''ai_todo.biz_type already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_todo' AND column_name = 'biz_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_todo ADD COLUMN biz_id BIGINT(20) DEFAULT NULL COMMENT ''关联业务ID（工单ID）'' AFTER biz_type',
    'SELECT ''ai_todo.biz_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_todo' AND index_name = 'idx_ai_todo_biz');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_todo ADD INDEX idx_ai_todo_biz (biz_type, biz_id)',
    'SELECT ''ai_todo.idx_ai_todo_biz already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验
SELECT 'ai_todo' AS t, COUNT(*) AS biz_cols FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'ai_todo'
  AND column_name IN ('biz_type', 'biz_id');
