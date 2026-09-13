-- =====================================================================
-- 第十五部分 F 组功能性优化 —— 数据库迁移脚本（可先行落地部分）
-- 日期: 2026-09-13
-- 说明: 对应《AI律师话务系统综合文档》第十五部分：
--   F1 语种偏好字段 / F2 关怀模式偏好 / F3 司法行政业务联动与 12345 协同
--   F6 多渠道统一接入与身份打通 / F7 知识库权威溯源与审核流 / F9 SLA 策略
-- 特性: 全部向前兼容、可重复执行（ALTER/INSERT 均做存在性判断）。
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- =====================================================================
-- 一、加列（F1/F2：ai_caller_profile；F3：ai_call_ticket；F7：ai_legal_knowledge）
-- =====================================================================

-- F1/F2：来电人档案增加语种偏好、关怀模式偏好
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_caller_profile' AND column_name = 'language_preference');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_caller_profile ADD COLUMN language_preference VARCHAR(10) DEFAULT ''zh-CN'' COMMENT ''语种偏好 zh-CN普通话 yue-CN粤语'' AFTER consult_preference',
    'SELECT ''ai_caller_profile.language_preference already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_caller_profile' AND column_name = 'care_mode');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_caller_profile ADD COLUMN care_mode TINYINT DEFAULT 0 COMMENT ''关怀模式偏好 0标准 1关怀'' AFTER language_preference',
    'SELECT ''ai_caller_profile.care_mode already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- F3：工单增加外部协同字段（法援/调解/公证/鉴定/仲裁/12345）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'external_type');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN external_type VARCHAR(20) DEFAULT NULL COMMENT ''外部条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345'' AFTER close_time',
    'SELECT ''ai_call_ticket.external_type already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'external_org_id');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN external_org_id BIGINT DEFAULT NULL COMMENT ''协同机构ID'' AFTER external_type',
    'SELECT ''ai_call_ticket.external_org_id already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'external_ticket_no');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN external_ticket_no VARCHAR(64) DEFAULT NULL COMMENT ''外部工单号'' AFTER external_org_id',
    'SELECT ''ai_call_ticket.external_ticket_no already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'external_status');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN external_status VARCHAR(20) DEFAULT NULL COMMENT ''外部状态 PENDING/ACCEPTED/PROCESSING/DONE/REJECTED/FAILED'' AFTER external_ticket_no',
    'SELECT ''ai_call_ticket.external_status already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'external_update_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN external_update_time DATETIME DEFAULT NULL COMMENT ''外部状态最近回写时间'' AFTER external_status',
    'SELECT ''ai_call_ticket.external_update_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'transfer_time');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN transfer_time DATETIME DEFAULT NULL COMMENT ''最近转出/接收时间'' AFTER external_update_time',
    'SELECT ''ai_call_ticket.transfer_time already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_call_ticket' AND column_name = 'direction');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_call_ticket ADD COLUMN direction VARCHAR(8) DEFAULT NULL COMMENT ''协同方向 OUT转出 IN转入'' AFTER transfer_time',
    'SELECT ''ai_call_ticket.direction already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- F7：法律知识增加权威溯源元数据
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'law_name');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN law_name VARCHAR(200) DEFAULT NULL COMMENT ''法律名称'' AFTER law_article',
    'SELECT ''ai_legal_knowledge.law_name already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'article_no');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN article_no VARCHAR(50) DEFAULT NULL COMMENT ''条号'' AFTER law_name',
    'SELECT ''ai_legal_knowledge.article_no already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'issuing_authority');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN issuing_authority VARCHAR(200) DEFAULT NULL COMMENT ''发布机关'' AFTER article_no',
    'SELECT ''ai_legal_knowledge.issuing_authority already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'effective_date');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN effective_date DATE DEFAULT NULL COMMENT ''施行日期'' AFTER issuing_authority',
    'SELECT ''ai_legal_knowledge.effective_date already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'valid_status');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN valid_status VARCHAR(8) DEFAULT ''1'' COMMENT ''效力状态 1现行有效 2已修订 3已失效'' AFTER effective_date',
    'SELECT ''ai_legal_knowledge.valid_status already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'source_url');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN source_url VARCHAR(500) DEFAULT NULL COMMENT ''权威来源链接'' AFTER valid_status',
    'SELECT ''ai_legal_knowledge.source_url already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_legal_knowledge' AND column_name = 'publish_version');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_legal_knowledge ADD COLUMN publish_version INT DEFAULT 1 COMMENT ''发布版本号'' AFTER source_url',
    'SELECT ''ai_legal_knowledge.publish_version already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================
