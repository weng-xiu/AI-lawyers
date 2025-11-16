-- 添加AI模型类型和提示词类型字典数据
-- 1. AI模型类型字典
INSERT INTO sys_dict_type VALUES (110, 'AI模型类型', 'ai_model_type', '0', 'admin', NOW(), '', NULL, 'AI模型类型字典');
INSERT INTO sys_dict_data VALUES (110, 1, 'OpenAI GPT', 'openai', 'ai_model_type', '', 'primary', 'Y', '0', 'admin', NOW(), '', NULL, 'OpenAI GPT模型');
INSERT INTO sys_dict_data VALUES (111, 2, '百度文心一言', 'wenxin', 'ai_model_type', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '百度文心一言模型');
INSERT INTO sys_dict_data VALUES (112, 3, '阿里通义千问', 'tongyi', 'ai_model_type', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '阿里通义千问模型');
INSERT INTO sys_dict_data VALUES (113, 4, '讯飞星火', 'spark', 'ai_model_type', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '讯飞星火模型');
INSERT INTO sys_dict_data VALUES (114, 5, '智谱AI', 'zhipu', 'ai_model_type', '', 'danger', 'N', '0', 'admin', NOW(), '', NULL, '智谱AI模型');

-- 2. AI提示词类型字典
INSERT INTO sys_dict_type VALUES (111, 'AI提示词类型', 'ai_prompt_type', '0', 'admin', NOW(), '', NULL, 'AI提示词类型字典');
INSERT INTO sys_dict_data VALUES (115, 1, '法律咨询', 'legal_consultation', 'ai_prompt_type', '', 'primary', 'Y', '0', 'admin', NOW(), '', NULL, '法律咨询提示词');
INSERT INTO sys_dict_data VALUES (116, 2, '文书生成', 'document_generation', 'ai_prompt_type', '', 'success', 'N', '0', 'admin', NOW(), '', NULL, '文书生成提示词');
INSERT INTO sys_dict_data VALUES (117, 3, '案例分析', 'case_analysis', 'ai_prompt_type', '', 'info', 'N', '0', 'admin', NOW(), '', NULL, '案例分析提示词');
INSERT INTO sys_dict_data VALUES (118, 4, '法律研究', 'legal_research', 'ai_prompt_type', '', 'warning', 'N', '0', 'admin', NOW(), '', NULL, '法律研究提示词');
INSERT INTO sys_dict_data VALUES (119, 5, '通用', 'general', 'ai_prompt_type', '', 'default', 'N', '0', 'admin', NOW(), '', NULL, '通用提示词');