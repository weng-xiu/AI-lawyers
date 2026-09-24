-- =====================================================================
-- P3-F3：分钟级物化统计表 ai_stat_minute
-- 日期: 2026-09-25
-- 说明: 落地物化表（stat_time/dimension/metric_key/metric_value），
--       大屏与 D6 报表后续切此表，消除高峰实时聚合压力。
--       按月 RANGE 分区便于归档清理；脚本幂等可重复执行。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_stat_minute (
    stat_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    stat_time    DATETIME     NOT NULL COMMENT '统计时间（分钟级，如 2026-09-25 10:31:00）',
    dimension    VARCHAR(64)  NOT NULL DEFAULT 'ALL' COMMENT '统计维度（ALL=全局；后续可扩展 skill:xx / agent:xx / line:xx）',
    metric_key   VARCHAR(64)  NOT NULL COMMENT '指标键（call_total/call_answered/call_missed/ticket_total/ticket_closed/...）',
    metric_value DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '指标值',
    create_time  DATETIME     DEFAULT NULL COMMENT '写入时间',
    PRIMARY KEY (stat_id, stat_time),
    UNIQUE KEY uk_stat (stat_time, dimension, metric_key),
    KEY idx_stat_key_time (metric_key, stat_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分钟级物化统计表（P3-F3）'
PARTITION BY RANGE (TO_DAYS(stat_time)) (
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION p_max VALUES LESS THAN MAXVALUE
);

-- 说明：主键带 stat_time 以满足分区表唯一键必须包含分区列的约束；
-- uk_stat 保证同一 (stat_time, dimension, metric_key) 幂等覆盖写（INSERT ... ON DUPLICATE KEY UPDATE）。
