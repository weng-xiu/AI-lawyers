-- =====================================================================
-- B5 智能队列分配（ACD）建表脚本
-- 包含：技能组、技能组成员、排队流水 3 张表 + 菜单权限 + 种子数据
-- 依赖：ai_call_agent_status（坐席表，已存在）、ai_consultation_category（咨询分类，已存在）
-- 菜单区间：3770 - 3789（避开已使用的 3720-3766）
-- 挂载目录：资源管理 parent_id = 3670
-- 日期：2026-08-16
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 技能组表
-- ---------------------------------------------------------------------
drop table if exists ai_skill_group;
create table ai_skill_group (
  group_id                bigint(20)      not null auto_increment    comment '技能组ID',
  group_name              varchar(80)     not null                   comment '技能组名称（民事/刑事/劳动/婚姻等）',
  group_code              varchar(40)     not null                   comment '技能组编码',
  category_id             bigint(20)      default null               comment '关联咨询分类ID（B1 agentCategoryId 自动映射）',
  strategy                varchar(20)     default 'round_robin'      comment '分配策略（round_robin轮询 least_recent最久未接 least_calls最少通话 all_ring全员振铃）',
  max_wait                int(11)         default 60                 comment '最大排队等待秒（超时溢出）',
  wrap_up_time            int(11)         default 10                 comment '话后整理秒（话后期间不分配）',
  service_level_threshold int(11)         default 20                 comment '服务水平阈值秒（大屏统计用）',
  overflow_group_id       bigint(20)      default null               comment '溢出技能组ID（无可用坐席时）',
  status                  char(1)         default '1'                comment '状态（0停用 1启用）',
  create_by               varchar(64)     default ''                 comment '创建者',
  create_time             datetime                                   comment '创建时间',
  update_by               varchar(64)     default ''                 comment '更新者',
  update_time             datetime                                   comment '更新时间',
  remark                  varchar(500)    default null               comment '备注',
  primary key (group_id),
  unique key uk_group_code (group_code),
  key idx_category_id (category_id)
) engine=innodb auto_increment=100 default charset=utf8mb4 comment='技能组表';

