-- =====================================================================
-- P1-6 续作（V2.72）：工单流转 DSL 扩展——时限（sla_hours）+ 表单（form_schema）
-- 日期：2026-10-03
--
-- 在 ai_ticket_flow_definition 上扩两列，使配置化状态机具备：
--   1) 时限：动作触发后按 sla_hours 重算工单 due_time（SLA 节点化，而非仅建单时算一次）；
--   2) 表单：form_schema JSON 声明该动作必填字段，执行前由状态机校验，缺失即拒绝。
-- 保持最小改动：两列均 nullable，旧规则不受影响；幂等 ALTER。
-- =====================================================================

-- 时限（小时）：null 表示该动作不重算 SLA；0 表示清空 due_time（归档终态）
ALTER TABLE ai_ticket_flow_definition
    ADD COLUMN IF NOT EXISTS sla_hours DECIMAL(8,2) DEFAULT NULL COMMENT '动作触发后SLA时限(小时)，空=不重算，0=清空截止时间';

-- 表单 schema：JSON，当前结构 {"required":["fieldA","fieldB"]}，可扩展校验规则
ALTER TABLE ai_ticket_flow_definition
    ADD COLUMN IF NOT EXISTS form_schema TEXT DEFAULT NULL COMMENT '该动作必填表单JSON，如{"required":["orgId","remark"]}';

-- 幂等 seed：HOTLINE 流各动作时限与表单
--   start(0→1)：受理后给 24h 处理时限；必填 processContent
--   transfer(0→0 / 1→1)：不重算时限；必填 orgId（外派机构）
--   complete(1→2)：办结不重算时限；无必填（processContent 由前端附带回填）
--   archive(2→3)：归档清空截止时间；无必填
UPDATE ai_ticket_flow_definition
   SET sla_hours = CASE action_code
                       WHEN 'start'    THEN 24.00
                       WHEN 'transfer' THEN NULL
                       WHEN 'complete' THEN NULL
                       WHEN 'archive'  THEN 0.00
                   END,
       form_schema = CASE action_code
                         WHEN 'start'    THEN '{"required":["processContent"]}'
                         WHEN 'transfer' THEN '{"required":["orgId"]}'
                         ELSE NULL
                     END
 WHERE flow_code = 'HOTLINE';
