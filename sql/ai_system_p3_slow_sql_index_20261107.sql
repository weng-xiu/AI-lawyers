-- =====================================================================
-- P3：慢查询与索引治理 —— 热表复合索引补齐
-- 日期：2026-11-07
-- 对应规划：4.4 表 P3 行 / 5.3 顺位 1
--
-- 依据说明：
--   仓库内未找到 Druid 两周慢 SQL 采集产物（logs/ 仅有 8~9 月旧业务日志，
--   慢 SQL 开关 2026-10-23 才打开且采集数据留在线上），本批按「Mapper 静态
--   查询画像 + 现有索引落差」产出候选索引；上线后仍须用 Druid stat TOP 慢 SQL
--   复核命中率，未达预期的索引按文末回滚语句删除。
--
-- 设计原则：
--   1) 列序按「等值列 → 范围/排序列」，兼顾 WHERE 过滤与 ORDER BY 免排序；
--   2) PII 密文列一律不建普通索引：号码模糊检索继续走 ai_pii_search_token，
--      等值检索走已有 caller_number_index 盲索引（HMAC-SM3 截断）；
--   3) 不删除任何既有索引。新增复合索引与既有单列索引存在左前缀重叠
--      （如 ai_quality_inspection.idx_agent / idx_review / idx_ai_status），
--      待线上验证无回退后再由 DBA 择期 DROP，见文末「可清理项」。
--
-- 执行前提：
--   1) 在目标库（ai-law）连接下执行；
--   2) ai_call_record / ai_call_ticket / ai_quality_inspection 已存在；
--   3) 归档表 ai_call_record_archive 若未建（未开启归档），本脚本自动跳过；
--   4) 大表 ADD INDEX 为在线 DDL（MySQL 8.0 InnoDB 默认 INPLACE + LOCK=NONE），
--      建议低峰窗口执行；DBA 可将同表多条 ADD INDEX 合并为单条 ALTER 以减少
--      表重建次数，本脚本为可重复执行采用逐索引存在性判断。
--
-- 幂等说明：每项均先查 information_schema.statistics，已存在则跳过。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、ai_call_record（话单主表；现有 PK + idx_ai_call_record_ct +
--     idx_ai_call_record_call_uuid + idx_record_number_index）
--     查询画像：selectAiCallRecordList（agent/category/status + call_time 区间，
--     order by call_time desc）、selectRecentCalls、今日话单/坐席负载、
--     getCallStatisticsByDate、selectTodayRecordsByAgent(Overtime)、
--     RecordingReconcileTask（status='1' + end_time 区间）
-- ---------------------------------------------------------------------

-- 1.1 列表默认排序与全表时间区间（ORDER BY call_time DESC / 报表区间）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
      AND index_name = 'idx_call_record_call_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_record ADD INDEX idx_call_record_call_time (call_time)',
    'SELECT ''ai_call_record.idx_call_record_call_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 按坐席筛选 + 时间倒序（列表 agentId、坐席今日话单、坐席效能报表）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
      AND index_name = 'idx_call_record_agent_call_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_record ADD INDEX idx_call_record_agent_call_time (agent_id, call_time)',
    'SELECT ''ai_call_record.idx_call_record_agent_call_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 按状态筛选 + 时间倒序（话单列表 status、未接/已完成视角）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
      AND index_name = 'idx_call_record_status_call_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_record ADD INDEX idx_call_record_status_call_time (status, call_time)',
    'SELECT ''ai_call_record.idx_call_record_status_call_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 按咨询分类筛选 + 时间倒序（列表 consultationCategory）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
      AND index_name = 'idx_call_record_category_call_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_record ADD INDEX idx_call_record_category_call_time (category_id, call_time)',
    'SELECT ''ai_call_record.idx_call_record_category_call_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 录音补传扫描：status='1' + end_time 区间（RecordingReconcileTask）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record'
      AND index_name = 'idx_call_record_status_end_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_record ADD INDEX idx_call_record_status_end_time (status, end_time)',
    'SELECT ''ai_call_record.idx_call_record_status_end_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 二、ai_call_ticket（工单表；现有仅 PK + uk_ticket_no，二级索引为空）
--     查询画像：selectAiCallTicketList（status/assign_user_id/record_id +
--     order by create_time desc）、CallEventProcessor 挂机后按 record_id 查重
--     （每通话一次）、TicketSlaScheduleTask/selectEscalatableTickets（status +
--     due_time）、SlaBoard（create_time 区间）、向量回填扫描
-- ---------------------------------------------------------------------

