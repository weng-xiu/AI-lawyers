-- ----------------------------------------------------------------------
-- SmartCall 节点演示流程（2026-08-15）
-- 覆盖新落地节点：answer / received / sentiment / extract / child / variable / script / service
-- 幂等脚本：重复执行会先删除 flow_id in (200, 201) 的旧数据
-- ----------------------------------------------------------------------
SET NAMES utf8mb4;

-- ----------------------------
-- 1、演示主流程 + 子流程
-- ----------------------------
delete from ai_ivr_flow where flow_id in (200, 201);
insert into ai_ivr_flow (flow_id, flow_name, flow_code, description, status, version, category, is_default, create_by, create_time, remark) values
(200, 'SmartCall节点演示主流程', 'SMARTCALL_DEMO', '演示语音收声、DTMF收号、情绪分析、信息抽取、子流程、变量赋值等SmartCall节点', '1', 1, 'demo', '0', 'admin', sysdate(), 'SmartCall设计融合演示'),
(201, 'SmartCall节点演示子流程', 'SMARTCALL_DEMO_CHILD', '被主流程 child 节点调用的子流程', '1', 1, 'demo', '0', 'admin', sysdate(), '子流程复用演示');

-- ----------------------------
-- 2、主流程节点（flow_id=200）
-- ----------------------------
delete from ai_ivr_node where flow_id in (200, 201);
insert into ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, position_x, position_y, sort_order, create_by, create_time, remark) values
(200, 200, 'start',      '开始',       null, 80,  80,  0, 'admin', sysdate(), '流程起点'),
(201, 200, 'say',        '欢迎语',     '{"text":"您好，欢迎来电，主叫号码${callerNumber}。"}', 260, 80, 1, 'admin', sysdate(), '支持SpEL模板'),
(202, 200, 'received',   '收工单号',   '{"jqrask":"请输入工单编号，按#号结束","endKey":"#","maxDigits":10}', 440, 80, 2, 'admin', sysdate(), 'DTMF收号'),
(203, 200, 'answer',     '收集问题',   '{"jqrask":"请问您遇到了什么法律问题？","silencePrompt":true,"silenceDuration":3,"silenceSay":"我没有听清，请您再说一遍"}', 620, 80, 3, 'admin', sysdate(), 'ASR语音收声'),
(204, 200, 'sentiment',  '情绪分析',   '{"text":"${lastInput}"}', 800, 80, 4, 'admin', sysdate(), '写入sentiment变量'),
(205, 200, 'extract',    '信息抽取',   '{"text":"${lastInput}","fields":[{"name":"userName","desc":"当事人姓名"},{"name":"address","desc":"涉及地址"}],"resultVar":"extractResult"}', 980, 80, 5, 'admin', sysdate(), 'LLM结构化抽取'),
(206, 200, 'child',      '调用子流程', '{"flowCode":"SMARTCALL_DEMO_CHILD"}', 1160, 80, 6, 'admin', sysdate(), '子流程复用'),
(207, 200, 'condition',  '情绪分支',   '{}', 1340, 80, 7, 'admin', sysdate(), '按sentiment路由'),
(208, 200, 'agent',      '转人工',     '{"queue":"complaint"}', 1520, 80, 8, 'admin', sysdate(), '负面情绪转人工'),
(209, 200, 'say',        '结束语',     '{"text":"感谢您的来电，再见。"}', 1520, 220, 9, 'admin', sysdate(), '播报结束'),
(210, 200, 'hangup',     '挂断',       null, 1700, 220, 10, 'admin', sysdate(), '结束通话');

-- ----------------------------
-- 3、主流程连线（flow_id=200）
-- ----------------------------
delete from ai_ivr_edge where flow_id in (200, 201);
insert into ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time) values
(200, 200, 200, 201, null, null, 0, 'admin', sysdate()),
(201, 200, 201, 202, null, null, 0, 'admin', sysdate()),
(202, 200, 202, 203, null, null, 0, 'admin', sysdate()),
(203, 200, 203, 204, null, null, 0, 'admin', sysdate()),
(204, 200, 204, 205, null, null, 0, 'admin', sysdate()),
(205, 200, 205, 206, null, null, 0, 'admin', sysdate()),
(206, 200, 206, 207, null, null, 0, 'admin', sysdate()),
(207, 200, 207, 208, '负面', 'sentiment == ''negative''', 0, 'admin', sysdate()),
(208, 200, 207, 209, '默认', null, 1, 'admin', sysdate()),
(209, 200, 209, 210, null, null, 0, 'admin', sysdate());

-- ----------------------------
-- 4、子流程节点与连线（flow_id=201）
-- ----------------------------
insert into ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, position_x, position_y, sort_order, create_by, create_time, remark) values
(211, 201, 'start',    '子流程开始', null, 80,  80, 0, 'admin', sysdate(), '子流程起点'),
(212, 201, 'variable', '写标记变量', '{"variables":[{"key":"childMark","val":"CHILD-OK"}]}', 260, 80, 1, 'admin', sysdate(), '变量赋值'),
(213, 201, 'script',   '拼接工单号', '{"scriptType":"js","script":"var no = String(dtmf);\n''ticket='' + no;","resultVar":"ticketNo"}', 440, 80, 2, 'admin', sysdate(), 'JS脚本'),
(214, 201, 'say',      '子流程播报', '{"text":"我们已记录您的诉求，工单号为${dtmf}。"}', 620, 80, 3, 'admin', sysdate(), '引用父流程变量'),
(215, 201, 'hangup',   '子流程结束', null, 800, 80, 4, 'admin', sysdate(), '结束子流程');

insert into ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time) values
(210, 201, 211, 212, null, null, 0, 'admin', sysdate()),
(211, 201, 212, 213, null, null, 0, 'admin', sysdate()),
(212, 201, 213, 214, null, null, 0, 'admin', sysdate()),
(213, 201, 214, 215, null, null, 0, 'admin', sysdate());
