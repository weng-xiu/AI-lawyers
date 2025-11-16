-- AI律师系统字典数据初始化脚本

-- 1. AI模型类型字典
INSERT INTO sys_dict_type VALUES (100, 'AI模型类型', 'ai_model_type', '0', 'admin', NOW(), '', NULL, 'AI模型类型字典');
INSERT INTO sys_dict_data VALUES (100, 1, 'OpenAI GPT', 'openai', 'ai_model_type', '', 'primary', 'Y', '0', 'admin', NOW(), '', NULL, 'OpenAI GPT模型');
INSERT INTO sys_dict_data VALUES (101, 2, '百度文心一言', 'wenxin', 'ai_model_type', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '百度文心一言模型');
INSERT INTO sys_dict_data VALUES (102, 3, '阿里通义千问', 'tongyi', 'ai_model_type', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '阿里通义千问模型');
INSERT INTO sys_dict_data VALUES (103, 4, '讯飞星火', 'spark', 'ai_model_type', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '讯飞星火模型');
INSERT INTO sys_dict_data VALUES (104, 5, '智谱AI', 'zhipu', 'ai_model_type', '', 'danger', 'N', '0', 'admin', NOW(), '', NULL, '智谱AI模型');

-- 2. AI提示词类型字典
INSERT INTO sys_dict_type VALUES (101, 'AI提示词类型', 'ai_prompt_type', '0', 'admin', NOW(), '', NULL, 'AI提示词类型字典');
INSERT INTO sys_dict_data VALUES (105, 1, '法律咨询', 'legal_consultation', 'ai_prompt_type', '', 'primary', 'Y', '0', 'admin', NOW(), '', NULL, '法律咨询提示词');
INSERT INTO sys_dict_data VALUES (106, 2, '文书生成', 'document_generation', 'ai_prompt_type', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '文书生成提示词');
INSERT INTO sys_dict_data VALUES (107, 3, '案例分析', 'case_analysis', 'ai_prompt_type', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '案例分析提示词');
INSERT INTO sys_dict_data VALUES (108, 4, '法律研究', 'legal_research', 'ai_prompt_type', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '法律研究提示词');
INSERT INTO sys_dict_data VALUES (109, 5, '通用', 'general', 'ai_prompt_type', '', 'default', 'N', '0', 'admin', NOW(), '', NULL, '通用提示词');

-- 3. AI场景类型字典
INSERT INTO sys_dict_type VALUES (102, 'AI场景类型', 'ai_scene_type', '0', 'admin', NOW(), '', NULL, 'AI场景类型字典');
INSERT INTO sys_dict_data VALUES (110, 1, '通用', 'general', 'ai_scene_type', '', '', 'Y', '0', 'admin', NOW(), '', NULL, '通用场景');
INSERT INTO sys_dict_data VALUES (111, 2, '民事', 'civil', 'ai_scene_type', '', '', 'N', '0', 'admin', NOW(), '', NULL, '民事场景');
INSERT INTO sys_dict_data VALUES (112, 3, '刑事', 'criminal', 'ai_scene_type', '', '', 'N', '0', 'admin', NOW(), '', NULL, '刑事场景');
INSERT INTO sys_dict_data VALUES (113, 4, '商事', 'commercial', 'ai_scene_type', '', '', 'N', '0', 'admin', NOW(), '', NULL, '商事场景');
INSERT INTO sys_dict_data VALUES (114, 5, '行政', 'administrative', 'ai_scene_type', '', '', 'N', '0', 'admin', NOW(), '', NULL, '行政场景');

-- 4. 咨询分类字典
INSERT INTO sys_dict_type VALUES (103, '咨询分类', 'consultation_category', '0', 'admin', NOW(), '', NULL, '咨询分类字典');
INSERT INTO sys_dict_data VALUES (115, 1, '婚姻家庭', 'marriage_family', 'consultation_category', '', '', 'Y', '0', 'admin', NOW(), '', NULL, '婚姻家庭咨询');
INSERT INTO sys_dict_data VALUES (116, 2, '劳动纠纷', 'labor_dispute', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '劳动纠纷咨询');
INSERT INTO sys_dict_data VALUES (117, 3, '合同纠纷', 'contract_dispute', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '合同纠纷咨询');
INSERT INTO sys_dict_data VALUES (118, 4, '房产纠纷', 'property_dispute', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '房产纠纷咨询');
INSERT INTO sys_dict_data VALUES (119, 5, '债务纠纷', 'debt_dispute', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '债务纠纷咨询');
INSERT INTO sys_dict_data VALUES (120, 6, '侵权责任', 'tort_liability', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '侵权责任咨询');
INSERT INTO sys_dict_data VALUES (121, 7, '知识产权', 'intellectual_property', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '知识产权咨询');
INSERT INTO sys_dict_data VALUES (122, 8, '刑事辩护', 'criminal_defense', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '刑事辩护咨询');
INSERT INTO sys_dict_data VALUES (123, 9, '行政诉讼', 'administrative_litigation', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '行政诉讼咨询');
INSERT INTO sys_dict_data VALUES (124, 10, '公司法务', 'corporate_legal', 'consultation_category', '', '', 'N', '0', 'admin', NOW(), '', NULL, '公司法务咨询');