-- ============================================
-- P1-8 坐席辅助/仿真：统一仿真评测页菜单/权限
-- 提交/进度/报告（case·check·turn 通过率），复用 lawyers:modelConfig:test 权限
-- 挂在话务系统(3500)下，menu_id 使用空闲的 3890 段
-- 幂等：可重复执行
-- 日期：2026-10-06
-- ============================================

-- 清理旧数据（幂等）
DELETE FROM sys_role_menu WHERE menu_id = 3890;
DELETE FROM sys_menu      WHERE menu_id = 3890;

-- 仿真评测菜单（Copilot/机器人切换在同一页面内完成）
INSERT INTO sys_menu VALUES('3890', '仿真评测', '3500', '9', 'simulation', 'lawyers/simulation/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:modelConfig:test', 'cog', 'admin', sysdate(), '', null, '坐席辅助/语音机器人上线前仿真回归（提交/进度/报告，case·check·turn 通过率）');

-- 为超级管理员角色分配菜单权限
INSERT INTO sys_role_menu VALUES (1, 3890);
