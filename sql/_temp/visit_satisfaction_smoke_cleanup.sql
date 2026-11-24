-- F5 回访满意度看板 接口冒烟测试数据清理（与 visit_satisfaction_smoke_data.sql 配对）
-- 只删本次冒烟造数：电话回访 910001-910008 / 图文评价 910101-910106 / 回访任务 910201-910203
-- 保留：菜单 3960、补建的 ai_user_evaluation 表结构、7-8 月历史种子数据

DELETE FROM ai_callback WHERE callback_id BETWEEN 910001 AND 910008 OR caller_name LIKE 'F5冒烟%';
DELETE FROM ai_user_evaluation WHERE evaluation_id BETWEEN 910101 AND 910106;
DELETE FROM ai_return_visit_task WHERE task_id BETWEEN 910201 AND 910203 OR task_no LIKE 'F5RV-%';

-- 核验（预期均为 0）
SELECT COUNT(*) AS cb_left FROM ai_callback WHERE callback_id BETWEEN 910001 AND 910008;
SELECT COUNT(*) AS ev_left FROM ai_user_evaluation WHERE evaluation_id BETWEEN 910101 AND 910106;
SELECT COUNT(*) AS rv_left FROM ai_return_visit_task WHERE task_id BETWEEN 910201 AND 910203;
