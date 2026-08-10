-- P1-4 签署记录管理：创建 ai_sign_record 表及菜单权限配置
-- 创建日期：2026-08-10
-- 用途：支持电子签章签署记录的查看与管理

-- ============================================
-- 1. 创建 ai_sign_record 表
-- ============================================
DROP TABLE IF EXISTS ai_sign_record;
CREATE TABLE ai_sign_record (
  sign_id          BIGINT(20)      NOT NULL AUTO_INCREMENT  COMMENT '签署记录ID',
  business_no      VARCHAR(64)     DEFAULT ''               COMMENT '业务流水号',
  sign_template    VARCHAR(100)    DEFAULT ''               COMMENT '签署模板',
  sign_party       VARCHAR(64)     DEFAULT ''               COMMENT '签署方姓名',
  sign_party_phone VARCHAR(20)     DEFAULT ''               COMMENT '签署方电话',
  sign_status      CHAR(1)         DEFAULT '0'              COMMENT '签署状态（0待签署 1签署中 2已完成 3已作废）',
  sign_time        DATETIME        DEFAULT NULL             COMMENT '签署完成时间',
  e_sign_id        VARCHAR(100)    DEFAULT ''               COMMENT '电子签章ID',
  certificate_no   VARCHAR(100)    DEFAULT ''               COMMENT '存证编号',
  sign_file_url    VARCHAR(255)    DEFAULT ''               COMMENT '签署文件地址',
  create_by        VARCHAR(64)     DEFAULT ''               COMMENT '创建者',
  create_time      DATETIME                                 COMMENT '创建时间',
  update_by        VARCHAR(64)     DEFAULT ''               COMMENT '更新者',
  update_time      DATETIME        DEFAULT NULL             COMMENT '更新时间',
  remark           VARCHAR(500)    DEFAULT NULL             COMMENT '备注',
  PRIMARY KEY (sign_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='签署记录表';

-- ============================================
-- 2. 签署记录管理菜单（parent_id = 3500，排序 8）
-- ============================================
INSERT INTO sys_menu VALUES('3680', '签署记录管理', '3500', '8', 'signRecord', 'lawyers/signRecord/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:signRecord:list', 'documentation', 'admin', sysdate(), '', null, '签署记录管理菜单');

-- 签署记录查询
INSERT INTO sys_menu VALUES('3681', '签署记录查询', '3680', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:signRecord:query', '#', 'admin', sysdate(), '', null, '');
-- 签署记录新增
INSERT INTO sys_menu VALUES('3682', '签署记录新增', '3680', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:signRecord:add', '#', 'admin', sysdate(), '', null, '');
-- 签署记录修改
INSERT INTO sys_menu VALUES('3683', '签署记录修改', '3680', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:signRecord:edit', '#', 'admin', sysdate(), '', null, '');
-- 签署记录删除
INSERT INTO sys_menu VALUES('3684', '签署记录删除', '3680', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:signRecord:remove', '#', 'admin', sysdate(), '', null, '');
-- 签署记录导出
INSERT INTO sys_menu VALUES('3685', '签署记录导出', '3680', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:signRecord:export', '#', 'admin', sysdate(), '', null, '');

-- ============================================
-- 3. 为超级管理员角色（role_id = 1）分配菜单权限
-- ============================================
INSERT INTO sys_role_menu VALUES (1, 3680);
INSERT INTO sys_role_menu VALUES (1, 3681);
INSERT INTO sys_role_menu VALUES (1, 3682);
INSERT INTO sys_role_menu VALUES (1, 3683);
INSERT INTO sys_role_menu VALUES (1, 3684);
INSERT INTO sys_role_menu VALUES (1, 3685);
