-- ----------------------------
-- 通话黑白名单表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_call_blacklist (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  phone_number VARCHAR(20) NOT NULL COMMENT '电话号码',
  list_type TINYINT NOT NULL DEFAULT 1 COMMENT '名单类型 1黑名单 2白名单',
  reason VARCHAR(500) COMMENT '加入原因',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0停用 1启用',
  effective_start DATETIME COMMENT '生效开始时间',
  effective_end DATETIME COMMENT '生效结束时间',
  create_by VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME COMMENT '更新时间',
  remark VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone_type (phone_number, list_type),
  KEY idx_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话黑白名单';
