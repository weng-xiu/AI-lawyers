-- =====================================================================
-- B4 运营大屏 + 坐席效能报表 —— 菜单与按钮权限脚本
-- 无新增业务表，仅新增菜单/按钮权限
-- 菜单ID区间：3810 - 3812（延续 B6 已使用的 3801）
-- 挂载目录：资源管理 parent_id = 3670
-- 日期：2026-08-16
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 运营大屏（3810）
-- ---------------------------------------------------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3810', '运营大屏', '3670', '11', 'dashboard', 'lawyers/dashboard/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:dashboard:view', 'chart', 'admin', sysdate(), '呼叫中心运营大屏');

-- ---------------------------------------------------------------------
-- 2. 坐席效能报表（3811）+ 导出按钮（3812）
-- ---------------------------------------------------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3811', '坐席效能报表', '3670', '12', 'agentPerformance', 'lawyers/performance/agent', '', '', 1, 0, 'C', '0', '0', 'lawyers:performance:agent:list', 'list', 'admin', sysdate(), '坐席效能聚合报表');

insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3812', '坐席效能导出', '3811', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:performance:agent:export', '#', 'admin', sysdate(), '');

-- ---------------------------------------------------------------------
-- 3. 为超级管理员角色（role_id=1）分配菜单权限
-- ---------------------------------------------------------------------
insert into sys_role_menu (role_id, menu_id)
select 1, menu_id from sys_menu where menu_id between 3810 and 3812
  and not exists (select 1 from sys_role_menu rm where rm.role_id=1 and rm.menu_id=sys_menu.menu_id);
