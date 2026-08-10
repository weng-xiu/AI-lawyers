-- ============================================================
-- 菜单结构修复脚本
-- 修复内容：
--   1. 5个P1孤儿菜单(parent_id=3500不存在)重新归属到正确的顶级分类
--   2. 删除指向静态mock页的重复"弹屏面板"菜单(3522)
--   3. 删除component指向不存在文件的重复菜单(3571法律知识-与3200重复)
--   4. 转接记录/待办/公告 保留但需新建对应vue页面
-- ============================================================

-- 1. P1孤儿菜单重新归属
-- 图文对话服务、视频咨询服务 -> 工作台(3666)
UPDATE sys_menu SET parent_id = 3666, order_num = 5 WHERE menu_id = 3720;
UPDATE sys_menu SET parent_id = 3666, order_num = 6 WHERE menu_id = 3730;
-- 有偿法律服务、签署记录管理 -> 业务处理(3668)
UPDATE sys_menu SET parent_id = 3668, order_num = 7 WHERE menu_id = 3740;
UPDATE sys_menu SET parent_id = 3668, order_num = 8 WHERE menu_id = 3680;
-- 智能风险预警 -> 资源管理(3670)
UPDATE sys_menu SET parent_id = 3670, order_num = 3 WHERE menu_id = 3750;

-- 2. 删除指向静态mock页 callPop.vue 的重复"弹屏面板"菜单(真实来电弹屏为3644)
DELETE FROM sys_role_menu WHERE menu_id = 3522;
DELETE FROM sys_menu WHERE menu_id = 3522;

-- 3. 删除重复的"法律知识"菜单3571（component指向不存在的callKnowledge，真实知识库为3200）
DELETE FROM sys_role_menu WHERE menu_id = 3571;
DELETE FROM sys_menu WHERE menu_id = 3571;

-- 4. 待办事项管理(3632)、公告管理(3638) 从业务处理移到 工作台(3666)
UPDATE sys_menu SET parent_id = 3666, order_num = 7 WHERE menu_id = 3632;
UPDATE sys_menu SET parent_id = 3666, order_num = 8 WHERE menu_id = 3638;
