-- ----------------------------
-- 法律知识库表结构更新
-- ----------------------------

-- 为法律知识库表添加审核状态字段和浏览量字段
ALTER TABLE ai_legal_knowledge 
ADD COLUMN audit_status char(1) DEFAULT '0' COMMENT '审核状态（0待审核 1审核通过 2审核不通过）' AFTER status,
ADD COLUMN view_count int(11) DEFAULT 0 COMMENT '浏览量' AFTER audit_status,
ADD COLUMN audit_by varchar(64) DEFAULT '' COMMENT '审核人' AFTER view_count,
ADD COLUMN audit_time datetime COMMENT '审核时间' AFTER audit_by,
ADD COLUMN audit_remark varchar(500) DEFAULT NULL COMMENT '审核备注' AFTER audit_time;

-- 更新现有数据的审核状态为已审核通过
UPDATE ai_legal_knowledge SET audit_status = '1' WHERE audit_status = '0';