-- =============================================================
-- 容器化部署：数据库初始化后修正 FreeSWITCH 线路网关主机
-- 容器内后端通过服务名访问 FreeSWITCH，需把 ESL/SIP 网关指向服务名
-- 本文件在 MySQL 容器首次启动、业务库导入后执行（由 deploy 脚本处理）
-- =============================================================

-- FreeSWITCH 线路（vendor=FREESWITCH）网关指向 compose 服务名 freeswitch
UPDATE ai_call_trunk
SET gateway_host = 'freeswitch'
WHERE vendor = 'FREESWITCH';

-- HTTP_API 兜底线路在容器环境暂不可用，禁用以免健康检查报错
UPDATE ai_call_trunk
SET enable_flag = '0'
WHERE vendor = 'HTTP_API';
