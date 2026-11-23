# AI律师话务系统

> 基于RuoYi-Vue二次开发的智能法律热线话务平台（聚焦呼叫中心核心能力）


## 一、项目简介

AI律师话务系统是一套面向公共法律服务热线的**专业呼叫中心系统**，以**电话语音服务**为核心，参照12348热线的话务运营标准进行设计。系统基于RuoYi-Vue快速开发框架二次开发，集成智能语音识别（ASR）、语音合成（TTS）、智能路由、实时质检等能力，为坐席人员提供高效、智能的话务工作台，实现热线电话的接听、转接、录音、工单记录与闭环管理，保障7×24小时法律咨询热线畅通。


## 二、项目背景

为提升公共法律热线服务效率，解决传统话务系统人工依赖度高、服务标准化不足、质量监管困难等问题，本项目借鉴广东法律服务网“12348”热线的成功经验，结合RuoYi-Vue成熟的后台管理能力，打造一款**轻量、智能、易运维**的话务系统。系统重点解决以下痛点：

- 电话接入后快速识别用户意图，智能分配专业坐席
- 坐席通话过程中实时获取法律知识辅助，提升应答质量
- 全量通话录音与自动质检，确保服务规范
- 工单全流程跟踪，实现咨询、转办、反馈的闭环


## 三、技术选型

### 3.1 基础框架

基于 **RuoYi-Vue** 进行二次开发，利用其成熟的权限管理、代码生成、数据权限等基础能力，快速构建话务管理后台。

### 3.2 核心组件

| 层次 | 技术选型 | 说明 |
|------|----------|------|
| 后端框架 | Spring Boot + Spring Security | 提供RESTful API与安全认证 |
| ORM | MyBatis + PageHelper | 数据访问与分页 |
| 前端 | Vue 2 + Element UI | 坐席工作台与管理后台UI |
| 工作流 | 自研状态机 + SLA 策略 | 工单流转（Flowable 未引入，配置化流程编排规划中） |
| 语音网关 | SIP协议（FreeSWITCH / Asterisk） | 电话接入与呼叫控制 |
| 语音识别 | ASR（阿里 DashScope / Whisper 兼容端点） | 实时转写通话内容（科大讯飞未接入） |
| 语音合成 | TTS | 自动播报IVR提示 |
| 数据库 | MySQL + Redis | 业务数据存储与缓存 |
| 实时通信 | WebSocket | 坐席状态同步、来电弹屏 |


## 四、系统架构

```
┌─────────────────────────────────────────────────────────┐
│                   电话线路接入层                         │
│     PSTN / 运营商中继  →  SIP网关（FreeSWITCH）        │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                      IVR/排队机                         │
│      欢迎语播报 → 按键导航 → 技能组路由 → 坐席分配     │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                   坐席工作台（前端）                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ 来电弹屏  │ │ 通话控制 │ │ 知识库   │ │ 工单录入 │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │
│  │ 录音回放 │ │ 质检评分 │ │ 统计看板 │ │ 状态切换 │ │
│  └──────────┘ └──────────┘ └──────────┘ └─────────┘ │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                   后端服务层（RuoYi-Vue）              │
│  呼叫控制 │ 坐席管理 │ 录音管理 │ 工单管理 │ 质检引擎 │
└─────────────────────────────────────────────────────────┘
                           │
┌─────────────────────────────────────────────────────────┐
│                   数据存储层                           │
│    MySQL（业务表） + Redis（缓存/队列） + 对象存储（录音）│
└─────────────────────────────────────────────────────────┘
```


## 五、核心功能模块

### 5.1 呼叫控制与路由

| 功能 | 说明 |
|------|------|
| IVR导航 | 自定义语音菜单，支持按键分流（如按1转合同纠纷，按2转劳动纠纷） |
| 智能路由 | 根据主叫号码历史记录、咨询类型、坐席技能等级，自动分配最优坐席 |
| 排队管理 | 当坐席全忙时，播放等待音，支持排队位置播报与超时溢出 |
| 黑名单/白名单 | 支持骚扰电话过滤与VIP用户优先接入 |

