-- =====================================================================
-- P3-F1 大表分区与冷热分离 + 归档（DBA 评审脚本）
-- 日期: 2026-09-26
-- 配套应用: DataArchiveTask（data.archive.*，默认关闭+dry-run）
--           归档只读查询 /lawyers/trunk/callArchive（权限 lawyers:trunk:callArchive:*）
-- 前置说明:
--   1) 第一部分【必须执行】——开启话单归档前先建归档表；
--   2) 第三部分【模板】——五张热表按月分区改造，须由 DBA 在维护窗口
--      评估执行（全表重建，建议 pt-online-schema-change 或影子表切换）；
--   3) 保留期默认值仅为应用出厂值，正式执行前必须按当地 12345/12348
--      政务热线数据与录音归档合规要求确认。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、话单归档表 ai_call_record_archive【开启归档前必须执行】
--
-- 结构与热表同构（CREATE TABLE LIKE 复制执行时刻全部列与二级索引），仅三处改造：
--   1) create_time 置 NOT NULL：分区键为 NULL 的行无法按月裁剪；
--   2) 主键改为 (record_id, create_time)：分区表唯一键必须包含分区键；
--      record_id 保留原值，工单/质检等历史引用不断链；
--   3) 按月 RANGE(TO_DAYS) 分区：归档回收 DROP PARTITION 秒级完成。
--
-- ⚠ 同构约束（有意保护）：DataArchiveTask 采用 INSERT ... SELECT * 迁移，
--   列数不一致会直接报错终止而非静默丢列。热表 ai_call_record 之后任何
--   ALTER TABLE（加列/改列）都必须同步执行到归档表，请纳入变更清单流程。
-- ---------------------------------------------------------------------

CREATE TABLE ai_call_record_archive LIKE ai_call_record;

ALTER TABLE ai_call_record_archive
  MODIFY create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（分区键）';

ALTER TABLE ai_call_record_archive
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (record_id, create_time);

-- 分区起始月按"预计最早一批归档数据的月份"设定（默认在线保留 12 个月，
-- 首批归档即系统最早话单所在月）；上限月随时间由 DBA 提前 REORGANIZE pmax 补充。
-- 下例给出 p202501 - p202612 全月分区 + pmax，可按实际数据分布裁剪起始月。
ALTER TABLE ai_call_record_archive
PARTITION BY RANGE (TO_DAYS(create_time)) (
  PARTITION p202501 VALUES LESS THAN (TO_DAYS('2025-02-01')),
  PARTITION p202502 VALUES LESS THAN (TO_DAYS('2025-03-01')),
  PARTITION p202503 VALUES LESS THAN (TO_DAYS('2025-04-01')),
  PARTITION p202504 VALUES LESS THAN (TO_DAYS('2025-05-01')),
  PARTITION p202505 VALUES LESS THAN (TO_DAYS('2025-06-01')),
  PARTITION p202506 VALUES LESS THAN (TO_DAYS('2025-07-01')),
  PARTITION p202507 VALUES LESS THAN (TO_DAYS('2025-08-01')),
  PARTITION p202508 VALUES LESS THAN (TO_DAYS('2025-09-01')),
  PARTITION p202509 VALUES LESS THAN (TO_DAYS('2025-10-01')),
  PARTITION p202510 VALUES LESS THAN (TO_DAYS('2025-11-01')),
  PARTITION p202511 VALUES LESS THAN (TO_DAYS('2025-12-01')),
  PARTITION p202512 VALUES LESS THAN (TO_DAYS('2026-01-01')),
  PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
  PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
  PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
  PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
  PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
  PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
  PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01')),
  PARTITION p202608 VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
  PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
  PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
  PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
  PARTITION pmax    VALUES LESS THAN MAXVALUE
);

-- 归档表二级索引已由 LIKE 复制（含 create_time 索引），查询接口
-- （号码模糊/坐席/状态/来电时间区间）可直接命中；无需额外建索引。

-- ---------------------------------------------------------------------
-- 二、归档表运维操作（DBA 例行，应用不自动做）
-- ---------------------------------------------------------------------
-- 1) 提前补未来月份分区（pmax 中已堆积数据时，REORGANIZE 会在线搬移，
--    务必在低峰窗口执行；理想节奏为每月 25 号前补出下月分区）：
-- ALTER TABLE ai_call_record_archive
--   REORGANIZE PARTITION pmax INTO (
--     PARTITION p202701 VALUES LESS THAN (TO_DAYS('2027-02-01')),
--     PARTITION pmax    VALUES LESS THAN MAXVALUE
--   );
--
-- 2) 归档到期回收（秒级，回收前确认当地合规保留期，建议 ≥36 个月）：
-- ALTER TABLE ai_call_record_archive DROP PARTITION p202501;
--
-- 3) 容量观测：
-- SELECT PARTITION_NAME, TABLE_ROWS, ROUND(DATA_LENGTH/1024/1024) AS data_mb
--   FROM information_schema.PARTITIONS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_call_record_archive';

-- ---------------------------------------------------------------------
-- 三、五张热表按月分区改造模板【仅模板，DBA 维护窗口执行】
--
-- 适用：在线数据量大（亿级/千万级）、保留期明确的热表，用 DROP PARTITION
-- 替代分批 DELETE（N12 清理任务对该表自然命中 0 行，保留开关不冲突）。
-- 通用步骤（每表一致，参考 ai_system_data_lifecycle_20260912.sql 中
-- ai_call_dial_log 的完整示例）：
--   1. SHOW CREATE TABLE 确认现网列/索引（下列主键列为开发库口径）；
--   2. 建分区影子表（_part），主键/唯一键必须包含分区键 create_time；
--   3. INSERT INTO ... SELECT 迁移存量保留期内的数据；
--   4. RENAME TABLE 原子切换（原表改名留档，_part 改正名）；
--   5. DBA 调度每月 DROP PARTITION + 提前 REORGANIZE pmax 补分区。
-- ⚠ 分区表不支持外键（本组表应用层引用，无真实外键约束）；不支持全文索引。
-- ---------------------------------------------------------------------

-- 3.1 ai_call_record：主键 (record_id) → (record_id, create_time)，分区键 create_time
--     （本表若执行分区，N12 话单清理与 DataArchiveTask 迁移均自然命中 0 行，
--      生命周期完全由 DROP PARTITION 承担，录音文件清理职责归 F2）

-- 3.2 ai_sms_log：主键含分区键改造，分区键 create_time（执行前核对该表主键列名）

-- 3.3 ai_quality_inspection：主键 (inspection_id) → (inspection_id, create_time)

-- 3.4 ai_agent_message：主键 (message_id) → (message_id, create_time)
--     （增长最快的话单伴生表之一，建议与 ai_call_record 同批实施）

-- 3.5 ai_call_dial_log：完整示例见 ai_system_data_lifecycle_20260912.sql 第三部分

-- ---------------------------------------------------------------------
-- 四、开启归档的推荐顺序
--   1) DBA 评审后执行第一部分建归档表（分区生效以 SHOW CREATE TABLE 核验）；
--   2) 配置 data.archive.enabled=true 且 dry-run=true（默认），观察 03:40
--      一轮日志中的预计迁移行数与保留期口径；
--   3) 确认无误后 data.archive.dry-run=false 正式迁移（分批限量，可随时关闭）；
--   4) 如需过渡期接管已归档话单的录音文件清理，配置
--      data.archive.delete-recording-file-months（如 36）；F2 对象存储化
--      上线后建议置回 0 改用对象存储生命周期策略。
-- ---------------------------------------------------------------------
