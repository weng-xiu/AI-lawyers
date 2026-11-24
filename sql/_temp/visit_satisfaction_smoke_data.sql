-- F5 回访满意度看板 接口冒烟测试数据（临时，验证后由 visit_satisfaction_smoke_cleanup.sql 删除）
-- 统一用近 3 天日期，前缀 F5SMK / F5EV / F5RV，避开 7-8 月历史种子
SET @now := NOW();

-- ① 电话回访：近 3 天 8 条，覆盖 4 档满意度 + 6 类归因关键词，含回访员维度（admin/张三）
INSERT INTO ai_callback
(callback_id, ledger_id, caller_number, caller_name, visit_time, visit_by, visit_by_id, satisfaction, visit_opinion, visit_result, status, create_time)
VALUES
(910001, NULL, '13800000001', 'F5冒烟-非常满意', DATE_SUB(@now, INTERVAL 1 DAY), 'admin', 1, '1', '律师解答很专业，问题解决了，感谢', '已解决', '1', DATE_SUB(@now, INTERVAL 1 DAY)),
(910002, NULL, '13800000002', 'F5冒烟-满意',     DATE_SUB(@now, INTERVAL 1 DAY), 'admin', 1, '2', '服务不错，解答清晰', '已解决', '1', DATE_SUB(@now, INTERVAL 1 DAY)),
(910003, NULL, '13800000003', 'F5冒烟-未解决',   DATE_SUB(@now, INTERVAL 2 DAY), 'admin', 1, '4', '打了好几次打不通，等待太久没人接，问题也没解决，很失望', '未解决', '1', DATE_SUB(@now, INTERVAL 2 DAY)),
(910004, NULL, '13800000004', 'F5冒烟-态度',     DATE_SUB(@now, INTERVAL 2 DAY), '张三', 2, '4', '坐席态度很差，语气生硬不耐烦，还推诿说不归他管', '未解决', '1', DATE_SUB(@now, INTERVAL 2 DAY)),
(910005, NULL, '13800000005', 'F5冒烟-流程',     DATE_SUB(@now, INTERVAL 3 DAY), '张三', 2, '3', '流程太繁琐，被转来转去反复让我交材料，手续麻烦', '跟进中', '1', DATE_SUB(@now, INTERVAL 3 DAY)),
(910006, NULL, '13800000006', 'F5冒烟-未落实',   DATE_SUB(@now, INTERVAL 3 DAY), 'admin', 1, '4', '回访后就没下文了，没人管，没回复，不了了之', '未落实', '1', DATE_SUB(@now, INTERVAL 3 DAY)),
(910007, NULL, '13800000007', 'F5冒烟-系统',     DATE_SUB(@now, INTERVAL 1 DAY), '张三', 2, '3', '小程序卡顿，网页链接打不开，登录还闪退', '跟进中', '1', DATE_SUB(@now, INTERVAL 1 DAY)),
(910008, NULL, '13800000008', 'F5冒烟-一般',     DATE_SUB(@now, INTERVAL 1 DAY), 'admin', 1, '3', '一般般吧，说的内容听不太懂', '跟进中', '1', DATE_SUB(@now, INTERVAL 1 DAY));

-- ② 图文咨询评价：近 3 天 6 条，覆盖高分(≥4)/低分(≤2)，含归因关键词
INSERT INTO ai_user_evaluation
(evaluation_id, consultation_id, user_id, overall_rating, professionalism_rating, responsiveness_rating, quality_rating, feedback, create_by, create_time)
VALUES
(910101, 910101, 1, 5, 5, 4, 5, 'AI 回答非常专业，解决了我的问题', 'admin', DATE_SUB(@now, INTERVAL 1 DAY)),
(910102, 910102, 1, 4, 4, 5, 4, '响应很快，解答也比较详细', 'admin', DATE_SUB(@now, INTERVAL 1 DAY)),
(910103, 910103, 1, 2, 3, 1, 2, '等了半天才回复，响应太慢，而且根本没解决我的问题', 'admin', DATE_SUB(@now, INTERVAL 2 DAY)),
(910104, 910104, 1, 1, 2, 1, 1, '系统一直卡顿闪退，网页打不开，答非所问，太失望了', 'admin', DATE_SUB(@now, INTERVAL 2 DAY)),
(910105, 910105, 1, 5, 5, 5, 5, '很好，专业', 'admin', DATE_SUB(@now, INTERVAL 3 DAY)),
(910106, 910106, 1, 3, 3, 3, 3, '中规中矩', 'admin', DATE_SUB(@now, INTERVAL 3 DAY));

-- ③ 回访任务：3 条 status=2 已逾期，分别落在 L1(<1天)/L2(≥1天)/L3(≥3天)
INSERT INTO ai_return_visit_task
(task_id, task_no, caller_number, caller_name, plan_time, assignee, assignee_id, status, priority, visit_result, create_time)
VALUES
(910201, 'F5RV-L1', '13800000011', 'F5冒烟-逾期L1', DATE_SUB(@now, INTERVAL 5 HOUR),  'admin', 1, '2', '2', NULL, DATE_SUB(@now, INTERVAL 1 DAY)),
(910202, 'F5RV-L2', '13800000012', 'F5冒烟-逾期L2', DATE_SUB(@now, INTERVAL 30 HOUR), 'admin', 1, '2', '1', NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
(910203, 'F5RV-L3', '13800000013', 'F5冒烟-逾期L3', DATE_SUB(@now, INTERVAL 80 HOUR), '张三', 2, '2', '1', NULL, DATE_SUB(@now, INTERVAL 4 DAY));
