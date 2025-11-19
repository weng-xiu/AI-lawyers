-- ----------------------------
-- AI律师用户端表结构
-- ----------------------------

-- ----------------------------
-- 1、用户咨询表
-- ----------------------------
drop table if exists ai_user_consultation;
create table ai_user_consultation (
  consultation_id   bigint(20)      not null auto_increment    comment '咨询ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  category          varchar(50)     not null                   comment '问题分类',
  content           text            not null                   comment '问题内容',
  attachments       varchar(1000)   default ''                 comment '附件路径',
  ai_answer         text            default null               comment 'AI回答',
  related_laws      text            default null               comment '相关法条',
  related_cases     text            default null               comment '相关案例',
  confidence        decimal(3,2)    default null               comment '置信度',
  status            varchar(20)     default 'PROCESSING'        comment '状态（PROCESSING-处理中，COMPLETED-已完成，FAILED-失败）',
  process_time      datetime        default null               comment '处理时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (consultation_id)
) engine=innodb auto_increment=1000 comment = '用户咨询表';

-- ----------------------------
-- 2、用户评价表
-- ----------------------------
drop table if exists ai_user_evaluation;
create table ai_user_evaluation (
  evaluation_id     bigint(20)      not null auto_increment    comment '评价ID',
  consultation_id   bigint(20)      not null                   comment '咨询ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  overall_rating    int(1)          not null                   comment '总体评价（1-5分）',
  professionalism_rating int(1)     not null                   comment '专业度评价（1-5分）',
  responsiveness_rating int(1)      not null                   comment '响应速度评价（1-5分）',
  quality_rating    int(1)          not null                   comment '解答质量评价（1-5分）',
  feedback          text            default null               comment '文字反馈',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (evaluation_id)
) engine=innodb auto_increment=1000 comment = '用户评价表';

-- ----------------------------
-- 3、法律知识库表
-- ----------------------------
drop table if exists ai_legal_knowledge;
create table ai_legal_knowledge (
  knowledge_id      bigint(20)      not null auto_increment    comment '知识ID',
  category          varchar(50)     not null                   comment '分类',
  title             varchar(200)    not null                   comment '标题',
  content           text            not null                   comment '内容',
  keywords          varchar(500)    default ''                 comment '关键词',
  reference         varchar(1000)   default ''                 comment '参考资料',
  view_count        int(10)         default 0                  comment '查看次数',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (knowledge_id)
) engine=innodb auto_increment=1000 comment = '法律知识库表';

-- ----------------------------
-- 4、法律案例库表
-- ----------------------------
drop table if exists ai_legal_case;
create table ai_legal_case (
  case_id           bigint(20)      not null auto_increment    comment '案例ID',
  category          varchar(50)     not null                   comment '分类',
  case_number       varchar(100)    not null                   comment '案例编号',
  case_name         varchar(200)    not null                   comment '案例名称',
  case_type         varchar(50)     not null                   comment '案例类型',
  case_summary      text            not null                   comment '案例摘要',
  case_detail       text            not null                   comment '案例详情',
  court             varchar(100)    default ''                 comment '审理法院',
  judgment_date     date            default null               comment '判决日期',
  keywords          varchar(500)    default ''                 comment '关键词',
  view_count        int(10)         default 0                  comment '查看次数',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (case_id)
) engine=innodb auto_increment=1000 comment = '法律案例库表';

-- ----------------------------
-- 5、法条库表
-- ----------------------------
drop table if exists ai_legal_article;
create table ai_legal_article (
  article_id        bigint(20)      not null auto_increment    comment '法条ID',
  law_name          varchar(100)    not null                   comment '法律名称',
  article_number    varchar(50)     not null                   comment '法条编号',
  article_content   text            not null                   comment '法条内容',
  category          varchar(50)     not null                   comment '分类',
  keywords          varchar(500)    default ''                 comment '关键词',
  view_count        int(10)         default 0                  comment '查看次数',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (article_id)
) engine=innodb auto_increment=1000 comment = '法条库表';

-- ----------------------------
-- 初始化-用户咨询表数据
-- ----------------------------
insert into ai_user_consultation values(1001, 1, 'marriage_family', '请问离婚财产如何分割？', '', '根据《中华人民共和国民法典》第一千零八十七条规定，离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。\n\n夫妻共同财产包括：\n1. 工资、奖金、劳务报酬\n2. 生产、经营、投资的收益\n3. 知识产权的收益\n4. 继承或者受赠的财产（遗嘱或赠与合同中确定只归一方的财产除外）\n5. 其他应当归共同所有的财产\n\n夫妻一方的财产包括：\n1. 一方的婚前财产\n2. 一方因受到人身损害获得的赔偿或者补偿\n3. 遗嘱或者赠与合同中确定只归一方的财产\n4. 一方专用的生活用品\n5. 其他应当归一方的财产', '《中华人民共和国民法典》第一千零八十七条\n《最高人民法院关于适用<中华人民共和国民法典>婚姻家庭编的解释（一）》第二十五条至第三十一条', '案例一：张某诉李某离婚纠纷案（案号：(2023)京01民终1234号）\n案例二：王某诉赵某离婚财产分割纠纷案（案号：(2023)沪01民终5678号）', 0.92, 'COMPLETED', now(), 'admin', now(), '', null, null);
insert into ai_user_consultation values(1002, 1, 'labor_dispute', '公司无故辞退员工，如何维权？', '', '根据《中华人民共和国劳动合同法》第四十八条规定，用人单位违反本法规定解除或者终止劳动合同，劳动者要求继续履行劳动合同的，用人单位应当继续履行；劳动者不要求继续履行劳动合同或者劳动合同已经不能继续履行的，用人单位应当依照本法第八十七条规定支付赔偿金。\n\n维权步骤：\n1. 收集证据：劳动合同、工资条、考勤记录、辞退通知等\n2. 协商解决：与用人单位协商，争取达成和解协议\n3. 申请劳动仲裁：向劳动争议仲裁委员会申请仲裁\n4. 提起诉讼：对仲裁裁决不服的，可以向人民法院提起诉讼\n\n赔偿标准：\n1. 经济补偿金：按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付\n2. 赔偿金：用人单位违法解除劳动合同的，应当向劳动者支付赔偿金，标准为经济补偿金的二倍', '《中华人民共和国劳动合同法》第四十八条、第八十七条\n《中华人民共和国劳动争议调解仲裁法》', '案例一：张某诉某科技公司违法解除劳动合同案（案号：(2023)粤01民终3456号）\n案例二：李某诉某制造厂劳动争议案（案号：(2023)浙01民终7890号）', 0.89, 'COMPLETED', now(), 'admin', now(), '', null, null);

