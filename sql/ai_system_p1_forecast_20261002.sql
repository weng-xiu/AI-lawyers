-- =====================================================================
-- P1-9 话务预测与智能排班（call forecast & staffing）—— 数据库迁移脚本
-- 日期: 2026-10-02
-- 说明:
--   1) ai_forecast_calendar 节假日/事件日历：预测时对 hour-of-week 基线剖面
--      做日型调整（HOLIDAY 假日系数 / EVENT 事件系数 / WORKDAY 调休补班）；
--   2) ai_forecast_plan 排班计划确认快照：班组长确认预测/排班建议时存 JSON
--      快照留痕（预测基线与系数随后续数据漂移，快照保证可追溯）。
--   数据源：ai_stat_minute（P3-F3 物化表）metric_key=call_total 按小时聚合。
--   全部向前兼容、可重复执行。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、节假日/事件日历表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_forecast_calendar (
    calendar_id  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日历ID',
    calendar_date DATE        NOT NULL COMMENT '日期',
    day_type     VARCHAR(16)  NOT NULL DEFAULT 'HOLIDAY' COMMENT '日型 HOLIDAY假日 EVENT事件(活动/舆情/政策发布) WORKDAY调休补班',
    day_name     VARCHAR(100) DEFAULT NULL COMMENT '名称（如"国庆节""普法宣传周"）',
    coefficient  DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '话务系数（EVENT 生效；空/1=不调整）',
    status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
    create_by    VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    create_time  DATETIME              DEFAULT NULL COMMENT '创建时间',
    update_by    VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    update_time  DATETIME              DEFAULT NULL COMMENT '更新时间',
    remark       VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (calendar_id),
    UNIQUE KEY uk_calendar_date (calendar_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='话务预测节假日/事件日历表（P1-9）';

-- ---------------------------------------------------------------------
-- 二、排班计划确认快照表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_forecast_plan (
    plan_id       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    begin_date    DATE        NOT NULL COMMENT '预测起始日',
    end_date      DATE        NOT NULL COMMENT '预测截止日',
    total_volume  INT         NOT NULL DEFAULT 0 COMMENT '预测总进线量',
    peak_hour     VARCHAR(16) DEFAULT NULL COMMENT '峰值时段（如 10:00）',
    peak_agents   INT         NOT NULL DEFAULT 0 COMMENT '峰值所需坐席数',
    payload_json  LONGTEXT    COMMENT '逐日逐时预测与排班建议快照 JSON',
    confirmed_by  VARCHAR(64) DEFAULT NULL COMMENT '确认人（班组长）',
    confirmed_time DATETIME   DEFAULT NULL COMMENT '确认时间',
    remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_time   DATETIME    DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (plan_id),
    KEY idx_begin_end (begin_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班计划确认快照表（P1-9）';

-- ---------------------------------------------------------------------
-- 三、内置 2026 年末节假日示例（法定假日按国务院公布口径手工维护，
--     预测默认假日系数 holiday-factor=0.4，可按本地话务规律调整）
-- ---------------------------------------------------------------------
INSERT INTO ai_forecast_calendar (calendar_date, day_type, day_name, coefficient, status, create_by, create_time, remark)
SELECT t.d, t.t, t.n, 1.00, '0', 'system', NOW(), t.r
FROM (
    SELECT '2026-10-01' AS d, 'HOLIDAY' AS t, '国庆节' AS n, '国庆假期（示例，按当地话务规律校准）' AS r
    UNION ALL SELECT '2026-10-02', 'HOLIDAY', '国庆节', '国庆假期'
    UNION ALL SELECT '2026-10-03', 'HOLIDAY', '国庆节', '国庆假期'
    UNION ALL SELECT '2026-10-04', 'HOLIDAY', '国庆节', '国庆假期'
    UNION ALL SELECT '2026-10-05', 'HOLIDAY', '国庆节', '国庆假期'
    UNION ALL SELECT '2026-10-06', 'HOLIDAY', '国庆节', '国庆假期'
    UNION ALL SELECT '2026-10-07', 'HOLIDAY', '国庆节', '国庆假期'
) t
WHERE NOT EXISTS (SELECT 1 FROM ai_forecast_calendar c WHERE c.calendar_date = t.d);

-- =====================================================================
-- 回滚脚本（按需手动执行）
-- =====================================================================
-- DELETE FROM ai_forecast_calendar WHERE create_by = 'system' AND day_type = 'HOLIDAY' AND day_name = '国庆节';
-- DROP TABLE IF EXISTS ai_forecast_plan;
-- DROP TABLE IF EXISTS ai_forecast_calendar;
