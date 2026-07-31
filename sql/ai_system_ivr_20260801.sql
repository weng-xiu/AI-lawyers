-- ----------------------------
-- IVR智能流程编排系统数据库表结构
-- 模块：ai-system，来源：SmartCall项目功能融合
-- ----------------------------

-- ----------------------------
-- 1、IVR流程表
-- ----------------------------
drop table if exists ai_ivr_flow;
create table ai_ivr_flow (
  flow_id           bigint(20)      not null auto_increment    comment '流程ID',
  flow_name         varchar(100)    not null                   comment '流程名称',
  flow_code         varchar(50)     not null                   comment '流程编码',
  description       varchar(500)    default null               comment '流程描述',
  flow_data         longtext                                   comment '流程定义JSON（LogicFlow格式）',
  status            char(1)         default '0'                comment '状态（0草稿 1已发布 2停用）',
  version           int(11)         default 1                  comment '版本号',
  category          varchar(50)     default null               comment '流程分类',
  is_default        char(1)         default '0'                comment '是否默认流程（0否 1是）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (flow_id),
  unique key uk_flow_code (flow_code)
) engine=innodb auto_increment=100 comment = 'IVR流程表';

-- ----------------------------
-- 2、IVR节点表
-- ----------------------------
drop table if exists ai_ivr_node;
create table ai_ivr_node (
  node_id           bigint(20)      not null auto_increment    comment '节点ID',
  flow_id           bigint(20)      not null                   comment '所属流程ID',
  node_type         varchar(30)     not null                   comment '节点类型（say/answer/intention/agent/condition/transfer/hangup等）',
  node_name         varchar(100)    not null                   comment '节点名称',
  node_config       longtext                                   comment '节点配置JSON',
  position_x        int(11)         default 0                  comment '画布X坐标',
  position_y        int(11)         default 0                  comment '画布Y坐标',
  sort_order        int(11)         default 0                  comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (node_id),
  key idx_flow_id (flow_id)
) engine=innodb auto_increment=1000 comment = 'IVR节点表';

-- ----------------------------
-- 3、IVR连线表
-- ----------------------------
drop table if exists ai_ivr_edge;
create table ai_ivr_edge (
  edge_id           bigint(20)      not null auto_increment    comment '连线ID',
  flow_id           bigint(20)      not null                   comment '所属流程ID',
  source_node_id    bigint(20)      not null                   comment '源节点ID',
  target_node_id    bigint(20)      not null                   comment '目标节点ID',
  edge_label        varchar(100)    default null               comment '连线标签',
  condition_expr    varchar(500)    default null               comment '条件表达式（SpEL）',
  sort_order        int(11)         default 0                  comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (edge_id),
  key idx_flow_id (flow_id)
) engine=innodb auto_increment=1000 comment = 'IVR连线表';

-- ----------------------------
-- 4、意图定义表
-- ----------------------------
drop table if exists ai_ivr_intention;
create table ai_ivr_intention (
  intention_id      bigint(20)      not null auto_increment    comment '意图ID',
  intention_name    varchar(100)    not null                   comment '意图名称',
  intention_code    varchar(50)     not null                   comment '意图编码',
  description       varchar(500)    default null               comment '描述',
  category          varchar(50)     default null               comment '意图分类',
  regex_pattern     varchar(500)    default null               comment '正则匹配模式',
  prompt_template   longtext                                   comment 'AI识别Prompt模板',
  example_utterances longtext                                   comment '示例话术（JSON数组）',
  model_id          bigint(20)      default null               comment '关联AI模型ID',
  priority          int(11)         default 0                  comment '优先级',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (intention_id),
  unique key uk_intention_code (intention_code)
) engine=innodb auto_increment=100 comment = '意图定义表';

-- ----------------------------
-- 5、意图识别日志表
-- ----------------------------
drop table if exists ai_ivr_intention_log;
create table ai_ivr_intention_log (
  log_id            bigint(20)      not null auto_increment    comment '日志ID',
  record_id         bigint(20)      default null               comment '通话记录ID',
  session_id        varchar(64)     default null               comment '会话ID',
  flow_id           bigint(20)      default null               comment '流程ID',
  node_id           bigint(20)      default null               comment '节点ID',
  input_text        varchar(1000)   default null               comment '输入文本',
  matched_intention varchar(50)     default null               comment '匹配的意图编码',
  matched_intention_name varchar(100) default null             comment '匹配的意图名称',
  confidence        decimal(5,4)    default null               comment '置信度',
  match_method      char(1)         default '1'                comment '匹配方式（1正则 2AI模型）',
  all_results       longtext                                   comment '所有匹配结果JSON',
  create_time       datetime                                   comment '创建时间',
  primary key (log_id),
  key idx_record_id (record_id),
  key idx_session_id (session_id)
) engine=innodb auto_increment=1000 comment = '意图识别日志表';

