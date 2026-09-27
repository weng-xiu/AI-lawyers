-- =============================================================================
-- P3-F4：坐席 Copilot 建议采纳行为埋点表
-- 背景：F4 坐席实时辅助深化——案情要素/推荐法条/相似工单在通话中下发热屏，
--       需记录坐席的采纳/修改/忽略行为，按坐席/班组统计建议采纳率（F10
--       大屏 copilotAdoption 卡此前透出 null 占位）。
-- 口径：采纳率 = ADOPT 数 / (ADOPT+MODIFY+IGNORE) 数；MODIFY=修改后采用单列。
-- 仅埋点，不驱动任何业务闭环；随话务埋点保留（默认不清理）。
-- 适用库：ai-law（MySQL 8.0）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `ai_copilot_feedback` (
  `feedback_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '埋点ID',
  `record_id`       BIGINT       DEFAULT NULL COMMENT '通话记录ID',
  `session_id`      VARCHAR(64)  DEFAULT NULL COMMENT '语音会话ID（非数字兜底）',
  `agent_id`        BIGINT       DEFAULT NULL COMMENT '坐席用户ID',
  `agent_name`      VARCHAR(64)  DEFAULT NULL COMMENT '坐席姓名',
  `dept_id`         BIGINT       DEFAULT NULL COMMENT '班组/部门ID',
  `suggestion_type` VARCHAR(16)  NOT NULL COMMENT '建议类型 ELEMENT/LAW/TICKET',
  `suggestion_ref`  VARCHAR(64)  DEFAULT NULL COMMENT '建议对象引用 chunkId/ticketId/element',
  `action`          VARCHAR(16)  NOT NULL COMMENT '行为 ADOPT/MODIFY/IGNORE',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间',
  PRIMARY KEY (`feedback_id`),
  KEY `idx_time_agent` (`create_time`, `agent_id`),
  KEY `idx_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='P3-F4 Copilot建议采纳行为埋点表';
