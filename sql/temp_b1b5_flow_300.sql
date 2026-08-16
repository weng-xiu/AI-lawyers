-- B1B5 联动测试流程节点
INSERT INTO ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, sort_order, create_by, create_time)
VALUES
(3000, 300, 'start',    '开始',              NULL,
 1, 'admin', NOW()),
(3001, 300, 'answer',   '收集问题',          '{"jqrask":"请问您遇到了什么法律问题？"}',
 2, 'admin', NOW()),
(3002, 300, 'agentChat','智能体对话',        '{"agentId":100,"welcome":"","useKnowledge":true,"topK":3,"maxTurns":5,"resultVar":"agentReply","handoffVar":"agentHandoff"}',
 3, 'admin', NOW()),
(3003, 300, 'say',      '播报回复',          '{"text":"${agentReply}"}',
 4, 'admin', NOW()),
(3004, 300, 'agent',    '转人工(智能分配)',  '{"dispatchMode":"dispatch","categoryVar":"agentCategoryId","enqueueIfNoAgent":true}',
 5, 'admin', NOW()),
(3005, 300, 'hangup',   '结束',              '{}',
 6, 'admin', NOW());

-- 连线：线性流转；agentChat 后无条件到 say（agentChat 内部根据 handoff 已写变量）
-- 注意：say→agent→hangup 线性连接
INSERT INTO ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time)
VALUES
(3000, 300, 3000, 3001, '', NULL, 1, 'admin', NOW()),
(3001, 300, 3001, 3002, '', NULL, 2, 'admin', NOW()),
(3002, 300, 3002, 3003, '', NULL, 3, 'admin', NOW()),
(3003, 300, 3003, 3004, '', NULL, 4, 'admin', NOW()),
(3004, 300, 3004, 3005, '', NULL, 5, 'admin', NOW());
