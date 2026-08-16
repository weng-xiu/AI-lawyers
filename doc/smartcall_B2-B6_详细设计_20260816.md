# SmartCall 深度融合 B2–B6 详细设计方案

**文档日期**：2026-08-16
**配套文档**：smartcall_需求分析与融合方案_20260801.md
**B1 状态**：已完成（智能体对话节点 agentChat，含 local RAG + Redis/MySQL 双写）
**调研基线**：2026-08-16 代码快照

---

## 〇、现状基线（事实，决定设计边界）

| 能力 | 现状 | 对 B 项的影响 |
|------|------|--------------|
| WebSocket | 已有 `/ws/chat/{sessionId}` 房间广播，**无 @OnMessage、无语音通道** | B2 需新增语音专用端点 |
| ASR/TTS | 已有 `AsrEngine.transcribe(byte[])` / `TtsEngine.synthesize(text)`，**全为同步整段接口** | B2 需扩展流式接口，不破坏现有同步实现 |
| 语音实现 | OpenAI兼容ASR(HTTP整段)、DashScope TTS(异步任务轮询)、Mock | B2 流式需新增 Provider 或能力开关 |
| Asterisk AMI | 已有 `AsteriskGatewayAdapter`（Originate/Hangup/Redirect/SIPshowpeer，原生 Socket），**无 PJSIP、无 AMI 事件监听** | B3 扩展事件监听，不重写 |
| FreeSWITCH ESL | 已有适配器 | B3 同类扩展 |
| 中继 `ai_call_trunk` | 完整实体/服务/监控（容量、质量、熔断、告警） | B3/B4 直接复用 |
| 坐席状态 `ai_call_agent_status` | 已有完整 CRUD + 登录/呼叫/保持/转接/咨询/三方/话后；`status`(0离线1在线2忙碌3休息)、`callStatus`(0空闲..5话后)；**无实时推送** | B5 复用，B3/B4 推送增强 |
| 统计 | 通话统计(总量/完成/转接/未接/均长；按坐席/分类/日期)、中继监控 overview/trend、工作台聚合；**无独立坐席效能报表、无独立大屏** | B4 新建聚合层 |
| 技能组/ACD | **完全缺失**：无 ai_skill_group 表、无技能组实体、无动态分配；IVR `agent/transfer` 目标为节点静态配置的 agentId/extension/number | B5 从零建 |
| 短信 SMS | **完全缺失**：无 Service/SDK/配置，IVR 无 sms 节点 | B6 从零建 |
| 通知 | ai_notice 公告、ai_todo 待办、sys_notice；均为拉取模型 | B6 可复用为发送留痕 |

**设计原则**：
1. 不破坏现有同步语音接口与网关适配器（B2/B3 以"扩展"为主）。
2. B1 的 `agentChat` 转人工结果（`agentHandoff`、`agentCategoryId`）作为 B5 分配的输入，形成闭环。
3. 无 PBX 环境可验证的（B5/B6/B4）走"在线调试 + 模拟"；强依赖 PBX 的（B2 真媒体流、B3 AMI 事件）提供 Mock 降级。
4. Java 8 + Spring Boot 2.5 + Vue2 + Element UI，不引入 Spring WebFlux/Reactor（B2 流式用 javax.WebSocket + 阻塞队列实现，避免 reactive 栈）。

---

## 一、B5 智能队列分配（优先级最高，仅次于已完成的 B1）

### 1.1 目标
把 IVR `agent/transfer` 节点从"静态目标"升级为"按技能组 + 策略动态选坐席"，承接 B1 `agentChat` 的转人工意图（携带 `agentCategoryId` 映射技能组）。

### 1.2 数据表（3 张）

**`ai_skill_group`（技能组）**
| 字段 | 类型 | 说明 |
|------|------|------|
| group_id | bigint PK | |
| group_name | varchar(80) | 技能组名称（民事/刑事/劳动/婚姻…） |
| group_code | varchar(40) unique | 编码 |
| category_id | bigint | 关联咨询分类（B1 的 agentCategoryId 自动映射） |
| strategy | varchar(20) | 分配策略：round_robin 轮询 / least_recent 最久未接 / least_calls 最少通话 / all_ring 全员振铃 |
| max_wait | int | 最大排队等待秒（超时溢出/转语音信箱） |
| wrap_up_time | int | 话后整理秒（callStatus=5 期间不分配） |
| service_level_threshold | int | 服务水平阈值秒（大屏统计用） |
| overflow_group_id | bigint | 溢出技能组（无可用坐席时） |
| status / 标准字段 | | 0停用 1启用 |

