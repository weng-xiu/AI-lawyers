-- ----------------------------
-- 系统配置模块数据库表结构
-- ----------------------------

-- ----------------------------
-- 1、AI模型参数配置表
-- ----------------------------
drop table if exists ai_model_config;
create table ai_model_config (
  config_id         bigint(20)      not null auto_increment    comment '配置ID',
  config_name       varchar(100)    not null                   comment '配置名称',
  model_type        varchar(50)     not null                   comment '模型类型',
  model_name        varchar(100)    not null                   comment '模型名称',
  api_key           varchar(500)    default null               comment 'API密钥',
  api_url           varchar(500)    default null               comment 'API地址',
  max_tokens        int(10)         default 2048               comment '最大令牌数',
  temperature      decimal(3,2)    default 0.70               comment '温度参数(0.0-1.0)',
  top_p             decimal(3,2)    default 0.90               comment 'Top-p参数(0.0-1.0)',
  frequency_penalty decimal(3,2)    default 0.00               comment '频率惩罚(-2.0-2.0)',
  presence_penalty  decimal(3,2)    default 0.00               comment '存在惩罚(-2.0-2.0)',
  timeout           int(10)         default 30                  comment '请求超时时间(秒)',
  retry_count       int(5)          default 3                   comment '重试次数',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  is_default        char(1)         default '0'                comment '是否默认（0否 1是）',
  remark            varchar(500)    default null               comment '备注',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = 'AI模型参数配置表';

-- ----------------------------
-- 初始化-AI模型参数配置表数据
-- ----------------------------
insert into ai_model_config values(100, '默认GPT配置', 'gpt', 'gpt-3.5-turbo', 'sk-xxxxxxxxxxxxxxxx', 'https://api.openai.com/v1/chat/completions', 2048, 0.70, 0.90, 0.00, 0.00, 30, 3, '0', '1', '默认使用的GPT模型配置', 'admin', sysdate(), '', null);
insert into ai_model_config values(101, '本地模型配置', 'local', 'lawyer-ai-v1', null, 'http://localhost:8080/api/chat', 4096, 0.80, 0.95, 0.10, 0.10, 60, 2, '1', '0', '本地部署的法律AI模型', 'admin', sysdate(), '', null);

-- ----------------------------
-- 2、系统提示词配置表
-- ----------------------------
drop table if exists ai_system_prompt;
create table ai_system_prompt (
  prompt_id         bigint(20)      not null auto_increment    comment '提示词ID',
  prompt_name       varchar(100)    not null                   comment '提示词名称',
  prompt_type       varchar(50)     not null                   comment '提示词类型',
  scene_type        varchar(50)     default null               comment '场景类型',
  content           text            not null                   comment '提示词内容',
  variables         varchar(1000)   default null               comment '变量定义(JSON格式)',
  is_default        char(1)         default '0'                comment '是否默认（0否 1是）',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  remark            varchar(500)    default null               comment '备注',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (prompt_id)
) engine=innodb auto_increment=100 comment = '系统提示词配置表';

-- ----------------------------
-- 初始化-系统提示词配置表数据
-- ----------------------------
insert into ai_system_prompt values(100, '法律咨询默认提示词', 'legal_consultation', 'general', '你是一名专业的法律顾问，具有丰富的法律知识和实践经验。请根据用户的问题，提供准确、专业的法律建议。\n\n回答要求：\n1. 回答要准确、专业，避免给出错误的法律信息\n2. 回答要简洁明了，便于非专业人士理解\n3. 如果问题涉及复杂法律问题，建议用户咨询专业律师\n4. 回答中可以引用相关法律条文，但要注明来源\n5. 不要提供具体的法律文书模板，只提供指导性建议\n\n用户问题：{question}\n问题分类：{category}\n请根据以上要求回答用户的问题。', '{"question": "用户问题", "category": "问题分类"}', '1', '0', '用于法律咨询的默认提示词', 'admin', sysdate(), '', null);
insert into ai_system_prompt values(101, '民事纠纷提示词', 'legal_consultation', 'civil', '你是一名专业的民事法律顾问，擅长处理各类民事纠纷案件。\n\n回答要求：\n1. 针对民事纠纷提供专业的法律建议\n2. 解释相关民事法律条文和规定\n3. 提供可能的解决方案和维权途径\n4. 说明诉讼时效和相关程序\n5. 提醒用户保留相关证据\n\n用户问题：{question}\n纠纷类型：{category}\n请根据以上要求回答用户的问题。', '{"question": "用户问题", "category": "纠纷类型"}', '0', '0', '专门用于民事纠纷咨询的提示词', 'admin', sysdate(), '', null);
insert into ai_system_prompt values(102, '刑事辩护提示词', 'legal_consultation', 'criminal', '你是一名专业的刑事法律顾问，具有丰富的刑事辩护经验。\n\n回答要求：\n1. 解释相关刑事法律条文和规定\n2. 说明可能的法律后果和量刑标准\n3. 提供辩护策略和维权途径\n4. 解释刑事诉讼程序\n5. 强调及时聘请律师的重要性\n\n用户问题：{question}\n案件类型：{category}\n请根据以上要求回答用户的问题。', '{"question": "用户问题", "category": "案件类型"}', '0', '0', '专门用于刑事辩护咨询的提示词', 'admin', sysdate(), '', null);
insert into ai_system_prompt values(103, '合同纠纷提示词', 'legal_consultation', 'contract', '你是一名专业的合同法律顾问，擅长处理各类合同纠纷。\n\n回答要求：\n1. 解释相关合同法律条文和规定\n2. 分析合同条款的有效性和法律效力\n3. 提供合同纠纷的解决方案\n4. 说明违约责任和赔偿标准\n5. 提供合同风险防范建议\n\n用户问题：{question}\n合同类型：{category}\n请根据以上要求回答用户的问题。', '{"question": "用户问题", "category": "合同类型"}', '0', '0', '专门用于合同纠纷咨询的提示词', 'admin', sysdate(), '', null);