### 5.2 坐席工作台

| 功能 | 说明 |
|------|------|
| 来电弹屏 | 自动弹出用户信息（姓名、地区、历史通话记录、未完结工单） |
| 通话控制 | 接听、挂断、保持/恢复、转接（盲转/咨询转）、三方通话 |
| 坐席状态 | 就绪/忙碌/小休/离线，状态切换自动影响路由分配 |
| 知识库速查 | 内置法律知识库，支持关键词搜索，快速获取法条、案例 |
| 通话便签 | 通话过程中快捷记录关键信息，结束后自动生成摘要 |

### 5.3 工单管理

| 功能 | 说明 |
|------|------|
| 工单创建 | 通话结束后自动生成咨询工单，包含用户信息、咨询内容、坐席处理意见 |
| 工单流转 | 支持内部转办（转其他坐席/律师）、外部转办（转12345或司法所） |
| 工单跟踪 | 待处理、处理中、已办结、已关闭多状态跟踪，支持超时提醒 |
| 工单回访 | 支持对已办结工单进行电话回访，记录满意度 |

### 5.4 录音与质检

| 功能 | 说明 |
|------|------|
| 全程录音 | 所有通话自动录音，文件加密存储，支持在线播放与下载 |
| 自动转译 | 通过ASR将录音转为文本，方便检索与复核 |
| 智能质检 | 基于规则（如是否问候、是否使用禁用词、语速、静音时长）自动打分 |
| 人工抽检 | 管理人员可随机抽取录音，进行人工复核与评分 |

### 5.5 坐席与运营管理

| 功能 | 说明 |
|------|------|
| 坐席管理 | 增删改查坐席账号，分配技能组、设置排班 |
| 技能组管理 | 按法律专业（合同、劳动、刑事、婚姻等）划分技能组 |
| 通话报表 | 呼入量、呼出量、平均通话时长、平均等待时长、放弃率等 |
| 满意度统计 | 汇总用户按键评价或回访结果 |
| 质检报告 | 按坐席、技能组、时间段生成质检合格率统计 |


## 六、快速部署

### 6.1 环境要求

- JDK 17（Spring Boot 3.2，最低 17，不再支持 JDK 8）
- MySQL 8.0（容器镜像 mysql:8.0；基线脚本按 MySQL 8 导出）
- Redis 6.0+（容器镜像 redis:7-alpine）
- Node.js 16+
- FreeSWITCH 1.10+（或 Asterisk）

### 6.2 数据库初始化

```bash
# 创建数据库（库名用连字符 ai-law，SQL 中需反引号引用；业务账号名用下划线 ai_law）
mysql -u root -p -e "CREATE DATABASE \`ai-law\` DEFAULT CHARACTER SET utf8mb4;"

# 导入 RuoYi 基础表与 Quartz 调度表
mysql -u root -p ai-law < sql/ry_20250522.sql
mysql -u root -p ai-law < sql/quartz.sql

# 再按文件名时间戳顺序导入 sql/ 下全部 ai_system_*.sql、ai_*.sql 增量脚本。
# 注意：手工增量路径历史脚本含重复菜单 ID（后出脚本已修复），
# 全新环境优先使用 Docker 全量基线，可避免顺序与重复问题。
```

> Docker 部署时数据库由 `deploy/docker/mysql/init/01-init.sql`（全量基线，含种子数据）
> 自动初始化：历史 ALTER 已折叠进建表语句，其中
> `ai_call_record`/`ai_call_record_archive.agent_id` 已为可空，
> `ai_call_agent_status` 与 `sys_user` 均已内置 `sip_extension` 列，无需再手工执行上述 ALTER。

如需在裸机手工执行增量脚本，`sql/` 目录按以下五类顺序处理（同类内按文件名日期先后）：

