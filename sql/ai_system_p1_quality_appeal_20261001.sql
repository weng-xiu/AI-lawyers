-- =====================================================================
-- P1-7（V2.59）：质检结果申诉闭环——坐席申诉 + 班组长复核改分留痕
-- 日期：2026-10-01
--
-- appeal_status：0未申诉 1待复核 2维持原判 3申诉成立已改分
-- 原始 AI 总分不因申诉覆盖（total_score 不动），改分写入 adjusted_score，全程留痕。
-- =====================================================================

ALTER TABLE ai_quality_inspection
    ADD COLUMN appeal_status CHAR(1) NULL DEFAULT '0' COMMENT '申诉状态 0未申诉 1待复核 2维持 3已改分' AFTER risk_warning_id,
    ADD COLUMN appeal_reason VARCHAR(500) NULL COMMENT '坐席申诉理由' AFTER appeal_status,
    ADD COLUMN appeal_time DATETIME NULL COMMENT '申诉时间' AFTER appeal_reason,
    ADD COLUMN appeal_review_remark VARCHAR(500) NULL COMMENT '班组长申诉复核说明' AFTER appeal_time,
    ADD COLUMN adjusted_score DECIMAL(5,1) NULL COMMENT '申诉成立后的调整分数（NULL=未调整）' AFTER appeal_review_remark,
    ADD INDEX idx_quality_appeal_status (appeal_status);
