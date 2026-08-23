-- =====================================================================
-- 坐席排班管理 - 班次定义 & 坐席排班
-- 作者: AI-lawyers
-- 日期: 2026-08-23
-- =====================================================================

-- ---------------------------------------------------------------------
-- 班次定义表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_work_shift (
  shift_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '班次ID',
  shift_name VARCHAR(50) NOT NULL COMMENT '班次名称(如早班/晚班)',
  start_time TIME NOT NULL COMMENT '上班时间',
  end_time TIME NOT NULL COMMENT '下班时间',
  is_overnight TINYINT DEFAULT 0 COMMENT '是否跨天 0否 1是',
  break_start TIME COMMENT '休息开始时间',
  break_end TIME COMMENT '休息结束时间',
  status TINYINT DEFAULT 1 COMMENT '状态 0停用 1启用',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (shift_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次定义';

-- ---------------------------------------------------------------------
-- 坐席排班表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ai_agent_schedule (
  schedule_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  agent_id BIGINT NOT NULL COMMENT '坐席ID',
  shift_id BIGINT NOT NULL COMMENT '班次ID',
  schedule_date DATE NOT NULL COMMENT '排班日期',
  schedule_type TINYINT DEFAULT 1 COMMENT '排班类型 1正常 2加班 3调休 4请假',
  status TINYINT DEFAULT 1 COMMENT '状态 0已取消 1有效',
  check_in_time DATETIME COMMENT '签到时间',
  check_out_time DATETIME COMMENT '签退时间',
  create_by VARCHAR(64) DEFAULT '',
  create_time DATETIME,
  update_by VARCHAR(64) DEFAULT '',
  update_time DATETIME,
  remark VARCHAR(500),
  PRIMARY KEY (schedule_id),
  UNIQUE KEY uk_agent_date (agent_id, schedule_date),
  KEY idx_date (schedule_date),
  KEY idx_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席排班';

-- ---------------------------------------------------------------------
-- 预置班次：早班、晚班
-- ---------------------------------------------------------------------
INSERT INTO ai_work_shift
  (shift_name, start_time, end_time, is_overnight, break_start, break_end, status, create_by, create_time, remark)
VALUES
  ('早班', '08:00:00', '18:00:00', 0, '12:00:00', '14:00:00', 1, 'admin', NOW(), '上午 08:00-12:00，下午 14:00-18:00'),
  ('晚班', '13:00:00', '22:00:00', 0, '18:00:00', '19:00:00', 1, 'admin', NOW(), '下午 13:00-18:00，晚间 19:00-22:00');
