-- P2-1 客户回访中心：ai_callback 表 + 菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. 客户回访表 ai_callback
-- ============================================
DROP TABLE IF EXISTS ai_callback;
CREATE TABLE ai_callback (
  callback_id      BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '回访ID',
  ledger_id        BIGINT(20)   DEFAULT NULL            COMMENT '关联台账ID',
  caller_number    VARCHAR(30)  DEFAULT NULL            COMMENT '来电号码',
  caller_name      VARCHAR(50)  DEFAULT NULL            COMMENT '来电人姓名',
  visit_time       DATETIME     DEFAULT NULL            COMMENT '回访时间',
  visit_by         VARCHAR(64)  DEFAULT NULL            COMMENT '回访人',
  satisfaction     CHAR(1)      DEFAULT NULL            COMMENT '满意度（1非常满意 2满意 3一般 4不满意）',
  visit_opinion    VARCHAR(1000) DEFAULT NULL           COMMENT '回访意见',
  visit_result     VARCHAR(500) DEFAULT NULL            COMMENT '回访结果',
  status           CHAR(1)      DEFAULT '0'             COMMENT '状态（0待回访 1已完成 2无法联系）',
  create_by        VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time      DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by        VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time      DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark           VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (callback_id),
  KEY idx_ai_callback_number (caller_number),
  KEY idx_ai_callback_time (visit_time),
  KEY idx_ai_callback_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户回访表';

-- ============================================
-- 2. 模拟数据（便于页面预览统计与趋势图）
-- ============================================
INSERT INTO ai_callback (ledger_id, caller_number, caller_name, visit_time, visit_by, satisfaction, visit_opinion, visit_result, status, create_by, create_time, remark) VALUES
(1, '13800138001', '张丽华', '2026-08-01 09:30:00', 'admin', '1', '律师解答很专业，态度很好，非常满意', '问题已解决，客户满意', '1', 'admin', '2026-08-01 09:30:00', '回访完成'),
(2, '13900139002', '王志强', '2026-08-01 10:15:00', 'admin', '2', '解答比较详细，但希望能更快回复', '问题基本解决', '1', 'admin', '2026-08-01 10:15:00', NULL),
(3, '13700137003', '李秀英', '2026-08-01 11:00:00', 'admin', '3', '一般，希望律师能更耐心一些', '需进一步跟进', '1', 'admin', '2026-08-01 11:00:00', NULL),
(4, '13600136004', '陈国庆', '2026-08-01 13:45:00', 'admin', '1', '非常感谢律师的耐心解答', '问题已解决', '1', 'admin', '2026-08-01 13:45:00', NULL),
(5, '13500135005', '赵美玲', '2026-08-01 14:20:00', 'admin', '2', '服务不错，建议增加视频咨询', '问题解决，采纳建议', '1', 'admin', '2026-08-01 14:20:00', NULL),
(6, '13800138006', '孙建华', NULL, NULL, NULL, NULL, NULL, '0', 'admin', '2026-08-01 15:00:00', '待回访'),
(7, '13900139007', '周晓东', '2026-07-31 16:00:00', 'admin', '1', '律师非常专业，推荐', '问题已解决', '1', 'admin', '2026-07-31 16:00:00', NULL),
(8, '13700137008', '吴桂芳', '2026-07-31 17:00:00', 'admin', '4', '回复太慢，体验不好', '需改进响应速度', '1', 'admin', '2026-07-31 17:00:00', '投诉'),
(9, '13600136009', '郑伟明', '2026-07-30 10:00:00', 'admin', '2', '解答清晰，谢谢', '问题已解决', '1', 'admin', '2026-07-30 10:00:00', NULL),
(10, '13500135010', '黄丽萍', '2026-07-30 14:30:00', 'admin', '3', '一般，希望更专业', '需跟进', '1', 'admin', '2026-07-30 14:30:00', NULL);

-- ============================================
-- 3. 系统菜单配置（话务系统3500下，3654+）
-- ============================================
INSERT INTO sys_menu VALUES('3654', '客户回访中心', '3500', '6', 'callback', 'lawyers/callback/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:callback:list', 'refresh-left', 'admin', sysdate(), '', null, '客户回访中心菜单');
INSERT INTO sys_menu VALUES('3655', '回访查询', '3654', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:callback:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3656', '回访新增', '3654', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:callback:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3657', '回访修改', '3654', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:callback:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3658', '回访删除', '3654', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:callback:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3659', '回访导出', '3654', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:callback:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3654);
INSERT INTO sys_role_menu VALUES (1, 3655);
INSERT INTO sys_role_menu VALUES (1, 3656);
INSERT INTO sys_role_menu VALUES (1, 3657);
INSERT INTO sys_role_menu VALUES (1, 3658);
INSERT INTO sys_role_menu VALUES (1, 3659);
