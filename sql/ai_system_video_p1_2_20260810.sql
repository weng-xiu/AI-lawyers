-- P1-2 视频咨询服务：视频咨询表 + 事件日志表 + 菜单配置
-- 创建日期：2026-08-10

-- ============================================
-- 1. 视频咨询表 ai_video_consult
-- ============================================
DROP TABLE IF EXISTS ai_video_consult;
CREATE TABLE ai_video_consult (
  consult_id       BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '视频咨询ID',
  consult_no       VARCHAR(64)  DEFAULT NULL            COMMENT '咨询编号',
  customer_name    VARCHAR(64)  DEFAULT NULL            COMMENT '客户姓名',
  customer_phone   VARCHAR(20)  DEFAULT NULL            COMMENT '客户电话',
  agent_id         BIGINT(20)   DEFAULT NULL            COMMENT '坐席ID',
  agent_name       VARCHAR(64)  DEFAULT NULL            COMMENT '坐席名称',
  room_no          VARCHAR(64)  DEFAULT NULL            COMMENT '房间号',
  start_time       DATETIME     DEFAULT NULL            COMMENT '开始时间',
  end_time         DATETIME     DEFAULT NULL            COMMENT '结束时间',
  duration         INT(11)      DEFAULT 0              COMMENT '通话时长(秒)',
  status           CHAR(1)      DEFAULT '0'             COMMENT '状态（0等待中 1进行中 2已结束 3已取消）',
  satisfaction     INT(11)      DEFAULT NULL            COMMENT '满意度(1-5)',
  recording_url    VARCHAR(255) DEFAULT NULL            COMMENT '录像地址',
  identity_verify  CHAR(1)      DEFAULT '0'             COMMENT '是否已身份核验（0否 1是）',
  create_by        VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by        VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (consult_id),
  KEY idx_avc_no (consult_no),
  KEY idx_avc_agent (agent_id),
  KEY idx_avc_status (status),
  KEY idx_avc_time (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频咨询表';

-- ============================================
-- 2. 视频咨询事件日志表 ai_video_consult_log
-- ============================================
DROP TABLE IF EXISTS ai_video_consult_log;
CREATE TABLE ai_video_consult_log (
  log_id          BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  consult_id      BIGINT(20)   NOT NULL                COMMENT '视频咨询ID',
  event_type      VARCHAR(32)  DEFAULT NULL            COMMENT '事件类型（join离开 leave加入 share屏幕共享 error异常 disconnect断开）',
  event_content   VARCHAR(255) DEFAULT NULL            COMMENT '事件描述',
  event_time      DATETIME     DEFAULT NULL            COMMENT '事件时间',
  create_time     DATETIME     DEFAULT NULL            COMMENT '创建时间',
  PRIMARY KEY (log_id),
  KEY idx_avcl_consult (consult_id),
  KEY idx_avcl_time (event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频咨询事件日志表';

-- ============================================
-- 3. 系统菜单配置
-- ============================================
INSERT INTO sys_menu VALUES('3650', '视频咨询服务', '3500', '5', 'video', 'lawyers/videoConsult/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:videoConsult:list', 'video', 'admin', sysdate(), '', null, '视频咨询服务菜单');
INSERT INTO sys_menu VALUES('3651', '视频咨询查询', '3650', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3652', '视频咨询创建', '3650', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3653', '视频咨询编辑', '3650', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3654', '视频咨询删除', '3650', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3655', '视频咨询导出', '3650', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:videoConsult:export', '#', 'admin', sysdate(), '', null, '');

INSERT INTO sys_role_menu VALUES (1, 3650);
INSERT INTO sys_role_menu VALUES (1, 3651);
INSERT INTO sys_role_menu VALUES (1, 3652);
INSERT INTO sys_role_menu VALUES (1, 3653);
INSERT INTO sys_role_menu VALUES (1, 3654);
INSERT INTO sys_role_menu VALUES (1, 3655);
