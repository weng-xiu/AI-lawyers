-- ----------------------------
-- 修复缺失的用户咨询表（仅创建，不动已存在的 ai_legal_knowledge 等表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_user_consultation (
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

-- 示例数据（便于验证咨询历史展示）
INSERT IGNORE INTO ai_user_consultation
(consultation_id, user_id, category, content, attachments, ai_answer, related_laws, related_cases, confidence, status, process_time, create_by, create_time, update_by, update_time, remark)
VALUES
(1001, 1, 'marriage_family', '请问离婚财产如何分割？', '', '根据《中华人民共和国民法典》第一千零八十七条规定，离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。', '《中华人民共和国民法典》第一千零八十七条', '张某诉李某离婚纠纷案', 0.92, 'COMPLETED', now(), 'admin', now(), '', null, null),
(1002, 1, 'labor_dispute', '公司无故辞退员工，如何维权？', '', '根据《中华人民共和国劳动合同法》第四十八条规定，用人单位违反本法规定解除或者终止劳动合同，劳动者可要求继续履行合同或主张赔偿金。', '《中华人民共和国劳动合同法》第四十八条', '张某诉某科技公司违法解除劳动合同案', 0.89, 'COMPLETED', now(), 'admin', now(), '', null, null);
