-- AI律师系统菜单和权限配置 - 话务系统菜单
-- 创建日期：2026-07-16

-- 1. 话务系统菜单目录
INSERT INTO sys_menu VALUES('3500', '话务系统', '3000', '5', 'callCenter', null, '', '', 1, 0, 'M', '0', '0', '', 'phone', 'admin', sysdate(), '', null, '话务系统目录');

-- 2. 坐席工作台菜单
INSERT INTO sys_menu VALUES('3510', '坐席工作台', '3500', '1', 'callWorkbench', 'lawyers/callCenter/callWorkbench', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:workbench:list', 'monitor', 'admin', sysdate(), '', null, '坐席工作台菜单');

-- 3. 坐席管理菜单
INSERT INTO sys_menu VALUES('3520', '坐席管理', '3500', '2', 'callAgent', 'lawyers/callCenter/callAgent', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:agent:list', 'user', 'admin', sysdate(), '', null, '坐席管理菜单');

-- 4. 坐席管理按钮权限
INSERT INTO sys_menu VALUES('3521', '坐席查询', '3520', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3522', '坐席新增', '3520', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3523', '坐席修改', '3520', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3524', '坐席删除', '3520', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3525', '坐席登录', '3520', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:login', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3526', '坐席注销', '3520', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:logout', '#', 'admin', sysdate(), '', null, '');

-- 5. 来电记录菜单
INSERT INTO sys_menu VALUES('3530', '来电记录', '3500', '3', 'callRecord', 'lawyers/callCenter/callRecord', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:record:list', 'phone', 'admin', sysdate(), '', null, '来电记录菜单');

-- 6. 来电记录按钮权限
INSERT INTO sys_menu VALUES('3531', '来电查询', '3530', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3532', '来电新增', '3530', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3533', '来电修改', '3530', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3534', '来电删除', '3530', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:remove', '#', 'admin', sysdate(), '', null, '');

-- 7. 工单管理菜单
INSERT INTO sys_menu VALUES('3540', '工单管理', '3500', '4', 'callTicket', 'lawyers/callCenter/callTicket', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:ticket:list', 'tickets', 'admin', sysdate(), '', null, '工单管理菜单');

-- 8. 工单管理按钮权限
INSERT INTO sys_menu VALUES('3541', '工单查询', '3540', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3542', '工单新增', '3540', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3543', '工单修改', '3540', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3544', '工单删除', '3540', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3545', '工单处理', '3540', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:process', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3546', '工单完成', '3540', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:complete', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3547', '工单归档', '3540', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:archive', '#', 'admin', sysdate(), '', null, '');

-- 9. 为超级管理员角色分配新增的菜单权限
INSERT INTO sys_role_menu VALUES (1, 3500);
INSERT INTO sys_role_menu VALUES (1, 3510);
INSERT INTO sys_role_menu VALUES (1, 3520);
INSERT INTO sys_role_menu VALUES (1, 3521);
INSERT INTO sys_role_menu VALUES (1, 3522);
INSERT INTO sys_role_menu VALUES (1, 3523);
INSERT INTO sys_role_menu VALUES (1, 3524);
INSERT INTO sys_role_menu VALUES (1, 3525);
INSERT INTO sys_role_menu VALUES (1, 3526);
INSERT INTO sys_role_menu VALUES (1, 3530);
INSERT INTO sys_role_menu VALUES (1, 3531);
INSERT INTO sys_role_menu VALUES (1, 3532);
INSERT INTO sys_role_menu VALUES (1, 3533);
INSERT INTO sys_role_menu VALUES (1, 3534);
INSERT INTO sys_role_menu VALUES (1, 3540);
INSERT INTO sys_role_menu VALUES (1, 3541);
INSERT INTO sys_role_menu VALUES (1, 3542);
INSERT INTO sys_role_menu VALUES (1, 3543);
INSERT INTO sys_role_menu VALUES (1, 3544);
INSERT INTO sys_role_menu VALUES (1, 3545);
INSERT INTO sys_role_menu VALUES (1, 3546);
INSERT INTO sys_role_menu VALUES (1, 3547);