**`ai_skill_group_member`（技能组成员）**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| group_id | bigint | |
| agent_id | bigint | 关联 ai_call_agent_status.agent_id |
| skill_level | int | 技能等级 1-5（高优先级分配给高等级） |
| priority | int | 组内优先级（同等级按此） |
| max_concurrent | int | 个人最大并发（默认1） |
| status | char(1) | 0禁用 1启用 |

**`ai_call_queue`（排队/分配流水）**
| 字段 | 类型 | 说明 |
|------|------|------|
| queue_id | bigint PK | |
| session_id | varchar(64) | IVR sessionId |
| record_id | bigint | 通话记录 |
| caller_number | varchar(20) | |
| group_id | bigint | 目标技能组 |
| enqueue_time | datetime | |
| dequeue_time | datetime | |
| agent_id | bigint | 分配到的坐席 |
| wait_duration | int | 等待秒 |
| queue_status | char(1) | 0排队中 1已分配 2超时溢出 3已放弃 4无可用坐席 |
| strategy_used | varchar(20) | 实际命中策略 |
| priority | int | 排队优先级（VIP/情绪激动可提升） |

### 1.3 代码结构
```
ai-system/.../domain/lawyers/skill/
  AiSkillGroup.java / AiSkillGroupMember.java / AiCallQueue.java
mapper/lawyers/skill/ + xml（3套）
service/lawyers/skill/
  IAiSkillGroupService.java            # 技能组 CRUD
  IAgentDispatchService.java           # 分配核心接口
  impl/AgentDispatchServiceImpl.java   # 4种策略实现 + 排队 + 溢出
ai-admin/.../controller/lawyers/skill/
  AiSkillGroupController.java          # 技能组/成员管理
  AgentDispatchController.java         # 排队监控/手动分配/踢除
ai-ui/src/api/lawyers/skill.js
ai-ui/src/views/lawyers/skill/
  group.vue      # 技能组+成员管理（左右布局）
  queue.vue      # 实时排队监控（轮询，B3 后可升级 WS 推送）
```

### 1.4 核心实现要点
- `AgentDispatchResult dispatch(Long groupId, DispatchContext ctx)`：
  1. 查组成员（status=1 且坐席 `status=1 在线` 且 `callStatus=0 空闲`，且未在话后整理期）；
  2. 按 skill_level desc, priority desc 排序后按策略选：
     - round_robin：Redis 记 `skill:rr:{groupId}` 指针轮询；
     - least_recent：取 `call_start_time` 最早/最久未通话者（查 ai_call_agent_status）；
     - least_calls：查当日该坐席 completed_count 最小者；
     - all_ring：返回全员列表（由 PBX 层并行振铃，本期模拟直接取第一个）；
  3. 无可用坐席：写 `ai_call_queue`（queue_status=0 排队），返回"排队中"，并设超时任务（B6 短信/公告可通知）；
  4. 有可用：写 queue_status=1，调用现有 `AiCallAgentStatusServiceImpl` 的呼叫/占用方法置 callStatus=1；
  5. 溢出：超时或无可用且配了 overflow_group_id 则递归分配一次。
- **IVR 引擎接入**：改造 `IvrEngineServiceImpl` 的 `agent/transfer` case：
  - 节点配置增加 `dispatchMode`（static/dispatch）；
  - dispatch 模式下从 `variables.get("agentCategoryId")`（B1 写入）反查技能组 `category_id`，调用 `agentDispatchService.dispatch(...)`；
  - 分配成功 target = 坐席分机；排队中则进入"等待"分支（可连线到 say 播报排队位置，再循环判断）；
  - 保留 static 模式完全兼容旧流程。
- 排队位置：`ai_call_queue` 同组 queue_status=0 且 enqueue_time 早于当前的 count。
- 不依赖 PBX：分配结果先写库/返回分机号，真正 bridge 由现有 gateway 适配器（B3 完善事件后回写 call_status）。

