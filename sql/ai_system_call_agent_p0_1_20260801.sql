-- P0-1 话务功能面板改造：扩展 ai_call_agent_status 表字段
-- 创建日期：2026-08-01
-- 用途：支持 CTI 通话控制（外呼/保持/转接/咨询/三方/话后整理/挂机/机器人接管/IVR转接）

-- ============================================
-- 1. 扩展 ai_call_agent_status 表字段
-- ============================================
ALTER TABLE ai_call_agent_status
  ADD COLUMN call_mode          varchar(1)    DEFAULT '0' COMMENT '应答模式 0=自动应答 1=手动应答' AFTER last_login_ip,
  ADD COLUMN current_call_id    bigint(20)    DEFAULT NULL  COMMENT '当前通话记录ID'                   AFTER call_mode,
  ADD COLUMN current_call_phone varchar(20)   DEFAULT NULL  COMMENT '当前通话号码'                     AFTER current_call_id,
  ADD COLUMN call_start_time    datetime      DEFAULT NULL  COMMENT '当前通话开始时间'                 AFTER current_call_phone,
  ADD COLUMN call_status        varchar(1)    DEFAULT '0' COMMENT '通话状态 0=空闲 1=通话中 2=保持 3=咨询中 4=三方 5=话后整理' AFTER call_start_time;

-- 2. 初始化已存在座席的通话状态字段
UPDATE ai_call_agent_status SET call_mode = '0', call_status = '0' WHERE call_mode IS NULL OR call_status IS NULL;
