-- =====================================================================
-- B6 短信通知节点（SMS）建表脚本
-- 包含：短信通道配置、短信模板、短信发送记录 3 张表 + 菜单权限 + Mock 种子数据
-- 菜单区间：3790 - 3799（避开已使用的 3760-3766 为 B1、3770-3789 为 B5）
-- 挂载目录：资源管理 parent_id = 3670
-- 日期：2026-08-16
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 短信通道配置表
-- ---------------------------------------------------------------------
drop table if exists ai_sms_config;
create table ai_sms_config (
  config_id           bigint(20)      not null auto_increment    comment '通道ID',
  config_name         varchar(80)     not null                   comment '通道名称',
  provider            varchar(20)     default 'mock'             comment '供应商（mock/aliyun/tencent）',
  access_key_id       varchar(128)    default null               comment '访问密钥ID',
  access_key_secret   varchar(128)    default null               comment '访问密钥Secret',
  sign_name           varchar(50)     default null               comment '短信签名',
  region_id           varchar(40)     default null               comment '地域（阿里云）',
  sdk_app_id          varchar(60)     default null               comment '腾讯云AppId',
  daily_limit         int(11)         default 10                 comment '单号码日发送上限（防骚扰）',
  status              char(1)         default '1'                comment '状态（0停用 1启用）',
  create_by           varchar(64)     default ''                 comment '创建者',
  create_time         datetime                                   comment '创建时间',
  update_by           varchar(64)     default ''                 comment '更新者',
  update_time         datetime                                   comment '更新时间',
  remark              varchar(500)    default null               comment '备注',
  primary key (config_id),
  key idx_provider (provider)
) engine=innodb auto_increment=1 default charset=utf8mb4 comment='短信通道配置表';

-- ---------------------------------------------------------------------
-- 2. 短信模板表
-- ---------------------------------------------------------------------
drop table if exists ai_sms_template;
create table ai_sms_template (
  template_id           bigint(20)      not null auto_increment  comment '模板ID',
  template_name         varchar(80)     not null                 comment '模板名称',
  provider_template_code varchar(60)    default null             comment '供应商模板CODE',
  content               varchar(500)    default null             comment '模板内容，含 ${变量} 占位',
  scene_type            varchar(30)     default 'generic'        comment '场景（queue/welcome/ticket/visit/generic）',
  config_id             bigint(20)      not null                 comment '所属短信通道ID',
  status                char(1)         default '1'              comment '状态（0停用 1启用）',
  create_by             varchar(64)     default ''               comment '创建者',
  create_time           datetime                                 comment '创建时间',
  update_by             varchar(64)     default ''               comment '更新者',
  update_time           datetime                                 comment '更新时间',
  remark                varchar(500)    default null             comment '备注',
  primary key (template_id),
  key idx_config_id (config_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 comment='短信模板表';

-- ---------------------------------------------------------------------
-- 3. 短信发送记录表
-- ---------------------------------------------------------------------
drop table if exists ai_sms_log;
create table ai_sms_log (
  log_id          bigint(20)      not null auto_increment    comment '记录ID',
  phone           varchar(20)     default null               comment '接收号码',
  template_id     bigint(20)      default null               comment '模板ID',
  config_id       bigint(20)      default null               comment '通道ID',
  params_json     varchar(500)    default null               comment '模板变量JSON',
  content         varchar(500)    default null               comment '实际发送内容',
  send_status     char(1)         default '0'                comment '发送状态（0待发 1成功 2失败）',
  provider_msg_id varchar(80)     default null               comment '供应商回执ID',
  fail_reason     varchar(255)    default null               comment '失败原因',
  session_id      varchar(64)     default null               comment 'IVR会话ID',
  record_id       bigint(20)      default null               comment '通话记录ID',
  create_time     datetime        default current_timestamp  comment '创建时间',
  primary key (log_id),
  key idx_phone_time (phone, create_time),
  key idx_session_id (session_id),
  key idx_record_id (record_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 comment='短信发送记录表';

-- =====================================================================
-- 4. 菜单与按钮权限（挂"资源管理"目录 parent_id=3670 下）
-- =====================================================================
-- 4.1 短信配置（3790）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3790', '短信配置', '3670', '8', 'smsConfig', 'lawyers/sms/config', '', '', 1, 0, 'C', '0', '0', 'lawyers:smsConfig:view', 'message', 'admin', sysdate(), '短信通道配置管理');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3791', '短信模板', '3670', '9', 'smsTemplate', 'lawyers/sms/template', 1, 0, 'C', '0', '0', 'lawyers:smsTemplate:view', 'form', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3792', '短信记录', '3670', '10', 'smsLog', 'lawyers/sms/log', 1, 0, 'C', '0', '0', 'lawyers:smsLog:view', 'list', 'admin', sysdate(), '');

-- 4.2 为超级管理员角色（role_id=1）分配菜单权限
insert into sys_role_menu (role_id, menu_id)
select 1, menu_id from sys_menu where menu_id between 3790 and 3799
  and not exists (select 1 from sys_role_menu rm where rm.role_id=1 and rm.menu_id=sys_menu.menu_id);

-- =====================================================================
-- 5. 种子数据：Mock 通道 + 模板
-- =====================================================================
insert into ai_sms_config (config_id, config_name, provider, sign_name, daily_limit, status, create_by, create_time, remark)
values (1, '本地模拟通道', 'mock', 'AI法律咨询', 10, '1', 'admin', now(), '无真实短信网关，仅落库留痕');

insert into ai_sms_template (template_id, template_name, provider_template_code, content, scene_type, config_id, status, create_by, create_time, remark)
values
(1, '排队通知', 'MOCK_QUEUE',  '【AI法律咨询】您好，您当前排队第${queuePos}位，请耐心等待。', 'queue', 1, '1', 'admin', now(), 'B5排队场景短信'),
(2, '工单通知', 'MOCK_TICKET', '【AI法律咨询】您的咨询工单已创建，工单号：${ticketNo}，稍后将有律师与您联系。', 'ticket', 1, '1', 'admin', now(), '工单创建后短信告知');