| 顺序 | 类别 | 文件示例 | 说明 |
|------|------|----------|------|
| 1 | 框架基础 | `ry_20250522.sql`、`quartz.sql` | RuoYi 系统表与 Quartz 调度表，必装 |
| 2 | 业务基线 | `ai_lawyers_tables_20250623.sql`、`ai_user_tables_20231119.sql`、`ai_system_call_center_20260715.sql` 等 | 建表 + 早期种子数据 |
| 3 | 功能增量 | `ai_system_*.sql`、`ai_admin_*.sql`、`ai_lawyers_*.sql` | 按日期递增的功能表/菜单/字典 |
| 4 | 修复回填 | `*_fix_*.sql`、`*_repair_*.sql`、`config_lawyer_sip_*.sql`、`add_lawyer_users_*.sql` | 仅存量库需要，按脚本头部说明执行 |
| 5 | 临时冒烟 | `temp_*.sql`、`*_smoke_data.sql`、`*_smoke_cleanup.sql` | **禁止在生产执行**；造数后必须成对执行 cleanup |

> 第 5 类仅供接口联调造数，`*_data.sql` 与同名 `*_cleanup.sql` 必须配对使用。
> 全新环境推荐直接用 Docker 全量基线，避免手工顺序带来的重复菜单 ID 与漏执行问题。

### 6.3 FreeSWITCH 安装与配置

1. 安装 FreeSWITCH 1.10+（Windows 版默认安装到 `C:\Program Files\FreeSWITCH`）
2. 确认 `conf/sip_profiles/internal.xml` 中开启了 WebSocket 监听：
   ```xml
   <param name="ws-binding"  value=":5066"/>
   <param name="wss-binding" value=":7443"/>
   ```
3. 律师坐席分机配置在仓库 `freeswitch-conf/directory/default/` 下，已预置 **1003～1006**
   （注册密码取 vars.xml 的 `$${default_password}`，默认 `1234`；工号 102～105 对应四位律师）。
   Docker 部署由 compose 卷挂载自动生效；裸机可用 `freeswitch-conf/deploy-agent-extensions.ps1` 一键下发。
4. 启动 FreeSWITCH（**Windows 下建议加 `-nonat` 参数避免 NAT 检测卡死**）：
   ```bat
   cd "C:\Program Files\FreeSWITCH"
   FreeSwitchConsole.exe -nonat -conf conf -log <项目目录>\.fs\log -db <项目目录>\.fs\db -run <项目目录>\.fs\run
   ```
5. 验证端口监听：
   ```
   5060/UDP  SIP 分机注册
   5066/TCP  SIP WebSocket（浏览器网页软电话接入）
   7443/TCP  SIP WSS
   8021/TCP  ESL（后端事件订阅）
   ```

### 6.4 后端启动

```bash
# 修改 ai-admin/src/main/resources/application.yml 中数据库、Redis、FreeSWITCH ESL 配置
# FreeSWITCH ESL 默认连接 127.0.0.1:8021，密码 ClueCon

# 编译打包
mvn clean install -DskipTests

# 启动
java -jar ai-admin/target/ai-admin.jar
```

后端启动后会自动连接 FreeSWITCH ESL，订阅呼叫事件。关键配置项：

```yaml
call:
  inbound:
    default-group-id: 104   # 默认技能组ID（综合咨询组）
  freeswitch:
    host: 127.0.0.1
    port: 8021
    password: ClueCon
```

### 6.5 前端启动

```bash
cd ai-ui
npm install
npm run dev
```

访问 `http://localhost/`（或 `http://localhost:80/`），默认账号 **admin/admin123**。

---

## 七、本地呼入呼出调试

### 7.1 架构说明

本系统支持两种坐席接入方式：

