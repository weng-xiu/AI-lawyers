-- AI律师系统菜单和权限配置
-- 创建日期：2025-06-23

-- 1. AI律师系统主菜单
INSERT INTO sys_menu VALUES('3000', 'AI律师系统', '0', '5', 'lawyers', null, '', '', 1, 0, 'M', '0', '0', '', 'law', 'admin', sysdate(), '', null, 'AI律师系统目录');

-- 2. 法律咨询管理菜单
INSERT INTO sys_menu VALUES('3100', '法律咨询管理', '3000', '1', 'consultation', 'lawyers/consultation/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:consultation:list', 'peoples', 'admin', sysdate(), '', null, '法律咨询管理菜单');

-- 3. 法律咨询管理按钮权限
INSERT INTO sys_menu VALUES('3101', '咨询查询', '3100', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:consultation:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3102', '咨询新增', '3100', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:consultation:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3103', '咨询修改', '3100', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:consultation:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3104', '咨询删除', '3100', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:consultation:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3105', '咨询导出', '3100', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:consultation:export', '#', 'admin', sysdate(), '', null, '');

-- 4. 法律知识库菜单
INSERT INTO sys_menu VALUES('3200', '法律知识库', '3000', '2', 'knowledge', 'lawyers/knowledge/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:knowledge:list', 'education', 'admin', sysdate(), '', null, '法律知识库菜单');

-- 5. 法律知识库按钮权限
INSERT INTO sys_menu VALUES('3201', '知识查询', '3200', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3202', '知识新增', '3200', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3203', '知识修改', '3200', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3204', '知识删除', '3200', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3205', '知识导出', '3200', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:knowledge:export', '#', 'admin', sysdate(), '', null, '');

-- 6. 咨询分类管理菜单
INSERT INTO sys_menu VALUES('3300', '咨询分类管理', '3000', '3', 'category', 'lawyers/category/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:category:list', 'tree', 'admin', sysdate(), '', null, '咨询分类管理菜单');

-- 7. 咨询分类管理按钮权限
INSERT INTO sys_menu VALUES('3301', '分类查询', '3300', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:category:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3302', '分类新增', '3300', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:category:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3303', '分类修改', '3300', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:category:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3304', '分类删除', '3300', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:category:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3305', '分类导出', '3300', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:category:export', '#', 'admin', sysdate(), '', null, '');

-- 8. 为超级管理员角色分配AI律师系统权限
INSERT INTO sys_role_menu VALUES (1, 3000);
INSERT INTO sys_role_menu VALUES (1, 3100);
INSERT INTO sys_role_menu VALUES (1, 3101);
INSERT INTO sys_role_menu VALUES (1, 3102);
INSERT INTO sys_role_menu VALUES (1, 3103);
INSERT INTO sys_role_menu VALUES (1, 3104);
INSERT INTO sys_role_menu VALUES (1, 3105);
INSERT INTO sys_role_menu VALUES (1, 3200);
INSERT INTO sys_role_menu VALUES (1, 3201);
INSERT INTO sys_role_menu VALUES (1, 3202);
INSERT INTO sys_role_menu VALUES (1, 3203);
INSERT INTO sys_role_menu VALUES (1, 3204);
INSERT INTO sys_role_menu VALUES (1, 3205);
INSERT INTO sys_role_menu VALUES (1, 3300);
INSERT INTO sys_role_menu VALUES (1, 3301);
INSERT INTO sys_role_menu VALUES (1, 3302);
INSERT INTO sys_role_menu VALUES (1, 3303);
INSERT INTO sys_role_menu VALUES (1, 3304);
INSERT INTO sys_role_menu VALUES (1, 3305);