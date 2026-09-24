-- =====================================================================
-- P3-D6：独立多维统计报表 —— 菜单脚本（幂等，可重复执行）
-- 日期: 2026-09-25
-- 说明: 菜单 3980（统计报表 C）+ 3981（报表导出 F 按钮），挂在 3666 工作台 下。
--       执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- 统计报表菜单（C）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3980, '统计报表', 3666, 9, 'statistics', 'lawyers/statistics/index', 1, 0, 'C', '0', '0', 'lawyers:report:view', 'chart', 'admin', NOW(), 'P3-D6 独立多维统计报表（呼叫/坐席服务/质检/业务工单，日周月维度+导出）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3980);

-- 报表导出按钮（F）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3981, '报表导出', 3980, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:report:export', '#', 'admin', NOW(), '统计报表 Excel 导出'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3981);

-- 授权超级管理员角色（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, t.menu_id FROM (SELECT 3980 AS menu_id UNION ALL SELECT 3981) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t.menu_id);

-- 回滚（如需）：
-- DELETE FROM sys_role_menu WHERE menu_id IN (3980, 3981);
-- DELETE FROM sys_menu WHERE menu_id IN (3980, 3981);
