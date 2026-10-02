-- =====================================================================
-- P2-15 呼叫行为风控（call behavior risk）—— 数据库迁移脚本
-- 日期: 2026-10-02
-- 说明:
--   定时任务按号码聚合近期呼叫行为（窗口内来电次数/超短通话占比/夜间来电/
--   未接率），加权评分超阈值自动生成高频置底（ai_hotspot_suppress PHONE）
--   规则并留痕，供班组长复核（确认/忽略）。
--   隐私口径：本表只存主叫号码盲索引（HMAC-SM3，确定性，可分组可关联）与
--   脱敏号码，不落明文/密文；明文号码仅在生成置底规则时内部使用。
--   全部向前兼容、可重复执行。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、呼叫行为风控评估记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_call_risk_record (
    risk_id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    caller_number_index VARCHAR(64)  NOT NULL COMMENT '主叫号码盲索引（HMAC-SM3 128位hex，确定性）',
    masked_number       VARCHAR(32)  DEFAULT NULL COMMENT '脱敏号码（138****5678，展示用）',
    window_hours        INT          NOT NULL DEFAULT 72 COMMENT '评估窗口（小时）',
    call_count          INT          NOT NULL DEFAULT 0 COMMENT '窗口内来电次数',
    short_count         INT          NOT NULL DEFAULT 0 COMMENT '超短通话次数（≤短通话阈值且接通）',
    night_count         INT          NOT NULL DEFAULT 0 COMMENT '夜间来电次数（22:00~次日7:00）',
    missed_count        INT          NOT NULL DEFAULT 0 COMMENT '未接来电次数',
    risk_score          INT          NOT NULL DEFAULT 0 COMMENT '风险评分 0~100（频次40+超短20+夜间20+未接20）',
    score_detail        VARCHAR(200) DEFAULT NULL COMMENT '评分构成说明（频次x+超短y+夜间z+未接w）',
    risk_level          VARCHAR(8)   NOT NULL DEFAULT 'LOW' COMMENT '风险等级 LOW/MEDIUM/HIGH',
    review_status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '复核状态 0待复核 1已确认 2已忽略',
    suppress_id         BIGINT       DEFAULT NULL COMMENT '自动生成的置底规则ID（null=未生成）',
    review_by           VARCHAR(64)  DEFAULT NULL COMMENT '复核人',
    review_time         DATETIME     DEFAULT NULL COMMENT '复核时间',
    review_remark       VARCHAR(500) DEFAULT NULL COMMENT '复核说明',
    create_time         DATETIME     DEFAULT NULL COMMENT '评估时间',
    PRIMARY KEY (risk_id),
    KEY idx_index_review (caller_number_index, review_status),
    KEY idx_review_status (review_status),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='呼叫行为风控评估记录表（P2-15，班组长复核留痕）';

-- =====================================================================
-- 回滚脚本（按需手动执行）
-- =====================================================================
-- DROP TABLE IF EXISTS ai_call_risk_record;
