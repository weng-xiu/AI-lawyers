-- P1 模块菜单ID冲突修复脚本
-- 为图文对话、视频咨询、有偿服务、风险预警分配不冲突的菜单ID（3720+）

-- ==================== 图文对话服务 (3720-3725) ====================
INSERT INTO sys_menu VALUES('3720', '图文对话服务', '3500', '4', 'chat', 'lawyers/chat/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:chat:list', 'chat', 'admin', sysdate(), '', null, '图文对话服务菜单');
INSERT INTO sys_menu VALUES('3721', '图文对话查询', '3720', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3722', '图文对话回复', '3720', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:send', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3723', '图文对话转接', '3720', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:transfer', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3724', '图文对话关闭', '3720', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:close', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3725', '图文对话导出', '3720', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_role_menu VALUES (1, 3720),(1,3721),(1,3722),(1,3723),(1,3724),(1,3725);

-- ==================== 视频咨询服务 (3730-3735) ====================
INSERT INTO sys_menu VALUES('3730', '视频咨询服务', '3500', '5', 'video', 'lawyers/videoConsult/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:videoConsult:list', 'video', 'admin', sysdate(), '', null, '视频咨询服务菜单');
INSERT INTO sys_menu VALUES('3731', '视频咨询查询', '3730', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3732', '视频咨询创建', '3730', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3733', '视频咨询编辑', '3730', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3734', '视频咨询删除', '3730', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3735', '视频咨询导出', '3730', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_role_menu VALUES (1, 3730),(1,3731),(1,3732),(1,3733),(1,3734),(1,3735);

-- ==================== 有偿法律服务 (3740-3745) ====================
INSERT INTO sys_menu VALUES('3740', '有偿法律服务', '3500', '7', 'paidService', 'lawyers/paidService/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:paidService:list', 'money', 'admin', sysdate(), '', null, '有偿法律服务菜单');
INSERT INTO sys_menu VALUES('3741', '有偿服务查询', '3740', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3742', '有偿服务新增', '3740', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3743', '有偿服务修改', '3740', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3744', '有偿服务删除', '3740', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3745', '有偿服务导出', '3740', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_role_menu VALUES (1, 3740),(1,3741),(1,3742),(1,3743),(1,3744),(1,3745);

-- ==================== 智能风险预警 (3750-3755) ====================
INSERT INTO sys_menu VALUES('3750', '智能风险预警', '3500', '9', 'riskWarning', 'lawyers/riskWarning/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:riskWarning:list', 'warning', 'admin', sysdate(), '', null, '智能风险预警菜单');
INSERT INTO sys_menu VALUES('3751', '风险预警查询', '3750', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3752', '风险预警新增', '3750', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3753', '风险预警修改', '3750', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3754', '风险预警删除', '3750', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3755', '风险预警导出', '3750', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_role_menu VALUES (1, 3750),(1,3751),(1,3752),(1,3753),(1,3754),(1,3755);
