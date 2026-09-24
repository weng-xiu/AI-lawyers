-- ==================================================================
-- F9 SLA 升级到人：创建 escalate_roles 引用的三个角色 + 菜单授权
-- 脚本名：ai_system_sla_roles_20260925.sql
-- 幂等：information_schema 判断，可重复执行
-- ==================================================================

-- ---------- sys_role ----------
INSERT IGNORE INTO sys_role (role_id, role_name, role_key, role_sort, status, del_flag, create_by, create_time, remark)
VALUES
    (10, '班长',         'ai_team_leader', 10, '0', '0', 'admin', NOW(), 'SLA 升级目标角色：班组负责人'),
    (11, '主管',         'ai_manager',     11, '0', '0', 'admin', NOW(), 'SLA 升级目标角色：部门主管'),
    (12, '主任',         'ai_director',    12, '0', '0', 'admin', NOW(), 'SLA 升级目标角色：中心主任');

-- ---------- sys_menu 占位（角色管理页中维护用户-角色关系即可，不新增独立菜单） ----------
-- 由于角色管理已复用现有"角色管理"页面（菜单 1002），本脚本不新增菜单；
-- 实际运行时请在"系统管理→角色管理"中为用户分配上述角色，并在"系统管理→用户管理"中绑定。

-- ---------- 说明 ----------
-- escalate_roles 逗号分隔链示例：
--   team_leader,manager,director   → 匹配 ai_team_leader / ai_manager / ai_director
-- 超期 1 个办结周期 → 推给 ai_team_leader 持有者
-- 超期 2 个办结周期 → 推给 ai_manager 持有者
-- 超期 3 个办结周期 → 推给 ai_director 持有者
