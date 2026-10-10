-- ============================================================
-- P3-#16（全渠道 G9）用户咨询渠道透传：ai_user_consultation 补 channel_type
-- 创建日期：2026-10-10
--
-- 背景：调研报告 6.3-#16"渠道适配层可先行"——AiUserConsultationController
--       补 channelType 透传；渠道字典（PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB）
--       与 ai_channel_identity / ai_unified_session 对齐（见
--       ai_system_f_group_20260913.sql F6）。存量数据按 H5 图文渠道回填。
--
-- 回滚：DROP COLUMN channel_type（存量 channel_type 值一并丢弃，无业务依赖）。
-- 幂等：MySQL 列新增不支持 IF NOT EXISTS，重复执行报 1060 可忽略。
-- ============================================================

ALTER TABLE ai_user_consultation
    ADD COLUMN channel_type VARCHAR(20) DEFAULT 'H5'
        COMMENT '提交渠道（PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB，对齐 F6 渠道字典）' AFTER status;

-- 存量数据回填 H5（与列默认值一致，显式声明以便审计）
UPDATE ai_user_consultation SET channel_type = 'H5' WHERE channel_type IS NULL;
