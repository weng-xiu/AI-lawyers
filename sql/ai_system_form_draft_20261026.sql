-- U4 表单防丢失：服务端草稿
-- 按「用户 + 表单标识」唯一；页面自动保存时覆盖写入，提交成功/用户丢弃后删除。
-- 草稿可能包含咨询文本等敏感信息，仅按 user_id 隔离访问，不提供跨用户查询。
CREATE TABLE IF NOT EXISTS `ai_form_draft` (
  `draft_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户ID',
  `form_key`    VARCHAR(128) NOT NULL COMMENT '表单标识（页面:用户名）',
  `content`     LONGTEXT     DEFAULT NULL COMMENT '表单内容（JSON：{ts, data}）',
  `update_time` DATETIME     DEFAULT NULL COMMENT '最后保存时间',
  PRIMARY KEY (`draft_id`),
  UNIQUE KEY `uk_form_draft_user_key` (`user_id`, `form_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单草稿（U4）';