### 1.5 验收标准
1. 技能组/成员 CRUD 正常，可按咨询分类绑定技能组；
2. 4 种策略各构造 3 个不同状态坐席，分配结果符合策略定义；
3. 全员忙时来电进入排队，queue_status=0，能查询排队位置；
4. 有坐席空闲后（手动置空闲）再次 dispatch 能分配成功并写 wait_duration；
5. 超时溢出到 overflow_group_id，无溢出则 queue_status=4；
6. B1 流程 agentChat→agent：`agentCategoryId=婚姻`自动分配到"婚姻家事"技能组坐席；
7. 旧流程（static 配置 agentId/extension）行为不变；
8. 无 PBX 环境下用 Controller 手动触发 dispatch 接口可完整验证。

---

## 二、B6 短信通知节点（无 PBX 依赖，立即可做）

### 2.1 目标
IVR 新增 `sms` 节点；流程中可给来电者发短信（如排队告知、工单编号、法律文书链接、满意度回访链接）。抽象多供应商（阿里云 dysms / 腾讯云 / Mock），与现有 TTS/ASR 的 Provider 模式一致。

### 2.2 数据表（2 张）

**`ai_sms_config`（短信通道配置）**
| 字段 | 类型 | 说明 |
|------|------|------|
| config_id | bigint PK | |
| config_name | varchar(80) | 通道名称 |
| provider | varchar(20) | mock/aliyun/tencent |
| access_key_id / access_key_secret | varchar | |
| sign_name | varchar(50) | 短信签名 |
| region_id | varchar(40) | 地域（阿里云） |
| sdk_app_id | varchar(60) | 腾讯云 AppId |
| daily_limit | int | 单号码日发送上限（防骚扰） |
| status / 标准字段 | | 0停用 1启用 |

**`ai_sms_template`（短信模板）**
| 字段 | 类型 | 说明 |
|------|------|------|
| template_id | bigint PK | |
| template_name | varchar(80) | |
| provider_template_code | varchar(60) | 供应商模板CODE（如 SMS_123456） |
| content | varchar(500) | 模板内容，含 ${变量} 占位（本地渲染/审核用） |
| scene_type | varchar(30) | queue/welcome/ticket/visit/generic |
| config_id | bigint | 所属通道 |
| status | char(1) | |

**`ai_sms_log`（发送记录，可不要独立表改用 sys_oper_log？建议独立）**
| 字段 | 类型 | 说明 |
|------|------|------|
| log_id | bigint PK | |
| phone | varchar(20) | 被叫号码 |
| template_id / config_id | bigint | |
| params_json | varchar(500) | 模板变量 |
| content | varchar(500) | 实际发送内容 |
| send_status | char(1) | 0待发 1成功 2失败 |
| provider_msg_id | varchar(80) | 供应商回执ID |
| fail_reason | varchar(255) | |
| session_id / record_id | | 关联 IVR 会话/通话（可空） |
| create_time | datetime | |

### 2.3 代码结构
```
ai-system/.../service/lawyers/sms/
  ISmsService.java                  # send(phone, templateId, params, sessionId, recordId)
  ISmsProvider.java                 # 统一供应商接口：SmsResult send(SmsRequest)
  impl/provider/MockSmsProvider.java
  impl/provider/AliyunSmsProvider.java   # 反射/可选依赖，无SDK时降级
  impl/provider/TencentSmsProvider.java
  impl/SmsServiceImpl.java          # 限流（日上限）、模板渲染、选通道、记录日志
domain/mapper/xml: SmsConfig/SmsTemplate/SmsLog
ai-admin/.../controller/lawyers/sms/
  AiSmsConfigController.java / AiSmsTemplateController.java / AiSmsLogController.java
IVR 引擎：IvrEngineServiceImpl 新增 NODE_SMS = "sms" case + executeSms
ai-ui: api/lawyers/sms.js + views/lawyers/sms/ (config/template/log 三个页或合并)
设计器 designer.vue：TYPE_META 加 sms(蓝色图标✉)、PALETTE、defaultConfig({configId,templateId,phoneVar:'callerNumber',paramsJson})、属性面板
```

