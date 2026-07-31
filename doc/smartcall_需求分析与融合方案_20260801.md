# SmartCall 项目解析与 AI-lawyers 功能融合方案

**文档日期**：2026-08-01
**项目来源**：https://gitee.com/gdzWork/SmartCall

---

## 一、SmartCall 项目核心解析

### 1.1 项目概述

SmartCall 是一套基于 **AI 大模型 + Asterisk 通信引擎** 构建的新一代智能客服呼叫中心系统。系统深度融合了 AI 语音机器人、智能 IVR 流程编排、实时语音识别（ASR）、语音合成（TTS）、大模型意图识别等核心能力。

**技术栈**：
- Java 17 + Spring Boot 3.5.x + Spring Cloud
- MyBatis-Flex 1.11.5 + MySQL 8.0+ + Redis 6.0+
- Asterisk 22 (PJSIP) + Asterisk-Java
- Spring AI（大模型集成框架）
- 阿里云 NLS / 通义千问 DashScope（ASR/TTS）
- Vue 3（前端）

### 1.2 核心能力模块

#### 1.2.1 AI 智能应答
| 功能 | 说明 |
|------|------|
| 大模型意图识别 | 集成通义千问、DeepSeek 等主流大模型，通过 Prompt 工程实现精准来电意图分类 |
| AI 智能体对话 | 内置知识库智能体集成（MaxKB），支持多轮智能问答、上下文记忆 |
| 正则+模型双引擎 | 正则快速匹配 + AI 模型深度识别，兼顾速度与精度 |
| 情绪分析 | 集成阿里云 NLP 情感分析，实时感知客户情绪，负面自动升级人工 |
| AI 信息提取 | 基于大模型从对话中提取姓名、地址、订单号等结构化信息 |

#### 1.2.2 智能 IVR 流程编排
**15种流程节点**：
1. 🎙️ 语音播放（Say）- TTS 实时合成，支持 SpEL 表达式
2. 🎧 语音识别收听（Answer）- ASR 实时转文字，支持客户打断
3. 🔢 DTMF 收号（Received）- 按键输入采集
4. 🧠 意图识别（Intention）- AI 大模型多意图分类
5. 🤖 智能体对话（Agent）- 知识库多轮问答
6. 🔀 条件分支（Condition）- SpEL 表达式动态路由
7. 📋 信息提取（Extract）- AI 提取结构化信息
8. 🔌 HTTP 服务调用（Service）- 流程中调用外部 API
9. 📜 脚本执行（Script）- Groovy / JavaScript 脚本
10. 🔗 转接人工（Transfer）- 智能队列分配
11. 📂 子流程调用（Child）- 流程模块化复用
12. 📌 变量赋值（Variable）- 全局变量管理
13. 📴 挂断（Hangup）- 支持挂断前结束语

#### 1.2.3 企业级架构基座
- 微服务 + 单体双模架构
- RBAC 权限体系（按钮级）
- 多租户架构
- OAuth2 统一认证
- API 网关（路由/鉴权/限流/日志）

#### 1.2.4 可扩展运营能力
| 模块 | 说明 |
|------|------|
| 📊 数据大屏 | 通话趋势、AI/人工占比、坐席效能分析 |
| 👥 坐席管理 | 坐席全生命周期、实时状态监控、通话保持/转接 |
| 📋 通话记录（CDR） | 全量通话记录、录音管理、呼损统计 |
| 📤 智能外呼 | 批量外呼、智能重拨、多机器人并发 |
| 📡 线路管理 | SIP 中继线路注册、状态监控、多线路智能路由 |

### 1.3 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                        Web 前端 (Vue 3)                  │
└────────────┬────────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────┐
│                    Nginx / API Gateway                    │
│            (路由转发 / OAuth2 鉴权 / 限流)               │
└────────────┬────────────────────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────┐
│              smart-aster (核心呼叫模块)                   │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐   │
│  │ IVR 流程引擎 │  │ ASR/TTS 对接 │  │ AI 意图识别  │   │
│  └─────────────┘  └─────────────┘  └──────────────┘   │
│  Asterisk AMI/AGI 集成  ←→  PJSIP 通话控制              │
└────────────┬────────────────────────────────────────────┘
             │
