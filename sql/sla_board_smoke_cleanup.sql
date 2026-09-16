-- F9 SLA 看板接口冒烟数据清理脚本
-- 与 sla_board_smoke_data.sql 配对，按 SLASMK 前缀精确删除，不影响真实数据
DELETE FROM ai_ticket_transfer WHERE idempotent_key LIKE 'SLASMK-%' OR ticket_no LIKE 'SLASMK-%';
DELETE FROM ai_call_ticket WHERE ticket_no LIKE 'SLASMK-%' OR record_id BETWEEN -900199 AND -900100;
-- 核验：以下两个计数均应为 0
SELECT COUNT(*) smoke_ticket_left FROM ai_call_ticket WHERE ticket_no LIKE 'SLASMK-%';
SELECT COUNT(*) smoke_transfer_left FROM ai_ticket_transfer WHERE idempotent_key LIKE 'SLASMK-%';
