-- ----------------------------
-- 智能法律咨询模块数据库表结构
-- ----------------------------

-- ----------------------------
-- 1、法律咨询记录表
-- ----------------------------
drop table if exists ai_legal_consultation;
create table ai_legal_consultation (
  consultation_id    bigint(20)      not null auto_increment    comment '咨询ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  question          text            not null                   comment '咨询问题',
  category_id       bigint(20)      default null               comment '问题分类ID',
  answer            text                                       comment 'AI回答',
  confidence        decimal(5,2)    default null               comment '回答置信度',
  status            char(1)         default '0'                comment '状态（0处理中 1已完成 2失败）',
  attachment_path   varchar(500)    default null               comment '附件路径',
  rating            int(1)          default null               comment '用户评分(1-5)',
  feedback          varchar(500)    default null               comment '用户反馈',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (consultation_id)
) engine=innodb auto_increment=100 comment = '法律咨询记录表';

-- ----------------------------
-- 2、法律知识库表
-- ----------------------------
drop table if exists ai_legal_knowledge;
create table ai_legal_knowledge (
  knowledge_id      bigint(20)      not null auto_increment    comment '知识ID',
  title             varchar(200)    not null                   comment '知识标题',
  category_id       bigint(20)      not null                   comment '分类ID',
  content           text            not null                   comment '知识内容',
  law_article       varchar(100)    default null               comment '法律条文',
  source            varchar(200)    default null               comment '来源',
  keywords          varchar(500)    default null               comment '关键词',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (knowledge_id)
) engine=innodb auto_increment=100 comment = '法律知识库表';

-- ----------------------------
-- 3、咨询分类表
-- ----------------------------
drop table if exists ai_consultation_category;
create table ai_consultation_category (
  category_id       bigint(20)      not null auto_increment    comment '分类ID',
  category_name     varchar(50)     not null                   comment '分类名称',
  parent_id         bigint(20)      default 0                  comment '父分类ID',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (category_id)
) engine=innodb auto_increment=100 comment = '咨询分类表';

-- ----------------------------
-- 初始化-咨询分类表数据
-- ----------------------------
insert into ai_consultation_category values(100,  '法律咨询',   0,   '0',          0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(101,  '民事纠纷',   100, '0,100',      1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(102,  '刑事辩护',   100, '0,100',      2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(103,  '行政法',     100, '0,100',      3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(104,  '经济法',     100, '0,100',      4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(105,  '婚姻家庭',   101, '0,100,101',  1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(106,  '劳动纠纷',   101, '0,100,101',  2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(107,  '合同纠纷',   101, '0,100,101',  3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(108,  '侵权责任',   101, '0,100,101',  4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(109,  '盗窃罪',     102, '0,100,102',  1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(110,  '故意伤害罪', 102, '0,100,102',  2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(111,  '诈骗罪',     102, '0,100,102',  3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(112,  '行政复议',   103, '0,100,103',  1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(113,  '行政诉讼',   103, '0,100,103',  2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(114,  '公司法',     104, '0,100,104',  1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(115,  '合同法',     104, '0,100,104',  2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into ai_consultation_category values(116,  '知识产权法', 104, '0,100,104',  3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);