┌────────────▼──────────┐  ┌─────────────────────────────┐
│   smart-maxkb         │  │    smart-upms (用户/权限)    │
│  (AI 智能体对接模块)  │  │  system / user / resource    │
└────────────┬──────────┘  └─────────────────────────────┘
             │
┌────────────▼────────────────────────────────────────────┐
│              语音服务 (WebSocket) / 大模型 API            │
│  通义千问 DashScope / 阿里云 NLS / DeepSeek / MaxKB     │
└──────────────────────────────────────────────────────────┘
```

### 1.4 项目模块结构

```
SmartCall
├── smart-aster          # 核心呼叫模块（IVR引擎/ASR/TTS/AI意图）
├── smart-maxkb          # MaxKB 智能体对接模块
├── smart-gateway        # API 网关
├── smart-auth           # 统一认证中心
├── smart-boot           # 单体模式启动器
├── smart-api            # 服务间 Feign API
├── smart-common         # 公共模块
├── smart-upms           # 用户权限管理
│   ├── smart-system     # 系统管理（字典/组织/角色/菜单/租户）
│   ├── smart-user       # 用户管理
│   └── smart-resource   # 资源管理（文件/OSS/短信）
├── smart-ops            # 运维监控
├── script               # 部署脚本
├── sql                  # 数据库脚本
└── docs                 # 文档与配置模板
```

---

## 二、AI-lawyers 现有架构分析

### 2.1 技术栈
- **后端**：Spring Boot + MyBatis + MySQL（基于 RuoYi 框架）
- **前端**：Vue 2 + Element UI
- **缓存**：Redis
- **权限**：Spring Security + JWT

### 2.2 现有模块结构

```
AI-lawyers
├── ai-admin          # 后端管理模块（Controller层）
├── ai-common         # 公共模块
├── ai-framework      # 框架层（安全/配置/数据源）
├── ai-generator      # 代码生成器
├── ai-quartz         # 定时任务
├── ai-system         # 系统模块（Domain/Mapper/Service层）
├── ai-ui             # 管理端前端（Vue 2）
├── aiuser-ui         # 用户端前端
└── sql               # 数据库脚本
```

### 2.3 已有的呼叫中心功能

| 功能模块 | 数据表 | 说明 |
|---------|--------|------|
| 坐席状态 | ai_call_agent_status | 坐席签入/签出、状态管理 |
| 来电记录 | ai_call_record | 通话记录、来电人信息、通话时长 |
| 工单管理 | ai_call_ticket | 工单流转、处理、归档 |
| 转接管理 | ai_call_transfer | 通话转接记录 |
| 台账记录 | ai_call_ledger | 咨询台账、满意度评价 |

### 2.4 已有的 AI 功能

| 功能模块 | 数据表 | 说明 |
|---------|--------|------|
| 模型配置 | ai_model_config | AI 大模型配置管理 |
| 系统提示词 | ai_system_prompt | 系统级 Prompt 配置 |
| 法律知识库 | ai_legal_knowledge | 法律知识条目管理 |
| 咨询分类 | ai_consultation_category | 法律咨询分类 |
| 法律咨询 | ai_legal_consultation | 法律咨询记录 |

---

## 三、功能融合方案设计

### 3.1 融合目标

将 SmartCall 的核心特色能力融入 AI-lawyers 12348 热线系统，重点融合：

1. **IVR 智能流程编排** - 可视化拖拽式 IVR 设计器
2. **AI 意图识别与智能体** - 大模型驱动的来电意图分类与知识库对话
3. **智能外呼任务** - 批量外呼、回访任务管理
4. **ASR/TTS 集成框架** - 语音识别与合成的统一抽象接口

### 3.2 功能模块映射

| SmartCall 模块 | AI-lawyers 融合位置 | 实现方式 |
|---------------|---------------------|---------|
| IVR 流程编排 | ai-system + ai-ui | 新建 ivr 子模块 |
| AI 意图识别 | ai-system + 现有模型配置 | 扩展 AiModelConfig |
| 智能体对话 | ai-system + 知识库 | 新建 agent 子模块 |
| 智能外呼 | ai-system + ai-ui | 新建 outbound 子模块 |
| ASR/TTS 框架 | ai-common + ai-system | 新建语音接口抽象 |

### 3.3 新增数据表设计

#### 3.3.1 IVR 流程相关表

**ai_ivr_flow（IVR流程表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| flow_id | bigint | 流程ID（主键） |
| flow_name | varchar(100) | 流程名称 |
| flow_code | varchar(50) | 流程编码（唯一） |
| description | varchar(500) | 流程描述 |
| flow_data | text | 流程定义JSON（LogicFlow格式） |
| status | char(1) | 状态（0草稿 1已发布 2停用） |
| version | int | 版本号 |
| create_by / create_time / update_by / update_time / remark | - | 标准字段 |

**ai_ivr_node（IVR节点表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| node_id | bigint | 节点ID |
| flow_id | bigint | 所属流程ID |
| node_type | varchar(30) | 节点类型（say/answer/intention/agent/transfer等） |
| node_name | varchar(100) | 节点名称 |
| node_config | text | 节点配置JSON |
| position_x | int | 画布X坐标 |
| position_y | int | 画布Y坐标 |
| sort_order | int | 排序 |

**ai_ivr_edge（IVR连线表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| edge_id | bigint | 连线ID |
| flow_id | bigint | 所属流程ID |
| source_node_id | bigint | 源节点ID |
| target_node_id | bigint | 目标节点ID |
| edge_label | varchar(100) | 连线标签（条件分支用） |
| condition_expr | varchar(500) | 条件表达式（SpEL） |

#### 3.3.2 AI 意图相关表

**ai_ivr_intention（意图定义表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| intention_id | bigint | 意图ID |
| intention_name | varchar(100) | 意图名称 |
| intention_code | varchar(50) | 意图编码 |
| description | varchar(500) | 描述 |
| regex_pattern | varchar(500) | 正则匹配模式 |
| prompt_template | text | AI 识别 Prompt 模板 |
| example_utterances | text | 示例话术（JSON数组） |
| model_id | bigint | 关联AI模型ID |
| status | char(1) | 状态 |

**ai_ivr_intention_log（意图识别日志表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| log_id | bigint | 日志ID |
| record_id | bigint | 通话记录ID |
| session_id | varchar(64) | 会话ID |
| input_text | varchar(1000) | 输入文本 |
| matched_intention | varchar(50) | 匹配的意图 |
| confidence | decimal(5,4) | 置信度 |
| match_method | char(1) | 匹配方式（1正则 2AI模型） |
| create_time | datetime | 创建时间 |

#### 3.3.3 外呼任务相关表

**ai_outbound_task（外呼任务表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | bigint | 任务ID |
| task_name | varchar(100) | 任务名称 |
| task_type | char(1) | 任务类型（1批量外呼 2回访 3通知） |
| caller_number | varchar(20) | 主叫号码 |
| ivr_flow_id | bigint | 关联IVR流程ID |
| start_time | datetime | 开始时间 |
| end_time | datetime | 结束时间 |
| total_count | int | 总号码数 |
| completed_count | int | 已完成数 |
| answered_count | int | 已接通数 |
| failed_count | int | 失败数 |
| status | char(1) | 状态（0待执行 1执行中 2已完成 3已暂停） |
| priority | int | 优先级 |
| retry_count | int | 重拨次数 |
| retry_interval | int | 重拨间隔（分钟） |

**ai_outbound_callee（外呼号码表）**
| 字段 | 类型 | 说明 |
|------|------|------|
| callee_id | bigint | ID |
| task_id | bigint | 任务ID |
| callee_number | varchar(20) | 被叫号码 |
| callee_name | varchar(50) | 被叫姓名 |
| callee_params | text | 附加参数（JSON） |
| call_status | char(1) | 呼叫状态（0待呼叫 1呼叫中 2已接通 3未接 4失败 5已完成） |
| call_time | datetime | 呼叫时间 |
| call_duration | int | 通话时长（秒） |
| record_id | bigint | 关联通话记录ID |
| retry_times | int | 已重拨次数 |

### 3.4 后端代码结构设计

```
ai-system/src/main/java/ai/lawyers/system/
├── domain/lawyers/
│   ├── ivr/
│   │   ├── AiIvrFlow.java          # IVR流程
│   │   ├── AiIvrNode.java          # IVR节点
│   │   ├── AiIvrEdge.java          # IVR连线
│   │   ├── AiIvrIntention.java     # 意图定义
│   │   └── AiIvrIntentionLog.java  # 意图识别日志
│   └── outbound/
│       ├── AiOutboundTask.java      # 外呼任务
│       └── AiOutboundCallee.java    # 外呼号码
├── mapper/lawyers/
│   ├── ivr/
│   │   ├── AiIvrFlowMapper.xml
│   │   ├── AiIvrNodeMapper.xml
│   │   ├── AiIvrEdgeMapper.xml
│   │   ├── AiIvrIntentionMapper.xml
│   │   └── AiIvrIntentionLogMapper.xml
│   └── outbound/
│       ├── AiOutboundTaskMapper.xml
│       └── AiOutboundCalleeMapper.xml
└── service/lawyers/
    ├── ivr/
    │   ├── IAiIvrFlowService.java
    │   ├── IAiIvrIntentionService.java
    │   └── impl/
    │       ├── AiIvrFlowServiceImpl.java
    │       └── AiIvrIntentionServiceImpl.java
    └── outbound/
        ├── IAiOutboundTaskService.java
        └── impl/
            └── AiOutboundTaskServiceImpl.java