-- 二、新建表
-- =====================================================================

-- F3：协同机构台账（法援/人民调解/公证/司法鉴定/仲裁/12345）
CREATE TABLE IF NOT EXISTS ai_external_org (
  org_id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  org_name      VARCHAR(200) NOT NULL COMMENT '机构名称',
  external_type VARCHAR(20) NOT NULL COMMENT '条线类型 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345',
  province      VARCHAR(50) DEFAULT NULL COMMENT '省',
  city          VARCHAR(50) DEFAULT NULL COMMENT '市',
  district      VARCHAR(50) DEFAULT NULL COMMENT '区县',
  address       VARCHAR(300) DEFAULT NULL COMMENT '机构地址',
  contact_phone VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
  contact_person VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  access_mode   VARCHAR(10) NOT NULL DEFAULT 'MANUAL' COMMENT '对接方式 API接口 FILE文件 MANUAL人工台账',
  api_url       VARCHAR(500) DEFAULT NULL COMMENT '协同接口地址（API方式）',
  app_id        VARCHAR(64) DEFAULT NULL COMMENT '对接AppId（API方式）',
  app_secret    VARCHAR(200) DEFAULT NULL COMMENT '对接密钥（加密存储，P3-G1同步加密）',
  service_hours VARCHAR(100) DEFAULT NULL COMMENT '服务时间',
  apply_materials VARCHAR(1000) DEFAULT NULL COMMENT '申请材料清单（转介指引展示）',
  status        CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  create_by     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time   DATETIME COMMENT '创建时间',
  update_by     VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time   DATETIME COMMENT '更新时间',
  remark        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (org_id),
  KEY idx_org_type_region (external_type, province, city, district)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='协同外部机构台账（F3）';

-- F3：工单转办流水（双向：12348转出 / 外部转入）
CREATE TABLE IF NOT EXISTS ai_ticket_transfer (
  transfer_id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '转办流水ID',
  ticket_id          BIGINT DEFAULT NULL COMMENT '本系统工单ID（转入建单后回填）',
  ticket_no          VARCHAR(64) DEFAULT NULL COMMENT '本系统工单号（冗余）',
  direction          VARCHAR(8) NOT NULL COMMENT '方向 OUT转出 IN转入',
  external_type      VARCHAR(20) NOT NULL COMMENT '外部条线',
  org_id             BIGINT DEFAULT NULL COMMENT '协同机构ID',
  org_name           VARCHAR(200) DEFAULT NULL COMMENT '协同机构名称（冗余）',
  external_ticket_no VARCHAR(64) DEFAULT NULL COMMENT '外部工单号（回写/来单携带）',
  external_status    VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '外部状态',
  transfer_status    CHAR(1) NOT NULL DEFAULT '0' COMMENT '流水状态 0处理中 1成功 2失败 3人工处理',
  request_payload   TEXT COMMENT '转出/来单请求报文（最小必要字段）',
  callback_payload  TEXT COMMENT '最近回调报文留痕',
  idempotent_key    VARCHAR(80) NOT NULL COMMENT '幂等键（调用方生成，唯一）',
  fail_reason       VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  retry_count       INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  next_retry_time   DATETIME DEFAULT NULL COMMENT '下次重试时间',
  transfer_time     DATETIME COMMENT '发起/接收时间',
  callback_time     DATETIME COMMENT '最近回调时间',
  create_by         VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time       DATETIME COMMENT '创建时间',
  update_by         VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time       DATETIME COMMENT '更新时间',
  PRIMARY KEY (transfer_id),
  UNIQUE KEY uk_idempotent_key (idempotent_key),
  KEY idx_ticket_id (ticket_id),
  KEY idx_external_no (external_type, external_ticket_no),
  KEY idx_next_retry (transfer_status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单跨域转办流水（F3）';

-- F6：渠道身份绑定表
CREATE TABLE IF NOT EXISTS ai_channel_identity (
  id                BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  profile_id        BIGINT NOT NULL COMMENT '来电档案ID（ai_caller_profile）',
  channel_type      VARCHAR(20) NOT NULL COMMENT '渠道 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB',
  channel_uid       VARCHAR(200) NOT NULL COMMENT '渠道身份标识 openid/unionid/账号（加密存储随P3-G1）',
  channel_nickname  VARCHAR(100) DEFAULT NULL COMMENT '渠道昵称',
  bind_status       CHAR(1) NOT NULL DEFAULT '0' COMMENT '绑定状态 0已绑定 1已解绑',
  bind_confirm_time DATETIME COMMENT '绑定二次确认时间',
  unbind_time       DATETIME COMMENT '解绑时间',
  create_by         VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time       DATETIME COMMENT '创建时间',
  update_by         VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time       DATETIME COMMENT '更新时间',
  remark            VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_channel_uid (channel_type, channel_uid),
  KEY idx_profile_id (profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多渠道身份绑定（F6）';

-- F6：统一会话索引
CREATE TABLE IF NOT EXISTS ai_unified_session (
  session_id   BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话索引ID',
  profile_id   BIGINT DEFAULT NULL COMMENT '来电档案ID（可空，未建档渠道会话）',
  caller_number VARCHAR(20) DEFAULT NULL COMMENT '手机号（时间线聚合兜底键）',
  channel_type VARCHAR(20) NOT NULL COMMENT '渠道 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB',
  biz_type     VARCHAR(10) NOT NULL COMMENT '业务类型 CALL/CHAT/VIDEO/IVR/MESSAGE/TICKET',
  biz_id       VARCHAR(64) NOT NULL COMMENT '业务主键（各业务表ID/会话号）',
  biz_title    VARCHAR(200) DEFAULT NULL COMMENT '业务摘要（时间线展示）',
  start_time   DATETIME COMMENT '会话开始时间',
  end_time     DATETIME COMMENT '会话结束时间',
  create_time  DATETIME COMMENT '索引创建时间',
  PRIMARY KEY (session_id),
  UNIQUE KEY uk_biz (biz_type, biz_id),
  KEY idx_profile_start (profile_id, start_time),
  KEY idx_caller_start (caller_number, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨渠道统一会话索引（F6）';

-- F9：SLA 策略
CREATE TABLE IF NOT EXISTS ai_sla_policy (
  policy_id        BIGINT NOT NULL AUTO_INCREMENT COMMENT '策略ID',
  policy_name      VARCHAR(100) NOT NULL COMMENT '策略名称',
  biz_type         VARCHAR(20) NOT NULL COMMENT '业务类型 TICKET/LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345',
  priority         CHAR(1) NOT NULL DEFAULT '2' COMMENT '优先级 1紧急 2普通 3低',
  respond_minutes  INT NOT NULL DEFAULT 60 COMMENT '响应时限（分钟）',
  resolve_minutes  INT NOT NULL DEFAULT 1440 COMMENT '办结时限（分钟）',
  warn_threshold   INT NOT NULL DEFAULT 80 COMMENT '预警阈值（百分比，时限消耗达该比例预警）',
  escalate_roles   VARCHAR(300) DEFAULT NULL COMMENT '逐级升级角色链（逗号分隔角色key，如 team_leader,manager,director）',
  status           CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  create_by        VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time      DATETIME COMMENT '创建时间',
  update_by        VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time      DATETIME COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (policy_id),
  UNIQUE KEY uk_biz_priority (biz_type, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单SLA策略配置（F9）';

-- F7：知识审核流水
CREATE TABLE IF NOT EXISTS ai_knowledge_audit (
  audit_id      BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核流水ID',
  knowledge_id  BIGINT NOT NULL COMMENT '知识ID',
  publish_version INT DEFAULT 1 COMMENT '对应版本号',
  action        VARCHAR(10) NOT NULL COMMENT '动作 SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE',
  from_status   VARCHAR(10) DEFAULT NULL COMMENT '变更前审核状态',
  to_status     VARCHAR(10) DEFAULT NULL COMMENT '变更后审核状态',
  audit_opinion VARCHAR(1000) DEFAULT NULL COMMENT '审核意见',
  auditor       VARCHAR(64) DEFAULT NULL COMMENT '审核人',
  audit_time    DATETIME COMMENT '审核时间',
  create_by     VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time   DATETIME COMMENT '创建时间',
  PRIMARY KEY (audit_id),
  KEY idx_knowledge_id (knowledge_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库审核发布流水（F7）';

-- =====================================================================
-- 三、字典数据
-- =====================================================================

INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '语种偏好', 'ai_language_pref', '0', 'admin', NOW(), 'F1 普通话/粤语'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_language_pref');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '接入渠道类型', 'ai_channel_type', '0', 'admin', NOW(), 'F6 多渠道身份'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_channel_type');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '司法行政业务条线', 'ai_external_type', '0', 'admin', NOW(), 'F3 法援/调解/公证/鉴定/仲裁/12345'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_external_type');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '外部协同状态', 'ai_external_status', '0', 'admin', NOW(), 'F3 外部工单状态'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_external_status');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '转办流水状态', 'ai_transfer_status', '0', 'admin', NOW(), 'F3'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_transfer_status');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '机构对接方式', 'ai_org_access_mode', '0', 'admin', NOW(), 'F3'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_org_access_mode');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '法条效力状态', 'ai_knowledge_valid', '0', 'admin', NOW(), 'F7'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_knowledge_valid');
INSERT INTO sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
SELECT '知识审核动作', 'ai_knowledge_audit_action', '0', 'admin', NOW(), 'F7'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_type = 'ai_knowledge_audit_action');

-- 字典数据（按 dict_type+dict_value 幂等）
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '普通话' AS b, 'zh-CN' AS c, 'ai_language_pref' AS d, '' AS e, 'primary' AS f, 'Y' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '粤语', 'yue-CN', 'ai_language_pref', '', 'success', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_language_pref' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '电话' AS b, 'PHONE' AS c, 'ai_channel_type' AS d, '' AS e, 'primary' AS f, 'Y' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '微信公众号', 'WECHAT_MP', 'ai_channel_type', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '微信小程序', 'WECHAT_MINI', 'ai_channel_type', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 4, 'H5', 'H5', 'ai_channel_type', '', 'info', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 5, '网站', 'WEB', 'ai_channel_type', '', 'info', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_channel_type' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '法律援助' AS b, 'LEGAL_AID' AS c, 'ai_external_type' AS d, '' AS e, 'primary' AS f, 'N' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '人民调解', 'MEDIATION', 'ai_external_type', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '公证', 'NOTARY', 'ai_external_type', '', 'info', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 4, '司法鉴定', 'FORENSIC', 'ai_external_type', '', 'warning', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 5, '仲裁', 'ARBITRATION', 'ai_external_type', '', 'danger', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 6, '12345政务热线', 'HOTLINE_12345', 'ai_external_type', '', 'danger', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_external_type' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '待受理' AS b, 'PENDING' AS c, 'ai_external_status' AS d, '' AS e, 'info' AS f, 'Y' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '已受理', 'ACCEPTED', 'ai_external_status', '', 'primary', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '办理中', 'PROCESSING', 'ai_external_status', '', 'warning', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 4, '已办结', 'DONE', 'ai_external_status', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 5, '不予受理', 'REJECTED', 'ai_external_status', '', 'danger', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 6, '失败', 'FAILED', 'ai_external_status', '', 'danger', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_external_status' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 0 AS a, '处理中' AS b, '0' AS c, 'ai_transfer_status' AS d, '' AS e, 'warning' AS f, 'Y' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 1, '成功', '1', 'ai_transfer_status', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 2, '失败', '2', 'ai_transfer_status', '', 'danger', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '人工处理', '3', 'ai_transfer_status', '', 'info', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_transfer_status' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '接口对接' AS b, 'API' AS c, 'ai_org_access_mode' AS d, '' AS e, 'success' AS f, 'N' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '文件交换', 'FILE', 'ai_org_access_mode', '', 'warning', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '人工台账', 'MANUAL', 'ai_org_access_mode', '', 'info', 'Y', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_org_access_mode' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '现行有效' AS b, '1' AS c, 'ai_knowledge_valid' AS d, '' AS e, 'success' AS f, 'Y' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '已修订', '2', 'ai_knowledge_valid', '', 'warning', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '已失效', '3', 'ai_knowledge_valid', '', 'danger', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_knowledge_valid' AND d.dict_value=v.c);

INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT 1 AS a, '提交审核' AS b, 'SUBMIT' AS c, 'ai_knowledge_audit_action' AS d, '' AS e, 'info' AS f, 'N' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '' AS k UNION ALL
  SELECT 2, '审核通过', 'APPROVE', 'ai_knowledge_audit_action', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 3, '审核驳回', 'REJECT', 'ai_knowledge_audit_action', '', 'danger', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 4, '发布', 'PUBLISH', 'ai_knowledge_audit_action', '', 'success', 'N', '0', 'admin', NOW(), '' UNION ALL
  SELECT 5, '下线', 'OFFLINE', 'ai_knowledge_audit_action', '', 'warning', 'N', '0', 'admin', NOW(), ''
) v
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_data d WHERE d.dict_type='ai_knowledge_audit_action' AND d.dict_value=v.c);

-- =====================================================================
-- 四、菜单（3900-3960，超管 role_id=1 自动授权）
-- =====================================================================

-- 顶级目录
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3900, '法服协同', 0, 60, '/collab', NULL, 1, 0, 'M', '0', '0', NULL, 'guide', 'admin', NOW(), '公共法律服务协同（第十五部分F组）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3900);

-- 3910 协同机构台账
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3910, '协同机构台账', 3900, 1, 'externalOrg', 'lawyers/collab/externalOrg/index', 1, 0, 'C', '0', '0', 'lawyers:externalOrg:list', 'peoples', 'admin', NOW(), '法援/调解/公证/鉴定/仲裁/12345机构台账'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3910);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3911, '机构查询', 3910, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:externalOrg:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3911);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3912, '机构新增', 3910, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:externalOrg:add', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3912);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3913, '机构修改', 3910, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:externalOrg:edit', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3913);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3914, '机构删除', 3910, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:externalOrg:remove', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3914);

-- 3920 转办管理
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3920, '转办协同', 3900, 2, 'ticketTransfer', 'lawyers/collab/transfer/index', 1, 0, 'C', '0', '0', 'lawyers:ticketTransfer:list', 'swap', 'admin', NOW(), '司法行政条线/12345工单双向转办'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3920);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3921, '转办查询', 3920, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ticketTransfer:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3921);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3922, '发起转办', 3920, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ticketTransfer:add', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3922);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3923, '转办重试', 3920, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ticketTransfer:retry', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3923);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3924, '转办导出', 3920, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:ticketTransfer:export', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3924);

