-- ----------------------------
-- 智能外呼任务系统数据库表结构
-- 模块：ai-system，来源：SmartCall项目功能融合
-- ----------------------------

-- ----------------------------
-- 1、外呼任务表
-- ----------------------------
drop table if exists ai_outbound_task;
create table ai_outbound_task (
  task_id           bigint(20)      not null auto_increment    comment '任务ID',
  task_name         varchar(100)    not null                   comment '任务名称',
  task_no           varchar(30)     not null                   comment '任务编号',
  task_type         char(1)         default '1'                comment '任务类型（1批量外呼 2回访 3通知）',
  caller_number     varchar(20)     default null               comment '主叫号码',
  ivr_flow_id       bigint(20)      default null               comment '关联IVR流程ID',
  ivr_flow_name     varchar(100)    default null               comment 'IVR流程名称',
  start_time        datetime                                   comment '开始时间',
  end_time          datetime                                   comment '结束时间',
  total_count       int(11)         default 0                  comment '总号码数',
  completed_count   int(11)         default 0                  comment '已完成数',
  answered_count    int(11)         default 0                  comment '已接通数',
  failed_count      int(11)         default 0                  comment '失败数',
  no_answer_count   int(11)         default 0                  comment '未接数',
  status            char(1)         default '0'                comment '状态（0待执行 1执行中 2已完成 3已暂停 4已终止）',
  priority          int(11)         default 5                  comment '优先级（1-10，数字越大优先级越高）',
  retry_count       int(11)         default 0                  comment '重拨次数',
  retry_interval    int(11)         default 30                 comment '重拨间隔（分钟）',
  max_concurrent    int(11)         default 10                 comment '最大并发数',
  caller_name         varchar(100)    default null               comment '发起人姓名',
  caller_dept_id     bigint(20)      default null               comment '发起部门ID',
  description       varchar(500)    default null               comment '任务说明',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (task_id),
  unique key uk_task_no (task_no),
  key idx_status (status)
) engine=innodb auto_increment=100 comment = '外呼任务表';

-- ----------------------------
-- 2、外呼号码表
-- ----------------------------
drop table if exists ai_outbound_callee;
create table ai_outbound_callee (
  callee_id         bigint(20)      not null auto_increment    comment 'ID',
  task_id           bigint(20)      not null                   comment '任务ID',
  callee_number     varchar(20)     not null                   comment '被叫号码',
  callee_name       varchar(50)     default null               comment '被叫姓名',
  callee_gender     char(1)         default null               comment '性别（0男 1女 2未知）',
  callee_age        int(11)         default null               comment '年龄',
  callee_address    varchar(200)    default null               comment '地址',
  callee_params     longtext                                   comment '附加参数（JSON）',
  call_status       char(1)         default '0'                comment '呼叫状态（0待呼叫 1呼叫中 2已接通 3未接 4失败 5已完成 6已取消）',
  call_time         datetime                                   comment '呼叫时间',
  call_duration     int(11)         default 0                  comment '通话时长（秒）',
  record_id         bigint(20)      default null               comment '关联通话记录ID',
  agent_id          bigint(20)      default null               comment '处理坐席ID',
  retry_times       int(11)         default 0                  comment '已重拨次数',
  last_retry_time   datetime                                   comment '最后重拨时间',
  fail_reason       varchar(500)    default null               comment '失败原因',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (callee_id),
  key idx_task_id (task_id),
  key idx_call_status (call_status),
  key idx_callee_number (callee_number)
) engine=innodb auto_increment=1000 comment = '外呼号码表';

-- ----------------------------
-- 3、外呼结果表
-- ----------------------------
drop table if exists ai_outbound_result;
create table ai_outbound_result (
  result_id         bigint(20)      not null auto_increment    comment '结果ID',
  task_id           bigint(20)      not null                   comment '任务ID',
  callee_id         bigint(20)      not null                   comment '被叫ID',
  callee_number     varchar(20)     not null                   comment '被叫号码',
  callee_name       varchar(50)     default null               comment '被叫姓名',
  record_id         bigint(20)      default null               comment '通话记录ID',
  call_result       char(1)         default null               comment '呼叫结果（1已接通 2未接 3忙 4关机 5空号 6停机 7其他）',
  call_duration     int(11)         default 0                  comment '通话时长（秒）',
  start_time        datetime                                   comment '开始时间',
  end_time          datetime                                   comment '结束时间',
  agent_id          bigint(20)      default null               comment '坐席ID',
  agent_name        varchar(50)     default null               comment '坐席姓名',
  intention_code      varchar(50)     default null               comment '识别意图编码',
  intention_name    varchar(100)    default null               comment '识别意图名称',
  keywords          varchar(500)    default null               comment '关键信息（JSON数组）',
  satisfaction      char(1)         default null               comment '满意度（1非常满意 2满意 3一般 4不满意 5非常不满意）',
  need_callback     char(1)         default '0'                comment '是否需要回访（0否 1是）',
  callback_time     datetime                                   comment '建议回访时间',
  transcript        longtext                                   comment '通话转写文本',
  summary         text                                       comment '通话摘要',
  recording_url     varchar(500)    default null               comment '录音地址',
  flow_data         longtext                                   comment '流程执行数据（JSON）',
  create_time       datetime                                   comment '创建时间',
  primary key (result_id),
  key idx_task_id (task_id),
  key idx_callee_id (callee_id)
) engine=innodb auto_increment=1000 comment = '外呼结果表';

-- ----------------------------
-- 初始化-外呼任务示例数据
-- ----------------------------
insert into ai_outbound_task values(
  100, '民法典宣传回访任务', 'OB20260801001', '2',
  '12348', 100, '12348法律咨询默认流程',
  sysdate(), date_add(sysdate(), INTERVAL 7 DAY),
  100, 0, 0, 0, 0,
  '0', 5, 1, 30, 10,
  '管理员', 103, '民法典实施一周年宣传回访任务',
  'admin', sysdate(), '', null, ''
);