-- 2.1 列表默认排序 + 报表/SLA 看板 create_time 区间
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket'
      AND index_name = 'idx_ticket_create_time');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_ticket ADD INDEX idx_ticket_create_time (create_time)',
    'SELECT ''ai_call_ticket.idx_ticket_create_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 状态筛选 + 时间倒序（工单列表/工作台待办统计）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket'
      AND index_name = 'idx_ticket_status_create');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_ticket ADD INDEX idx_ticket_status_create (status, create_time)',
    'SELECT ''ai_call_ticket.idx_ticket_status_create already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 处理人筛选 + 时间倒序（“我的工单”列表）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket'
      AND index_name = 'idx_ticket_assign_create');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_ticket ADD INDEX idx_ticket_assign_create (assign_user_id, create_time)',
    'SELECT ''ai_call_ticket.idx_ticket_assign_create already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.4 SLA 超时/临近超时扫描：status in ('0','1') + due_time 范围
--     （selectOvertimePendingTickets / selectEscalatableTickets /
--       SlaBoard.countDueSoon / selectDueSoon）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket'
      AND index_name = 'idx_ticket_status_due');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_ticket ADD INDEX idx_ticket_status_due (status, due_time)',
    'SELECT ''ai_call_ticket.idx_ticket_status_due already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.5 按话单查工单（自动建单幂等检查、详情页反查，每通话至少一次）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket'
      AND index_name = 'idx_ticket_record_id');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_call_ticket ADD INDEX idx_ticket_record_id (record_id)',
    'SELECT ''ai_call_ticket.idx_ticket_record_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 三、ai_quality_inspection（质检表；现有 idx_record/idx_agent/idx_review/
--     idx_ai_status/idx_create_time/idx_quality_appeal_status）
--     查询画像：selectAiQualityInspectionList（source_type/agent_id/ai_status/
--     review_status/risk_warning_id + create_time 区间，order by create_time desc）、
--     selectHighScoreInspections（ai_status='2' + total_score + create_time 区间）
-- ---------------------------------------------------------------------

-- 3.1 AI 质检状态 + 时间倒序（待检/检中/失败列表、高分沉淀扫描）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_quality_inspection'
      AND index_name = 'idx_qi_ai_status_create');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_quality_inspection ADD INDEX idx_qi_ai_status_create (ai_status, create_time)',
    'SELECT ''ai_quality_inspection.idx_qi_ai_status_create already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.2 人工复核状态 + 时间倒序（待复核列表）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_quality_inspection'
      AND index_name = 'idx_qi_review_create');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_quality_inspection ADD INDEX idx_qi_review_create (review_status, create_time)',
    'SELECT ''ai_quality_inspection.idx_qi_review_create already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.3 被检坐席 + 时间倒序（坐席质检历史）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_quality_inspection'
      AND index_name = 'idx_qi_agent_create');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_quality_inspection ADD INDEX idx_qi_agent_create (agent_id, create_time)',
    'SELECT ''ai_quality_inspection.idx_qi_agent_create already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.4 风险预警联动反查（risk_warning_id 等值）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_quality_inspection'
      AND index_name = 'idx_qi_risk_warning');
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE ai_quality_inspection ADD INDEX idx_qi_risk_warning (risk_warning_id)',
    'SELECT ''ai_quality_inspection.idx_qi_risk_warning already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 四、ai_call_record_archive（归档镜像，可选）
--     归档表由 CREATE TABLE ... LIKE 在某一时刻复制热表索引，本批新增索引
--     不会自动同步；selectArchiveList 同样按 agent/category/status +
--     call_time 区间查询并以 call_time 倒序，故按需补齐。
--     表不存在时（未开启归档）自动跳过。
-- ---------------------------------------------------------------------

SET @archive_exists = (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive');

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
      AND index_name = 'idx_archive_call_time');
