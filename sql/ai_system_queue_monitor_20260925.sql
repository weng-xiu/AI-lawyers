-- =====================================================================
-- P3-H1：Stream 队列监控（积压/死信治理） —— 菜单脚本（幂等，可重复执行）
-- 日期: 2026-09-25
-- 说明: 菜单 3982（队列监控 C）+ 3983（死信重投 F 按钮），挂在 3666 工作台 下。
--       执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- 队列监控菜单（C）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3982, '队列监控', 3666, 10, 'queueMonitor', 'lawyers/queueMonitor/index', 1, 0, 'C', '0', '0', 'lawyers:queueMonitor:list', 'list', 'admin', NOW(), 'P3-H1 Stream 队列水位/积压/死信监控与死信重投'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3982);

-- 死信重投/删除按钮（F）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3983, '死信重投', 3982, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:queueMonitor:replay', '#', 'admin', NOW(), 'Stream 死信重投与删除'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3983);

-- 授权超级管理员角色（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, t.menu_id FROM (SELECT 3982 AS menu_id UNION ALL SELECT 3983) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t.menu_id);

-- 回滚（如需）：
-- DELETE FROM sys_role_menu WHERE menu_id IN (3982, 3983);
-- DELETE FROM sys_menu WHERE menu_id IN (3982, 3983);
