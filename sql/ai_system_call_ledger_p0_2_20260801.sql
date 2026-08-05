-- P0-2 台账管理改造：扩展 ai_call_ledger 表字段（关联来电记录/工单）
-- 创建日期：2026-08-01
-- 用途：支持台账自动填充（从来电记录拉取）与转工单（回写工单ID）

-- ============================================
-- 1. 扩展 ai_call_ledger 表字段
-- ============================================
ALTER TABLE ai_call_ledger
  ADD COLUMN record_id    bigint(20)    DEFAULT NULL  COMMENT '关联来电记录ID' AFTER ledger_no,
  ADD COLUMN ticket_id    bigint(20)    DEFAULT NULL  COMMENT '关联工单ID'    AFTER record_id;

-- 2. 索引（便于按来电记录/工单反查台账）
CREATE INDEX idx_ai_call_ledger_record_id ON ai_call_ledger(record_id);
CREATE INDEX idx_ai_call_ledger_ticket_id ON ai_call_ledger(ticket_id);
