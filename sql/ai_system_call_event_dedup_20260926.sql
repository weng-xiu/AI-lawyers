-- =============================================================================
-- P3-B4：PBX 事件幂等去重表
-- 背景：ESL 订阅同时含 CHANNEL_HANGUP 与 CHANNEL_HANGUP_COMPLETE（二者都会触发），
--       且 PBX 重发/多实例重复消费会导致 finishCall 双释放线路并发、指标双计。
-- 方案：事件入口按 dedup_key = source|eventKey|eventName 做 INSERT IGNORE 判重，
--       返回 1=首次到达（继续处理），0=重复（丢弃）；DB 异常时 fail-open 放行，
--       由状态机终态兜底（见 CallDispatchServiceImpl.onCallEvent）。
-- 保留：短保留（默认 7 天，data.retention.call-event-dedup-days），
--       由 DataLifecycleCleanupTask 每日批量清理。
-- 适用库：ai-law（MySQL 8.0）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ai_call_event_dedup` (
  `dedup_key`   VARCHAR(191) NOT NULL COMMENT '幂等键：source|eventKey|eventName',
  `source`      VARCHAR(64)  NOT NULL COMMENT '事件来源，如 ESL:host:port',
  `event_key`   VARCHAR(64)  NOT NULL COMMENT '事件对象键，通常为通道 UUID',
  `event_name`  VARCHAR(64)  NOT NULL COMMENT '规范化事件名（HANGUP_COMPLETE 归一为 HANGUP）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次到达时间',
  PRIMARY KEY (`dedup_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P3-B4 PBX事件幂等去重表';
