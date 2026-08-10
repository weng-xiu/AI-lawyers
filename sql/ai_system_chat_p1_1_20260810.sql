-- P1-1 图文对话服务：会话表 + 消息表 + 菜单配置
-- 创建日期：2026-08-10

-- ============================================
-- 1. 图文会话表 ai_chat_session
-- ============================================
DROP TABLE IF EXISTS ai_chat_session;
CREATE TABLE ai_chat_session (
  session_id        BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  session_no        VARCHAR(64)  DEFAULT NULL            COMMENT '会话编号',
  customer_name     VARCHAR(64)  DEFAULT NULL            COMMENT '客户姓名',
  customer_phone    VARCHAR(20)  DEFAULT NULL            COMMENT '客户电话',
  customer_avatar   VARCHAR(255) DEFAULT NULL            COMMENT '客户头像URL',
  user_id           BIGINT(20)   DEFAULT NULL            COMMENT '受理人ID',
  assignee          VARCHAR(64)  DEFAULT NULL            COMMENT '受理人',
  channel           CHAR(1)      DEFAULT '1'             COMMENT '渠道（1图文 2视频辅助）',
  status            CHAR(1)      DEFAULT '0'             COMMENT '状态（0进行中 1已结束 2转人工）',
  last_message      VARCHAR(500) DEFAULT NULL            COMMENT '最后一条消息摘要',
  last_message_time DATETIME     DEFAULT NULL            COMMENT '最后消息时间',
  unread_count      INT(11)      DEFAULT 0              COMMENT '未读消息数',
  create_by         VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time       DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by         VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time       DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark            VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (session_id),
  KEY idx_ai_chat_session_no (session_no),
  KEY idx_ai_chat_session_assignee (assignee),
  KEY idx_ai_chat_session_status (status),
  KEY idx_ai_chat_session_time (last_message_time),
  KEY idx_ai_chat_session_phone (customer_phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图文会话表';

-- ============================================
-- 2. 聊天消息表 ai_chat_message
-- ============================================
DROP TABLE IF EXISTS ai_chat_message;
CREATE TABLE ai_chat_message (
  message_id     BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  session_id     BIGINT(20)   NOT NULL                COMMENT '会话ID',
  sender_type    CHAR(1)      DEFAULT '1'             COMMENT '发送者类型（1客户 2客服 3系统）',
  sender_name    VARCHAR(64)  DEFAULT NULL            COMMENT '发送人名称',
  content        TEXT         DEFAULT NULL            COMMENT '消息内容',
  msg_type       CHAR(1)      DEFAULT '1'             COMMENT '消息类型（1文字 2图片 3文件）',
  attachment_url VARCHAR(255) DEFAULT NULL            COMMENT '附件URL',
  is_read        CHAR(1)      DEFAULT '0'             COMMENT '是否已读（0未读 1已读）',
  send_time      DATETIME     DEFAULT NULL            COMMENT '发送时间',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (message_id),
  KEY idx_ai_chat_msg_session (session_id),
  KEY idx_ai_chat_msg_send_time (send_time),
  KEY idx_ai_chat_msg_read (is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- ============================================
-- 3. 系统菜单配置（话务系统3500下）
-- ============================================
-- 图文对话服务菜单
INSERT INTO sys_menu VALUES('3644', '图文对话服务', '3500', '4', 'chat', 'lawyers/chat/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:chat:list', 'chat', 'admin', sysdate(), '', null, '图文对话服务菜单');
INSERT INTO sys_menu VALUES('3645', '图文对话查询', '3644', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3646', '图文对话回复', '3644', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:send', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3647', '图文对话转接', '3644', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:transfer', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3648', '图文对话关闭', '3644', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:close', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3649', '图文对话导出', '3644', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:chat:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3644);
INSERT INTO sys_role_menu VALUES (1, 3645);
INSERT INTO sys_role_menu VALUES (1, 3646);
INSERT INTO sys_role_menu VALUES (1, 3647);
INSERT INTO sys_role_menu VALUES (1, 3648);
INSERT INTO sys_role_menu VALUES (1, 3649);
