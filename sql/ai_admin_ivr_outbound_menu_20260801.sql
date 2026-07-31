-- IVR智能流程和外呼任务系统菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. IVR智能流程分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3600', 'IVR智能流程', '3500', '6', 'ivrFlow', null, '', '', 1, 0, 'M', '0', '0', '', 'tree', 'admin', sysdate(), '', null, 'IVR智能流程目录');

-- 1.1 IVR流程管理
INSERT INTO sys_menu VALUES('3601', 'IVR流程管理', '3600', '1', 'flow', 'lawyers/ivr/flow/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:ivr:flow:list', 'flow', 'admin', sysdate(), '', null, 'IVR流程管理菜单');

-- 1.2 IVR流程管理按钮权限
INSERT INTO sys_menu VALUES('3602', '流程查询', '3601', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3603', '流程新增', '3601', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3604', '流程修改', '3601', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3605', '流程删除', '3601', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3606', '流程导出', '3601', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3607', '流程发布', '3601', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:flow:publish', '#', 'admin', sysdate(), '', null, '');

-- 1.3 意图定义管理
INSERT INTO sys_menu VALUES('3610', '意图定义管理', '3600', '2', 'intention', 'lawyers/ivr/intention/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:ivr:intention:list', 'checkbox', 'admin', sysdate(), '', null, '意图定义管理菜单');

-- 1.4 意图定义管理按钮权限
INSERT INTO sys_menu VALUES('3611', '意图查询', '3610', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3612', '意图新增', '3610', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3613', '意图修改', '3610', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3614', '意图删除', '3610', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3615', '意图导出', '3610', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3616', '意图匹配', '3610', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:ivr:intention:match', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 2. 智能外呼分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3620', '智能外呼', '3500', '7', 'outbound', null, '', '', 1, 0, 'M', '0', '0', '', 'phone', 'admin', sysdate(), '', null, '智能外呼目录');

-- 2.1 外呼任务管理
INSERT INTO sys_menu VALUES('3621', '外呼任务管理', '3620', '1', 'task', 'lawyers/outbound/task/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:outbound:task:list', 'job', 'admin', sysdate(), '', null, '外呼任务管理菜单');

-- 2.2 外呼任务管理按钮权限
INSERT INTO sys_menu VALUES('3622', '任务查询', '3621', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3623', '任务新增', '3621', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3624', '任务修改', '3621', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3625', '任务删除', '3621', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3626', '任务导出', '3621', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3627', '任务启动', '3621', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:start', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3628', '任务暂停', '3621', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:pause', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3629', '任务停止', '3621', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:outbound:task:stop', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 3. 为超级管理员角色分配所有菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3600);
INSERT INTO sys_role_menu VALUES (1, 3601);
INSERT INTO sys_role_menu VALUES (1, 3602);
INSERT INTO sys_role_menu VALUES (1, 3603);
INSERT INTO sys_role_menu VALUES (1, 3604);
INSERT INTO sys_role_menu VALUES (1, 3605);
INSERT INTO sys_role_menu VALUES (1, 3606);
INSERT INTO sys_role_menu VALUES (1, 3607);
INSERT INTO sys_role_menu VALUES (1, 3610);
INSERT INTO sys_role_menu VALUES (1, 3611);
INSERT INTO sys_role_menu VALUES (1, 3612);
INSERT INTO sys_role_menu VALUES (1, 3613);
INSERT INTO sys_role_menu VALUES (1, 3614);
INSERT INTO sys_role_menu VALUES (1, 3615);
INSERT INTO sys_role_menu VALUES (1, 3616);
INSERT INTO sys_role_menu VALUES (1, 3620);
INSERT INTO sys_role_menu VALUES (1, 3621);
INSERT INTO sys_role_menu VALUES (1, 3622);
INSERT INTO sys_role_menu VALUES (1, 3623);
INSERT INTO sys_role_menu VALUES (1, 3624);
INSERT INTO sys_role_menu VALUES (1, 3625);
INSERT INTO sys_role_menu VALUES (1, 3626);
INSERT INTO sys_role_menu VALUES (1, 3627);
INSERT INTO sys_role_menu VALUES (1, 3628);
INSERT INTO sys_role_menu VALUES (1, 3629);
