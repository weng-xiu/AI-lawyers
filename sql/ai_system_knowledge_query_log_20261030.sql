-- F8 知识库运营增强：RAG 检索查询日志
-- 每次智能体/语音机器人/坐席助手检索落一条日志，用于统计知识命中率与未命中问句排行。
-- 隐私约束：query_text 已脱敏（手机号/身份证/银行卡/邮箱）并截断 500 字符；
--           分组仅使用 query_hash（归一化问句 SHA-256 前 32 位），不按原文模糊检索。
-- 表未创建时后端读接口降级为 available=false，不影响检索主链路。
CREATE TABLE IF NOT EXISTS `ai_knowledge_query_log` (
  `log_id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `query_text`       VARCHAR(512) NOT NULL COMMENT '脱敏并截断后的问句文本',
  `query_hash`       CHAR(32)     NOT NULL COMMENT '归一化问句 SHA-256 前32位（分组键）',
  `scene`            VARCHAR(32)  NOT NULL DEFAULT 'other' COMMENT '调用场景 agent/voice_robot/copilot/other',
  `hit_count`        INT          NOT NULL DEFAULT 0 COMMENT '命中知识分块数',
  `top_knowledge_id` BIGINT       DEFAULT NULL COMMENT '最高分命中知识ID（无命中为空）',
  `route`            VARCHAR(16)  NOT NULL DEFAULT 'none' COMMENT '命中路由 fused/vector/keyword/none',
  `latency_ms`       INT          NOT NULL DEFAULT 0 COMMENT '检索耗时（毫秒）',
  `converted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已转知识草稿：0否 1是',
  `create_time`      DATETIME     NOT NULL COMMENT '记录时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_kql_create_time` (`create_time`),
  KEY `idx_kql_hash_time` (`query_hash`, `create_time`),
  KEY `idx_kql_hit_time` (`hit_count`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识检索查询日志（F8 运营分析）';
