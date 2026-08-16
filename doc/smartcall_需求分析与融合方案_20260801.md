# SmartCall 项目解析与 AI-lawyers 深度融合方案（修订版）

**文档日期**：2026-08-01（2026-08-16 修订）
**项目来源**：https://gitee.com/gdzWork/SmartCall （Apache-2.0）
**修订说明**：初版将 IVR、意图识别、智能外呼等列为"待建设"，经代码核对，这些能力在本项目中**已实质性落地**。本次修订按"已落地 / 部分落地 / 待建设"重新校准基线，聚焦真正的差距点与深度融合路径。

---

## 一、SmartCall 项目核心解析

### 1.1 项目定位

SmartCall 是一套基于 **AI 大模型 + Asterisk 通信引擎**的新一代智能客服呼叫中心，核心是"传统呼叫中心与 AI 大模型深度融合"——围绕 IVR 流程编排、ASR/TTS、大模型意图识别、智能体知识库问答构建 AI 机器人与用户的自然对话能力。运营管理模块（坐席、线路、CDR、外呼、大屏）提供数据模型与扩展接口供二次开发。

### 1.2 技术栈与本项目对比

| 层次 | SmartCall | AI-lawyers（本项目） | 融合策略 |
|------|-----------|----------------------|----------|
| JDK | Java 17 | **Java 8** | 不升级 JDK，所有移植代码须兼容 8（禁用 record/sealed/虚拟线程） |
| 框架 | Spring Boot 3.5.x + Spring Cloud | Spring Boot 2.5.x 单体（RuoYi） | **保持单体**，不引入微服务/Nacos/Gateway |
| ORM | MyBatis-Flex 1.11.5 | MyBatis | 沿用 MyBatis，实体/XML 手写 |
| 通信 | Asterisk 22 (PJSIP) + Asterisk-Java | FreeSWITCH/Asterisk/HTTP/Simulator 网关适配器 | 复用本项目现有 `ICallDispatchService` 网关抽象 |
| AI 框架 | Spring AI（DashScope/DeepSeek） | 自研 OpenAI 兼容/Claude 协议客户端 | 复用 `AiModelConfigService`，不引入 Spring AI |
| 语音 | Alibaba NLS SDK / DashScope SDK | HTTP 直连（DashScope CosyVoice + Whisper 兼容） | 沿用 HTTP 方式，避免 Java17 SDK 依赖 |
| 脚本 | Groovy 3.0 + Nashorn 15.6 | Nashorn（Java 8 内置） | 仅用 JS，不引入 Groovy |
| 前端 | Vue 3 + LogicFlow | **Vue 2 + Element UI** + 自研 SVG 设计器 | 保持 Vue2，设计器用自研 SVG，不引入 LogicFlow |
| 认证 | OAuth2 + 多租户 | Spring Security + JWT（RuoYi） | 沿用 RuoYi 权限/菜单/数据权限 |
| 数据库 | MySQL 8.0 | MySQL 5.7/8.0 | 兼容现有 |

> 关键结论：两个项目技术代差明显（Java17/SpringBoot3/Vue3 vs Java8/SpringBoot2/Vue2）。**不能直接拷贝代码或依赖**，融合方式是"移植设计思想与协议适配，用本项目技术栈重新实现"。

### 1.3 SmartCall 核心能力清单

- **AI 智能应答**：大模型意图识别、MaxKB 智能体多轮对话（插件化 `NodeGranter`，可扩 Dify/Coze/FastGPT）、正则+模型双引擎、阿里云 NLP 情绪分析、LLM 信息抽取。
- **智能 IVR**：LogicFlow 拖拽编排，**16 类节点**（Say/Answer/Received/Intention/Agent/Condition/Extract/Service/Script/Transfer/Child/Variable/Hangup/SMS 等），SpEL 表达式动态路由，在线调试。
- **语音**：ASR/TTS 接口抽象（`VoiceModelEnum`），内置阿里云 NLS、通义千问 DashScope、电信三家，可自定义扩展。
- **架构基座**：微服务+单体双模、RBAC、多租户、OAuth2、API 网关。
- **可扩展运营能力**：数据大屏、坐席管理、CDR、智能外呼、SIP 线路管理。

