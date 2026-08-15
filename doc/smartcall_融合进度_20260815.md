# SmartCall 设计融入进度与后续路线

**日期**：2026-08-15
**来源**：https://gitee.com/gdzWork/SmartCall （Apache-2.0，已克隆至 `third_party/SmartCall` 作为设计参考）
**基线**：`doc/smartcall_需求分析与融合方案_20260801.md`（一期：IVR 表结构 / 意图 / 外呼）

---

## 一、本轮已融入的能力

### 1. 真实大模型调用（替换原模拟回复）

`AiModelConfigServiceImpl` 已接入真实 Chat Completions：

- OpenAI 兼容协议（GPT / DeepSeek / 通义千问兼容模式 / 智谱 / vLLM / Ollama），支持 `response_format=json_object`；
- Claude 走 Anthropic Messages 协议；
- 支持 `apiUrl / apiKey / modelName / temperature / topP / maxTokens / timeout / retryCount`；
- 新增 `chat(system, user)` 与 `chatJson(system, user)`，供意图、抽取、情绪节点复用；
- 未配置默认模型时给出明确提示，调用异常由 IVR 引擎降级兜底。

配置示例（`ai_model_config` 表）：

```text
model_type = OpenAI        # OpenAI / Claude / ChatGLM / DeepSeek / DashScope / local
model_name = qwen-plus 或 deepseek-chat 或 gpt-4o-mini
api_url    = https://dashscope.aliyuncs.com/compatible-mode/v1
api_key    = sk-xxx
is_default = 1
```

### 2. 语音引擎抽象（对齐 VoiceModelEnum）

新增 `ai.lawyers.system.service.lawyers.voice`：

| 类 | 说明 |
|---|---|
| `VoiceModelEnum` | ali / dianxin / dashscope / mock 四类引擎 |
| `AsrEngine` / `TtsEngine` | 语音识别与合成接口抽象 |
| `DashScopeTtsEngine` | 通义千问 CosyVoice 异步 HTTP 合成（无 SDK 依赖） |
| `OpenAiCompatibleAsrEngine` | Whisper 兼容 `/audio/transcriptions` multipart 转写 |
| `MockVoiceEngine` | 无密钥环境兜底，只记日志 |
| `VoiceEngineManager` | 按枚举选择引擎，失败自动降级 Mock |
| `VoiceProperties` | `application.yml` 的 `voice.*` 配置 |

```yaml
voice:
  engine: mock                      # dashscope / ali / dianxin / mock
  dashscope-api-key:
  dashscope-base-url: https://dashscope.aliyuncs.com/api/v1
  dashscope-tts-model: cosyvoice-v1
  dashscope-voice: longxiaochun
  asr-base-url: http://localhost:8000/v1
  asr-api-key:
  asr-model: whisper-1
```

### 3. IVR 引擎补齐 SmartCall 全部节点

`IvrEngineServiceImpl` 现有节点：`start / say / answer / received / menu / intention /
sentiment / extract / service / script / child / condition / agent / transfer / variable / hangup`。

本轮新增实现：

- `answer`：语音收声（询问语、静默提示），结果写 `lastInput`；
- `received`：DTMF 收号（结束键、最大位数），结果写 `dtmf`；
- `sentiment`：大模型情绪分析，失败回退关键词规则，结果写 `sentiment`（positive/negative/neutral）；
- `extract`：LLM 结构化抽取，`fields` 支持对象或 `[{name,desc}]` 数组，每个字段写入流程变量；
- `service`：流程内 HTTP 调用，支持 `${变量}` 模板、请求头/请求体、JSON 路径取值、超时；
- `script`：JavaScript 脚本（Java 8 内置 Nashorn），最后一个表达式值写入结果变量；
- `child`：按 `flowId/flowCode` 调用子流程并合并变量，最多嵌套 3 层；
- `variable`：支持 `variables: [{key,val}]` 批量赋值；
- `say`：支持 `${表达式}` SpEL 模板，节点配置 `voiceEngine` 时由后端合成 TTS 音频。