### 2.4 实现要点
- `ISmsProvider` 接口与现有 `AsrEngine/TtsEngine` 同构，按 `provider` 从 Spring 容器取实现（`@Component("aliyunSmsProvider")` + 工厂映射）。
- 阿里云 SDK 做成 `optional` 依赖（`provided` 或反射加载）：无 SDK jar 时 `AliyunSmsProvider.supports()=false`，SmsServiceImpl 自动降级到 Mock，保证无凭证环境可启动可测试。
- 日限流：Redis key `sms:limit:{phone}:{yyyyMMdd}` incr，超限拒绝并记日志。
- 模板变量：IVR 节点 paramsJson 用 SpEL 渲染（`#callerNumber`、`#ticketNo`、流程变量），与现有 say 节点的 render 一致；phone 默认取 `request.getCallerNumber()`。
- `executeSms`：读取 configId/templateId/phoneVar/paramsJson → 渲染 → 调 smsService.send → 写 `variables.put("smsStatus"/"smsMsgId")` → step detail "发送短信至 xxx：成功/失败" → nextNode。短信失败**不阻断**流程（try/catch，smsStatus=fail 后仍走向下一节点，可用条件分支判断）。
- 与 B5 联动：排队超时节点可发短信"您当前排队第N位，可访问xxx预约"；工单创建后发短信告知工单号。

### 2.5 验收标准
1. Mock 通道下，IVR 测试运行走到 sms 节点能在日志表看到记录、content 正确渲染变量；
2. 配置阿里云通道（有凭证）时真实发送成功，provider_msg_id 落库；无凭证时自动降级 Mock 不报错；
3. 同号码超过 daily_limit 被限流，send_status=2 且有 fail_reason；
4. 模板 paramsJson 中 `${#ticketNo}` 能取到流程变量；phoneVar 可指定任意流程变量；
5. 短信发送失败不中断 IVR 后续节点；
6. 设计器可拖拽配置 sms 节点，保存/刷新配置回显；
7. 短信日志页可按号码/时间/状态筛选、查看内容。

---

## 三、B4 数据大屏与坐席效能报表（无 PBX 依赖）

### 3.1 目标
在现有通话统计、中继监控基础上，新建统一"运营大屏"聚合接口与坐席效能报表，前端用 ECharts 呈现。复用现有数据源（ai_call_record、ai_call_agent_status、ai_outbound_task/result、ai_call_dial_log、ai_call_ticket、ai_agent_message、ai_call_queue(B5)）。

### 3.2 数据表
**不新建业务表**。可选新建 1 张分钟级统计表用于大屏性能（数据量大时）：
- `ai_stat_minute`（stat_time, dimension, metric_key, metric_value）—— 本期可先用现有表实时聚合 + Redis 缓存（30s~60s TTL），数据量上来再加物化表，避免过度设计。

### 3.3 代码结构
```
ai-system/.../service/lawyers/stat/
  IDashboardService.java
  impl/DashboardServiceImpl.java     # 多源聚合，Redis 缓存
  IAgentPerformanceService.java
  impl/AgentPerformanceServiceImpl.java
mapper/lawyers/stat/（新建聚合 Mapper，或复用现有 Mapper 加查询方法）
ai-admin/.../controller/lawyers/stat/
  DashboardController.java           # /lawyers/stat/dashboard/*
  AgentPerformanceController.java    # /lawyers/stat/performance/*
ai-ui/src/api/lawyers/stat.js
ai-ui/src/views/lawyers/dashboard/
  index.vue          # 大屏（深色主题，ECharts：呼叫趋势/接通率/AI占比/坐席负载/中继健康/排队/外呼进度）
ai-ui/src/views/lawyers/performance/
  agent.vue          # 坐席效能报表（表格 + 可选导出）
```

