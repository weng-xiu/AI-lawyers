SET NAMES utf8mb4;

-- 4 名律师 SIP 分机配置：工号 102-105 -> 分机 1003-1006
UPDATE sys_user SET sip_extension = '1003' WHERE agent_id = 102;
UPDATE sys_user SET sip_extension = '1004' WHERE agent_id = 103;
UPDATE sys_user SET sip_extension = '1005' WHERE agent_id = 104;
UPDATE sys_user SET sip_extension = '1006' WHERE agent_id = 105;

-- 坐席运行表同步（签入时读取该字段注册软电话）
UPDATE ai_call_agent_status SET sip_extension = '1003' WHERE agent_id = 102;
UPDATE ai_call_agent_status SET sip_extension = '1004' WHERE agent_id = 103;
UPDATE ai_call_agent_status SET sip_extension = '1005' WHERE agent_id = 104;
UPDATE ai_call_agent_status SET sip_extension = '1006' WHERE agent_id = 105;
