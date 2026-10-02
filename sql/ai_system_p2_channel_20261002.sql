-- =====================================================================
-- P2-16（V2.62）：全渠道收敛——公众端网页咨询接入 ACD/工单体系
-- 日期：2026-10-02
--
-- 核心变更：ai_chat_session 纳入画像/排队/工单；ai_call_queue 支持多渠道；
-- ai_call_ticket 增加来源渠道；工单对话快照表（审计用）。
-- 全部使用 PREPARE/IF 判断，幂等可重复执行。
-- =====================================================================

-- -------------------------------------------------------------
-- 1) ai_chat_session：纳入画像 / ACD / 工单体系（幂等扩列）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'profile_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN profile_id BIGINT(20) DEFAULT NULL COMMENT "统一画像ID（ai_caller_profile.profile_id）" AFTER customer_phone',
    'SELECT "ai_chat_session.profile_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'category_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN category_id BIGINT(20) DEFAULT NULL COMMENT "咨询分类ID（决定技能组）" AFTER profile_id',
    'SELECT "ai_chat_session.category_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'queue_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN queue_id BIGINT(20) DEFAULT NULL COMMENT "ACD 排队ID（ai_call_queue.queue_id）" AFTER category_id',
    'SELECT "ai_chat_session.queue_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'ticket_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN ticket_id BIGINT(20) DEFAULT NULL COMMENT "关联工单ID（ai_call_ticket.ticket_id）" AFTER queue_id',
    'SELECT "ai_chat_session.ticket_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'handoff_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN handoff_time DATETIME DEFAULT NULL COMMENT "转人工时间" AFTER ticket_id',
    'SELECT "ai_chat_session.handoff_time already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'answer_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN answer_time DATETIME DEFAULT NULL COMMENT "坐席首次应答时间（SLA）" AFTER handoff_time',
    'SELECT "ai_chat_session.answer_time already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_chat_session' AND column_name = 'close_reason');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_chat_session ADD COLUMN close_reason VARCHAR(32) DEFAULT NULL COMMENT "关闭原因（agent_close/user_leave/timeout/to_ticket）" AFTER answer_time',
    'SELECT "ai_chat_session.close_reason already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引（忽略已存在错误）
ALTER TABLE ai_chat_session ADD KEY idx_chat_profile (profile_id);
ALTER TABLE ai_chat_session ADD KEY idx_chat_category (category_id);
ALTER TABLE ai_chat_session ADD KEY idx_chat_queue (queue_id);
ALTER TABLE ai_chat_session ADD KEY idx_chat_ticket (ticket_id);

-- -------------------------------------------------------------
-- 2) ai_call_queue：支持多渠道语义（幂等扩列）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_queue' AND column_name = 'channel_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_queue ADD COLUMN channel_type VARCHAR(16) DEFAULT "PHONE" COMMENT "渠道 PHONE/WEB_CHAT/H5_MESSAGE/VIDEO" AFTER session_id',
    'SELECT "ai_call_queue.channel_type already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_queue' AND column_name = 'profile_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_queue ADD COLUMN profile_id BIGINT(20) DEFAULT NULL COMMENT "统一画像ID" AFTER caller_number',
    'SELECT "ai_call_queue.profile_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE ai_call_queue ADD KEY idx_queue_channel (channel_type, queue_status);
ALTER TABLE ai_call_queue ADD KEY idx_queue_profile (profile_id);

-- -------------------------------------------------------------
-- 3) ai_call_ticket：来源渠道与业务回溯（幂等扩列）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'source_channel');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN source_channel VARCHAR(16) DEFAULT "PHONE" COMMENT "来源渠道 PHONE/WEB_CHAT/H5_MESSAGE/VIDEO/IVR" AFTER record_id',
    'SELECT "ai_call_ticket.source_channel already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'source_biz_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN source_biz_id VARCHAR(64) DEFAULT NULL COMMENT "来源业务ID（chat_session_id / consultation_id 等）" AFTER source_channel',
    'SELECT "ai_call_ticket.source_biz_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'profile_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN profile_id BIGINT(20) DEFAULT NULL COMMENT "统一画像ID" AFTER record_id',
    'SELECT "ai_call_ticket.profile_id already exists, skipped" AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE ai_call_ticket ADD KEY idx_ticket_source (source_channel, source_biz_id);
ALTER TABLE ai_call_ticket ADD KEY idx_ticket_profile (profile_id);

-- -------------------------------------------------------------
-- 4) 工单对话快照表（新增，可选）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_ticket_chat_snapshot (
    snapshot_id      BIGINT(20)   NOT NULL AUTO_INCREMENT,
    ticket_id        BIGINT(20)   NOT NULL COMMENT '工单ID',
    chat_session_id  BIGINT(20)   NOT NULL COMMENT '源图文会话ID',
    msg_count        INT(11)      DEFAULT 0,
    snapshot_text    MEDIUMTEXT   COMMENT '格式化对话文本（发言人 [时间] 内容 逐行）',
    create_by        VARCHAR(64)  DEFAULT '',
    create_time      DATETIME     DEFAULT NULL,
    PRIMARY KEY (snapshot_id),
    UNIQUE KEY uk_snapshot_ticket (ticket_id),
    KEY idx_snapshot_session (chat_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单对话快照（P2-16）';

-- -------------------------------------------------------------
-- 5) ai_channel_identity 字典扩展（WEB 渠道）
-- -------------------------------------------------------------
INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, status, create_by, create_time)
SELECT 'sys_channel_type', '网页咨询', 'WEB_CHAT', 10, '0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'sys_channel_type' AND dict_value = 'WEB_CHAT');

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, status, create_by, create_time)
SELECT 'sys_channel_type', 'H5留言', 'H5_MESSAGE', 11, '0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'sys_channel_type' AND dict_value = 'H5_MESSAGE');

INSERT INTO sys_dict_data (dict_type, dict_label, dict_value, dict_sort, status, create_by, create_time)
SELECT 'sys_channel_type', '视频咨询', 'VIDEO', 12, '0', 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data WHERE dict_type = 'sys_channel_type' AND dict_value = 'VIDEO');
