-- ============================================================
-- 新增功能模块菜单：录音管理、黑白名单、排班管理、外呼结果、IVR执行日志
-- 数据库：ai-law
-- 说明：RuoYi 动态菜单，新增页面需在 sys_menu 注册后才能在侧边栏显示
-- ============================================================

-- ---------- 录音管理（挂载到 3667 来电管理） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3820, '录音管理', 3667, 5, 'recording', 'lawyers/recording/index', 1, 0, 'C', '0', '0', 'lawyers:call:record:list', 'microphone', 'admin', NOW(), '通话录音管理'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3820);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3821, '录音查询', 3820, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:call:record:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3821);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3822, '录音删除', 3820, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:call:record:remove', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3822);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3823, '录音导出', 3820, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:call:record:export', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3823);

-- ---------- 黑白名单（挂载到 3670 资源管理） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3830, '黑白名单', 3670, 13, 'blacklist', 'lawyers/blacklist/index', 1, 0, 'C', '0', '0', 'lawyers:blacklist:list', 'peoples', 'admin', NOW(), '通话黑白名单管理'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3830);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3831, '名单查询', 3830, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:blacklist:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3831);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3832, '名单新增', 3830, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:blacklist:add', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3832);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3833, '名单修改', 3830, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:blacklist:edit', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3833);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3834, '名单删除', 3830, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:blacklist:remove', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3834);

-- ---------- 排班管理（挂载到 3666 工作台） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3840, '排班管理', 3666, 5, 'schedule', 'lawyers/schedule/index', 1, 0, 'C', '0', '0', 'lawyers:schedule:list', 'date', 'admin', NOW(), '坐席班次与排班管理'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3840);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3841, '排班查询', 3840, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:schedule:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3841);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3842, '排班新增', 3840, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:schedule:add', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3842);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3843, '排班修改', 3840, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:schedule:edit', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3843);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3844, '排班删除', 3840, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:schedule:remove', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3844);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3845, '签到签退', 3840, 5, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:schedule:check', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3845);

-- ---------- 外呼结果（挂载到 3669 外呼与IVR） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3850, '外呼结果', 3669, 5, 'result', 'lawyers/outbound/result', 1, 0, 'C', '0', '0', 'lawyers:outbound:result:list', 'chart', 'admin', NOW(), '外呼结果查询'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3850);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3851, '结果查询', 3850, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:outbound:result:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3851);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3852, '结果导出', 3850, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:outbound:result:export', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3852);

-- ---------- IVR执行日志（挂载到 3669 外呼与IVR） ----------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3860, 'IVR执行日志', 3669, 6, 'executionLog', 'lawyers/ivr/executionLog', 1, 0, 'C', '0', '0', 'lawyers:ivr:executionLog:list', 'log', 'admin', NOW(), 'IVR流程执行日志'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3860);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3861, '日志查询', 3860, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ivr:executionLog:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3861);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3862, '日志导出', 3860, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ivr:executionLog:export', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3862);

-- 验证
SELECT menu_id, menu_name, parent_id, path, component, perms FROM sys_menu WHERE menu_id BETWEEN 3820 AND 3862 ORDER BY menu_id;
