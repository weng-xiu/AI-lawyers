-- 12348热线话务系统V2菜单和权限配置
-- 创建日期：2026-03-14
-- 说明：重新组织话务系统菜单，增加台账记录菜单

-- ============================================
-- 先删除旧的话务系统菜单和角色菜单关联（从子节点删起）
-- ============================================
DELETE FROM sys_role_menu WHERE menu_id IN (
  SELECT menu_id FROM (
    SELECT menu_id FROM sys_menu WHERE parent_id = 3500
    UNION ALL
    SELECT m2.menu_id FROM sys_menu m1
    JOIN sys_menu m2 ON m1.menu_id = m2.parent_id
    WHERE m1.parent_id = 3500
  ) t
);

DELETE FROM sys_menu WHERE parent_id = 3500;
DELETE FROM sys_menu WHERE menu_id = 3500;

-- ============================================
-- 1. 话务系统菜单目录
-- ============================================
INSERT INTO sys_menu VALUES('3500', '话务系统', '3000', '5', 'callCenter', null, '', '', 1, 0, 'M', '0', '0', '', 'phone', 'admin', sysdate(), '', null, '话务系统目录');

-- ============================================
-- 2. 工作台
-- ============================================
INSERT INTO sys_menu VALUES('3510', '坐席工作台', '3500', '1', 'callWorkbench', 'lawyers/callCenter/callWorkbench', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:workbench:list', 'dashboard', 'admin', sysdate(), '', null, '坐席工作台菜单');

-- ============================================
-- 3. 话务服务分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3520', '话务服务', '3500', '2', 'callService', null, '', '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', sysdate(), '', null, '话务服务目录');

-- 3.1 话务功能面板
INSERT INTO sys_menu VALUES('3521', '话务功能面板', '3520', '1', 'callPanel', 'lawyers/callCenter/callPanel', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:panel:list', 'phone', 'admin', sysdate(), '', null, '话务功能面板菜单');

-- 3.2 来电弹屏
INSERT INTO sys_menu VALUES('3522', '来电弹屏', '3520', '2', 'callPop', 'lawyers/callCenter/callPop', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:pop:list', 'message', 'admin', sysdate(), '', null, '来电弹屏菜单');

-- 3.3 来电记录
INSERT INTO sys_menu VALUES('3523', '来电记录', '3520', '3', 'callRecord', 'lawyers/callCenter/callRecord', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:record:list', 'list', 'admin', sysdate(), '', null, '来电记录菜单');

-- 3.4 来电记录按钮权限
INSERT INTO sys_menu VALUES('3524', '来电查询', '3523', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3525', '来电新增', '3523', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3526', '来电修改', '3523', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3527', '来电删除', '3523', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:record:remove', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 业务管理分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3530', '业务管理', '3500', '3', 'callBusiness', null, '', '', 1, 0, 'M', '0', '0', '', 'edit', 'admin', sysdate(), '', null, '业务管理目录');

-- 4.1 工单管理
INSERT INTO sys_menu VALUES('3531', '工单管理', '3530', '1', 'workOrder', 'lawyers/callCenter/workOrder', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:ticket:list', 'tickets', 'admin', sysdate(), '', null, '工单管理菜单');

-- 4.2 工单管理按钮权限
INSERT INTO sys_menu VALUES('3532', '工单查询', '3531', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3533', '工单新增', '3531', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3534', '工单修改', '3531', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3535', '工单删除', '3531', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3536', '工单处理', '3531', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:process', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3537', '工单完成', '3531', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:complete', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3538', '工单归档', '3531', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ticket:archive', '#', 'admin', sysdate(), '', null, '');

-- 4.3 台账记录
INSERT INTO sys_menu VALUES('3540', '台账记录', '3530', '2', 'callLedger', 'lawyers/callCenter/callLedger', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:ledger:list', 'excel', 'admin', sysdate(), '', null, '台账记录菜单');

-- 4.4 台账记录按钮权限
INSERT INTO sys_menu VALUES('3541', '台账查询', '3540', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ledger:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3542', '台账新增', '3540', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ledger:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3543', '台账修改', '3540', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ledger:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3544', '台账删除', '3540', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ledger:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3545', '台账导出', '3540', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:ledger:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 5. 跟进支撑分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3550', '跟进支撑', '3500', '4', 'callSupport', null, '', '', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', sysdate(), '', null, '跟进支撑目录');

-- 5.1 坐席管理
INSERT INTO sys_menu VALUES('3551', '坐席管理', '3550', '1', 'callAgent', 'lawyers/callCenter/callAgent', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:agent:list', 'user', 'admin', sysdate(), '', null, '坐席管理菜单');

-- 5.2 坐席管理按钮权限
INSERT INTO sys_menu VALUES('3552', '坐席查询', '3551', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3553', '坐席新增', '3551', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3554', '坐席修改', '3551', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3555', '坐席删除', '3551', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3556', '坐席登录', '3551', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:login', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3557', '坐席注销', '3551', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:agent:logout', '#', 'admin', sysdate(), '', null, '');

-- 5.3 转接记录
INSERT INTO sys_menu VALUES('3560', '转接记录', '3550', '2', 'callTransfer', 'lawyers/callCenter/callTransfer', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:transfer:list', 'guide', 'admin', sysdate(), '', null, '转接记录菜单');

-- 5.4 转接记录按钮权限
INSERT INTO sys_menu VALUES('3561', '转接查询', '3560', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:transfer:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3562', '转接新增', '3560', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:transfer:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3563', '转接删除', '3560', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:transfer:remove', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 6. 知识库分组（目录）
-- ============================================
INSERT INTO sys_menu VALUES('3570', '知识库', '3500', '5', 'callKnowledge', null, '', '', 1, 0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '', null, '知识库目录');

-- 6.1 法律知识
INSERT INTO sys_menu VALUES('3571', '法律知识', '3570', '1', 'callKnowledgeList', 'lawyers/callCenter/callKnowledge', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:knowledge:list', 'education', 'admin', sysdate(), '', null, '法律知识菜单');

-- ============================================
-- 7. 为超级管理员角色分配所有菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3500);
INSERT INTO sys_role_menu VALUES (1, 3510);
INSERT INTO sys_role_menu VALUES (1, 3520);
INSERT INTO sys_role_menu VALUES (1, 3521);
INSERT INTO sys_role_menu VALUES (1, 3522);
INSERT INTO sys_role_menu VALUES (1, 3523);
INSERT INTO sys_role_menu VALUES (1, 3524);
INSERT INTO sys_role_menu VALUES (1, 3525);
INSERT INTO sys_role_menu VALUES (1, 3526);
INSERT INTO sys_role_menu VALUES (1, 3527);
INSERT INTO sys_role_menu VALUES (1, 3530);
INSERT INTO sys_role_menu VALUES (1, 3531);
INSERT INTO sys_role_menu VALUES (1, 3532);
INSERT INTO sys_role_menu VALUES (1, 3533);
INSERT INTO sys_role_menu VALUES (1, 3534);
INSERT INTO sys_role_menu VALUES (1, 3535);
INSERT INTO sys_role_menu VALUES (1, 3536);
INSERT INTO sys_role_menu VALUES (1, 3537);
INSERT INTO sys_role_menu VALUES (1, 3538);
INSERT INTO sys_role_menu VALUES (1, 3540);
INSERT INTO sys_role_menu VALUES (1, 3541);
INSERT INTO sys_role_menu VALUES (1, 3542);
INSERT INTO sys_role_menu VALUES (1, 3543);
INSERT INTO sys_role_menu VALUES (1, 3544);
INSERT INTO sys_role_menu VALUES (1, 3545);
INSERT INTO sys_role_menu VALUES (1, 3550);
INSERT INTO sys_role_menu VALUES (1, 3551);
INSERT INTO sys_role_menu VALUES (1, 3552);
INSERT INTO sys_role_menu VALUES (1, 3553);
INSERT INTO sys_role_menu VALUES (1, 3554);
INSERT INTO sys_role_menu VALUES (1, 3555);
INSERT INTO sys_role_menu VALUES (1, 3556);
INSERT INTO sys_role_menu VALUES (1, 3557);
INSERT INTO sys_role_menu VALUES (1, 3560);
INSERT INTO sys_role_menu VALUES (1, 3561);
INSERT INTO sys_role_menu VALUES (1, 3562);
INSERT INTO sys_role_menu VALUES (1, 3563);
INSERT INTO sys_role_menu VALUES (1, 3570);
INSERT INTO sys_role_menu VALUES (1, 3571);