-- ---------------------------------------------------------------------
-- 2. 技能组成员表
-- ---------------------------------------------------------------------
drop table if exists ai_skill_group_member;
create table ai_skill_group_member (
  id              bigint(20)      not null auto_increment    comment '主键ID',
  group_id        bigint(20)      not null                   comment '技能组ID',
  agent_id        bigint(20)      not null                   comment '坐席ID（ai_call_agent_status.agent_id）',
  skill_level     int(11)         default 3                  comment '技能等级1-5（等级高者优先分配）',
  priority        int(11)         default 0                  comment '组内优先级（同等级按此排序，越大越优先）',
  max_concurrent  int(11)         default 1                  comment '个人最大并发通话数',
  status          char(1)         default '1'                comment '状态（0禁用 1启用）',
  create_time     datetime                                   comment '创建时间',
  update_time     datetime                                   comment '更新时间',
  primary key (id),
  unique key uk_group_agent (group_id, agent_id),
  key idx_agent_id (agent_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 comment='技能组成员表';

-- ---------------------------------------------------------------------
-- 3. 排队/分配流水表
-- ---------------------------------------------------------------------
drop table if exists ai_call_queue;
create table ai_call_queue (
  queue_id        bigint(20)      not null auto_increment    comment '排队ID',
  session_id      varchar(64)     default null               comment 'IVR会话ID',
  record_id       bigint(20)      default null               comment '通话记录ID',
  caller_number   varchar(20)     default null               comment '主叫号码',
  group_id        bigint(20)      not null                   comment '目标技能组ID',
  enqueue_time    datetime        not null                   comment '入队时间',
  dequeue_time    datetime        default null               comment '出队时间',
  agent_id        bigint(20)      default null               comment '分配到的坐席ID',
  wait_duration   int(11)         default 0                  comment '等待时长（秒）',
  queue_status    char(1)         default '0'                comment '排队状态（0排队中 1已分配 2超时溢出 3已放弃 4无可用坐席）',
  strategy_used   varchar(20)     default null               comment '实际命中的分配策略',
  priority        int(11)         default 0                  comment '排队优先级（VIP/情绪激动可提升）',
  create_time     datetime        default current_timestamp  comment '创建时间',
  update_time     datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (queue_id),
  key idx_session_id (session_id),
  key idx_group_status (group_id, queue_status),
  key idx_record_id (record_id),
  key idx_enqueue_time (enqueue_time)
) engine=innodb auto_increment=1 default charset=utf8mb4 comment='排队/分配流水表';

-- =====================================================================
-- 4. 菜单与按钮权限（挂在"资源管理"目录 parent_id=3670 下）
--    使用显式列名，兼容 sys_menu 全字段（query/route_name/is_frame/is_cache/visible/status）
-- =====================================================================
-- 4.1 技能组管理（3770）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3770', '技能组管理', '3670', '6', 'skillGroup', 'lawyers/skill/group', '', '', 1, 0, 'C', '0', '0', 'lawyers:skillGroup:view', 'tree-table', 'admin', sysdate(), '技能组与坐席分配管理');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3771', '技能组查询', '3770', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:skillGroup:query',  '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3772', '技能组新增', '3770', '2', '', '', 1, 0, 'F', '0', '0', 'lawyers:skillGroup:add',    '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3773', '技能组修改', '3770', '3', '', '', 1, 0, 'F', '0', '0', 'lawyers:skillGroup:edit',   '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3774', '技能组删除', '3770', '4', '', '', 1, 0, 'F', '0', '0', 'lawyers:skillGroup:remove', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3775', '技能组导出', '3770', '5', '', '', 1, 0, 'F', '0', '0', 'lawyers:skillGroup:export', '#', 'admin', sysdate(), '');

-- 4.2 实时排队监控（3780）
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3780', '排队监控', '3670', '7', 'queue', 'lawyers/skill/queue', '', '', 1, 0, 'C', '0', '0', 'lawyers:queue:view', 'list', 'admin', sysdate(), '实时排队与分配监控');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3781', '排队查询', '3780', '1', '', '', 1, 0, 'F', '0', '0', 'lawyers:queue:query',  '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3782', '手动分配', '3780', '2', '', '', 1, 0, 'F', '0', '0', 'lawyers:queue:assign', '#', 'admin', sysdate(), '');
insert into sys_menu (menu_id, menu_name, parent_id, order_num, path, component, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
values('3783', '踢除排队', '3780', '3', '', '', 1, 0, 'F', '0', '0', 'lawyers:queue:kick',   '#', 'admin', sysdate(), '');

-- 4.3 为超级管理员角色（role_id=1）分配全部菜单权限
insert into sys_role_menu (role_id, menu_id)
select 1, menu_id from sys_menu where menu_id between 3770 and 3789
  and not exists (select 1 from sys_role_menu rm where rm.role_id=1 and rm.menu_id=sys_menu.menu_id);

-- =====================================================================
-- 5. 种子数据：常用技能组（绑定现有咨询分类）
-- =====================================================================
insert into ai_skill_group (group_id, group_name, group_code, category_id, strategy, max_wait, wrap_up_time, service_level_threshold, overflow_group_id, status, remark)
values
  (100, '婚姻家事组', 'MARRIAGE',  105, 'least_recent', 60, 10, 20, null, '1', '婚姻、继承、抚养等家事纠纷'),
  (101, '劳动争议组', 'LABOR',     106, 'least_recent', 60, 10, 20, null, '1', '劳动合同、工资、工伤等'),
  (102, '合同纠纷组', 'CONTRACT',  107, 'round_robin',  60, 10, 20, null, '1', '各类民商事合同纠纷'),
  (103, '刑事辩护组', 'CRIMINAL',  102, 'least_calls',  90, 15, 30, null, '1', '刑事咨询与辩护，高优先级'),
  (104, '综合咨询组', 'GENERAL',   100, 'round_robin',  45,  5, 15, 100,  '1', '兜底技能组，溢出到婚姻家事组');
