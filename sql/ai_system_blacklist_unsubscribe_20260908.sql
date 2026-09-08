-- W4: 外呼合规——黑白名单扩展退订类型 + 外呼时段配置
-- 日期: 2026-09-08

-- 1. ai_call_blacklist.list_type 字典扩展：1黑名单 2白名单 3退订名单
--    退订名单用于"一处退订，呼叫+短信均禁止"
--    list_type 字段为 INT，无需 DDL 变更，仅需更新字典数据与前端展示

-- 2.（可选）为退订场景增加索引，加速按号码查询退订状态
--    若 ai_call_blacklist 已有 phone_number 索引则跳过
ALTER TABLE ai_call_blacklist ADD INDEX idx_phone_listtype (phone_number, list_type);

-- 3. 外呼合规配置（写入 application.yml 或环境变量，无需 SQL）
-- call.outbound.compliance.enabled=true
-- call.outbound.compliance.allowed-hours=09:00-21:00
-- call.outbound.compliance.timezone=Asia/Shanghai

-- 4. 示例：插入一条退订名单（测试用）
-- INSERT INTO ai_call_blacklist (phone_number, list_type, reason, status, effective_start, create_time)
-- VALUES ('13800138000', 3, '短信回复T退订', 1, NOW(), NOW());
