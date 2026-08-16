-- ============================================================
-- B1 智能体对话节点 —— 数据库建表与菜单权限脚本
-- 模块：ai-system + ai-admin，来源：SmartCall 项目深度融合 B1
-- 日期：2026-08-16
-- 说明：
--   1. 建 2 张表：智能体配置表、对话消息表（审计/回溯）
--   2. 在"IVR智能流程"目录(parent=3600)下新增"AI智能体配置"菜单
--   3. 为超级管理员(role_id=1)分配菜单与按钮权限
--   4. 写入 1 条 local 模式默认智能体种子数据
--   注意：执行前请确认 3600 目录菜单存在；若不存在请先执行 ai_admin_ivr_outbound_menu_20260801.sql
-- ============================================================

-- ----------------------------
-- 1、智能体配置表
-- ----------------------------
drop table if exists ai_agent_config;
create table ai_agent_config (
  agent_id          bigint(20)      not null auto_increment    comment '智能体ID',
  agent_name        varchar(100)    not null                   comment '智能体名称',
  provider          varchar(30)     default 'local'            comment '平台（local本地知识库 maxkb dify fastgpt coze）',
  api_url           varchar(500)    default null               comment '外部平台对话接口地址',
  api_key           varchar(500)    default null               comment '外部平台认证密钥（加密存储）',
  app_id            varchar(100)    default null               comment '平台应用/知识库ID',
  category_id       bigint(20)      default null               comment '默认关联咨询分类ID',
  system_prompt     text                                       comment '角色设定系统提示词',
  model_id          bigint(20)      default null               comment '关联大模型配置ID（为空使用默认模型）',
  knowledge_ids     varchar(500)    default null               comment '限定知识库ID（逗号分隔，空则全库检索）',
  enable_context    char(1)         default '1'                comment '是否启用多轮上下文（0否 1是）',
  context_rounds    int(11)         default 5                  comment '上下文保留轮数',
  handoff_keywords  varchar(500)    default null               comment '转人工关键词（逗号分隔，命中即转人工）',
  welcome           varchar(500)    default null               comment '首轮欢迎语（无用户输入时播报）',
  status            char(1)         default '1'                comment '状态（0停用 1启用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (agent_id),
  key idx_category_id (category_id),
  key idx_status (status)
) engine=innodb auto_increment=100 comment = 'AI智能体配置表';

-- ----------------------------
-- 2、智能体对话消息表（多轮上下文审计与回溯）
-- ----------------------------
drop table if exists ai_agent_message;
create table ai_agent_message (
  message_id        bigint(20)      not null auto_increment    comment '消息ID',
  session_id        varchar(64)     not null                   comment '会话ID（对应IVR sessionId）',
  agent_id          bigint(20)      not null                   comment '智能体ID',
  flow_id           bigint(20)      default null               comment '所属IVR流程ID',
  node_id           bigint(20)      default null               comment '所属IVR节点ID',
  record_id         bigint(20)      default null               comment '关联通话记录ID',
  caller_number     varchar(20)     default null               comment '主叫号码',
  role              varchar(20)     not null                   comment '角色（user用户 assistant助手）',
  content           text                                       comment '消息内容',
  handoff           char(1)         default '0'                comment '本轮是否触发转人工（0否 1是，仅assistant）',
  reason            varchar(200)    default null               comment '转人工原因',
  knowledge_refs    varchar(500)    default null               comment '命中的知识库ID（逗号分隔）',
  turn_no           int(11)         default 0                  comment '轮次序号（同一session内自增）',
  create_time       datetime                                   comment '创建时间',
  primary key (message_id),
  key idx_session_id (session_id),
  key idx_agent_id (agent_id),
  key idx_record_id (record_id),
  key idx_create_time (create_time)
) engine=innodb auto_increment=1 comment = 'AI智能体对话消息表';

-- ============================================================
-- 3、菜单与按钮权限（挂在"IVR智能流程"目录 parent=3600 下）
--    菜单ID区间：3760 - 3769（避开已使用的 3720-3755 P1 区间）
-- ============================================================

-- 3.1 智能体配置菜单
INSERT INTO sys_menu VALUES('3760', 'AI智能体配置', '3600', '3', 'agentConfig', 'lawyers/agent/config', '', '', 1, 0, 'C', '0', '0', 'lawyers:agent:config:list', 'monitor', 'admin', sysdate(), '', null, 'AI智能体配置菜单');

-- 3.2 按钮权限
INSERT INTO sys_menu VALUES('3761', '智能体查询', '3760', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3762', '智能体新增', '3760', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3763', '智能体修改', '3760', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3764', '智能体删除', '3760', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3765', '智能体导出', '3760', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3766', '智能体连接测试', '3760', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:agent:config:test', '#', 'admin', sysdate(), '', null, '');

-- ============================================================
-- 4、为超级管理员(role_id=1)分配菜单与按钮权限
-- ============================================================
INSERT INTO sys_role_menu VALUES (1, 3760);
INSERT INTO sys_role_menu VALUES (1, 3761);
INSERT INTO sys_role_menu VALUES (1, 3762);
INSERT INTO sys_role_menu VALUES (1, 3763);
INSERT INTO sys_role_menu VALUES (1, 3764);
INSERT INTO sys_role_menu VALUES (1, 3765);
INSERT INTO sys_role_menu VALUES (1, 3766);

-- ============================================================
-- 5、种子数据：一条 local 模式默认法律咨询智能体
-- ============================================================
INSERT INTO ai_agent_config (
  agent_id, agent_name, provider, category_id, system_prompt,
  enable_context, context_rounds, handoff_keywords, welcome, status,
  create_by, create_time, remark
) VALUES (
  100,
  '12348法律咨询助手',
  'local',
  NULL,
  '你是12348公共法律服务热线的智能法律助手，基于提供的法律知识回答群众咨询。要求：1.只回答法律相关问题，用语专业、准确、通俗易懂；2.优先依据"参考知识"作答，无依据时如实说明并建议咨询人工律师；3.不提供具体诉讼代理意见，不替代正式法律意见；4.涉及紧急危险、信访投诉、强烈要求人工或问题超出能力范围时，应判定需要转接人工。',
  '1',
  5,
  '人工,律师,转人工,找律师,投诉,信访,起诉,报警,12345,骂人,生气',
  '您好，这里是12348公共法律服务热线智能助手，请问您有什么法律问题需要咨询？',
  '1',
  'admin',
  sysdate(),
  'B1默认智能体，基于本地法律知识库+默认大模型，可直接用于agentChat节点'
);
