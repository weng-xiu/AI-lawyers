-- B5 分类→技能组自动分发验证流程（start -> agent(智能分配按category) -> hangup）
INSERT INTO ai_ivr_flow (flow_id, flow_name, flow_code, description, status, version, create_by, create_time)
VALUES (301, 'B5分类分发验证', 'B5_DISPATCH_TEST', '验证按agentCategoryId自动匹配技能组', '1', 1, 'admin', NOW())
ON DUPLICATE KEY UPDATE flow_name=VALUES(flow_name);
DELETE FROM ai_ivr_node WHERE flow_id=301;
DELETE FROM ai_ivr_edge WHERE flow_id=301;

INSERT INTO ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, sort_order, create_by, create_time)
VALUES
(3010, 301, 'start',  '开始',             NULL, 1, 'admin', NOW()),
(3011, 301, 'agent',  '智能分配(按分类)', '{"dispatchMode":"dispatch","categoryVar":"agentCategoryId","enqueueIfNoAgent":true}', 2, 'admin', NOW()),
(3012, 301, 'hangup', '结束',             '{}', 3, 'admin', NOW());

INSERT INTO ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time)
VALUES
(3010, 301, 3010, 3011, '', NULL, 1, 'admin', NOW()),
(3011, 301, 3011, 3012, '', NULL, 2, 'admin', NOW());
