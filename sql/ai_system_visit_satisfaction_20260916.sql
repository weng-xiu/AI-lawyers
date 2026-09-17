-- =====================================================================
-- 第十五部分 F5 —— 智能回访满意度归因看板 菜单/权限迁移脚本
-- 日期: 2026-09-16
-- 说明: 对应《AI律师话务系统综合文档》第十五部分 F5
--       （满意度归因分析 + 逾期回访逐级升级监控）。
--       后端接口 GET /lawyers/visit/satisfaction/board，权限 lawyers:visitBoard:view。
--       挂在 F 组一级菜单"法服协同"(3900) 下，SLA看板(3945) 之后，menu_id=3960。
-- 特性: 可重复执行（NOT EXISTS 幂等）；超管 role_id=1 自动授权。
-- 执行前提: 在目标库（ai-law）连接下，且已执行 ai_system_f_group_20260913.sql。
-- 备注: 本次纯增量（新增聚合查询/定时任务/页面），无任何业务表结构变更。
-- =====================================================================

-- 3960 回访满意度看板（菜单 C）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT 3960, '回访满意度', 3900, 6, 'visitSatisfaction', 'lawyers/collab/visitSatisfaction/index', 1, 0, 'C', '0', '0', 'lawyers:visitBoard:view', 'chart', 'admin', NOW(), 'F5 智能回访：满意度归因(原因TOP/情绪极性)+图文评价+回访员排行+逾期三级升级监控'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_id = 3960);

-- 超管自动授权（role_id=1）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, menu_id FROM sys_menu WHERE menu_id = 3960
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 3960);

-- =====================================================================
-- 补建公众端图文咨询评价表 ai_user_evaluation
-- 说明: 实体 ai-admin/.../domain/lawyers/AiUserEvaluation.java 与建表脚本
--       sql/ai_user_tables_20231119.sql 早已存在，但本库漏建，导致 F5 看板聚合
--       图文评价时报表不存在。这里以 IF NOT EXISTS 幂等补建，结构与原始 DDL 完全一致。
-- =====================================================================
CREATE TABLE IF NOT EXISTS ai_user_evaluation (
  evaluation_id           bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  consultation_id         bigint(20)  NOT NULL                COMMENT '咨询ID',
  user_id                 bigint(20)  NOT NULL                COMMENT '用户ID',
  overall_rating          int(1)      NOT NULL                COMMENT '总体评价（1-5分）',
  professionalism_rating  int(1)      NOT NULL                COMMENT '专业度评价（1-5分）',
  responsiveness_rating   int(1)      NOT NULL                COMMENT '响应速度评价（1-5分）',
  quality_rating          int(1)      NOT NULL                COMMENT '解答质量评价（1-5分）',
  feedback                text        DEFAULT NULL            COMMENT '文字反馈',
  create_by               varchar(64) DEFAULT ''               COMMENT '创建者',
  create_time             datetime                             COMMENT '创建时间',
  update_by               varchar(64) DEFAULT ''               COMMENT '更新者',
  update_time             datetime                             COMMENT '更新时间',
  remark                  varchar(500) DEFAULT NULL           COMMENT '备注',
  PRIMARY KEY (evaluation_id),
  KEY idx_eval_create_time (create_time),
  KEY idx_eval_user (user_id)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='用户评价表（F5 图文咨询满意度归因）';
