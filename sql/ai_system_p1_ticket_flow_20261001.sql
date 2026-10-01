-- =====================================================================
-- P1-6（V2.59）：工单配置化流转 DSL——表驱动状态机（不引入 Flowable）
-- 日期：2026-10-01
--
-- 一行 = 一条"状态-动作"转移规则；新工单类型只需配流不改代码。
-- role_key：执行所需角色，admin 全部放行；多个角色逗号分隔。
-- target_status 允许与 status_from 相同（如转办外派，状态不变仅留动作记录）。
-- 当前工单类型用 flow_code 标识（默认 HOTLINE；F3 外部类型可建同构流）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_ticket_flow_definition (
    flow_id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    flow_code    VARCHAR(32)  NOT NULL DEFAULT 'HOTLINE' COMMENT '工单流程类型（HOTLINE/LEGAL_AID/...）',
    status_from  CHAR(1)      NOT NULL COMMENT '源状态 0待处理 1处理中 2已完成 3已归档',
    action_code  VARCHAR(32)  NOT NULL COMMENT '动作标识（start/complete/archive/transfer）',
    action_name  VARCHAR(32)  NOT NULL COMMENT '动作展示名',
    target_status CHAR(1)     NOT NULL COMMENT '目标状态（可与源相同）',
    role_key     VARCHAR(64)  NOT NULL DEFAULT 'agent' COMMENT '允许角色逗号分隔（agent/leader/admin）',
    sort_no      INT          NOT NULL DEFAULT 0 COMMENT '同状态下排序',
    status       CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (flow_id),
    UNIQUE KEY uk_flow_action (flow_code, status_from, action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流程状态机定义（P1-6 配置化流转 DSL）';

-- 默认热线工单流 seed（幂等）
INSERT INTO ai_ticket_flow_definition
    (flow_code, status_from, action_code, action_name, target_status, role_key, sort_no)
VALUES
    ('HOTLINE', '0', 'start',    '开始处理', '1', 'agent,leader', 10),
    ('HOTLINE', '0', 'transfer', '转办外派', '0', 'leader,admin',  20),
    ('HOTLINE', '1', 'complete', '办结',     '2', 'agent,leader', 10),
    ('HOTLINE', '1', 'transfer', '转办外派', '1', 'leader,admin',  20),
    ('HOTLINE', '2', 'archive',  '归档',     '3', 'leader,admin', 10)
ON DUPLICATE KEY UPDATE action_name = VALUES(action_name);