---

## 二、AI-lawyers 融合现状基线（已核对代码）

> 以下结论经实际代码核对，对应进度记录见 `doc/smartcall_融合进度_20260815.md`。

### 2.1 已实质性落地（可运行）

| 能力 | 落地情况 | 关键代码 |
|------|----------|----------|
| **IVR 流程数据模型** | flow/node/edge/intention/intention_log/execution_log 六张表 + 完整 CRUD | `domain/lawyers/ivr/`、`sql/ai_system_ivr_20260801.sql` |
| **IVR 执行引擎** | 1337 行，支持 **16 类节点**（start/say/menu/dtmf/answer/received/intention/condition/sentiment/extract/service/script/child/agent/transfer/variable/hangup），含 SpEL 模板、子流程嵌套(≤3层)、JS 脚本、HTTP 调用、防死循环(200步)、执行日志、结果回写话单 | `service/impl/lawyers/ivr/engine/IvrEngineServiceImpl.java` |
| **可视化设计器** | 原生 SVG 拖拽设计器（非 LogicFlow），节点面板/连线/属性配置/缩放 | `ai-ui/.../ivr/flow/designer.vue` |
| **AI 意图识别** | 正则优先(置信度1.0)+大模型兜底(0.6)双引擎，结果自动映射咨询分类，落日志 | `IntentionRecognitionServiceImpl.java` |
| **情绪分析/信息抽取** | 基于 LLM `chatJson`，关键词/异常兜底，结果写入流程变量 | `IvrEngineServiceImpl` 的 sentiment/extract 节点 |
| **智能外呼** | task/callee/result 三表 + 定时扫描执行引擎(15s)，支持模拟/网关双模式、失败重试、网关事件回调、接通后联动 IVR、可自动建工单 | `OutboundExecutionServiceImpl.java`（689 行） |
| **ASR/TTS 抽象** | `AsrEngine`/`TtsEngine` 接口 + VoiceEngineManager 路由 + DashScope CosyVoice TTS + Whisper 兼容 ASR + Mock 降级 | `service/lawyers/voice/`（8 个文件） |
| **真实大模型调用** | OpenAI 兼容协议 + Claude Anthropic 协议，提供 callAiModel/chat/chatJson/testConnection | `AiModelConfigServiceImpl` |
| **呼叫中心联动** | IVR/外呼结果回写 `ai_call_record`，转接 `ai_call_transfer`，自动建 `ai_call_ticket` | 既有呼叫中心模块 |

### 2.2 部分落地（有抽象/预留，未完全打通）

| 能力 | 现状 | 缺口 |
|------|------|------|
| Answer 实时收声 | 节点语义完整，从 `inputs` 队列取文本 | **实时流式 ASR 未接入**，需语音网关 WebSocket 推送识别文本 |
| Say 播报 | SpEL 模板 + 可合成 TTS 音频文件 | 缺少与 Asterisk/FreeSWITCH 的 playback 通道对接 |
| 情绪分析 | LLM 通道完整 | 阿里云 NLP SDK 通道为预留 |
| 转人工 | agent/transfer 节点 + 坐席状态模型 | 缺少按技能组/负载的智能队列分配算法 |
| 线路管理 | `ai_call_trunk` 中继模型 + 网关适配器 | 缺少 PJSIP endpoints/aors/auths/contacts/queues 细粒度模型 |
| 外呼执行 | 模拟模式完整可用 | 真实网关联调需 Asterisk/FreeSWITCH 环境 |

### 2.3 待建设（本次深度融合重点）