SET @ddl = IF(@archive_exists = 0,
    'SELECT ''ai_call_record_archive not exists, skipped'' AS info',
    IF(@idx_exists = 0,
       'ALTER TABLE ai_call_record_archive ADD INDEX idx_archive_call_time (call_time)',
       'SELECT ''ai_call_record_archive.idx_archive_call_time already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
      AND index_name = 'idx_archive_agent_call_time');
SET @ddl = IF(@archive_exists = 0,
    'SELECT ''ai_call_record_archive not exists, skipped'' AS info',
    IF(@idx_exists = 0,
       'ALTER TABLE ai_call_record_archive ADD INDEX idx_archive_agent_call_time (agent_id, call_time)',
       'SELECT ''ai_call_record_archive.idx_archive_agent_call_time already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
      AND index_name = 'idx_archive_status_call_time');
SET @ddl = IF(@archive_exists = 0,
    'SELECT ''ai_call_record_archive not exists, skipped'' AS info',
    IF(@idx_exists = 0,
       'ALTER TABLE ai_call_record_archive ADD INDEX idx_archive_status_call_time (status, call_time)',
       'SELECT ''ai_call_record_archive.idx_archive_status_call_time already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_record_archive'
      AND index_name = 'idx_archive_category_call_time');
SET @ddl = IF(@archive_exists = 0,
    'SELECT ''ai_call_record_archive not exists, skipped'' AS info',
    IF(@idx_exists = 0,
       'ALTER TABLE ai_call_record_archive ADD INDEX idx_archive_category_call_time (category_id, call_time)',
       'SELECT ''ai_call_record_archive.idx_archive_category_call_time already exists, skipped'' AS info'));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 五、执行后校验：列出本批索引
-- ---------------------------------------------------------------------
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
  FROM information_schema.statistics
 WHERE table_schema = DATABASE()
   AND index_name IN (
       'idx_call_record_call_time', 'idx_call_record_agent_call_time',
       'idx_call_record_status_call_time', 'idx_call_record_category_call_time',
       'idx_call_record_status_end_time',
       'idx_ticket_create_time', 'idx_ticket_status_create', 'idx_ticket_assign_create',
       'idx_ticket_status_due', 'idx_ticket_record_id',
       'idx_qi_ai_status_create', 'idx_qi_review_create', 'idx_qi_agent_create',
       'idx_qi_risk_warning',
       'idx_archive_call_time', 'idx_archive_agent_call_time',
       'idx_archive_status_call_time', 'idx_archive_category_call_time')
 GROUP BY table_name, index_name
 ORDER BY table_name, index_name;

-- ---------------------------------------------------------------------
-- 六、回滚（如线上 TOP 慢 SQL 复核显示某索引长期不被命中）
-- ---------------------------------------------------------------------
-- ALTER TABLE ai_call_record DROP INDEX idx_call_record_call_time;
-- ALTER TABLE ai_call_record DROP INDEX idx_call_record_agent_call_time;
-- ALTER TABLE ai_call_record DROP INDEX idx_call_record_status_call_time;
-- ALTER TABLE ai_call_record DROP INDEX idx_call_record_category_call_time;
-- ALTER TABLE ai_call_record DROP INDEX idx_call_record_status_end_time;
-- ALTER TABLE ai_call_ticket DROP INDEX idx_ticket_create_time;
-- ALTER TABLE ai_call_ticket DROP INDEX idx_ticket_status_create;
-- ALTER TABLE ai_call_ticket DROP INDEX idx_ticket_assign_create;
-- ALTER TABLE ai_call_ticket DROP INDEX idx_ticket_status_due;
-- ALTER TABLE ai_call_ticket DROP INDEX idx_ticket_record_id;
-- ALTER TABLE ai_quality_inspection DROP INDEX idx_qi_ai_status_create;
-- ALTER TABLE ai_quality_inspection DROP INDEX idx_qi_review_create;
-- ALTER TABLE ai_quality_inspection DROP INDEX idx_qi_agent_create;
-- ALTER TABLE ai_quality_inspection DROP INDEX idx_qi_risk_warning;
-- ALTER TABLE ai_call_record_archive DROP INDEX idx_archive_call_time;
-- ALTER TABLE ai_call_record_archive DROP INDEX idx_archive_agent_call_time;
-- ALTER TABLE ai_call_record_archive DROP INDEX idx_archive_status_call_time;
-- ALTER TABLE ai_call_record_archive DROP INDEX idx_archive_category_call_time;
--
-- ---------------------------------------------------------------------
-- 七、可清理项（本轮不执行，待线上验证后由 DBA 评估）
--   ai_quality_inspection 新增的 (ai_status, create_time) / (review_status, create_time)
--   / (agent_id, create_time) 与既有 idx_ai_status / idx_review / idx_agent 存在
--   左前缀重叠，既有单列索引可在确认无慢 SQL 回退后 DROP，以降低写入索引维护成本。
-- ---------------------------------------------------------------------