| 接入方式 | 说明 | 适用场景 |
|----------|------|----------|
| 桌面软电话（MicroSIP/Zoiper） | 通过 UDP 注册到 FreeSWITCH 5060 端口 | 传统坐席、生产环境 |
| 浏览器网页软电话（WebRTC + JsSIP） | 坐席签入后自动通过 WebSocket 注册到 5066 端口 | 开发调试、无客户端部署 |

浏览器网页软电话在坐席**签入时自动注册** `sip_extension` 字段对应的分机号，**签出时自动注销**，无需手动配置软电话。

### 7.2 前置准备

1. 启动 MySQL、Redis、FreeSWITCH、后端、前端（见第六章）
2. 确认 FreeSWITCH 已注册分机：
   ```bat
   "C:\Program Files\FreeSWITCH\fs_cli.exe" -x "show registrations"
   ```
3. 如使用桌面软电话测试，可注册任一律师分机（1003～1006，密码均为 `1234`），例如 1003：
   - SIP 服务器：`198.18.0.1`（或 FreeSWITCH 绑定的 IP）
   - 端口：`5060`
   - 用户名/登录名：`1003`
   - 密码：`1234`
   - 传输协议：UDP

> **注意**：若机器安装了 Clash 等代理工具，FreeSWITCH 可能绑定到 Clash TUN 网卡地址 `198.18.0.1`。浏览器访问时需确保该地址可达，或将 Clash 的 TUN 模式临时关闭。

### 7.3 坐席签入（自动注册浏览器分机）

1. 浏览器访问 `http://localhost/`，使用律师账号登录（如 **zhangls/123456**，对应工号 102、分机 1003）。
   注意：admin 账号对应坐席（工号 100）未绑定 `sip_extension`，不能注册网页软电话，仅用于后台管理。
2. 浏览器地址栏右侧弹出麦克风权限请求时，点击**允许**（WebRTC 通话必需）
3. 进入呼叫中心工作台，点击**签入**（工号 102）
4. 观察右上角坐席状态栏：
   - 🟡 `分机 1003 注册中…` → 正在通过 WebSocket 连接 FreeSWITCH
   - 🟢 `分机 1003 已就绪` → 注册成功，可接听来电
   - 🔴 `分机 1003 注册失败` → 鼠标悬停查看错误原因
5. 后端验证：
   ```bat
   fs_cli -x "show registrations"
   ```
   应看到 1003 分机通过 `network_proto=ws` 注册。

### 7.4 呼入测试（1004 拨打 1003）

1. 确保桌面软电话 1004（或另一浏览器登录 lils 签入）已注册，坐席 102（分机 1003）已签入且状态为"空闲"
2. 用软电话 1004 拨打 **1003**
3. 事件链路：

```
FreeSWITCH CHANNEL_CREATE
    → 后端 ESL 捕获（DefaultInboundCallHandler）
    → 插入话单（ai_call_record，agent_id 暂空，分配后回填）
    → ACD 分配坐席（AgentDispatchServiceImpl，策略 round_robin）
    → 桥接分机 1003（uuid_transfer → user/1003）
    → 浏览器收到来电（SipIncomingAlert 浮层 + 来电弹屏）
    → 点击"接听"（WebRTC 建立语音通道）
    → 通话中可：保持/转接/三方/挂机
    → CHANNEL_HANGUP → 话单闭环（end_time/duration/status）
```

4. 后端实时日志（`logs/sys-info.log`）关键字段：
   ```
   [ESL] 入站来话 1004 -> 1003
   [Inbound] 收到来话 caller=1004 dnis=1003
   分配成功：主叫[1004] → 坐席[张律师] 技能组[综合咨询组]
   bridgeToAssignedAgent: 桥接坐席 ext=1003 result=true
   ```

### 7.5 呼出测试

1. 坐席签入后，在工作台外呼面板输入目标号码（如另一部分机 1004）
2. 点击外呼，后端通过 ESL 向 FreeSWITCH 发送 originate 命令
3. FreeSWITCH 先呼叫坐席分机 1003，坐席接听后再呼叫被叫号码
4. 通话建立后同样支持保持、转接、挂机等操作