| 编号 | 能力 | 说明 |
|------|------|------|
| **B1** | **MaxKB/Dify 智能体对话节点** | 代码中无任何实现，是 SmartCall 最核心的 AI 能力差距。当前 agent 节点为空壳 |
| **B2** | **实时 WebSocket 流式 ASR/TTS** | 当前 ASR 是"节点取文本"模式，TTS 仅合成文件；未实现通话过程中的实时双向语音流 |
| **B3** | **Asterisk PJSIP 管理模型 + 注册监控** | endpoints/aors/auths/contacts/queues 数据模型与在线状态监控 |
| **B4** | **数据大屏与坐席效能报表** | 通话趋势、AI/人工占比、坐席效能、呼损统计 |
| **B5** | 智能队列分配算法 | 按技能组、负载、优先级的转人工路由（B1/B2 的配套） |
| **B6** | 短信发送节点（SMS） | SmartCall 有 SMS 节点，本项目缺失（可对接阿里云/腾讯云短信） |

### 2.4 明确不移植（与本项目定位/技术栈冲突）

- 微服务拆分、Spring Cloud、Nacos、API 网关、OAuth2 认证中心——本项目保持 RuoYi 单体。
- 多租户——12348 公共法律服务为单租户政务场景。
- 前端迁移 Vue3/LogicFlow——成本高、收益低，自研 SVG 设计器已满足需求。
- Groovy 脚本引擎、Spring AI、Alibaba NLS SDK（Java17 依赖）——用 Java8 兼容替代方案。

---

## 三、深度融合目标与原则

### 3.1 目标

将 SmartCall 中**本项目尚缺失的核心 AI 对话与实时语音能力**融入 12348 热线，形成完整链路：

```
来电 → IVR 流程引擎 → [意图识别 / 智能体多轮问答 / 情绪分析 / 信息抽取]
       → ASR 实时转写 + TTS 实时播报 → 负面情绪/复杂问题自动转人工坐席
       → 通话记录/工单/台账闭环；外呼任务复用同一套流程
```

### 3.2 原则

1. **不升级技术栈**：全部代码兼容 Java 8 + Spring Boot 2.5 + Vue 2。
2. **复用现有底座**：大模型走 `AiModelConfigService`，呼叫走 `ICallDispatchService` 网关，权限走 RuoYi，不另起炉灶。
3. **接口抽象先行**：智能体平台、ASR/TTS 引擎均用接口 + 管理器模式，可插拔（对齐 SmartCall 的 `NodeGranter`/`VoiceModelEnum` 设计思想）。
4. **政务场景定制**：智能体默认对接法律知识库，意图分类对齐 `ai_consultation_category`，情绪负面自动升级人工并建工单。
5. **可独立验证**：每个批次都能在无 Asterisk 环境下通过"模拟模式 + 在线调试"验证业务逻辑。

---

## 四、待建设能力详细设计

### 4.1 B1：智能体（Agent）对话节点【最高优先级】

**目标**：让 IVR 中的 `agent` 节点调用知识库智能体进行多轮法律问答，支持上下文记忆、智能体选择、负面情绪转人工。

#### 4.1.1 数据模型

新增表 `ai_agent_config`（智能体配置）：

| 字段 | 类型 | 说明 |
|------|------|------|
| agent_id | bigint PK | 智能体ID |
| agent_name | varchar(100) | 名称（如"民事法律咨询助手"） |
| provider | varchar(30) | 平台：maxkb / dify / fastgpt / coze / local |
| api_url | varchar(500) | 对话接口地址 |
| api_key | varchar(500) | 认证密钥（加密存储） |
| app_id | varchar(100) | 平台应用/知识库ID |
| category_id | bigint | 关联咨询分类（用于按意图选智能体） |
| system_prompt | text | 系统提示词（local 模式生效） |
| model_id | bigint | 关联大模型配置（local 模式） |
| knowledge_ids | varchar(500) | 关联本项目法律知识库ID（多个逗号分隔） |
| enable_context | char(1) | 是否启用多轮上下文（0/1） |
| context_rounds | int | 上下文保留轮数（默认5） |
| status | char(1) | 状态（0停用 1启用） |
| 标准字段 | - | create_by/create_time/update_by/update_time/remark |

