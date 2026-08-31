-- =====================================================================
-- 二期 T3 RAG 混合检索 - 知识库分块表
-- 日期：2026-08-30
-- 说明：
--   1) 将审核通过的法律知识按标题/法条 + 正文切分为语义分块；
--   2) chunk_content 建 ngram FULLTEXT 索引（MySQL 5.7.6+ 内置 ngram 解析器，
--      支持中文二元分词），作为"关键词路"召回；应用层向量近邻作为"向量路"召回，
--      两路 RRF 融合；embedding 向量同时落 LONGBLOB 备份/重启重建内存索引用；
--   3) 向前兼容：只新增表，不改动既有 ai_legal_knowledge 结构。
--   注：ngram_token_size 默认 2；如 MySQL 配置改动需在 my.cnf 设置
--      ngram_token_size=2 后重启。FULLTEXT 建索引要求表引擎为 InnoDB（5.6+ 支持）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `ai_legal_knowledge_chunk` (
  `chunk_id`      bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '分块ID',
  `knowledge_id`  bigint(20)   NOT NULL COMMENT '所属知识ID',
  `title`         varchar(200) DEFAULT '' COMMENT '知识标题（冗余，便于溯源展示）',
  `chunk_index`   int(11)      NOT NULL DEFAULT 0 COMMENT '分块序号（知识内从0开始）',
  `chunk_content` text         NOT NULL COMMENT '分块文本（标题/法条 + 正文片段）',
  `law_article`   varchar(500) DEFAULT NULL COMMENT '法律条文（冗余溯源）',
  `source`        varchar(200) DEFAULT NULL COMMENT '出处来源（冗余溯源）',
  `category_id`   bigint(20)   DEFAULT NULL COMMENT '分类ID（冗余，便于按类过滤）',
  `embedding`     longblob     DEFAULT NULL COMMENT '向量（float[] 小端字节，备份/重建内存索引用）',
  `embedding_dim` int(11)      DEFAULT 0 COMMENT '向量维度（0=未向量化）',
  `status`        char(1)      DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by`     varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
  `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`chunk_id`),
  KEY `idx_knowledge_id` (`knowledge_id`),
  KEY `idx_category_id` (`category_id`),
  FULLTEXT KEY `ft_chunk_content` (`chunk_content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律知识分块表（T3 RAG）';

-- 可选：若部署环境 MySQL 未编译 ngram 解析器导致上面建表失败，
-- 可改用下面不带 FULLTEXT 的建表语句，检索会自动降级为 LIKE 关键词路：
--
-- CREATE TABLE IF NOT EXISTS `ai_legal_knowledge_chunk` (
--   `chunk_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '分块ID',
--   `knowledge_id` bigint(20) NOT NULL COMMENT '所属知识ID',
--   `title` varchar(200) DEFAULT '' COMMENT '知识标题',
--   `chunk_index` int(11) NOT NULL DEFAULT 0 COMMENT '分块序号',
--   `chunk_content` text NOT NULL COMMENT '分块文本',
--   `law_article` varchar(500) DEFAULT NULL COMMENT '法律条文',
--   `source` varchar(200) DEFAULT NULL COMMENT '出处来源',
--   `category_id` bigint(20) DEFAULT NULL COMMENT '分类ID',
--   `embedding` longblob DEFAULT NULL COMMENT '向量备份',
--   `embedding_dim` int(11) DEFAULT 0 COMMENT '向量维度',
--   `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
--   `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
--   `create_time` datetime DEFAULT NULL COMMENT '创建时间',
--   `update_time` datetime DEFAULT NULL COMMENT '更新时间',
--   PRIMARY KEY (`chunk_id`),
--   KEY `idx_knowledge_id` (`knowledge_id`),
--   KEY `idx_category_id` (`category_id`)
-- ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='法律知识分块表（T3 RAG，无FULLTEXT降级版）';
