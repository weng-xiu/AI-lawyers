# 12348热线话务系统 V2.0 - 实现计划（基于原型优化）

## [ ] Task 1: 整体UI框架升级（深蓝顶栏+深灰侧栏）
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 顶部导航栏改为深蓝色（#1e3a8a），包含12348Logo、坐席信息（头像+姓名+在线状态）、全局搜索、通知、设置
  - 侧边栏改为深灰色（#1e293b），按原型分为5组菜单：工作台、话务服务、业务管理、回访与支持
  - 新增菜单分组标题和分隔线
  - 主内容区背景调整为浅灰（#f1f5f9）
  - 统一卡片圆角、阴影、间距设计规范
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `human-judgement` TR-1.1: 顶栏深蓝、侧栏深灰、主区浅灰、卡片白色，风格统一
  - `human-judgement` TR-1.2: 菜单项按5组分组，有分组标题和图标
  - `human-judgement` TR-1.3: 顶栏包含Logo、坐席信息、搜索框、通知、设置图标
- **Notes**: 修改Navbar、Sidebar、variables.scss、sidebar.scss

## [ ] Task 2: 工作台页面重做
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 顶部欢迎横幅：渐变蓝背景，左侧姓名+日期问候，右侧今日服务数+满意度
  - 4个个人统计卡片：总通话数、通话时长、服务满意度、在线时长，带同比变化箭头
  - 团队概览卡片：4项团队数据
  - 公告通知+快捷入口双栏布局
  - 最近通话记录列表
- **Acceptance Criteria Addressed**: AC-1, AC-6
- **Test Requirements**:
  - `human-judgement` TR-2.1: 7个模块布局与原型一致
  - `human-judgement` TR-2.2: 统计卡片有同比箭头（上升/下降）
  - `human-judgement` TR-2.3: 快捷入口8个图标布局整齐
- **Notes**: 重写 callWorkbench.vue

## [ ] Task 3: 话务功能面板页面开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 坐席状态条：状态切换标签（置闲/置忙/小休/会议/培训）、签入签出信息
  - 等待来电横幅：深蓝渐变背景，显示"空闲等待来电中"和等待时长
  - 功能按钮区：9个功能按钮（签入、签出、外呼、保持/恢复、转移、咨询、三方通话、话后整理、挂机）
  - 排队信息：左侧排队列表（序号、号码、类型、时长）+平均等待时长
  - 今日话务统计：4个小卡片+小时分布柱状图(ECharts)+客户满意度进度条
  - 技能组信息：3个技能组进度条（民商、劳动、婚姻）
- **Acceptance Criteria Addressed**: AC-2, AC-6
- **Test Requirements**:
  - `human-judgement` TR-3.1: 6个区域布局完整
  - `human-judgement` TR-3.2: ECharts柱状图正常渲染
  - `human-judgement` TR-3.3: 技能组进度条颜色区分
- **Notes**: 新建 callPanel.vue

## [ ] Task 4: 来电弹屏页面开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 来电横幅：绿色渐变背景，显示大号码、归属地、IVR分类、通话时长计时
  - 左侧来电人信息卡：头像、姓名、电话、归属地、来电次数、标签
  - 右侧历史记录Tab切换：历史通话、历史工单、来电轨迹、用户画像
  - 智能来电分析区：意图预测、咨询偏好、高频问题
  - 情绪预警提示条
  - 推荐知识文章
  - 底部操作栏：6个操作按钮
- **Acceptance Criteria Addressed**: AC-3, AC-6
- **Test Requirements**:
  - `human-judgement` TR-4.1: 绿色来电横幅醒目，信息完整
  - `human-judgement` TR-4.2: 4个Tab切换流畅
  - `human-judgement` TR-4.3: 智能分析区域3列布局
  - `human-judgement` TR-4.4: 底部操作栏6个按钮对齐
- **Notes**: 新建 callPop.vue

