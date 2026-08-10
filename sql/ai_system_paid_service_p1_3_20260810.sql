-- P1-3 有偿法律服务管理：付费法律服务表 + 菜单配置
-- 创建日期：2026-08-10

-- ============================================
-- 1. 有偿法律服务表 ai_paid_legal_service
-- ============================================
DROP TABLE IF EXISTS ai_paid_legal_service;
CREATE TABLE ai_paid_legal_service (
  paid_id         BIGINT(20)   NOT NULL AUTO_INCREMENT COMMENT '付费服务ID',
  service_no      VARCHAR(64)  DEFAULT NULL            COMMENT '服务编号',
  customer_name   VARCHAR(64)  DEFAULT NULL            COMMENT '客户姓名',
  customer_phone  VARCHAR(20)  DEFAULT NULL            COMMENT '客户电话',
  service_type    VARCHAR(32)  DEFAULT NULL            COMMENT '服务类型（代书/调解/诉讼代理/法律顾问）',
  amount          DECIMAL(10,2) DEFAULT NULL           COMMENT '服务金额',
  payment_status  CHAR(1)      DEFAULT '0'             COMMENT '支付状态（0未付 1已付 2部分付）',
  service_status  CHAR(1)      DEFAULT '0'             COMMENT '服务状态（0待受理 1处理中 2已完成 3已关闭）',
  handler_name    VARCHAR(64)  DEFAULT NULL            COMMENT '处理人',
  create_by       VARCHAR(64)  DEFAULT ''              COMMENT '创建者',
  create_time     DATETIME     DEFAULT NULL            COMMENT '创建时间',
  update_by       VARCHAR(64)  DEFAULT ''              COMMENT '更新者',
  update_time     DATETIME     DEFAULT NULL            COMMENT '更新时间',
  remark          VARCHAR(500) DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (paid_id),
  KEY idx_aps_no (service_no),
  KEY idx_aps_customer (customer_name),
  KEY idx_aps_type (service_type),
  KEY idx_aps_payment (payment_status),
  KEY idx_aps_service (service_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='有偿法律服务表';

-- ============================================
-- 2. 系统菜单配置
-- ============================================
INSERT INTO sys_menu VALUES('3670', '有偿法律服务', '3500', '7', 'paidService', 'lawyers/paidService/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:paidService:list', 'money', 'admin', sysdate(), '', null, '有偿法律服务菜单');
INSERT INTO sys_menu VALUES('3671', '付费服务查询', '3670', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3672', '付费服务新增', '3670', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3673', '付费服务修改', '3670', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3674', '付费服务删除', '3670', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3675', '付费服务导出', '3670', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:paidService:export', '#', 'admin', sysdate(), '', null, '');

INSERT INTO sys_role_menu VALUES (1, 3670);
INSERT INTO sys_role_menu VALUES (1, 3671);
INSERT INTO sys_role_menu VALUES (1, 3672);
INSERT INTO sys_role_menu VALUES (1, 3673);
INSERT INTO sys_role_menu VALUES (1, 3674);
INSERT INTO sys_role_menu VALUES (1, 3675);
