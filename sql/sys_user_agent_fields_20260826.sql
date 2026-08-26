-- =============================================================
-- 将坐席配置字段合并到 sys_user 表
-- 坐席 = 系统用户的呼叫中心身份，不再通过独立坐席管理页面维护
-- 运行状态（在线/忙碌/通话中等）仍保留在 ai_call_agent_status 表
-- =============================================================

-- 1. sys_user 增加坐席配置字段
ALTER TABLE sys_user
  ADD COLUMN agent_id     BIGINT       NULL COMMENT '坐席工号（关联 ai_call_agent_status.agent_id，空表示非坐席）' AFTER lawyer_intro,
  ADD COLUMN sip_extension VARCHAR(20) NULL COMMENT 'SIP分机号（需与 FreeSWITCH 分机一致）' AFTER agent_id,
  ADD COLUMN call_mode    CHAR(1)      NULL DEFAULT '0' COMMENT '应答模式（0自动 1手动）' AFTER sip_extension;

-- 2. 为坐席工号建立唯一索引（一个用户只能绑定一个工号）
ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_agent_id (agent_id);

-- 3. 将已有的坐席绑定关系同步回 sys_user
UPDATE sys_user u
INNER JOIN ai_call_agent_status a ON u.user_id = a.user_id
SET u.agent_id     = a.agent_id,
    u.sip_extension = a.sip_extension,
    u.call_mode    = a.call_mode
WHERE u.agent_id IS NULL;
