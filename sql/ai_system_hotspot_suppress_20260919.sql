-- =====================================================================
-- P3-D1 高频置底（hotspot suppress）—— 数据库迁移脚本
-- 日期: 2026-09-19
-- 说明:
--   对骚扰/高频来电与外呼号码做"降权置底/直接拦截"处置：
--     1) ai_hotspot_suppress      置底规则（号码精确匹配 / 关键词匹配）
--     2) ai_hotspot_suppress_log  命中处置日志
--   入站来电命中且动作为 PRIORITY 时，排队优先级置为负数沉到队尾（仍可接听）；
--   动作为 REJECT 时直接挂断。全部向前兼容、可重复执行。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 一、置底规则表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_hotspot_suppress (
    suppress_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    rule_name       VARCHAR(100) NOT NULL COMMENT '规则名称',
    match_type      VARCHAR(16)  NOT NULL DEFAULT 'PHONE' COMMENT '匹配类型 PHONE号码 KEYWORD关键词',
    match_value     VARCHAR(128) NOT NULL COMMENT '匹配值（号码精确匹配/关键词）',
    action          VARCHAR(16)  NOT NULL DEFAULT 'PRIORITY' COMMENT '处置动作 PRIORITY置底降权 REJECT直接拦截',
    priority_level  INT          NOT NULL DEFAULT -100 COMMENT '置底优先级（负数，入站队列数值越大越优先）',
    trigger_count   INT          NOT NULL DEFAULT 0 COMMENT '时间窗内触发次数阈值（0=不按频次，命中即生效）',
    window_seconds  INT          NOT NULL DEFAULT 0 COMMENT '频次统计时间窗（秒，0=不限制）',
    hit_count       INT          NOT NULL DEFAULT 0 COMMENT '累计命中次数',
    status          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
    effective_start DATETIME              DEFAULT NULL COMMENT '生效开始时间（空=立即）',
    effective_end   DATETIME              DEFAULT NULL COMMENT '生效结束时间（空=长期）',
    create_by       VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    create_time     DATETIME              DEFAULT NULL COMMENT '创建时间',
    update_by       VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    update_time     DATETIME              DEFAULT NULL COMMENT '更新时间',
    remark          VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (suppress_id),
    UNIQUE KEY uk_match (match_type, match_value),
    KEY idx_status_effect (status, effective_start, effective_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高频置底规则表';

-- ---------------------------------------------------------------------
-- 二、命中处置日志表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_hotspot_suppress_log (
    log_id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    suppress_id   BIGINT      NOT NULL COMMENT '命中规则ID',
    rule_name     VARCHAR(100) DEFAULT NULL COMMENT '规则名称（冗余，便于检索）',
    direction     VARCHAR(8)  NOT NULL DEFAULT 'INBOUND' COMMENT '呼叫方向 INBOUND入站 OUTBOUND外呼',
    match_type    VARCHAR(16)  DEFAULT NULL COMMENT '匹配类型',
    match_value   VARCHAR(128) DEFAULT NULL COMMENT '命中的匹配值',
    caller_number VARCHAR(32)  DEFAULT NULL COMMENT '主叫号码',
    callee_number VARCHAR(32)  DEFAULT NULL COMMENT '被叫号码',
    action        VARCHAR(16)  DEFAULT NULL COMMENT '实际处置动作',
    priority      INT          DEFAULT NULL COMMENT '处置后优先级',
    channel_uuid  VARCHAR(64)  DEFAULT NULL COMMENT '通道UUID/话单关联键',
    create_time   DATETIME     DEFAULT NULL COMMENT '命中时间',
    PRIMARY KEY (log_id),
    KEY idx_suppress (suppress_id),
    KEY idx_caller_time (caller_number, create_time),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高频置底命中处置日志表';

-- ---------------------------------------------------------------------
-- 三、字典数据（匹配类型 / 处置动作）
-- ---------------------------------------------------------------------
-- 字典类型：置底匹配类型
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '置底匹配类型', 'ai_hotspot_match_type', '0', 'admin', NOW(), '高频置底规则匹配类型'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_hotspot_match_type');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '号码匹配', 'PHONE', 'ai_hotspot_match_type', '', 'primary', 'Y', '0', 'admin', NOW(), '按主叫号码精确匹配'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type='ai_hotspot_match_type' AND dict_value='PHONE');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '关键词匹配', 'KEYWORD', 'ai_hotspot_match_type', '', 'info', 'N', '0', 'admin', NOW(), '按关键词匹配'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type='ai_hotspot_match_type' AND dict_value='KEYWORD');

-- 字典类型：置底处置动作
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '置底处置动作', 'ai_hotspot_action', '0', 'admin', NOW(), '高频置底处置动作'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_hotspot_action');

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 1, '置底降权', 'PRIORITY', 'ai_hotspot_action', '', 'warning', 'Y', '0', 'admin', NOW(), '排队沉到队尾，仍可接听'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type='ai_hotspot_action' AND dict_value='PRIORITY');
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT 2, '直接拦截', 'REJECT', 'ai_hotspot_action', '', 'danger', 'N', '0', 'admin', NOW(), '直接挂断/跳过'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict_data WHERE dict_type='ai_hotspot_action' AND dict_value='REJECT');

-- ---------------------------------------------------------------------
-- 四、菜单与按钮权限（挂"资源管理"目录 menu_id=3670）
--   menu_id 取 3970~3974（当前最大 3960，先做存在性判断）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3970, '高频置底', 3670, 14, 'hotspotSuppress', 'lawyers/hotspotSuppress/index', 1, 0, 'C', '0', '0', 'lawyers:hotspot:list', 'filter', 'admin', NOW(), '高频来电/外呼置底规则管理'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3970);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT t.id, t.name, 3970, t.sort, '', NULL, 1, 0, 'F', '0', '0', t.perm, '#', 'admin', NOW(), NULL
FROM (
    SELECT 3971 AS id, '置底查询' AS name, 1 AS sort, 'lawyers:hotspot:query' AS perm
    UNION ALL SELECT 3972, '置底新增', 2, 'lawyers:hotspot:add'
    UNION ALL SELECT 3973, '置底修改', 3, 'lawyers:hotspot:edit'
    UNION ALL SELECT 3974, '置底删除', 4, 'lawyers:hotspot:remove'
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.menu_id = t.id);

-- 超管授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, t.id FROM (
    SELECT 3970 AS id UNION ALL SELECT 3971 UNION ALL SELECT 3972 UNION ALL SELECT 3973 UNION ALL SELECT 3974
) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t.id);

-- =====================================================================
-- 回滚脚本（按需手动执行）
-- =====================================================================
-- DELETE FROM sys_role_menu WHERE menu_id BETWEEN 3970 AND 3974;
-- DELETE FROM sys_menu WHERE menu_id BETWEEN 3970 AND 3974;
-- DELETE FROM sys_dict_data WHERE dict_type IN ('ai_hotspot_match_type','ai_hotspot_action');
-- DELETE FROM sys_dict_type WHERE dict_type IN ('ai_hotspot_match_type','ai_hotspot_action');
-- DROP TABLE IF EXISTS ai_hotspot_suppress_log;
-- DROP TABLE IF EXISTS ai_hotspot_suppress;