#### 4.1.2 后端设计

```
ai-system/src/main/java/ai/lawyers/system/
├── domain/lawyers/agent/AiAgentConfig.java
├── mapper/lawyers/agent/AiAgentConfigMapper.java (+xml)
├── service/lawyers/agent/
│   ├── IAiAgentConfigService.java
│   ├── IAgentChatService.java              # 智能体对话统一接口
│   └── impl/
│       ├── AiAgentConfigServiceImpl.java
│       ├── AgentChatServiceImpl.java       # 统一入口，按 provider 路由
│       └── provider/
│           ├── MaxKbChatProvider.java      # MaxKB HTTP 适配
│           ├── DifyChatProvider.java       # Dify 适配
│           └── LocalRagChatProvider.java   # 本地知识库 RAG（复用 ai_legal_knowledge + callAiModel）
```

- `IAgentChatService.chat(agentId, sessionId, userMessage, variables)` 返回 `{answer, intent, sentiment, suggestedTransfer, metadata}`。
- 会话上下文按 `callerNumber + sessionId` 维护，存 Redis（TTL 30 分钟），结构为消息轮次列表。
- `LocalRagChatProvider`：先用关键词/向量（如无向量库则 LIKE 检索）从 `ai_legal_knowledge` 取 Top-K 知识片段拼入 Prompt，再调 `chatJson`，做到不依赖外部智能体平台也能用。
- IVR `agent` 节点改造：读取节点配置 `agentId/knowledgeIds/maxRounds`，循环调用 `IAgentChatService.chat`，每轮把 ASR 文本传入、把回答交 TTS 播报；当返回 `suggestedTransfer=true`（情绪负面/用户要求人工）或达到最大轮次时，走 transfer 节点。

#### 4.1.3 前端设计

- `ai-ui/src/views/lawyers/agent/config.vue`：智能体配置 CRUD（provider 下拉、连接测试、关联分类/知识库）。
- 设计器 agent 节点属性面板增加"选择智能体""最大对话轮数""负面情绪转人工"配置项。
- 在线调试面板支持多轮对话输入，展示智能体回答与转人工触发标记。

#### 4.1.4 验收

- 配置一个 local 智能体，在 IVR 测试中输入法律问题，能基于知识库多轮回答。
- 输入"我要投诉/找律师/人工"等，自动触发转人工分支。

---

### 4.2 B2：实时 WebSocket 流式 ASR/TTS

**目标**：通话过程中实时语音转文字、实时文字转语音播报，支撑 Answer 收声与 agent 多轮对话。

#### 4.2.1 设计

- Java 8 下用 **OkHttp WebSocket**（或 Tyrus）作客户端，连接语音网关/ASR 服务。
- 抽象接口 `IRealtimeVoiceService`：
  - `startRecognition(sessionId, callback)`：开始流式识别，回调 `onPartial(text)`/`onFinal(text)`/`onError`。
  - `stopRecognition(sessionId)`。
  - `streamSpeak(sessionId, text)`：流式合成并推送播报。
- 实现 `DashScopeRealtimeVoiceService`（通义千问 Qwen3-ASR / CosyVoice 实时协议，HTTP/WS），无法连通时降级为现有"文件 TTS + 文本输入"模式。
- 与 IVR 引擎集成：`answer` 节点阻塞等待 `onFinal` 回调写入流程变量 `lastInput`；`say`/`agent` 回答调用 `streamSpeak`。
- 通话音频流由 FreeSWITCH/Asterisk 通过媒体网关转发（不在本期实现 PBX 侧，只定义对接契约）。

#### 4.2.2 验收

