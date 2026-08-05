-- P1-2 未接来电：未接来电表 + 菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. 未接来电表 ai_missed_call
-- ============================================
DROP TABLE IF EXISTS ai_missed_call;
CREATE TABLE ai_missed_call (
  missed_call_id   BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '未接来电ID',
  caller_number    VARCHAR(30)  NOT NULL                COMMENT '来电号码',
  caller_name      VARCHAR(50)  DEFAULT NULL            COMMENT '来电人姓名',
  call_time        DATETIME     DEFAULT NULL            COMMENT '来电时间',
  status           CHAR(1)      DEFAULT '0'             COMMENT '状态（0未回拨 1已回拨）',
  callback_time    DATETIME     DEFAULT NULL            COMMENT '回拨时间',
  callback_by      VARCHAR(64)  DEFAULT NULL            COMMENT '回拨人',
  record_id        BIGINT(20)   DEFAULT NULL            COMMENT '关联来电记录ID',
  voice_content    VARCHAR(1000) DEFAULT NULL           COMMENT '语音留言内容',
  voice_duration   INT(5)       DEFAULT NULL            COMMENT '语音留言时长(秒)',
  notice_status    CHAR(1)      DEFAULT '0'             COMMENT '通知状态（0未通知 1已通知）',
  notice_channel   VARCHAR(20)  DEFAULT NULL            COMMENT '通知方式（1短信 2微信 3邮件）',
  create_by        VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by        VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (missed_call_id),
  KEY idx_ai_missed_call_number (caller_number),
  KEY idx_ai_missed_call_time (call_time),
  KEY idx_ai_missed_call_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='未接来电表';

-- ============================================
-- 2. 模拟数据（便于页面预览）
-- ============================================
INSERT INTO ai_missed_call (caller_number, caller_name, call_time, status, callback_time, callback_by, voice_content, voice_duration, notice_status, notice_channel, create_by, create_time, remark) VALUES
('13800138001', '张丽华', '2026-08-01 09:12:30', '1', '2026-08-01 10:05:00', 'admin', '关于离婚财产分割的咨询，请回电', 25, '1', '1', 'admin', '2026-08-01 09:12:30', '已回拨'),
('13900139002', '王志强', '2026-08-01 10:45:18', '0', NULL, NULL, '劳动纠纷咨询，请求尽快回电', 18, '1', '2', 'admin', '2026-08-01 10:45:18', NULL),
('13700137003', '李秀英', '2026-08-01 11:22:00', '0', NULL, NULL, NULL, NULL, '0', NULL, 'admin', '2026-08-01 11:22:00', '无留言'),
('13600136004', '陈国庆', '2026-08-01 13:30:45', '1', '2026-08-01 14:15:22', 'admin', '交通事故赔偿问题', 32, '1', '1', 'admin', '2026-08-01 13:30:45', NULL),
('13500135005', '赵美玲', '2026-08-01 14:50:10', '0', NULL, NULL, '房屋租赁合同纠纷，希望咨询律师', 40, '0', NULL, 'admin', '2026-08-01 14:50:10', '紧急'),
('13800138006', '孙建华', '2026-08-01 15:18:33', '0', NULL, NULL, NULL, NULL, '0', NULL, 'admin', '2026-08-01 15:18:33', '多次未接'),
('13900139007', '周晓东', '2026-07-31 16:20:00', '1', '2026-08-01 09:00:00', 'admin', '债务纠纷，已协商', 28, '1', '3', 'admin', '2026-07-31 16:20:00', NULL),
('13700137008', '吴桂芳', '2026-07-31 17:05:42', '0', NULL, NULL, '医疗事故咨询，请律师回电', 35, '1', '2', 'admin', '2026-07-31 17:05:42', NULL);

-- ============================================
-- 3. 系统菜单配置（话务系统3500下，3647+）
-- ============================================
INSERT INTO sys_menu VALUES('3647', '未接来电', '3500', '5', 'missedCall', 'lawyers/callCenter/missedCall', '', '', 1, 0, 'C', '0', '0', 'lawyers:call:missed:list', 'phone-outline', 'admin', sysdate(), '', null, '未接来电菜单');
INSERT INTO sys_menu VALUES('3648', '未接查询', '3647', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3649', '未接新增', '3647', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3650', '未接修改', '3647', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3651', '未接删除', '3647', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3652', '未接导出', '3647', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3653', '回拨标记', '3647', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:call:missed:callback', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3647);
INSERT INTO sys_role_menu VALUES (1, 3648);
INSERT INTO sys_role_menu VALUES (1, 3649);
INSERT INTO sys_role_menu VALUES (1, 3650);
INSERT INTO sys_role_menu VALUES (1, 3651);
INSERT INTO sys_role_menu VALUES (1, 3652);
INSERT INTO sys_role_menu VALUES (1, 3653);