-- 3930 渠道身份
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3930, '渠道身份', 3900, 3, 'channelIdentity', 'lawyers/collab/channel/index', 1, 0, 'C', '0', '0', 'lawyers:channelIdentity:list', 'wechat', 'admin', NOW(), '多渠道身份绑定查询/解绑'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3930);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3931, '身份查询', 3930, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:channelIdentity:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3931);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3932, '身份解绑', 3930, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:channelIdentity:unbind', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3932);

-- 3940 SLA策略
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3940, 'SLA策略', 3900, 4, 'slaPolicy', 'lawyers/collab/slaPolicy/index', 1, 0, 'C', '0', '0', 'lawyers:slaPolicy:list', 'time-range', 'admin', NOW(), '工单SLA时限与逐级升级策略'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3940);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3941, '策略查询', 3940, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:slaPolicy:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3941);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3942, '策略新增', 3940, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:slaPolicy:add', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3942);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3943, '策略修改', 3940, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:slaPolicy:edit', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3943);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3944, '策略删除', 3940, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:slaPolicy:remove', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3944);

-- 3950 知识审核（独立页面，与知识库管理互补）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3950, '知识审核', 3900, 5, 'knowledgeAudit', 'lawyers/knowledgeAudit/index', 1, 0, 'C', '0', '0', 'lawyers:knowledge:audit:list', 'validCode', 'admin', NOW(), '知识库权威溯源审核发布流'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3950);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3951, '审核查询', 3950, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:knowledge:audit:query', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3951);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3952, '提交审核', 3950, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:knowledge:audit:submit', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3952);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3953, '审核发布', 3950, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:knowledge:audit:approve', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3953);
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3954, '知识下线', 3950, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:knowledge:audit:offline', '#', 'admin', NOW(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3954);

