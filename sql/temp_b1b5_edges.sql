-- 重构 B1B5 流程连线：agentChat 条件分支
DELETE FROM ai_ivr_edge WHERE flow_id=300;
INSERT INTO ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time)
VALUES
(3000, 300, 3000, 3001, '',           NULL,                          1, 'admin', NOW()),
(3001, 300, 3001, 3002, '',           NULL,                          2, 'admin', NOW()),
-- 智能体判定转人工 → 直接转人工节点
(3002, 300, 3002, 3004, '转人工',     '#agentHandoff == true',       3, 'admin', NOW()),
-- 不转人工 → 播报回复后结束
(3003, 300, 3002, 3003, '继续',       NULL,                          4, 'admin', NOW()),
(3004, 300, 3003, 3005, '',           NULL,                          5, 'admin', NOW()),
(3005, 300, 3004, 3005, '',           NULL,                          6, 'admin', NOW());