### 3.4 大屏聚合接口（`/lawyers/stat/dashboard`）
| 接口 | 指标 | 数据源 |
|------|------|--------|
| /summary | 今日总呼入、接通率、排队数、在线坐席数、AI 处理量、外呼完成率、满意度 | call_record + call_queue(B5) + agent_status + agent_message(B1) + outbound_task + ledger |
| /callTrend?hours=24 | 每分钟/每5分钟 呼入/接通/未接/AI转接 折线 | call_record by create_time |
| /categoryPie | 按咨询分类占比（婚姻/劳动/民事…） | call_record join category，或 agent_message.category_id |
| /agentLoad | 各在线坐席当前状态、今日通话数、均长、满意度 | agent_status + record 统计 |
| /trunkHealth | 各中继并发/接通率/告警（复用 TrunkMonitorService） | 现有 trunk monitor |
| /queueNow | 当前排队列表（B5） | ai_call_queue |
| /aiRatio | AI 独立解决 vs 转人工比例 | agent_message where handoff=0/1 |
| /outboundProgress | 各外呼任务进度条 | outbound_task |

实现要点：
- `DashboardServiceImpl` 每个聚合方法加 `@RedisCache(key="dash:xxx", ttl=30s)`（用现有 RedisCache）；
- 时间范围统一用参数，默认今日；
- 所有 SQL 走索引时间列，避免全表扫（call_record.create_time、dial_log.dial_time）。

### 3.5 坐席效能报表（`/lawyers/stat/performance/agent`）
列：坐席、签入时长（login_time→now/logout 之差）、就绪时长（status=1 且 callStatus=0 累计）、通话时长、话后时长、呼入接听数、呼出数、未接/漏接数、平均振铃时长、平均通话时长、转接数、AI 协访次数、满意度均值、首次解决率（无转工单/无转接占比）、服务水平（X秒内接听占比）。
- 支持按时间范围、技能组（B5）、坐席筛选；
- 导出 Excel（复用 ExcelUtil）；
- 部分指标现有表已有，部分需新增 Mapper 聚合（如签入/就绪时长需要状态变更流水——当前 `ai_call_agent_status` 只存当前状态，无历史。**方案**：新增轻量表 `ai_agent_status_log`（agent_id, from_status, to_status, from_call_status, to_call_status, log_time）由现有 `updateAgentStatus`/`makeCall`/`afterWork`/`hangup` 等方法在状态切换时插入一条；或本期效能报表只统计"通话类可回溯指标"，状态时长指标在 B3 引入事件流后补。建议本期加 status_log 表，成本低且为 B3 实时推送铺路）。

**新增 `ai_agent_status_log`（归入 B4 或 B5）**
| 字段 | 类型 |
|------|------|
| log_id / agent_id / user_id | bigint |
| from_status / to_status | char(1) |
| from_call_status / to_call_status | char(1) |
| duration | int（该状态持续秒，状态切换时回填上一条） |
| log_time | datetime |

### 3.6 验收标准
1. 大屏 /summary 返回全部指标且数值与数据库直查一致；
2. 7 个图表接口在 500ms 内返回（Redis 缓存生效，二次请求 < 50ms）；
3. 大屏页面 30s 自动刷新，无控制台报错；
4. 坐席效能报表按技能组/时间筛选正确，导出 Excel 内容一致；
5. 状态流水表在坐席登录/通话/话后/登出时正确记录，duration 计算正确；
6. AI 占比能反映 B1 agentChat 的独立解决率（handoff=0）；
7. 中继健康组件直接复用现有 TrunkMonitor 数据，无重复实现。

---

## 四、B2 实时 WebSocket 流式 ASR/TTS（强依赖 PBX/语音引擎，靠后）

### 4.1 目标
为坐席工作台/IVR 提供边说边转写（流式 ASR）和实时语音播报（流式 TTS）的能力。**本期在无真实语音媒体链路下，定义可对接的 WebSocket 协议 + Mock 流式 Provider，跑通前端实时字幕与 TTS 音频播放链路**；真实音频帧对接 Asterisk/FreeSWITCH 媒体流在 B3 之后。

### 4.2 现状与约束
- ASR/TTS 现仅同步整段接口（`byte[]` in/out）；
- 已有 `WebSocketConfig`（JSR-356），但 `/ws/chat` 无 @OnMessage；
- 不引入 WebFlux/Reactor（Java8 兼容），用 `javax.websocket.Session + 线程池 + BlockingQueue` 实现双向帧。

