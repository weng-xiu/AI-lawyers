-- ----------------------------
-- 咨询台账表
-- ----------------------------
drop table if exists ai_call_ledger;
create table ai_call_ledger (
  ledger_id         bigint(20)      not null auto_increment    comment '台账ID',
  ledger_no         varchar(30)     not null                   comment '台账编号',
  caller_name       varchar(50)     default null               comment '咨询人姓名',
  caller_phone      varchar(20)     default null               comment '联系电话',
  caller_id_card    varchar(18)     default null               comment '身份证号',
  caller_gender     char(1)         default '2'                comment '性别（0男1女2未知）',
  caller_age        int(11)         default null               comment '年龄',
  caller_job        varchar(50)     default null               comment '职业',
  caller_company    varchar(100)    default null               comment '工作单位',
  caller_address    varchar(200)    default null               comment '联系地址',
  category_id       bigint(20)      default null               comment '咨询分类ID',
  category_name     varchar(50)     default null               comment '咨询分类名称',
  sub_category      varchar(50)     default null               comment '问题分类',
  service_type      char(1)         default null               comment '服务方式（1电话2现场3网络4视频）',
  source_channel    varchar(20)     default null               comment '来源渠道',
  lawyer_id         bigint(20)      default null               comment '承办律师ID',
  lawyer_name       varchar(50)     default null               comment '承办律师姓名',
  consult_content   text                                       comment '咨询摘要',
  involve_amount    decimal(12,2)   default null               comment '涉及金额',
  lawyer_answer     text                                       comment '律师解答意见',
  consult_duration  int(11)         default 0                  comment '咨询时长(分钟)',
  satisfaction      char(1)         default null               comment '满意度（1非常满意2满意3一般4不满意）',
  is_visit          char(1)         default '0'                comment '是否已回访（0否1是）',
  visit_time        datetime                                   comment '回访时间',
  visit_by          varchar(50)     default null               comment '回访人',
  visit_opinion     varchar(500)    default null               comment '回访意见',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  primary key (ledger_id),
  unique key uk_ledger_no (ledger_no)
) engine=innodb auto_increment=1000 comment = '咨询台账表';

-- ----------------------------
-- 初始化-咨询台账表数据
-- ----------------------------
insert into ai_call_ledger values(1000, 'DJ20260314100000ABCDEF', '张三', '13800138001', '110101199001011234', '0', 36, '工程师', '北京科技有限公司', '北京市朝阳区建国路88号', 1, '民事纠纷', '合同纠纷', '1', '电话咨询', 1, '李律师', '咨询劳动合同解除相关问题', 50000.00, '建议收集证据申请劳动仲裁', 30, '2', '1', '2026-03-15 10:00:00', '王主任', '对解答满意', 'admin', '2026-03-14 10:00:00', 'admin', '2026-03-15 10:00:00', '首次咨询', '0');
insert into ai_call_ledger values(1001, 'DJ20260314110000ABCDEG', '李四', '13800138002', '110101198505055678', '1', 41, '教师', '北京市第一中学', '北京市海淀区中关村大街1号', 2, '刑事辩护', '盗窃案件', '2', '现场咨询', 2, '赵律师', '咨询亲属盗窃案件辩护事宜', 0.00, '建议尽快委托律师介入', 60, '1', '0', null, null, null, 'admin', '2026-03-14 11:00:00', '', null, '家属代为咨询', '0');
insert into ai_call_ledger values(1002, 'DJ20260314140000ABCDEH', '王五', '13800138003', '110101199212129012', '0', 34, '个体工商户', '王五服装店', '北京市西城区西单北大街100号', 3, '婚姻家庭', '离婚财产分割', '3', '网络咨询', 3, '陈律师', '咨询离婚财产分割问题', 2000000.00, '建议协商解决，协商不成起诉', 45, '3', '0', null, null, null, 'admin', '2026-03-14 14:00:00', '', null, '涉及房产分割', '0');
insert into ai_call_ledger values(1003, 'DJ20260314153000ABCDEI', '赵六', '13800138004', '110101198808083456', '1', 38, '医生', '北京协和医院', '北京市东城区王府井大街1号', 4, '交通事故', '人身损害赔偿', '4', '视频咨询', 4, '孙律师', '咨询交通事故赔偿标准', 300000.00, '建议做伤残鉴定后起诉', 50, '2', '1', '2026-03-16 09:30:00', '周助理', '满意解答，后续会考虑委托', 'admin', '2026-03-14 15:30:00', 'admin', '2026-03-16 09:30:00', '已预约面谈', '0');
insert into ai_call_ledger values(1004, 'DJ20260314164500ABCDEJ', '钱七', '13800138005', '110101199509097890', '0', 31, '程序员', '互联网科技公司', '北京市昌平区回龙观西大街', 5, '知识产权', '商标侵权', '1', '电话咨询', 5, '吴律师', '咨询商标被侵权如何维权', 150000.00, '建议收集证据发律师函，必要时起诉', 25, '4', '0', null, null, null, 'admin', '2026-03-14 16:45:00', '', null, '对解答不太满意', '0');
