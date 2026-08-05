-- P2-2 回访任务管理：ai_return_visit_task 表 + 菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. 回访任务表 ai_return_visit_task
-- ============================================
DROP TABLE IF EXISTS ai_return_visit_task;
CREATE TABLE ai_return_visit_task (
  task_id        BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  task_no        VARCHAR(50)  DEFAULT NULL            COMMENT '任务编号',
  caller_number  VARCHAR(30)  DEFAULT NULL            COMMENT '来电号码',
  caller_name    VARCHAR(50)  DEFAULT NULL            COMMENT '来电人姓名',
  plan_time      DATETIME     DEFAULT NULL            COMMENT '计划回访时间',
  actual_time    DATETIME     DEFAULT NULL            COMMENT '实际回访时间',
  assignee       VARCHAR(64)  DEFAULT NULL            COMMENT '受理人',
  status         CHAR(1)      DEFAULT '0'             COMMENT '状态（0待回访 1已完成 2已逾期）',
  priority       CHAR(1)      DEFAULT '2'             COMMENT '优先级（1高 2中 3低）',
  visit_result   VARCHAR(1000) DEFAULT NULL           COMMENT '回访结果',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (task_id),
  KEY idx_rvt_no (task_no),
  KEY idx_rvt_number (caller_number),
  KEY idx_rvt_plan (plan_time),
  KEY idx_rvt_status (status),
  KEY idx_rvt_assignee (assignee)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回访任务表';

-- ============================================
-- 2. 模拟数据
-- ============================================
INSERT INTO ai_return_visit_task (task_no, caller_number, caller_name, plan_time, actual_time, assignee, status, priority, visit_result, create_by, create_time, remark) VALUES
('RVT20260801001', '13800138001', '张丽华', '2026-08-01 10:00:00', '2026-08-01 09:55:00', 'admin', '1', '1', '客户满意，问题已解决', 'admin', '2026-07-31 16:00:00', '紧急回访'),
('RVT20260801002', '13900139002', '王志强', '2026-08-01 11:00:00', '2026-08-01 11:10:00', 'admin', '1', '2', '已联系，需进一步跟进', 'admin', '2026-07-31 16:30:00', NULL),
('RVT20260801003', '13700137003', '李秀英', '2026-08-02 09:30:00', NULL, 'admin', '0', '2', NULL, 'admin', '2026-08-01 08:00:00', '常规回访'),
('RVT20260801004', '13600136004', '陈国庆', '2026-08-02 14:00:00', NULL, 'admin', '0', '1', NULL, 'admin', '2026-08-01 09:00:00', '高优先级'),
('RVT20260801005', '13500135005', '赵美玲', '2026-07-31 16:00:00', NULL, 'admin', '2', '1', NULL, 'admin', '2026-07-30 10:00:00', '客户未接听，已逾期'),
('RVT20260801006', '13800138006', '孙建华', '2026-08-03 10:30:00', NULL, 'admin', '0', '3', NULL, 'admin', '2026-08-01 10:00:00', '低优先级'),
('RVT20260801007', '13900139007', '周晓东', '2026-07-30 15:00:00', '2026-07-30 15:20:00', 'admin', '1', '2', '客户表示感谢', 'admin', '2026-07-29 11:00:00', NULL),
('RVT20260801008', '13700137008', '吴桂芳', '2026-07-29 09:00:00', NULL, 'admin', '2', '1', NULL, 'admin', '2026-07-28 14:00:00', '多次未联系上'),
('RVT20260801009', '13600136009', '郑伟明', '2026-08-01 13:30:00', '2026-08-01 13:40:00', 'admin', '1', '2', '问题已解答', 'admin', '2026-07-31 17:00:00', NULL),
('RVT20260801010', '13500135010', '黄丽萍', '2026-08-02 16:00:00', NULL, 'admin', '0', '2', NULL, 'admin', '2026-08-01 11:30:00', '常规回访');

-- ============================================
-- 3. 系统菜单配置（话务系统3500下，3660+）
-- ============================================
INSERT INTO sys_menu VALUES('3660', '回访任务管理', '3500', '7', 'callback/task', 'lawyers/callback/task', '', '', 1, 0, 'C', '0', '0', 'lawyers:returnVisitTask:list', 'list', 'admin', sysdate(), '', null, '回访任务管理菜单');
INSERT INTO sys_menu VALUES('3661', '任务查询', '3660', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:returnVisitTask:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3662', '任务新增', '3660', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:returnVisitTask:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3663', '任务修改', '3660', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:returnVisitTask:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3664', '任务删除', '3660', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:returnVisitTask:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3665', '任务导出', '3660', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:returnVisitTask:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3660);
INSERT INTO sys_role_menu VALUES (1, 3661);
INSERT INTO sys_role_menu VALUES (1, 3662);
INSERT INTO sys_role_menu VALUES (1, 3663);
INSERT INTO sys_role_menu VALUES (1, 3664);
INSERT INTO sys_role_menu VALUES (1, 3665);
