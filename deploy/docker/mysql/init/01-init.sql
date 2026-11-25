-- =====================================================================
-- 广东12348公共法律服务热线 —— MySQL 8 基线初始化脚本
-- 用途：MySQL 容器首次启动时自动执行（docker-compose 挂载 init 目录到 initdb.d）
-- 来源：合并 sql/ 下历史增量脚本（ry_20250522 起至 20261107）后的最终结构与种子数据
-- 内容：114 张表；admin 账号；完整菜单/字典/配置；律师坐席账号；中继线路种子
-- 编码：UTF-8（无 BOM），导入需 --default-character-set=utf8mb4
-- 执行顺序：01-init.sql → 99-container-trunk-fix.sql
-- =====================================================================
-- MySQL dump 10.13  Distrib 8.0.26, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: ai-law
-- ------------------------------------------------------
-- Server version	8.0.26

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_agent_config`
--

DROP TABLE IF EXISTS `ai_agent_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_agent_config` (
  `agent_id` bigint NOT NULL AUTO_INCREMENT COMMENT '智能体ID',
  `agent_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '智能体名称',
  `provider` varchar(30) COLLATE utf8mb4_general_ci DEFAULT 'local' COMMENT '平台（local本地知识库 maxkb dify fastgpt coze）',
  `api_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部平台对话接口地址',
  `api_key` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部平台认证密钥（加密存储）',
  `app_id` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '平台应用/知识库ID',
  `category_id` bigint DEFAULT NULL COMMENT '默认关联咨询分类ID',
  `system_prompt` text COLLATE utf8mb4_general_ci COMMENT '角色设定系统提示词',
  `model_id` bigint DEFAULT NULL COMMENT '关联大模型配置ID（为空使用默认模型）',
  `knowledge_ids` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '限定知识库ID（逗号分隔，空则全库检索）',
  `enable_context` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否启用多轮上下文（0否 1是）',
  `context_rounds` int DEFAULT '5' COMMENT '上下文保留轮数',
  `handoff_keywords` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '转人工关键词（逗号分隔，命中即转人工）',
  `welcome` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '首轮欢迎语（无用户输入时播报）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '状态（0停用 1启用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`agent_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI智能体配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_agent_config`
--

LOCK TABLES `ai_agent_config` WRITE;
/*!40000 ALTER TABLE `ai_agent_config` DISABLE KEYS */;
INSERT INTO `ai_agent_config` VALUES (100,'12348法律咨询助手','local',NULL,NULL,NULL,NULL,'你是12348公共法律服务热线的智能法律助手，基于提供的法律知识回答群众咨询。要求：1.只回答法律相关问题，用语专业、准确、通俗易懂；2.优先依据\"参考知识\"作答，无依据时如实说明并建议咨询人工律师；3.不提供具体诉讼代理意见，不替代正式法律意见；4.涉及紧急危险、信访投诉、强烈要求人工或问题超出能力范围时，应判定需要转接人工。',NULL,NULL,'1',5,'人工,律师,转人工,找律师,投诉,信访,起诉,报警,12345,骂人,生气','您好，这里是12348公共法律服务热线智能助手，请问您有什么法律问题需要咨询？','1','admin','2026-11-16 22:43:25','',NULL,'B1默认智能体，基于本地法律知识库+默认大模型，可直接用于agentChat节点');
/*!40000 ALTER TABLE `ai_agent_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_agent_message`
--

DROP TABLE IF EXISTS `ai_agent_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_agent_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID（对应IVR sessionId）',
  `agent_id` bigint NOT NULL COMMENT '智能体ID',
  `flow_id` bigint DEFAULT NULL COMMENT '所属IVR流程ID',
  `node_id` bigint DEFAULT NULL COMMENT '所属IVR节点ID',
  `record_id` bigint DEFAULT NULL COMMENT '关联通话记录ID',
  `caller_number` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主叫号码',
  `role` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色（user用户 assistant助手）',
  `content` text COLLATE utf8mb4_general_ci COMMENT '消息内容',
  `handoff` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '本轮是否触发转人工（0否 1是，仅assistant）',
  `reason` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '转人工原因',
  `knowledge_refs` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '命中的知识库ID（逗号分隔）',
  `turn_no` int DEFAULT '0' COMMENT '轮次序号（同一session内自增）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`message_id`),
  UNIQUE KEY `uk_session_turn` (`session_id`,`turn_no`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_agent_id` (`agent_id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI智能体对话消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_agent_message`
--

LOCK TABLES `ai_agent_message` WRITE;
/*!40000 ALTER TABLE `ai_agent_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_agent_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_agent_schedule`
--

DROP TABLE IF EXISTS `ai_agent_schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_agent_schedule` (
  `schedule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '排班ID',
  `agent_id` bigint NOT NULL COMMENT '坐席ID',
  `shift_id` bigint NOT NULL COMMENT '班次ID',
  `schedule_date` date NOT NULL COMMENT '排班日期',
  `schedule_type` tinyint DEFAULT '1' COMMENT '排班类型 1正常 2加班 3调休 4请假',
  `status` tinyint DEFAULT '1' COMMENT '状态 0已取消 1有效',
  `check_in_time` datetime DEFAULT NULL COMMENT '签到时间',
  `check_out_time` datetime DEFAULT NULL COMMENT '签退时间',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`schedule_id`),
  UNIQUE KEY `uk_agent_date` (`agent_id`,`schedule_date`),
  KEY `idx_date` (`schedule_date`),
  KEY `idx_agent` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='坐席排班';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_agent_schedule`
--

LOCK TABLES `ai_agent_schedule` WRITE;
/*!40000 ALTER TABLE `ai_agent_schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_agent_schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_agent_status_log`
--

DROP TABLE IF EXISTS `ai_agent_status_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_agent_status_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流水ID',
  `agent_id` bigint NOT NULL COMMENT '坐席ID',
  `user_id` bigint DEFAULT NULL COMMENT '绑定用户ID',
  `event_type` varchar(32) DEFAULT NULL COMMENT '事件类型 LOGIN/LOGOUT/STATUS/MAKE_CALL/HANGUP/AFTER_WORK/HOLD/RESUME/TRANSFER 等',
  `from_status` char(1) DEFAULT NULL COMMENT '原在线状态 0离线1在线2忙碌3休息',
  `to_status` char(1) DEFAULT NULL COMMENT '新在线状态',
  `from_call_status` char(1) DEFAULT NULL COMMENT '原通话状态 0空闲1通话2保持3咨询4三方5话后',
  `to_call_status` char(1) DEFAULT NULL COMMENT '新通话状态',
  `duration` int DEFAULT '0' COMMENT '上一状态持续秒数（下一切换时回填）',
  `record_id` bigint DEFAULT NULL COMMENT '关联通话记录ID（通话类事件）',
  `log_time` datetime DEFAULT NULL COMMENT '状态切换时间',
  `create_time` datetime DEFAULT NULL COMMENT '落库时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_agent_time` (`agent_id`,`log_time`),
  KEY `idx_log_time` (`log_time`),
  KEY `idx_ai_agent_status_log_ct` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='坐席状态变更流水表（T4-3）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_agent_status_log`
--

