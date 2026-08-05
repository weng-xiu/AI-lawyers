-- P0-3 统一工作台：待办表 + 公告表 + 菜单配置
-- 创建日期：2026-08-01

-- ============================================
-- 1. 待办表 ai_todo
-- ============================================
DROP TABLE IF EXISTS ai_todo;
CREATE TABLE ai_todo (
  todo_id        BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '待办ID',
  todo_title     VARCHAR(200) NOT NULL                COMMENT '待办标题',
  todo_content   VARCHAR(1000) DEFAULT NULL           COMMENT '待办内容',
  status         CHAR(1)      DEFAULT '0'             COMMENT '状态（0待办 1已完成 2延后 3忽略）',
  priority       CHAR(1)      DEFAULT '2'             COMMENT '优先级（1紧急 2普通 3低）',
  due_date       DATE         DEFAULT NULL            COMMENT '到期日期',
  user_id        BIGINT(20)   DEFAULT NULL            COMMENT '归属用户ID',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (todo_id),
  KEY idx_ai_todo_user (user_id),
  KEY idx_ai_todo_status (status),
  KEY idx_ai_todo_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台待办表';

-- ============================================
-- 2. 公告表 ai_notice
-- ============================================
DROP TABLE IF EXISTS ai_notice;
CREATE TABLE ai_notice (
  notice_id      BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  notice_title   VARCHAR(200) NOT NULL                COMMENT '公告标题',
  notice_content TEXT         DEFAULT NULL            COMMENT '公告内容',
  notice_type    VARCHAR(50)  DEFAULT NULL            COMMENT '公告类型',
  publish_time   DATETIME     DEFAULT NULL            COMMENT '发布时间',
  status         CHAR(1)      DEFAULT '0'             COMMENT '状态（0草稿 1发布）',
  is_top         CHAR(1)      DEFAULT '0'             COMMENT '是否置顶（0否 1是）',
  create_by      VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time    DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by      VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time    DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark         VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (notice_id),
  KEY idx_ai_notice_status (status),
  KEY idx_ai_notice_publish (publish_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台公告表';

-- ============================================
-- 3. 系统菜单配置（话务系统3500下，3630+）
-- ============================================
-- 3.1 统一工作台
INSERT INTO sys_menu VALUES('3630', '统一工作台', '3500', '1', 'workbench', 'lawyers/workbench/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:workbench:index', 'dashboard', 'admin', sysdate(), '', null, '统一工作台菜单');
INSERT INTO sys_menu VALUES('3631', '工作台查询', '3630', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:index', '#', 'admin', sysdate(), '', null, '');

-- 3.2 待办事项管理
INSERT INTO sys_menu VALUES('3632', '待办事项管理', '3500', '2', 'todo', 'lawyers/workbench/todo', '', '', 1, 0, 'C', '0', '0', 'lawyers:workbench:todo:list', 'edit', 'admin', sysdate(), '', null, '待办事项管理菜单');
INSERT INTO sys_menu VALUES('3633', '待办查询', '3632', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:todo:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3634', '待办新增', '3632', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:todo:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3635', '待办修改', '3632', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:todo:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3636', '待办删除', '3632', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:todo:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3637', '待办导出', '3632', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:todo:export', '#', 'admin', sysdate(), '', null, '');

-- 3.3 工作台公告管理
INSERT INTO sys_menu VALUES('3638', '公告管理', '3500', '3', 'wbNotice', 'lawyers/workbench/notice', '', '', 1, 0, 'C', '0', '0', 'lawyers:workbench:notice:list', 'message', 'admin', sysdate(), '', null, '工作台公告管理菜单');
INSERT INTO sys_menu VALUES('3639', '公告查询', '3638', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:notice:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3640', '公告新增', '3638', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:notice:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3641', '公告修改', '3638', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:notice:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3642', '公告删除', '3638', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:notice:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3643', '公告导出', '3638', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:workbench:notice:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 4. 为超级管理员角色分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3630);
INSERT INTO sys_role_menu VALUES (1, 3631);
INSERT INTO sys_role_menu VALUES (1, 3632);
INSERT INTO sys_role_menu VALUES (1, 3633);
INSERT INTO sys_role_menu VALUES (1, 3634);
INSERT INTO sys_role_menu VALUES (1, 3635);
INSERT INTO sys_role_menu VALUES (1, 3636);
INSERT INTO sys_role_menu VALUES (1, 3637);
INSERT INTO sys_role_menu VALUES (1, 3638);
INSERT INTO sys_role_menu VALUES (1, 3639);
INSERT INTO sys_role_menu VALUES (1, 3640);
INSERT INTO sys_role_menu VALUES (1, 3641);
INSERT INTO sys_role_menu VALUES (1, 3642);
INSERT INTO sys_role_menu VALUES (1, 3643);
