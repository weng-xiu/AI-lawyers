-- =====================================================================
-- F9（2026-11-01）：坐席换班申请与审批
--
-- 背景：排班表 ai_agent_schedule 已支持批量排班与签到签退，
-- 但班次调整仍由班组长直接改表（无留痕、无审批）。
-- 本表把「坐席发起 → 班组长审批 → 审批通过后自动改排班」串成闭环：
--   1) SWAP     互换：申请人与对方坐席同一天的两个班次互相交换（排班行不变，仅换 shift_id）；
--   2) TRANSFER 转班：申请人的排班整条转给接收坐席（接收坐席当日必须无有效排班）；
--   3) 审批通过前排班不变，审批通过后才落库，驳回/撤销不产生副作用。
--
-- 状态口径：0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销（仅申请人可撤销待审批单）。
-- 幂等：CREATE TABLE IF NOT EXISTS，可重复执行。
-- 执行前提：在目标库（ai-law）连接下执行。
-- =====================================================================

CREATE TABLE IF NOT EXISTS ai_shift_change_request (
    request_id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    applicant_agent_id    BIGINT       NOT NULL COMMENT '申请人坐席ID',
    applicant_schedule_id BIGINT       NOT NULL COMMENT '申请人待变更排班ID',
    change_type           VARCHAR(16)  NOT NULL DEFAULT 'SWAP' COMMENT '变更类型 SWAP互换 TRANSFER转班',
    target_agent_id       BIGINT                DEFAULT NULL COMMENT '接收坐席ID（互换取对方排班坐席，转班必填）',
    target_schedule_id    BIGINT                DEFAULT NULL COMMENT '对方排班ID（互换必填，转班为空）',
    reason                VARCHAR(500)          DEFAULT NULL COMMENT '申请原因',
    status                CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0待审批 1已通过 2已驳回 3已撤销',
    approver              VARCHAR(64)           DEFAULT NULL COMMENT '审批人（班组长账号）',
    approve_time          DATETIME              DEFAULT NULL COMMENT '审批时间',
    approve_remark        VARCHAR(500)          DEFAULT NULL COMMENT '审批意见',
    create_by             VARCHAR(64)           DEFAULT '' COMMENT '创建者',
    create_time           DATETIME              DEFAULT NULL COMMENT '创建时间',
    update_by             VARCHAR(64)           DEFAULT '' COMMENT '更新者',
    update_time           DATETIME              DEFAULT NULL COMMENT '更新时间',
    remark                VARCHAR(500)          DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (request_id),
    KEY idx_applicant_status (applicant_agent_id, status),
    KEY idx_status_create (status, create_time),
    KEY idx_applicant_schedule (applicant_schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席换班申请与审批（F9）';

-- =====================================================================
-- 回滚脚本（按需手动执行）
-- =====================================================================
-- DROP TABLE IF EXISTS ai_shift_change_request;
