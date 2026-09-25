-- =====================================================================
-- P3-E5：大模型调用成本与质量看板 —— 建表/加列/菜单（幂等，可重复执行）
-- 日期: 2026-09-25
-- 说明: ① ai_model_call_log 大模型调用明细日志（token/耗时/结果/费用快照）
--       ② ai_model_config 增加输入/输出单价列（元/千Token，仅用于成本估算，可空）
--       ③ 菜单 3985（模型成本看板 C）+ 3984（导出 F 按钮），挂 3666 工作台组
--          注：3983 已被队列监控「死信重投」占用，E5 查看菜单改用首个空位 3985
-- 执行前提: 在目标库（ai-law）连接下执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 大模型调用明细日志表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model_call_log` (
  `log_id`           BIGINT(20)    NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `call_time`        DATETIME      NOT NULL                COMMENT '调用发起时间',
  `kind`             VARCHAR(16)   NOT NULL DEFAULT 'chat' COMMENT '调用类型：chat 对话/embed 向量/rerank 精排',
  `scene`            VARCHAR(32)   NOT NULL DEFAULT 'other' COMMENT '业务场景：intention/emotion/extract/quality/summary/agent/consultation/rag/rag_index/test/other',
  `config_id`        BIGINT(20)    DEFAULT NULL            COMMENT '模型配置ID（rerank 等无配置时为空）',
  `config_name`      VARCHAR(100)  DEFAULT NULL            COMMENT '配置名称快照',
  `model_type`       VARCHAR(32)   DEFAULT NULL            COMMENT '模型类型快照',
  `model_name`       VARCHAR(100)  DEFAULT NULL            COMMENT '模型名称快照',
  `prompt_tokens`    INT(11)       NOT NULL DEFAULT 0      COMMENT '输入Token数（embedding 计入此项）',
  `completion_tokens` INT(11)      NOT NULL DEFAULT 0      COMMENT '输出Token数',
  `total_tokens`     INT(11)       NOT NULL DEFAULT 0      COMMENT '总Token数',
  `input_price`      DECIMAL(12,6) DEFAULT NULL            COMMENT '输入单价快照（元/千Token）',
  `output_price`     DECIMAL(12,6) DEFAULT NULL            COMMENT '输出单价快照（元/千Token）',
  `cost_amount`      DECIMAL(14,6) NOT NULL DEFAULT 0.000000 COMMENT '估算费用（元，按单价快照折算）',
  `elapsed_ms`       INT(11)       NOT NULL DEFAULT 0      COMMENT '端到端耗时（毫秒，含重试等待）',
  `attempts`         INT(11)       NOT NULL DEFAULT 1      COMMENT '实际尝试次数（含首次）',
  `result`           CHAR(1)       NOT NULL DEFAULT '1'    COMMENT '结果：1成功 0失败 2舱壁拒绝',
  `fail_reason`      VARCHAR(500)  DEFAULT NULL            COMMENT '失败原因（截断500字）',
  `trace_id`         VARCHAR(64)   DEFAULT NULL            COMMENT '链路traceId（与MDC/日志串联）',
  `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_mcl_call_time` (`call_time`),
  KEY `idx_mcl_model` (`model_name`, `call_time`),
  KEY `idx_mcl_kind_scene` (`kind`, `scene`, `call_time`),
  KEY `idx_mcl_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='大模型调用明细日志（P3-E5 成本与质量看板数据源）';

-- ---------------------------------------------------------------------
-- 2. ai_model_config 增加成本单价列（元/千Token，可空=不计费估算）
-- ---------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_model_config' AND column_name = 'input_price');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_model_config ADD COLUMN input_price DECIMAL(12,6) DEFAULT NULL COMMENT ''输入单价（元/千Token，成本估算用）'' AFTER max_tokens',
    'SELECT ''ai_model_config.input_price already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_model_config' AND column_name = 'output_price');
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE ai_model_config ADD COLUMN output_price DECIMAL(12,6) DEFAULT NULL COMMENT ''输出单价（元/千Token，成本估算用）'' AFTER input_price',
    'SELECT ''ai_model_config.output_price already exists, skipped'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 菜单与授权：3985 模型成本看板（C）+ 3984 明细导出（F），挂 3666 工作台
--    （3983 已被队列监控「死信重投」占用，不可复用）
-- ---------------------------------------------------------------------
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3985, '模型成本', 3666, 10, 'modelCost', 'lawyers/modelCost/index', 1, 0, 'C', '0', '0', 'lawyers:modelCost:view', 'money', 'admin', NOW(), 'P3-E5 大模型调用成本与质量看板（调用量/Token/估算费用/成功率/耗时/单位通话成本）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3985);

INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3984, '调用明细导出', 3985, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:modelCost:export', '#', 'admin', NOW(), '大模型调用明细 Excel 导出'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3984);

-- 修复早期版本误执行：3984 曾挂到 3983（死信重投）下，统一校正父级为 3985
UPDATE sys_menu SET parent_id = 3985
WHERE menu_id = 3984 AND perms = 'lawyers:modelCost:export' AND parent_id <> 3985;

-- 授权超级管理员角色（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, t.menu_id FROM (SELECT 3985 AS menu_id UNION ALL SELECT 3984) t
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = t.menu_id);

-- 回滚（如需）：
-- DELETE FROM sys_role_menu WHERE menu_id IN (3985, 3984);
-- DELETE FROM sys_menu WHERE menu_id IN (3985, 3984);
-- ALTER TABLE ai_model_config DROP COLUMN output_price;
-- ALTER TABLE ai_model_config DROP COLUMN input_price;
-- DROP TABLE IF EXISTS ai_model_call_log;
