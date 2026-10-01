-- =====================================================================
-- P1-8（V2.59）：相似工单语义检索——工单内容向量持久化
-- 日期：2026-10-01
--
-- 复用 RAG 同款方案：向量以小端 float32 连续字节存 LONGBLOB 作为内存索引备份，
-- 不引入向量数据库/新中间件。仅已办结(2)/已归档(3)工单号入索引。
-- 执行前请确认 ai_call_ticket 表已存在（ai_system_recording_20260823.sql）。
-- =====================================================================

ALTER TABLE ai_call_ticket
    ADD COLUMN content_embedding LONGBLOB NULL COMMENT '标题+内容语义向量（小端float32连续字节，仅办结工单）' AFTER process_content,
    ADD COLUMN embedding_dim INT NOT NULL DEFAULT 0 COMMENT '向量维度（0=未向量化）' AFTER content_embedding,
    ADD INDEX idx_ticket_embedding_dim (embedding_dim);
