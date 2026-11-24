-- =====================================================================
-- B6 短信节点验证流程（临时）
-- 流程：start -> sms(排队通知模板) -> hangup
-- 节点配置：templateId=1（排队通知）、phoneVar=callerNumber、params={queuePos:3}
-- 预期渲染内容：【AI法律咨询】您好，您当前排队第3位，请耐心等待。
-- 日期：2026-08-16
-- =====================================================================
INSERT INTO ai_ivr_flow (flow_id, flow_name, flow_code, description, status, version, create_by, create_time)
VALUES (302, 'B6短信节点验证', 'B6_SMS_TEST', '验证IVR短信节点发送与落库', '1', 1, 'admin', NOW())
ON DUPLICATE KEY UPDATE flow_name=VALUES(flow_name);
DELETE FROM ai_ivr_node WHERE flow_id=302;
DELETE FROM ai_ivr_edge WHERE flow_id=302;

INSERT INTO ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, sort_order, create_by, create_time)
VALUES
(3020, 302, 'start',  '开始',         NULL, 1, 'admin', NOW()),
(3021, 302, 'sms',    '发送排队短信', '{"templateId":1,"phoneVar":"callerNumber","params":{"queuePos":"3"}}', 2, 'admin', NOW()),
(3022, 302, 'hangup', '结束',         '{}', 3, 'admin', NOW());

INSERT INTO ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time)
VALUES
(3020, 302, 3020, 3021, '', NULL, 1, 'admin', NOW()),
(3021, 302, 3021, 3022, '', NULL, 2, 'admin', NOW());