### 7.6 常用调试命令

```bat
# 查看已注册分机
fs_cli -x "show registrations"

# 查看当前通话通道
fs_cli -x "show channels"

# 查看 sofia profile 状态（含 WS/WSS 绑定地址）
fs_cli -x "sofia status profile internal"

# 查看 SIP 中继状态
fs_cli -x "sofia status gateway"

# 实时查看事件（类似 tcpdump）
fs_cli -x "events plain CHANNEL_CREATE CHANNEL_ANSWER CHANNEL_HANGUP"
```

### 7.7 常见问题排查

| 问题 | 原因 | 解决方法 |
|------|------|----------|
| 浏览器分机一直"注册中…" | 5 秒轮询重复注册导致连接被杀 | 已修复（agent.js 增加 Promise 守卫）；强制刷新页面 |
| FreeSWITCH 启动卡住（68MB 内存不监听端口） | NAT/UPnP 检测卡死 | 启动时加 `-nonat` 参数 |
| 话单插入失败 `Field 'agent_id' doesn't have a default value` | 来话先落单后分配坐席，而列被建成 NOT NULL | 全量基线 01-init.sql 已将两张话单表 agent_id 建为可空；存量库手工执行 `ALTER TABLE ai_call_record MODIFY COLUMN agent_id bigint DEFAULT NULL` |
| 律师分机（1003～1006）软电话未注册导致 FreeSWITCH 崩溃 | B-leg 无法建立 | 使用浏览器自动注册（坐席 sip_extension 须非空，admin 坐席无分机不可用于通话），确保 5066 端口可达 |
| 浏览器无法获取麦克风 | 非 HTTPS/localhost 访问 | 通过 `http://localhost/` 访问，或配置 HTTPS（WSS 7443） |
| ESL 连接断开 | FreeSWITCH 重启 | 后端自动重连，查看日志 `[ESL-127.0.0.1] 鉴权成功` |

---

## 八、项目特色

- ✅ **纯话务聚焦**：完全围绕电话服务场景，不混杂图文、视频等其他渠道
- ✅ **深度集成RuoYi-Vue**：利用框架现成的权限、日志、代码生成，缩短开发周期
- ✅ **智能辅助**：ASR实时转译、知识库检索，减轻坐席负担
- ✅ **灵活路由**：基于技能组、负载、优先级的多策略分配
- ✅ **全链路质检**：自动+人工双重保障，提升服务质量
- ✅ **工单闭环**：从咨询到回访，全程可追溯


## 九、后续规划

- 对接大模型实现智能问答辅助（生成回复建议）
- 增加预测式外呼，主动服务
- 引入实时语音情感分析，辅助坐席调整沟通策略
- 支持与第三方政务平台（如12345）工单互通


## 十、文档索引

项目文档统一维护在**语雀知识库「HMC开源」→「AI-lawyer」目录**，仓库不再存放文档正文，仅保留本索引。

| 文档 | 链接 |
|---|---|
| AI律师话务系统综合文档 V2.0 | https://www.yuque.com/wengxiulin/gp6u6a/pun8a91kfh791tr0 |
| 项目操作手册 | https://www.yuque.com/wengxiulin/gp6u6a/ulk94bdsfdem7uwx |
| 优化方向调研报告 V1.0 | https://www.yuque.com/wengxiulin/gp6u6a/rqccg6dw32l9ab63 |
| 代码结构盘点与优化规划 V1.0 | https://www.yuque.com/wengxiulin/gp6u6a/mmv876ixargouhul |
| 容量基线与压测常态化 P7 | https://www.yuque.com/wengxiulin/gp6u6a/rwl70gi4qca4bh8n |
| 下一步开发计划 V1.0 | https://www.yuque.com/wengxiulin/gp6u6a/next-dev-plan-v1 |

> 自 2026-11-11 起语雀为唯一维护源，本地 `doc/` 已移除；仓库内不再保留文档正文。