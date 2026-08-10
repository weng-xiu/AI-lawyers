-- P1-5 智能风险预警管理：预警记录表 + 预警规则表 + 菜单配置
-- 创建日期：2026-08-10

-- ============================================
-- 1. 预警记录表 ai_risk_warning
-- ============================================
DROP TABLE IF EXISTS ai_risk_warning;
CREATE TABLE ai_risk_warning (
  warning_id     BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '预警ID',
  warning_type   VARCHAR(32)  DEFAULT NULL            COMMENT '预警类型（敏感词/情绪异常/异常行为/合规风险）',
  warning_level  CHAR(1)      DEFAULT '3'             COMMENT '预警级别（1高 2中 3低）',
  source_type    VARCHAR(32)  DEFAULT NULL            COMMENT '来源类型（通话/图文/视频）',
  source_id      BIGINT(20)   DEFAULT NULL            COMMENT '来源ID',
  content        TEXT         DEFAULT NULL            COMMENT '触发内容',
  customer_name  VARCHAR(64)  DEFAULT NULL            COMMENT '客户姓名',
  status         CHAR(1)      DEFAULT '0'             COMMENT '状态（0待处理 1处理中 2已处理 3已忽略）',
  handler_name   VARCHAR(64)  DEFAULT NULL            COMMENT '处理人姓名',
  handle_result  VARCHAR(500) DEFAULT NULL            COMMENT '处理结果',
  handle_time    DATETIME     DEFAULT NULL            COMMENT '处理时间',
  trigger_time   DATETIME     DEFAULT NULL            COMMENT '触发时间',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (warning_id),
  KEY idx_ai_risk_warn_type (warning_type),
  KEY idx_ai_risk_warn_level (warning_level),
  KEY idx_ai_risk_warn_status (status),
  KEY idx_ai_risk_warn_trigger (trigger_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能风险预警记录表';

-- ============================================
-- 2. 预警规则表 ai_risk_warning_rule
-- ============================================
DROP TABLE IF EXISTS ai_risk_warning_rule;
CREATE TABLE ai_risk_warning_rule (
  rule_id        BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  rule_name      VARCHAR(100) NOT NULL                COMMENT '规则名称',
  rule_type      VARCHAR(32)  DEFAULT NULL            COMMENT '规则类型（敏感词/情绪异常/异常行为/合规风险）',
  rule_level     CHAR(1)      DEFAULT '3'             COMMENT '规则级别（1高 2中 3低）',
  keywords       VARCHAR(500) DEFAULT NULL            COMMENT '关键词（多个关键词用逗号分隔）',
  is_enabled     CHAR(1)      DEFAULT '1'             COMMENT '是否启用（0禁用 1启用）',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (rule_id),
  KEY idx_ai_risk_rule_type (rule_type),
  KEY idx_ai_risk_rule_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能风险预警规则表';

-- ============================================
-- 3. 系统菜单配置（话务系统3500下，3666+）
-- ============================================
-- 3.1 智能风险预警管理（主菜单）
INSERT INTO sys_menu VALUES('3666', '智能风险预警管理', '3500', '9', 'riskWarning', 'lawyers/riskWarning/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:riskWarning:list', 'warning', 'admin', sysdate(), '', null, '智能风险预警管理菜单');

-- 3.2 预警记录子菜单
INSERT INTO sys_menu VALUES('3667', '预警查询', '3666', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3668', '预警新增', '3666', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3669', '预警修改', '3666', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3670', '预警删除', '3666', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3671', '预警导出', '3666', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:riskWarning:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3666);
INSERT INTO sys_role_menu VALUES (1, 3667);
INSERT INTO sys_role_menu VALUES (1, 3668);
INSERT INTO sys_role_menu VALUES (1, 3669);
INSERT INTO sys_role_menu VALUES (1, 3670);
INSERT INTO sys_role_menu VALUES (1, 3671);

-- ============================================
-- 5. 模拟预警记录数据
-- ============================================
INSERT INTO ai_risk_warning (warning_type, warning_level, source_type, source_id, content, customer_name, status, trigger_time, create_time, remark) VALUES
('敏感词', '1', '通话', 1, '客户在通话中提及了不文明用语，触发了敏感词监控规则。', '张三', '0', '2026-08-10 09:15:00', '2026-08-10 09:15:01', '语音通话中出现敏感词汇'),
('情绪异常', '2', '图文', 2, '客户在图文对话中表现出明显的愤怒情绪，语气激烈。', '李四', '1', '2026-08-10 10:30:00', '2026-08-10 10:30:01', '图文对话中情绪异常'),
('异常行为', '1', '视频', 3, '视频咨询中检测到异常画面，疑似存在不合规行为。', '王五', '0', '2026-08-10 11:00:00', '2026-08-10 11:00:01', '视频咨询检测异常'),
('合规风险', '2', '通话', 4, '通话内容涉及敏感法律建议，可能存在越权风险。', '赵六', '2', '2026-08-10 13:30:00', '2026-08-10 13:30:01', '通话内容合规风险'),
('敏感词', '3', '图文', 5, '图文对话中检测到轻度敏感词。', '孙七', '3', '2026-08-10 14:00:00', '2026-08-10 14:00:01', '轻度敏感词已忽略');

-- ============================================
-- 6. 模拟预警规则数据
-- ============================================
INSERT INTO ai_risk_warning_rule (rule_name, rule_type, rule_level, keywords, is_enabled, create_by, create_time, remark) VALUES
('敏感词过滤规则', '敏感词', '1', '投诉,差评,骗子,退款', '1', 'admin', '2026-08-10 09:00:00', '监控对话中的敏感词汇'),
('情绪异常检测规则', '情绪异常', '2', '我很生气,太失望了,无法接受', '1', 'admin', '2026-08-10 09:00:00', '检测客户情绪波动'),
('异常行为监控规则', '异常行为', '1', '威胁,举报,起诉', '1', 'admin', '2026-08-10 09:00:00', '监控异常行为关键词'),
('合规风险预警规则', '合规风险', '2', '违法,刑期,免于起诉', '1', 'admin', '2026-08-10 09:00:00', '监控法律合规风险关键词');
