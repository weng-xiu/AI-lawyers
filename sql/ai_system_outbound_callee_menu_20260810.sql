-- 注册外呼被叫名单隐藏菜单（修复 task -> callee 跳转 404）
INSERT INTO sys_menu VALUES('3690', '被叫名单', '3669', '4', 'callee', 'lawyers/outbound/callee/index', '', '', 1, 1, 'C', '0', '0', 'lawyers:outbound:callee:list', 'list', 'admin', sysdate(), '', null, '外呼被叫名单(隐藏菜单)');
INSERT INTO sys_role_menu VALUES (1, 3690);