### 4.3 数据表
**不新建表**。ASR 转写结果若需留痕，复用 `ai_call_record` 增加 `asr_text` 字段（或写通话摘要）；实时字幕本身不持久化（可在挂断时整段保存）。可选新增 `ai_asr_session`（session_id, record_id, engine, partial_text, final_text, start/end_time）用于调试，建议后期按需。

### 4.4 代码结构
```
ai-framework/.../websocket/voice/
  VoiceWebSocketServer.java     # @ServerEndpoint("/ws/voice/{sessionId}/{role}")
  VoiceSessionManager.java      # 会话注册表（asr/tts 双向）
ai-system/.../service/lawyers/voice/
  StreamAsrEngine.java          # 接口：void startAudio(AsrCallback); void onAudio(byte[]); void finish();
  StreamTtsEngine.java          # 接口：void speak(String text, TtsCallback); void stop();
  callback: onPartial(String)/onFinal(String)/onAudio(byte[])/onError(Throwable)
  impl/stream/
    MockStreamAsrEngine.java    # 收到音频帧后随机/延迟回显模拟 partial（或对整段调同步 transcribe 拆字）
    MockStreamTtsEngine.java    # 把文本切片定时推空音频/文本帧
    DashScopeStreamAsrEngine.java # 预留（DashScope 实时 ASR WebSocket 对接）
    DashScopeStreamTtsEngine.java # 预留
  StreamVoiceManager.java       # 按 session 选择 engine + 桥接到 VoiceWebSocketServer
```

### 4.5 WebSocket 协议（JSON 帧）
客户端→服务端：
```json
{ "type": "start", "engine": "mock", "format": "pcm", "sampleRate": 16000 }
{ "type": "audio", "seq": 12, "data": "<base64 PCM帧>" }
{ "type": "tts", "text": "您好，这里是12348", "voice": "xxx" }
{ "type": "stop" }
```
服务端→客户端：
```json
{ "type": "asr_partial", "text": "我想咨询", "isFinal": false }
{ "type": "asr_final", "text": "我想咨询离婚财产分割" }
{ "type": "tts_audio", "seq": 1, "data": "<base64>", "isEnd": false }
{ "type": "tts_end" }
{ "type": "error", "message": "..." }
```

### 4.6 实现要点
- 端点用 `@OnMessage` 接收文本控制帧与二进制音频帧；二进制直接喂 `streamAsrEngine.onAudio(bytes)`。
- ASR 回调通过 `session.getBasicRemote().sendBinary/ sendText` 推回；用单线程 `SendTask` 队列保证线程安全（javax.websocket.RemoteEndpoint.Basic 串行）。
- 流式 Provider 接口作为现有同步接口的**扩展**（不改动 AsrEngine/TtsEngine），`VoiceEngineManager` 增加 `getStreamAsr/StreamTts`；真实 Provider 未配置时返回 Mock。
- 与 B1 联动：流式 ASR 的 `onFinal` 文本可直接作为 `agentChat` 的输入（坐席辅助场景：来电者说话→实时字幕→AI 给坐席建议）。但注意 IVR 中 answer 节点当前是"整段收声后转写"，流式改造属于可选增强，默认仍走同步，不强制。
- 鉴权：WebSocket 握手期用现有 JWT（query token 或 Sec-WebSocket-Protocol），复用 Spring Security 鉴权工具。
- 资源释放：@OnClose 调 `finish()/stop()`，移除会话，避免线程泄漏。

### 4.7 验收标准
1. 前端用原生 WebSocket 连接 `/ws/voice/xxx/agent`，发送 start + 若干 audio 帧，能收到 asr_partial/asr_final（Mock 引擎下回显或延迟模拟文本）；
2. 发送 tts 帧后能按序收到 tts_audio 分片和 tts_end，前端可用 AudioContext 播放 PCM（Mock 下至少协议跑通、无报错）；
3. 断网/关闭浏览器触发 @OnClose，后端会话清理、线程停止，无 OOM/线程泄漏（压测 100 连接）；
4. 未携带/非法 token 握手被拒（403）；
5. 同步 ASR/TTS 接口与现有 IVR answer/say 节点行为完全不受影响；
6. 配置真实 DashScope 凭证后，DashScope Stream Provider 能对接成功（无凭证环境自动 Mock，不启动失败）；
7. 坐席工作台能看到实时字幕区域（前端组件 `VoiceCaption.vue`）。

