-- =====================================================================
-- B6 短信模块 —— 按钮权限补充脚本
-- 为短信配置/模板/记录菜单补充增删改查按钮权限
-- 菜单ID区间：3793 - 3801（延续 B6 建表脚本 3790-3792）
-- 日期：2026-08-16
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 短信配置（parent=3790）按钮
-- ---------------------------------------------------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values
('3793', '短信通道查询', '3790', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsConfig:query', '#', 'admin', sysdate(), ''),
('3794', '短信通道新增', '3790', '2', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsConfig:add', '#', 'admin', sysdate(), ''),
('3795', '短信通道修改', '3790', '3', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsConfig:edit', '#', 'admin', sysdate(), ''),
('3796', '短信通道删除', '3790', '4', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsConfig:remove', '#', 'admin', sysdate(), '');

-- ---------------------------------------------------------------------
-- 2. 短信模板（parent=3791）按钮
-- ---------------------------------------------------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values
('3797', '短信模板查询', '3791', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsTemplate:query', '#', 'admin', sysdate(), ''),
('3798', '短信模板新增', '3791', '2', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsTemplate:add', '#', 'admin', sysdate(), ''),
('3799', '短信模板修改', '3791', '3', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsTemplate:edit', '#', 'admin', sysdate(), ''),
('3800', '短信模板删除', '3791', '4', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsTemplate:remove', '#', 'admin', sysdate(), '');

-- ---------------------------------------------------------------------
-- 3. 短信记录（parent=3792）按钮
-- ---------------------------------------------------------------------
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values
('3801', '短信记录删除', '3792', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:smsLog:remove', '#', 'admin', sysdate(), '');

-- ---------------------------------------------------------------------
-- 4. 为超级管理员角色（role_id=1）分配按钮权限
-- ---------------------------------------------------------------------
insert into sys_role_menu (role_id, menu_id)
select 1, menu_id from sys_menu where menu_id between 3793 and 3801
  and not exists (select 1 from sys_role_menu rm where rm.role_id=1 and rm.menu_id=sys_menu.menu_id);