-- 超管授权
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 3900 AND 3954
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=1 AND rm.menu_id=sys_menu.menu_id);

-- =====================================================================
-- 五、种子数据：默认 SLA 策略 + 示例协同机构（示例，可按需调整）
-- =====================================================================

INSERT INTO ai_sla_policy (policy_name, biz_type, priority, respond_minutes, resolve_minutes, warn_threshold, escalate_roles, status, create_by, create_time, remark)
SELECT * FROM (
  SELECT '热线工单-紧急' AS a, 'TICKET' AS b, '1' AS c, 15 AS d, 240 AS e, 80 AS f, 'team_leader,manager' AS g, '0' AS h, 'admin' AS i, NOW() AS j, '紧急工单4小时办结' AS k UNION ALL
  SELECT '热线工单-普通', 'TICKET', '2', 60, 1440, 80, 'team_leader,manager,director', '0', 'admin', NOW(), '普通工单24小时办结' UNION ALL
  SELECT '热线工单-低', 'TICKET', '3', 240, 4320, 80, 'team_leader', '0', 'admin', NOW(), '低优先级3个工作日' UNION ALL
  SELECT '法援转办默认策略', 'LEGAL_AID', '2', 120, 4320, 80, 'manager,director', '0', 'admin', NOW(), 'F3法援条线'
) s
WHERE NOT EXISTS (SELECT 1 FROM ai_sla_policy p WHERE p.biz_type=s.b AND p.priority=s.c);

