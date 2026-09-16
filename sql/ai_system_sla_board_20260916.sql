-- =====================================================================
-- 第十五部分 F9 —— 工单 SLA 可视化看板 菜单/权限迁移脚本
-- 日期: 2026-09-16
-- 说明: 对应《AI律师话务系统综合文档》第十五部分 F9
--       （SLA 看板：剩余时长/超时 TOP/条线与人员达成率/跨域流转）。
--       后端接口 GET /lawyers/sla/board/data，权限 lawyers:slaBoard:view。
--       挂在 F 组一级菜单"法服协同"(3900) 下，紧随 SLA策略(3940)。
-- 特性: 可重复执行（NOT EXISTS 幂等）；超管 role_id=1 自动授权。
-- 执行前提: 在目标库（ai-law）连接下，且已执行 ai_system_f_group_20260913.sql。
-- =====================================================================

-- 3945 SLA 看板（菜单 C）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3945, 'SLA看板', 3900, 5, 'slaBoard', 'lawyers/collab/slaBoard/index', 1, 0, 'C', '0', '0', 'lawyers:slaBoard:view', 'dashboard', 'admin', NOW(), '工单SLA达成率/超时TOP/临近超时/跨域流转可视化看板（F9）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3945);

-- 超管自动授权（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id = 3945
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 3945);
