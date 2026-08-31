-- =====================================================================
-- T5-3 消息中心（站内信）ai_message 建表 + 菜单/权限
-- 二期健壮与提效方案 20260830
-- 说明：待办/预警/回访到期/质检驳回/工单分配等事件经 T2-3 Stream 队列
--       (message-notify) 异步入库生成站内信，顶部铃铛 + 列表页查看。
-- =====================================================================

-- 1. 站内信表
CREATE TABLE IF NOT EXISTS `ai_message` (
  `message_id`        bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `receiver_user_id`  bigint(20)    NOT NULL COMMENT '接收人用户ID（sys_user.user_id）',
  `msg_type`          char(1)       DEFAULT '1' COMMENT '消息类型（1系统通知 2待办提醒 3风险预警 4质检通知 5工单通知 9其他）',
  `title`             varchar(200)  NOT NULL COMMENT '消息标题',
  `content`           varchar(1000) DEFAULT '' COMMENT '消息内容',
  `biz_type`          varchar(32)   DEFAULT NULL COMMENT '业务类型（quality/ticket/warning/outbound 等，用于跳转）',
  `biz_id`            bigint(20)    DEFAULT NULL COMMENT '业务ID（跳转目标主键）',
  `is_read`           char(1)       DEFAULT '0' COMMENT '是否已读（0未读 1已读）',
  `read_time`         datetime      DEFAULT NULL COMMENT '阅读时间',
  `sender`            varchar(64)   DEFAULT 'system' COMMENT '发送方（system/质检驳回人等）',
  `priority`          char(1)       DEFAULT '2' COMMENT '优先级（1高 2中 3低）',
  `create_by`         varchar(64)   DEFAULT '',
  `create_time`       datetime      DEFAULT NULL,
  `update_by`         varchar(64)   DEFAULT '',
  `update_time`       datetime      DEFAULT NULL,
  `remark`            varchar(500)  DEFAULT NULL,
  PRIMARY KEY (`message_id`),
  KEY `idx_receiver_read` (`receiver_user_id`, `is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息中心-站内信';

-- 2. 菜单：消息中心（挂到"资源管理"目录 parent_id=3670，路由 /lawyers/message）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3880, '消息中心', 3670, 62, 'message', 'lawyers/message/index', 1, 0, 'C', '0', '0', 'lawyers:message:list', 'message', 'admin', sysdate(), '消息中心-站内信列表'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3880);

-- 2.0 超级管理员角色授权（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id BETWEEN 3880 AND 3883
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = sys_menu.menu_id);

-- 2.1 消息查询（列表/未读数）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3881, '消息查询', 3880, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:message:query', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3881);

-- 2.2 标记已读/全部已读
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3882, '消息已读', 3880, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:message:read', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3882);

-- 2.3 删除消息
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3883, '消息删除', 3880, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:message:remove', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3883);