-- ----------------------------
-- 6、IVR流程执行日志表
-- ----------------------------
drop table if exists ai_ivr_execution_log;
create table ai_ivr_execution_log (
  exec_id           bigint(20)      not null auto_increment    comment '执行ID',
  record_id         bigint(20)      default null               comment '通话记录ID',
  session_id        varchar(64)     not null                   comment '会话ID',
  flow_id           bigint(20)      not null                   comment '流程ID',
  flow_name         varchar(100)    default null               comment '流程名称',
  current_node_id   bigint(20)      default null               comment '当前节点ID',
  current_node_type varchar(30)     default null               comment '当前节点类型',
  current_node_name varchar(100)    default null               comment '当前节点名称',
  execute_result    text                                       comment '执行结果',
  variables         longtext                                   comment '流程变量JSON',
  status            char(1)         default '0'                comment '状态（0执行中 1已完成 2异常终止）',
  error_msg         varchar(1000)   default null               comment '错误信息',
  start_time        datetime                                   comment '开始时间',
  end_time          datetime                                   comment '结束时间',
  create_time       datetime                                   comment '创建时间',
  primary key (exec_id),
  key idx_record_id (record_id),
  key idx_session_id (session_id)
) engine=innodb auto_increment=1000 comment = 'IVR流程执行日志表';

-- ----------------------------
-- 初始化-IVR流程示例数据
-- ----------------------------
insert into ai_ivr_flow values(100, '12348法律咨询默认流程', 'DEFAULT_LAWYER', '12348热线法律咨询默认IVR流程，包含语音导航、意图识别、转人工等节点', null, '1', 1, '法律咨询', '1', 'admin', sysdate(), '', null, '系统默认流程');
insert into ai_ivr_flow values(101, '民事咨询流程', 'CIVIL_CONSULT', '民事案件咨询专用IVR流程', null, '0', 1, '民事咨询', '0', 'admin', sysdate(), '', null, '');
insert into ai_ivr_flow values(102, '刑事咨询流程', 'CRIMINAL_CONSULT', '刑事案件咨询专用IVR流程', null, '0', 1, '刑事咨询', '0', 'admin', sysdate(), '', null, '');

-- ----------------------------
-- 初始化-意图定义示例数据
-- ----------------------------
insert into ai_ivr_intention values(100, '法律咨询', 'LEGAL_CONSULT', '用户需要法律相关咨询', '咨询类', '.*(咨询|法律|怎么办|怎么处理).*', '请判断用户是否需要法律咨询服务，用户问题：{input}', '["我想咨询一下法律问题","这个事情怎么办","法律上怎么处理"]', null, 1, '0', 'admin', sysdate(), '', null, '');
insert into ai_ivr_intention values(101, '合同纠纷', 'CONTRACT_DISPUTE', '用户涉及合同相关纠纷', '民事类', '.*(合同|违约|协议|签订).*', '请判断用户是否涉及合同纠纷，用户问题：{input}', '["合同违约了怎么办","签订的协议有问题","对方不履行合同"]', null, 2, '0', 'admin', sysdate(), '', null, '');
insert into ai_ivr_intention values(102, '婚姻家庭', 'MARRIAGE_FAMILY', '用户涉及婚姻家庭相关问题', '民事类', '.*(离婚|结婚|婚姻|抚养|财产).*', '请判断用户是否涉及婚姻家庭问题，用户问题：{input}', '["想离婚怎么办","孩子抚养权","夫妻财产分割"]', null, 2, '0', 'admin', sysdate(), '', null, '');
insert into ai_ivr_intention values(103, '劳动争议', 'LABOR_DISPUTE', '用户涉及劳动争议相关问题', '民事类', '.*(工资|劳动|工伤|辞退|仲裁).*', '请判断用户是否涉及劳动争议问题，用户问题：{input}', '["公司拖欠工资","被辞退了怎么办","工伤赔偿标准"]', null, 2, '0', 'admin', sysdate(), '', null, '');
insert into ai_ivr_intention values(104, '转人工坐席', 'TRANSFER_AGENT', '用户要求转接人工坐席', '操作类', '.*(人工|客服|坐席|转人工).*', '请判断用户是否需要转接人工坐席，用户问题：{input}', '["我要转人工","找客服说话","接人工坐席"]', null, 10, '0', 'admin', sysdate(), '', null, '');