## [ ] Task 5: 工单管理页面重做（侧滑详情+流转记录）
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 顶部3个统计卡：待处理、处理中、已办结（不同颜色图标）
  - 多条件筛选栏：工单状态、业务类型、时间范围、紧急程度
  - 工单列表：工单号链接、标题、业务类型标签、申请人、当前环节、紧急程度（普通/紧急/特急）、创建时间
  - 右侧滑出详情面板：
    - 标题+状态标签
    - 工单基本信息（两列网格）
    - 问题描述
    - 流转记录时间线（圆点+线条+节点信息）
- **Acceptance Criteria Addressed**: AC-4, AC-6
- **Test Requirements**:
  - `human-judgement` TR-5.1: 3个统计卡片颜色区分
  - `human-judgement` TR-5.2: 工单列表行点击后侧滑面板从右侧滑出
  - `human-judgement` TR-5.3: 流转记录时间线样式正确（当前节点高亮）
  - `human-judgement` TR-5.4: 紧急程度标签颜色区分（普通/紧急/特急）
- **Notes**: 重写 callTicket.vue，新增drawer侧滑面板

## [ ] Task 6: 台账记录页面开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - 左侧主区（约70%宽度）：
    - 台账模板选择下拉
    - 自动填充信息区（带标签"来自通话数据"）：来电号码/来电人/服务渠道/接入时间/咨询类型
    - 台账内容区：咨询内容摘要（必填）、处理结果（必填）、满意度星级评价、转工单开关、附件上传虚线框
  - 右侧历史台账（约30%宽度）：
    - 3个统计数字：今日/本周/待转工单
    - 搜索框+状态筛选下拉
    - 台账列表卡片：工单号+类型标签、摘要、状态标签、时间、操作（查看/编辑/转工单）
- **Acceptance Criteria Addressed**: AC-5, AC-6
- **Test Requirements**:
  - `human-judgement` TR-6.1: 左右双栏布局比例约7:3
  - `human-judgement` TR-6.2: 满意度星级可交互点击
  - `human-judgement` TR-6.3: 右侧台账卡片样式与原型一致
  - `human-judgement` TR-6.4: 附件上传区虚线框样式
- **Notes**: 新建 callLedger.vue + 后端台账实体/接口

## [ ] Task 7: 后端台账管理模块开发
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - 实体类 AiCallLedger：台账ID、台账编号、模板类型、来电号码、来电人、服务渠道、接入时间、咨询类型、内容摘要、处理结果、满意度、是否转工单、关联工单号、附件URL
  - Mapper接口及XML
  - Service接口及实现
  - Controller：CRUD + 按状态/日期筛选 + 转工单
  - SQL建表脚本
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `programmatic` TR-7.1: 台账CRUD接口可用
  - `programmatic` TR-7.2: 按条件筛选分页查询正常
  - `programmatic` TR-7.3: 转工单后生成工单记录并关联
- **Notes**: 遵循项目后端代码规范

## [ ] Task 8: 菜单配置与路由注册
- **Priority**: medium
- **Depends On**: Task 2, 3, 4, 5, 6
- **Description**:
  - 更新SQL菜单脚本，按5组重新配置菜单结构
  - 前端路由注册：话务功能面板、来电弹屏、台账记录
  - 权限标识配置
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `human-judgement` TR-8.1: 左侧菜单按5组展示，分组有标题
  - `human-judgement` TR-8.2: 点击各菜单正确跳转
- **Notes**: 更新 ai_admin_call_center_menu_20260716.sql

## [ ] Task 9: 联调与视觉走查
- **Priority**: medium
- **Depends On**: Task 1-8
- **Description**:
  - 所有页面编译通过
  - 路由跳转正常
  - 与原型对比视觉一致性检查
  - 响应式基本适配
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `programmatic` TR-9.1: 前端编译无错误
  - `human-judgement` TR-9.2: 5个核心页面视觉效果与原型一致度≥80%
  - `human-judgement` TR-9.3: 各页面间跳转无白屏/报错
