-- AI律师系统菜单和权限配置更新 - 系统配置菜单
-- 创建日期：2025-11-15

-- 1. 系统配置菜单
INSERT INTO sys_menu VALUES('3400', '系统配置', '3000', '4', 'systemConfig', null, '', '', 1, 0, 'M', '0', '0', '', 'system', 'admin', sysdate(), '', null, '系统配置目录');

-- 2. AI模型参数配置菜单
INSERT INTO sys_menu VALUES('3410', 'AI模型参数配置', '3400', '1', 'modelConfig', 'lawyers/modelConfig/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:modelConfig:list', 'robot', 'admin', sysdate(), '', null, 'AI模型参数配置菜单');

-- 3. AI模型参数配置按钮权限
INSERT INTO sys_menu VALUES('3411', '模型配置查询', '3410', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3412', '模型配置新增', '3410', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3413', '模型配置修改', '3410', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3414', '模型配置删除', '3410', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3415', '模型配置导出', '3410', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3416', '获取默认配置', '3410', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:default', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3417', '设置默认配置', '3410', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:setDefault', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3418', '测试模型连接', '3410', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:modelConfig:test', '#', 'admin', sysdate(), '', null, '');

-- 4. 系统提示词配置菜单
INSERT INTO sys_menu VALUES('3420', '系统提示词配置', '3400', '2', 'systemPrompt', 'lawyers/systemPrompt/index', '', '', 1, 0, 'C', '0', '0', 'lawyers:systemPrompt:list', 'message', 'admin', sysdate(), '', null, '系统提示词配置菜单');

-- 5. 系统提示词配置按钮权限
INSERT INTO sys_menu VALUES('3421', '提示词查询', '3420', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:query', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3422', '提示词新增', '3420', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:add', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3423', '提示词修改', '3420', '3', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:edit', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3424', '提示词删除', '3420', '4', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:remove', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3425', '提示词导出', '3420', '5', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:export', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3426', '获取默认提示词', '3420', '6', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:default', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3427', '设置默认提示词', '3420', '7', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:setDefault', '#', 'admin', sysdate(), '', null, '');
INSERT INTO sys_menu VALUES('3428', '预览提示词效果', '3420', '8', '', '', '', '', 1, 0, 'F', '0', '0', 'lawyers:systemPrompt:preview', '#', 'admin', sysdate(), '', null, '');

-- 6. 为超级管理员角色分配新增的菜单权限
INSERT INTO sys_role_menu VALUES (1, 3400);
INSERT INTO sys_role_menu VALUES (1, 3410);
INSERT INTO sys_role_menu VALUES (1, 3411);
INSERT INTO sys_role_menu VALUES (1, 3412);
INSERT INTO sys_role_menu VALUES (1, 3413);
INSERT INTO sys_role_menu VALUES (1, 3414);
INSERT INTO sys_role_menu VALUES (1, 3415);
INSERT INTO sys_role_menu VALUES (1, 3416);
INSERT INTO sys_role_menu VALUES (1, 3417);
INSERT INTO sys_role_menu VALUES (1, 3418);
INSERT INTO sys_role_menu VALUES (1, 3420);
INSERT INTO sys_role_menu VALUES (1, 3421);
INSERT INTO sys_role_menu VALUES (1, 3422);
INSERT INTO sys_role_menu VALUES (1, 3423);
INSERT INTO sys_role_menu VALUES (1, 3424);
INSERT INTO sys_role_menu VALUES (1, 3425);
INSERT INTO sys_role_menu VALUES (1, 3426);
INSERT INTO sys_role_menu VALUES (1, 3427);
INSERT INTO sys_role_menu VALUES (1, 3428);

-- 7. 添加模型类型字典类型
INSERT INTO sys_dict_type VALUES('101', 'AI模型类型', 'lawyers_model_type', '0', 'admin', sysdate(), '', null, 'AI模型类型列表');

-- 8. 添加模型类型字典数据
INSERT INTO sys_dict_data VALUES('1010', 1, 'OpenAI GPT', 'openai', 'lawyers_model_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', null, 'OpenAI GPT模型');
INSERT INTO sys_dict_data VALUES('1011', 2, '百度文心一言', 'wenxin', 'lawyers_model_type', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '百度文心一言模型');
INSERT INTO sys_dict_data VALUES('1012', 3, '阿里通义千问', 'tongyi', 'lawyers_model_type', '', 'info', 'N', '0', 'admin', sysdate(), '', null, '阿里通义千问模型');
INSERT INTO sys_dict_data VALUES('1013', 4, '讯飞星火', 'spark', 'lawyers_model_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '讯飞星火模型');
INSERT INTO sys_dict_data VALUES('1014', 5, '智谱AI', 'zhipu', 'lawyers_model_type', '', 'danger', 'N', '0', 'admin', sysdate(), '', null, '智谱AI模型');

-- 9. 添加提示词类型字典类型
INSERT INTO sys_dict_type VALUES('102', '提示词类型', 'lawyers_prompt_type', '0', 'admin', sysdate(), '', null, '提示词类型列表');

-- 10. 添加提示词类型字典数据
INSERT INTO sys_dict_data VALUES('1020', 1, '法律咨询', 'legal_consultation', 'lawyers_prompt_type', '', 'primary', 'N', '0', 'admin', sysdate(), '', null, '法律咨询提示词');
INSERT INTO sys_dict_data VALUES('1021', 2, '文书生成', 'document_generation', 'lawyers_prompt_type', '', 'success', 'N', '0', 'admin', sysdate(), '', null, '文书生成提示词');
INSERT INTO sys_dict_data VALUES('1022', 3, '案例分析', 'case_analysis', 'lawyers_prompt_type', '', 'info', 'N', '0', 'admin', sysdate(), '', null, '案例分析提示词');
INSERT INTO sys_dict_data VALUES('1023', 4, '法律研究', 'legal_research', 'lawyers_prompt_type', '', 'warning', 'N', '0', 'admin', sysdate(), '', null, '法律研究提示词');
INSERT INTO sys_dict_data VALUES('1024', 5, '通用', 'general', 'lawyers_prompt_type', '', 'default', 'N', '0', 'admin', sysdate(), '', null, '通用提示词');