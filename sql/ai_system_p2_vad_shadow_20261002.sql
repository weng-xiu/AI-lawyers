-- =====================================================================
-- P2-12（方案 A）：Silero VAD 影子验证——对比统计落表
-- 日期：2026-10-02
--
-- 影子模式运行时，SileroVadShadowService 每日输出结构化报告日志
-- （logger=VAD_SHADOW_REPORT）。本表用于存储解析后的每日统计，
-- 支撑误打断率/漏打断率/打断响应延迟的趋势分析与阈值调优。
-- =====================================================================

-- -------------------------------------------------------------
-- 1) VAD 影子对比日统计表（新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_vad_shadow_daily (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    stat_date             DATE         NOT NULL COMMENT '统计日期',
    total_frames          BIGINT       NOT NULL DEFAULT 0 COMMENT '总帧数',
    both_voice_frames     BIGINT       NOT NULL DEFAULT 0 COMMENT '双判语音帧数',
    only_legacy_frames    BIGINT       NOT NULL DEFAULT 0 COMMENT '仅旧VAD判语音帧数（潜在漏报）',
    only_silero_frames    BIGINT       NOT NULL DEFAULT 0 COMMENT '仅Silero判语音帧数（潜在误报）',
    both_silent_frames    BIGINT       NOT NULL DEFAULT 0 COMMENT '双判静默帧数',
    legacy_start_count    INT          NOT NULL DEFAULT 0 COMMENT '旧VAD语音起始次数',
    silero_start_count    INT          NOT NULL DEFAULT 0 COMMENT 'Silero语音起始次数',
    legacy_end_count      INT          NOT NULL DEFAULT 0 COMMENT '旧VAD语音结束次数',
    silero_end_count      INT          NOT NULL DEFAULT 0 COMMENT 'Silero语音结束次数',
    false_alarm_rate      DECIMAL(8,6) DEFAULT NULL COMMENT '误打断率（仅Silero判语音/总帧数）',
    miss_rate             DECIMAL(8,6) DEFAULT NULL COMMENT '漏打断率（仅旧VAD判语音/总帧数）',
    avg_latency_diff_ms   DECIMAL(10,2) DEFAULT NULL COMMENT '平均打断响应延迟差（ms，Silero-旧VAD）',
    latency_diff_count    BIGINT       NOT NULL DEFAULT 0 COMMENT '延迟差样本数（多实例加权合并均值用）',
    active_sessions       INT          NOT NULL DEFAULT 0 COMMENT '当日活跃会话数（已结束且有音频帧）',
    create_time           DATETIME     DEFAULT NULL COMMENT '记录创建时间',
    update_time           DATETIME     DEFAULT NULL COMMENT '记录更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stat_date (stat_date),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VAD影子对比日统计（P2-12方案A）';

-- -------------------------------------------------------------
-- 2) VAD 影子会话明细表（可选，用于抽样审计）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_vad_shadow_session (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    session_id            VARCHAR(64)  NOT NULL COMMENT '语音会话ID',
    conn_id               VARCHAR(64)  DEFAULT NULL COMMENT 'WebSocket连接ID',
    sample_rate           INT          NOT NULL DEFAULT 16000 COMMENT '采样率',
    mock_mode             CHAR(1)      NOT NULL DEFAULT '0' COMMENT '是否Mock模式 0否(ONNX) 1是',
    total_frames          INT          NOT NULL DEFAULT 0 COMMENT '本会话总帧数',
    both_voice_frames     INT          NOT NULL DEFAULT 0 COMMENT '双判语音帧数',
    only_legacy_frames    INT          NOT NULL DEFAULT 0 COMMENT '仅旧VAD判语音帧数',
    only_silero_frames    INT          NOT NULL DEFAULT 0 COMMENT '仅Silero判语音帧数',
    false_alarm_rate      DECIMAL(8,6) DEFAULT NULL COMMENT '会话级误打断率',
    miss_rate             DECIMAL(8,6) DEFAULT NULL COMMENT '会话级漏打断率',
    avg_latency_diff_ms   DECIMAL(10,2) DEFAULT NULL COMMENT '会话级平均延迟差(ms)',
    legacy_start_count    INT          NOT NULL DEFAULT 0 COMMENT '旧VAD起始次数',
    silero_start_count    INT          NOT NULL DEFAULT 0 COMMENT 'Silero起始次数',
    create_time      DATETIME     DEFAULT NULL COMMENT '会话开始时间',
    end_time         DATETIME     DEFAULT NULL COMMENT '会话结束时间',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='VAD影子会话明细（P2-12方案A，抽样审计用）';

-- -------------------------------------------------------------
-- 3) 已部署库补列：latency_diff_count（延迟差样本数，加权均值合并用）
-- -------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_vad_shadow_daily' AND column_name = 'latency_diff_count');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_vad_shadow_daily ADD COLUMN latency_diff_count BIGINT NOT NULL DEFAULT 0 COMMENT ''延迟差样本数（多实例加权合并均值用）'' AFTER avg_latency_diff_ms',
    'SELECT ''ai_vad_shadow_daily.latency_diff_count already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
