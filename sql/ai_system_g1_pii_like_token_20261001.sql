-- ============================================================
-- P3-G1-b2 LIKE 检索类表加密（V2.56）：话单/台账字段扩列 + 盲索引列 + 模糊检索 token 表
-- 创建日期：2026-10-01
--
-- 背景：ai_call_record / ai_call_ledger 号码列由明文改为 SM4-GCM 密文（encp: 前缀），
--       等值查询走 caller_number_index 盲索引；LIKE '%kw%' 模糊检索改走
--       ai_pii_search_token 位置分片盲 token（组间 OR / 组内 AND，语义等价零误判）。
--       ai_call_ticket 表本身无 caller_number 列（号码经 join ai_call_record 取得），
--       话单加密即覆盖工单检索，故本脚本不涉及工单表结构。
--
-- 同构强约束：ai_call_record 的 ALTER（扩列/加列/索引）同步镜像归档表
--       ai_call_record_archive（归档 INSERT...SELECT 列数一致性）。
--
-- 执行顺序：本 DDL → 部署新版本 → 管理员依次调用
--           POST /lawyers/pii/migrateCallRecord
--           POST /lawyers/pii/migrateCallLedger
--           （来电档案 /lawyers/pii/migrateCallerProfile 亦已升级回填 token）
-- 幂等：全部 ALTER 经 information_schema 判断，可重复执行。
-- ============================================================

-- ------------------------------------------------------------
-- 1. ai_call_record：号码列扩长 + 盲索引列 + 普通索引
-- ------------------------------------------------------------
SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
         AND column_name = 'caller_number' AND character_maximum_length >= 128) = 0,
    'ALTER TABLE ai_call_record MODIFY COLUMN caller_number VARCHAR(128) NOT NULL COMMENT ''来电号码（SM4-GCM 密文 encp: 前缀，G1-b2）''',
    'SELECT ''ai_call_record.caller_number already widened, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
         AND column_name = 'caller_number_index') = 0,
    'ALTER TABLE ai_call_record ADD COLUMN caller_number_index VARCHAR(32) DEFAULT NULL COMMENT ''来电号码盲索引（HMAC-SM3 截断 128 位 hex，G1-b2）'' AFTER caller_number',
    'SELECT ''ai_call_record.caller_number_index already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
         AND index_name = 'idx_record_number_index') = 0,
    'ALTER TABLE ai_call_record ADD KEY idx_record_number_index (caller_number_index)',
    'SELECT ''ai_call_record idx_record_number_index already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 2. ai_call_record_archive：同构镜像（列扩长 + 盲索引列 + 索引）
-- ------------------------------------------------------------
SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
         AND column_name = 'caller_number' AND character_maximum_length >= 128) = 0,
    'ALTER TABLE ai_call_record_archive MODIFY COLUMN caller_number VARCHAR(128) NOT NULL COMMENT ''来电号码（SM4-GCM 密文 encp: 前缀，G1-b2 镜像）''',
    'SELECT ''archive.caller_number already widened, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
         AND column_name = 'caller_number_index') = 0,
    'ALTER TABLE ai_call_record_archive ADD COLUMN caller_number_index VARCHAR(32) DEFAULT NULL COMMENT ''来电号码盲索引（G1-b2 镜像）'' AFTER caller_number',
    'SELECT ''archive.caller_number_index already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.statistics
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
         AND index_name = 'idx_archive_number_index') = 0,
    'ALTER TABLE ai_call_record_archive ADD KEY idx_archive_number_index (caller_number_index)',
    'SELECT ''archive idx_archive_number_index already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 3. ai_call_ledger：号码/身份证列扩长（承载密文）；模糊检索走 token 表，无索引列
-- ------------------------------------------------------------
SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_ledger'
         AND column_name = 'caller_phone' AND character_maximum_length >= 128) = 0,
    'ALTER TABLE ai_call_ledger MODIFY COLUMN caller_phone VARCHAR(128) DEFAULT NULL COMMENT ''联系电话（SM4-GCM 密文 encp: 前缀，G1-b2）''',
    'SELECT ''ai_call_ledger.caller_phone already widened, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
       WHERE table_schema = DATABASE() AND table_name = 'ai_call_ledger'
         AND column_name = 'caller_id_card' AND character_maximum_length >= 128) = 0,
    'ALTER TABLE ai_call_ledger MODIFY COLUMN caller_id_card VARCHAR(128) DEFAULT NULL COMMENT ''身份证号（SM4-GCM 密文 encp: 前缀，G1-b2）''',
    'SELECT ''ai_call_ledger.caller_id_card already widened, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- 4. 模糊检索位置分片盲 token 表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_pii_search_token (
  token_id    BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT 'tokenID',
  owner_type  VARCHAR(30)   NOT NULL                COMMENT '属主类型 CALL_RECORD/CALL_LEDGER/CALLER_PROFILE',
  owner_id    BIGINT(20)    NOT NULL                COMMENT '属主ID record_id/ledger_id/profile_id',
  token_value CHAR(32)      NOT NULL                COMMENT '位置分片盲token（HMAC-SM3 截断 128 位 hex）',
  PRIMARY KEY (token_id),
  UNIQUE KEY uk_pii_token_owner (owner_type, owner_id, token_value),
  KEY idx_pii_token_lookup (token_value, owner_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PII 模糊检索位置分片盲token（G1-b2）';
