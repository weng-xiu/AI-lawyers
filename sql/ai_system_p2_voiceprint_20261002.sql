-- =====================================================================
-- P2-15（V2.61）：声纹与来电人风控——声纹聚类 + 骚扰识别
-- 日期：2026-10-02
--
-- 声纹 Embedding SM4-GCM 密文化存储（enc2: 前缀），LSH 哈希用于预筛选。
-- 骚扰规则可配、可自动执行、可人工复核。
-- =====================================================================

-- -------------------------------------------------------------
-- 1) 声纹特征 Embedding 表（新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_voiceprint_embedding (
    embedding_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '声纹ID',
    record_id         BIGINT       NOT NULL COMMENT '关联通话记录ID（ai_call_record）',
    profile_id        BIGINT       DEFAULT NULL COMMENT '关联来电档案ID（ai_caller_profile）',
    voice_cluster_id  BIGINT       DEFAULT NULL COMMENT '声纹聚类簇ID',
    embedding_cipher  VARCHAR(4096) NOT NULL COMMENT '512-d Embedding SM4-GCM密文（enc2:前缀，JSON数组格式）',
    embedding_hash    VARCHAR(64)  NOT NULL COMMENT 'Embedding 局部敏感哈希(LSH) 16段 hex，用于相似性预筛选',
    similarity_conf   DECIMAL(5,4) DEFAULT NULL COMMENT '最近一次聚类匹配置信度',
    extractor_model   VARCHAR(50)  DEFAULT 'ecapa-tdnn-512' COMMENT '提取模型版本',
    vad_start_ms      INT          DEFAULT NULL COMMENT 'VAD有效语音起始毫秒',
    vad_end_ms        INT          DEFAULT NULL COMMENT 'VAD有效语音结束毫秒',
    quality_score     DECIMAL(4,2) DEFAULT NULL COMMENT '音频质量评分 0-100（信噪比/时长）',
    status            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0有效 1低质量 2已删除(合规擦除)',
    delete_reason     VARCHAR(100) DEFAULT NULL COMMENT '删除原因（合规到期/用户要求/误标记）',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (embedding_id),
    KEY idx_record_id (record_id),
    KEY idx_profile_id (profile_id),
    KEY idx_cluster_id (voice_cluster_id),
    KEY idx_embedding_hash (embedding_hash),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹特征Embedding（P2-15）';

-- -------------------------------------------------------------
-- 2) 声纹聚类簇表（新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_voice_cluster (
    cluster_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '聚类簇ID',
    cluster_center    VARCHAR(4096) DEFAULT NULL COMMENT '簇中心Embedding SM4-GCM密文（enc2:）',
    cluster_hash      VARCHAR(64)  DEFAULT NULL COMMENT '簇中心LSH哈希（快速预匹配）',
    profile_count     INT          NOT NULL DEFAULT 0 COMMENT '关联档案数',
    call_count        INT          NOT NULL DEFAULT 0 COMMENT '累计通话次数',
    first_call_time   DATETIME     DEFAULT NULL COMMENT '首次出现时间',
    last_call_time    DATETIME     DEFAULT NULL COMMENT '最近出现时间',
    merge_to          BIGINT       DEFAULT NULL COMMENT '合并目标簇ID（级联合并时指向新簇）',
    status            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0活跃 1已合并 2已归档 9已删除',
    retention_until   DATE         DEFAULT NULL COMMENT '合规保留截止日期（PII到期自动删除）',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (cluster_id),
    KEY idx_cluster_hash (cluster_hash),
    KEY idx_status_time (status, last_call_time),
    KEY idx_retention (retention_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹聚类簇（P2-15）';

-- -------------------------------------------------------------
-- 3) 聚类-档案映射表（新增，多对多）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_voice_cluster_profile (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    cluster_id        BIGINT       NOT NULL COMMENT '聚类簇ID',
    profile_id        BIGINT       NOT NULL COMMENT '档案ID',
    bind_type         VARCHAR(16)  NOT NULL DEFAULT 'AUTO' COMMENT '绑定类型 AUTO自动匹配 MANUAL人工确认 MERGE档案合并',
    confidence        DECIMAL(5,4) DEFAULT NULL COMMENT '绑定置信度',
    is_primary        CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否主档案 0否 1是（簇内优先展示）',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cluster_profile (cluster_id, profile_id),
    KEY idx_profile_id (profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='声纹聚类-档案映射（P2-15）';

-- -------------------------------------------------------------
-- 4) 骚扰行为识别规则配置表（新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_harass_rule (
    rule_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    rule_code         VARCHAR(30)  NOT NULL COMMENT '规则编码（如 HARASS-001）',
    rule_name         VARCHAR(100) NOT NULL COMMENT '规则名称',
    rule_type         VARCHAR(20)  NOT NULL DEFAULT 'FREQUENCY' COMMENT '规则类型 FREQUENCY频次 DURATION时长 KEYWORD关键词 VOICE声纹',
    time_window_sec   INT          NOT NULL DEFAULT 300 COMMENT '统计时间窗（秒）',
    threshold_count   INT          NOT NULL DEFAULT 3 COMMENT '触发阈值（次数）',
    threshold_duration INT         DEFAULT NULL COMMENT '通话时长阈值（秒，<该值计入）',
    match_scope       VARCHAR(20)  NOT NULL DEFAULT 'PHONE' COMMENT '作用域 PHONE号码 VOICE_CLUSTER声纹簇',
    action_type       VARCHAR(16)  NOT NULL DEFAULT 'PRIORITY' COMMENT '处置动作 PRIORITY置底 REJECT拦截 BLACKLIST黑名单',
    auto_execute      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否自动执行 0否 1是',
    priority_level    INT          NOT NULL DEFAULT -100 COMMENT '置底优先级（负数）',
    effective_start   DATETIME     DEFAULT NULL COMMENT '生效开始',
    effective_end     DATETIME     DEFAULT NULL COMMENT '生效结束',
    status            CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
    create_by         VARCHAR(64)  DEFAULT '' COMMENT '创建者',
    create_time       DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by         VARCHAR(64)  DEFAULT '' COMMENT '更新者',
    update_time       DATETIME     DEFAULT NULL COMMENT '更新时间',
    remark            VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_rule_code (rule_code),
    KEY idx_type_status (rule_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骚扰行为识别规则（P2-15）';

-- 默认规则 seed（幂等，auto_execute=0 默认不自动执行，观察一周后逐步开启）
INSERT INTO ai_harass_rule
    (rule_code, rule_name, rule_type, time_window_sec, threshold_count, threshold_duration,
     match_scope, action_type, auto_execute, priority_level, status, create_by)
VALUES
    ('HARASS-001', '高频短时呼叫', 'FREQUENCY', 300, 5, 15, 'PHONE', 'PRIORITY', '0', -200, '0', 'system'),
    ('HARASS-002', '极短骚扰判定', 'DURATION', 600, 3, 3, 'PHONE', 'BLACKLIST', '0', -999, '0', 'system'),
    ('HARASS-003', '关键词辱骂', 'KEYWORD', 300, 1, NULL, 'PHONE', 'BLACKLIST', '0', -999, '0', 'system'),
    ('HARASS-004', '声纹簇异常频繁', 'FREQUENCY', 1800, 8, 20, 'VOICE_CLUSTER', 'PRIORITY', '0', -150, '0', 'system'),
    ('HARASS-005', '已拉黑号码重拨', 'FREQUENCY', 86400, 1, NULL, 'PHONE', 'REJECT', '0', -999, '0', 'system')
ON DUPLICATE KEY UPDATE rule_name = VALUES(rule_name);

-- -------------------------------------------------------------
-- 5) 骚扰行为命中日志表（新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_harass_detect_log (
    log_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    rule_id           BIGINT       NOT NULL COMMENT '命中规则ID',
    rule_code         VARCHAR(30)  DEFAULT NULL COMMENT '规则编码冗余',
    match_scope       VARCHAR(20)  NOT NULL COMMENT '作用域 PHONE/VOICE_CLUSTER',
    match_value       VARCHAR(128) NOT NULL COMMENT '命中值（号码或cluster_id）',
    profile_id        BIGINT       DEFAULT NULL COMMENT '关联档案ID',
    voice_cluster_id  BIGINT       DEFAULT NULL COMMENT '关联声纹簇ID',
    trigger_count     INT          NOT NULL COMMENT '实际触发次数',
    action_type       VARCHAR(16)  NOT NULL COMMENT '处置动作',
    action_result     VARCHAR(16)  DEFAULT 'EXECUTED' COMMENT '执行结果 EXECUTED已执行 PENDING待复核 REJECTED已撤销',
    channel_uuid      VARCHAR(64)  DEFAULT NULL COMMENT '通道UUID',
    record_ids        VARCHAR(500) DEFAULT NULL COMMENT '关联record_id列表（逗号分隔）',
    create_time       DATETIME     DEFAULT NULL COMMENT '命中时间',
    audit_by          VARCHAR(64)  DEFAULT NULL COMMENT '复核人',
    audit_time        DATETIME     DEFAULT NULL COMMENT '复核时间',
    audit_opinion     VARCHAR(500) DEFAULT NULL COMMENT '复核意见',
    PRIMARY KEY (log_id),
    KEY idx_rule_time (rule_id, create_time),
    KEY idx_match_value (match_scope, match_value, create_time),
    KEY idx_cluster (voice_cluster_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骚扰行为识别命中日志（P2-15）';

-- -------------------------------------------------------------
-- 6) 热点抑制表扩展（支持声纹簇匹配）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_hotspot_suppress' AND column_name = 'match_scope');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_hotspot_suppress ADD COLUMN match_scope VARCHAR(20) NOT NULL DEFAULT ''PHONE'' COMMENT ''匹配作用域 PHONE号码 KEYWORD关键词 VOICE_CLUSTER声纹簇'' AFTER match_type',
    'SELECT ''ai_hotspot_suppress.match_scope already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_hotspot_suppress' AND column_name = 'voice_cluster_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_hotspot_suppress ADD COLUMN voice_cluster_id BIGINT DEFAULT NULL COMMENT ''关联声纹簇ID（match_scope=VOICE_CLUSTER时）'' AFTER match_value',
    'SELECT ''ai_hotspot_suppress.voice_cluster_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 7) 来电档案表扩展（声纹簇关联）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_caller_profile' AND column_name = 'primary_voice_cluster_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_caller_profile ADD COLUMN primary_voice_cluster_id BIGINT DEFAULT NULL COMMENT ''主声纹聚类簇ID（P2-15）'' AFTER care_mode',
    'SELECT ''ai_caller_profile.primary_voice_cluster_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------
-- 8) 通话记录表扩展（声纹提取标记）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record' AND column_name = 'voiceprint_status');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_record ADD COLUMN voiceprint_status CHAR(1) DEFAULT ''0'' COMMENT ''声纹提取状态 0未提取 1已提取 2提取失败 3低质量'' AFTER asr_status',
    'SELECT ''ai_call_record.voiceprint_status already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