- Mock 语音网关下，模拟推送音频/文本，IVR answer 节点能拿到最终识别文本并继续流程。
- agent 回答能触发 TTS 播报（文件模式即可，真实流式需网关）。

---

### 4.3 B3：Asterisk PJSIP 管理模型与监控

**目标**：在现有 `ai_call_trunk` 中继基础上，补齐 PJSIP 端点、注册、队列的数据模型与监控。

#### 4.3.1 数据表

- `ai_pjsip_endpoint`：端点（extension、callerid、context、allow、auth、aors）。
- `ai_pjsip_registration`：注册状态（endpoint、server、status、last_qualify）。
- `ai_call_queue`：队列（queue_name、strategy、strategy=ringall/leastrecent/rrmemory、wrapuptime）。
- `ai_call_queue_member`：队列坐席成员（queue_id、agent_id、paused）。

#### 4.3.2 功能

- 通过 AMI/Asterisk- Java（Java8 兼容版本）或 HTTP 定时拉取 PJSIP 注册状态、在线坐席。
- 队列状态展示（等待数、最长等待、可用坐席），为 B5 智能分配提供数据。
- 前端：线路管理下新增"分机端点""队列管理"页。

> 依赖真实 Asterisk 环境，可在有环境时实施；无环境时仅完成数据模型与管理 CRUD。

---

### 4.4 B4：数据大屏与坐席效能报表

**目标**：对齐 SmartCall 数据大屏，提供实时话务态势。

- **大屏页**（`ai-ui/.../dashboard/screen.vue`）：今日呼入/呼出量、接通率、AI 应答占比、转人工率、在线坐席数、呼损数、通话趋势折线、坐席效能 Top 榜。
- 后端新增聚合查询接口（复用现有 `ai_call_record`/`ai_outbound_result`/`ai_ivr_execution_log` 统计口径）。
- 坐席效能：应答数、平均通话时长、满意度、转接次数（基于 `ai_call_record.agent_id` 聚合）。
- WebSocket/SSE 推送实时指标（可选，初期用 10s 轮询）。

---

### 4.5 B5：智能队列分配（转人工路由）

**目标**：转人工时按技能组 + 负载 + 优先级选最优坐席。

- 新增 `ai_agent_skill`（坐席-咨询分类技能映射 + 熟练度）。
- `IAgentDispatchService.selectBestAgent(categoryId, callerNumber)`：
  1. 按意图映射的 `categoryId` 筛选有该技能的就绪坐席；
  2. 同技能组内按"当前通话数最少 + 熟练度最高"排序；
  3. 无匹配则回退到默认技能组或排队。
- IVR `transfer`/`agent` 节点调用该服务确定 `transferTarget`，写回 `ai_call_record`。

---

### 4.6 B6：SMS 短信节点

- IVR 新增 `sms` 节点，配置 `smsCode`（模板编码）、`phone`（SpEL 变量）、`params`。
- 抽象 `ISmsService`，提供阿里云/腾讯云短信实现与 Mock 实现，异步发送不阻塞流程。
- 用于身份核验、通话后通知、回访提醒。

---

## 五、模块映射与代码结构（修订）

### 5.1 SmartCall → AI-lawyers 映射表

| SmartCall | AI-lawyers | 状态 |
|-----------|------------|------|
| smart-aster IVR 引擎 | `IvrEngineServiceImpl`（16 节点） | ✅ 已实现 |
| smart-aster ASR/TTS | `service/lawyers/voice/` | ✅ 抽象+文件模式；⏳ 实时流 B2 |
| smart-aster 意图识别 | `IntentionRecognitionServiceImpl` | ✅ |
| smart-maxkb 智能体 | **待建 B1** | ❌ |
| smart-upms | RuoYi 权限体系 | ✅ 直接复用 |
| CDR/转接 | ai_call_record/transfer/ticket/ledger | ✅ |
| 智能外呼 | outbound 三表 + 执行引擎 | ✅ |
| 坐席/队列 | ai_call_agent_status + **待建 B3/B5** | 🟡 |
| 数据大屏 | **待建 B4** | ❌ |
| SMS 节点 | **待建 B6** | ❌ |

