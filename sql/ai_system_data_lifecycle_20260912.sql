-- =====================================================================
-- 数据生命周期治理（N12）DBA 评审脚本
-- 日期: 2026-09-12
-- 说明: 配合后端定时清理任务 DataLifecycleCleanupTask：
--         1) 为按 create_time 分批删除补齐索引（避免全表扫描与长事务）；
--         2) 提供 ai_call_dial_log 按月 RANGE 分区改造模板（超大型部署，
--            分区裁剪 + DROP PARTITION 替代逐行 DELETE，需停机/窗口期执行）；
--         3) 保留期为应用默认值，正式执行前须按当地 12345/12348 政务热线
--            数据与录音归档合规要求确认。
-- 注意: 本脚本不建议在生产直接整段执行：
--         - MySQL 8.0 的 CREATE INDEX 不支持 IF NOT EXISTS，重复执行会报错，
--           执行前请先用 information_schema 核对索引是否已存在；
--         - 分区改造涉及全表重建，须由 DBA 在维护窗口评估执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、清理任务默认保留策略（应用配置 data.retention.*，此处仅登记口径）
-- ---------------------------------------------------------------------
-- ai_ivr_execution_log   保留 1 个月   IVR 流程执行流水（排障用，增长快）
-- ai_ivr_intention_log   保留 1 个月   IVR 意图识别流水
-- ai_call_dial_log       保留 3 个月   拨号/排队/SIP 挂断明细（增长最快）
-- ai_agent_status_log    保留 6 个月   坐席状态变更流水
-- ai_sms_log             保留 6 个月   短信发送日志
-- ai_video_consult_log   保留 6 个月   视频咨询事件日志
-- ai_call_record         保留 36 个月  话单+录音（含磁盘录音文件联动删除）
-- 任务默认 data.retention.enabled=false、dry-run=true，
-- 先试运行观察日志，再由运维显式开启实际删除。

-- ---------------------------------------------------------------------
-- 二、补索引：清理按 create_time 范围扫描，缺索引会升级为全表扫描
--    执行前核对:
--    SELECT table_name, index_name FROM information_schema.statistics
--    WHERE table_schema = DATABASE()
--      AND table_name IN ('ai_ivr_execution_log','ai_ivr_intention_log',
--                         'ai_call_dial_log','ai_agent_status_log',
--                         'ai_sms_log','ai_video_consult_log','ai_call_record')
--      AND column_name = 'create_time';
-- ---------------------------------------------------------------------

CREATE INDEX idx_ai_ivr_execution_log_ct ON ai_ivr_execution_log(create_time);
CREATE INDEX idx_ai_ivr_intention_log_ct ON ai_ivr_intention_log(create_time);
CREATE INDEX idx_ai_call_dial_log_ct     ON ai_call_dial_log(create_time);
CREATE INDEX idx_ai_agent_status_log_ct  ON ai_agent_status_log(create_time);
CREATE INDEX idx_ai_sms_log_ct           ON ai_sms_log(create_time);
CREATE INDEX idx_ai_video_consult_log_ct ON ai_video_consult_log(create_time);
CREATE INDEX idx_ai_call_record_ct       ON ai_call_record(create_time);

-- ---------------------------------------------------------------------
-- 三（可选，仅超大型部署）：ai_call_dial_log 按月 RANGE 分区模板
--
-- 适用：日增拨号流水数十万行以上、保留期明确（默认 3 个月）的场景。
-- 收益：按月 DROP PARTITION 瞬时释放空间，替代分批 DELETE；
--       查询带 create_time 时自动分区裁剪。
-- 代价：需提前创建未来月份分区（建议另配每月 25 号建下月分区的调度），
--       分区表主键/唯一键必须包含分区键 create_time，外键不被支持
--       （本表无真实外键约束，应用层引用 record_id）。
--
-- 落地步骤（维护窗口）：
--   1. 创建分区新表（结构以 SHOW CREATE TABLE ai_call_dial_log 为准补齐）；
--   2. INSERT INTO ... SELECT 迁移存量近 N 个月数据；
--   3. RENAME TABLE 原子切换；
--   4. 将 data.retention.dial-log-months 对应的清理改为 DROP PARTITION
--      （由 DBA 调度执行，应用 DELETE 任务对该表保留但自然命中 0 行）。
-- ---------------------------------------------------------------------

-- 示例结构（列定义以现网 SHOW CREATE TABLE 为准，此处仅演示分区骨架）：
--
-- CREATE TABLE ai_call_dial_log_part (
--   log_id        BIGINT       NOT NULL,
--   create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
--   PRIMARY KEY (log_id, create_time)
--   -- 其余列/索引与 ai_call_dial_log 保持一致
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
-- PARTITION BY RANGE (TO_DAYS(create_time)) (
--   PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
--   PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
--   PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
--   PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
--   PARTITION pmax    VALUES LESS THAN MAXVALUE
-- );
--
-- 到期回收示例（回收 2026-07 月分区前，先 REORGANIZE pmax 补出后续月份）：
-- ALTER TABLE ai_call_dial_log DROP PARTITION p202607;