```

```
ai-admin/src/main/java/ai/lawyers/web/controller/lawyers/
├── ivr/
│   ├── AiIvrFlowController.java       # IVR流程管理
│   └── AiIvrIntentionController.java  # 意图管理
└── outbound/
    └── AiOutboundTaskController.java   # 外呼任务管理
```

```
ai-ui/src/
├── api/lawyers/
│   ├── ivr.js           # IVR相关API
│   └── outbound.js      # 外呼相关API
└── views/lawyers/
    ├── ivr/
    │   ├── flow.vue         # IVR流程列表
    │   ├── flowDesign.vue   # IVR流程设计器
    │   └── intention.vue    # 意图管理
    └── outbound/
        ├── task.vue         # 外呼任务列表
        └── taskDetail.vue   # 外呼任务详情
```

### 3.5 核心融合点

#### 3.5.1 IVR 流程引擎与现有呼叫中心集成
- IVR 流程发布后关联到来电号码/技能组
- 通话接入时根据配置触发对应 IVR 流程
- 流程节点执行结果更新到通话记录

#### 3.5.2 AI 意图识别与现有法律咨询集成
- 意图识别结果自动关联咨询分类
- AI 智能体对话复用现有法律知识库
- 意图识别日志与通话记录关联

#### 3.5.3 外呼任务与现有工单/台账集成
- 外呼结果自动生成通话记录
- 外呼中可触发工单创建
- 外呼任务支持关联回访工单

---

## 四、实施计划

| 阶段 | 任务 | 产出 |
|------|------|------|
| 阶段一 | 数据库表设计与SQL脚本生成 | sql/ai_system_ivr_*.sql, sql/ai_system_outbound_*.sql |
| 阶段二 | 后端Domain/Mapper/Service层实现 | ai-system 模块代码 |
| 阶段三 | 后端Controller层实现 | ai-admin 模块代码 |
| 阶段四 | 前端API与页面实现 | ai-ui 模块代码 |
| 阶段五 | 菜单配置与集成测试 | 菜单SQL + 测试报告 |

---

## 五、风险与注意事项

1. **技术栈差异**：SmartCall 基于 Spring Boot 3 + MyBatis-Flex，AI-lawyers 基于 Spring Boot 2 + MyBatis，需注意 API 兼容性
2. **Asterisk 集成**：实际电话交换功能依赖 Asterisk，当前阶段聚焦业务数据模型与管理功能
3. **前端框架差异**：SmartCall 使用 Vue 3 + LogicFlow，AI-lawyers 使用 Vue 2，需选择兼容的流程图组件
4. **大模型集成**：复用现有 AiModelConfig 配置，扩展支持语音模型