---

## 五、B3 Asterisk PJSIP 端点/队列模型与 AMI 事件监控（强依赖 PBX，最后）

### 5.1 目标
把现有"一次性 AMI 请求/响应"升级为"常驻 AMI 事件监听 + PJSIP 端点/队列模型"，实现：坐席真实状态由 PBX 事件驱动（不再仅靠 REST 手动置位）、通话状态实时回写、排队/振铃/桥接/挂断事件闭环。FreeSWITCH 同理扩展 ESL 事件订阅。

> 此项需要真实 Asterisk 22 + PJSIP 环境，无法在开发机端到端验证；以"可编译可运行 + 模拟器/Mock 事件 + 接口契约正确"为验收标准。

### 5.2 数据表
**不新建业务表**，复用：`ai_call_trunk`（中继/端点）、`ai_call_agent_status`（坐席状态）、`ai_call_record`（通话记录）、`ai_call_dial_log`（外呼/中继话单）、B5 的 `ai_call_queue`。

可选新增 `ai_pjsip_endpoint`（端点配置管理）——若需要在页面管理 PJSIP endpoint/aor/auth 则建；本期优先用 Asterisk 配置文件 + 只读展示，不做配置下发，避免风险。建议仅新增一张事件原始流水表用于排查：
- `ai_ami_event_log`（event_time, session_id, event_name, channel, caller, callee, linkedid, raw_json），仅在 debug 开关开启时写入。

### 5.3 代码结构
```
ai-system/.../service/lawyers/trunk/gateway/
  ami/
    AmiClient.java              # 常驻 TCP 连接（带心跳/断线重连），收发 AMI 帧
    AmiEventListener.java       # 接口：onEvent(AmiEvent)
    AmiEventParser.java         # 文本帧 → Map/对象
    event/
      AbstractAmiEventHandler.java
      QueueMemberStatusHandler.java     # 坐席队列状态（PJSIP contact 状态）
      NewChannelHandler.java            # 新通道
      NewCalleridHandler.java
      DialHandler.java                  # 振铃
      BridgeEnterHandler.java           # 桥接（接通）
      HangupHandler.java                # 挂断→回写 call_record/agent_status/queue
      VarSetHandler.java                # 读取 AI_CALL_UUID/AI_RECORD_ID
  AsteriskGatewayAdapter.java  # 改造：复用 AmiClient，而非每次 new Socket
  pjsip/
    PjsipEndpointService.java   # PJSIPShowEndpoints / PJSIPShowSubscriptions 只读查询
    dto/PjsipEndpoint.java / PjsipContact.java
  freeswitch/
    EslEventListener.java       # FreeSWITCH 事件订阅（同类）
ai-admin/.../controller/lawyers/trunk/
  AmiMonitorController.java     # 连接状态、最近事件、手动重连、端点列表
```

### 5.4 实现要点
- `AmiClient`：Spring 单例，`@PostConstruct` 按配置（现有 `call.gateway.asterisk.*`）建立 TCP 连接，Login 后发送 `Events: all`（或白名单），启动读线程循环解析 `Event: xxx` 帧；写线程用 LinkedBlockingQueue；心跳每 30s 发 `Ping`，失败指数退避重连。
- 事件分发给注册的 `AmiEventListener`（Spring 自动收集），按 Event 名路由。
- 核心事件→状态映射：
  - `Newchannel`：建 ai_call_record（呼入）或关联现有外呼 record（读 Variable AI_RECORD_ID）；
  - `Dial`(SubEvent=Begin)：置坐席 callStatus=1(通话中)/振铃；B5 排队者出队；
  - `BridgeEnter`：接通，record 接通时间、status=1；
  - `Hangup`：写挂断原因/通话时长，置坐席 callStatus=0 或 5(话后)，更新中继 current_concurrent、ai_call_dial_log；
  - `QueueMemberStatus`：同步坐席 status（在线/休息/离线）到 ai_call_agent_status；
  - `VarSet`：拿到流程变量（AI_CALL_UUID）关联会话。
