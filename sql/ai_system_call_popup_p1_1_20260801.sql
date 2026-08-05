-- P1-1 来电弹屏：来电人档案表 + 菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. 来电人档案表 ai_caller_profile
-- ============================================
DROP TABLE IF EXISTS ai_caller_profile;
CREATE TABLE ai_caller_profile (
  profile_id          BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  caller_number       VARCHAR(30)   NOT NULL                COMMENT '来电号码',
  caller_name         VARCHAR(50)   DEFAULT NULL            COMMENT '来电人姓名',
  caller_gender       CHAR(1)       DEFAULT '2'             COMMENT '性别（0男 1女 2未知）',
  caller_age          INT(3)        DEFAULT NULL            COMMENT '年龄',
  caller_id_card      VARCHAR(20)   DEFAULT NULL            COMMENT '身份证号',
  caller_address      VARCHAR(255)  DEFAULT NULL            COMMENT '联系地址',
  customer_level      VARCHAR(50)   DEFAULT NULL            COMMENT '客户等级',
  tags                VARCHAR(500)  DEFAULT NULL            COMMENT '标签（逗号分隔）',
  intent_prediction   VARCHAR(100)  DEFAULT NULL            COMMENT '意图预测',
  intent_confidence   INT(3)        DEFAULT 0               COMMENT '意图置信度',
  consult_preference  VARCHAR(100)  DEFAULT NULL            COMMENT '咨询偏好',
  high_freq_problem   VARCHAR(100)  DEFAULT NULL            COMMENT '高频问题',
  freq_mention_count  INT(5)        DEFAULT 0               COMMENT '高频提及次数',
  risk_level          CHAR(1)       DEFAULT '0'             COMMENT '风险等级（0低 1中 2高）',
  emotion_status      VARCHAR(50)   DEFAULT NULL            COMMENT '情绪状态',
  emotion_warning     VARCHAR(500)  DEFAULT NULL            COMMENT '情绪预警',
  last_call_time      DATETIME      DEFAULT NULL            COMMENT '最后来电时间',
  create_by           VARCHAR(64)   DEFAULT ''              COMMENT '创建者',
  create_time         DATETIME      DEFAULT NULL            COMMENT '创建时间',
  update_by           VARCHAR(64)   DEFAULT ''              COMMENT '更新者',
  update_time         DATETIME      DEFAULT NULL            COMMENT '更新时间',
  remark              VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (profile_id),
  UNIQUE KEY uk_ai_caller_profile_number (caller_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来电人档案表';

-- ============================================
-- 2. 系统菜单配置（话务系统3500下，3644+）
-- ============================================
INSERT INTO sys_menu VALUES('3644', '来电弹屏', '3500', '4', 'callPopup', 'lawyers/callCenter/callPopup', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:popup:query', 'phone', 'admin', sysdate(), '', null, '来电弹屏菜单');
INSERT INTO sys_menu VALUES('3645', '弹屏查询', '3644', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:popup:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3646', '弹屏编辑', '3644', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:popup:edit', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 3. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3644);
INSERT INTO sys_role_menu VALUES (1, 3645);
INSERT INTO sys_role_menu VALUES (1, 3646);
