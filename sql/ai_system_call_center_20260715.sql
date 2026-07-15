-- ----------------------------
-- 12348热线话务系统数据库表结构
-- ----------------------------

-- ----------------------------
-- 1、坐席状态表
-- ----------------------------
drop table if exists ai_call_agent_status;
create table ai_call_agent_status (
  agent_id          bigint(20)      not null auto_increment    comment '坐席ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  agent_name        varchar(50)     not null                   comment '坐席名称',
  status            char(1)         default '0'                comment '状态（0离线 1在线 2忙碌 3休息）',
  login_time        datetime                                   comment '登录时间',
  logout_time       datetime                                   comment '注销时间',
  last_login_ip     varchar(128)    default ''                 comment '最后登录IP',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (agent_id)
) engine=innodb auto_increment=100 comment = '坐席状态表';

-- ----------------------------
-- 2、来电记录表
-- ----------------------------
drop table if exists ai_call_record;
create table ai_call_record (
  record_id         bigint(20)      not null auto_increment    comment '记录ID',
  caller_number     varchar(20)     not null                   comment '来电号码',
  caller_name       varchar(50)     default null               comment '来电人姓名',
  caller_address    varchar(200)    default null               comment '来电人地址',
  call_time         datetime        not null                   comment '来电时间',
  end_time          datetime                                   comment '结束时间',
  duration          int(11)         default 0                  comment '通话时长(秒)',
  agent_id          bigint(20)      not null                   comment '坐席ID',
  category_id       bigint(20)      default null               comment '咨询分类ID',
  content           text                                       comment '咨询内容',
  answer            text                                       comment '解答内容',
  status            char(1)         default '0'                comment '状态（0接通中 1已完成 2已转接 3未接）',
  transfer_id       bigint(20)      default null               comment '转接记录ID',
  ticket_id         bigint(20)      default null               comment '工单ID',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (record_id)
) engine=innodb auto_increment=1000 comment = '来电记录表';

-- ----------------------------
-- 3、工单表
-- ----------------------------
drop table if exists ai_call_ticket;
create table ai_call_ticket (
  ticket_id         bigint(20)      not null auto_increment    comment '工单ID',
  ticket_no         varchar(30)     not null                   comment '工单号',
  record_id         bigint(20)      not null                   comment '来电记录ID',
  title             varchar(200)    not null                   comment '工单标题',
  content           text            not null                   comment '工单内容',
  priority          char(1)         default '2'                comment '优先级（1紧急 2普通 3低）',
  status            char(1)         default '0'                comment '状态（0待处理 1处理中 2已完成 3已归档）',
  assign_user_id    bigint(20)      default null               comment '处理人ID',
  assign_user_name  varchar(50)     default null               comment '处理人姓名',
  process_content   text                                       comment '处理内容',
  process_time      datetime                                   comment '处理时间',
  close_time        datetime                                   comment '关闭时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (ticket_id),
  unique key uk_ticket_no (ticket_no)
) engine=innodb auto_increment=10000 comment = '工单表';

-- ----------------------------
-- 4、转接记录表
-- ----------------------------
drop table if exists ai_call_transfer;
create table ai_call_transfer (
  transfer_id       bigint(20)      not null auto_increment    comment '转接ID',
  record_id         bigint(20)      not null                   comment '来电记录ID',
  from_agent_id     bigint(20)      not null                   comment '转出坐席ID',
  from_agent_name   varchar(50)     not null                   comment '转出坐席名称',
  to_agent_id       bigint(20)      not null                   comment '转入坐席ID',
  to_agent_name     varchar(50)     not null                   comment '转入坐席名称',
  transfer_time     datetime        not null                   comment '转接时间',
  reason            varchar(500)    default null               comment '转接原因',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (transfer_id)
) engine=innodb auto_increment=100 comment = '转接记录表';

-- ----------------------------
-- 初始化-坐席状态表数据
-- ----------------------------
insert into ai_call_agent_status values(100, 1, '管理员', '0', null, null, '127.0.0.1', 'admin', sysdate(), '', null, '系统管理员');
insert into ai_call_agent_status values(101, 2, '测试员', '0', null, null, '127.0.0.1', 'admin', sysdate(), '', null, '测试坐席');
