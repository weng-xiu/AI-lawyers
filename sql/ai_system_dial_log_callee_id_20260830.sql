-- =====================================================================
-- 外呼事件回调精确定位被叫（缺陷 C5 修复）
-- ai_call_dial_log 增加 callee_id 列：拨号时记录外呼被叫号码ID，
-- 网关事件回调按 call_uuid -> dial_log -> callee_id 精确定位被叫，
-- 替代原先在任务内按状态"猜第一个呼叫中号码"导致的并发错配。
-- 日期：2026-08-30
-- =====================================================================

ALTER TABLE ai_call_dial_log
    ADD COLUMN callee_id BIGINT(20) NULL COMMENT '关联外呼被叫号码ID(ai_outbound_callee.callee_id)' AFTER task_id;

-- 回调事件按 call_uuid 查询，已有索引；callee_id 仅用于回表定位，数据量随外呼增长，加普通索引便于按被叫反查
ALTER TABLE ai_call_dial_log
    ADD INDEX idx_callee_id (callee_id);