INSERT INTO ai_external_org (org_name, external_type, province, city, district, address, contact_phone, access_mode, status, service_hours, apply_materials, create_by, create_time, remark)
SELECT * FROM (
  SELECT '广东省法律援助局' AS a, 'LEGAL_AID' AS b, '广东省' AS c, '广州市' AS d, '越秀区' AS e, '广州市越秀区' AS f, '020-12348' AS g, 'MANUAL' AS h, '0' AS i, '工作日 9:00-12:00 14:00-18:00' AS j, '身份证/经济困难证明/案件材料' AS k, 'admin' AS l, NOW() AS m, '示例机构请按实际维护' AS n UNION ALL
  SELECT '广州市法律援助处', 'LEGAL_AID', '广东省', '广州市', '越秀区', '广州市越秀区', '020-12348', 'MANUAL', '0', '工作日 9:00-12:00 14:00-18:00', '身份证/经济困难证明/案件材料', 'admin', NOW(), '示例机构请按实际维护' UNION ALL
  SELECT '广州市政务服务便民热线', 'HOTLINE_12345', '广东省', '广州市', NULL, NULL, '12345', 'MANUAL', '0', '7×24小时', NULL, 'admin', NOW(), '12345双号并行协同（国办发〔2020〕53号）'
) s
WHERE NOT EXISTS (SELECT 1 FROM ai_external_org o WHERE o.org_name=s.a AND o.external_type=s.b);

-- =====================================================================
-- 六、验证
-- =====================================================================
SELECT 'ai_caller_profile' AS t, COUNT(*) AS new_cols FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='ai_caller_profile' AND column_name IN ('language_preference','care_mode')
UNION ALL
SELECT 'ai_call_ticket', COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='ai_call_ticket' AND column_name IN ('external_type','external_org_id','external_ticket_no','external_status','external_update_time','transfer_time','direction')
UNION ALL
SELECT 'ai_legal_knowledge', COUNT(*) FROM information_schema.columns
  WHERE table_schema=DATABASE() AND table_name='ai_legal_knowledge' AND column_name IN ('law_name','article_no','issuing_authority','effective_date','valid_status','source_url','publish_version');

SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('ai_external_org','ai_ticket_transfer','ai_channel_identity','ai_unified_session','ai_sla_policy','ai_knowledge_audit')
ORDER BY table_name;