### 4. 前端 IVR 设计器

`ai-ui/src/views/lawyers/ivr/flow/designer.vue` 已加入 16 类节点面板与属性表单，
新增：语音收声、DTMF 收号、情绪分析、信息抽取、HTTP 服务、脚本执行、子流程、变量赋值。

### 5. 演示脚本

- `sql/ai_system_ivr_smartcall_demo_20260815.sql`：主流程 `SMARTCALL_DEMO` + 子流程
  `SMARTCALL_DEMO_CHILD`，覆盖全部新节点，可在「IVR流程管理 → 测试」中直接运行。

---

## 二、SmartCall 设计 → AI-lawyers 映射总览

| SmartCall 设计 | AI-lawyers 现状 |
|---|---|
| IVR 可视化编排（LogicFlow） | ✅ 已有 SVG 设计器；✅ 已补齐全部节点语义 |
| Say（TTS + SpEL） | ✅ 模板渲染 + DashScope HTTP TTS + Mock |
| Answer（ASR 实时收声） | ✅ 节点语义；⏳ 实时流式 ASR 需在语音网关接入 |
| Received（DTMF） | ✅ |
| Intention（正则 + 大模型双引擎） | ✅（一期已完成，本轮接入真实 LLM） |
| Sentiment（阿里 NLP / 大模型） | ✅ 大模型 + 关键词兜底；⏳ 阿里 NLP SDK 通道 |
| Extract（LLM 结构化抽取） | ✅ |
| Service（HTTP 服务调用） | ✅ |
| Script（Groovy / JS） | ✅ JS（Nashorn）；⏳ Groovy |
| Child（子流程） | ✅ |
| Transfer / 转人工队列 | ✅ agent/transfer 节点 + 现有坐席状态模型 |
| VoiceModelEnum + ASR/TTS 抽象 | ✅ 枚举 + 接口 + DashScope/Whisper 兼容实现 |
| MaxKB 智能体对话 | ⏳ 待接入（复用 chatJson 可快速实现 HTTP 适配器） |
| Asterisk PJSIP 数据模型（endpoints/aors/auths/contacts/queues） | ⏳ 现有 ai_call_trunk 线路模型，待按需扩展 |
| CDR 明细/转接链路 | ✅ 已有 ai_call_record / ai_call_transfer / ai_call_ledger |
| 智能外呼（任务/号码/重拨） | ✅（一期已完成 ai_outbound_task / ai_outbound_callee） |
| 数据大屏 / 坐席效能 | ⏳ 待做（可复用 home/*VO 统计口径） |
| 多租户 / OAuth2 / 网关 | ⏳ 与 RuoYi 权限体系取舍后决定 |

---

## 三、验证方式

1. 后端：`mvn -o -pl ai-system -am compile` 已通过；前端 `npm run build:prod` 已通过；
2. 功能：执行演示 SQL 后，登录管理端 → 咨询管理 → IVR流程管理 → 对 `SMARTCALL_DEMO`
   点「测试」，输入：

   ```text
   12345#
   我签的合同对方违约了，太生气了
   ```

   预期步骤依次为：收号 → 收声 → 情绪(negative) → 抽取(合同信息) → 子流程 → 转人工分支。

---

## 四、后续批次建议

| 批次 | 内容 | 依赖 |
|---|---|---|
| B1 | MaxKB/Dify 智能体节点（复用 chatJson + 会话上下文） | 可立即做 |
| B2 | 实时 WebSocket ASR/TTS（DashScope Omni/Qwen3 实时协议） | 需引入 WS 客户端（Java8 下 okhttp/tyrus） |
| B3 | Asterisk PJSIP 管理数据模型 + 线路注册监控 | 需 Asterisk 22 环境 |
| B4 | 数据大屏、坐席效能、CDR 报表 | 现有统计表扩展 |
| B5 | 前端向 Vue3/LogicFlow 迁移（对齐 SmartCall UI） | 长期演进 |

