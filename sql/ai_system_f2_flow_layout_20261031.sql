-- =====================================================================
-- F2（2026-10-31）：工单流程可视化编排——画布节点布局持久化
--
-- 画布上 4 个状态节点（0 待处理 / 1 处理中 / 2 已完成 / 3 已归档）支持自由拖拽，
-- 布局按「流程编码 + 状态编码」维度保存，切换流程或刷新页面后回显。
-- 与 ai_ticket_flow_definition 解耦：规则增删不影响布局，布局缺失时前端用默认坐标。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_ticket_flow_layout (
    layout_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    flow_code    VARCHAR(32)  NOT NULL DEFAULT 'HOTLINE' COMMENT '工单流程类型（HOTLINE/LEGAL_AID/...）',
    status_code  VARCHAR(16)  NOT NULL COMMENT '状态编码 0待处理 1处理中 2已完成 3已归档',
    pos_x        INT          NOT NULL DEFAULT 0 COMMENT '节点左上角 X 坐标（画布像素）',
    pos_y        INT          NOT NULL DEFAULT 0 COMMENT '节点左上角 Y 坐标（画布像素）',
    update_by    VARCHAR(64)  NULL COMMENT '最后调整人',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NULL COMMENT '最后调整时间',
    PRIMARY KEY (layout_id),
    UNIQUE KEY uk_flow_status (flow_code, status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单流程画布节点布局（F2 可视化编排）';