- 所有写库操作幂等（按 linkedid/channel 唯一键），避免事件重放导致重复。
- `checkHealth` 从"每次 new Socket 发 SIPshowpeer"改为复用 AmiClient 发 `PJSIPShowEndpoints`（PJSIP）或 `SIPshowpeer`（旧 chan_sip）。
- FreeSWITCH ESL：现有 `FreeSwitchGatewayAdapter` 用 bgapi 同步，扩展一个常驻 ESL 连接订阅 `CHANNEL_*` 事件，映射到同一套内部状态接口（定义 `CallEventBus`，AMI/ESL 都往它发）。
- 内部定义统一 `CallEvent` 模型（与 PBX 无关），由 B5 分配、B4 统计、B2 字幕、坐席状态栏共同消费，**这是解耦关键**。
- 无 PBX 环境：`AmiClient` 连接失败时进入"离线模式"不阻断启动；提供 `SimulatorGatewayAdapter`（已存在）+ 一个 `SimAmiEventFeeder` 定时造事件用于开发联调。

### 5.5 验收标准
1. Asterisk 配置正确时，AmiClient 能登录并持续接收事件，日志打印 QueueMemberStatus/Newchannel/BridgeEnter/Hangup；
2. 一通真实呼入：Newchannel→Dial→BridgeEnter→Hangup 全链路自动写/更新 ai_call_record、坐席状态、中继并发、dial_log，无需 REST 手动干预；
3. 坐席在话机上注销/置忙，QueueMemberStatus 事件自动同步到 ai_call_agent_status，前端状态栏（B4/轮询或 WS）能看到；
4. AMI 连接断开自动重连（kill 连接后观察重连日志与恢复）；
5. FreeSWITCH 模式下 ESL 事件同样驱动状态（有环境时）；
6. 无 Asterisk 环境时后端正常启动，AmiMonitorController 显示"未连接/模拟模式"，SimAmiEventFeeder 造的事件能驱动状态变化；
7. 事件重复投递不产生重复 record（幂等验证：重放同一 Hangup 不重复统计）；
8. 现有 originate/bridgeToAgent/hangup 仍正常工作（复用 AmiClient 发送命令）。

---

## 六、落地顺序与依赖关系

| 顺序 | 项 | 依赖 PBX | 依赖其他 B | 说明 |
|------|----|---------|-----------|------|
| ✅ 已完成 | B1 智能体节点 | 否 | 无 | agentChat 输出 agentHandoff/agentCategoryId |
| 1（进行下一项） | B5 智能队列 | 否（模拟可验） | B1 的 categoryId | 核心闭环价值最高 |
| 2 | B6 短信节点 | 否 | 可被 B5 排队场景调用 | 独立、风险低 |
| 3 | B4 大屏/效能报表 | 否 | 消费 B1/B5 数据 | 聚合展示，需加 status_log 表 |
| 4 | B2 流式语音 | 部分（Mock 可验） | 为 B1/坐席辅助增强 | 定义协议，真实媒体后接 |
| 5 | B3 AMI 事件/PJSIP | **是** | 驱动 B5 状态、B4 数据 | 最后，依赖真实环境 |

**里程碑建议**：
- M1（无 PBX 可演示全链路）：B1 + B5 + B6 + B4，端到端走通"AI 接待→智能转人工→排队短信通知→大屏可见"。
- M2（接 PBX）：B3 事件驱动状态 + B2 真实流式语音。

## 七、风险与对策
1. **状态一致性（B3/B5）**：事件可能乱序/丢失。对策：幂等键 + 定时对账任务（每 5min 对比 PBX 端点状态与 DB 坐席状态）。
2. **流式语音兼容（B2）**：不引入 Reactor，用 javax.websocket + 线程池，保持 Java8 兼容。
3. **短信供应商 SDK 依赖（B6）**：optional + 反射/降级，保证无 SDK 可编译可启动。
4. **大屏性能（B4）**：Redis 短 TTL 缓存 + 索引；必要时再加分钟物化表，不预先过度设计。
5. **技能组与现有坐席模型耦合（B5）**：技能组只存"成员关系+等级"，不改动 ai_call_agent_status 主结构；分配通过现有 Service 方法操作状态。
6. **菜单 ID 冲突**：沿用 3770+ 安全区间（B1 已用 3760-3766），各模块 SQL 独立编号。