-- ----------------------------
-- 初始化-用户评价表数据
-- ----------------------------
insert into ai_user_evaluation values(1001, 1001, 1, 5, 5, 5, 5, '回答非常专业，解决了我的问题，感谢！', 'admin', now(), '', null, null);
insert into ai_user_evaluation values(1002, 1002, 1, 4, 4, 5, 4, '回答很详细，但希望能有更多具体案例参考', 'admin', now(), '', null, null);

-- ----------------------------
-- 初始化-法律知识库表数据
-- ----------------------------
insert into ai_legal_knowledge values(1001, 'marriage_family', '离婚财产分割原则', '离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。', '离婚,财产分割,夫妻共同财产', '《中华人民共和国民法典》第一千零八十七条', 15, '0', 'admin', now(), '', null, null);
insert into ai_legal_knowledge values(1002, 'labor_dispute', '违法解除劳动合同赔偿', '用人单位违反本法规定解除或者终止劳动合同，劳动者要求继续履行劳动合同的，用人单位应当继续履行；劳动者不要求继续履行劳动合同或者劳动合同已经不能继续履行的，用人单位应当依照本法第八十七条规定支付赔偿金。', '违法解除,劳动合同,赔偿金', '《中华人民共和国劳动合同法》第四十八条', 23, '0', 'admin', now(), '', null, null);

-- ----------------------------
-- 初始化-法律案例库表数据
-- ----------------------------
insert into ai_legal_case values(1001, 'marriage_family', '(2023)京01民终1234号', '张某诉李某离婚纠纷案', '离婚纠纷', '张某与李某因感情破裂诉至法院要求离婚，双方对财产分割存在争议。法院经审理认为，夫妻共同财产应按照照顾子女、女方和无过错方权益的原则进行分割。', '张某与李某于2015年登记结婚，婚后育有一子。因感情破裂，张某诉至法院要求离婚。双方对子女抚养问题达成一致，但对财产分割存在争议。法院经审理查明，夫妻共同财产包括：房产一套、车辆两辆、银行存款50万元、股票市值30万元。法院综合考虑双方贡献、子女利益等因素，判决房产归张某所有，张某支付李某折价补偿款60万元；车辆各分一辆；银行存款和股票按6:4比例分割。', '北京市第一中级人民法院', '2023-05-15', '离婚,财产分割,夫妻共同财产', 32, '0', 'admin', now(), '', null, null);
insert into ai_legal_case values(1002, 'labor_dispute', '(2023)粤01民终3456号', '张某诉某科技公司违法解除劳动合同案', '劳动争议', '张某在某科技公司工作5年，公司以业务调整为由单方面解除劳动合同，未支付经济补偿。张某诉至法院要求公司支付违法解除劳动合同赔偿金。', '张某于2018年入职某科技公司，担任技术主管，月薪15000元。2023年3月，公司以业务调整为由单方面解除与张某的劳动合同，未支付任何经济补偿。张某认为公司违法解除劳动合同，向劳动争议仲裁委员会申请仲裁，要求公司支付违法解除劳动合同赔偿金。仲裁委员会裁决公司支付张某赔偿金15万元。公司不服仲裁裁决，诉至法院。法院经审理认为，公司未能证明解除劳动合同的合法性，属于违法解除，应支付赔偿金。', '广东省广州市中级人民法院', '2023-07-20', '违法解除,劳动合同,赔偿金', 45, '0', 'admin', now(), '', null, null);

-- ----------------------------
-- 初始化-法条库表数据
-- ----------------------------
insert into ai_legal_article values(1001, '中华人民共和国民法典', '第一千零八十七条', '离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。\n\n对夫或者妻在家庭土地承包经营中享有的权益等，应当依法予以保护。', 'marriage_family', '离婚,财产分割,夫妻共同财产', 67, '0', 'admin', now(), '', null, null);
insert into ai_legal_article values(1002, '中华人民共和国劳动合同法', '第四十八条', '用人单位违反本法规定解除或者终止劳动合同，劳动者要求继续履行劳动合同的，用人单位应当继续履行；劳动者不要求继续履行劳动合同或者劳动合同已经不能继续履行的，用人单位应当依照本法第八十七条规定支付赔偿金。', 'labor_dispute', '违法解除,劳动合同,赔偿金', 89, '0', 'admin', now(), '', null, null);