### 5.2 新增代码结构

```
ai-system/src/main/java/ai/lawyers/system/
├── domain/lawyers/agent/AiAgentConfig.java
├── mapper/lawyers/agent/AiAgentConfigMapper.xml
├── service/lawyers/agent/
│   ├── IAiAgentConfigService.java
│   ├── IAgentChatService.java
│   └── impl/provider/{MaxKb,Dify,LocalRag}ChatProvider.java
└── service/lawyers/voice/realtime/   # B2 实时语音

ai-admin/.../controller/lawyers/agent/AiAgentConfigController.java

ai-ui/src/
├── api/lawyers/agent.js
└── views/lawyers/agent/config.vue
```

---

## 六、分阶段实施计划

| 阶段 | 内容 | 依赖 | 可验证性 |
|------|------|------|----------|
| **B1** | 智能体配置表 + 对话接口 + LocalRag/MaxKB 适配 + IVR agent 节点打通 + 前端配置页 | 现有 LLM/知识库 | 无外部依赖，IVR 在线调试即可验证 |
| **B5** | 坐席技能 + 智能队列分配 | 现有坐席状态 | 可单元测试/模拟坐席验证 |
| **B6** | SMS 节点 + 短信服务抽象 + Mock 实现 | 无 | 模拟模式验证 |
| **B4** | 数据大屏 + 坐席效能聚合接口 | 现有统计表 | 直接访问页面 |
| **B2** | 实时语音接口抽象 + WS 客户端 + 与 IVR 集成 | 语音网关契约 | 需 Mock 网关 |
| **B3** | PJSIP/队列模型 + AMI 监控 | Asterisk 环境 | 需真实环境 |

**建议优先级**：B1 → B5 → B6 → B4 → B2 → B3。B1 是核心 AI 能力差距且可立即落地；B5/B6/B4 不依赖 PBX 环境可并行；B2/B3 需语音/通信环境，靠后。

---

## 七、风险与对策

1. **技术代差**：SmartCall 基于 Java17/SpringBoot3，禁止直接拷贝。对策：仅移植接口设计思想，用 Java8 重写；引入任何新依赖前确认其 Java8 兼容性。
2. **智能体平台依赖**：MaxKB/Dify 需单独部署。对策：优先实现 LocalRag 提供方，复用本项目法律知识库 + LLM，保证无外部平台也可运行；MaxKB/Dify 作为可选 provider。
3. **实时语音复杂**：B2 涉及媒体流、ASR/TTS 双向流、PBX 集成，工程量大。对策：先定义接口契约 + Mock 实现打通 IVR 链路，真实流式在有语音网关时分阶段接入。
4. **Asterisk 环境**：B3 需真实 PBX。对策：数据模型与 CRUD 先行，监控联调后置。
5. **政务合规**：12348 场景对数据出境、通话合规有要求。对策：默认支持内网部署的本地模型/本地知识库，云端 ASR/TTS 可关闭。
6. **类加载冲突**：项目存在 ai-system 与 ai-admin 同包同名类，已通过禁用 devtools restart 解决（见 `RuoYiApplication`），后续新增类应放在唯一包路径，避免重复定义。

---

## 八、附录：当前可立即体验的融合能力

执行演示脚本 `sql/ai_system_ivr_smartcall_demo_20260815.sql` 后，登录管理端 → 外呼与IVR → IVR流程管理 → 对 `SMARTCALL_DEMO` 点"测试"，可体验已落地的完整节点链：

```
收号(DTMF) → 收声(Answer) → 情绪分析 → 信息抽取 → 子流程 → 条件分支 → 转人工
```

这是 B1（智能体节点）落地前的基础能力底座。
