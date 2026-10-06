-- ============================================
-- P1-6 工单流程编排：DSL 流程规则后端管理菜单/权限
-- ai_ticket_flow_definition 运行时维护（列表/查询/新增/修改）
-- 挂在话务系统(3500)下，menu_id 使用 3800 段
-- 幂等：可重复执行
-- 日期：2026-10-05
-- ============================================

-- 清理旧数据（幂等）
DELETE FROM sys_role_menu WHERE menu_id IN (3800, 3801, 3802, 3803);
DELETE FROM sys_menu      WHERE menu_id IN (3800, 3801, 3802, 3803);

-- 工单流程配置菜单（前端管理页暂未提供，component 预留）
INSERT INTO sys_menu VALUES('3800', '工单流程配置', '3500', '8', 'ticketFlow', 'lawyers/callCenter/ticketFlow', '', '', 1, 0, 'C', '0', '0', 'lawyers:ticketFlow:list', 'tree-table', 'admin', sysdate(), '', null, '工单流程动作规则配置菜单');
INSERT INTO sys_menu VALUES('3801', '流程规则查询', '3800', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ticketFlow:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3802', '流程规则新增', '3800', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ticketFlow:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3803', '流程规则修改', '3800', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ticketFlow:edit', '#', 'admin', sysdate(), '', null, '');

-- 为超级管理员角色分配菜单权限
INSERT INTO sys_role_menu VALUES (1, 3800);
INSERT INTO sys_role_menu VALUES (1, 3801);
INSERT INTO sys_role_menu VALUES (1, 3802);
INSERT INTO sys_role_menu VALUES (1, 3803);
