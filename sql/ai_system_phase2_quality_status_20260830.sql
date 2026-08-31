-- =====================================================================
-- 二期 T4 智能质检 / 坐席状态流水 / SLA - DDL 与菜单
-- 日期：2026-08-30
-- 说明：向前兼容，只新增表/可空列/菜单，不删除既有表列。
--   1) ai_quality_inspection  智能质检记录（ASR转写 + AI评分 + 人工复核 + 风险联动）
--   2) ai_agent_status_log    坐席在线/通话状态变更流水（duration 回填，供效能/SLA）
--   3) ai_call_queue 增 SLA 字段（应答时间/振铃时长/放弃时间，可空）
--   4) 质检菜单与按钮权限（lawyers:quality:*），编号沿用 3870+ 安全区间
-- =====================================================================

-- ---------- 1. 智能质检记录表 ----------
CREATE TABLE IF NOT EXISTS `ai_quality_inspection` (
  `inspection_id`   bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '质检ID',
  `source_type`     char(1)      NOT NULL DEFAULT '1' COMMENT '来源 1通话录音 2图文 3视频',
  `record_id`       bigint(20)   DEFAULT NULL COMMENT '通话记录ID',
  `session_id`      varchar(64)  DEFAULT NULL COMMENT '会话ID',
  `agent_id`        bigint(20)   DEFAULT NULL COMMENT '被检坐席ID',
  `caller_number`   varchar(32)  DEFAULT NULL COMMENT '主叫号码（冗余便于检索）',
  `transcript`      text         COMMENT '转写文本（ASR/对话拼接）',
  `total_score`     decimal(5,2) DEFAULT NULL COMMENT 'AI质检总分（0-100）',
  `dimension_json`  text         COMMENT '各维度评分JSON（服务规范/答复准确/情绪态度/违禁话术）',
  `violation_json`  text         COMMENT '命中违禁/敏感词/情绪问题明细JSON',
  `ai_status`       char(1)      DEFAULT '0' COMMENT 'AI质检 0待检 1检中 2完成 3失败',
  `ai_remark`       varchar(1000) DEFAULT NULL COMMENT 'AI质检总体评语/失败原因',
  `review_status`   char(1)      DEFAULT '0' COMMENT '人工复核 0未复核 1通过 2驳回整改',
  `reviewer_id`     bigint(20)   DEFAULT NULL COMMENT '复核人用户ID',
  `reviewer_name`   varchar(64)  DEFAULT NULL COMMENT '复核人姓名',
  `review_remark`   varchar(500) DEFAULT NULL COMMENT '复核评语',
  `review_time`     datetime     DEFAULT NULL COMMENT '复核时间',
  `risk_warning_id` bigint(20)   DEFAULT NULL COMMENT '联动生成的风险预警ID',
  `create_by`       varchar(64)  DEFAULT '' COMMENT '创建者',
  `create_time`     datetime     DEFAULT NULL COMMENT '创建时间',
  `update_by`       varchar(64)  DEFAULT '' COMMENT '更新者',
  `update_time`     datetime     DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`inspection_id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_agent` (`agent_id`),
  KEY `idx_review` (`review_status`),
  KEY `idx_ai_status` (`ai_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能质检记录表（T4-1）';

-- ---------- 2. 坐席状态变更流水表 ----------
CREATE TABLE IF NOT EXISTS `ai_agent_status_log` (
  `log_id`           bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `agent_id`         bigint(20)  NOT NULL COMMENT '坐席ID',
  `user_id`          bigint(20)  DEFAULT NULL COMMENT '绑定用户ID',
  `event_type`       varchar(32) DEFAULT NULL COMMENT '事件类型 LOGIN/LOGOUT/STATUS/MAKE_CALL/HANGUP/AFTER_WORK/HOLD/RESUME/TRANSFER 等',
  `from_status`      char(1)     DEFAULT NULL COMMENT '原在线状态 0离线1在线2忙碌3休息',
  `to_status`        char(1)     DEFAULT NULL COMMENT '新在线状态',
  `from_call_status` char(1)     DEFAULT NULL COMMENT '原通话状态 0空闲1通话2保持3咨询4三方5话后',
  `to_call_status`   char(1)     DEFAULT NULL COMMENT '新通话状态',
  `duration`         int(11)     DEFAULT 0 COMMENT '上一状态持续秒数（下一切换时回填）',
  `record_id`        bigint(20)  DEFAULT NULL COMMENT '关联通话记录ID（通话类事件）',
  `log_time`         datetime    DEFAULT NULL COMMENT '状态切换时间',
  `create_time`      datetime    DEFAULT NULL COMMENT '落库时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_agent_time` (`agent_id`, `log_time`),
  KEY `idx_log_time` (`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席状态变更流水表（T4-3）';

-- ---------- 3. 排队表补 SLA 字段（可空，向前兼容） ----------
-- 应答时间（坐席真正接听时刻，用于 X 秒内接听占比 SLA）
ALTER TABLE `ai_call_queue` ADD COLUMN `answer_time`   datetime DEFAULT NULL COMMENT '应答时间（SLA，T4-3）' AFTER `dequeue_time`;
-- 振铃时长（秒，分配后到接听的振铃耗时）
ALTER TABLE `ai_call_queue` ADD COLUMN `ring_duration` int(11)  DEFAULT NULL COMMENT '振铃时长秒（SLA，T4-3）' AFTER `answer_time`;
-- 放弃时间（来电者放弃排队时刻，配合 queue_status=3 算放弃前等待）
ALTER TABLE `ai_call_queue` ADD COLUMN `abandon_time`  datetime DEFAULT NULL COMMENT '放弃时间（SLA，T4-3）' AFTER `ring_duration`;

-- ---------- 4. 质检菜单与按钮权限（3870+ 安全区间，幂等） ----------
-- 4.1 质检管理目录（挂在话务管理下；parent_id 可按现场菜单树调整，此处作为一级菜单示例 0）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3870, '智能质检', 0, 60, 'quality', 'lawyers/quality/inspection', 1, 0, 'C', '0', '0', 'lawyers:quality:list', 'documentation', 'admin', sysdate(), '智能质检管理（T4-1）'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3870);

-- 4.2 质检明细查询
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3871, '质检详情', 3870, 1, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:quality:query', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3871);

-- 4.3 人工复核（通过/驳回）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3872, '质检复核', 3870, 2, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:quality:review', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3872);

-- 4.4 手动触发质检/重检
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3873, '触发质检', 3870, 3, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:quality:inspect', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3873);

-- 4.5 导出质检结果
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3874, '质检导出', 3870, 4, '', NULL, 1, 0, 'F', '0', '0', 'lawyers:quality:export', '#', 'admin', sysdate(), ''
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3874);

-- ---------- 5. 二期健壮性补漏（T1/T2 依赖但基础建表脚本未含的列/约束） ----------
-- 注意：与项目既有迁移脚本一致使用普通 ALTER，仅需在目标库执行一次；
--       若列/索引已存在会报错，忽略即可。

-- 5.1 外呼号码表补「下次重试时间」列（T2-2 指数退避：未到该时间的失败号码不被扫描捞出）
--     代码已使用 ai_outbound_callee.next_retry_time / updateNextRetryTime，基础建表脚本缺该列
ALTER TABLE `ai_outbound_callee`
  ADD COLUMN `next_retry_time` datetime DEFAULT NULL COMMENT '下次可重试时间（指数退避窗口，T2-2）' AFTER `last_retry_time`;

-- 5.2 智能体对话消息表补 (session_id, turn_no) 唯一约束（T1-7 C7）
--     turn_no 由 Redis INCR 原子分配；该唯一约束兜底计数器与 DB 漂移，
--     代码 insertWithRetry 捕获唯一键冲突后重新取号重试，防止并发丢消息/轮次重复。
ALTER TABLE `ai_agent_message`
  ADD UNIQUE KEY `uk_session_turn` (`session_id`, `turn_no`);