LOCK TABLES `ai_agent_status_log` WRITE;
/*!40000 ALTER TABLE `ai_agent_status_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_agent_status_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ai_call_session`
--

DROP TABLE IF EXISTS `ai_ai_call_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ai_call_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'AI辅助会话ID',
  `record_id` bigint DEFAULT NULL COMMENT '关联人工通话记录ID',
  `agent_id` bigint DEFAULT NULL COMMENT '人工坐席ID',
  `caller_phone` varchar(20) DEFAULT NULL COMMENT '主叫号码',
  `caller_name` varchar(50) DEFAULT NULL COMMENT '客户姓名',
  `session_status` char(1) DEFAULT '0' COMMENT '状态 0初始化 1分析中 2推荐中 3已小结 4已结束 9异常',
  `intent_category` varchar(64) DEFAULT NULL COMMENT '识别的咨询意图分类',
  `recommend_laws` text COMMENT 'AI推荐的法条(JSON)',
  `recommend_scripts` text COMMENT 'AI推荐的话术建议(JSON)',
  `call_summary` text COMMENT 'AI自动生成的通话小结',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`session_id`),
  KEY `idx_session_record` (`record_id`),
  KEY `idx_session_agent` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI律师辅助会话';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ai_call_session`
--

LOCK TABLES `ai_ai_call_session` WRITE;
/*!40000 ALTER TABLE `ai_ai_call_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_ai_call_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_agent_status`
--

DROP TABLE IF EXISTS `ai_call_agent_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_agent_status` (
  `agent_id` bigint NOT NULL AUTO_INCREMENT COMMENT '坐席ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `agent_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '坐席名称',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0离线 1在线 2忙碌 3休息）',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  `logout_time` datetime DEFAULT NULL COMMENT '注销时间',
  `last_login_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登录IP',
  `sip_extension` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SIP分机号（需与 FreeSWITCH 分机一致）',
  `call_mode` varchar(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '应答模式 0=自动应答 1=手动应答',
  `current_call_id` bigint DEFAULT NULL COMMENT '当前通话记录ID',
  `current_call_phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前通话号码',
  `call_start_time` datetime DEFAULT NULL COMMENT '当前通话开始时间',
  `call_status` varchar(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '通话状态 0=空闲 1=通话中 2=保持 3=咨询中 4=三方 5=话后整理',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='坐席状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_agent_status`
--

LOCK TABLES `ai_call_agent_status` WRITE;
/*!40000 ALTER TABLE `ai_call_agent_status` DISABLE KEYS */;
INSERT INTO `ai_call_agent_status` VALUES (100,1,'管理员','0',NULL,NULL,'127.0.0.1',NULL,'0',NULL,NULL,NULL,'0','admin','2026-11-16 22:43:19','',NULL,'系统管理员'),(101,2,'测试员','0',NULL,NULL,'127.0.0.1',NULL,'0',NULL,NULL,NULL,'0','admin','2026-11-16 22:43:19','',NULL,'测试坐席'),(102,1001,'张律师','1','2026-03-14 08:30:00',NULL,'192.168.1.10','1003','0',NULL,NULL,NULL,'0','admin','2026-03-14 08:30:00','',NULL,'民商事法律咨询坐席'),(103,1002,'李律师','1','2026-03-14 08:35:00',NULL,'192.168.1.11','1004','0',NULL,NULL,NULL,'0','admin','2026-03-14 08:35:00','',NULL,'劳动纠纷坐席'),(104,1003,'王律师','1','2026-03-14 08:40:00',NULL,'192.168.1.12','1005','0',NULL,NULL,NULL,'0','admin','2026-03-14 08:40:00','',NULL,'婚姻家庭坐席'),(105,1004,'赵律师','2','2026-03-14 09:00:00',NULL,'192.168.1.13','1006','0',NULL,NULL,NULL,'0','admin','2026-03-14 09:00:00','',NULL,'刑事辩护坐席'),(106,0,'刘律师','1','2026-03-14 08:45:00',NULL,'192.168.1.14',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 08:45:00','',NULL,'行政纠纷坐席'),(107,0,'陈律师','1','2026-03-14 08:50:00',NULL,'192.168.1.15',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 08:50:00','',NULL,'知识产权坐席'),(108,0,'孙律师','3','2026-03-14 09:10:00',NULL,'192.168.1.16',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 09:10:00','',NULL,'房产纠纷坐席'),(109,0,'周律师','1','2026-03-14 08:55:00',NULL,'192.168.1.17',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 08:55:00','',NULL,'合同纠纷坐席'),(110,0,'吴律师','0',NULL,NULL,'192.168.1.18',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 09:00:00','',NULL,'离线坐席'),(111,0,'郑律师','0',NULL,NULL,'192.168.1.19',NULL,'0',NULL,NULL,NULL,'0','admin','2026-03-14 09:00:00','',NULL,'离线坐席');
/*!40000 ALTER TABLE `ai_call_agent_status` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_blacklist`
--

DROP TABLE IF EXISTS `ai_call_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone_number` varchar(20) NOT NULL COMMENT '电话号码',
  `list_type` tinyint NOT NULL DEFAULT '1' COMMENT '名单类型 1黑名单 2白名单',
  `reason` varchar(500) DEFAULT NULL COMMENT '加入原因',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态 0停用 1启用',
  `effective_start` datetime DEFAULT NULL COMMENT '生效开始时间',
  `effective_end` datetime DEFAULT NULL COMMENT '生效结束时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone_type` (`phone_number`,`list_type`),
  KEY `idx_phone` (`phone_number`),
  KEY `idx_phone_listtype` (`phone_number`,`list_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通话黑白名单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_blacklist`
--

LOCK TABLES `ai_call_blacklist` WRITE;
/*!40000 ALTER TABLE `ai_call_blacklist` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_blacklist` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_dial_log`
--

DROP TABLE IF EXISTS `ai_call_dial_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_dial_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `call_uuid` varchar(64) DEFAULT NULL COMMENT '呼叫唯一标识',
  `record_id` bigint DEFAULT NULL COMMENT '关联通话记录ID',
  `task_id` bigint DEFAULT NULL COMMENT '关联外呼任务ID',
  `callee_id` bigint DEFAULT NULL COMMENT '关联外呼被叫号码ID',
  `agent_id` bigint DEFAULT NULL COMMENT '发起坐席ID',
  `caller_number` varchar(20) DEFAULT NULL COMMENT '主叫号码',
  `callee_number` varchar(20) DEFAULT NULL COMMENT '被叫号码',
  `callee_carrier` char(2) DEFAULT NULL COMMENT '被叫运营商',
  `callee_province` varchar(32) DEFAULT NULL COMMENT '被叫归属省',
  `callee_city` varchar(32) DEFAULT NULL COMMENT '被叫归属市',
  `trunk_id` bigint DEFAULT NULL COMMENT '实际使用线路ID',
  `trunk_code` varchar(32) DEFAULT NULL COMMENT '实际使用线路编码',
  `trunk_carrier` char(2) DEFAULT NULL COMMENT '线路运营商',
  `line_type` char(1) DEFAULT NULL COMMENT '线路类型',
  `route_strategy` varchar(32) DEFAULT NULL COMMENT '路由策略',
  `failover_count` int DEFAULT '0' COMMENT '故障切换次数',
  `failover_trunks` varchar(500) DEFAULT NULL COMMENT '切换途经线路链',
  `queue_wait_ms` bigint DEFAULT '0' COMMENT '排队等待毫秒',
  `dial_status` char(1) DEFAULT '0' COMMENT '拨号状态 0排队 1拨号 2振铃 3接通 4挂断 5失败 6超时 7被拒 8占线 9空号',
  `hangup_cause` varchar(64) DEFAULT NULL COMMENT '挂断原因',
  `sip_code` int DEFAULT NULL COMMENT 'SIP 响应码',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `dial_time` datetime DEFAULT NULL COMMENT '拨号时间',
  `ring_time` datetime DEFAULT NULL COMMENT '振铃时间',
  `answer_time` datetime DEFAULT NULL COMMENT '接通时间',
  `hangup_time` datetime DEFAULT NULL COMMENT '挂断时间',
  `ring_duration` int DEFAULT '0' COMMENT '振铃时长(秒)',
  `talk_duration` int DEFAULT '0' COMMENT '通话时长(秒)',
  `total_duration` int DEFAULT '0' COMMENT '呼叫总时长(秒)',
  `bill_duration` int DEFAULT '0' COMMENT '计费时长(秒)',
  `is_connected` char(1) DEFAULT '0' COMMENT '是否接通 0=否 1=是',
  `mos` decimal(4,2) DEFAULT NULL COMMENT 'MOS 评分',
  `packet_loss` decimal(5,2) DEFAULT NULL COMMENT '丢包率(%)',
  `jitter` int DEFAULT NULL COMMENT '抖动(ms)',
  `rtt` int DEFAULT NULL COMMENT '往返时延(ms)',
  `record_file` varchar(255) DEFAULT NULL COMMENT '录音文件',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`log_id`),
  KEY `idx_dial_uuid` (`call_uuid`),
  KEY `idx_dial_time` (`dial_time`),
  KEY `idx_dial_trunk_time` (`trunk_id`,`dial_time`),
  KEY `idx_dial_callee` (`callee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拨号明细日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_dial_log`
--

LOCK TABLES `ai_call_dial_log` WRITE;
/*!40000 ALTER TABLE `ai_call_dial_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_dial_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_event_dedup`
--

DROP TABLE IF EXISTS `ai_call_event_dedup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_event_dedup` (
  `dedup_key` varchar(191) NOT NULL COMMENT '幂等键：source|eventKey|eventName',
  `source` varchar(64) NOT NULL COMMENT '事件来源，如 ESL:host:port',
  `event_key` varchar(64) NOT NULL COMMENT '事件对象键，通常为通道 UUID',
  `event_name` varchar(64) NOT NULL COMMENT '规范化事件名（HANGUP_COMPLETE 归一为 HANGUP）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次到达时间',
  PRIMARY KEY (`dedup_key`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='P3-B4 PBX事件幂等去重表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_event_dedup`
--

LOCK TABLES `ai_call_event_dedup` WRITE;
/*!40000 ALTER TABLE `ai_call_event_dedup` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_event_dedup` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_ledger`
--

DROP TABLE IF EXISTS `ai_call_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_ledger` (
  `ledger_id` bigint NOT NULL AUTO_INCREMENT COMMENT '台账ID',
  `ledger_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '台账编号',
  `record_id` bigint DEFAULT NULL COMMENT '关联来电记录ID',
  `ticket_id` bigint DEFAULT NULL COMMENT '关联工单ID',
  `caller_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '咨询人姓名',
  `caller_phone` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话（SM4-GCM 密文 encp: 前缀，G1-b2）',
  `caller_id_card` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证号（SM4-GCM 密文 encp: 前缀，G1-b2）',
  `caller_gender` char(1) COLLATE utf8mb4_general_ci DEFAULT '2' COMMENT '性别（0男1女2未知）',
  `caller_age` int DEFAULT NULL COMMENT '年龄',
  `caller_job` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '职业',
  `caller_company` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工作单位',
  `caller_address` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系地址',
  `category_id` bigint DEFAULT NULL COMMENT '咨询分类ID',
  `category_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '咨询分类名称',
  `sub_category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '问题分类',
  `service_type` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '服务方式（1电话2现场3网络4视频）',
  `source_channel` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源渠道',
  `lawyer_id` bigint DEFAULT NULL COMMENT '承办律师ID',
  `lawyer_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '承办律师姓名',
  `consult_content` text COLLATE utf8mb4_general_ci COMMENT '咨询摘要',
  `involve_amount` decimal(12,2) DEFAULT NULL COMMENT '涉及金额',
  `lawyer_answer` text COLLATE utf8mb4_general_ci COMMENT '律师解答意见',
  `consult_duration` int DEFAULT '0' COMMENT '咨询时长(分钟)',
  `satisfaction` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '满意度（1非常满意2满意3一般4不满意）',
  `is_visit` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否已回访（0否1是）',
  `visit_time` datetime DEFAULT NULL COMMENT '回访时间',
  `visit_by` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回访人',
  `visit_opinion` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '回访意见',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  PRIMARY KEY (`ledger_id`),
  UNIQUE KEY `uk_ledger_no` (`ledger_no`),
  KEY `idx_ai_call_ledger_record_id` (`record_id`),
  KEY `idx_ai_call_ledger_ticket_id` (`ticket_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='咨询台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_ledger`
--

LOCK TABLES `ai_call_ledger` WRITE;
/*!40000 ALTER TABLE `ai_call_ledger` DISABLE KEYS */;
INSERT INTO `ai_call_ledger` VALUES (1000,'DJ20260314100000ABCDEF',NULL,NULL,'张三','13800138001','110101199001011234','0',36,'工程师','北京科技有限公司','北京市朝阳区建国路88号',1,'民事纠纷','合同纠纷','1','电话咨询',1,'李律师','咨询劳动合同解除相关问题',50000.00,'建议收集证据申请劳动仲裁',30,'2','1','2026-03-15 10:00:00','王主任','对解答满意','admin','2026-03-14 10:00:00','admin','2026-03-15 10:00:00','首次咨询','0'),(1001,'DJ20260314110000ABCDEG',NULL,NULL,'李四','13800138002','110101198505055678','1',41,'教师','北京市第一中学','北京市海淀区中关村大街1号',2,'刑事辩护','盗窃案件','2','现场咨询',2,'赵律师','咨询亲属盗窃案件辩护事宜',0.00,'建议尽快委托律师介入',60,'1','0',NULL,NULL,NULL,'admin','2026-03-14 11:00:00','',NULL,'家属代为咨询','0'),(1002,'DJ20260314140000ABCDEH',NULL,NULL,'王五','13800138003','110101199212129012','0',34,'个体工商户','王五服装店','北京市西城区西单北大街100号',3,'婚姻家庭','离婚财产分割','3','网络咨询',3,'陈律师','咨询离婚财产分割问题',2000000.00,'建议协商解决，协商不成起诉',45,'3','0',NULL,NULL,NULL,'admin','2026-03-14 14:00:00','',NULL,'涉及房产分割','0'),(1003,'DJ20260314153000ABCDEI',NULL,NULL,'赵六','13800138004','110101198808083456','1',38,'医生','北京协和医院','北京市东城区王府井大街1号',4,'交通事故','人身损害赔偿','4','视频咨询',4,'孙律师','咨询交通事故赔偿标准',300000.00,'建议做伤残鉴定后起诉',50,'2','1','2026-03-16 09:30:00','周助理','满意解答，后续会考虑委托','admin','2026-03-14 15:30:00','admin','2026-03-16 09:30:00','已预约面谈','0'),(1004,'DJ20260314164500ABCDEJ',NULL,NULL,'钱七','13800138005','110101199509097890','0',31,'程序员','互联网科技公司','北京市昌平区回龙观西大街',5,'知识产权','商标侵权','1','电话咨询',5,'吴律师','咨询商标被侵权如何维权',150000.00,'建议收集证据发律师函，必要时起诉',25,'4','0',NULL,NULL,NULL,'admin','2026-03-14 16:45:00','',NULL,'对解答不太满意','0');
/*!40000 ALTER TABLE `ai_call_ledger` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_queue`
--

DROP TABLE IF EXISTS `ai_call_queue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_queue` (
  `queue_id` bigint NOT NULL AUTO_INCREMENT COMMENT '排队ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT 'IVR会话ID',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `caller_number` varchar(20) DEFAULT NULL COMMENT '主叫号码',
  `group_id` bigint NOT NULL COMMENT '目标技能组ID',
  `enqueue_time` datetime NOT NULL COMMENT '入队时间',
  `dequeue_time` datetime DEFAULT NULL COMMENT '出队时间',
  `answer_time` datetime DEFAULT NULL COMMENT '应答时间（SLA，T4-3）',
  `ring_duration` int DEFAULT NULL COMMENT '振铃时长秒（SLA，T4-3）',
  `abandon_time` datetime DEFAULT NULL COMMENT '放弃时间（SLA，T4-3）',
  `agent_id` bigint DEFAULT NULL COMMENT '分配到的坐席ID',
  `wait_duration` int DEFAULT '0' COMMENT '等待时长（秒）',
  `queue_status` char(1) DEFAULT '0' COMMENT '排队状态（0排队中 1已分配 2超时溢出 3已放弃 4无可用坐席）',
  `strategy_used` varchar(20) DEFAULT NULL COMMENT '实际命中的分配策略',
  `priority` int DEFAULT '0' COMMENT '排队优先级（VIP/情绪激动可提升）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`queue_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_group_status` (`group_id`,`queue_status`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_enqueue_time` (`enqueue_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排队/分配流水表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_queue`
--

LOCK TABLES `ai_call_queue` WRITE;
/*!40000 ALTER TABLE `ai_call_queue` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_queue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_record`
--

DROP TABLE IF EXISTS `ai_call_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_record` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `caller_number` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '来电号码（SM4-GCM 密文 encp: 前缀，G1-b2）',
  `caller_number_index` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电号码盲索引（HMAC-SM3 截断 128 位 hex，G1-b2）',
  `caller_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电人姓名',
  `caller_address` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电人地址',
  `call_time` datetime NOT NULL COMMENT '来电时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int DEFAULT '0' COMMENT '通话时长(秒)',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席ID（来话在坐席分配前先落单，必须可空；对应 README 手工 ALTER 口径）',
  `category_id` bigint DEFAULT NULL COMMENT '咨询分类ID',
  `content` text COLLATE utf8mb4_general_ci COMMENT '咨询内容',
  `answer` text COLLATE utf8mb4_general_ci COMMENT '解答内容',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0接通中 1已完成 2已转接 3未接）',
  `transfer_id` bigint DEFAULT NULL COMMENT '转接记录ID',
  `ticket_id` bigint DEFAULT NULL COMMENT '工单ID',
  `call_uuid` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通话通道UUID(FreeSWITCH Unique-ID)',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `record_file` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '录音文件路径',
  `recording_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '录音访问URL',
  `record_duration` int DEFAULT '0' COMMENT '录音时长(秒)',
  `asr_status` tinyint DEFAULT '0' COMMENT 'ASR转写状态 0待转写 1转写中 2已完成 3失败',
  `transcript` text COLLATE utf8mb4_general_ci COMMENT 'ASR转写文本',
  `ai_summary` mediumtext COLLATE utf8mb4_general_ci COMMENT 'AI通话小结（案情摘要/法律意见/待办/回访建议）',
  `ai_summary_status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT 'AI小结状态 0待生成 1生成中 2已生成 3失败',
  `ai_summary_time` datetime DEFAULT NULL COMMENT 'AI小结生成时间',
  `ai_summary_fail_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'AI小结失败原因',
  PRIMARY KEY (`record_id`),
  KEY `idx_ai_call_record_call_uuid` (`call_uuid`),
  KEY `idx_ai_call_record_ct` (`create_time`),
  KEY `idx_record_number_index` (`caller_number_index`),
  KEY `idx_call_record_call_time` (`call_time`),
  KEY `idx_call_record_agent_call_time` (`agent_id`,`call_time`),
  KEY `idx_call_record_status_call_time` (`status`,`call_time`),
  KEY `idx_call_record_category_call_time` (`category_id`,`call_time`),
  KEY `idx_call_record_status_end_time` (`status`,`end_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1030 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='来电记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_record`
--

LOCK TABLES `ai_call_record` WRITE;
/*!40000 ALTER TABLE `ai_call_record` DISABLE KEYS */;
INSERT INTO `ai_call_record` VALUES (1000,'13800138001',NULL,'张先生','广东省广州市天河区珠江新城','2026-03-14 09:15:30','2026-03-14 09:23:45',495,102,1,'咨询劳动合同解除相关法律问题，公司拖欠工资3个月','根据《劳动合同法》第三十八条规定，用人单位未及时足额支付劳动报酬的，劳动者可以解除劳动合同，并要求经济补偿。','1',NULL,NULL,NULL,'admin','2026-03-14 09:15:30','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1001,'13900139002',NULL,'李女士','广东省深圳市南山区科技园','2026-03-14 09:30:20','2026-03-14 09:42:35',735,103,2,'咨询离婚财产分割问题，婚前房产婚后共同还贷如何分配','婚前一方付首付并登记在自己名下的房产，婚后共同还贷部分及增值部分属于夫妻共同财产，离婚时应予分割。','1',NULL,NULL,NULL,'admin','2026-03-14 09:30:20','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1002,'13600136003',NULL,'王先生','广东省珠海市香洲区吉大','2026-03-14 10:05:10','2026-03-14 10:15:40',630,104,3,'咨询继承纠纷，父母去世后房产继承分配问题','根据《民法典》继承编，没有遗嘱的按法定继承办理，第一顺序继承人为配偶、子女、父母，一般均等分配。','1',NULL,NULL,NULL,'admin','2026-03-14 10:05:10','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1003,'13700137004',NULL,'赵女士','广东省佛山市禅城区祖庙','2026-03-14 10:20:00','2026-03-14 10:30:20',620,105,4,'咨询交通事故人身损害赔偿标准','根据《民法典》侵权责任编，人身损害赔偿包括医疗费、护理费、误工费等，构成伤残的还包括残疾赔偿金。','1',NULL,NULL,NULL,'admin','2026-03-14 10:20:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1004,'13500135005',NULL,'钱先生','广东省东莞市莞城区','2026-03-14 10:45:30','2026-03-14 10:55:15',585,106,5,'咨询商标被侵权如何维权','建议收集侵权证据，发送律师函要求停止侵权，必要时向人民法院起诉索赔。','1',NULL,NULL,NULL,'admin','2026-03-14 10:45:30','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1005,'18800188006',NULL,'孙女士','广东省广州市越秀区北京路','2026-03-14 11:00:00','2026-03-14 11:12:30',750,107,6,'咨询房屋租赁合同纠纷，房东不退押金','根据《民法典》合同编，租赁合同到期后出租人应在合理期限内退还押金，无正当理由扣留的，承租人可以起诉。','1',NULL,NULL,NULL,'admin','2026-03-14 11:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1006,'18600186007',NULL,'周先生','广东省中山市石岐区','2026-03-14 11:15:20','2026-03-14 11:28:45',805,109,7,'咨询民间借贷纠纷，借款人不还款','建议收集借条、转账记录等证据，先协商解决，协商不成可向人民法院提起诉讼，注意诉讼时效为3年。','1',NULL,NULL,NULL,'admin','2026-03-14 11:15:20','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1007,'15800158008',NULL,'吴女士','广东省惠州市惠城区','2026-03-14 11:30:00','2026-03-14 11:38:20',500,102,1,'咨询工伤认定及赔偿标准','建议先申请工伤认定，认定后再做劳动能力鉴定，根据伤残等级计算赔偿金额，包括医疗费、停工留薪期工资等。','1',NULL,NULL,NULL,'admin','2026-03-14 11:30:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1008,'15900159009',NULL,'郑先生','广东省江门市蓬江区','2026-03-14 13:30:00','2026-03-14 13:42:10',730,103,2,'咨询消费者权益保护，网购商品质量问题索赔','根据《消费者权益保护法》，经营者提供商品有欺诈行为的，消费者可要求三倍赔偿，最低500元。','1',NULL,NULL,NULL,'admin','2026-03-14 13:30:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1009,'18200182010',NULL,'冯女士','广东省广州市番禺区市桥','2026-03-14 13:50:30','2026-03-14 14:05:20',890,104,3,'咨询离婚后子女抚养权变更条件','根据《民法典》婚姻家庭编，具有抚养权一方不尽抚养义务或有虐待行为的，另一方可以请求变更抚养权。','1',NULL,NULL,NULL,'admin','2026-03-14 13:50:30','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1010,'13300133011',NULL,'陈先生','广东省深圳市福田区','2026-03-14 14:10:00','2026-03-14 14:20:30',630,106,5,'咨询专利侵权纠纷处理流程','建议先进行专利侵权比对分析，确认侵权后可发送律师函或直接起诉，可申请诉前禁令和证据保全。','1',NULL,NULL,NULL,'admin','2026-03-14 14:10:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1011,'15300153012',NULL,'林女士','广东省汕头市金平区','2026-03-14 14:30:00','2026-03-14 14:45:20',920,109,8,'咨询房产买卖合同纠纷，卖方违约','根据《民法典》合同编，卖方违约的，买方可以要求继续履行或解除合同并主张违约金和赔偿损失。','1',NULL,NULL,NULL,'admin','2026-03-14 14:30:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1012,'18100181013',NULL,'黄先生','广东省湛江市霞山区','2026-03-14 14:50:00','2026-03-14 15:00:15',615,102,1,'咨询劳动仲裁流程和时限','劳动争议申请仲裁的时效为1年，自知道或应当知道权利被侵害之日起计算，仲裁是劳动争议的前置程序。','1',NULL,NULL,NULL,'admin','2026-03-14 14:50:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1013,'15000150014',NULL,'何女士','广东省茂名市茂南区','2026-03-14 15:10:00','2026-03-14 15:22:30',750,103,2,'咨询婚前财产公证办理流程','双方携带身份证、户口本、房产证等到公证处办理，公证员会对财产归属进行确认并出具公证书。','1',NULL,NULL,NULL,'admin','2026-03-14 15:10:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1014,'17600176015',NULL,'罗先生','广东省肇庆市端州区','2026-03-14 15:30:00','2026-03-14 15:40:20',620,104,3,'咨询离婚诉讼中房产如何评估分割','诉讼中可申请法院委托有资质的评估机构进行房产评估，按评估价值进行分割。','1',NULL,NULL,NULL,'admin','2026-03-14 15:30:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1015,'13100131016',NULL,'梁女士','广东省广州市白云区','2026-03-14 15:50:00','2026-03-14 16:05:30',930,105,4,'咨询医疗事故损害赔偿','建议先申请医疗事故技术鉴定，确认是否构成医疗事故，再根据伤残等级计算赔偿金额。','1',NULL,NULL,NULL,'admin','2026-03-14 15:50:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1016,'15500155017',NULL,'宋先生','广东省清远市清城区','2026-03-14 16:10:00','2026-03-14 16:18:40',520,106,5,'咨询著作权侵权如何维权','收集侵权证据，可发律师函要求停止侵权并赔偿损失，也可向版权行政管理部门投诉。','1',NULL,NULL,NULL,'admin','2026-03-14 16:10:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1017,'18500185018',NULL,'谢女士','广东省广州市花都区','2026-03-14 16:25:00','2026-03-14 16:35:10',610,109,7,'咨询房屋拆迁补偿标准','拆迁补偿标准由当地政府制定，一般包括房屋价值补偿、搬迁安置费、停产停业损失补偿等。','1',NULL,NULL,NULL,'admin','2026-03-14 16:25:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1018,'17700177019',NULL,'韩先生','广东省韶关市武江区','2026-03-14 16:40:00','2026-03-14 16:52:20',740,102,1,'咨询经济补偿金计算方法','根据《劳动合同法》第四十七条，经济补偿按工作年限计算，每满一年支付一个月工资，月工资指离职前12个月的平均工资。','1',NULL,NULL,NULL,'admin','2026-03-14 16:40:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1019,'13200132020',NULL,'唐女士','广东省揭阳市榕城区','2026-03-14 17:00:00','2026-03-14 17:08:30',510,103,2,'咨询家庭暴力如何申请人身安全保护令','遭受家庭暴力或面临家庭暴力现实危险的，可向人民法院申请 人身安全保护令，需提供遭受家暴的证据。','1',NULL,NULL,NULL,'admin','2026-03-14 17:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1020,'15600156021',NULL,'许先生','广东省广州市增城区','2026-03-14 08:30:00','2026-03-14 08:38:45',525,102,1,'咨询未签劳动合同双倍工资赔偿','用人单位自用工之日起超过一个月未签订书面劳动合同的，应向劳动者每月支付二倍工资。','1',NULL,NULL,NULL,'admin','2026-03-14 08:30:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1021,'18300183022',NULL,'邓女士','广东省广州市从化区','2026-03-14 09:00:00',NULL,0,102,1,'咨询社保缴纳问题',NULL,'0',NULL,NULL,NULL,'admin','2026-03-14 09:00:00','',NULL,'排队中',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1022,'13700137023',NULL,'曹先生','广东省深圳市罗湖区','2026-03-14 09:05:00',NULL,0,103,2,'咨询抚养费标准',NULL,'0',NULL,NULL,NULL,'admin','2026-03-14 09:05:00','',NULL,'排队中',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1023,'18900189024',NULL,'彭女士','广东省广州市海珠区','2026-03-14 09:10:00','2026-03-14 09:15:00',300,104,3,'咨询婚前财产问题',NULL,'3',NULL,NULL,NULL,'admin','2026-03-14 09:10:00','',NULL,'未接',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1024,'13800138025',NULL,'潘先生','广东省佛山市南海区','2026-03-14 12:00:00',NULL,0,102,1,'咨询加班费计算标准',NULL,'0',NULL,NULL,NULL,'admin','2026-03-14 12:00:00','',NULL,'排队中',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1025,'13900139026',NULL,'蒋女士','广东省广州市荔湾区','2026-03-13 14:00:00','2026-03-13 14:15:30',930,102,1,'咨询违法解除劳动合同赔偿','违法解除劳动合同的，劳动者可以要求继续履行或支付赔偿金，赔偿金为经济补偿标准的二倍。','1',NULL,NULL,NULL,'admin','2026-03-13 14:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1026,'13600136027',NULL,'沈先生','广东省深圳市宝安区','2026-03-13 15:00:00','2026-03-13 15:12:00',720,103,2,'咨询婚内财产公证','婚内可以就夫妻财产约定进行公证，需双方自愿到场，携带身份证、结婚证和财产权属证明。','1',NULL,NULL,NULL,'admin','2026-03-13 15:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1027,'13700137028',NULL,'姚女士','广东省珠海市斗门区','2026-03-13 16:00:00','2026-03-13 16:10:20',620,104,3,'咨询离婚诉讼管辖法院','一般由被告住所地人民法院管辖，被告离开住所地超过一年的，由原告住所地人民法院管辖。','1',NULL,NULL,NULL,'admin','2026-03-13 16:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1028,'13500135029',NULL,'陆先生','广东省东莞市虎门镇','2026-03-12 10:00:00','2026-03-12 10:15:30',930,105,4,'咨询交通事故责任认定复核','对交通事故认定书不服的，可在收到认定书之日起3日内向上一级公安机关交通管理部门提出书面复核申请。','2',100,NULL,NULL,'admin','2026-03-12 10:00:00','',NULL,'已转接',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL),(1029,'18800188030',NULL,'袁女士','广东省中山市小榄镇','2026-03-12 11:00:00','2026-03-12 11:08:30',510,106,5,'咨询商业秘密保护','建议与员工签订保密协议和竞业限制条款，发现泄密后及时收集证据，追究违约责任。','1',NULL,NULL,NULL,'admin','2026-03-12 11:00:00','',NULL,'',NULL,NULL,0,0,NULL,NULL,'0',NULL,NULL);
/*!40000 ALTER TABLE `ai_call_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_record_archive`
--

DROP TABLE IF EXISTS `ai_call_record_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_record_archive` (
  `record_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `caller_number` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '来电号码（SM4-GCM 密文 encp: 前缀，G1-b2 镜像）',
  `caller_number_index` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电号码盲索引（G1-b2 镜像）',
  `caller_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电人姓名',
  `caller_address` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来电人地址',
  `call_time` datetime NOT NULL COMMENT '来电时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int DEFAULT '0' COMMENT '通话时长(秒)',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席ID（来话在坐席分配前先落单，必须可空；对应 README 手工 ALTER 口径）',
  `category_id` bigint DEFAULT NULL COMMENT '咨询分类ID',
  `content` text COLLATE utf8mb4_general_ci COMMENT '咨询内容',
  `answer` text COLLATE utf8mb4_general_ci COMMENT '解答内容',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0接通中 1已完成 2已转接 3未接）',
  `transfer_id` bigint DEFAULT NULL COMMENT '转接记录ID',
  `ticket_id` bigint DEFAULT NULL COMMENT '工单ID',
  `call_uuid` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '通话通道UUID(FreeSWITCH Unique-ID)',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（分区键）',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `record_file` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '录音文件路径',
  `recording_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '录音访问URL',
  `record_duration` int DEFAULT '0' COMMENT '录音时长(秒)',
  `asr_status` tinyint DEFAULT '0' COMMENT 'ASR转写状态 0待转写 1转写中 2已完成 3失败',
  `transcript` text COLLATE utf8mb4_general_ci COMMENT 'ASR转写文本',
  `ai_summary` mediumtext COLLATE utf8mb4_general_ci COMMENT 'AI通话小结（案情摘要/法律意见/待办/回访建议）',
  `ai_summary_status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT 'AI小结状态 0待生成 1生成中 2已生成 3失败',
  `ai_summary_time` datetime DEFAULT NULL COMMENT 'AI小结生成时间',
  `ai_summary_fail_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'AI小结失败原因',
  PRIMARY KEY (`record_id`,`create_time`),
  KEY `idx_ai_call_record_call_uuid` (`call_uuid`),
  KEY `idx_ai_call_record_ct` (`create_time`),
  KEY `idx_archive_number_index` (`caller_number_index`),
  KEY `idx_archive_call_time` (`call_time`),
  KEY `idx_archive_agent_call_time` (`agent_id`,`call_time`),
  KEY `idx_archive_status_call_time` (`status`,`call_time`),
  KEY `idx_archive_category_call_time` (`category_id`,`call_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='来电记录表'
/*!50100 PARTITION BY RANGE (to_days(`create_time`))
(PARTITION p202501 VALUES LESS THAN (739648) ENGINE = InnoDB,
 PARTITION p202502 VALUES LESS THAN (739676) ENGINE = InnoDB,
 PARTITION p202503 VALUES LESS THAN (739707) ENGINE = InnoDB,
 PARTITION p202504 VALUES LESS THAN (739737) ENGINE = InnoDB,
 PARTITION p202505 VALUES LESS THAN (739768) ENGINE = InnoDB,
 PARTITION p202506 VALUES LESS THAN (739798) ENGINE = InnoDB,
 PARTITION p202507 VALUES LESS THAN (739829) ENGINE = InnoDB,
 PARTITION p202508 VALUES LESS THAN (739860) ENGINE = InnoDB,
 PARTITION p202509 VALUES LESS THAN (739890) ENGINE = InnoDB,
 PARTITION p202510 VALUES LESS THAN (739921) ENGINE = InnoDB,
 PARTITION p202511 VALUES LESS THAN (739951) ENGINE = InnoDB,
 PARTITION p202512 VALUES LESS THAN (739982) ENGINE = InnoDB,
 PARTITION p202601 VALUES LESS THAN (740013) ENGINE = InnoDB,
 PARTITION p202602 VALUES LESS THAN (740041) ENGINE = InnoDB,
 PARTITION p202603 VALUES LESS THAN (740072) ENGINE = InnoDB,
 PARTITION p202604 VALUES LESS THAN (740102) ENGINE = InnoDB,
 PARTITION p202605 VALUES LESS THAN (740133) ENGINE = InnoDB,
 PARTITION p202606 VALUES LESS THAN (740163) ENGINE = InnoDB,
 PARTITION p202607 VALUES LESS THAN (740194) ENGINE = InnoDB,
 PARTITION p202608 VALUES LESS THAN (740225) ENGINE = InnoDB,
 PARTITION p202609 VALUES LESS THAN (740255) ENGINE = InnoDB,
 PARTITION p202610 VALUES LESS THAN (740286) ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN (740316) ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN (740347) ENGINE = InnoDB,
 PARTITION pmax VALUES LESS THAN MAXVALUE ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_record_archive`
--

LOCK TABLES `ai_call_record_archive` WRITE;
/*!40000 ALTER TABLE `ai_call_record_archive` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_record_archive` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_risk_record`
--

DROP TABLE IF EXISTS `ai_call_risk_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_risk_record` (
  `risk_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `caller_number_index` varchar(64) NOT NULL COMMENT '主叫号码盲索引（HMAC-SM3 128位hex，确定性）',
  `masked_number` varchar(32) DEFAULT NULL COMMENT '脱敏号码（138****5678，展示用）',
  `window_hours` int NOT NULL DEFAULT '72' COMMENT '评估窗口（小时）',
  `call_count` int NOT NULL DEFAULT '0' COMMENT '窗口内来电次数',
  `short_count` int NOT NULL DEFAULT '0' COMMENT '超短通话次数（≤短通话阈值且接通）',
  `night_count` int NOT NULL DEFAULT '0' COMMENT '夜间来电次数（22:00~次日7:00）',
  `missed_count` int NOT NULL DEFAULT '0' COMMENT '未接来电次数',
  `risk_score` int NOT NULL DEFAULT '0' COMMENT '风险评分 0~100（频次40+超短20+夜间20+未接20）',
  `score_detail` varchar(200) DEFAULT NULL COMMENT '评分构成说明（频次x+超短y+夜间z+未接w）',
  `risk_level` varchar(8) NOT NULL DEFAULT 'LOW' COMMENT '风险等级 LOW/MEDIUM/HIGH',
  `review_status` char(1) NOT NULL DEFAULT '0' COMMENT '复核状态 0待复核 1已确认 2已忽略',
  `suppress_id` bigint DEFAULT NULL COMMENT '自动生成的置底规则ID（null=未生成）',
  `review_by` varchar(64) DEFAULT NULL COMMENT '复核人',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '复核说明',
  `create_time` datetime DEFAULT NULL COMMENT '评估时间',
  PRIMARY KEY (`risk_id`),
  KEY `idx_index_review` (`caller_number_index`,`review_status`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='呼叫行为风控评估记录表（P2-15，班组长复核留痕）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_risk_record`
--

LOCK TABLES `ai_call_risk_record` WRITE;
/*!40000 ALTER TABLE `ai_call_risk_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_risk_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_ticket`
--

DROP TABLE IF EXISTS `ai_call_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_ticket` (
  `ticket_id` bigint NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `ticket_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工单号',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '工单标题',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '工单内容',
  `priority` char(1) COLLATE utf8mb4_general_ci DEFAULT '2' COMMENT '优先级（1紧急 2普通 3低）',
  `due_time` datetime DEFAULT NULL COMMENT 'SLA截止时间',
  `overtime_flag` tinyint DEFAULT '0' COMMENT '是否超时 0否 1是',
  `remind_time` datetime DEFAULT NULL COMMENT '最后提醒时间',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0待处理 1处理中 2已完成 3已归档）',
  `assign_user_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `assign_user_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '处理人姓名',
  `process_content` text COLLATE utf8mb4_general_ci COMMENT '处理内容',
  `content_embedding` longblob COMMENT '标题+内容语义向量（小端float32连续字节，仅办结工单）',
  `embedding_dim` int NOT NULL DEFAULT '0' COMMENT '向量维度（0=未向量化）',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `close_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `external_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345',
  `external_org_id` bigint DEFAULT NULL COMMENT '协同机构ID',
  `external_ticket_no` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部工单号',
  `external_status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '外部状态 PENDING/ACCEPTED/PROCESSING/DONE/REJECTED/FAILED',
  `external_update_time` datetime DEFAULT NULL COMMENT '外部状态最近回写时间',
  `transfer_time` datetime DEFAULT NULL COMMENT '最近转出/接收时间',
  `direction` varchar(8) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '协同方向 OUT转出 IN转入',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`ticket_id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  KEY `idx_ticket_embedding_dim` (`embedding_dim`),
  KEY `idx_ticket_create_time` (`create_time`),
  KEY `idx_ticket_status_create` (`status`,`create_time`),
  KEY `idx_ticket_assign_create` (`assign_user_id`,`create_time`),
  KEY `idx_ticket_status_due` (`status`,`due_time`),
  KEY `idx_ticket_record_id` (`record_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10015 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_ticket`
--

LOCK TABLES `ai_call_ticket` WRITE;
/*!40000 ALTER TABLE `ai_call_ticket` DISABLE KEYS */;
INSERT INTO `ai_call_ticket` VALUES (10000,'TKT20260314091530A1B2C3',1000,'劳动合同解除咨询','咨询劳动合同解除相关法律问题，公司拖欠工资3个月','2',NULL,0,NULL,'2',102,'张律师','已建议劳动者收集证据，可申请劳动仲裁要求支付拖欠工资及经济补偿。',NULL,0,'2026-03-14 09:25:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 09:15:30','',NULL,''),(10001,'TKT20260314093020D4E5F6',1001,'离婚财产分割咨询','咨询离婚财产分割问题，婚前房产婚后共同还贷如何分配','1',NULL,0,NULL,'1',103,'李律师',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 09:30:20','',NULL,''),(10002,'TKT20260314100510G7H8I9',1002,'继承纠纷咨询','咨询继承纠纷，父母去世后房产继承分配问题','2',NULL,0,NULL,'2',104,'王律师','已解答法定继承顺序和分配原则，建议协商解决。',NULL,0,'2026-03-14 10:12:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 10:05:10','',NULL,''),(10003,'TKT20260314102000J1K2L3',1003,'交通事故赔偿咨询','咨询交通事故人身损害赔偿标准','2',NULL,0,NULL,'2',105,'赵律师','已告知赔偿项目和计算标准，建议做伤残鉴定。',NULL,0,'2026-03-14 10:28:00','2026-03-14 10:30:20',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 10:20:00','',NULL,''),(10004,'TKT20260314104530M4N5O6',1004,'商标侵权咨询','咨询商标被侵权如何维权','3',NULL,0,NULL,'0',NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 10:45:30','',NULL,''),(10005,'TKT20260314110000P7Q8R9',1005,'租房押金纠纷咨询','咨询房屋租赁合同纠纷，房东不退押金','2',NULL,0,NULL,'1',107,'孙律师',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 11:00:00','',NULL,''),(10006,'TKT20260314111520S1T2U3',1006,'民间借贷纠纷咨询','咨询民间借贷纠纷，借款人不还款','1',NULL,0,NULL,'2',109,'周律师','已建议收集借条、转账记录等证据，可向法院起诉。',NULL,0,'2026-03-14 11:25:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 11:15:20','',NULL,''),(10007,'TKT20260314113000V4W5X6',1007,'工伤认定咨询','咨询工伤认定及赔偿标准','1',NULL,0,NULL,'1',102,'张律师',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 11:30:00','',NULL,''),(10008,'TKT20260314133000Y7Z8A9',1008,'消费者权益咨询','咨询消费者权益保护，网购商品质量问题索赔','3',NULL,0,NULL,'2',103,'李律师','已告知消费者三倍赔偿规定，建议先与商家协商。',NULL,0,'2026-03-14 13:38:00','2026-03-14 13:42:10',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 13:30:00','',NULL,''),(10009,'TKT20260314135030B1C2D3',1009,'抚养权变更咨询','咨询离婚后子女抚养权变更条件','2',NULL,0,NULL,'3',104,'王律师','已归档：已告知抚养权变更条件和法律程序。',NULL,0,'2026-03-14 14:00:00','2026-03-14 14:05:20',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 13:50:30','admin','2026-03-14 14:05:20',''),(10010,'TKT20260314141000E4F5G6',1010,'专利侵权咨询','咨询专利侵权纠纷处理流程','2',NULL,0,NULL,'0',NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 14:10:00','',NULL,''),(10011,'TKT20260314143000H7I8J9',1011,'房产买卖违约咨询','咨询房产买卖合同纠纷，卖方违约','1',NULL,0,NULL,'1',109,'周律师',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 14:30:00','',NULL,''),(10012,'TKT20260314145000K1L2M3',1012,'劳动仲裁咨询','咨询劳动仲裁流程和时限','2',NULL,0,NULL,'2',102,'张律师','已告知劳动仲裁时效和流程。',NULL,0,'2026-03-14 14:58:00',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 14:50:00','',NULL,''),(10013,'TKT20260314151000N4O5P6',1013,'婚前财产公证咨询','咨询婚前财产公证办理流程','3',NULL,0,NULL,'3',103,'李律师','已归档。',NULL,0,'2026-03-14 15:20:00','2026-03-14 15:22:30',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 15:10:00','admin','2026-03-14 15:22:30',''),(10014,'TKT20260314153000Q7R8S9',1014,'离婚房产评估咨询','咨询离婚诉讼中房产如何评估分割','2',NULL,0,NULL,'1',104,'王律师',NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'admin','2026-03-14 15:30:00','',NULL,'');
/*!40000 ALTER TABLE `ai_call_ticket` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_transfer`
--

DROP TABLE IF EXISTS `ai_call_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_transfer` (
  `transfer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '转接ID',
  `record_id` bigint NOT NULL COMMENT '来电记录ID',
  `from_agent_id` bigint NOT NULL COMMENT '转出坐席ID',
  `from_agent_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '转出坐席名称',
  `to_agent_id` bigint NOT NULL COMMENT '转入坐席ID',
  `to_agent_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '转入坐席名称',
  `transfer_time` datetime NOT NULL COMMENT '转接时间',
  `reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '转接原因',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`transfer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=103 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='转接记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_transfer`
--

LOCK TABLES `ai_call_transfer` WRITE;
/*!40000 ALTER TABLE `ai_call_transfer` DISABLE KEYS */;
INSERT INTO `ai_call_transfer` VALUES (100,1028,102,'张律师',105,'赵律师','2026-03-12 10:10:00','咨询人问题涉及交通事故，转接至刑事辩护坐席','admin','2026-03-12 10:10:00',''),(101,1003,104,'王律师',105,'赵律师','2026-03-14 10:25:00','咨询人问题涉及交通事故赔偿，转接至专业坐席','admin','2026-03-14 10:25:00',''),(102,1015,104,'王律师',105,'赵律师','2026-03-14 15:55:00','医疗事故问题转接至专业坐席','admin','2026-03-14 15:55:00','');
/*!40000 ALTER TABLE `ai_call_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_call_trunk`
--

DROP TABLE IF EXISTS `ai_call_trunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_trunk` (
  `trunk_id` bigint NOT NULL AUTO_INCREMENT COMMENT '线路ID',
  `trunk_code` varchar(32) NOT NULL COMMENT '线路编码(唯一)',
  `trunk_name` varchar(64) NOT NULL COMMENT '线路名称',
  `carrier` char(2) DEFAULT '00' COMMENT '运营商 CM/CU/CT/CB/VI/00',
  `line_type` char(1) DEFAULT '1' COMMENT '线路类型 1=SIP 2=PSTN 3=SIP中继网关 4=WebRTC',
  `vendor` varchar(32) DEFAULT NULL COMMENT '网关厂商/中间件',
  `protocol` varchar(16) DEFAULT 'SIP' COMMENT '信令协议 SIP/SS7/H323/HTTP',
  `gateway_host` varchar(64) DEFAULT NULL COMMENT '网关/SBC 地址',
  `gateway_port` int DEFAULT '5060' COMMENT '网关端口',
  `transport` varchar(8) DEFAULT 'UDP' COMMENT '传输层 UDP/TCP/TLS',
  `api_base_url` varchar(255) DEFAULT NULL COMMENT 'CTI中间件 HTTP API 基地址',
  `auth_user` varchar(64) DEFAULT NULL COMMENT '注册/鉴权用户名',
  `auth_password` varchar(128) DEFAULT NULL COMMENT '注册/鉴权密码',
  `realm` varchar(64) DEFAULT NULL COMMENT 'SIP realm',
  `caller_prefix` varchar(16) DEFAULT NULL COMMENT '外呼主叫前缀(出局字冠)',
  `caller_display` varchar(32) DEFAULT NULL COMMENT '主叫显号',
  `strip_digits` int DEFAULT '0' COMMENT '被叫号码去除前N位',
  `add_prefix` varchar(16) DEFAULT NULL COMMENT '被叫号码添加前缀',
  `max_concurrent` int DEFAULT '30' COMMENT '最大并发呼叫数',
  `current_concurrent` int DEFAULT '0' COMMENT '当前并发数',
  `cps_limit` int DEFAULT '5' COMMENT '每秒最大呼叫数(CPS)',
  `priority` int DEFAULT '10' COMMENT '优先级(越小越优先)',
  `weight` int DEFAULT '100' COMMENT '负载权重',
  `is_backup` char(1) DEFAULT '0' COMMENT '是否备份线路 0=主用 1=备用',
  `health_status` char(1) DEFAULT '0' COMMENT '健康状态 0=未知 1=正常 2=亚健康 3=故障 4=熔断',
  `enable_flag` char(1) DEFAULT '1' COMMENT '启用状态 0=停用 1=启用',
  `last_check_time` datetime DEFAULT NULL COMMENT '最后巡检时间',
  `last_success_time` datetime DEFAULT NULL COMMENT '最后成功时间',
  `last_fail_time` datetime DEFAULT NULL COMMENT '最后失败时间',
  `fail_count` int DEFAULT '0' COMMENT '连续失败次数',
  `circuit_open_time` datetime DEFAULT NULL COMMENT '熔断开启时间',
  `connect_rate` decimal(5,2) DEFAULT '0.00' COMMENT '接通率(%)',
  `asr` decimal(5,2) DEFAULT '0.00' COMMENT 'ASR 应答率(%)',
  `acd` int DEFAULT '0' COMMENT 'ACD 平均通话时长(秒)',
  `mos` decimal(4,2) DEFAULT '0.00' COMMENT 'MOS 语音质量评分',
  `total_calls` bigint DEFAULT '0' COMMENT '累计呼叫次数',
  `success_calls` bigint DEFAULT '0' COMMENT '累计接通次数',
  `total_duration` bigint DEFAULT '0' COMMENT '累计通话时长(秒)',
  `alarm_connect_rate` decimal(5,2) DEFAULT '80.00' COMMENT '接通率告警阈值(%)',
  `alarm_fail_times` int DEFAULT '5' COMMENT '连续失败告警/熔断阈值',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志 0=存在 2=删除',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`trunk_id`),
  UNIQUE KEY `uk_trunk_code` (`trunk_code`),
  KEY `idx_trunk_route` (`del_flag`,`enable_flag`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运营商中继线路';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_call_trunk`
--

LOCK TABLES `ai_call_trunk` WRITE;
/*!40000 ALTER TABLE `ai_call_trunk` DISABLE KEYS */;
INSERT INTO `ai_call_trunk` VALUES (1,'FS-LOCAL','FreeSWITCH 本地中继','00','3','FREESWITCH','SIP','freeswitch',5060,'UDP',NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,30,0,5,1,100,'0','1','1',NULL,NULL,NULL,0,NULL,0.00,0.00,0,0.00,0,0,0,80.00,5,'0','admin','2026-11-16 22:43:46','',NULL,'容器内 FreeSWITCH 网关；deploy/docker/mysql/99-container-trunk-fix.sql 会再次修正 gateway_host'),(2,'HTTP-API','CTI HTTP API 中继','00','1','HTTP_API','HTTP','127.0.0.1',80,'TCP',NULL,NULL,NULL,NULL,NULL,NULL,0,NULL,20,0,5,20,50,'1','0','1',NULL,NULL,NULL,0,NULL,0.00,0.00,0,0.00,0,0,0,80.00,5,'0','admin','2026-11-16 22:43:46','',NULL,'未接入 CTI 中间件时默认禁用；容器部署由 99 脚本置 enable_flag=0');
/*!40000 ALTER TABLE `ai_call_trunk` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_callback`
--

DROP TABLE IF EXISTS `ai_callback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_callback` (
  `callback_id` bigint NOT NULL AUTO_INCREMENT COMMENT '回访ID',
  `ledger_id` bigint DEFAULT NULL COMMENT '关联台账ID',
  `caller_number` varchar(30) DEFAULT NULL COMMENT '来电号码',
  `caller_name` varchar(50) DEFAULT NULL COMMENT '来电人姓名',
  `visit_time` datetime DEFAULT NULL COMMENT '回访时间',
  `visit_by` varchar(64) DEFAULT NULL COMMENT '回访人',
  `visit_by_id` bigint DEFAULT NULL COMMENT '回访人用户ID',
  `satisfaction` char(1) DEFAULT NULL COMMENT '满意度（1非常满意 2满意 3一般 4不满意）',
  `visit_opinion` varchar(1000) DEFAULT NULL COMMENT '回访意见',
  `visit_result` varchar(500) DEFAULT NULL COMMENT '回访结果',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待回访 1已完成 2无法联系）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`callback_id`),
  KEY `idx_ai_callback_number` (`caller_number`),
  KEY `idx_ai_callback_time` (`visit_time`),
  KEY `idx_ai_callback_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户回访表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_callback`
--

LOCK TABLES `ai_callback` WRITE;
/*!40000 ALTER TABLE `ai_callback` DISABLE KEYS */;
INSERT INTO `ai_callback` VALUES (1,1,'13800138001','张丽华','2026-08-01 09:30:00','admin',NULL,'1','律师解答很专业，态度很好，非常满意','问题已解决，客户满意','1','admin','2026-08-01 09:30:00','',NULL,'回访完成'),(2,2,'13900139002','王志强','2026-08-01 10:15:00','admin',NULL,'2','解答比较详细，但希望能更快回复','问题基本解决','1','admin','2026-08-01 10:15:00','',NULL,NULL),(3,3,'13700137003','李秀英','2026-08-01 11:00:00','admin',NULL,'3','一般，希望律师能更耐心一些','需进一步跟进','1','admin','2026-08-01 11:00:00','',NULL,NULL),(4,4,'13600136004','陈国庆','2026-08-01 13:45:00','admin',NULL,'1','非常感谢律师的耐心解答','问题已解决','1','admin','2026-08-01 13:45:00','',NULL,NULL),(5,5,'13500135005','赵美玲','2026-08-01 14:20:00','admin',NULL,'2','服务不错，建议增加视频咨询','问题解决，采纳建议','1','admin','2026-08-01 14:20:00','',NULL,NULL),(6,6,'13800138006','孙建华',NULL,NULL,NULL,NULL,NULL,NULL,'0','admin','2026-08-01 15:00:00','',NULL,'待回访'),(7,7,'13900139007','周晓东','2026-07-31 16:00:00','admin',NULL,'1','律师非常专业，推荐','问题已解决','1','admin','2026-07-31 16:00:00','',NULL,NULL),(8,8,'13700137008','吴桂芳','2026-07-31 17:00:00','admin',NULL,'4','回复太慢，体验不好','需改进响应速度','1','admin','2026-07-31 17:00:00','',NULL,'投诉'),(9,9,'13600136009','郑伟明','2026-07-30 10:00:00','admin',NULL,'2','解答清晰，谢谢','问题已解决','1','admin','2026-07-30 10:00:00','',NULL,NULL),(10,10,'13500135010','黄丽萍','2026-07-30 14:30:00','admin',NULL,'3','一般，希望更专业','需跟进','1','admin','2026-07-30 14:30:00','',NULL,NULL);
/*!40000 ALTER TABLE `ai_callback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_caller_profile`
--

DROP TABLE IF EXISTS `ai_caller_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_caller_profile` (
  `profile_id` bigint NOT NULL AUTO_INCREMENT COMMENT '档案ID',
  `caller_number` varchar(128) NOT NULL COMMENT '来电号码（SM4-GCM 密文 encp: 前缀，G1-b）',
  `caller_number_index` varchar(32) DEFAULT NULL COMMENT '来电号码盲索引（HMAC-SM3 截断 128 位 hex，G1-b）',
  `caller_name` varchar(50) DEFAULT NULL COMMENT '来电人姓名',
  `caller_gender` char(1) DEFAULT '2' COMMENT '性别（0男 1女 2未知）',
  `caller_age` int DEFAULT NULL COMMENT '年龄',
  `caller_id_card` varchar(128) DEFAULT NULL COMMENT '身份证号（SM4-GCM 密文 encp: 前缀，G1-b）',
  `caller_id_card_index` varchar(32) DEFAULT NULL COMMENT '身份证号盲索引（HMAC-SM3 截断 128 位 hex，G1-b）',
  `caller_address` varchar(255) DEFAULT NULL COMMENT '联系地址',
  `customer_level` varchar(50) DEFAULT NULL COMMENT '客户等级',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签（逗号分隔）',
  `intent_prediction` varchar(100) DEFAULT NULL COMMENT '意图预测',
  `intent_confidence` int DEFAULT '0' COMMENT '意图置信度',
  `consult_preference` varchar(100) DEFAULT NULL COMMENT '咨询偏好',
  `language_preference` varchar(10) DEFAULT 'zh-CN' COMMENT '语种偏好 zh-CN普通话 yue-CN粤语',
  `care_mode` tinyint DEFAULT '0' COMMENT '关怀模式偏好 0标准 1关怀',
  `high_freq_problem` varchar(100) DEFAULT NULL COMMENT '高频问题',
  `freq_mention_count` int DEFAULT '0' COMMENT '高频提及次数',
  `risk_level` char(1) DEFAULT '0' COMMENT '风险等级（0低 1中 2高）',
  `emotion_status` varchar(50) DEFAULT NULL COMMENT '情绪状态',
  `emotion_warning` varchar(500) DEFAULT NULL COMMENT '情绪预警',
  `last_call_time` datetime DEFAULT NULL COMMENT '最后来电时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`profile_id`),
  UNIQUE KEY `uk_ai_caller_profile_number_index` (`caller_number_index`),
  KEY `idx_ai_caller_profile_id_card_index` (`caller_id_card_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='来电人档案表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_caller_profile`
--

LOCK TABLES `ai_caller_profile` WRITE;
/*!40000 ALTER TABLE `ai_caller_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_caller_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_channel_identity`
--

DROP TABLE IF EXISTS `ai_channel_identity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_channel_identity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `profile_id` bigint NOT NULL COMMENT '来电档案ID（ai_caller_profile）',
  `channel_type` varchar(20) NOT NULL COMMENT '渠道 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB',
  `channel_uid` varchar(200) NOT NULL COMMENT '渠道身份标识 openid/unionid/账号（加密存储随P3-G1）',
  `channel_nickname` varchar(100) DEFAULT NULL COMMENT '渠道昵称',
  `bind_status` char(1) NOT NULL DEFAULT '0' COMMENT '绑定状态 0已绑定 1已解绑',
  `bind_confirm_time` datetime DEFAULT NULL COMMENT '绑定二次确认时间',
  `unbind_time` datetime DEFAULT NULL COMMENT '解绑时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_uid` (`channel_type`,`channel_uid`),
  KEY `idx_profile_id` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='多渠道身份绑定（F6）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_channel_identity`
--

LOCK TABLES `ai_channel_identity` WRITE;
/*!40000 ALTER TABLE `ai_channel_identity` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_channel_identity` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_message`
--

DROP TABLE IF EXISTS `ai_chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `sender_type` char(1) DEFAULT '1' COMMENT '发送者类型（1客户 2客服 3系统）',
  `sender_name` varchar(64) DEFAULT NULL COMMENT '发送人名称',
  `content` text COMMENT '消息内容',
  `msg_type` char(1) DEFAULT '1' COMMENT '消息类型（1文字 2图片 3文件）',
  `attachment_url` varchar(255) DEFAULT NULL COMMENT '附件URL',
  `is_read` char(1) DEFAULT '0' COMMENT '是否已读（0未读 1已读）',
  `send_time` datetime DEFAULT NULL COMMENT '发送时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`message_id`),
  KEY `idx_ai_chat_msg_session` (`session_id`),
  KEY `idx_ai_chat_msg_send_time` (`send_time`),
  KEY `idx_ai_chat_msg_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='聊天消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_message`
--

LOCK TABLES `ai_chat_message` WRITE;
/*!40000 ALTER TABLE `ai_chat_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_chat_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_chat_session`
--

DROP TABLE IF EXISTS `ai_chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `session_no` varchar(64) DEFAULT NULL COMMENT '会话编号',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '客户姓名',
  `customer_phone` varchar(20) DEFAULT NULL COMMENT '客户电话',
  `customer_avatar` varchar(255) DEFAULT NULL COMMENT '客户头像URL',
  `user_id` bigint DEFAULT NULL COMMENT '受理人ID',
  `assignee` varchar(64) DEFAULT NULL COMMENT '受理人',
  `channel` char(1) DEFAULT '1' COMMENT '渠道（1图文 2视频辅助）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0进行中 1已结束 2转人工）',
  `last_message` varchar(500) DEFAULT NULL COMMENT '最后一条消息摘要',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `unread_count` int DEFAULT '0' COMMENT '未读消息数',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`session_id`),
  KEY `idx_ai_chat_session_no` (`session_no`),
  KEY `idx_ai_chat_session_assignee` (`assignee`),
  KEY `idx_ai_chat_session_status` (`status`),
  KEY `idx_ai_chat_session_time` (`last_message_time`),
  KEY `idx_ai_chat_session_phone` (`customer_phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图文会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_chat_session`
--

LOCK TABLES `ai_chat_session` WRITE;
/*!40000 ALTER TABLE `ai_chat_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_chat_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_consultation_category`
--

DROP TABLE IF EXISTS `ai_consultation_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_consultation_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类ID',
  `ancestors` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=117 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='咨询分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_consultation_category`
--

LOCK TABLES `ai_consultation_category` WRITE;
/*!40000 ALTER TABLE `ai_consultation_category` DISABLE KEYS */;
INSERT INTO `ai_consultation_category` VALUES (100,'法律咨询',0,'0',0,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(101,'民事纠纷',100,'0,100',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(102,'刑事辩护',100,'0,100',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(103,'行政法',100,'0,100',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(104,'经济法',100,'0,100',4,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(105,'婚姻家庭',101,'0,100,101',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(106,'劳动纠纷',101,'0,100,101',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(107,'合同纠纷',101,'0,100,101',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(108,'侵权责任',101,'0,100,101',4,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(109,'盗窃罪',102,'0,100,102',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(110,'故意伤害罪',102,'0,100,102',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(111,'诈骗罪',102,'0,100,102',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(112,'行政复议',103,'0,100,103',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(113,'行政诉讼',103,'0,100,103',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(114,'公司法',104,'0,100,104',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(115,'合同法',104,'0,100,104',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL),(116,'知识产权法',104,'0,100,104',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:18','',NULL);
/*!40000 ALTER TABLE `ai_consultation_category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_copilot_feedback`
--

DROP TABLE IF EXISTS `ai_copilot_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_copilot_feedback` (
  `feedback_id` bigint NOT NULL AUTO_INCREMENT COMMENT '埋点ID',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '语音会话ID（非数字兜底）',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席用户ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '坐席姓名',
  `dept_id` bigint DEFAULT NULL COMMENT '班组/部门ID',
  `suggestion_type` varchar(16) NOT NULL COMMENT '建议类型 ELEMENT/LAW/TICKET',
  `suggestion_ref` varchar(64) DEFAULT NULL COMMENT '建议对象引用 chunkId/ticketId/element',
  `action` varchar(16) NOT NULL COMMENT '行为 ADOPT/MODIFY/IGNORE',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间',
  PRIMARY KEY (`feedback_id`),
  KEY `idx_time_agent` (`create_time`,`agent_id`),
  KEY `idx_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='P3-F4 Copilot建议采纳行为埋点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_copilot_feedback`
--

LOCK TABLES `ai_copilot_feedback` WRITE;
/*!40000 ALTER TABLE `ai_copilot_feedback` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_copilot_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_external_org`
--

DROP TABLE IF EXISTS `ai_external_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_external_org` (
  `org_id` bigint NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  `org_name` varchar(200) NOT NULL COMMENT '机构名称',
  `external_type` varchar(20) NOT NULL COMMENT '条线类型 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345',
  `province` varchar(50) DEFAULT NULL COMMENT '省',
  `city` varchar(50) DEFAULT NULL COMMENT '市',
  `district` varchar(50) DEFAULT NULL COMMENT '区县',
  `address` varchar(300) DEFAULT NULL COMMENT '机构地址',
  `contact_phone` varchar(30) DEFAULT NULL COMMENT '联系电话',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `access_mode` varchar(10) NOT NULL DEFAULT 'MANUAL' COMMENT '对接方式 API接口 FILE文件 MANUAL人工台账',
  `api_url` varchar(500) DEFAULT NULL COMMENT '协同接口地址（API方式）',
  `app_id` varchar(64) DEFAULT NULL COMMENT '对接AppId（API方式）',
  `app_secret` varchar(200) DEFAULT NULL COMMENT '对接密钥（加密存储，P3-G1同步加密）',
  `service_hours` varchar(100) DEFAULT NULL COMMENT '服务时间',
  `apply_materials` varchar(1000) DEFAULT NULL COMMENT '申请材料清单（转介指引展示）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`org_id`),
  KEY `idx_org_type_region` (`external_type`,`province`,`city`,`district`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协同外部机构台账（F3）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_external_org`
--

LOCK TABLES `ai_external_org` WRITE;
/*!40000 ALTER TABLE `ai_external_org` DISABLE KEYS */;
INSERT INTO `ai_external_org` VALUES (1,'广东省法律援助局','LEGAL_AID','广东省','广州市','越秀区','广州市越秀区','020-12348',NULL,'MANUAL',NULL,NULL,NULL,'工作日 9:00-12:00 14:00-18:00','身份证/经济困难证明/案件材料','0','admin','2026-11-16 22:43:32','',NULL,'示例机构请按实际维护'),(2,'广州市法律援助处','LEGAL_AID','广东省','广州市','越秀区','广州市越秀区','020-12348',NULL,'MANUAL',NULL,NULL,NULL,'工作日 9:00-12:00 14:00-18:00','身份证/经济困难证明/案件材料','0','admin','2026-11-16 22:43:32','',NULL,'示例机构请按实际维护'),(3,'广州市政务服务便民热线','HOTLINE_12345','广东省','广州市',NULL,NULL,'12345',NULL,'MANUAL',NULL,NULL,NULL,'7×24小时',NULL,'0','admin','2026-11-16 22:43:32','',NULL,'12345双号并行协同（国办发〔2020〕53号）');
/*!40000 ALTER TABLE `ai_external_org` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_forecast_calendar`
--

DROP TABLE IF EXISTS `ai_forecast_calendar`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_forecast_calendar` (
  `calendar_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日历ID',
  `calendar_date` date NOT NULL COMMENT '日期',
  `day_type` varchar(16) NOT NULL DEFAULT 'HOLIDAY' COMMENT '日型 HOLIDAY假日 EVENT事件(活动/舆情/政策发布) WORKDAY调休补班',
  `day_name` varchar(100) DEFAULT NULL COMMENT '名称（如"国庆节""普法宣传周"）',
  `coefficient` decimal(5,2) NOT NULL DEFAULT '1.00' COMMENT '话务系数（EVENT 生效；空/1=不调整）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`calendar_id`),
  UNIQUE KEY `uk_calendar_date` (`calendar_date`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='话务预测节假日/事件日历表（P1-9）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_forecast_calendar`
--

LOCK TABLES `ai_forecast_calendar` WRITE;
/*!40000 ALTER TABLE `ai_forecast_calendar` DISABLE KEYS */;
INSERT INTO `ai_forecast_calendar` VALUES (1,'2026-10-01','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期（示例，按当地话务规律校准）'),(2,'2026-10-02','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期'),(3,'2026-10-03','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期'),(4,'2026-10-04','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期'),(5,'2026-10-05','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期'),(6,'2026-10-06','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期'),(7,'2026-10-07','HOLIDAY','国庆节',1.00,'0','system','2026-11-16 22:43:42','',NULL,'国庆假期');
/*!40000 ALTER TABLE `ai_forecast_calendar` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_forecast_plan`
--

DROP TABLE IF EXISTS `ai_forecast_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_forecast_plan` (
  `plan_id` bigint NOT NULL AUTO_INCREMENT COMMENT '计划ID',
  `begin_date` date NOT NULL COMMENT '预测起始日',
  `end_date` date NOT NULL COMMENT '预测截止日',
  `total_volume` int NOT NULL DEFAULT '0' COMMENT '预测总进线量',
  `peak_hour` varchar(16) DEFAULT NULL COMMENT '峰值时段（如 10:00）',
  `peak_agents` int NOT NULL DEFAULT '0' COMMENT '峰值所需坐席数',
  `payload_json` longtext COMMENT '逐日逐时预测与排班建议快照 JSON',
  `confirmed_by` varchar(64) DEFAULT NULL COMMENT '确认人（班组长）',
  `confirmed_time` datetime DEFAULT NULL COMMENT '确认时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`plan_id`),
  KEY `idx_begin_end` (`begin_date`,`end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排班计划确认快照表（P1-9）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_forecast_plan`
--

LOCK TABLES `ai_forecast_plan` WRITE;
/*!40000 ALTER TABLE `ai_forecast_plan` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_forecast_plan` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_form_draft`
--

DROP TABLE IF EXISTS `ai_form_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_form_draft` (
  `draft_id` bigint NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `form_key` varchar(128) NOT NULL COMMENT '表单标识（页面:用户名）',
  `content` longtext COMMENT '表单内容（JSON：{ts, data}）',
  `update_time` datetime DEFAULT NULL COMMENT '最后保存时间',
  PRIMARY KEY (`draft_id`),
  UNIQUE KEY `uk_form_draft_user_key` (`user_id`,`form_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表单草稿（U4）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_form_draft`
--

LOCK TABLES `ai_form_draft` WRITE;
/*!40000 ALTER TABLE `ai_form_draft` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_form_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_hotspot_suppress`
--

DROP TABLE IF EXISTS `ai_hotspot_suppress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_hotspot_suppress` (
  `suppress_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `match_type` varchar(16) NOT NULL DEFAULT 'PHONE' COMMENT '匹配类型 PHONE号码 KEYWORD关键词',
  `match_value` varchar(128) NOT NULL COMMENT '匹配值（号码精确匹配/关键词）',
  `action` varchar(16) NOT NULL DEFAULT 'PRIORITY' COMMENT '处置动作 PRIORITY置底降权 REJECT直接拦截',
  `priority_level` int NOT NULL DEFAULT '-100' COMMENT '置底优先级（负数，入站队列数值越大越优先）',
  `trigger_count` int NOT NULL DEFAULT '0' COMMENT '时间窗内触发次数阈值（0=不按频次，命中即生效）',
  `window_seconds` int NOT NULL DEFAULT '0' COMMENT '频次统计时间窗（秒，0=不限制）',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '累计命中次数',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  `effective_start` datetime DEFAULT NULL COMMENT '生效开始时间（空=立即）',
  `effective_end` datetime DEFAULT NULL COMMENT '生效结束时间（空=长期）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`suppress_id`),
  UNIQUE KEY `uk_match` (`match_type`,`match_value`),
  KEY `idx_status_effect` (`status`,`effective_start`,`effective_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='高频置底规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_hotspot_suppress`
--

LOCK TABLES `ai_hotspot_suppress` WRITE;
/*!40000 ALTER TABLE `ai_hotspot_suppress` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_hotspot_suppress` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_hotspot_suppress_log`
--

DROP TABLE IF EXISTS `ai_hotspot_suppress_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_hotspot_suppress_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `suppress_id` bigint NOT NULL COMMENT '命中规则ID',
  `rule_name` varchar(100) DEFAULT NULL COMMENT '规则名称（冗余，便于检索）',
  `direction` varchar(8) NOT NULL DEFAULT 'INBOUND' COMMENT '呼叫方向 INBOUND入站 OUTBOUND外呼',
  `match_type` varchar(16) DEFAULT NULL COMMENT '匹配类型',
  `match_value` varchar(128) DEFAULT NULL COMMENT '命中的匹配值',
  `caller_number` varchar(32) DEFAULT NULL COMMENT '主叫号码',
  `callee_number` varchar(32) DEFAULT NULL COMMENT '被叫号码',
  `action` varchar(16) DEFAULT NULL COMMENT '实际处置动作',
  `priority` int DEFAULT NULL COMMENT '处置后优先级',
  `channel_uuid` varchar(64) DEFAULT NULL COMMENT '通道UUID/话单关联键',
  `create_time` datetime DEFAULT NULL COMMENT '命中时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_suppress` (`suppress_id`),
  KEY `idx_caller_time` (`caller_number`,`create_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='高频置底命中处置日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_hotspot_suppress_log`
--

LOCK TABLES `ai_hotspot_suppress_log` WRITE;
/*!40000 ALTER TABLE `ai_hotspot_suppress_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_hotspot_suppress_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_edge`
--

DROP TABLE IF EXISTS `ai_ivr_edge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_edge` (
  `edge_id` bigint NOT NULL AUTO_INCREMENT COMMENT '连线ID',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `source_node_id` bigint NOT NULL COMMENT '源节点ID',
  `target_node_id` bigint NOT NULL COMMENT '目标节点ID',
  `edge_label` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '连线标签',
  `condition_expr` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '条件表达式（SpEL）',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`edge_id`),
  KEY `idx_flow_id` (`flow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IVR连线表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_edge`
--

LOCK TABLES `ai_ivr_edge` WRITE;
/*!40000 ALTER TABLE `ai_ivr_edge` DISABLE KEYS */;
INSERT INTO `ai_ivr_edge` VALUES (100,100,100,101,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(101,100,101,102,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(102,100,102,103,'1',NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(103,100,102,105,'0',NULL,1,'admin','2026-11-16 22:43:25','',NULL,NULL),(104,100,103,104,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(105,100,104,105,NULL,'matchedIntention == \'TRANSFER_AGENT\'',0,'admin','2026-11-16 22:43:25','',NULL,NULL),(106,100,104,107,NULL,NULL,1,'admin','2026-11-16 22:43:25','',NULL,NULL),(107,100,107,106,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(200,200,200,201,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(201,200,201,202,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(202,200,202,203,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(203,200,203,204,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(204,200,204,205,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(205,200,205,206,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(206,200,206,207,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(207,200,207,208,'负面','sentiment == \'negative\'',0,'admin','2026-11-16 22:43:25','',NULL,NULL),(208,200,207,209,'默认',NULL,1,'admin','2026-11-16 22:43:25','',NULL,NULL),(209,200,209,210,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(210,201,211,212,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(211,201,212,213,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(212,201,213,214,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL),(213,201,214,215,NULL,NULL,0,'admin','2026-11-16 22:43:25','',NULL,NULL);
/*!40000 ALTER TABLE `ai_ivr_edge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_execution_log`
--

DROP TABLE IF EXISTS `ai_ivr_execution_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_execution_log` (
  `exec_id` bigint NOT NULL AUTO_INCREMENT COMMENT '执行ID',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `session_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '会话ID',
  `flow_id` bigint NOT NULL COMMENT '流程ID',
  `flow_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程名称',
  `current_node_id` bigint DEFAULT NULL COMMENT '当前节点ID',
  `current_node_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前节点类型',
  `current_node_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '当前节点名称',
  `execute_result` text COLLATE utf8mb4_general_ci COMMENT '执行结果',
  `variables` longtext COLLATE utf8mb4_general_ci COMMENT '流程变量JSON',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0执行中 1已完成 2异常终止）',
  `error_msg` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '错误信息',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`exec_id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_ai_ivr_execution_log_ct` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IVR流程执行日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_execution_log`
--

LOCK TABLES `ai_ivr_execution_log` WRITE;
/*!40000 ALTER TABLE `ai_ivr_execution_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_ivr_execution_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_flow`
--

DROP TABLE IF EXISTS `ai_ivr_flow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_flow` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '流程ID',
  `flow_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程名称',
  `flow_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '流程编码',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程描述',
  `flow_data` longtext COLLATE utf8mb4_general_ci COMMENT '流程定义JSON（LogicFlow格式）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0草稿 1已发布 2停用）',
  `version` int DEFAULT '1' COMMENT '版本号',
  `category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '流程分类',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否默认流程（0否 1是）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_flow_code` (`flow_code`)
) ENGINE=InnoDB AUTO_INCREMENT=202 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IVR流程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_flow`
--

LOCK TABLES `ai_ivr_flow` WRITE;
/*!40000 ALTER TABLE `ai_ivr_flow` DISABLE KEYS */;
INSERT INTO `ai_ivr_flow` VALUES (100,'12348法律咨询默认流程','DEFAULT_LAWYER','12348热线法律咨询默认IVR流程，包含语音导航、意图识别、转人工等节点',NULL,'1',1,'法律咨询','1','admin','2026-11-16 22:43:22','',NULL,'系统默认流程'),(101,'民事咨询流程','CIVIL_CONSULT','民事案件咨询专用IVR流程',NULL,'0',1,'民事咨询','0','admin','2026-11-16 22:43:22','',NULL,''),(102,'刑事咨询流程','CRIMINAL_CONSULT','刑事案件咨询专用IVR流程',NULL,'0',1,'刑事咨询','0','admin','2026-11-16 22:43:22','',NULL,''),(200,'SmartCall节点演示主流程','SMARTCALL_DEMO','演示语音收声、DTMF收号、情绪分析、信息抽取、子流程、变量赋值等SmartCall节点',NULL,'1',1,'demo','0','admin','2026-11-16 22:43:25','',NULL,'SmartCall设计融合演示'),(201,'SmartCall节点演示子流程','SMARTCALL_DEMO_CHILD','被主流程 child 节点调用的子流程',NULL,'1',1,'demo','0','admin','2026-11-16 22:43:25','',NULL,'子流程复用演示');
/*!40000 ALTER TABLE `ai_ivr_flow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_intention`
--

DROP TABLE IF EXISTS `ai_ivr_intention`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_intention` (
  `intention_id` bigint NOT NULL AUTO_INCREMENT COMMENT '意图ID',
  `intention_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '意图名称',
  `intention_code` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '意图编码',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '描述',
  `category` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '意图分类',
  `regex_pattern` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '正则匹配模式',
  `prompt_template` longtext COLLATE utf8mb4_general_ci COMMENT 'AI识别Prompt模板',
  `example_utterances` longtext COLLATE utf8mb4_general_ci COMMENT '示例话术（JSON数组）',
  `model_id` bigint DEFAULT NULL COMMENT '关联AI模型ID',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`intention_id`),
  UNIQUE KEY `uk_intention_code` (`intention_code`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='意图定义表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_intention`
--

LOCK TABLES `ai_ivr_intention` WRITE;
/*!40000 ALTER TABLE `ai_ivr_intention` DISABLE KEYS */;
INSERT INTO `ai_ivr_intention` VALUES (100,'法律咨询','LEGAL_CONSULT','用户需要法律相关咨询','咨询类','.*(咨询|法律|怎么办|怎么处理).*','请判断用户是否需要法律咨询服务，用户问题：{input}','[\"我想咨询一下法律问题\",\"这个事情怎么办\",\"法律上怎么处理\"]',NULL,1,'0','admin','2026-11-16 22:43:22','',NULL,''),(101,'合同纠纷','CONTRACT_DISPUTE','用户涉及合同相关纠纷','民事类','.*(合同|违约|协议|签订).*','请判断用户是否涉及合同纠纷，用户问题：{input}','[\"合同违约了怎么办\",\"签订的协议有问题\",\"对方不履行合同\"]',NULL,2,'0','admin','2026-11-16 22:43:22','',NULL,''),(102,'婚姻家庭','MARRIAGE_FAMILY','用户涉及婚姻家庭相关问题','民事类','.*(离婚|结婚|婚姻|抚养|财产).*','请判断用户是否涉及婚姻家庭问题，用户问题：{input}','[\"想离婚怎么办\",\"孩子抚养权\",\"夫妻财产分割\"]',NULL,2,'0','admin','2026-11-16 22:43:22','',NULL,''),(103,'劳动争议','LABOR_DISPUTE','用户涉及劳动争议相关问题','民事类','.*(工资|劳动|工伤|辞退|仲裁).*','请判断用户是否涉及劳动争议问题，用户问题：{input}','[\"公司拖欠工资\",\"被辞退了怎么办\",\"工伤赔偿标准\"]',NULL,2,'0','admin','2026-11-16 22:43:22','',NULL,''),(104,'转人工坐席','TRANSFER_AGENT','用户要求转接人工坐席','操作类','.*(人工|客服|坐席|转人工).*','请判断用户是否需要转接人工坐席，用户问题：{input}','[\"我要转人工\",\"找客服说话\",\"接人工坐席\"]',NULL,10,'0','admin','2026-11-16 22:43:22','',NULL,'');
/*!40000 ALTER TABLE `ai_ivr_intention` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_intention_log`
--

DROP TABLE IF EXISTS `ai_ivr_intention_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_intention_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `session_id` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话ID',
  `flow_id` bigint DEFAULT NULL COMMENT '流程ID',
  `node_id` bigint DEFAULT NULL COMMENT '节点ID',
  `input_text` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '输入文本',
  `matched_intention` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '匹配的意图编码',
  `matched_intention_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '匹配的意图名称',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '置信度',
  `match_method` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '匹配方式（1正则 2AI模型）',
  `all_results` longtext COLLATE utf8mb4_general_ci COMMENT '所有匹配结果JSON',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_ai_ivr_intention_log_ct` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='意图识别日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_intention_log`
--

LOCK TABLES `ai_ivr_intention_log` WRITE;
/*!40000 ALTER TABLE `ai_ivr_intention_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_ivr_intention_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ivr_node`
--

DROP TABLE IF EXISTS `ai_ivr_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ivr_node` (
  `node_id` bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
  `flow_id` bigint NOT NULL COMMENT '所属流程ID',
  `node_type` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点类型（say/answer/intention/agent/condition/transfer/hangup等）',
  `node_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '节点名称',
  `node_config` longtext COLLATE utf8mb4_general_ci COMMENT '节点配置JSON',
  `position_x` int DEFAULT '0' COMMENT '画布X坐标',
  `position_y` int DEFAULT '0' COMMENT '画布Y坐标',
  `sort_order` int DEFAULT '0' COMMENT '排序',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`node_id`),
  KEY `idx_flow_id` (`flow_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IVR节点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ivr_node`
--

LOCK TABLES `ai_ivr_node` WRITE;
/*!40000 ALTER TABLE `ai_ivr_node` DISABLE KEYS */;
INSERT INTO `ai_ivr_node` VALUES (100,100,'start','开始',NULL,80,80,0,'admin','2026-11-16 22:43:25','',NULL,'流程起点'),(101,100,'say','欢迎语','{\"text\":\"您好，欢迎拨打12348公共法律服务热线。法律咨询请按1，转人工请按0。\"}',260,80,1,'admin','2026-11-16 22:43:25','',NULL,'语音播报'),(102,100,'menu','功能选择','{\"prompt\":\"请选择服务\"}',440,80,2,'admin','2026-11-16 22:43:25','',NULL,'按键导航'),(103,100,'intention','法律意图识别','{}',620,80,3,'admin','2026-11-16 22:43:25','',NULL,'正则+AI双引擎识别'),(104,100,'condition','意图分类分支','{}',800,80,4,'admin','2026-11-16 22:43:25','',NULL,'按意图路由'),(105,100,'agent','转人工坐席','{\"agentId\":0,\"agentExtension\":\"\"}',980,80,5,'admin','2026-11-16 22:43:25','',NULL,'转接人工'),(106,100,'hangup','挂断',NULL,1160,80,6,'admin','2026-11-16 22:43:25','',NULL,'结束通话'),(107,100,'say','咨询提示语','{\"text\":\"请您简要描述您的法律问题，我们将为您匹配专业律师。\"}',980,220,7,'admin','2026-11-16 22:43:25','',NULL,'意图识别后引导'),(200,200,'start','开始',NULL,80,80,0,'admin','2026-11-16 22:43:25','',NULL,'流程起点'),(201,200,'say','欢迎语','{\"text\":\"您好，欢迎来电，主叫号码${callerNumber}。\"}',260,80,1,'admin','2026-11-16 22:43:25','',NULL,'支持SpEL模板'),(202,200,'received','收工单号','{\"jqrask\":\"请输入工单编号，按#号结束\",\"endKey\":\"#\",\"maxDigits\":10}',440,80,2,'admin','2026-11-16 22:43:25','',NULL,'DTMF收号'),(203,200,'answer','收集问题','{\"jqrask\":\"请问您遇到了什么法律问题？\",\"silencePrompt\":true,\"silenceDuration\":3,\"silenceSay\":\"我没有听清，请您再说一遍\"}',620,80,3,'admin','2026-11-16 22:43:25','',NULL,'ASR语音收声'),(204,200,'sentiment','情绪分析','{\"text\":\"${lastInput}\"}',800,80,4,'admin','2026-11-16 22:43:25','',NULL,'写入sentiment变量'),(205,200,'extract','信息抽取','{\"text\":\"${lastInput}\",\"fields\":[{\"name\":\"userName\",\"desc\":\"当事人姓名\"},{\"name\":\"address\",\"desc\":\"涉及地址\"}],\"resultVar\":\"extractResult\"}',980,80,5,'admin','2026-11-16 22:43:25','',NULL,'LLM结构化抽取'),(206,200,'child','调用子流程','{\"flowCode\":\"SMARTCALL_DEMO_CHILD\"}',1160,80,6,'admin','2026-11-16 22:43:25','',NULL,'子流程复用'),(207,200,'condition','情绪分支','{}',1340,80,7,'admin','2026-11-16 22:43:25','',NULL,'按sentiment路由'),(208,200,'agent','转人工','{\"queue\":\"complaint\"}',1520,80,8,'admin','2026-11-16 22:43:25','',NULL,'负面情绪转人工'),(209,200,'say','结束语','{\"text\":\"感谢您的来电，再见。\"}',1520,220,9,'admin','2026-11-16 22:43:25','',NULL,'播报结束'),(210,200,'hangup','挂断',NULL,1700,220,10,'admin','2026-11-16 22:43:25','',NULL,'结束通话'),(211,201,'start','子流程开始',NULL,80,80,0,'admin','2026-11-16 22:43:25','',NULL,'子流程起点'),(212,201,'variable','写标记变量','{\"variables\":[{\"key\":\"childMark\",\"val\":\"CHILD-OK\"}]}',260,80,1,'admin','2026-11-16 22:43:25','',NULL,'变量赋值'),(213,201,'script','拼接工单号','{\"scriptType\":\"js\",\"script\":\"var no = String(dtmf);\n\'ticket=\' + no;\",\"resultVar\":\"ticketNo\"}',440,80,2,'admin','2026-11-16 22:43:25','',NULL,'JS脚本'),(214,201,'say','子流程播报','{\"text\":\"我们已记录您的诉求，工单号为${dtmf}。\"}',620,80,3,'admin','2026-11-16 22:43:25','',NULL,'引用父流程变量'),(215,201,'hangup','子流程结束',NULL,800,80,4,'admin','2026-11-16 22:43:25','',NULL,'结束子流程');
/*!40000 ALTER TABLE `ai_ivr_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_audit`
--

DROP TABLE IF EXISTS `ai_knowledge_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_audit` (
  `audit_id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核流水ID',
  `knowledge_id` bigint NOT NULL COMMENT '知识ID',
  `publish_version` int DEFAULT '1' COMMENT '对应版本号',
  `action` varchar(20) NOT NULL COMMENT '动作 SUBMIT/APPROVE/REJECT/PUBLISH/OFFLINE/VALID_STATUS',
  `from_status` varchar(10) DEFAULT NULL COMMENT '变更前审核状态',
  `to_status` varchar(10) DEFAULT NULL COMMENT '变更后审核状态',
  `audit_opinion` varchar(1000) DEFAULT NULL COMMENT '审核意见',
  `auditor` varchar(64) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`audit_id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库审核发布流水（F7）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_audit`
--

LOCK TABLES `ai_knowledge_audit` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_audit` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_audit` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_knowledge_query_log`
--

DROP TABLE IF EXISTS `ai_knowledge_query_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_knowledge_query_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `query_text` varchar(512) NOT NULL COMMENT '脱敏并截断后的问句文本',
  `query_hash` char(32) NOT NULL COMMENT '归一化问句 SHA-256 前32位（分组键）',
  `scene` varchar(32) NOT NULL DEFAULT 'other' COMMENT '调用场景 agent/voice_robot/copilot/other',
  `hit_count` int NOT NULL DEFAULT '0' COMMENT '命中知识分块数',
  `top_knowledge_id` bigint DEFAULT NULL COMMENT '最高分命中知识ID（无命中为空）',
  `route` varchar(16) NOT NULL DEFAULT 'none' COMMENT '命中路由 fused/vector/keyword/none',
  `latency_ms` int NOT NULL DEFAULT '0' COMMENT '检索耗时（毫秒）',
  `converted` tinyint NOT NULL DEFAULT '0' COMMENT '是否已转知识草稿：0否 1是',
  `create_time` datetime NOT NULL COMMENT '记录时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_kql_create_time` (`create_time`),
  KEY `idx_kql_hash_time` (`query_hash`,`create_time`),
  KEY `idx_kql_hit_time` (`hit_count`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识检索查询日志（F8 运营分析）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_knowledge_query_log`
--

LOCK TABLES `ai_knowledge_query_log` WRITE;
/*!40000 ALTER TABLE `ai_knowledge_query_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_knowledge_query_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_legal_article`
--

DROP TABLE IF EXISTS `ai_legal_article`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_legal_article` (
  `article_id` bigint NOT NULL AUTO_INCREMENT COMMENT '法条ID',
  `law_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '法律名称',
  `article_number` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '法条编号',
  `article_content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '法条内容',
  `category` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类',
  `keywords` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '关键词',
  `view_count` int DEFAULT '0' COMMENT '查看次数',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`article_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法条库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_legal_article`
--

LOCK TABLES `ai_legal_article` WRITE;
/*!40000 ALTER TABLE `ai_legal_article` DISABLE KEYS */;
INSERT INTO `ai_legal_article` VALUES (1001,'中华人民共和国民法典','第一千零八十七条','离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。\n\n对夫或者妻在家庭土地承包经营中享有的权益等，应当依法予以保护。','marriage_family','离婚,财产分割,夫妻共同财产',67,'0','admin','2026-11-16 22:43:17','',NULL,NULL),(1002,'中华人民共和国劳动合同法','第四十八条','用人单位违反本法规定解除或者终止劳动合同，劳动者要求继续履行劳动合同的，用人单位应当继续履行；劳动者不要求继续履行劳动合同或者劳动合同已经不能继续履行的，用人单位应当依照本法第八十七条规定支付赔偿金。','labor_dispute','违法解除,劳动合同,赔偿金',89,'0','admin','2026-11-16 22:43:17','',NULL,NULL);
/*!40000 ALTER TABLE `ai_legal_article` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_legal_case`
--

DROP TABLE IF EXISTS `ai_legal_case`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_legal_case` (
  `case_id` bigint NOT NULL AUTO_INCREMENT COMMENT '案例ID',
  `category` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '分类',
  `case_number` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '案例编号',
  `case_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '案例名称',
  `case_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '案例类型',
  `case_summary` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '案例摘要',
  `case_detail` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '案例详情',
  `court` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '审理法院',
  `judgment_date` date DEFAULT NULL COMMENT '判决日期',
  `keywords` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '关键词',
  `view_count` int DEFAULT '0' COMMENT '查看次数',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`case_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律案例库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_legal_case`
--

LOCK TABLES `ai_legal_case` WRITE;
/*!40000 ALTER TABLE `ai_legal_case` DISABLE KEYS */;
INSERT INTO `ai_legal_case` VALUES (1001,'marriage_family','(2023)京01民终1234号','张某诉李某离婚纠纷案','离婚纠纷','张某与李某因感情破裂诉至法院要求离婚，双方对财产分割存在争议。法院经审理认为，夫妻共同财产应按照照顾子女、女方和无过错方权益的原则进行分割。','张某与李某于2015年登记结婚，婚后育有一子。因感情破裂，张某诉至法院要求离婚。双方对子女抚养问题达成一致，但对财产分割存在争议。法院经审理查明，夫妻共同财产包括：房产一套、车辆两辆、银行存款50万元、股票市值30万元。法院综合考虑双方贡献、子女利益等因素，判决房产归张某所有，张某支付李某折价补偿款60万元；车辆各分一辆；银行存款和股票按6:4比例分割。','北京市第一中级人民法院','2023-05-15','离婚,财产分割,夫妻共同财产',32,'0','admin','2026-11-16 22:43:17','',NULL,NULL),(1002,'labor_dispute','(2023)粤01民终3456号','张某诉某科技公司违法解除劳动合同案','劳动争议','张某在某科技公司工作5年，公司以业务调整为由单方面解除劳动合同，未支付经济补偿。张某诉至法院要求公司支付违法解除劳动合同赔偿金。','张某于2018年入职某科技公司，担任技术主管，月薪15000元。2023年3月，公司以业务调整为由单方面解除与张某的劳动合同，未支付任何经济补偿。张某认为公司违法解除劳动合同，向劳动争议仲裁委员会申请仲裁，要求公司支付违法解除劳动合同赔偿金。仲裁委员会裁决公司支付张某赔偿金15万元。公司不服仲裁裁决，诉至法院。法院经审理认为，公司未能证明解除劳动合同的合法性，属于违法解除，应支付赔偿金。','广东省广州市中级人民法院','2023-07-20','违法解除,劳动合同,赔偿金',45,'0','admin','2026-11-16 22:43:17','',NULL,NULL);
/*!40000 ALTER TABLE `ai_legal_case` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_legal_consultation`
--

DROP TABLE IF EXISTS `ai_legal_consultation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_legal_consultation` (
  `consultation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '咨询ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `question` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '咨询问题',
  `category_id` bigint DEFAULT NULL COMMENT '问题分类ID',
  `answer` text COLLATE utf8mb4_general_ci COMMENT 'AI回答',
  `confidence` decimal(5,2) DEFAULT NULL COMMENT '回答置信度',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0处理中 1已完成 2失败）',
  `consultation_channel` char(1) COLLATE utf8mb4_general_ci DEFAULT '2' COMMENT '咨询渠道（1语音 2图文 3视频）',
  `attachment_path` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '附件路径',
  `rating` int DEFAULT NULL COMMENT '用户评分(1-5)',
  `feedback` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户反馈',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`consultation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律咨询记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_legal_consultation`
--

LOCK TABLES `ai_legal_consultation` WRITE;
/*!40000 ALTER TABLE `ai_legal_consultation` DISABLE KEYS */;
INSERT INTO `ai_legal_consultation` VALUES (100,1,'离婚后孩子的抚养权应该归谁？',1,'根据《民法典》相关规定，离婚后不满两周岁的子女以由母亲直接抚养为原则。已满两周岁的子女，父母双方对抚养问题协议不成的，由人民法院根据双方的具体情况按照最有利于未成年子女的原则判决。',NULL,'1','1',NULL,NULL,NULL,'admin','2026-08-01 09:15:00','',NULL,'语音渠道咨询'),(101,2,'劳动合同未签订，如何维权？',2,'根据《劳动合同法》规定，用人单位自用工之日起超过一个月不满一年未与劳动者订立书面劳动合同的，应当向劳动者每月支付二倍的工资。',NULL,'1','1',NULL,NULL,NULL,'admin','2026-08-01 10:30:00','',NULL,'语音渠道咨询'),(102,3,'交通事故责任认定标准是什么？',3,'交通事故责任认定由公安机关交通管理部门根据当事人的行为对发生道路交通事故所起的作用以及过错的严重程度确定。',NULL,'1','2',NULL,NULL,NULL,'admin','2026-08-01 11:00:00','',NULL,'图文渠道咨询'),(103,4,'房屋租赁合同提前解约如何处理？',4,'需根据合同约定处理。若合同未约定，提前解约方应承担违约责任，赔偿房东合理损失。建议协商解决。',NULL,'1','2',NULL,NULL,NULL,'admin','2026-08-01 13:45:00','',NULL,'图文渠道咨询'),(104,5,'债务纠纷起诉需要哪些材料？',5,'需要准备起诉状、债权凭证（借条、欠条等）、转账记录、身份证明等材料，向有管辖权的人民法院提起诉讼。',NULL,'1','3',NULL,NULL,NULL,'admin','2026-08-01 14:20:00','',NULL,'视频渠道咨询'),(105,6,'医疗事故如何申请鉴定？',6,'可向卫生行政部门提出医疗事故技术鉴定申请，或直接向医学会提出鉴定申请。需提交相关病历资料。',NULL,'0','3',NULL,NULL,NULL,'admin','2026-08-01 15:30:00','',NULL,'视频渠道咨询处理中'),(106,7,'知识产权侵权如何取证？',7,'可通过公证保全、时间戳认证、区块链存证等方式固定证据。建议在发现侵权后及时取证，必要时可申请法院诉前证据保全。',NULL,'1','2',NULL,NULL,NULL,'admin','2026-08-01 16:10:00','',NULL,'图文渠道咨询'),(107,8,'公司股权纠纷如何解决？',8,'可通过协商、调解、仲裁或诉讼方式解决。建议先查阅公司章程及股东协议的相关约定，再选择合适的争议解决方式。',NULL,'1','1',NULL,NULL,NULL,'admin','2026-08-01 16:45:00','',NULL,'语音渠道咨询');
/*!40000 ALTER TABLE `ai_legal_consultation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_legal_knowledge`
--

DROP TABLE IF EXISTS `ai_legal_knowledge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_legal_knowledge` (
  `knowledge_id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识ID',
  `title` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识标题',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '知识内容',
  `law_article` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '法律条文',
  `law_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '法律名称',
  `article_no` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '条号',
  `issuing_authority` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发布机关',
  `effective_date` date DEFAULT NULL COMMENT '施行日期',
  `valid_status` varchar(8) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '效力状态 1现行有效 2已修订 3已失效',
  `source_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权威来源链接',
  `publish_version` int DEFAULT '1' COMMENT '发布版本号',
  `source` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '来源',
  `keywords` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关键词',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `audit_status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '审核状态（0待审核 1审核通过 2审核不通过）',
  `view_count` int DEFAULT '0' COMMENT '浏览量',
  `audit_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '审核备注',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`knowledge_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='法律知识库表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_legal_knowledge`
--

LOCK TABLES `ai_legal_knowledge` WRITE;
/*!40000 ALTER TABLE `ai_legal_knowledge` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_legal_knowledge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_legal_knowledge_chunk`
--

DROP TABLE IF EXISTS `ai_legal_knowledge_chunk`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_legal_knowledge_chunk` (
  `chunk_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分块ID',
  `knowledge_id` bigint NOT NULL COMMENT '所属知识ID',
  `title` varchar(200) DEFAULT '' COMMENT '知识标题（冗余，便于溯源展示）',
  `chunk_index` int NOT NULL DEFAULT '0' COMMENT '分块序号（知识内从0开始）',
  `chunk_content` text NOT NULL COMMENT '分块文本（标题/法条 + 正文片段）',
  `law_article` varchar(500) DEFAULT NULL COMMENT '法律条文（冗余溯源）',
  `source` varchar(200) DEFAULT NULL COMMENT '出处来源（冗余溯源）',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID（冗余，便于按类过滤）',
  `embedding` longblob COMMENT '向量（float[] 小端字节，备份/重建内存索引用）',
  `embedding_dim` int DEFAULT '0' COMMENT '向量维度（0=未向量化）',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`chunk_id`),
  KEY `idx_knowledge_id` (`knowledge_id`),
  KEY `idx_category_id` (`category_id`),
  FULLTEXT KEY `ft_chunk_content` (`chunk_content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法律知识分块表（T3 RAG）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_legal_knowledge_chunk`
--

LOCK TABLES `ai_legal_knowledge_chunk` WRITE;
/*!40000 ALTER TABLE `ai_legal_knowledge_chunk` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_legal_knowledge_chunk` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_message`
--

DROP TABLE IF EXISTS `ai_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `message_id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `receiver_user_id` bigint NOT NULL COMMENT '接收人用户ID（sys_user.user_id）',
  `msg_type` char(1) DEFAULT '1' COMMENT '消息类型（1系统通知 2待办提醒 3风险预警 4质检通知 5工单通知 9其他）',
  `title` varchar(200) NOT NULL COMMENT '消息标题',
  `content` varchar(1000) DEFAULT '' COMMENT '消息内容',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务类型（quality/ticket/warning/outbound 等，用于跳转）',
  `biz_id` bigint DEFAULT NULL COMMENT '业务ID（跳转目标主键）',
  `is_read` char(1) DEFAULT '0' COMMENT '是否已读（0未读 1已读）',
  `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
  `sender` varchar(64) DEFAULT 'system' COMMENT '发送方（system/质检驳回人等）',
  `priority` char(1) DEFAULT '2' COMMENT '优先级（1高 2中 3低）',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`message_id`),
  KEY `idx_receiver_read` (`receiver_user_id`,`is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消息中心-站内信';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_message`
--

LOCK TABLES `ai_message` WRITE;
/*!40000 ALTER TABLE `ai_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_missed_call`
--

DROP TABLE IF EXISTS `ai_missed_call`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_missed_call` (
  `missed_call_id` bigint NOT NULL AUTO_INCREMENT COMMENT '未接来电ID',
  `caller_number` varchar(30) NOT NULL COMMENT '来电号码',
  `caller_name` varchar(50) DEFAULT NULL COMMENT '来电人姓名',
  `call_time` datetime DEFAULT NULL COMMENT '来电时间',
  `status` char(1) DEFAULT '0' COMMENT '状态（0未回拨 1已回拨）',
  `callback_time` datetime DEFAULT NULL COMMENT '回拨时间',
  `callback_by` varchar(64) DEFAULT NULL COMMENT '回拨人',
  `record_id` bigint DEFAULT NULL COMMENT '关联来电记录ID',
  `voice_content` varchar(1000) DEFAULT NULL COMMENT '语音留言内容',
  `voice_duration` int DEFAULT NULL COMMENT '语音留言时长(秒)',
  `voice_file_url` varchar(512) DEFAULT NULL COMMENT '语音留言录音文件 URL/路径',
  `notice_status` char(1) DEFAULT '0' COMMENT '通知状态（0未通知 1已通知）',
  `notice_channel` varchar(20) DEFAULT NULL COMMENT '通知方式（1短信 2微信 3邮件）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`missed_call_id`),
  KEY `idx_ai_missed_call_number` (`caller_number`),
  KEY `idx_ai_missed_call_time` (`call_time`),
  KEY `idx_ai_missed_call_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='未接来电表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_missed_call`
--

LOCK TABLES `ai_missed_call` WRITE;
/*!40000 ALTER TABLE `ai_missed_call` DISABLE KEYS */;
INSERT INTO `ai_missed_call` VALUES (1,'13800138001','张丽华','2026-08-01 09:12:30','1','2026-08-01 10:05:00','admin',NULL,'关于离婚财产分割的咨询，请回电',25,NULL,'1','1','admin','2026-08-01 09:12:30','',NULL,'已回拨'),(2,'13900139002','王志强','2026-08-01 10:45:18','0',NULL,NULL,NULL,'劳动纠纷咨询，请求尽快回电',18,NULL,'1','2','admin','2026-08-01 10:45:18','',NULL,NULL),(3,'13700137003','李秀英','2026-08-01 11:22:00','0',NULL,NULL,NULL,NULL,NULL,NULL,'0',NULL,'admin','2026-08-01 11:22:00','',NULL,'无留言'),(4,'13600136004','陈国庆','2026-08-01 13:30:45','1','2026-08-01 14:15:22','admin',NULL,'交通事故赔偿问题',32,NULL,'1','1','admin','2026-08-01 13:30:45','',NULL,NULL),(5,'13500135005','赵美玲','2026-08-01 14:50:10','0',NULL,NULL,NULL,'房屋租赁合同纠纷，希望咨询律师',40,NULL,'0',NULL,'admin','2026-08-01 14:50:10','',NULL,'紧急'),(6,'13800138006','孙建华','2026-08-01 15:18:33','0',NULL,NULL,NULL,NULL,NULL,NULL,'0',NULL,'admin','2026-08-01 15:18:33','',NULL,'多次未接'),(7,'13900139007','周晓东','2026-07-31 16:20:00','1','2026-08-01 09:00:00','admin',NULL,'债务纠纷，已协商',28,NULL,'1','3','admin','2026-07-31 16:20:00','',NULL,NULL),(8,'13700137008','吴桂芳','2026-07-31 17:05:42','0',NULL,NULL,NULL,'医疗事故咨询，请律师回电',35,NULL,'1','2','admin','2026-07-31 17:05:42','',NULL,NULL);
/*!40000 ALTER TABLE `ai_missed_call` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model_call_log`
--

DROP TABLE IF EXISTS `ai_model_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_call_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `call_time` datetime NOT NULL COMMENT '调用发起时间',
  `kind` varchar(16) NOT NULL DEFAULT 'chat' COMMENT '调用类型：chat 对话/embed 向量/rerank 精排',
  `scene` varchar(32) NOT NULL DEFAULT 'other' COMMENT '业务场景：intention/emotion/extract/quality/summary/agent/consultation/rag/rag_index/test/other',
  `config_id` bigint DEFAULT NULL COMMENT '模型配置ID（rerank 等无配置时为空）',
  `config_name` varchar(100) DEFAULT NULL COMMENT '配置名称快照',
  `model_type` varchar(32) DEFAULT NULL COMMENT '模型类型快照',
  `model_name` varchar(100) DEFAULT NULL COMMENT '模型名称快照',
  `prompt_tokens` int NOT NULL DEFAULT '0' COMMENT '输入Token数（embedding 计入此项）',
  `completion_tokens` int NOT NULL DEFAULT '0' COMMENT '输出Token数',
  `total_tokens` int NOT NULL DEFAULT '0' COMMENT '总Token数',
  `input_price` decimal(12,6) DEFAULT NULL COMMENT '输入单价快照（元/千Token）',
  `output_price` decimal(12,6) DEFAULT NULL COMMENT '输出单价快照（元/千Token）',
  `cost_amount` decimal(14,6) NOT NULL DEFAULT '0.000000' COMMENT '估算费用（元，按单价快照折算）',
  `elapsed_ms` int NOT NULL DEFAULT '0' COMMENT '端到端耗时（毫秒，含重试等待）',
  `attempts` int NOT NULL DEFAULT '1' COMMENT '实际尝试次数（含首次）',
  `result` char(1) NOT NULL DEFAULT '1' COMMENT '结果：1成功 0失败 2舱壁拒绝',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因（截断500字）',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路traceId（与MDC/日志串联）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_mcl_call_time` (`call_time`),
  KEY `idx_mcl_model` (`model_name`,`call_time`),
  KEY `idx_mcl_kind_scene` (`kind`,`scene`,`call_time`),
  KEY `idx_mcl_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型调用明细日志（P3-E5 成本与质量看板数据源）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_model_call_log`
--

LOCK TABLES `ai_model_call_log` WRITE;
/*!40000 ALTER TABLE `ai_model_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_model_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model_config`
--

DROP TABLE IF EXISTS `ai_model_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '配置名称',
  `model_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型类型',
  `model_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '模型名称',
  `api_key` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API密钥',
  `api_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'API地址',
  `max_tokens` int DEFAULT '2048' COMMENT '最大令牌数',
  `input_price` decimal(12,6) DEFAULT NULL COMMENT '输入单价（元/千Token，成本估算用）',
  `output_price` decimal(12,6) DEFAULT NULL COMMENT '输出单价（元/千Token，成本估算用）',
  `temperature` decimal(3,2) DEFAULT '0.70' COMMENT '温度参数(0.0-1.0)',
  `top_p` decimal(3,2) DEFAULT '0.90' COMMENT 'Top-p参数(0.0-1.0)',
  `frequency_penalty` decimal(3,2) DEFAULT '0.00' COMMENT '频率惩罚(-2.0-2.0)',
  `presence_penalty` decimal(3,2) DEFAULT '0.00' COMMENT '存在惩罚(-2.0-2.0)',
  `timeout` int DEFAULT '30' COMMENT '请求超时时间(秒)',
  `retry_count` int DEFAULT '3' COMMENT '重试次数',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否默认（0否 1是）',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=102 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI模型参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_model_config`
--

LOCK TABLES `ai_model_config` WRITE;
/*!40000 ALTER TABLE `ai_model_config` DISABLE KEYS */;
INSERT INTO `ai_model_config` VALUES (100,'默认GPT配置','gpt','gpt-3.5-turbo','sk-xxxxxxxxxxxxxxxx','https://api.openai.com/v1/chat/completions',2048,NULL,NULL,0.70,0.90,0.00,0.00,30,3,'0','1','默认使用的GPT模型配置','admin','2026-11-16 22:43:19','',NULL),(101,'本地模型配置','local','lawyer-ai-v1',NULL,'http://localhost:8080/api/chat',4096,NULL,NULL,0.80,0.95,0.10,0.10,60,2,'1','0','本地部署的法律AI模型','admin','2026-11-16 22:43:19','',NULL);
/*!40000 ALTER TABLE `ai_model_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_notice`
--

DROP TABLE IF EXISTS `ai_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_notice` (
  `notice_id` bigint NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(200) NOT NULL COMMENT '公告标题',
  `notice_content` text COMMENT '公告内容',
  `notice_type` varchar(50) DEFAULT NULL COMMENT '公告类型',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `status` char(1) DEFAULT '0' COMMENT '状态（0草稿 1发布）',
  `is_top` char(1) DEFAULT '0' COMMENT '是否置顶（0否 1是）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`),
  KEY `idx_ai_notice_status` (`status`),
  KEY `idx_ai_notice_publish` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作台公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_notice`
--

LOCK TABLES `ai_notice` WRITE;
/*!40000 ALTER TABLE `ai_notice` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_number_segment`
--

DROP TABLE IF EXISTS `ai_number_segment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_number_segment` (
  `segment_id` bigint NOT NULL AUTO_INCREMENT COMMENT '号段ID',
  `segment_prefix` varchar(16) NOT NULL COMMENT '号段前缀',
  `carrier` char(2) DEFAULT NULL COMMENT '运营商',
  `number_type` char(1) DEFAULT '1' COMMENT '号码类型 1=手机 2=固话区号 3=虚商 4=特服号',
  `province` varchar(32) DEFAULT NULL COMMENT '归属省份',
  `city` varchar(32) DEFAULT NULL COMMENT '归属城市',
  `match_length` int DEFAULT '7' COMMENT '匹配长度',
  `enable_flag` char(1) DEFAULT '1' COMMENT '启用状态 0=停用 1=启用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`segment_id`),
  UNIQUE KEY `uk_segment_prefix` (`segment_prefix`),
  KEY `idx_segment_match` (`enable_flag`,`match_length`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='号段-运营商路由';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_number_segment`
--

LOCK TABLES `ai_number_segment` WRITE;
/*!40000 ALTER TABLE `ai_number_segment` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_number_segment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_outbound_callee`
--

DROP TABLE IF EXISTS `ai_outbound_callee`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_outbound_callee` (
  `callee_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `callee_number` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '被叫号码',
  `callee_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '被叫姓名',
  `callee_gender` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '性别（0男 1女 2未知）',
  `callee_age` int DEFAULT NULL COMMENT '年龄',
  `callee_address` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '地址',
  `callee_params` longtext COLLATE utf8mb4_general_ci COMMENT '附加参数（JSON）',
  `call_status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '呼叫状态（0待呼叫 1呼叫中 2已接通 3未接 4失败 5已完成 6已取消）',
  `call_time` datetime DEFAULT NULL COMMENT '呼叫时间',
  `call_duration` int DEFAULT '0' COMMENT '通话时长（秒）',
  `record_id` bigint DEFAULT NULL COMMENT '关联通话记录ID',
  `agent_id` bigint DEFAULT NULL COMMENT '处理坐席ID',
  `retry_times` int DEFAULT '0' COMMENT '已重拨次数',
  `last_retry_time` datetime DEFAULT NULL COMMENT '最后重拨时间',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次可重试时间（指数退避窗口，T2-2）',
  `fail_reason` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '失败原因',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`callee_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_call_status` (`call_status`),
  KEY `idx_callee_number` (`callee_number`)
) ENGINE=InnoDB AUTO_INCREMENT=1004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外呼号码表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_outbound_callee`
--

LOCK TABLES `ai_outbound_callee` WRITE;
/*!40000 ALTER TABLE `ai_outbound_callee` DISABLE KEYS */;
INSERT INTO `ai_outbound_callee` VALUES (1001,100,'13800000001','张三','0',35,'上海市浦东新区','{\"input\":\"我签的合同对方违约了，怎么办\"}','0',NULL,0,NULL,NULL,0,NULL,NULL,NULL,'admin','2026-11-16 22:43:25','',NULL,'演示号码-合同纠纷'),(1002,100,'13800000002','李四','1',42,'上海市徐汇区','{\"input\":\"我想咨询离婚和财产分割的问题\"}','0',NULL,0,NULL,NULL,0,NULL,NULL,NULL,'admin','2026-11-16 22:43:25','',NULL,'演示号码-婚姻家庭'),(1003,100,'13800000003','王五','0',28,'上海市静安区','{\"input\":\"公司拖欠我三个月工资，我想申请劳动仲裁\"}','0',NULL,0,NULL,NULL,0,NULL,NULL,NULL,'admin','2026-11-16 22:43:25','',NULL,'演示号码-劳动争议');
/*!40000 ALTER TABLE `ai_outbound_callee` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_outbound_result`
--

DROP TABLE IF EXISTS `ai_outbound_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_outbound_result` (
  `result_id` bigint NOT NULL AUTO_INCREMENT COMMENT '结果ID',
  `task_id` bigint NOT NULL COMMENT '任务ID',
  `callee_id` bigint NOT NULL COMMENT '被叫ID',
  `callee_number` varchar(20) COLLATE utf8mb4_general_ci NOT NULL COMMENT '被叫号码',
  `callee_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '被叫姓名',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `call_result` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '呼叫结果（1已接通 2未接 3忙 4关机 5空号 6停机 7其他）',
  `call_duration` int DEFAULT '0' COMMENT '通话时长（秒）',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席ID',
  `agent_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '坐席姓名',
  `intention_code` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '识别意图编码',
  `intention_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '识别意图名称',
  `keywords` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关键信息（JSON数组）',
  `satisfaction` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '满意度（1非常满意 2满意 3一般 4不满意 5非常不满意）',
  `need_callback` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否需要回访（0否 1是）',
  `callback_time` datetime DEFAULT NULL COMMENT '建议回访时间',
  `transcript` longtext COLLATE utf8mb4_general_ci COMMENT '通话转写文本',
  `summary` text COLLATE utf8mb4_general_ci COMMENT '通话摘要',
  `recording_url` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '录音地址',
  `flow_data` longtext COLLATE utf8mb4_general_ci COMMENT '流程执行数据（JSON）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`result_id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_callee_id` (`callee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外呼结果表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_outbound_result`
--

LOCK TABLES `ai_outbound_result` WRITE;
/*!40000 ALTER TABLE `ai_outbound_result` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_outbound_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_outbound_task`
--

DROP TABLE IF EXISTS `ai_outbound_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_outbound_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `task_no` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务编号',
  `task_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '任务类型（1批量外呼 2回访 3通知）',
  `caller_number` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '主叫号码',
  `ivr_flow_id` bigint DEFAULT NULL COMMENT '关联IVR流程ID',
  `ivr_flow_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'IVR流程名称',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `total_count` int DEFAULT '0' COMMENT '总号码数',
  `completed_count` int DEFAULT '0' COMMENT '已完成数',
  `answered_count` int DEFAULT '0' COMMENT '已接通数',
  `failed_count` int DEFAULT '0' COMMENT '失败数',
  `no_answer_count` int DEFAULT '0' COMMENT '未接数',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0待执行 1执行中 2已完成 3已暂停 4已终止）',
  `priority` int DEFAULT '5' COMMENT '优先级（1-10，数字越大优先级越高）',
  `retry_count` int DEFAULT '0' COMMENT '重拨次数',
  `retry_interval` int DEFAULT '30' COMMENT '重拨间隔（分钟）',
  `max_concurrent` int DEFAULT '10' COMMENT '最大并发数',
  `caller_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '发起人姓名',
  `caller_dept_id` bigint DEFAULT NULL COMMENT '发起部门ID',
  `description` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务说明',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外呼任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_outbound_task`
--

LOCK TABLES `ai_outbound_task` WRITE;
/*!40000 ALTER TABLE `ai_outbound_task` DISABLE KEYS */;
INSERT INTO `ai_outbound_task` VALUES (100,'民法典宣传回访任务','OB20260801001','2','12348',100,'12348法律咨询默认流程','2026-11-16 22:43:22','2026-11-23 22:43:22',3,0,0,0,0,'0',5,1,30,10,'管理员',103,'民法典实施一周年宣传回访任务','admin','2026-11-16 22:43:22','',NULL,'');
/*!40000 ALTER TABLE `ai_outbound_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_paid_legal_service`
--

DROP TABLE IF EXISTS `ai_paid_legal_service`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_paid_legal_service` (
  `paid_id` bigint NOT NULL AUTO_INCREMENT COMMENT '付费服务ID',
  `service_no` varchar(64) DEFAULT NULL COMMENT '服务编号',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '客户姓名',
  `customer_phone` varchar(20) DEFAULT NULL COMMENT '客户电话',
  `service_type` varchar(32) DEFAULT NULL COMMENT '服务类型（代书/调解/诉讼代理/法律顾问）',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '服务金额',
  `payment_status` char(1) DEFAULT '0' COMMENT '支付状态（0未付 1已付 2部分付）',
  `service_status` char(1) DEFAULT '0' COMMENT '服务状态（0待受理 1处理中 2已完成 3已关闭）',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人用户ID',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`paid_id`),
  KEY `idx_aps_no` (`service_no`),
  KEY `idx_aps_customer` (`customer_name`),
  KEY `idx_aps_type` (`service_type`),
  KEY `idx_aps_payment` (`payment_status`),
  KEY `idx_aps_service` (`service_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='有偿法律服务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_paid_legal_service`
--

LOCK TABLES `ai_paid_legal_service` WRITE;
/*!40000 ALTER TABLE `ai_paid_legal_service` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_paid_legal_service` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_pii_search_token`
--

DROP TABLE IF EXISTS `ai_pii_search_token`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_pii_search_token` (
  `token_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'tokenID',
  `owner_type` varchar(30) NOT NULL COMMENT '属主类型 CALL_RECORD/CALL_LEDGER/CALLER_PROFILE',
  `owner_id` bigint NOT NULL COMMENT '属主ID record_id/ledger_id/profile_id',
  `token_value` char(32) NOT NULL COMMENT '位置分片盲token（HMAC-SM3 截断 128 位 hex）',
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uk_pii_token_owner` (`owner_type`,`owner_id`,`token_value`),
  KEY `idx_pii_token_lookup` (`token_value`,`owner_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PII 模糊检索位置分片盲token（G1-b2）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_pii_search_token`
--

LOCK TABLES `ai_pii_search_token` WRITE;
/*!40000 ALTER TABLE `ai_pii_search_token` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_pii_search_token` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_quality_inspection`
--

DROP TABLE IF EXISTS `ai_quality_inspection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_quality_inspection` (
  `inspection_id` bigint NOT NULL AUTO_INCREMENT COMMENT '质检ID',
  `source_type` char(1) NOT NULL DEFAULT '1' COMMENT '来源 1通话录音 2图文 3视频',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `session_id` varchar(64) DEFAULT NULL COMMENT '会话ID',
  `agent_id` bigint DEFAULT NULL COMMENT '被检坐席ID',
  `caller_number` varchar(32) DEFAULT NULL COMMENT '主叫号码（冗余便于检索）',
  `transcript` text COMMENT '转写文本（ASR/对话拼接）',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT 'AI质检总分（0-100）',
  `dimension_json` text COMMENT '各维度评分JSON（服务规范/答复准确/情绪态度/违禁话术）',
  `violation_json` text COMMENT '命中违禁/敏感词/情绪问题明细JSON',
  `ai_status` char(1) DEFAULT '0' COMMENT 'AI质检 0待检 1检中 2完成 3失败',
  `ai_remark` varchar(1000) DEFAULT NULL COMMENT 'AI质检总体评语/失败原因',
  `review_status` char(1) DEFAULT '0' COMMENT '人工复核 0未复核 1通过 2驳回整改',
  `reviewer_id` bigint DEFAULT NULL COMMENT '复核人用户ID',
  `reviewer_name` varchar(64) DEFAULT NULL COMMENT '复核人姓名',
  `review_remark` varchar(500) DEFAULT NULL COMMENT '复核评语',
  `review_time` datetime DEFAULT NULL COMMENT '复核时间',
  `risk_warning_id` bigint DEFAULT NULL COMMENT '联动生成的风险预警ID',
  `appeal_status` char(1) DEFAULT '0' COMMENT '申诉状态 0未申诉 1待复核 2维持 3已改分',
  `appeal_reason` varchar(500) DEFAULT NULL COMMENT '坐席申诉理由',
  `appeal_time` datetime DEFAULT NULL COMMENT '申诉时间',
  `appeal_review_remark` varchar(500) DEFAULT NULL COMMENT '班组长申诉复核说明',
  `adjusted_score` decimal(5,1) DEFAULT NULL COMMENT '申诉成立后的调整分数（NULL=未调整）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`inspection_id`),
  KEY `idx_record` (`record_id`),
  KEY `idx_agent` (`agent_id`),
  KEY `idx_review` (`review_status`),
  KEY `idx_ai_status` (`ai_status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_quality_appeal_status` (`appeal_status`),
  KEY `idx_qi_ai_status_create` (`ai_status`,`create_time`),
  KEY `idx_qi_review_create` (`review_status`,`create_time`),
  KEY `idx_qi_agent_create` (`agent_id`,`create_time`),
  KEY `idx_qi_risk_warning` (`risk_warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能质检记录表（T4-1）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_quality_inspection`
--

LOCK TABLES `ai_quality_inspection` WRITE;
/*!40000 ALTER TABLE `ai_quality_inspection` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_quality_inspection` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_quality_template`
--

DROP TABLE IF EXISTS `ai_quality_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_quality_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `template_name` varchar(64) NOT NULL COMMENT '模板名称',
  `dimensions` varchar(500) NOT NULL COMMENT '维度权重JSON：[{"key":"serviceNorm","name":"服务规范","weight":1.0}]',
  `score_prompt` text COMMENT '自定义评分系统提示词（留空用内置默认）',
  `is_default` char(1) NOT NULL DEFAULT '0' COMMENT '1默认生效模板（仅一条）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_by` varchar(64) DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`template_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质检评分模板（P1-7 维度/权重/prompt 自定义）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_quality_template`
--

LOCK TABLES `ai_quality_template` WRITE;
/*!40000 ALTER TABLE `ai_quality_template` DISABLE KEYS */;
INSERT INTO `ai_quality_template` VALUES (1,'默认热线质检模板','[{\"key\":\"serviceNorm\",\"name\":\"服务规范\",\"weight\":1.0},{\"key\":\"answerAccuracy\",\"name\":\"答复准确\",\"weight\":1.0},{\"key\":\"emotionAttitude\",\"name\":\"情绪态度\",\"weight\":1.0},{\"key\":\"compliance\",\"name\":\"合规话术\",\"weight\":1.0}]',NULL,'1','0','system','2026-11-16 22:43:41',NULL,NULL);
/*!40000 ALTER TABLE `ai_quality_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_return_visit_task`
--

DROP TABLE IF EXISTS `ai_return_visit_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_return_visit_task` (
  `task_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_no` varchar(50) DEFAULT NULL COMMENT '任务编号',
  `caller_number` varchar(30) DEFAULT NULL COMMENT '来电号码',
  `caller_name` varchar(50) DEFAULT NULL COMMENT '来电人姓名',
  `plan_time` datetime DEFAULT NULL COMMENT '计划回访时间',
  `actual_time` datetime DEFAULT NULL COMMENT '实际回访时间',
  `assignee` varchar(64) DEFAULT NULL COMMENT '受理人',
  `assignee_id` bigint DEFAULT NULL COMMENT '受理人用户ID',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待回访 1已完成 2已逾期）',
  `priority` char(1) DEFAULT '2' COMMENT '优先级（1高 2中 3低）',
  `visit_result` varchar(1000) DEFAULT NULL COMMENT '回访结果',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`task_id`),
  KEY `idx_rvt_no` (`task_no`),
  KEY `idx_rvt_number` (`caller_number`),
  KEY `idx_rvt_plan` (`plan_time`),
  KEY `idx_rvt_status` (`status`),
  KEY `idx_rvt_assignee` (`assignee`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='回访任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_return_visit_task`
--

LOCK TABLES `ai_return_visit_task` WRITE;
/*!40000 ALTER TABLE `ai_return_visit_task` DISABLE KEYS */;
INSERT INTO `ai_return_visit_task` VALUES (1,'RVT20260801001','13800138001','张丽华','2026-08-01 10:00:00','2026-08-01 09:55:00','admin',NULL,'1','1','客户满意，问题已解决','admin','2026-07-31 16:00:00','',NULL,'紧急回访'),(2,'RVT20260801002','13900139002','王志强','2026-08-01 11:00:00','2026-08-01 11:10:00','admin',NULL,'1','2','已联系，需进一步跟进','admin','2026-07-31 16:30:00','',NULL,NULL),(3,'RVT20260801003','13700137003','李秀英','2026-08-02 09:30:00',NULL,'admin',NULL,'0','2',NULL,'admin','2026-08-01 08:00:00','',NULL,'常规回访'),(4,'RVT20260801004','13600136004','陈国庆','2026-08-02 14:00:00',NULL,'admin',NULL,'0','1',NULL,'admin','2026-08-01 09:00:00','',NULL,'高优先级'),(5,'RVT20260801005','13500135005','赵美玲','2026-07-31 16:00:00',NULL,'admin',NULL,'2','1',NULL,'admin','2026-07-30 10:00:00','',NULL,'客户未接听，已逾期'),(6,'RVT20260801006','13800138006','孙建华','2026-08-03 10:30:00',NULL,'admin',NULL,'0','3',NULL,'admin','2026-08-01 10:00:00','',NULL,'低优先级'),(7,'RVT20260801007','13900139007','周晓东','2026-07-30 15:00:00','2026-07-30 15:20:00','admin',NULL,'1','2','客户表示感谢','admin','2026-07-29 11:00:00','',NULL,NULL),(8,'RVT20260801008','13700137008','吴桂芳','2026-07-29 09:00:00',NULL,'admin',NULL,'2','1',NULL,'admin','2026-07-28 14:00:00','',NULL,'多次未联系上'),(9,'RVT20260801009','13600136009','郑伟明','2026-08-01 13:30:00','2026-08-01 13:40:00','admin',NULL,'1','2','问题已解答','admin','2026-07-31 17:00:00','',NULL,NULL),(10,'RVT20260801010','13500135010','黄丽萍','2026-08-02 16:00:00',NULL,'admin',NULL,'0','2',NULL,'admin','2026-08-01 11:30:00','',NULL,'常规回访');
/*!40000 ALTER TABLE `ai_return_visit_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_risk_warning`
--

DROP TABLE IF EXISTS `ai_risk_warning`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_risk_warning` (
  `warning_id` bigint NOT NULL AUTO_INCREMENT COMMENT '预警ID',
  `warning_type` varchar(32) DEFAULT NULL COMMENT '预警类型（敏感词/情绪异常/异常行为/合规风险）',
  `warning_level` char(1) DEFAULT '3' COMMENT '预警级别（1高 2中 3低）',
  `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型（通话/图文/视频）',
  `source_id` bigint DEFAULT NULL COMMENT '来源ID',
  `rule_id` bigint DEFAULT NULL COMMENT '命中的风险预警规则ID（ai_risk_warning_rule.rule_id），空=手工/其他来源',
  `suggest_transfer_type` varchar(20) DEFAULT NULL COMMENT '建议转办条线快照，预警生成时由命中规则写入',
  `transfer_id` bigint DEFAULT NULL COMMENT '一键转办生成的转办流水ID（ai_ticket_transfer.transfer_id），非空=已转办',
  `content` text COMMENT '触发内容',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '客户姓名',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待处理 1处理中 2已处理 3已忽略）',
  `handler_name` varchar(64) DEFAULT NULL COMMENT '处理人姓名',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人用户ID',
  `handle_result` varchar(500) DEFAULT NULL COMMENT '处理结果',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `trigger_time` datetime DEFAULT NULL COMMENT '触发时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`warning_id`),
  KEY `idx_ai_risk_warn_type` (`warning_type`),
  KEY `idx_ai_risk_warn_level` (`warning_level`),
  KEY `idx_ai_risk_warn_status` (`status`),
  KEY `idx_ai_risk_warn_trigger` (`trigger_time`),
  KEY `idx_risk_warning_transfer` (`transfer_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能风险预警记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_risk_warning`
--

LOCK TABLES `ai_risk_warning` WRITE;
/*!40000 ALTER TABLE `ai_risk_warning` DISABLE KEYS */;
INSERT INTO `ai_risk_warning` VALUES (1,'敏感词','1','通话',1,NULL,NULL,NULL,'客户在通话中提及了不文明用语，触发了敏感词监控规则。','张三','0',NULL,NULL,NULL,NULL,'2026-08-10 09:15:00','2026-08-10 09:15:01','语音通话中出现敏感词汇'),(2,'情绪异常','2','图文',2,NULL,NULL,NULL,'客户在图文对话中表现出明显的愤怒情绪，语气激烈。','李四','1',NULL,NULL,NULL,NULL,'2026-08-10 10:30:00','2026-08-10 10:30:01','图文对话中情绪异常'),(3,'异常行为','1','视频',3,NULL,NULL,NULL,'视频咨询中检测到异常画面，疑似存在不合规行为。','王五','0',NULL,NULL,NULL,NULL,'2026-08-10 11:00:00','2026-08-10 11:00:01','视频咨询检测异常'),(4,'合规风险','2','通话',4,NULL,NULL,NULL,'通话内容涉及敏感法律建议，可能存在越权风险。','赵六','2',NULL,NULL,NULL,NULL,'2026-08-10 13:30:00','2026-08-10 13:30:01','通话内容合规风险'),(5,'敏感词','3','图文',5,NULL,NULL,NULL,'图文对话中检测到轻度敏感词。','孙七','3',NULL,NULL,NULL,NULL,'2026-08-10 14:00:00','2026-08-10 14:00:01','轻度敏感词已忽略');
/*!40000 ALTER TABLE `ai_risk_warning` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_risk_warning_rule`
--

DROP TABLE IF EXISTS `ai_risk_warning_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_risk_warning_rule` (
  `rule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `rule_name` varchar(100) NOT NULL COMMENT '规则名称',
  `rule_type` varchar(32) DEFAULT NULL COMMENT '规则类型（敏感词/情绪异常/异常行为/合规风险）',
  `rule_level` char(1) DEFAULT '3' COMMENT '规则级别（1高 2中 3低）',
  `keywords` varchar(500) DEFAULT NULL COMMENT '关键词（多个关键词用逗号分隔）',
  `suggest_transfer_type` varchar(20) DEFAULT NULL COMMENT '建议转办条线 LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345，空=不建议',
  `suggest_org_id` bigint DEFAULT NULL COMMENT '默认建议协同机构ID（ai_external_org.org_id），空=坐席按条线自选',
  `is_enabled` char(1) DEFAULT '1' COMMENT '是否启用（0禁用 1启用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`rule_id`),
  KEY `idx_ai_risk_rule_type` (`rule_type`),
  KEY `idx_ai_risk_rule_enabled` (`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='智能风险预警规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_risk_warning_rule`
--

LOCK TABLES `ai_risk_warning_rule` WRITE;
/*!40000 ALTER TABLE `ai_risk_warning_rule` DISABLE KEYS */;
INSERT INTO `ai_risk_warning_rule` VALUES (1,'敏感词过滤规则','敏感词','1','投诉,差评,骗子,退款',NULL,NULL,'1','admin','2026-08-10 09:00:00','',NULL,'监控对话中的敏感词汇'),(2,'情绪异常检测规则','情绪异常','2','我很生气,太失望了,无法接受',NULL,NULL,'1','admin','2026-08-10 09:00:00','',NULL,'检测客户情绪波动'),(3,'异常行为监控规则','异常行为','1','威胁,举报,起诉',NULL,NULL,'1','admin','2026-08-10 09:00:00','',NULL,'监控异常行为关键词'),(4,'合规风险预警规则','合规风险','2','违法,刑期,免于起诉',NULL,NULL,'1','admin','2026-08-10 09:00:00','',NULL,'监控法律合规风险关键词');
/*!40000 ALTER TABLE `ai_risk_warning_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_shift_change_request`
--

DROP TABLE IF EXISTS `ai_shift_change_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_shift_change_request` (
  `request_id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请ID',
  `applicant_agent_id` bigint NOT NULL COMMENT '申请人坐席ID',
  `applicant_schedule_id` bigint NOT NULL COMMENT '申请人待变更排班ID',
  `change_type` varchar(16) NOT NULL DEFAULT 'SWAP' COMMENT '变更类型 SWAP互换 TRANSFER转班',
  `target_agent_id` bigint DEFAULT NULL COMMENT '接收坐席ID（互换取对方排班坐席，转班必填）',
  `target_schedule_id` bigint DEFAULT NULL COMMENT '对方排班ID（互换必填，转班为空）',
  `reason` varchar(500) DEFAULT NULL COMMENT '申请原因',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态 0待审批 1已通过 2已驳回 3已撤销',
  `approver` varchar(64) DEFAULT NULL COMMENT '审批人（班组长账号）',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approve_remark` varchar(500) DEFAULT NULL COMMENT '审批意见',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`request_id`),
  KEY `idx_applicant_status` (`applicant_agent_id`,`status`),
  KEY `idx_status_create` (`status`,`create_time`),
  KEY `idx_applicant_schedule` (`applicant_schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='坐席换班申请与审批（F9）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_shift_change_request`
--

LOCK TABLES `ai_shift_change_request` WRITE;
/*!40000 ALTER TABLE `ai_shift_change_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_shift_change_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_sign_record`
--

DROP TABLE IF EXISTS `ai_sign_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sign_record` (
  `sign_id` bigint NOT NULL AUTO_INCREMENT COMMENT '签署记录ID',
  `business_no` varchar(64) DEFAULT '' COMMENT '业务流水号',
  `sign_template` varchar(100) DEFAULT '' COMMENT '签署模板',
  `sign_party` varchar(64) DEFAULT '' COMMENT '签署方姓名',
  `sign_party_phone` varchar(20) DEFAULT '' COMMENT '签署方电话',
  `sign_status` char(1) DEFAULT '0' COMMENT '签署状态（0待签署 1签署中 2已完成 3已作废）',
  `sign_time` datetime DEFAULT NULL COMMENT '签署完成时间',
  `e_sign_id` varchar(100) DEFAULT '' COMMENT '电子签章ID',
  `certificate_no` varchar(100) DEFAULT '' COMMENT '存证编号',
  `sign_file_url` varchar(255) DEFAULT '' COMMENT '签署文件地址',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`sign_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签署记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_sign_record`
--

LOCK TABLES `ai_sign_record` WRITE;
/*!40000 ALTER TABLE `ai_sign_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_sign_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_skill_group`
--

DROP TABLE IF EXISTS `ai_skill_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_skill_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT COMMENT '技能组ID',
  `group_name` varchar(80) NOT NULL COMMENT '技能组名称（民事/刑事/劳动/婚姻等）',
  `group_code` varchar(40) NOT NULL COMMENT '技能组编码',
  `category_id` bigint DEFAULT NULL COMMENT '关联咨询分类ID（B1 agentCategoryId 自动映射）',
  `strategy` varchar(20) DEFAULT 'round_robin' COMMENT '分配策略（round_robin轮询 least_recent最久未接 least_calls最少通话 all_ring全员振铃）',
  `max_wait` int DEFAULT '60' COMMENT '最大排队等待秒（超时溢出）',
  `wrap_up_time` int DEFAULT '10' COMMENT '话后整理秒（话后期间不分配）',
  `service_level_threshold` int DEFAULT '20' COMMENT '服务水平阈值秒（大屏统计用）',
  `overflow_group_id` bigint DEFAULT NULL COMMENT '溢出技能组ID（无可用坐席时）',
  `status` char(1) DEFAULT '1' COMMENT '状态（0停用 1启用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_group_code` (`group_code`),
  KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能组表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_skill_group`
--

LOCK TABLES `ai_skill_group` WRITE;
/*!40000 ALTER TABLE `ai_skill_group` DISABLE KEYS */;
INSERT INTO `ai_skill_group` VALUES (100,'婚姻家事组','MARRIAGE',105,'least_recent',60,10,20,NULL,'1','',NULL,'',NULL,'婚姻、继承、抚养等家事纠纷'),(101,'劳动争议组','LABOR',106,'least_recent',60,10,20,NULL,'1','',NULL,'',NULL,'劳动合同、工资、工伤等'),(102,'合同纠纷组','CONTRACT',107,'round_robin',60,10,20,NULL,'1','',NULL,'',NULL,'各类民商事合同纠纷'),(103,'刑事辩护组','CRIMINAL',102,'least_calls',90,15,30,NULL,'1','',NULL,'',NULL,'刑事咨询与辩护，高优先级'),(104,'综合咨询组','GENERAL',100,'round_robin',45,5,15,100,'1','',NULL,'',NULL,'兜底技能组，溢出到婚姻家事组');
/*!40000 ALTER TABLE `ai_skill_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_skill_group_member`
--

DROP TABLE IF EXISTS `ai_skill_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_skill_group_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `group_id` bigint NOT NULL COMMENT '技能组ID',
  `agent_id` bigint NOT NULL COMMENT '坐席ID（ai_call_agent_status.agent_id）',
  `skill_level` int DEFAULT '3' COMMENT '技能等级1-5（等级高者优先分配）',
  `priority` int DEFAULT '0' COMMENT '组内优先级（同等级按此排序，越大越优先）',
  `max_concurrent` int DEFAULT '1' COMMENT '个人最大并发通话数',
  `status` char(1) DEFAULT '1' COMMENT '状态（0禁用 1启用）',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_agent` (`group_id`,`agent_id`),
  KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='技能组成员表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_skill_group_member`
--

LOCK TABLES `ai_skill_group_member` WRITE;
/*!40000 ALTER TABLE `ai_skill_group_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_skill_group_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_sla_policy`
--

DROP TABLE IF EXISTS `ai_sla_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sla_policy` (
  `policy_id` bigint NOT NULL AUTO_INCREMENT COMMENT '策略ID',
  `policy_name` varchar(100) NOT NULL COMMENT '策略名称',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型 TICKET/LEGAL_AID/MEDIATION/NOTARY/FORENSIC/ARBITRATION/HOTLINE_12345',
  `priority` char(1) NOT NULL DEFAULT '2' COMMENT '优先级 1紧急 2普通 3低',
  `respond_minutes` int NOT NULL DEFAULT '60' COMMENT '响应时限（分钟）',
  `resolve_minutes` int NOT NULL DEFAULT '1440' COMMENT '办结时限（分钟）',
  `warn_threshold` int NOT NULL DEFAULT '80' COMMENT '预警阈值（百分比，时限消耗达该比例预警）',
  `escalate_roles` varchar(300) DEFAULT NULL COMMENT '逐级升级角色链（逗号分隔角色key，如 team_leader,manager,director）',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态 0启用 1停用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`policy_id`),
  UNIQUE KEY `uk_biz_priority` (`biz_type`,`priority`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单SLA策略配置（F9）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_sla_policy`
--

LOCK TABLES `ai_sla_policy` WRITE;
/*!40000 ALTER TABLE `ai_sla_policy` DISABLE KEYS */;
INSERT INTO `ai_sla_policy` VALUES (1,'热线工单-紧急','TICKET','1',15,240,80,'ai_team_leader,ai_manager','0','admin','2026-11-16 22:43:32','',NULL,'紧急工单4小时办结'),(2,'热线工单-普通','TICKET','2',60,1440,80,'ai_team_leader,ai_manager,ai_director','0','admin','2026-11-16 22:43:32','',NULL,'普通工单24小时办结'),(3,'热线工单-低','TICKET','3',240,4320,80,'ai_team_leader','0','admin','2026-11-16 22:43:32','',NULL,'低优先级3个工作日'),(4,'法援转办默认策略','LEGAL_AID','2',120,4320,80,'ai_manager,ai_director','0','admin','2026-11-16 22:43:32','',NULL,'F3法援条线');
/*!40000 ALTER TABLE `ai_sla_policy` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_sms_config`
--

DROP TABLE IF EXISTS `ai_sms_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sms_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT '通道ID',
  `config_name` varchar(80) NOT NULL COMMENT '通道名称',
  `provider` varchar(20) DEFAULT 'mock' COMMENT '供应商（mock/aliyun/tencent）',
  `access_key_id` varchar(128) DEFAULT NULL COMMENT '访问密钥ID',
  `access_key_secret` varchar(128) DEFAULT NULL COMMENT '访问密钥Secret',
  `sign_name` varchar(50) DEFAULT NULL COMMENT '短信签名',
  `region_id` varchar(40) DEFAULT NULL COMMENT '地域（阿里云）',
  `sdk_app_id` varchar(60) DEFAULT NULL COMMENT '腾讯云AppId',
  `daily_limit` int DEFAULT '10' COMMENT '单号码日发送上限（防骚扰）',
  `status` char(1) DEFAULT '1' COMMENT '状态（0停用 1启用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`),
  KEY `idx_provider` (`provider`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信通道配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_sms_config`
--

LOCK TABLES `ai_sms_config` WRITE;
/*!40000 ALTER TABLE `ai_sms_config` DISABLE KEYS */;
INSERT INTO `ai_sms_config` VALUES (1,'本地模拟通道','mock',NULL,NULL,'AI法律咨询',NULL,NULL,10,'1','admin','2026-11-16 22:43:26','',NULL,'无真实短信网关，仅落库留痕');
/*!40000 ALTER TABLE `ai_sms_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_sms_log`
--

DROP TABLE IF EXISTS `ai_sms_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sms_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `phone` varchar(20) DEFAULT NULL COMMENT '接收号码',
  `template_id` bigint DEFAULT NULL COMMENT '模板ID',
  `config_id` bigint DEFAULT NULL COMMENT '通道ID',
  `params_json` varchar(500) DEFAULT NULL COMMENT '模板变量JSON',
  `content` varchar(500) DEFAULT NULL COMMENT '实际发送内容',
  `send_status` char(1) DEFAULT '0' COMMENT '发送状态（0待发 1成功 2失败）',
  `provider_msg_id` varchar(80) DEFAULT NULL COMMENT '供应商回执ID',
  `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `session_id` varchar(64) DEFAULT NULL COMMENT 'IVR会话ID',
  `record_id` bigint DEFAULT NULL COMMENT '通话记录ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_phone_time` (`phone`,`create_time`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_record_id` (`record_id`),
  KEY `idx_ai_sms_log_ct` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信发送记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_sms_log`
--

LOCK TABLES `ai_sms_log` WRITE;
/*!40000 ALTER TABLE `ai_sms_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_sms_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_sms_template`
--

DROP TABLE IF EXISTS `ai_sms_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_sms_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `template_name` varchar(80) NOT NULL COMMENT '模板名称',
  `provider_template_code` varchar(60) DEFAULT NULL COMMENT '供应商模板CODE',
  `content` varchar(500) DEFAULT NULL COMMENT '模板内容，含 ${变量} 占位',
  `scene_type` varchar(30) DEFAULT 'generic' COMMENT '场景（queue/welcome/ticket/visit/generic）',
  `config_id` bigint NOT NULL COMMENT '所属短信通道ID',
  `status` char(1) DEFAULT '1' COMMENT '状态（0停用 1启用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`template_id`),
  KEY `idx_config_id` (`config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信模板表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_sms_template`
--

LOCK TABLES `ai_sms_template` WRITE;
/*!40000 ALTER TABLE `ai_sms_template` DISABLE KEYS */;
INSERT INTO `ai_sms_template` VALUES (1,'排队通知','MOCK_QUEUE','【AI法律咨询】您好，您当前排队第${queuePos}位，请耐心等待。','queue',1,'1','admin','2026-11-16 22:43:26','',NULL,'B5排队场景短信'),(2,'工单通知','MOCK_TICKET','【AI法律咨询】您的咨询工单已创建，工单号：${ticketNo}，稍后将有律师与您联系。','ticket',1,'1','admin','2026-11-16 22:43:26','',NULL,'工单创建后短信告知');
/*!40000 ALTER TABLE `ai_sms_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_speaker_profile`
--

DROP TABLE IF EXISTS `ai_speaker_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_speaker_profile` (
  `profile_id` bigint NOT NULL AUTO_INCREMENT COMMENT '声纹档案ID',
  `caller_number_index` varchar(32) DEFAULT NULL COMMENT '来电号码盲索引（HMAC-SM3 截断128位hex，关联 ai_caller_profile.caller_number_index，不存号码）',
  `embedding_model` varchar(64) NOT NULL DEFAULT 'campplus_zh_16k' COMMENT '声纹模型标识（CAM++ 16k 中文，ONNX 导出版本）',
  `embedding_dim` int NOT NULL DEFAULT '192' COMMENT '中心向量维度',
  `centroid_vector` blob COMMENT '聚类中心向量（L2 归一 float32 小端字节，维度与 embedding_dim 一致）',
  `confidence` decimal(5,4) DEFAULT NULL COMMENT '建档置信度（0~1，各样本对中心平均余弦）',
  `sample_count` int NOT NULL DEFAULT '0' COMMENT '参与建中心的语音段数',
  `profile_status` char(1) NOT NULL DEFAULT '0' COMMENT '状态（0草稿待确认 1生效 2撤销/失效）',
  `consent_time` datetime DEFAULT NULL COMMENT '单独同意取得时间（状态置1前必须非空）',
  `pia_ref` varchar(128) DEFAULT NULL COMMENT '个人信息保护影响评估（PIA）归档编号',
  `last_match_time` datetime DEFAULT NULL COMMENT '最近一次跨通话匹配时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`profile_id`),
  UNIQUE KEY `uk_ai_speaker_profile_number_model` (`caller_number_index`,`embedding_model`),
  KEY `idx_ai_speaker_profile_status` (`profile_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='声纹聚类中心表（P2-15 设计先行；跨通话建档待 PIA/法务评估，评估通过前禁止应用层写入）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_speaker_profile`
--

LOCK TABLES `ai_speaker_profile` WRITE;
/*!40000 ALTER TABLE `ai_speaker_profile` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_speaker_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_stat_minute`
--

DROP TABLE IF EXISTS `ai_stat_minute`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_stat_minute` (
  `stat_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stat_time` datetime NOT NULL COMMENT '统计时间（分钟级，如 2026-09-25 10:31:00）',
  `dimension` varchar(64) NOT NULL DEFAULT 'ALL' COMMENT '统计维度（ALL=全局；后续可扩展 skill:xx / agent:xx / line:xx）',
  `metric_key` varchar(64) NOT NULL COMMENT '指标键（call_total/call_answered/call_missed/ticket_total/ticket_closed/...）',
  `metric_value` decimal(18,4) NOT NULL DEFAULT '0.0000' COMMENT '指标值',
  `create_time` datetime DEFAULT NULL COMMENT '写入时间',
  PRIMARY KEY (`stat_id`,`stat_time`),
  UNIQUE KEY `uk_stat` (`stat_time`,`dimension`,`metric_key`),
  KEY `idx_stat_key_time` (`metric_key`,`stat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分钟级物化统计表（P3-F3）'
/*!50100 PARTITION BY RANGE (to_days(`stat_time`))
(PARTITION p202609 VALUES LESS THAN (740255) ENGINE = InnoDB,
 PARTITION p202610 VALUES LESS THAN (740286) ENGINE = InnoDB,
 PARTITION p202611 VALUES LESS THAN (740316) ENGINE = InnoDB,
 PARTITION p202612 VALUES LESS THAN (740347) ENGINE = InnoDB,
 PARTITION p_max VALUES LESS THAN MAXVALUE ENGINE = InnoDB) */;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_stat_minute`
--

LOCK TABLES `ai_stat_minute` WRITE;
/*!40000 ALTER TABLE `ai_stat_minute` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_stat_minute` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_system_prompt`
--

DROP TABLE IF EXISTS `ai_system_prompt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_system_prompt` (
  `prompt_id` bigint NOT NULL AUTO_INCREMENT COMMENT '提示词ID',
  `prompt_name` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '提示词名称',
  `prompt_type` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '提示词类型',
  `scene_type` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '场景类型',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '提示词内容',
  `variables` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '变量定义(JSON格式)',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否默认（0否 1是）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`prompt_id`)
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统提示词配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_system_prompt`
--

LOCK TABLES `ai_system_prompt` WRITE;
/*!40000 ALTER TABLE `ai_system_prompt` DISABLE KEYS */;
INSERT INTO `ai_system_prompt` VALUES (100,'法律咨询默认提示词','legal_consultation','general','你是一名专业的法律顾问，具有丰富的法律知识和实践经验。请根据用户的问题，提供准确、专业的法律建议。\n\n回答要求：\n1. 回答要准确、专业，避免给出错误的法律信息\n2. 回答要简洁明了，便于非专业人士理解\n3. 如果问题涉及复杂法律问题，建议用户咨询专业律师\n4. 回答中可以引用相关法律条文，但要注明来源\n5. 不要提供具体的法律文书模板，只提供指导性建议\n\n用户问题：{question}\n问题分类：{category}\n请根据以上要求回答用户的问题。','{\"question\": \"用户问题\", \"category\": \"问题分类\"}','1','0','用于法律咨询的默认提示词','admin','2026-11-16 22:43:19','',NULL),(101,'民事纠纷提示词','legal_consultation','civil','你是一名专业的民事法律顾问，擅长处理各类民事纠纷案件。\n\n回答要求：\n1. 针对民事纠纷提供专业的法律建议\n2. 解释相关民事法律条文和规定\n3. 提供可能的解决方案和维权途径\n4. 说明诉讼时效和相关程序\n5. 提醒用户保留相关证据\n\n用户问题：{question}\n纠纷类型：{category}\n请根据以上要求回答用户的问题。','{\"question\": \"用户问题\", \"category\": \"纠纷类型\"}','0','0','专门用于民事纠纷咨询的提示词','admin','2026-11-16 22:43:19','',NULL),(102,'刑事辩护提示词','legal_consultation','criminal','你是一名专业的刑事法律顾问，具有丰富的刑事辩护经验。\n\n回答要求：\n1. 解释相关刑事法律条文和规定\n2. 说明可能的法律后果和量刑标准\n3. 提供辩护策略和维权途径\n4. 解释刑事诉讼程序\n5. 强调及时聘请律师的重要性\n\n用户问题：{question}\n案件类型：{category}\n请根据以上要求回答用户的问题。','{\"question\": \"用户问题\", \"category\": \"案件类型\"}','0','0','专门用于刑事辩护咨询的提示词','admin','2026-11-16 22:43:19','',NULL),(103,'合同纠纷提示词','legal_consultation','contract','你是一名专业的合同法律顾问，擅长处理各类合同纠纷。\n\n回答要求：\n1. 解释相关合同法律条文和规定\n2. 分析合同条款的有效性和法律效力\n3. 提供合同纠纷的解决方案\n4. 说明违约责任和赔偿标准\n5. 提供合同风险防范建议\n\n用户问题：{question}\n合同类型：{category}\n请根据以上要求回答用户的问题。','{\"question\": \"用户问题\", \"category\": \"合同类型\"}','0','0','专门用于合同纠纷咨询的提示词','admin','2026-11-16 22:43:19','',NULL);
/*!40000 ALTER TABLE `ai_system_prompt` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ticket_flow_definition`
--

DROP TABLE IF EXISTS `ai_ticket_flow_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ticket_flow_definition` (
  `flow_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_code` varchar(32) NOT NULL DEFAULT 'HOTLINE' COMMENT '工单流程类型（HOTLINE/LEGAL_AID/...）',
  `status_from` char(1) NOT NULL COMMENT '源状态 0待处理 1处理中 2已完成 3已归档',
  `action_code` varchar(32) NOT NULL COMMENT '动作标识（start/complete/archive/transfer）',
  `action_name` varchar(32) NOT NULL COMMENT '动作展示名',
  `target_status` char(1) NOT NULL COMMENT '目标状态（可与源相同）',
  `role_key` varchar(64) NOT NULL DEFAULT 'agent' COMMENT '允许角色逗号分隔（agent/leader/admin）',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同状态下排序',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `sla_hours` decimal(8,2) DEFAULT NULL COMMENT '动作触发后SLA时限(小时)，空=不重算，0=清空截止时间',
  `form_schema` text COMMENT '该动作必填表单JSON',
  PRIMARY KEY (`flow_id`),
  UNIQUE KEY `uk_flow_action` (`flow_code`,`status_from`,`action_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单流程状态机定义（P1-6 配置化流转 DSL）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ticket_flow_definition`
--

LOCK TABLES `ai_ticket_flow_definition` WRITE;
/*!40000 ALTER TABLE `ai_ticket_flow_definition` DISABLE KEYS */;
INSERT INTO `ai_ticket_flow_definition` VALUES (1,'HOTLINE','0','start','开始处理','1','agent,leader',10,'0','2026-11-16 22:43:41','2026-11-16 22:43:45',24.00,'{\"required\":[\"processContent\"]}'),(2,'HOTLINE','0','transfer','转办外派','0','leader,admin',20,'0','2026-11-16 22:43:41','2026-11-16 22:43:45',NULL,'{\"required\":[\"orgId\"]}'),(3,'HOTLINE','1','complete','办结','2','agent,leader',10,'0','2026-11-16 22:43:41',NULL,NULL,NULL),(4,'HOTLINE','1','transfer','转办外派','1','leader,admin',20,'0','2026-11-16 22:43:41','2026-11-16 22:43:45',NULL,'{\"required\":[\"orgId\"]}'),(5,'HOTLINE','2','archive','归档','3','leader,admin',10,'0','2026-11-16 22:43:41','2026-11-16 22:43:45',0.00,NULL);
/*!40000 ALTER TABLE `ai_ticket_flow_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ticket_flow_layout`
--

DROP TABLE IF EXISTS `ai_ticket_flow_layout`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ticket_flow_layout` (
  `layout_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `flow_code` varchar(32) NOT NULL DEFAULT 'HOTLINE' COMMENT '工单流程类型（HOTLINE/LEGAL_AID/...）',
  `status_code` varchar(16) NOT NULL COMMENT '状态编码 0待处理 1处理中 2已完成 3已归档',
  `pos_x` int NOT NULL DEFAULT '0' COMMENT '节点左上角 X 坐标（画布像素）',
  `pos_y` int NOT NULL DEFAULT '0' COMMENT '节点左上角 Y 坐标（画布像素）',
  `update_by` varchar(64) DEFAULT NULL COMMENT '最后调整人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '最后调整时间',
  PRIMARY KEY (`layout_id`),
  UNIQUE KEY `uk_flow_status` (`flow_code`,`status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单流程画布节点布局（F2 可视化编排）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ticket_flow_layout`
--

LOCK TABLES `ai_ticket_flow_layout` WRITE;
/*!40000 ALTER TABLE `ai_ticket_flow_layout` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_ticket_flow_layout` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_ticket_transfer`
--

DROP TABLE IF EXISTS `ai_ticket_transfer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_ticket_transfer` (
  `transfer_id` bigint NOT NULL AUTO_INCREMENT COMMENT '转办流水ID',
  `ticket_id` bigint DEFAULT NULL COMMENT '本系统工单ID（转入建单后回填）',
  `ticket_no` varchar(64) DEFAULT NULL COMMENT '本系统工单号（冗余）',
  `direction` varchar(8) NOT NULL COMMENT '方向 OUT转出 IN转入',
  `external_type` varchar(20) NOT NULL COMMENT '外部条线',
  `org_id` bigint DEFAULT NULL COMMENT '协同机构ID',
  `org_name` varchar(200) DEFAULT NULL COMMENT '协同机构名称（冗余）',
  `external_ticket_no` varchar(64) DEFAULT NULL COMMENT '外部工单号（回写/来单携带）',
  `external_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '外部状态',
  `transfer_status` char(1) NOT NULL DEFAULT '0' COMMENT '流水状态 0处理中 1成功 2失败 3人工处理',
  `request_payload` text COMMENT '转出/来单请求报文（最小必要字段）',
  `callback_payload` text COMMENT '最近回调报文留痕',
  `idempotent_key` varchar(80) NOT NULL COMMENT '幂等键（调用方生成，唯一）',
  `fail_reason` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `next_retry_time` datetime DEFAULT NULL COMMENT '下次重试时间',
  `transfer_time` datetime DEFAULT NULL COMMENT '发起/接收时间',
  `callback_time` datetime DEFAULT NULL COMMENT '最近回调时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`transfer_id`),
  UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),
  KEY `idx_ticket_id` (`ticket_id`),
  KEY `idx_external_no` (`external_type`,`external_ticket_no`),
  KEY `idx_next_retry` (`transfer_status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单跨域转办流水（F3）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_ticket_transfer`
--

LOCK TABLES `ai_ticket_transfer` WRITE;
/*!40000 ALTER TABLE `ai_ticket_transfer` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_ticket_transfer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_todo`
--

DROP TABLE IF EXISTS `ai_todo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_todo` (
  `todo_id` bigint NOT NULL AUTO_INCREMENT COMMENT '待办ID',
  `todo_title` varchar(200) NOT NULL COMMENT '待办标题',
  `todo_content` varchar(1000) DEFAULT NULL COMMENT '待办内容',
  `status` char(1) DEFAULT '0' COMMENT '状态（0待办 1已完成 2延后 3忽略）',
  `priority` char(1) DEFAULT '2' COMMENT '优先级（1紧急 2普通 3低）',
  `due_date` date DEFAULT NULL COMMENT '到期日期',
  `user_id` bigint DEFAULT NULL COMMENT '归属用户ID',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '关联业务类型（ticket=工单）',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务ID（工单ID）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`todo_id`),
  KEY `idx_ai_todo_user` (`user_id`),
  KEY `idx_ai_todo_status` (`status`),
  KEY `idx_ai_todo_due` (`due_date`),
  KEY `idx_ai_todo_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作台待办表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_todo`
--

LOCK TABLES `ai_todo` WRITE;
/*!40000 ALTER TABLE `ai_todo` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_todo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_trunk_alarm`
--

DROP TABLE IF EXISTS `ai_trunk_alarm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_trunk_alarm` (
  `alarm_id` bigint NOT NULL AUTO_INCREMENT COMMENT '告警ID',
  `trunk_id` bigint DEFAULT NULL COMMENT '线路ID',
  `trunk_code` varchar(32) DEFAULT NULL COMMENT '线路编码',
  `carrier` char(2) DEFAULT NULL COMMENT '运营商',
  `alarm_type` varchar(32) DEFAULT NULL COMMENT '告警类型',
  `alarm_level` char(1) DEFAULT '1' COMMENT '告警级别 1提示 2一般 3严重 4紧急',
  `alarm_title` varchar(128) DEFAULT NULL COMMENT '告警标题',
  `alarm_content` varchar(1000) DEFAULT NULL COMMENT '告警内容',
  `metric_value` varchar(64) DEFAULT NULL COMMENT '指标值',
  `threshold_value` varchar(64) DEFAULT NULL COMMENT '阈值',
  `alarm_status` char(1) DEFAULT '0' COMMENT '状态 0未处理 1已确认 2已恢复 3已忽略',
  `alarm_time` datetime DEFAULT NULL COMMENT '告警时间',
  `recover_time` datetime DEFAULT NULL COMMENT '恢复时间',
  `handle_by` varchar(64) DEFAULT NULL COMMENT '处理人',
  `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
  `handle_remark` varchar(500) DEFAULT NULL COMMENT '处理说明',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`alarm_id`),
  KEY `idx_alarm_trunk_time` (`trunk_id`,`alarm_time`),
  KEY `idx_alarm_status` (`alarm_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线路告警';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_trunk_alarm`
--

LOCK TABLES `ai_trunk_alarm` WRITE;
/*!40000 ALTER TABLE `ai_trunk_alarm` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_trunk_alarm` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_trunk_metric`
--

DROP TABLE IF EXISTS `ai_trunk_metric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_trunk_metric` (
  `metric_id` bigint NOT NULL AUTO_INCREMENT COMMENT '统计ID',
  `trunk_id` bigint DEFAULT NULL COMMENT '线路ID',
  `trunk_code` varchar(32) DEFAULT NULL COMMENT '线路编码',
  `carrier` char(2) DEFAULT NULL COMMENT '运营商',
  `stat_time` datetime DEFAULT NULL COMMENT '统计时间',
  `stat_dimension` char(1) DEFAULT '1' COMMENT '统计维度 1=分钟 2=小时 3=天',
  `total_calls` int DEFAULT '0' COMMENT '呼叫总数',
  `connected_calls` int DEFAULT '0' COMMENT '接通数',
  `failed_calls` int DEFAULT '0' COMMENT '失败数',
  `connect_rate` decimal(5,2) DEFAULT '0.00' COMMENT '接通率(%)',
  `asr` decimal(5,2) DEFAULT '0.00' COMMENT '应答率(%)',
  `acd` int DEFAULT '0' COMMENT '平均通话时长(秒)',
  `total_duration` bigint DEFAULT '0' COMMENT '通话总时长(秒)',
  `avg_ring_duration` int DEFAULT '0' COMMENT '平均振铃时长(秒)',
  `max_concurrent` int DEFAULT '0' COMMENT '最大并发',
  `avg_mos` decimal(4,2) DEFAULT '0.00' COMMENT '平均MOS',
  `avg_packet_loss` decimal(5,2) DEFAULT '0.00' COMMENT '平均丢包率(%)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`metric_id`),
  KEY `idx_metric_trunk_time` (`trunk_id`,`stat_time`),
  KEY `idx_metric_time` (`stat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='线路质量统计';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_trunk_metric`
--

LOCK TABLES `ai_trunk_metric` WRITE;
/*!40000 ALTER TABLE `ai_trunk_metric` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_trunk_metric` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_unified_session`
--

DROP TABLE IF EXISTS `ai_unified_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_unified_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话索引ID',
  `profile_id` bigint DEFAULT NULL COMMENT '来电档案ID（可空，未建档渠道会话）',
  `caller_number` varchar(20) DEFAULT NULL COMMENT '手机号（时间线聚合兜底键）',
  `channel_type` varchar(20) NOT NULL COMMENT '渠道 PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB',
  `biz_type` varchar(10) NOT NULL COMMENT '业务类型 CALL/CHAT/VIDEO/IVR/MESSAGE/TICKET',
  `biz_id` varchar(64) NOT NULL COMMENT '业务主键（各业务表ID/会话号）',
  `biz_title` varchar(200) DEFAULT NULL COMMENT '业务摘要（时间线展示）',
  `start_time` datetime DEFAULT NULL COMMENT '会话开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '会话结束时间',
  `active_flag` char(1) DEFAULT '0' COMMENT '是否活跃 1活跃 0已结束（历史数据默认已结束）',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最近心跳时间（看门狗判活）',
  `expire_time` datetime DEFAULT NULL COMMENT '占用兜底过期时间（无结束事件时由看门狗回收）',
  `create_time` datetime DEFAULT NULL COMMENT '索引创建时间',
  `active_owner` varchar(96) GENERATED ALWAYS AS ((case when (`active_flag` = _utf8mb4'1') then concat(ifnull(`profile_id`,_utf8mb4'N'),_utf8mb4':',ifnull(`caller_number`,_utf8mb4'N'),_utf8mb4':',`channel_type`) end)) VIRTUAL,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_biz` (`biz_type`,`biz_id`),
  UNIQUE KEY `uk_active_owner` (`active_owner`),
  KEY `idx_profile_start` (`profile_id`,`start_time`),
  KEY `idx_caller_start` (`caller_number`,`start_time`),
  KEY `idx_active_heartbeat` (`active_flag`,`last_heartbeat`),
  KEY `idx_active_expire` (`active_flag`,`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨渠道统一会话索引（F6）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_unified_session`
--

LOCK TABLES `ai_unified_session` WRITE;
/*!40000 ALTER TABLE `ai_unified_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_unified_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_user_consultation`
--

DROP TABLE IF EXISTS `ai_user_consultation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_consultation` (
  `consultation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '咨询ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `category` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '问题分类',
  `content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '问题内容',
  `attachments` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '附件路径',
  `ai_answer` text COLLATE utf8mb4_general_ci COMMENT 'AI回答',
  `related_laws` text COLLATE utf8mb4_general_ci COMMENT '相关法条',
  `related_cases` text COLLATE utf8mb4_general_ci COMMENT '相关案例',
  `confidence` decimal(3,2) DEFAULT NULL COMMENT '置信度',
  `status` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'PROCESSING' COMMENT '状态（PROCESSING-处理中，COMPLETED-已完成，FAILED-失败）',
  `channel_type` varchar(20) COLLATE utf8mb4_general_ci DEFAULT 'H5' COMMENT '提交渠道（PHONE/WECHAT_MP/WECHAT_MINI/H5/WEB，对齐 F6 渠道字典）',
  `process_time` datetime DEFAULT NULL COMMENT '处理时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`consultation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户咨询表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_user_consultation`
--

LOCK TABLES `ai_user_consultation` WRITE;
/*!40000 ALTER TABLE `ai_user_consultation` DISABLE KEYS */;
INSERT INTO `ai_user_consultation` VALUES (1001,1,'marriage_family','请问离婚财产如何分割？','','根据《中华人民共和国民法典》第一千零八十七条规定，离婚时，夫妻的共同财产由双方协议处理；协议不成的，由人民法院根据财产的具体情况，按照照顾子女、女方和无过错方权益的原则判决。\n\n夫妻共同财产包括：\n1. 工资、奖金、劳务报酬\n2. 生产、经营、投资的收益\n3. 知识产权的收益\n4. 继承或者受赠的财产（遗嘱或赠与合同中确定只归一方的财产除外）\n5. 其他应当归共同所有的财产\n\n夫妻一方的财产包括：\n1. 一方的婚前财产\n2. 一方因受到人身损害获得的赔偿或者补偿\n3. 遗嘱或者赠与合同中确定只归一方的财产\n4. 一方专用的生活用品\n5. 其他应当归一方的财产','《中华人民共和国民法典》第一千零八十七条\n《最高人民法院关于适用<中华人民共和国民法典>婚姻家庭编的解释（一）》第二十五条至第三十一条','案例一：张某诉李某离婚纠纷案（案号：(2023)京01民终1234号）\n案例二：王某诉赵某离婚财产分割纠纷案（案号：(2023)沪01民终5678号）',0.92,'COMPLETED','H5','2026-11-16 22:43:17','admin','2026-11-16 22:43:17','',NULL,NULL),(1002,1,'labor_dispute','公司无故辞退员工，如何维权？','','根据《中华人民共和国劳动合同法》第四十八条规定，用人单位违反本法规定解除或者终止劳动合同，劳动者要求继续履行劳动合同的，用人单位应当继续履行；劳动者不要求继续履行劳动合同或者劳动合同已经不能继续履行的，用人单位应当依照本法第八十七条规定支付赔偿金。\n\n维权步骤：\n1. 收集证据：劳动合同、工资条、考勤记录、辞退通知等\n2. 协商解决：与用人单位协商，争取达成和解协议\n3. 申请劳动仲裁：向劳动争议仲裁委员会申请仲裁\n4. 提起诉讼：对仲裁裁决不服的，可以向人民法院提起诉讼\n\n赔偿标准：\n1. 经济补偿金：按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付\n2. 赔偿金：用人单位违法解除劳动合同的，应当向劳动者支付赔偿金，标准为经济补偿金的二倍','《中华人民共和国劳动合同法》第四十八条、第八十七条\n《中华人民共和国劳动争议调解仲裁法》','案例一：张某诉某科技公司违法解除劳动合同案（案号：(2023)粤01民终3456号）\n案例二：李某诉某制造厂劳动争议案（案号：(2023)浙01民终7890号）',0.89,'COMPLETED','H5','2026-11-16 22:43:17','admin','2026-11-16 22:43:17','',NULL,NULL);
/*!40000 ALTER TABLE `ai_user_consultation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_user_evaluation`
--

DROP TABLE IF EXISTS `ai_user_evaluation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_user_evaluation` (
  `evaluation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `consultation_id` bigint NOT NULL COMMENT '咨询ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `overall_rating` int NOT NULL COMMENT '总体评价（1-5分）',
  `professionalism_rating` int NOT NULL COMMENT '专业度评价（1-5分）',
  `responsiveness_rating` int NOT NULL COMMENT '响应速度评价（1-5分）',
  `quality_rating` int NOT NULL COMMENT '解答质量评价（1-5分）',
  `feedback` text COLLATE utf8mb4_general_ci COMMENT '文字反馈',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`evaluation_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户评价表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_user_evaluation`
--

LOCK TABLES `ai_user_evaluation` WRITE;
/*!40000 ALTER TABLE `ai_user_evaluation` DISABLE KEYS */;
INSERT INTO `ai_user_evaluation` VALUES (1001,1001,1,5,5,5,5,'回答非常专业，解决了我的问题，感谢！','admin','2026-11-16 22:43:17','',NULL,NULL),(1002,1002,1,4,4,5,4,'回答很详细，但希望能有更多具体案例参考','admin','2026-11-16 22:43:17','',NULL,NULL);
/*!40000 ALTER TABLE `ai_user_evaluation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_vad_shadow_daily`
--

DROP TABLE IF EXISTS `ai_vad_shadow_daily`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_vad_shadow_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `stat_date` date NOT NULL COMMENT '统计日期',
  `total_frames` bigint NOT NULL DEFAULT '0' COMMENT '总帧数',
  `both_voice_frames` bigint NOT NULL DEFAULT '0' COMMENT '双判语音帧数',
  `only_legacy_frames` bigint NOT NULL DEFAULT '0' COMMENT '仅旧VAD判语音帧数（潜在漏报）',
  `only_silero_frames` bigint NOT NULL DEFAULT '0' COMMENT '仅Silero判语音帧数（潜在误报）',
  `both_silent_frames` bigint NOT NULL DEFAULT '0' COMMENT '双判静默帧数',
  `legacy_start_count` int NOT NULL DEFAULT '0' COMMENT '旧VAD语音起始次数',
  `silero_start_count` int NOT NULL DEFAULT '0' COMMENT 'Silero语音起始次数',
  `legacy_end_count` int NOT NULL DEFAULT '0' COMMENT '旧VAD语音结束次数',
  `silero_end_count` int NOT NULL DEFAULT '0' COMMENT 'Silero语音结束次数',
  `false_alarm_rate` decimal(8,6) DEFAULT NULL COMMENT '误打断率（仅Silero判语音/总帧数）',
  `miss_rate` decimal(8,6) DEFAULT NULL COMMENT '漏打断率（仅旧VAD判语音/总帧数）',
  `avg_latency_diff_ms` decimal(10,2) DEFAULT NULL COMMENT '平均打断响应延迟差（ms，Silero-旧VAD）',
  `latency_diff_count` bigint NOT NULL DEFAULT '0' COMMENT '延迟差样本数（多实例加权合并均值用）',
  `active_sessions` int NOT NULL DEFAULT '0' COMMENT '当日活跃会话数（已结束且有音频帧）',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stat_date` (`stat_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VAD影子对比日统计（P2-12方案A）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_vad_shadow_daily`
--

LOCK TABLES `ai_vad_shadow_daily` WRITE;
/*!40000 ALTER TABLE `ai_vad_shadow_daily` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_vad_shadow_daily` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_vad_shadow_session`
--

DROP TABLE IF EXISTS `ai_vad_shadow_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_vad_shadow_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `session_id` varchar(64) NOT NULL COMMENT '语音会话ID',
  `conn_id` varchar(64) DEFAULT NULL COMMENT 'WebSocket连接ID',
  `sample_rate` int NOT NULL DEFAULT '16000' COMMENT '采样率',
  `mock_mode` char(1) NOT NULL DEFAULT '0' COMMENT '是否Mock模式 0否(ONNX) 1是',
  `total_frames` int NOT NULL DEFAULT '0' COMMENT '本会话总帧数',
  `both_voice_frames` int NOT NULL DEFAULT '0' COMMENT '双判语音帧数',
  `only_legacy_frames` int NOT NULL DEFAULT '0' COMMENT '仅旧VAD判语音帧数',
  `only_silero_frames` int NOT NULL DEFAULT '0' COMMENT '仅Silero判语音帧数',
  `false_alarm_rate` decimal(8,6) DEFAULT NULL COMMENT '会话级误打断率',
  `miss_rate` decimal(8,6) DEFAULT NULL COMMENT '会话级漏打断率',
  `avg_latency_diff_ms` decimal(10,2) DEFAULT NULL COMMENT '会话级平均延迟差(ms)',
  `legacy_start_count` int NOT NULL DEFAULT '0' COMMENT '旧VAD起始次数',
  `silero_start_count` int NOT NULL DEFAULT '0' COMMENT 'Silero起始次数',
  `create_time` datetime DEFAULT NULL COMMENT '会话开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '会话结束时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VAD影子会话明细（P2-12方案A，抽样审计用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_vad_shadow_session`
--

LOCK TABLES `ai_vad_shadow_session` WRITE;
/*!40000 ALTER TABLE `ai_vad_shadow_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_vad_shadow_session` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_video_consult`
--

DROP TABLE IF EXISTS `ai_video_consult`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_video_consult` (
  `consult_id` bigint NOT NULL AUTO_INCREMENT COMMENT '视频咨询ID',
  `consult_no` varchar(64) DEFAULT NULL COMMENT '咨询编号',
  `customer_name` varchar(64) DEFAULT NULL COMMENT '客户姓名',
  `customer_phone` varchar(20) DEFAULT NULL COMMENT '客户电话',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席ID',
  `agent_name` varchar(64) DEFAULT NULL COMMENT '坐席名称',
  `room_no` varchar(64) DEFAULT NULL COMMENT '房间号',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间',
  `duration` int DEFAULT '0' COMMENT '通话时长(秒)',
  `status` char(1) DEFAULT '0' COMMENT '状态（0等待中 1进行中 2已结束 3已取消）',
  `satisfaction` int DEFAULT NULL COMMENT '满意度(1-5)',
  `recording_url` varchar(255) DEFAULT NULL COMMENT '录像地址',
  `identity_verify` char(1) DEFAULT '0' COMMENT '是否已身份核验（0否 1是）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`consult_id`),
  KEY `idx_avc_no` (`consult_no`),
  KEY `idx_avc_agent` (`agent_id`),
  KEY `idx_avc_status` (`status`),
  KEY `idx_avc_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频咨询表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_video_consult`
--

LOCK TABLES `ai_video_consult` WRITE;
/*!40000 ALTER TABLE `ai_video_consult` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_video_consult` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_video_consult_log`
--

DROP TABLE IF EXISTS `ai_video_consult_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_video_consult_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `consult_id` bigint NOT NULL COMMENT '视频咨询ID',
  `event_type` varchar(32) DEFAULT NULL COMMENT '事件类型（join离开 leave加入 share屏幕共享 error异常 disconnect断开）',
  `event_content` varchar(255) DEFAULT NULL COMMENT '事件描述',
  `event_time` datetime DEFAULT NULL COMMENT '事件时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_avcl_consult` (`consult_id`),
  KEY `idx_avcl_time` (`event_time`),
  KEY `idx_ai_video_consult_log_ct` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频咨询事件日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_video_consult_log`
--

LOCK TABLES `ai_video_consult_log` WRITE;
/*!40000 ALTER TABLE `ai_video_consult_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_video_consult_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_work_shift`
--

DROP TABLE IF EXISTS `ai_work_shift`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_work_shift` (
  `shift_id` bigint NOT NULL AUTO_INCREMENT COMMENT '班次ID',
  `shift_name` varchar(50) NOT NULL COMMENT '班次名称(如早班/晚班)',
  `start_time` time NOT NULL COMMENT '上班时间',
  `end_time` time NOT NULL COMMENT '下班时间',
  `is_overnight` tinyint DEFAULT '0' COMMENT '是否跨天 0否 1是',
  `break_start` time DEFAULT NULL COMMENT '休息开始时间',
  `break_end` time DEFAULT NULL COMMENT '休息结束时间',
  `status` tinyint DEFAULT '1' COMMENT '状态 0停用 1启用',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`shift_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='班次定义';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_work_shift`
--

LOCK TABLES `ai_work_shift` WRITE;
/*!40000 ALTER TABLE `ai_work_shift` DISABLE KEYS */;
INSERT INTO `ai_work_shift` VALUES (1,'早班','08:00:00','18:00:00',0,'12:00:00','14:00:00',1,'admin','2026-11-16 22:43:27','',NULL,'上午 08:00-12:00，下午 14:00-18:00'),(2,'晚班','13:00:00','22:00:00',0,'18:00:00','19:00:00',1,'admin','2026-11-16 22:43:27','',NULL,'下午 13:00-18:00，晚间 19:00-22:00');
/*!40000 ALTER TABLE `ai_work_shift` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table`
--

DROP TABLE IF EXISTS `gen_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table` (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table`
--

LOCK TABLES `gen_table` WRITE;
/*!40000 ALTER TABLE `gen_table` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `gen_table_column`
--

DROP TABLE IF EXISTS `gen_table_column`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `gen_table_column` (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `sort` int DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码生成业务表字段';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `gen_table_column`
--

LOCK TABLES `gen_table_column` WRITE;
/*!40000 ALTER TABLE `gen_table_column` DISABLE KEYS */;
/*!40000 ALTER TABLE `gen_table_column` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_blob_triggers`
--

DROP TABLE IF EXISTS `qrtz_blob_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_blob_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Blob类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_blob_triggers`
--

LOCK TABLES `qrtz_blob_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_blob_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_blob_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_calendars`
--

DROP TABLE IF EXISTS `qrtz_calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_calendars` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`,`calendar_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='日历信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_calendars`
--

LOCK TABLES `qrtz_calendars` WRITE;
/*!40000 ALTER TABLE `qrtz_calendars` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_cron_triggers`
--

DROP TABLE IF EXISTS `qrtz_cron_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_cron_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Cron类型的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_cron_triggers`
--

LOCK TABLES `qrtz_cron_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_cron_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_cron_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_fired_triggers`
--

DROP TABLE IF EXISTS `qrtz_fired_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_fired_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`,`entry_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='已触发的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_fired_triggers`
--

LOCK TABLES `qrtz_fired_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_fired_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_fired_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_job_details`
--

DROP TABLE IF EXISTS `qrtz_job_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_job_details` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`job_name`,`job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_job_details`
--

LOCK TABLES `qrtz_job_details` WRITE;
/*!40000 ALTER TABLE `qrtz_job_details` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_job_details` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_locks`
--

DROP TABLE IF EXISTS `qrtz_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_locks` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`,`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='存储的悲观锁信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_locks`
--

LOCK TABLES `qrtz_locks` WRITE;
/*!40000 ALTER TABLE `qrtz_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_paused_trigger_grps`
--

DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_paused_trigger_grps` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`,`trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='暂停的触发器表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_paused_trigger_grps`
--

LOCK TABLES `qrtz_paused_trigger_grps` WRITE;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_paused_trigger_grps` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_scheduler_state`
--

DROP TABLE IF EXISTS `qrtz_scheduler_state`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_scheduler_state` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`,`instance_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度器状态表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_scheduler_state`
--

LOCK TABLES `qrtz_scheduler_state` WRITE;
/*!40000 ALTER TABLE `qrtz_scheduler_state` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_scheduler_state` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simple_triggers`
--

DROP TABLE IF EXISTS `qrtz_simple_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simple_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='简单触发器的信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simple_triggers`
--

LOCK TABLES `qrtz_simple_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simple_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simple_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_simprop_triggers`
--

DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_simprop_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13,4) DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='同步机制的行锁表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_simprop_triggers`
--

LOCK TABLES `qrtz_simprop_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_simprop_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qrtz_triggers`
--

DROP TABLE IF EXISTS `qrtz_triggers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qrtz_triggers` (
  `sched_name` varchar(120) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`,`trigger_name`,`trigger_group`),
  KEY `sched_name` (`sched_name`,`job_name`,`job_group`),
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='触发器详细信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qrtz_triggers`
--

LOCK TABLES `qrtz_triggers` WRITE;
/*!40000 ALTER TABLE `qrtz_triggers` DISABLE KEYS */;
/*!40000 ALTER TABLE `qrtz_triggers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='参数配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
INSERT INTO `sys_config` VALUES (1,'主框架页-默认皮肤样式名称','sys.index.skinName','skin-blue','Y','admin','2026-11-16 22:43:16','',NULL,'蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow'),(2,'用户管理-账号初始密码','sys.user.initPassword','123456','Y','admin','2026-11-16 22:43:16','',NULL,'初始化密码 123456'),(3,'主框架页-侧边栏主题','sys.index.sideTheme','theme-dark','Y','admin','2026-11-16 22:43:16','',NULL,'深色主题theme-dark，浅色主题theme-light'),(4,'账号自助-验证码开关','sys.account.captchaEnabled','true','Y','admin','2026-11-16 22:43:16','',NULL,'是否开启验证码功能（true开启，false关闭）'),(5,'账号自助-是否开启用户注册功能','sys.account.registerUser','false','Y','admin','2026-11-16 22:43:16','',NULL,'是否开启注册用户功能（true开启，false关闭）'),(6,'用户登录-黑名单列表','sys.login.blackIPList','','Y','admin','2026-11-16 22:43:16','',NULL,'设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）'),(7,'用户管理-初始密码修改策略','sys.account.initPasswordModify','1','Y','admin','2026-11-16 22:43:16','',NULL,'0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框'),(8,'用户管理-账号密码更新周期','sys.account.passwordValidateDays','0','Y','admin','2026-11-16 22:43:16','',NULL,'密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dept`
--

DROP TABLE IF EXISTS `sys_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门id',
  `ancestors` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=200 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

LOCK TABLES `sys_dept` WRITE;
/*!40000 ALTER TABLE `sys_dept` DISABLE KEYS */;
INSERT INTO `sys_dept` VALUES (100,0,'0','若依科技',0,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(101,100,'0,100','深圳总公司',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(102,100,'0,100','长沙分公司',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(103,101,'0,100,101','研发部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(104,101,'0,100,101','市场部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(105,101,'0,100,101','测试部门',3,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(106,101,'0,100,101','财务部门',4,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(107,101,'0,100,101','运维部门',5,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(108,102,'0,100,102','市场部门',1,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL),(109,102,'0,100,102','财务部门',2,'若依','15888888888','ry@qq.com','0','0','admin','2026-11-16 22:43:16','',NULL);
/*!40000 ALTER TABLE `sys_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_data`
--

DROP TABLE IF EXISTS `sys_dict_data`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int DEFAULT '0' COMMENT '字典排序',
  `dict_label` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) COLLATE utf8mb4_general_ci DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1073 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典数据表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

LOCK TABLES `sys_dict_data` WRITE;
/*!40000 ALTER TABLE `sys_dict_data` DISABLE KEYS */;
INSERT INTO `sys_dict_data` VALUES (1,1,'男','0','sys_user_sex','','','Y','0','admin','2026-11-16 22:43:16','',NULL,'性别男'),(2,2,'女','1','sys_user_sex','','','N','0','admin','2026-11-16 22:43:16','',NULL,'性别女'),(3,3,'未知','2','sys_user_sex','','','N','0','admin','2026-11-16 22:43:16','',NULL,'性别未知'),(4,1,'显示','0','sys_show_hide','','primary','Y','0','admin','2026-11-16 22:43:16','',NULL,'显示菜单'),(5,2,'隐藏','1','sys_show_hide','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'隐藏菜单'),(6,1,'正常','0','sys_normal_disable','','primary','Y','0','admin','2026-11-16 22:43:16','',NULL,'正常状态'),(7,2,'停用','1','sys_normal_disable','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'停用状态'),(8,1,'正常','0','sys_job_status','','primary','Y','0','admin','2026-11-16 22:43:16','',NULL,'正常状态'),(9,2,'暂停','1','sys_job_status','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'停用状态'),(10,1,'默认','DEFAULT','sys_job_group','','','Y','0','admin','2026-11-16 22:43:16','',NULL,'默认分组'),(11,2,'系统','SYSTEM','sys_job_group','','','N','0','admin','2026-11-16 22:43:16','',NULL,'系统分组'),(12,1,'是','Y','sys_yes_no','','primary','Y','0','admin','2026-11-16 22:43:16','',NULL,'系统默认是'),(13,2,'否','N','sys_yes_no','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'系统默认否'),(14,1,'通知','1','sys_notice_type','','warning','Y','0','admin','2026-11-16 22:43:16','',NULL,'通知'),(15,2,'公告','2','sys_notice_type','','success','N','0','admin','2026-11-16 22:43:16','',NULL,'公告'),(16,1,'正常','0','sys_notice_status','','primary','Y','0','admin','2026-11-16 22:43:16','',NULL,'正常状态'),(17,2,'关闭','1','sys_notice_status','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'关闭状态'),(18,99,'其他','0','sys_oper_type','','info','N','0','admin','2026-11-16 22:43:16','',NULL,'其他操作'),(19,1,'新增','1','sys_oper_type','','info','N','0','admin','2026-11-16 22:43:16','',NULL,'新增操作'),(20,2,'修改','2','sys_oper_type','','info','N','0','admin','2026-11-16 22:43:16','',NULL,'修改操作'),(21,3,'删除','3','sys_oper_type','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'删除操作'),(22,4,'授权','4','sys_oper_type','','primary','N','0','admin','2026-11-16 22:43:16','',NULL,'授权操作'),(23,5,'导出','5','sys_oper_type','','warning','N','0','admin','2026-11-16 22:43:16','',NULL,'导出操作'),(24,6,'导入','6','sys_oper_type','','warning','N','0','admin','2026-11-16 22:43:16','',NULL,'导入操作'),(25,7,'强退','7','sys_oper_type','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'强退操作'),(26,8,'生成代码','8','sys_oper_type','','warning','N','0','admin','2026-11-16 22:43:16','',NULL,'生成操作'),(27,9,'清空数据','9','sys_oper_type','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'清空操作'),(28,1,'成功','0','sys_common_status','','primary','N','0','admin','2026-11-16 22:43:16','',NULL,'正常状态'),(29,2,'失败','1','sys_common_status','','danger','N','0','admin','2026-11-16 22:43:16','',NULL,'停用状态'),(100,1,'OpenAI GPT','openai','ai_model_type','','primary','Y','0','admin','2026-11-16 22:43:18','',NULL,'OpenAI GPT模型'),(101,2,'百度文心一言','wenxin','ai_model_type','','success','N','0','admin','2026-11-16 22:43:18','',NULL,'百度文心一言模型'),(102,3,'阿里通义千问','tongyi','ai_model_type','','info','N','0','admin','2026-11-16 22:43:18','',NULL,'阿里通义千问模型'),(103,4,'讯飞星火','spark','ai_model_type','','warning','N','0','admin','2026-11-16 22:43:18','',NULL,'讯飞星火模型'),(104,5,'智谱AI','zhipu','ai_model_type','','danger','N','0','admin','2026-11-16 22:43:18','',NULL,'智谱AI模型'),(105,1,'法律咨询','legal_consultation','ai_prompt_type','','primary','Y','0','admin','2026-11-16 22:43:18','',NULL,'法律咨询提示词'),(106,2,'文书生成','document_generation','ai_prompt_type','','success','N','0','admin','2026-11-16 22:43:18','',NULL,'文书生成提示词'),(107,3,'案例分析','case_analysis','ai_prompt_type','','info','N','0','admin','2026-11-16 22:43:18','',NULL,'案例分析提示词'),(108,4,'法律研究','legal_research','ai_prompt_type','','warning','N','0','admin','2026-11-16 22:43:18','',NULL,'法律研究提示词'),(109,5,'通用','general','ai_prompt_type','','default','N','0','admin','2026-11-16 22:43:18','',NULL,'通用提示词'),(110,1,'通用','general','ai_scene_type','','','Y','0','admin','2026-11-16 22:43:18','',NULL,'通用场景'),(111,2,'民事','civil','ai_scene_type','','','N','0','admin','2026-11-16 22:43:18','',NULL,'民事场景'),(112,3,'刑事','criminal','ai_scene_type','','','N','0','admin','2026-11-16 22:43:18','',NULL,'刑事场景'),(113,4,'商事','commercial','ai_scene_type','','','N','0','admin','2026-11-16 22:43:18','',NULL,'商事场景'),(114,5,'行政','administrative','ai_scene_type','','','N','0','admin','2026-11-16 22:43:18','',NULL,'行政场景'),(115,1,'婚姻家庭','marriage_family','consultation_category','','','Y','0','admin','2026-11-16 22:43:18','',NULL,'婚姻家庭咨询'),(116,2,'劳动纠纷','labor_dispute','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'劳动纠纷咨询'),(117,3,'合同纠纷','contract_dispute','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'合同纠纷咨询'),(118,4,'房产纠纷','property_dispute','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'房产纠纷咨询'),(119,5,'债务纠纷','debt_dispute','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'债务纠纷咨询'),(120,6,'侵权责任','tort_liability','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'侵权责任咨询'),(121,7,'知识产权','intellectual_property','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'知识产权咨询'),(122,8,'刑事辩护','criminal_defense','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'刑事辩护咨询'),(123,9,'行政诉讼','administrative_litigation','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'行政诉讼咨询'),(124,10,'公司法务','corporate_legal','consultation_category','','','N','0','admin','2026-11-16 22:43:18','',NULL,'公司法务咨询'),(1000,1,'待审核','0','lawyers_audit_status','','warning','N','0','admin','2026-11-16 22:43:19','',NULL,'待审核状态'),(1001,2,'审核通过','1','lawyers_audit_status','','success','N','0','admin','2026-11-16 22:43:19','',NULL,'审核通过状态'),(1002,3,'审核不通过','2','lawyers_audit_status','','danger','N','0','admin','2026-11-16 22:43:19','',NULL,'审核不通过状态'),(1010,1,'OpenAI GPT','openai','lawyers_model_type','','primary','N','0','admin','2026-11-16 22:43:19','',NULL,'OpenAI GPT模型'),(1011,2,'百度文心一言','wenxin','lawyers_model_type','','success','N','0','admin','2026-11-16 22:43:19','',NULL,'百度文心一言模型'),(1012,3,'阿里通义千问','tongyi','lawyers_model_type','','info','N','0','admin','2026-11-16 22:43:19','',NULL,'阿里通义千问模型'),(1013,4,'讯飞星火','spark','lawyers_model_type','','warning','N','0','admin','2026-11-16 22:43:19','',NULL,'讯飞星火模型'),(1014,5,'智谱AI','zhipu','lawyers_model_type','','danger','N','0','admin','2026-11-16 22:43:19','',NULL,'智谱AI模型'),(1020,1,'法律咨询','legal_consultation','lawyers_prompt_type','','primary','N','0','admin','2026-11-16 22:43:19','',NULL,'法律咨询提示词'),(1021,2,'文书生成','document_generation','lawyers_prompt_type','','success','N','0','admin','2026-11-16 22:43:19','',NULL,'文书生成提示词'),(1022,3,'案例分析','case_analysis','lawyers_prompt_type','','info','N','0','admin','2026-11-16 22:43:19','',NULL,'案例分析提示词'),(1023,4,'法律研究','legal_research','lawyers_prompt_type','','warning','N','0','admin','2026-11-16 22:43:19','',NULL,'法律研究提示词'),(1024,5,'通用','general','lawyers_prompt_type','','default','N','0','admin','2026-11-16 22:43:19','',NULL,'通用提示词'),(1025,1,'普通话','zh-CN','ai_language_pref','','primary','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1026,2,'粤语','yue-CN','ai_language_pref','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1028,1,'电话','PHONE','ai_channel_type','','primary','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1029,2,'微信公众号','WECHAT_MP','ai_channel_type','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1030,3,'微信小程序','WECHAT_MINI','ai_channel_type','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1031,4,'H5','H5','ai_channel_type','','info','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1032,5,'网站','WEB','ai_channel_type','','info','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1035,1,'法律援助','LEGAL_AID','ai_external_type','','primary','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1036,2,'人民调解','MEDIATION','ai_external_type','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1037,3,'公证','NOTARY','ai_external_type','','info','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1038,4,'司法鉴定','FORENSIC','ai_external_type','','warning','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1039,5,'仲裁','ARBITRATION','ai_external_type','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1040,6,'12345政务热线','HOTLINE_12345','ai_external_type','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1042,1,'待受理','PENDING','ai_external_status','','info','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1043,2,'已受理','ACCEPTED','ai_external_status','','primary','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1044,3,'办理中','PROCESSING','ai_external_status','','warning','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1045,4,'已办结','DONE','ai_external_status','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1046,5,'不予受理','REJECTED','ai_external_status','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1047,6,'失败','FAILED','ai_external_status','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1049,0,'处理中','0','ai_transfer_status','','warning','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1050,1,'成功','1','ai_transfer_status','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1051,2,'失败','2','ai_transfer_status','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1052,3,'人工处理','3','ai_transfer_status','','info','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1056,1,'接口对接','API','ai_org_access_mode','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1057,2,'文件交换','FILE','ai_org_access_mode','','warning','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1058,3,'人工台账','MANUAL','ai_org_access_mode','','info','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1059,1,'现行有效','1','ai_knowledge_valid','','success','Y','0','admin','2026-11-16 22:43:32','',NULL,''),(1060,2,'已修订','2','ai_knowledge_valid','','warning','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1061,3,'已失效','3','ai_knowledge_valid','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1062,1,'提交审核','SUBMIT','ai_knowledge_audit_action','','info','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1063,2,'审核通过','APPROVE','ai_knowledge_audit_action','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1064,3,'审核驳回','REJECT','ai_knowledge_audit_action','','danger','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1065,4,'发布','PUBLISH','ai_knowledge_audit_action','','success','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1066,5,'下线','OFFLINE','ai_knowledge_audit_action','','warning','N','0','admin','2026-11-16 22:43:32','',NULL,''),(1067,6,'效力变更','VALID_STATUS','ai_knowledge_audit_action','','primary','N','0','admin','2026-11-26 00:00:00','',NULL,'N3 法规效力状态变更留痕'),(1069,1,'号码匹配','PHONE','ai_hotspot_match_type','','primary','Y','0','admin','2026-11-16 22:43:33','',NULL,'按主叫号码精确匹配'),(1070,2,'关键词匹配','KEYWORD','ai_hotspot_match_type','','info','N','0','admin','2026-11-16 22:43:33','',NULL,'按关键词匹配'),(1071,1,'置底降权','PRIORITY','ai_hotspot_action','','warning','Y','0','admin','2026-11-16 22:43:33','',NULL,'排队沉到队尾，仍可接听'),(1072,2,'直接拦截','REJECT','ai_hotspot_action','','danger','N','0','admin','2026-11-16 22:43:33','',NULL,'直接挂断/跳过');
/*!40000 ALTER TABLE `sys_dict_data` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '字典类型',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (1,'用户性别','sys_user_sex','0','admin','2026-11-16 22:43:16','',NULL,'用户性别列表'),(2,'菜单状态','sys_show_hide','0','admin','2026-11-16 22:43:16','',NULL,'菜单状态列表'),(3,'系统开关','sys_normal_disable','0','admin','2026-11-16 22:43:16','',NULL,'系统开关列表'),(4,'任务状态','sys_job_status','0','admin','2026-11-16 22:43:16','',NULL,'任务状态列表'),(5,'任务分组','sys_job_group','0','admin','2026-11-16 22:43:16','',NULL,'任务分组列表'),(6,'系统是否','sys_yes_no','0','admin','2026-11-16 22:43:16','',NULL,'系统是否列表'),(7,'通知类型','sys_notice_type','0','admin','2026-11-16 22:43:16','',NULL,'通知类型列表'),(8,'通知状态','sys_notice_status','0','admin','2026-11-16 22:43:16','',NULL,'通知状态列表'),(9,'操作类型','sys_oper_type','0','admin','2026-11-16 22:43:16','',NULL,'操作类型列表'),(10,'系统状态','sys_common_status','0','admin','2026-11-16 22:43:16','',NULL,'登录状态列表'),(100,'AI模型类型','ai_model_type','0','admin','2026-11-16 22:43:18','',NULL,'AI模型类型字典'),(101,'AI提示词类型','ai_prompt_type','0','admin','2026-11-16 22:43:18','',NULL,'AI提示词类型字典'),(102,'AI场景类型','ai_scene_type','0','admin','2026-11-16 22:43:18','',NULL,'AI场景类型字典'),(103,'咨询分类','consultation_category','0','admin','2026-11-16 22:43:18','',NULL,'咨询分类字典'),(104,'语种偏好','ai_language_pref','0','admin','2026-11-16 22:43:32','',NULL,'F1 普通话/粤语'),(105,'接入渠道类型','ai_channel_type','0','admin','2026-11-16 22:43:32','',NULL,'F6 多渠道身份'),(106,'司法行政业务条线','ai_external_type','0','admin','2026-11-16 22:43:32','',NULL,'F3 法援/调解/公证/鉴定/仲裁/12345'),(107,'外部协同状态','ai_external_status','0','admin','2026-11-16 22:43:32','',NULL,'F3 外部工单状态'),(108,'转办流水状态','ai_transfer_status','0','admin','2026-11-16 22:43:32','',NULL,'F3'),(109,'机构对接方式','ai_org_access_mode','0','admin','2026-11-16 22:43:32','',NULL,'F3'),(110,'法条效力状态','ai_knowledge_valid','0','admin','2026-11-16 22:43:32','',NULL,'F7'),(111,'知识审核动作','ai_knowledge_audit_action','0','admin','2026-11-16 22:43:32','',NULL,'F7'),(112,'置底匹配类型','ai_hotspot_match_type','0','admin','2026-11-16 22:43:33','',NULL,'高频置底规则匹配类型'),(113,'置底处置动作','ai_hotspot_action','0','admin','2026-11-16 22:43:33','',NULL,'高频置底处置动作');
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) COLLATE utf8mb4_general_ci DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`,`job_name`,`job_group`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job`
--

LOCK TABLES `sys_job` WRITE;
/*!40000 ALTER TABLE `sys_job` DISABLE KEYS */;
INSERT INTO `sys_job` VALUES (1,'系统默认（无参）','DEFAULT','ryTask.ryNoParams','0/10 * * * * ?','3','1','1','admin','2026-11-16 22:43:16','',NULL,''),(2,'系统默认（有参）','DEFAULT','ryTask.ryParams(\'ry\')','0/15 * * * * ?','3','1','1','admin','2026-11-16 22:43:16','',NULL,''),(3,'系统默认（多参）','DEFAULT','ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)','0/20 * * * * ?','3','1','1','admin','2026-11-16 22:43:16','',NULL,'');
/*!40000 ALTER TABLE `sys_job` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_job_log`
--

DROP TABLE IF EXISTS `sys_job_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job_log` (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '日志信息',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '异常信息',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时任务调度日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_job_log`
--

LOCK TABLES `sys_job_log` WRITE;
/*!40000 ALTER TABLE `sys_job_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_job_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_logininfor`
--

DROP TABLE IF EXISTS `sys_logininfor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_logininfor` (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作系统',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '提示消息',
  `login_time` datetime DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`),
  KEY `idx_sys_logininfor_s` (`status`),
  KEY `idx_sys_logininfor_lt` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统访问记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_logininfor`
--

LOCK TABLES `sys_logininfor` WRITE;
/*!40000 ALTER TABLE `sys_logininfor` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_logininfor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_menu`
--

DROP TABLE IF EXISTS `sys_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '路由名称',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3987 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

LOCK TABLES `sys_menu` WRITE;
/*!40000 ALTER TABLE `sys_menu` DISABLE KEYS */;
INSERT INTO `sys_menu` VALUES (1,'系统管理',0,1,'system',NULL,'','',1,0,'M','0','0','','system','admin','2026-11-16 22:43:16','',NULL,'系统管理目录'),(2,'系统监控',0,2,'monitor',NULL,'','',1,0,'M','0','0','','monitor','admin','2026-11-16 22:43:16','',NULL,'系统监控目录'),(3,'系统工具',0,3,'tool',NULL,'','',1,0,'M','0','0','','tool','admin','2026-11-16 22:43:16','',NULL,'系统工具目录'),(4,'若依官网',0,4,'http://ruoyi.vip',NULL,'','',0,0,'M','0','0','','guide','admin','2026-11-16 22:43:16','',NULL,'若依官网地址'),(100,'用户管理',1,1,'user','system/user/index','','',1,0,'C','0','0','system:user:list','user','admin','2026-11-16 22:43:16','',NULL,'用户管理菜单'),(101,'角色管理',1,2,'role','system/role/index','','',1,0,'C','0','0','system:role:list','peoples','admin','2026-11-16 22:43:16','',NULL,'角色管理菜单'),(102,'菜单管理',1,3,'menu','system/menu/index','','',1,0,'C','0','0','system:menu:list','tree-table','admin','2026-11-16 22:43:16','',NULL,'菜单管理菜单'),(103,'部门管理',1,4,'dept','system/dept/index','','',1,0,'C','0','0','system:dept:list','tree','admin','2026-11-16 22:43:16','',NULL,'部门管理菜单'),(104,'岗位管理',1,5,'post','system/post/index','','',1,0,'C','0','0','system:post:list','post','admin','2026-11-16 22:43:16','',NULL,'岗位管理菜单'),(105,'字典管理',1,6,'dict','system/dict/index','','',1,0,'C','0','0','system:dict:list','dict','admin','2026-11-16 22:43:16','',NULL,'字典管理菜单'),(106,'参数设置',1,7,'config','system/config/index','','',1,0,'C','0','0','system:config:list','edit','admin','2026-11-16 22:43:16','',NULL,'参数设置菜单'),(107,'通知公告',1,8,'notice','system/notice/index','','',1,0,'C','0','0','system:notice:list','message','admin','2026-11-16 22:43:16','',NULL,'通知公告菜单'),(108,'日志管理',1,9,'log','','','',1,0,'M','0','0','','log','admin','2026-11-16 22:43:16','',NULL,'日志管理菜单'),(109,'在线用户',2,1,'online','monitor/online/index','','',1,0,'C','0','0','monitor:online:list','online','admin','2026-11-16 22:43:16','',NULL,'在线用户菜单'),(110,'定时任务',2,2,'job','monitor/job/index','','',1,0,'C','0','0','monitor:job:list','job','admin','2026-11-16 22:43:16','',NULL,'定时任务菜单'),(111,'数据监控',2,3,'druid','monitor/druid/index','','',1,0,'C','0','0','monitor:druid:list','druid','admin','2026-11-16 22:43:16','',NULL,'数据监控菜单'),(112,'服务监控',2,4,'server','monitor/server/index','','',1,0,'C','0','0','monitor:server:list','server','admin','2026-11-16 22:43:16','',NULL,'服务监控菜单'),(113,'缓存监控',2,5,'cache','monitor/cache/index','','',1,0,'C','0','0','monitor:cache:list','redis','admin','2026-11-16 22:43:16','',NULL,'缓存监控菜单'),(114,'缓存列表',2,6,'cacheList','monitor/cache/list','','',1,0,'C','0','0','monitor:cache:list','redis-list','admin','2026-11-16 22:43:16','',NULL,'缓存列表菜单'),(115,'表单构建',3,1,'build','tool/build/index','','',1,0,'C','0','0','tool:build:list','build','admin','2026-11-16 22:43:16','',NULL,'表单构建菜单'),(116,'代码生成',3,2,'gen','tool/gen/index','','',1,0,'C','0','0','tool:gen:list','code','admin','2026-11-16 22:43:16','',NULL,'代码生成菜单'),(117,'系统接口',3,3,'swagger','tool/swagger/index','','',1,0,'C','0','0','tool:swagger:list','swagger','admin','2026-11-16 22:43:16','',NULL,'系统接口菜单'),(500,'操作日志',108,1,'operlog','monitor/operlog/index','','',1,0,'C','0','0','monitor:operlog:list','form','admin','2026-11-16 22:43:16','',NULL,'操作日志菜单'),(501,'登录日志',108,2,'logininfor','monitor/logininfor/index','','',1,0,'C','0','0','monitor:logininfor:list','logininfor','admin','2026-11-16 22:43:16','',NULL,'登录日志菜单'),(1000,'用户查询',100,1,'','','','',1,0,'F','0','0','system:user:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1001,'用户新增',100,2,'','','','',1,0,'F','0','0','system:user:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1002,'用户修改',100,3,'','','','',1,0,'F','0','0','system:user:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1003,'用户删除',100,4,'','','','',1,0,'F','0','0','system:user:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1004,'用户导出',100,5,'','','','',1,0,'F','0','0','system:user:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1005,'用户导入',100,6,'','','','',1,0,'F','0','0','system:user:import','#','admin','2026-11-16 22:43:16','',NULL,''),(1006,'重置密码',100,7,'','','','',1,0,'F','0','0','system:user:resetPwd','#','admin','2026-11-16 22:43:16','',NULL,''),(1007,'角色查询',101,1,'','','','',1,0,'F','0','0','system:role:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1008,'角色新增',101,2,'','','','',1,0,'F','0','0','system:role:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1009,'角色修改',101,3,'','','','',1,0,'F','0','0','system:role:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1010,'角色删除',101,4,'','','','',1,0,'F','0','0','system:role:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1011,'角色导出',101,5,'','','','',1,0,'F','0','0','system:role:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1012,'菜单查询',102,1,'','','','',1,0,'F','0','0','system:menu:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1013,'菜单新增',102,2,'','','','',1,0,'F','0','0','system:menu:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1014,'菜单修改',102,3,'','','','',1,0,'F','0','0','system:menu:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1015,'菜单删除',102,4,'','','','',1,0,'F','0','0','system:menu:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1016,'部门查询',103,1,'','','','',1,0,'F','0','0','system:dept:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1017,'部门新增',103,2,'','','','',1,0,'F','0','0','system:dept:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1018,'部门修改',103,3,'','','','',1,0,'F','0','0','system:dept:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1019,'部门删除',103,4,'','','','',1,0,'F','0','0','system:dept:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1020,'岗位查询',104,1,'','','','',1,0,'F','0','0','system:post:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1021,'岗位新增',104,2,'','','','',1,0,'F','0','0','system:post:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1022,'岗位修改',104,3,'','','','',1,0,'F','0','0','system:post:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1023,'岗位删除',104,4,'','','','',1,0,'F','0','0','system:post:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1024,'岗位导出',104,5,'','','','',1,0,'F','0','0','system:post:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1025,'字典查询',105,1,'#','','','',1,0,'F','0','0','system:dict:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1026,'字典新增',105,2,'#','','','',1,0,'F','0','0','system:dict:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1027,'字典修改',105,3,'#','','','',1,0,'F','0','0','system:dict:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1028,'字典删除',105,4,'#','','','',1,0,'F','0','0','system:dict:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1029,'字典导出',105,5,'#','','','',1,0,'F','0','0','system:dict:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1030,'参数查询',106,1,'#','','','',1,0,'F','0','0','system:config:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1031,'参数新增',106,2,'#','','','',1,0,'F','0','0','system:config:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1032,'参数修改',106,3,'#','','','',1,0,'F','0','0','system:config:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1033,'参数删除',106,4,'#','','','',1,0,'F','0','0','system:config:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1034,'参数导出',106,5,'#','','','',1,0,'F','0','0','system:config:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1035,'公告查询',107,1,'#','','','',1,0,'F','0','0','system:notice:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1036,'公告新增',107,2,'#','','','',1,0,'F','0','0','system:notice:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1037,'公告修改',107,3,'#','','','',1,0,'F','0','0','system:notice:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1038,'公告删除',107,4,'#','','','',1,0,'F','0','0','system:notice:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1039,'操作查询',500,1,'#','','','',1,0,'F','0','0','monitor:operlog:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1040,'操作删除',500,2,'#','','','',1,0,'F','0','0','monitor:operlog:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1041,'日志导出',500,3,'#','','','',1,0,'F','0','0','monitor:operlog:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1042,'登录查询',501,1,'#','','','',1,0,'F','0','0','monitor:logininfor:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1043,'登录删除',501,2,'#','','','',1,0,'F','0','0','monitor:logininfor:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1044,'日志导出',501,3,'#','','','',1,0,'F','0','0','monitor:logininfor:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1045,'账户解锁',501,4,'#','','','',1,0,'F','0','0','monitor:logininfor:unlock','#','admin','2026-11-16 22:43:16','',NULL,''),(1046,'在线查询',109,1,'#','','','',1,0,'F','0','0','monitor:online:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1047,'批量强退',109,2,'#','','','',1,0,'F','0','0','monitor:online:batchLogout','#','admin','2026-11-16 22:43:16','',NULL,''),(1048,'单条强退',109,3,'#','','','',1,0,'F','0','0','monitor:online:forceLogout','#','admin','2026-11-16 22:43:16','',NULL,''),(1049,'任务查询',110,1,'#','','','',1,0,'F','0','0','monitor:job:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1050,'任务新增',110,2,'#','','','',1,0,'F','0','0','monitor:job:add','#','admin','2026-11-16 22:43:16','',NULL,''),(1051,'任务修改',110,3,'#','','','',1,0,'F','0','0','monitor:job:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1052,'任务删除',110,4,'#','','','',1,0,'F','0','0','monitor:job:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1053,'状态修改',110,5,'#','','','',1,0,'F','0','0','monitor:job:changeStatus','#','admin','2026-11-16 22:43:16','',NULL,''),(1054,'任务导出',110,6,'#','','','',1,0,'F','0','0','monitor:job:export','#','admin','2026-11-16 22:43:16','',NULL,''),(1055,'生成查询',116,1,'#','','','',1,0,'F','0','0','tool:gen:query','#','admin','2026-11-16 22:43:16','',NULL,''),(1056,'生成修改',116,2,'#','','','',1,0,'F','0','0','tool:gen:edit','#','admin','2026-11-16 22:43:16','',NULL,''),(1057,'生成删除',116,3,'#','','','',1,0,'F','0','0','tool:gen:remove','#','admin','2026-11-16 22:43:16','',NULL,''),(1058,'导入代码',116,4,'#','','','',1,0,'F','0','0','tool:gen:import','#','admin','2026-11-16 22:43:16','',NULL,''),(1059,'预览代码',116,5,'#','','','',1,0,'F','0','0','tool:gen:preview','#','admin','2026-11-16 22:43:16','',NULL,''),(1060,'生成代码',116,6,'#','','','',1,0,'F','0','0','tool:gen:code','#','admin','2026-11-16 22:43:16','',NULL,''),(3000,'AI律师系统',0,5,'lawyers',NULL,'','',1,0,'M','0','0','','law','admin','2026-11-16 22:43:18','',NULL,'AI律师系统目录'),(3100,'法律咨询管理',3000,1,'consultation','lawyers/consultation/index','','',1,0,'C','0','0','lawyers:consultation:list','peoples','admin','2026-11-16 22:43:18','',NULL,'法律咨询管理菜单'),(3101,'咨询查询',3100,1,'','','','',1,0,'F','0','0','lawyers:consultation:query','#','admin','2026-11-16 22:43:18','',NULL,''),(3102,'咨询新增',3100,2,'','','','',1,0,'F','0','0','lawyers:consultation:add','#','admin','2026-11-16 22:43:18','',NULL,''),(3103,'咨询修改',3100,3,'','','','',1,0,'F','0','0','lawyers:consultation:edit','#','admin','2026-11-16 22:43:18','',NULL,''),(3104,'咨询删除',3100,4,'','','','',1,0,'F','0','0','lawyers:consultation:remove','#','admin','2026-11-16 22:43:18','',NULL,''),(3105,'咨询导出',3100,5,'','','','',1,0,'F','0','0','lawyers:consultation:export','#','admin','2026-11-16 22:43:18','',NULL,''),(3200,'法律知识库',3000,2,'knowledge','lawyers/knowledge/index','','',1,0,'C','0','0','lawyers:knowledge:list','education','admin','2026-11-16 22:43:18','',NULL,'法律知识库菜单'),(3201,'知识查询',3200,1,'','','','',1,0,'F','0','0','lawyers:knowledge:query','#','admin','2026-11-16 22:43:18','',NULL,''),(3202,'知识新增',3200,2,'','','','',1,0,'F','0','0','lawyers:knowledge:add','#','admin','2026-11-16 22:43:18','',NULL,''),(3203,'知识修改',3200,3,'','','','',1,0,'F','0','0','lawyers:knowledge:edit','#','admin','2026-11-16 22:43:18','',NULL,''),(3204,'知识删除',3200,4,'','','','',1,0,'F','0','0','lawyers:knowledge:remove','#','admin','2026-11-16 22:43:18','',NULL,''),(3205,'知识导出',3200,5,'','','','',1,0,'F','0','0','lawyers:knowledge:export','#','admin','2026-11-16 22:43:18','',NULL,''),(3206,'知识导入',3200,6,'','','','',1,0,'F','0','0','lawyers:knowledge:import','#','admin','2026-11-16 22:43:19','',NULL,''),(3207,'知识审核',3200,7,'','','','',1,0,'F','0','0','lawyers:knowledge:audit','#','admin','2026-11-16 22:43:19','',NULL,''),(3300,'咨询分类管理',3000,3,'category','lawyers/category/index','','',1,0,'C','0','0','lawyers:category:list','tree','admin','2026-11-16 22:43:18','',NULL,'咨询分类管理菜单'),(3301,'分类查询',3300,1,'','','','',1,0,'F','0','0','lawyers:category:query','#','admin','2026-11-16 22:43:18','',NULL,''),(3302,'分类新增',3300,2,'','','','',1,0,'F','0','0','lawyers:category:add','#','admin','2026-11-16 22:43:18','',NULL,''),(3303,'分类修改',3300,3,'','','','',1,0,'F','0','0','lawyers:category:edit','#','admin','2026-11-16 22:43:18','',NULL,''),(3304,'分类删除',3300,4,'','','','',1,0,'F','0','0','lawyers:category:remove','#','admin','2026-11-16 22:43:18','',NULL,''),(3305,'分类导出',3300,5,'','','','',1,0,'F','0','0','lawyers:category:export','#','admin','2026-11-16 22:43:18','',NULL,''),(3400,'系统配置',3000,4,'systemConfig',NULL,'','',1,0,'M','0','0','','system','admin','2026-11-16 22:43:19','',NULL,'系统配置目录'),(3410,'AI模型参数配置',3400,1,'modelConfig','lawyers/modelConfig/index','','',1,0,'C','0','0','lawyers:modelConfig:list','robot','admin','2026-11-16 22:43:19','',NULL,'AI模型参数配置菜单'),(3411,'模型配置查询',3410,1,'','','','',1,0,'F','0','0','lawyers:modelConfig:query','#','admin','2026-11-16 22:43:19','',NULL,''),(3412,'模型配置新增',3410,2,'','','','',1,0,'F','0','0','lawyers:modelConfig:add','#','admin','2026-11-16 22:43:19','',NULL,''),(3413,'模型配置修改',3410,3,'','','','',1,0,'F','0','0','lawyers:modelConfig:edit','#','admin','2026-11-16 22:43:19','',NULL,''),(3414,'模型配置删除',3410,4,'','','','',1,0,'F','0','0','lawyers:modelConfig:remove','#','admin','2026-11-16 22:43:19','',NULL,''),(3415,'模型配置导出',3410,5,'','','','',1,0,'F','0','0','lawyers:modelConfig:export','#','admin','2026-11-16 22:43:19','',NULL,''),(3416,'获取默认配置',3410,6,'','','','',1,0,'F','0','0','lawyers:modelConfig:default','#','admin','2026-11-16 22:43:19','',NULL,''),(3417,'设置默认配置',3410,7,'','','','',1,0,'F','0','0','lawyers:modelConfig:setDefault','#','admin','2026-11-16 22:43:19','',NULL,''),(3418,'测试模型连接',3410,8,'','','','',1,0,'F','0','0','lawyers:modelConfig:test','#','admin','2026-11-16 22:43:19','',NULL,''),(3420,'系统提示词配置',3400,2,'systemPrompt','lawyers/systemPrompt/index','','',1,0,'C','0','0','lawyers:systemPrompt:list','message','admin','2026-11-16 22:43:19','',NULL,'系统提示词配置菜单'),(3421,'提示词查询',3420,1,'','','','',1,0,'F','0','0','lawyers:systemPrompt:query','#','admin','2026-11-16 22:43:19','',NULL,''),(3422,'提示词新增',3420,2,'','','','',1,0,'F','0','0','lawyers:systemPrompt:add','#','admin','2026-11-16 22:43:19','',NULL,''),(3423,'提示词修改',3420,3,'','','','',1,0,'F','0','0','lawyers:systemPrompt:edit','#','admin','2026-11-16 22:43:19','',NULL,''),(3424,'提示词删除',3420,4,'','','','',1,0,'F','0','0','lawyers:systemPrompt:remove','#','admin','2026-11-16 22:43:19','',NULL,''),(3425,'提示词导出',3420,5,'','','','',1,0,'F','0','0','lawyers:systemPrompt:export','#','admin','2026-11-16 22:43:19','',NULL,''),(3426,'获取默认提示词',3420,6,'','','','',1,0,'F','0','0','lawyers:systemPrompt:default','#','admin','2026-11-16 22:43:19','',NULL,''),(3427,'设置默认提示词',3420,7,'','','','',1,0,'F','0','0','lawyers:systemPrompt:setDefault','#','admin','2026-11-16 22:43:19','',NULL,''),(3428,'预览提示词效果',3420,8,'','','','',1,0,'F','0','0','lawyers:systemPrompt:preview','#','admin','2026-11-16 22:43:19','',NULL,''),(3500,'话务系统',3000,5,'callCenter',NULL,'','',1,0,'M','0','0','','phone','admin','2026-11-16 22:43:20','',NULL,'话务系统目录'),(3510,'坐席工作台',3500,1,'callWorkbench','lawyers/callCenter/callWorkbench','','',1,0,'C','0','0','lawyers:call:workbench:list','dashboard','admin','2026-11-16 22:43:20','',NULL,'坐席工作台菜单'),(3520,'话务服务',3500,2,'callService',NULL,'','',1,0,'M','0','0','','monitor','admin','2026-11-16 22:43:20','',NULL,'话务服务目录'),(3521,'话务功能面板',3520,1,'callPanel','lawyers/callCenter/callPanel','','',1,0,'C','0','0','lawyers:call:panel:list','phone','admin','2026-11-16 22:43:20','',NULL,'话务功能面板菜单'),(3523,'来电记录',3520,3,'callRecord','lawyers/callCenter/callRecord','','',1,0,'C','0','0','lawyers:call:record:list','list','admin','2026-11-16 22:43:20','',NULL,'来电记录菜单'),(3524,'来电查询',3523,1,'','','','',1,0,'F','0','0','lawyers:call:record:query','#','admin','2026-11-16 22:43:20','',NULL,''),(3525,'来电新增',3523,2,'','','','',1,0,'F','0','0','lawyers:call:record:add','#','admin','2026-11-16 22:43:20','',NULL,''),(3526,'来电修改',3523,3,'','','','',1,0,'F','0','0','lawyers:call:record:edit','#','admin','2026-11-16 22:43:20','',NULL,''),(3527,'来电删除',3523,4,'','','','',1,0,'F','0','0','lawyers:call:record:remove','#','admin','2026-11-16 22:43:20','',NULL,''),(3530,'业务管理',3500,3,'callBusiness',NULL,'','',1,0,'M','0','0','','edit','admin','2026-11-16 22:43:20','',NULL,'业务管理目录'),(3531,'工单管理',3530,1,'workOrder','lawyers/callCenter/workOrder','','',1,0,'C','0','0','lawyers:call:ticket:list','tickets','admin','2026-11-16 22:43:20','',NULL,'工单管理菜单'),(3532,'工单查询',3531,1,'','','','',1,0,'F','0','0','lawyers:call:ticket:query','#','admin','2026-11-16 22:43:20','',NULL,''),(3533,'工单新增',3531,2,'','','','',1,0,'F','0','0','lawyers:call:ticket:add','#','admin','2026-11-16 22:43:20','',NULL,''),(3534,'工单修改',3531,3,'','','','',1,0,'F','0','0','lawyers:call:ticket:edit','#','admin','2026-11-16 22:43:20','',NULL,''),(3535,'工单删除',3531,4,'','','','',1,0,'F','0','0','lawyers:call:ticket:remove','#','admin','2026-11-16 22:43:20','',NULL,''),(3536,'工单处理',3531,5,'','','','',1,0,'F','0','0','lawyers:call:ticket:process','#','admin','2026-11-16 22:43:20','',NULL,''),(3537,'工单完成',3531,6,'','','','',1,0,'F','0','0','lawyers:call:ticket:complete','#','admin','2026-11-16 22:43:20','',NULL,''),(3538,'工单归档',3531,7,'','','','',1,0,'F','0','0','lawyers:call:ticket:archive','#','admin','2026-11-16 22:43:20','',NULL,''),(3540,'台账记录',3530,2,'callLedger','lawyers/callCenter/callLedger','','',1,0,'C','0','0','lawyers:call:ledger:list','excel','admin','2026-11-16 22:43:20','',NULL,'台账记录菜单'),(3541,'台账查询',3540,1,'','','','',1,0,'F','0','0','lawyers:call:ledger:query','#','admin','2026-11-16 22:43:20','',NULL,''),(3542,'台账新增',3540,2,'','','','',1,0,'F','0','0','lawyers:call:ledger:add','#','admin','2026-11-16 22:43:20','',NULL,''),(3543,'台账修改',3540,3,'','','','',1,0,'F','0','0','lawyers:call:ledger:edit','#','admin','2026-11-16 22:43:20','',NULL,''),(3544,'台账删除',3540,4,'','','','',1,0,'F','0','0','lawyers:call:ledger:remove','#','admin','2026-11-16 22:43:20','',NULL,''),(3545,'台账导出',3540,5,'','','','',1,0,'F','0','0','lawyers:call:ledger:export','#','admin','2026-11-16 22:43:20','',NULL,''),(3546,'工单完成',3540,6,'','','','',1,0,'F','0','0','lawyers:call:ticket:complete','#','admin','2026-11-16 22:43:20','',NULL,''),(3547,'工单归档',3540,7,'','','','',1,0,'F','0','0','lawyers:call:ticket:archive','#','admin','2026-11-16 22:43:20','',NULL,''),(3550,'跟进支撑',3500,4,'callSupport',NULL,'','',1,0,'M','0','0','','peoples','admin','2026-11-16 22:43:20','',NULL,'跟进支撑目录'),(3551,'坐席管理',3550,1,'callAgent','lawyers/callCenter/callAgent','','',1,0,'C','0','0','lawyers:call:agent:list','user','admin','2026-11-16 22:43:20','',NULL,'坐席管理菜单'),(3552,'坐席查询',3551,1,'','','','',1,0,'F','0','0','lawyers:call:agent:query','#','admin','2026-11-16 22:43:20','',NULL,''),(3553,'坐席新增',3551,2,'','','','',1,0,'F','0','0','lawyers:call:agent:add','#','admin','2026-11-16 22:43:20','',NULL,''),(3554,'坐席修改',3551,3,'','','','',1,0,'F','0','0','lawyers:call:agent:edit','#','admin','2026-11-16 22:43:20','',NULL,''),(3555,'坐席删除',3551,4,'','','','',1,0,'F','0','0','lawyers:call:agent:remove','#','admin','2026-11-16 22:43:20','',NULL,''),(3556,'坐席登录',3551,5,'','','','',1,0,'F','0','0','lawyers:call:agent:login','#','admin','2026-11-16 22:43:20','',NULL,''),(3557,'坐席注销',3551,6,'','','','',1,0,'F','0','0','lawyers:call:agent:logout','#','admin','2026-11-16 22:43:20','',NULL,''),(3560,'转接记录',3550,2,'callTransfer','lawyers/callCenter/callTransfer','','',1,0,'C','0','0','lawyers:call:transfer:list','guide','admin','2026-11-16 22:43:20','',NULL,'转接记录菜单'),(3561,'转接查询',3560,1,'','','','',1,0,'F','0','0','lawyers:call:transfer:query','#','admin','2026-11-16 22:43:20','',NULL,''),(3562,'转接新增',3560,2,'','','','',1,0,'F','0','0','lawyers:call:transfer:add','#','admin','2026-11-16 22:43:20','',NULL,''),(3563,'转接删除',3560,3,'','','','',1,0,'F','0','0','lawyers:call:transfer:remove','#','admin','2026-11-16 22:43:20','',NULL,''),(3570,'知识库',3500,5,'callKnowledge',NULL,'','',1,0,'M','0','0','','education','admin','2026-11-16 22:43:20','',NULL,'知识库目录'),(3600,'IVR智能流程',3500,6,'ivrFlow',NULL,'','',1,0,'M','0','0','','tree','admin','2026-11-16 22:43:23','',NULL,'IVR智能流程目录'),(3601,'IVR流程管理',3600,1,'flow','lawyers/ivr/flow/index','','',1,0,'C','0','0','lawyers:ivr:flow:list','flow','admin','2026-11-16 22:43:23','',NULL,'IVR流程管理菜单'),(3602,'流程查询',3601,1,'','','','',1,0,'F','0','0','lawyers:ivr:flow:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3603,'流程新增',3601,2,'','','','',1,0,'F','0','0','lawyers:ivr:flow:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3604,'流程修改',3601,3,'','','','',1,0,'F','0','0','lawyers:ivr:flow:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3605,'流程删除',3601,4,'','','','',1,0,'F','0','0','lawyers:ivr:flow:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3606,'流程导出',3601,5,'','','','',1,0,'F','0','0','lawyers:ivr:flow:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3607,'流程发布',3601,6,'','','','',1,0,'F','0','0','lawyers:ivr:flow:publish','#','admin','2026-11-16 22:43:23','',NULL,''),(3610,'意图定义管理',3600,2,'intention','lawyers/ivr/intention/index','','',1,0,'C','0','0','lawyers:ivr:intention:list','checkbox','admin','2026-11-16 22:43:23','',NULL,'意图定义管理菜单'),(3611,'意图查询',3610,1,'','','','',1,0,'F','0','0','lawyers:ivr:intention:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3612,'意图新增',3610,2,'','','','',1,0,'F','0','0','lawyers:ivr:intention:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3613,'意图修改',3610,3,'','','','',1,0,'F','0','0','lawyers:ivr:intention:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3614,'意图删除',3610,4,'','','','',1,0,'F','0','0','lawyers:ivr:intention:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3615,'意图导出',3610,5,'','','','',1,0,'F','0','0','lawyers:ivr:intention:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3616,'意图匹配',3610,6,'','','','',1,0,'F','0','0','lawyers:ivr:intention:match','#','admin','2026-11-16 22:43:23','',NULL,''),(3620,'智能外呼',3500,7,'outbound',NULL,'','',1,0,'M','0','0','','phone','admin','2026-11-16 22:43:23','',NULL,'智能外呼目录'),(3621,'外呼任务管理',3620,1,'task','lawyers/outbound/task/index','','',1,0,'C','0','0','lawyers:outbound:task:list','job','admin','2026-11-16 22:43:23','',NULL,'外呼任务管理菜单'),(3622,'任务查询',3621,1,'','','','',1,0,'F','0','0','lawyers:outbound:task:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3623,'任务新增',3621,2,'','','','',1,0,'F','0','0','lawyers:outbound:task:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3624,'任务修改',3621,3,'','','','',1,0,'F','0','0','lawyers:outbound:task:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3625,'任务删除',3621,4,'','','','',1,0,'F','0','0','lawyers:outbound:task:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3626,'任务导出',3621,5,'','','','',1,0,'F','0','0','lawyers:outbound:task:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3627,'任务启动',3621,6,'','','','',1,0,'F','0','0','lawyers:outbound:task:start','#','admin','2026-11-16 22:43:23','',NULL,''),(3628,'任务暂停',3621,7,'','','','',1,0,'F','0','0','lawyers:outbound:task:pause','#','admin','2026-11-16 22:43:23','',NULL,''),(3629,'任务停止',3621,8,'','','','',1,0,'F','0','0','lawyers:outbound:task:stop','#','admin','2026-11-16 22:43:23','',NULL,''),(3630,'统一工作台',3500,1,'workbench','lawyers/workbench/index','','',1,0,'C','0','0','lawyers:workbench:index','dashboard','admin','2026-11-16 22:43:23','',NULL,'统一工作台菜单'),(3631,'工作台查询',3630,1,'','','','',1,0,'F','0','0','lawyers:workbench:index','#','admin','2026-11-16 22:43:23','',NULL,''),(3632,'待办事项管理',3666,7,'todo','lawyers/workbench/todo','','',1,0,'C','1','0','lawyers:workbench:todo:list','edit','admin','2026-11-16 22:43:23','',NULL,'待办事项管理菜单'),(3633,'待办查询',3632,1,'','','','',1,0,'F','0','0','lawyers:workbench:todo:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3634,'待办新增',3632,2,'','','','',1,0,'F','0','0','lawyers:workbench:todo:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3635,'待办修改',3632,3,'','','','',1,0,'F','0','0','lawyers:workbench:todo:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3636,'待办删除',3632,4,'','','','',1,0,'F','0','0','lawyers:workbench:todo:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3637,'待办导出',3632,5,'','','','',1,0,'F','0','0','lawyers:workbench:todo:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3638,'公告管理',3666,8,'wbNotice','lawyers/workbench/notice','','',1,0,'C','1','0','lawyers:workbench:notice:list','message','admin','2026-11-16 22:43:23','',NULL,'工作台公告管理菜单'),(3639,'公告查询',3638,1,'','','','',1,0,'F','0','0','lawyers:workbench:notice:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3640,'公告新增',3638,2,'','','','',1,0,'F','0','0','lawyers:workbench:notice:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3641,'公告修改',3638,3,'','','','',1,0,'F','0','0','lawyers:workbench:notice:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3642,'公告删除',3638,4,'','','','',1,0,'F','0','0','lawyers:workbench:notice:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3643,'公告导出',3638,5,'','','','',1,0,'F','0','0','lawyers:workbench:notice:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3644,'来电弹屏',3500,4,'callPopup','lawyers/callCenter/callPopup','','',1,0,'C','0','0','lawyers:call:popup:query','phone','admin','2026-11-16 22:43:21','',NULL,'来电弹屏菜单'),(3645,'弹屏查询',3644,1,'','','','',1,0,'F','0','0','lawyers:call:popup:query','#','admin','2026-11-16 22:43:21','',NULL,''),(3646,'弹屏编辑',3644,2,'','','','',1,0,'F','0','0','lawyers:call:popup:edit','#','admin','2026-11-16 22:43:21','',NULL,''),(3647,'未接来电',3500,5,'missedCall','lawyers/callCenter/missedCall','','',1,0,'C','0','0','lawyers:call:missed:list','phone-outline','admin','2026-11-16 22:43:22','',NULL,'未接来电菜单'),(3648,'未接查询',3647,1,'','','','',1,0,'F','0','0','lawyers:call:missed:query','#','admin','2026-11-16 22:43:22','',NULL,''),(3649,'未接新增',3647,2,'','','','',1,0,'F','0','0','lawyers:call:missed:add','#','admin','2026-11-16 22:43:22','',NULL,''),(3650,'未接修改',3647,3,'','','','',1,0,'F','0','0','lawyers:call:missed:edit','#','admin','2026-11-16 22:43:22','',NULL,''),(3651,'未接删除',3647,4,'','','','',1,0,'F','0','0','lawyers:call:missed:remove','#','admin','2026-11-16 22:43:22','',NULL,''),(3652,'未接导出',3647,5,'','','','',1,0,'F','0','0','lawyers:call:missed:export','#','admin','2026-11-16 22:43:22','',NULL,''),(3653,'回拨标记',3647,6,'','','','',1,0,'F','0','0','lawyers:call:missed:callback','#','admin','2026-11-16 22:43:22','',NULL,''),(3654,'客户回访中心',3500,6,'callback','lawyers/callback/index','','',1,0,'C','0','0','lawyers:callback:list','refresh-left','admin','2026-11-16 22:43:21','',NULL,'客户回访中心菜单'),(3655,'回访查询',3654,1,'','','','',1,0,'F','0','0','lawyers:callback:query','#','admin','2026-11-16 22:43:21','',NULL,''),(3656,'回访新增',3654,2,'','','','',1,0,'F','0','0','lawyers:callback:add','#','admin','2026-11-16 22:43:21','',NULL,''),(3657,'回访修改',3654,3,'','','','',1,0,'F','0','0','lawyers:callback:edit','#','admin','2026-11-16 22:43:21','',NULL,''),(3658,'回访删除',3654,4,'','','','',1,0,'F','0','0','lawyers:callback:remove','#','admin','2026-11-16 22:43:21','',NULL,''),(3659,'回访导出',3654,5,'','','','',1,0,'F','0','0','lawyers:callback:export','#','admin','2026-11-16 22:43:21','',NULL,''),(3660,'回访任务管理',3500,7,'callback/task','lawyers/callback/task','','',1,0,'C','0','0','lawyers:returnVisitTask:list','list','admin','2026-11-16 22:43:23','',NULL,'回访任务管理菜单'),(3661,'任务查询',3660,1,'','','','',1,0,'F','0','0','lawyers:returnVisitTask:query','#','admin','2026-11-16 22:43:23','',NULL,''),(3662,'任务新增',3660,2,'','','','',1,0,'F','0','0','lawyers:returnVisitTask:add','#','admin','2026-11-16 22:43:23','',NULL,''),(3663,'任务修改',3660,3,'','','','',1,0,'F','0','0','lawyers:returnVisitTask:edit','#','admin','2026-11-16 22:43:23','',NULL,''),(3664,'任务删除',3660,4,'','','','',1,0,'F','0','0','lawyers:returnVisitTask:remove','#','admin','2026-11-16 22:43:23','',NULL,''),(3665,'任务导出',3660,5,'','','','',1,0,'F','0','0','lawyers:returnVisitTask:export','#','admin','2026-11-16 22:43:23','',NULL,''),(3666,'智能风险预警管理',3500,9,'riskWarning','lawyers/riskWarning/index','','',1,0,'C','0','0','lawyers:riskWarning:list','warning','admin','2026-11-16 22:43:24','',NULL,'智能风险预警管理菜单'),(3667,'预警查询',3666,1,'','','','',1,0,'F','0','0','lawyers:riskWarning:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3668,'预警新增',3666,2,'','','','',1,0,'F','0','0','lawyers:riskWarning:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3669,'预警修改',3666,3,'','','','',1,0,'F','0','0','lawyers:riskWarning:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3670,'有偿法律服务',3500,7,'paidService','lawyers/paidService/index','','',1,0,'C','0','0','lawyers:paidService:list','money','admin','2026-11-16 22:43:24','',NULL,'有偿法律服务菜单'),(3671,'付费服务查询',3670,1,'','','','',1,0,'F','0','0','lawyers:paidService:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3672,'付费服务新增',3670,2,'','','','',1,0,'F','0','0','lawyers:paidService:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3673,'付费服务修改',3670,3,'','','','',1,0,'F','0','0','lawyers:paidService:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3674,'付费服务删除',3670,4,'','','','',1,0,'F','0','0','lawyers:paidService:remove','#','admin','2026-11-16 22:43:24','',NULL,''),(3675,'付费服务导出',3670,5,'','','','',1,0,'F','0','0','lawyers:paidService:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3680,'签署记录管理',3668,8,'signRecord','lawyers/signRecord/index','','',1,0,'C','0','0','lawyers:signRecord:list','documentation','admin','2026-11-16 22:43:24','',NULL,'签署记录管理菜单'),(3681,'签署记录查询',3680,1,'','','','',1,0,'F','0','0','lawyers:signRecord:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3682,'签署记录新增',3680,2,'','','','',1,0,'F','0','0','lawyers:signRecord:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3683,'签署记录修改',3680,3,'','','','',1,0,'F','0','0','lawyers:signRecord:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3684,'签署记录删除',3680,4,'','','','',1,0,'F','0','0','lawyers:signRecord:remove','#','admin','2026-11-16 22:43:24','',NULL,''),(3685,'签署记录导出',3680,5,'','','','',1,0,'F','0','0','lawyers:signRecord:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3690,'被叫名单',3669,4,'callee','lawyers/outbound/callee/index','','',1,1,'C','0','0','lawyers:outbound:callee:list','list','admin','2026-11-16 22:43:25','',NULL,'外呼被叫名单(隐藏菜单)'),(3720,'图文对话服务',3500,4,'chat','lawyers/chat/index','','',1,0,'C','0','0','lawyers:chat:list','chat','admin','2026-11-16 22:43:24','',NULL,'图文对话服务菜单'),(3721,'图文对话查询',3720,1,'','','','',1,0,'F','0','0','lawyers:chat:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3722,'图文对话回复',3720,2,'','','','',1,0,'F','0','0','lawyers:chat:send','#','admin','2026-11-16 22:43:24','',NULL,''),(3723,'图文对话转接',3720,3,'','','','',1,0,'F','0','0','lawyers:chat:transfer','#','admin','2026-11-16 22:43:24','',NULL,''),(3724,'图文对话关闭',3720,4,'','','','',1,0,'F','0','0','lawyers:chat:close','#','admin','2026-11-16 22:43:24','',NULL,''),(3725,'图文对话导出',3720,5,'','','','',1,0,'F','0','0','lawyers:chat:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3730,'视频咨询服务',3500,5,'video','lawyers/videoConsult/index','','',1,0,'C','0','0','lawyers:videoConsult:list','video','admin','2026-11-16 22:43:24','',NULL,'视频咨询服务菜单'),(3731,'视频咨询查询',3730,1,'','','','',1,0,'F','0','0','lawyers:videoConsult:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3732,'视频咨询创建',3730,2,'','','','',1,0,'F','0','0','lawyers:videoConsult:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3733,'视频咨询编辑',3730,3,'','','','',1,0,'F','0','0','lawyers:videoConsult:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3734,'视频咨询删除',3730,4,'','','','',1,0,'F','0','0','lawyers:videoConsult:remove','#','admin','2026-11-16 22:43:24','',NULL,''),(3735,'视频咨询导出',3730,5,'','','','',1,0,'F','0','0','lawyers:videoConsult:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3740,'有偿法律服务',3500,7,'paidService','lawyers/paidService/index','','',1,0,'C','0','0','lawyers:paidService:list','money','admin','2026-11-16 22:43:24','',NULL,'有偿法律服务菜单'),(3741,'有偿服务查询',3740,1,'','','','',1,0,'F','0','0','lawyers:paidService:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3742,'有偿服务新增',3740,2,'','','','',1,0,'F','0','0','lawyers:paidService:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3743,'有偿服务修改',3740,3,'','','','',1,0,'F','0','0','lawyers:paidService:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3744,'有偿服务删除',3740,4,'','','','',1,0,'F','0','0','lawyers:paidService:remove','#','admin','2026-11-16 22:43:24','',NULL,''),(3745,'有偿服务导出',3740,5,'','','','',1,0,'F','0','0','lawyers:paidService:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3750,'智能风险预警',3500,9,'riskWarning','lawyers/riskWarning/index','','',1,0,'C','0','0','lawyers:riskWarning:list','warning','admin','2026-11-16 22:43:24','',NULL,'智能风险预警菜单'),(3751,'风险预警查询',3750,1,'','','','',1,0,'F','0','0','lawyers:riskWarning:query','#','admin','2026-11-16 22:43:24','',NULL,''),(3752,'风险预警新增',3750,2,'','','','',1,0,'F','0','0','lawyers:riskWarning:add','#','admin','2026-11-16 22:43:24','',NULL,''),(3753,'风险预警修改',3750,3,'','','','',1,0,'F','0','0','lawyers:riskWarning:edit','#','admin','2026-11-16 22:43:24','',NULL,''),(3754,'风险预警删除',3750,4,'','','','',1,0,'F','0','0','lawyers:riskWarning:remove','#','admin','2026-11-16 22:43:24','',NULL,''),(3755,'风险预警导出',3750,5,'','','','',1,0,'F','0','0','lawyers:riskWarning:export','#','admin','2026-11-16 22:43:24','',NULL,''),(3760,'AI智能体配置',3600,3,'agentConfig','lawyers/agent/config','','',1,0,'C','0','0','lawyers:agent:config:list','monitor','admin','2026-11-16 22:43:25','',NULL,'AI智能体配置菜单'),(3761,'智能体查询',3760,1,'','','','',1,0,'F','0','0','lawyers:agent:config:query','#','admin','2026-11-16 22:43:25','',NULL,''),(3762,'智能体新增',3760,2,'','','','',1,0,'F','0','0','lawyers:agent:config:add','#','admin','2026-11-16 22:43:25','',NULL,''),(3763,'智能体修改',3760,3,'','','','',1,0,'F','0','0','lawyers:agent:config:edit','#','admin','2026-11-16 22:43:25','',NULL,''),(3764,'智能体删除',3760,4,'','','','',1,0,'F','0','0','lawyers:agent:config:remove','#','admin','2026-11-16 22:43:25','',NULL,''),(3765,'智能体导出',3760,5,'','','','',1,0,'F','0','0','lawyers:agent:config:export','#','admin','2026-11-16 22:43:25','',NULL,''),(3766,'智能体连接测试',3760,6,'','','','',1,0,'F','0','0','lawyers:agent:config:test','#','admin','2026-11-16 22:43:25','',NULL,''),(3770,'技能组管理',3670,6,'skillGroup','lawyers/skill/group','','',1,0,'C','0','0','lawyers:skillGroup:view','tree-table','admin','2026-11-16 22:43:26','',NULL,'技能组与坐席分配管理'),(3771,'技能组查询',3770,1,'','',NULL,'',1,0,'F','0','0','lawyers:skillGroup:query','#','admin','2026-11-16 22:43:26','',NULL,''),(3772,'技能组新增',3770,2,'','',NULL,'',1,0,'F','0','0','lawyers:skillGroup:add','#','admin','2026-11-16 22:43:26','',NULL,''),(3773,'技能组修改',3770,3,'','',NULL,'',1,0,'F','0','0','lawyers:skillGroup:edit','#','admin','2026-11-16 22:43:26','',NULL,''),(3774,'技能组删除',3770,4,'','',NULL,'',1,0,'F','0','0','lawyers:skillGroup:remove','#','admin','2026-11-16 22:43:26','',NULL,''),(3775,'技能组导出',3770,5,'','',NULL,'',1,0,'F','0','0','lawyers:skillGroup:export','#','admin','2026-11-16 22:43:26','',NULL,''),(3780,'排队监控',3670,7,'queue','lawyers/skill/queue','','',1,0,'C','0','0','lawyers:queue:view','list','admin','2026-11-16 22:43:26','',NULL,'实时排队与分配监控'),(3781,'排队查询',3780,1,'','',NULL,'',1,0,'F','0','0','lawyers:queue:query','#','admin','2026-11-16 22:43:26','',NULL,''),(3782,'手动分配',3780,2,'','',NULL,'',1,0,'F','0','0','lawyers:queue:assign','#','admin','2026-11-16 22:43:26','',NULL,''),(3783,'踢除排队',3780,3,'','',NULL,'',1,0,'F','0','0','lawyers:queue:kick','#','admin','2026-11-16 22:43:26','',NULL,''),(3790,'短信配置',3670,8,'smsConfig','lawyers/sms/config','','',1,0,'C','0','0','lawyers:smsConfig:view','message','admin','2026-11-16 22:43:26','',NULL,'短信通道配置管理'),(3791,'短信模板',3670,9,'smsTemplate','lawyers/sms/template',NULL,'',1,0,'C','0','0','lawyers:smsTemplate:view','form','admin','2026-11-16 22:43:26','',NULL,''),(3792,'短信记录',3670,10,'smsLog','lawyers/sms/log',NULL,'',1,0,'C','0','0','lawyers:smsLog:view','list','admin','2026-11-16 22:43:26','',NULL,''),(3793,'短信通道查询',3790,1,'','',NULL,'',1,0,'F','0','0','lawyers:smsConfig:query','#','admin','2026-11-16 22:43:26','',NULL,''),(3794,'短信通道新增',3790,2,'','',NULL,'',1,0,'F','0','0','lawyers:smsConfig:add','#','admin','2026-11-16 22:43:26','',NULL,''),(3795,'短信通道修改',3790,3,'','',NULL,'',1,0,'F','0','0','lawyers:smsConfig:edit','#','admin','2026-11-16 22:43:26','',NULL,''),(3796,'短信通道删除',3790,4,'','',NULL,'',1,0,'F','0','0','lawyers:smsConfig:remove','#','admin','2026-11-16 22:43:26','',NULL,''),(3797,'短信模板查询',3791,1,'','',NULL,'',1,0,'F','0','0','lawyers:smsTemplate:query','#','admin','2026-11-16 22:43:26','',NULL,''),(3798,'短信模板新增',3791,2,'','',NULL,'',1,0,'F','0','0','lawyers:smsTemplate:add','#','admin','2026-11-16 22:43:26','',NULL,''),(3799,'短信模板修改',3791,3,'','',NULL,'',1,0,'F','0','0','lawyers:smsTemplate:edit','#','admin','2026-11-16 22:43:26','',NULL,''),(3800,'工单流程配置',3500,8,'ticketFlow','lawyers/callCenter/ticketFlow','','',1,0,'C','0','0','lawyers:ticketFlow:list','tree-table','admin','2026-11-16 22:43:43','',NULL,'工单流程动作规则配置菜单'),(3801,'流程规则查询',3800,1,'','','','',1,0,'F','0','0','lawyers:ticketFlow:query','#','admin','2026-11-16 22:43:43','',NULL,''),(3802,'流程规则新增',3800,2,'','','','',1,0,'F','0','0','lawyers:ticketFlow:add','#','admin','2026-11-16 22:43:43','',NULL,''),(3803,'流程规则修改',3800,3,'','','','',1,0,'F','0','0','lawyers:ticketFlow:edit','#','admin','2026-11-16 22:43:43','',NULL,''),(3810,'运营大屏',3670,11,'dashboard','lawyers/dashboard/index','','',1,0,'C','0','0','lawyers:dashboard:view','chart','admin','2026-11-16 22:43:26','',NULL,'呼叫中心运营大屏'),(3811,'坐席效能报表',3670,12,'agentPerformance','lawyers/performance/agent','','',1,0,'C','0','0','lawyers:performance:agent:list','list','admin','2026-11-16 22:43:26','',NULL,'坐席效能聚合报表'),(3812,'坐席效能导出',3811,1,'','',NULL,'',1,0,'F','0','0','lawyers:performance:agent:export','#','admin','2026-11-16 22:43:26','',NULL,''),(3820,'录音管理',3667,5,'recording','lawyers/recording/index',NULL,'',1,0,'C','0','0','lawyers:call:record:list','microphone','admin','2026-11-16 22:43:27','',NULL,'通话录音管理'),(3821,'录音查询',3820,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:call:record:query','#','admin','2026-11-16 22:43:27','',NULL,''),(3822,'录音删除',3820,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:call:record:remove','#','admin','2026-11-16 22:43:27','',NULL,''),(3823,'录音导出',3820,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:call:record:export','#','admin','2026-11-16 22:43:27','',NULL,''),(3830,'黑白名单',3670,13,'blacklist','lawyers/blacklist/index',NULL,'',1,0,'C','0','0','lawyers:blacklist:list','peoples','admin','2026-11-16 22:43:27','',NULL,'通话黑白名单管理'),(3831,'名单查询',3830,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:blacklist:query','#','admin','2026-11-16 22:43:27','',NULL,''),(3832,'名单新增',3830,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:blacklist:add','#','admin','2026-11-16 22:43:27','',NULL,''),(3833,'名单修改',3830,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:blacklist:edit','#','admin','2026-11-16 22:43:27','',NULL,''),(3834,'名单删除',3830,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:blacklist:remove','#','admin','2026-11-16 22:43:27','',NULL,''),(3840,'排班管理',3666,5,'schedule','lawyers/schedule/index',NULL,'',1,0,'C','0','0','lawyers:schedule:list','date','admin','2026-11-16 22:43:27','',NULL,'坐席班次与排班管理'),(3841,'排班查询',3840,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:schedule:query','#','admin','2026-11-16 22:43:27','',NULL,''),(3842,'排班新增',3840,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:schedule:add','#','admin','2026-11-16 22:43:27','',NULL,''),(3843,'排班修改',3840,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:schedule:edit','#','admin','2026-11-16 22:43:27','',NULL,''),(3844,'排班删除',3840,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:schedule:remove','#','admin','2026-11-16 22:43:27','',NULL,''),(3845,'签到签退',3840,5,'',NULL,NULL,'',1,0,'F','0','0','lawyers:schedule:check','#','admin','2026-11-16 22:43:27','',NULL,''),(3850,'外呼结果',3669,5,'result','lawyers/outbound/result',NULL,'',1,0,'C','0','0','lawyers:outbound:result:list','chart','admin','2026-11-16 22:43:27','',NULL,'外呼结果查询'),(3851,'结果查询',3850,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:outbound:result:query','#','admin','2026-11-16 22:43:27','',NULL,''),(3852,'结果导出',3850,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:outbound:result:export','#','admin','2026-11-16 22:43:27','',NULL,''),(3860,'IVR执行日志',3669,6,'executionLog','lawyers/ivr/executionLog',NULL,'',1,0,'C','0','0','lawyers:ivr:executionLog:list','log','admin','2026-11-16 22:43:27','',NULL,'IVR流程执行日志'),(3861,'日志查询',3860,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ivr:executionLog:query','#','admin','2026-11-16 22:43:27','',NULL,''),(3862,'日志导出',3860,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ivr:executionLog:export','#','admin','2026-11-16 22:43:27','',NULL,''),(3870,'智能质检',0,60,'quality','lawyers/quality/inspection',NULL,'',1,0,'C','0','0','lawyers:quality:list','documentation','admin','2026-11-16 22:43:29','',NULL,'智能质检管理（T4-1）'),(3871,'质检详情',3870,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:quality:query','#','admin','2026-11-16 22:43:29','',NULL,''),(3872,'质检复核',3870,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:quality:review','#','admin','2026-11-16 22:43:29','',NULL,''),(3873,'触发质检',3870,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:quality:inspect','#','admin','2026-11-16 22:43:29','',NULL,''),(3874,'质检导出',3870,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:quality:export','#','admin','2026-11-16 22:43:29','',NULL,''),(3880,'消息中心',3670,62,'message','lawyers/message/index',NULL,'',1,0,'C','0','0','lawyers:message:list','message','admin','2026-11-16 22:43:30','',NULL,'消息中心-站内信列表'),(3881,'消息查询',3880,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:message:query','#','admin','2026-11-16 22:43:30','',NULL,''),(3882,'消息已读',3880,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:message:read','#','admin','2026-11-16 22:43:30','',NULL,''),(3883,'消息删除',3880,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:message:remove','#','admin','2026-11-16 22:43:30','',NULL,''),(3890,'仿真评测',3500,9,'simulation','lawyers/simulation/index','','',1,0,'C','0','0','lawyers:modelConfig:test','cog','admin','2026-11-16 22:43:43','',NULL,'坐席辅助/语音机器人上线前仿真回归（提交/进度/报告，case·check·turn 通过率）'),(3900,'法服协同',0,60,'/collab',NULL,NULL,'',1,0,'M','0','0',NULL,'guide','admin','2026-11-16 22:43:32','',NULL,'公共法律服务协同（第十五部分F组）'),(3910,'协同机构台账',3900,1,'externalOrg','lawyers/collab/externalOrg/index',NULL,'',1,0,'C','0','0','lawyers:externalOrg:list','peoples','admin','2026-11-16 22:43:32','',NULL,'法援/调解/公证/鉴定/仲裁/12345机构台账'),(3911,'机构查询',3910,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:externalOrg:query','#','admin','2026-11-16 22:43:32','',NULL,''),(3912,'机构新增',3910,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:externalOrg:add','#','admin','2026-11-16 22:43:32','',NULL,''),(3913,'机构修改',3910,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:externalOrg:edit','#','admin','2026-11-16 22:43:32','',NULL,''),(3914,'机构删除',3910,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:externalOrg:remove','#','admin','2026-11-16 22:43:32','',NULL,''),(3920,'转办协同',3900,2,'ticketTransfer','lawyers/collab/transfer/index',NULL,'',1,0,'C','0','0','lawyers:ticketTransfer:list','swap','admin','2026-11-16 22:43:32','',NULL,'司法行政条线/12345工单双向转办'),(3921,'转办查询',3920,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ticketTransfer:query','#','admin','2026-11-16 22:43:32','',NULL,''),(3922,'发起转办',3920,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ticketTransfer:add','#','admin','2026-11-16 22:43:32','',NULL,''),(3923,'转办重试',3920,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ticketTransfer:retry','#','admin','2026-11-16 22:43:32','',NULL,''),(3924,'转办导出',3920,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:ticketTransfer:export','#','admin','2026-11-16 22:43:32','',NULL,''),(3930,'渠道身份',3900,3,'channelIdentity','lawyers/collab/channel/index',NULL,'',1,0,'C','0','0','lawyers:channelIdentity:list','wechat','admin','2026-11-16 22:43:32','',NULL,'多渠道身份绑定查询/解绑'),(3931,'身份查询',3930,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:channelIdentity:query','#','admin','2026-11-16 22:43:32','',NULL,''),(3932,'身份解绑',3930,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:channelIdentity:unbind','#','admin','2026-11-16 22:43:32','',NULL,''),(3940,'SLA策略',3900,4,'slaPolicy','lawyers/collab/slaPolicy/index',NULL,'',1,0,'C','0','0','lawyers:slaPolicy:list','time-range','admin','2026-11-16 22:43:32','',NULL,'工单SLA时限与逐级升级策略'),(3941,'策略查询',3940,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:slaPolicy:query','#','admin','2026-11-16 22:43:32','',NULL,''),(3942,'策略新增',3940,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:slaPolicy:add','#','admin','2026-11-16 22:43:32','',NULL,''),(3943,'策略修改',3940,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:slaPolicy:edit','#','admin','2026-11-16 22:43:32','',NULL,''),(3944,'策略删除',3940,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:slaPolicy:remove','#','admin','2026-11-16 22:43:32','',NULL,''),(3945,'SLA看板',3900,5,'slaBoard','lawyers/collab/slaBoard/index',NULL,'',1,0,'C','0','0','lawyers:slaBoard:view','dashboard','admin','2026-11-16 22:43:32','',NULL,'工单SLA达成率/超时TOP/临近超时/跨域流转可视化看板（F9）'),(3950,'知识审核',3900,5,'knowledgeAudit','lawyers/knowledgeAudit/index',NULL,'',1,0,'C','0','0','lawyers:knowledge:audit:list','validCode','admin','2026-11-16 22:43:32','',NULL,'知识库权威溯源审核发布流'),(3951,'审核查询',3950,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:knowledge:audit:query','#','admin','2026-11-16 22:43:32','',NULL,''),(3952,'提交审核',3950,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:knowledge:audit:submit','#','admin','2026-11-16 22:43:32','',NULL,''),(3953,'审核发布',3950,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:knowledge:audit:approve','#','admin','2026-11-16 22:43:32','',NULL,''),(3954,'知识下线',3950,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:knowledge:audit:offline','#','admin','2026-11-16 22:43:32','',NULL,''),(3960,'回访满意度',3900,6,'visitSatisfaction','lawyers/collab/visitSatisfaction/index',NULL,'',1,0,'C','0','0','lawyers:visitBoard:view','chart','admin','2026-11-16 22:43:33','',NULL,'F5 智能回访：满意度归因(原因TOP/情绪极性)+图文评价+回访员排行+逾期三级升级监控'),(3970,'高频置底',3670,14,'hotspotSuppress','lawyers/hotspotSuppress/index',NULL,'',1,0,'C','0','0','lawyers:hotspot:list','filter','admin','2026-11-16 22:43:33','',NULL,'高频来电/外呼置底规则管理'),(3971,'置底查询',3970,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:hotspot:query','#','admin','2026-11-16 22:43:33','',NULL,NULL),(3972,'置底新增',3970,2,'',NULL,NULL,'',1,0,'F','0','0','lawyers:hotspot:add','#','admin','2026-11-16 22:43:33','',NULL,NULL),(3973,'置底修改',3970,3,'',NULL,NULL,'',1,0,'F','0','0','lawyers:hotspot:edit','#','admin','2026-11-16 22:43:33','',NULL,NULL),(3974,'置底删除',3970,4,'',NULL,NULL,'',1,0,'F','0','0','lawyers:hotspot:remove','#','admin','2026-11-16 22:43:33','',NULL,NULL),(3980,'统计报表',3666,9,'statistics','lawyers/statistics/index',NULL,'',1,0,'C','0','0','lawyers:report:view','chart','admin','2026-11-16 22:43:35','',NULL,'P3-D6 独立多维统计报表（呼叫/坐席服务/质检/业务工单，日周月维度+导出）'),(3981,'报表导出',3980,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:report:export','#','admin','2026-11-16 22:43:35','',NULL,'统计报表 Excel 导出'),(3982,'队列监控',3666,10,'queueMonitor','lawyers/queueMonitor/index',NULL,'',1,0,'C','0','0','lawyers:queueMonitor:list','list','admin','2026-11-16 22:43:35','',NULL,'P3-H1 Stream 队列水位/积压/死信监控与死信重投'),(3983,'死信重投',3982,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:queueMonitor:replay','#','admin','2026-11-16 22:43:35','',NULL,'Stream 死信重投与删除'),(3984,'调用明细导出',3985,1,'',NULL,NULL,'',1,0,'F','0','0','lawyers:modelCost:export','#','admin','2026-11-16 22:43:35','',NULL,'大模型调用明细 Excel 导出'),(3985,'模型成本',3666,10,'modelCost','lawyers/modelCost/index',NULL,'',1,0,'C','0','0','lawyers:modelCost:view','money','admin','2026-11-16 22:43:35','',NULL,'P3-E5 大模型调用成本与质量看板（调用量/Token/估算费用/成功率/耗时/单位通话成本）'),(3986,'PII加密迁移',3644,3,'','','','',1,0,'F','0','0','lawyers:pii:crypt','#','admin','2026-11-16 22:43:38','',NULL,'G1-b PII 存量加密迁移');
/*!40000 ALTER TABLE `sys_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob COMMENT '公告内容',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知公告表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_notice`
--

LOCK TABLES `sys_notice` WRITE;
/*!40000 ALTER TABLE `sys_notice` DISABLE KEYS */;
INSERT INTO `sys_notice` VALUES (1,'温馨提醒：2018-07-01 若依新版本发布啦','2',0xE696B0E78988E69CACE58685E5AEB9,'0','admin','2026-11-16 22:43:16','',NULL,'管理员'),(2,'维护通知：2018-07-01 若依系统凌晨维护','1',0xE7BBB4E68AA4E58685E5AEB9,'0','admin','2026-11-16 22:43:16','',NULL,'管理员');
/*!40000 ALTER TABLE `sys_notice` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_oper_log`
--

DROP TABLE IF EXISTS `sys_oper_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求方式',
  `operator_type` int DEFAULT '0' COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_oper_log`
--

LOCK TABLES `sys_oper_log` WRITE;
/*!40000 ALTER TABLE `sys_oper_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_oper_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_post`
--

DROP TABLE IF EXISTS `sys_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_post`
--

LOCK TABLES `sys_post` WRITE;
/*!40000 ALTER TABLE `sys_post` DISABLE KEYS */;
INSERT INTO `sys_post` VALUES (1,'ceo','董事长',1,'0','admin','2026-11-16 22:43:16','',NULL,''),(2,'se','项目经理',2,'0','admin','2026-11-16 22:43:16','',NULL,''),(3,'hr','人力资源',3,'0','admin','2026-11-16 22:43:16','',NULL,''),(4,'user','普通员工',4,'0','admin','2026-11-16 22:43:16','',NULL,'');
/*!40000 ALTER TABLE `sys_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) COLLATE utf8mb4_general_ci DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) DEFAULT '1' COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) DEFAULT '1' COMMENT '部门树选择项是否关联显示',
  `status` char(1) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

LOCK TABLES `sys_role` WRITE;
/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'超级管理员','admin',1,'1',1,1,'0','0','admin','2026-11-16 22:43:16','',NULL,'超级管理员'),(2,'普通角色','common',2,'2',1,1,'0','0','admin','2026-11-16 22:43:16','',NULL,'普通角色'),(10,'班长','ai_team_leader',10,'1',1,1,'0','0','admin','2026-11-16 22:43:35','',NULL,'SLA 升级目标角色：班组负责人'),(11,'主管','ai_manager',11,'1',1,1,'0','0','admin','2026-11-16 22:43:35','',NULL,'SLA 升级目标角色：部门主管'),(12,'主任','ai_director',12,'1',1,1,'0','0','admin','2026-11-16 22:43:35','',NULL,'SLA 升级目标角色：中心主任');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_dept`
--

DROP TABLE IF EXISTS `sys_role_dept`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_dept` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`,`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和部门关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_dept`
--

LOCK TABLES `sys_role_dept` WRITE;
/*!40000 ALTER TABLE `sys_role_dept` DISABLE KEYS */;
INSERT INTO `sys_role_dept` VALUES (2,100),(2,101),(2,105);
/*!40000 ALTER TABLE `sys_role_dept` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_role_menu`
--

DROP TABLE IF EXISTS `sys_role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

LOCK TABLES `sys_role_menu` WRITE;
/*!40000 ALTER TABLE `sys_role_menu` DISABLE KEYS */;
INSERT INTO `sys_role_menu` VALUES (1,3000),(1,3100),(1,3101),(1,3102),(1,3103),(1,3104),(1,3105),(1,3200),(1,3201),(1,3202),(1,3203),(1,3204),(1,3205),(1,3206),(1,3207),(1,3300),(1,3301),(1,3302),(1,3303),(1,3304),(1,3305),(1,3400),(1,3410),(1,3411),(1,3412),(1,3413),(1,3414),(1,3415),(1,3416),(1,3417),(1,3418),(1,3420),(1,3421),(1,3422),(1,3423),(1,3424),(1,3425),(1,3426),(1,3427),(1,3428),(1,3500),(1,3510),(1,3520),(1,3521),(1,3523),(1,3524),(1,3525),(1,3526),(1,3527),(1,3530),(1,3531),(1,3532),(1,3533),(1,3534),(1,3535),(1,3536),(1,3537),(1,3538),(1,3540),(1,3541),(1,3542),(1,3543),(1,3544),(1,3545),(1,3546),(1,3547),(1,3550),(1,3551),(1,3552),(1,3553),(1,3554),(1,3555),(1,3556),(1,3557),(1,3560),(1,3561),(1,3562),(1,3563),(1,3570),(1,3600),(1,3601),(1,3602),(1,3603),(1,3604),(1,3605),(1,3606),(1,3607),(1,3610),(1,3611),(1,3612),(1,3613),(1,3614),(1,3615),(1,3616),(1,3620),(1,3621),(1,3622),(1,3623),(1,3624),(1,3625),(1,3626),(1,3627),(1,3628),(1,3629),(1,3630),(1,3631),(1,3632),(1,3633),(1,3634),(1,3635),(1,3636),(1,3637),(1,3638),(1,3639),(1,3640),(1,3641),(1,3642),(1,3643),(1,3644),(1,3645),(1,3646),(1,3647),(1,3648),(1,3649),(1,3650),(1,3651),(1,3652),(1,3653),(1,3654),(1,3655),(1,3656),(1,3657),(1,3658),(1,3659),(1,3660),(1,3661),(1,3662),(1,3663),(1,3664),(1,3665),(1,3666),(1,3667),(1,3668),(1,3669),(1,3670),(1,3671),(1,3672),(1,3673),(1,3674),(1,3675),(1,3680),(1,3681),(1,3682),(1,3683),(1,3684),(1,3685),(1,3690),(1,3720),(1,3721),(1,3722),(1,3723),(1,3724),(1,3725),(1,3730),(1,3731),(1,3732),(1,3733),(1,3734),(1,3735),(1,3740),(1,3741),(1,3742),(1,3743),(1,3744),(1,3745),(1,3750),(1,3751),(1,3752),(1,3753),(1,3754),(1,3755),(1,3760),(1,3761),(1,3762),(1,3763),(1,3764),(1,3765),(1,3766),(1,3770),(1,3771),(1,3772),(1,3773),(1,3774),(1,3775),(1,3780),(1,3781),(1,3782),(1,3783),(1,3790),(1,3791),(1,3792),(1,3793),(1,3794),(1,3795),(1,3796),(1,3797),(1,3798),(1,3799),(1,3800),(1,3801),(1,3802),(1,3803),(1,3810),(1,3811),(1,3812),(1,3880),(1,3890),(1,3900),(1,3910),(1,3911),(1,3912),(1,3913),(1,3914),(1,3920),(1,3921),(1,3922),(1,3923),(1,3924),(1,3930),(1,3931),(1,3932),(1,3940),(1,3941),(1,3942),(1,3943),(1,3944),(1,3945),(1,3950),(1,3951),(1,3952),(1,3953),(1,3954),(1,3960),(1,3970),(1,3971),(1,3972),(1,3973),(1,3974),(1,3980),(1,3981),(1,3982),(1,3983),(1,3984),(1,3985),(1,3986),(2,1),(2,2),(2,3),(2,4),(2,100),(2,101),(2,102),(2,103),(2,104),(2,105),(2,106),(2,107),(2,108),(2,109),(2,110),(2,111),(2,112),(2,113),(2,114),(2,115),(2,116),(2,117),(2,500),(2,501),(2,1000),(2,1001),(2,1002),(2,1003),(2,1004),(2,1005),(2,1006),(2,1007),(2,1008),(2,1009),(2,1010),(2,1011),(2,1012),(2,1013),(2,1014),(2,1015),(2,1016),(2,1017),(2,1018),(2,1019),(2,1020),(2,1021),(2,1022),(2,1023),(2,1024),(2,1025),(2,1026),(2,1027),(2,1028),(2,1029),(2,1030),(2,1031),(2,1032),(2,1033),(2,1034),(2,1035),(2,1036),(2,1037),(2,1038),(2,1039),(2,1040),(2,1041),(2,1042),(2,1043),(2,1044),(2,1045),(2,1046),(2,1047),(2,1048),(2,1049),(2,1050),(2,1051),(2,1052),(2,1053),(2,1054),(2,1055),(2,1056),(2,1057),(2,1058),(2,1059),(2,1060);
/*!40000 ALTER TABLE `sys_role_menu` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) COLLATE utf8mb4_general_ci DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `lawyer_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '是否为律师（0否 1是）',
  `lawyer_license` varchar(50) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '执业证号',
  `law_firm` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '所属律所',
  `specialty` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '专业领域（多个用逗号分隔）',
  `practice_years` int DEFAULT NULL COMMENT '执业年限',
  `lawyer_intro` text COLLATE utf8mb4_general_ci COMMENT '律师简介',
  `agent_id` bigint DEFAULT NULL COMMENT '坐席工号（关联 ai_call_agent_status.agent_id，空表示非坐席）',
  `sip_extension` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT 'SIP分机号（需与 FreeSWITCH 分机一致）',
  `call_mode` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '应答模式（0自动 1手动）',
  `email` varchar(50) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '手机号码',
  `sex` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '密码',
  `status` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) COLLATE utf8mb4_general_ci DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `pwd_update_date` datetime DEFAULT NULL COMMENT '密码最后更新时间',
  `create_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_sys_user_agent_id` (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,103,'admin','若依','00','0',NULL,NULL,NULL,NULL,NULL,100,NULL,'0','ry@163.com','15888888888','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-11-16 22:43:16','2026-11-16 22:43:16','admin','2026-11-16 22:43:16','',NULL,'管理员'),(2,105,'ry','若依','00','0',NULL,NULL,NULL,NULL,NULL,101,NULL,'0','ry@qq.com','15666666666','1','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','127.0.0.1','2026-11-16 22:43:16','2026-11-16 22:43:16','admin','2026-11-16 22:43:16','',NULL,'测试员'),(100,103,'testuser','测试用户','00','0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'0','test@example.com','13800138000','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','','2026-11-16 22:43:17','2026-11-16 22:43:17','admin','2026-11-16 22:43:17','',NULL,'测试用户'),(101,103,'user01','普通用户01','01','0',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'0','user01@example.com','13800138001','0','','$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2','0','0','','2026-11-16 22:43:18','2026-11-16 22:43:18','admin','2026-11-16 22:43:18','',NULL,'普通用户01'),(1001,100,'zhangls','张律师','00','1',NULL,'12348法律服务中心','婚姻家事、离婚财产分割、子女抚养',NULL,NULL,102,'1003','0','','','0','','$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy','0','0','',NULL,NULL,'admin','2026-11-16 22:43:29','',NULL,'婚姻家事坐席'),(1002,100,'lils','李律师','00','1',NULL,'12348法律服务中心','婚姻家事、遗产继承、财产公证',NULL,NULL,103,'1004','0','','','1','','$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy','0','0','',NULL,NULL,'admin','2026-11-16 22:43:29','',NULL,'婚姻家事坐席'),(1003,100,'wangls','王律师','00','1',NULL,'12348法律服务中心','合同纠纷、买卖合同、债务债权',NULL,NULL,104,'1005','0','','','0','','$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy','0','0','',NULL,NULL,'admin','2026-11-16 22:43:29','',NULL,'合同纠纷坐席'),(1004,100,'zhaols','赵律师','00','1',NULL,'12348法律服务中心','合同纠纷、劳动合同、建筑工程',NULL,NULL,105,'1006','0','','','1','','$2a$10$mkzAyQsDSiuRbcSPnZGNCuMsDMC/EjhUs3eF2agwPpTPgjNcoJ8Gy','0','0','',NULL,NULL,'admin','2026-11-16 22:43:29','',NULL,'合同纠纷坐席');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_post`
--

DROP TABLE IF EXISTS `sys_user_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_post`
--

LOCK TABLES `sys_user_post` WRITE;
/*!40000 ALTER TABLE `sys_user_post` DISABLE KEYS */;
INSERT INTO `sys_user_post` VALUES (1,1),(2,2),(1001,4),(1002,4),(1003,4),(1004,4);
/*!40000 ALTER TABLE `sys_user_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

LOCK TABLES `sys_user_role` WRITE;
/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1),(2,2),(100,2),(101,2),(1001,2),(1002,2),(1003,2),(1004,2);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
