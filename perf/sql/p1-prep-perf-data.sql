-- =====================================================================
-- P1 首轮容量压测 · 数据预置与清理（MySQL 5.7 / 8.0）
-- 关联：perf/jmeter/scenario-a-agents.jmx、scenario-c-quality.jmx
--       perf/jmeter/emit-call-events.ps1
-- 边界：仅用于独立压测库，禁止生产执行
-- =====================================================================
-- 执行方式（脚本内 SET 变量必须在同一会话内生效，勿分段复制到不同连接）：
--   mysql -h <host> -u <user> -p --default-character-set=utf8mb4 <db> < p1-prep-perf-data.sql
--
-- 对齐口径（改档只改下面 3 个变量）：
--   T1 基线：@AGENT_COUNT=100  @RECORD_COUNT=1100
--   T2 目标：@AGENT_COUNT=300  @RECORD_COUNT=3300
--   T3 峰值：@AGENT_COUNT=500  @RECORD_COUNT=6600
--   @RECORD_COUNT = 质检速率(次/分) × 10 分钟 × 1.1
--
-- 前置（缺一不可，否则指标失真）：
--   1) JMeter 场景A 以 agentId=userId=${__threadNum} 签入，本脚本按同规则预置坐席；
--   2) 场景C 以 ${__counter(TRUE,)} 取 record_id（从 1 开始），本脚本预置 record_id=1..N；
--   3) ai.quality.enabled=true、ai.quality.auto-on-hangup=true、ai.quality.sample-rate=1.0；
--   4) 话单 transcript 非空（本脚本已写入），否则 inspect 会走 ASR 且 Mock 返回空导致置失败；
--   5) 每轮压测前重置质检记录（本脚本第 2 段幂等删除），避免命中「已完成幂等跳过」快路径。
--
-- 执行顺序：
--   ① 确认上述配置 → ② 执行本脚本 1~2 段 → ③ 跑场景A/（可选 场景B）
--   → ④（可选）另窗跑 emit-call-events.ps1 → ⑤ 跑场景C → ⑥ 压测后执行第 4 段清理
-- =====================================================================

SET NAMES utf8mb4;

-- ---------- 参数 ----------
SET @AGENT_COUNT  = 100;    -- 场景A 并发坐席，对应 -JtierA
SET @RECORD_COUNT = 1100;   -- 场景C 话单池，对应场景C 线程数 × 10min 速率 × 1.1
SET @CALLER_PREFIX = '138'; -- 合成主叫号码前缀（明文，解密侧无 encp: 前缀时原样返回）

-- 数字序列 1..10000（覆盖 T3 的 6600 与 500 坐席）
DROP TEMPORARY TABLE IF EXISTS tmp_perf_seq;
CREATE TEMPORARY TABLE tmp_perf_seq (seq INT NOT NULL PRIMARY KEY);
INSERT INTO tmp_perf_seq (seq)
SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c
CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d;

-- ---------- 1. 坐席预置（幂等） ----------
-- agent_id = user_id = 序号：与 JMeter 的 agentId/userId=${__threadNum} 一致；
-- 若库内已有同 agent_id 但 user_id 不同，签入会被判「工号已绑定其他账号」返回 -1。
INSERT INTO ai_call_agent_status
    (agent_id, user_id, agent_name, status, call_status, call_mode, login_time, logout_time, create_time, update_time)
SELECT s.seq, s.seq, CONCAT('perf-agent-', s.seq), '0', '0', '0', NULL, NULL, NOW(), NOW()
FROM tmp_perf_seq s
WHERE s.seq <= @AGENT_COUNT
ON DUPLICATE KEY UPDATE
    user_id     = VALUES(user_id),
    agent_name  = VALUES(agent_name),
    status      = '0',
    call_status = '0',
    login_time  = NULL,
    logout_time = NULL,
    update_time = NOW();

-- ---------- 2. 质检话单池预置 + 幂等重置 ----------
-- record_id 与场景C 的 ${__counter(TRUE,)} 一一对应；status=1（已完成）、asr_status=2（已转写）。
INSERT INTO ai_call_record
    (record_id, caller_number, caller_name, call_time, end_time, duration, agent_id,
     status, transcript, asr_status, create_time, update_time)
SELECT s.seq,
       CONCAT(@CALLER_PREFIX, LPAD(s.seq, 8, '0')),
       CONCAT('perf-caller-', s.seq),
       DATE_SUB(NOW(), INTERVAL (s.seq % 1440) MINUTE),
       DATE_SUB(NOW(), INTERVAL (s.seq % 1440) MINUTE) + INTERVAL 60 SECOND,
       60,
       ((s.seq - 1) % @AGENT_COUNT) + 1,
       '1',
       '主叫：您好，我想咨询劳动争议仲裁的时效问题。\n坐席：劳动仲裁申请时效一般为一年，自当事人知道或应当知道权利被侵害之日起计算；拖欠劳动报酬争议在劳动关系存续期间不受一年限制。\n主叫：明白了，谢谢。',
       2,
       NOW(), NOW()
FROM tmp_perf_seq s
WHERE s.seq <= @RECORD_COUNT
ON DUPLICATE KEY UPDATE
    transcript  = VALUES(transcript),
    asr_status  = 2,
    status      = VALUES(status),
    update_time = NOW();

-- 重置质检记录：清掉上一轮 ai_status=2 的行，否则 inspect 命中幂等快路径（接口仍 200，指标虚高）
DELETE FROM ai_quality_inspection
WHERE record_id BETWEEN 1 AND @RECORD_COUNT;

-- ---------- 3. 预置结果校验（应返回 坐席数≈@AGENT_COUNT、话单数≈@RECORD_COUNT、待检=0） ----------
SELECT
    (SELECT COUNT(*) FROM ai_call_agent_status WHERE agent_id BETWEEN 1 AND @AGENT_COUNT) AS agents_ready,
    (SELECT COUNT(*) FROM ai_call_record WHERE record_id BETWEEN 1 AND @RECORD_COUNT AND transcript IS NOT NULL) AS records_ready,
    (SELECT COUNT(*) FROM ai_quality_inspection WHERE record_id BETWEEN 1 AND @RECORD_COUNT) AS inspections_pending;

-- =====================================================================
-- 4. 压测后清理（按需取消注释执行；perf 库可保留数据跳过）
-- =====================================================================
-- DELETE FROM ai_quality_inspection WHERE record_id BETWEEN 1 AND @RECORD_COUNT;
-- DELETE FROM ai_call_record        WHERE record_id BETWEEN 1 AND @RECORD_COUNT;
-- DELETE FROM ai_call_agent_status  WHERE agent_id  BETWEEN 1 AND @AGENT_COUNT;
--
-- 若使用了 emit-call-events.ps1（会话号 perf-<RunId>-<seq>），按号段清理拨号日志：
-- DELETE FROM ai_call_dial_log WHERE call_uuid LIKE 'perf-<RunId>-%';
-- 注意：ai_call_dial_log 已纳入空库基线 deploy/docker/mysql/init/01-init.sql（L306 起），
-- 空库（仅执行 01-init.sql DDL，不导入业务种子）可直接运行本脚本。若压测库导入过含
-- ai_call_agent_status / ai_call_record / ai_quality_inspection 种子的全量脚本，本脚本
-- 1..100、1..1100 自增主键会与种子行撞号（ON DUPLICATE 覆盖），清理 DELETE 还会删掉被覆盖
-- 的种子行；因此压测库必须独立且仅做 DDL 初始化。列名已与 01-init.sql 逐一核对一致。

DROP TEMPORARY TABLE IF EXISTS tmp_perf_seq;
