-- ----------------------------
-- IVR 执行引擎种子数据（2026-08-15）
-- 为默认流程 DEFAULT_LAWYER 补齐可执行节点/连线，并补充外呼演示号码
-- ----------------------------

-- ----------------------------
-- 1、默认流程节点（flow_id=100）
-- ----------------------------
delete from ai_ivr_node where flow_id = 100;
insert into ai_ivr_node (node_id, flow_id, node_type, node_name, node_config, position_x, position_y, sort_order, create_by, create_time, remark) values
(100, 100, 'start',   '开始',       null, 80,  80,  0, 'admin', sysdate(), '流程起点'),
(101, 100, 'say',     '欢迎语',     '{"text":"您好，欢迎拨打12348公共法律服务热线。法律咨询请按1，转人工请按0。"}', 260, 80, 1, 'admin', sysdate(), '语音播报'),
(102, 100, 'menu',    '功能选择',   '{"prompt":"请选择服务"}', 440, 80, 2, 'admin', sysdate(), '按键导航'),
(103, 100, 'intention','法律意图识别', '{}', 620, 80, 3, 'admin', sysdate(), '正则+AI双引擎识别'),
(104, 100, 'condition','意图分类分支', '{}', 800, 80, 4, 'admin', sysdate(), '按意图路由'),
(105, 100, 'agent',   '转人工坐席', '{"agentId":0,"agentExtension":""}', 980, 80, 5, 'admin', sysdate(), '转接人工'),
(106, 100, 'hangup',  '挂断',       null, 1160, 80, 6, 'admin', sysdate(), '结束通话'),
(107, 100, 'say',     '咨询提示语', '{"text":"请您简要描述您的法律问题，我们将为您匹配专业律师。"}', 980, 220, 7, 'admin', sysdate(), '意图识别后引导');

-- ----------------------------
-- 2、默认流程连线（flow_id=100）
-- ----------------------------
delete from ai_ivr_edge where flow_id = 100;
insert into ai_ivr_edge (edge_id, flow_id, source_node_id, target_node_id, edge_label, condition_expr, sort_order, create_by, create_time) values
(100, 100, 100, 101, null, null, 0, 'admin', sysdate()),
(101, 100, 101, 102, null, null, 0, 'admin', sysdate()),
(102, 100, 102, 103, '1',  null, 0, 'admin', sysdate()),
(103, 100, 102, 105, '0',  null, 1, 'admin', sysdate()),
(104, 100, 103, 104, null, null, 0, 'admin', sysdate()),
(105, 100, 104, 105, null, 'matchedIntention == ''TRANSFER_AGENT''', 0, 'admin', sysdate()),
(106, 100, 104, 107, null, null, 1, 'admin', sysdate()),
(107, 100, 107, 106, null, null, 0, 'admin', sysdate());

-- ----------------------------
-- 3、外呼演示号码（task_id=100 民法典宣传回访任务）
--    每行 callee_params.input 作为 IVR 意图识别节点的模拟语音输入
-- ----------------------------
delete from ai_outbound_callee where task_id = 100;
insert into ai_outbound_callee (callee_id, task_id, callee_number, callee_name, callee_gender, callee_age, callee_address, callee_params, call_status, create_by, create_time, remark) values
(1001, 100, '13800000001', '张三', '0', 35, '上海市浦东新区', '{"input":"我签的合同对方违约了，怎么办"}', '0', 'admin', sysdate(), '演示号码-合同纠纷'),
(1002, 100, '13800000002', '李四', '1', 42, '上海市徐汇区', '{"input":"我想咨询离婚和财产分割的问题"}', '0', 'admin', sysdate(), '演示号码-婚姻家庭'),
(1003, 100, '13800000003', '王五', '0', 28, '上海市静安区', '{"input":"公司拖欠我三个月工资，我想申请劳动仲裁"}', '0', 'admin', sysdate(), '演示号码-劳动争议');

-- 任务号码数与演示数据对齐
update ai_outbound_task set total_count = 3, completed_count = 0, answered_count = 0, failed_count = 0, no_answer_count = 0 where task_id = 100;
