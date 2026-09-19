-- =====================================================================
-- 方案B（P1）：同渠道会话互斥 + 跨渠道合并提示 —— 统一会话索引占用态改造
-- 日期：2026-09-19
-- 说明：
--   1) ai_unified_session 增加活跃标记/心跳/兜底过期时间；
--   2) 以生成列 active_owner（身份+渠道）+ 唯一键实现"同一公众同一渠道仅一条活跃会话"
--      的 DB 最终兜底（Redis 不可用时依赖它）；会话结束 active_flag=0，生成列变 NULL 自动放行；
--   3) 历史存量会话一律视为已结束，避免上线即占用。
-- 兼容：MySQL 5.7+（生成列 VIRTUAL 支持）；回滚见文件末尾。
-- =====================================================================

-- 1. 新增占用态字段
ALTER TABLE ai_unified_session
  ADD COLUMN active_flag     CHAR(1)   DEFAULT '0' COMMENT '是否活跃 1活跃 0已结束（历史数据默认已结束）' AFTER end_time,
  ADD COLUMN last_heartbeat  DATETIME  DEFAULT NULL COMMENT '最近心跳时间（看门狗判活）' AFTER active_flag,
  ADD COLUMN expire_time     DATETIME  DEFAULT NULL COMMENT '占用兜底过期时间（无结束事件时由看门狗回收）' AFTER last_heartbeat;

-- 2. 生成列：活跃会话的"身份+渠道"占用键；非活跃为 NULL（MySQL 唯一键允许多个 NULL）
ALTER TABLE ai_unified_session
  ADD COLUMN active_owner VARCHAR(96) GENERATED ALWAYS AS
    (CASE WHEN active_flag = '1'
          THEN CONCAT(IFNULL(profile_id, 'N'), ':', IFNULL(caller_number, 'N'), ':', channel_type)
     END) VIRTUAL,
  ADD UNIQUE KEY uk_active_owner (active_owner);

-- 3. 看门狗扫描索引
ALTER TABLE ai_unified_session
  ADD INDEX idx_active_heartbeat (active_flag, last_heartbeat),
  ADD INDEX idx_active_expire (active_flag, expire_time);

-- =====================================================================
-- 回滚：
-- ALTER TABLE ai_unified_session DROP INDEX uk_active_owner, DROP INDEX idx_active_heartbeat,
--   DROP INDEX idx_active_expire, DROP COLUMN active_owner,
--   DROP COLUMN expire_time, DROP COLUMN last_heartbeat, DROP COLUMN active_flag;
-- =====================================================================
