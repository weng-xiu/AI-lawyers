# 12348热线话务系统 - 实现计划

## [ ] Task 1: 创建数据库表结构
- **Priority**: high
- **Depends On**: None
- **Description**: 
  - 创建坐席状态表(ai_call_agent_status)
  - 创建来电记录表(ai_call_record)
  - 创建工单表(ai_call_ticket)
  - 创建转接记录表(ai_call_transfer)
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4
- **Test Requirements**:
  - `programmatic` TR-1.1: 执行SQL脚本后所有表创建成功
  - `programmatic` TR-1.2: 表结构与设计文档一致
- **Notes**: SQL文件放在sql目录下，命名规则为ai_system_call_center_当前时间.sql

## [x] Task 2: 坐席管理模块开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 创建坐席状态实体类(AiCallAgentStatus)
  - 创建坐席状态Mapper接口和XML
  - 创建坐席状态Service接口和实现
  - 创建坐席管理Controller
  - 实现坐席登录/注销、状态切换功能
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `programmatic` TR-2.1: 坐席登录成功，状态更新为在线
  - `programmatic` TR-2.2: 坐席注销成功，状态更新为离线
  - `programmatic` TR-2.3: 状态切换（在线/忙碌/休息/离线）功能正常
- **Notes**: 后端代码放在ai-system模块的lawyers包下

## [x] Task 3: 来电记录模块开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 创建来电记录实体类(AiCallRecord)
  - 创建来电记录Mapper接口和XML
  - 创建来电记录Service接口和实现
  - 创建来电记录Controller
  - 实现来电记录的增删改查功能
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `programmatic` TR-3.1: 创建来电记录成功，数据正确保存
  - `programmatic` TR-3.2: 查询来电记录列表正常分页
  - `programmatic` TR-3.3: 更新来电记录信息正常
  - `programmatic` TR-3.4: 删除来电记录正常
- **Notes**: 后端代码放在ai-system模块的lawyers包下

## [x] Task 4: 工单管理模块开发
- **Priority**: high
- **Depends On**: Task 1
- **Description**: 
  - 创建工单实体类(AiCallTicket)
  - 创建工单Mapper接口和XML
  - 创建工单Service接口和实现
  - 创建工单Controller
  - 实现工单创建、流转、状态跟踪功能
- **Acceptance Criteria Addressed**: AC-3
- **Test Requirements**:
  - `programmatic` TR-4.1: 创建工单成功，数据正确保存
  - `programmatic` TR-4.2: 工单流转（待处理/处理中/已完成/已归档）正常
  - `programmatic` TR-4.3: 查询工单列表正常分页
  - `programmatic` TR-4.4: 工单归档功能正常
- **Notes**: 后端代码放在ai-system模块的lawyers包下

## [x] Task 5: 咨询转接功能开发
- **Priority**: medium
- **Depends On**: Task 1, Task 2, Task 3
- **Description**: 
  - 创建转接记录实体类(AiCallTransfer)
  - 创建转接记录Mapper接口和XML
  - 创建转接记录Service接口和实现
  - 在来电记录Controller中添加转接接口
  - 实现咨询转接功能，记录转接历史
- **Acceptance Criteria Addressed**: AC-4
- **Test Requirements**:
  - `programmatic` TR-5.1: 转接记录创建成功
  - `programmatic` TR-5.2: 来电记录状态更新为已转接
  - `programmatic` TR-5.3: 查询转接历史记录正常
- **Notes**: 后端代码放在ai-system模块的lawyers包下

## [x] Task 6: 统计分析模块开发（已在来电记录模块中实现）
- **Priority**: medium
- **Depends On**: Task 1, Task 3
- **Description**: 
  - 在来电记录Service中添加统计方法
  - 创建统计分析Controller
  - 实现话务量统计、坐席绩效统计、咨询分类统计功能
- **Acceptance Criteria Addressed**: AC-5
- **Test Requirements**:
  - `programmatic` TR-6.1: 话务量统计接口返回正确数据
  - `programmatic` TR-6.2: 坐席绩效统计接口返回正确数据
  - `programmatic` TR-6.3: 咨询分类统计接口返回正确数据
- **Notes**: 后端代码放在ai-system模块的lawyers包下

## [x] Task 7: 前端API接口开发
- **Priority**: high
- **Depends On**: Task 2, Task 3, Task 4, Task 5, Task 6
- **Description**: 
  - 创建前端API文件(callCenter.js)
  - 封装坐席管理、来电记录、工单管理、转接记录、统计分析接口
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-7.1: 所有API接口封装完整
  - `human-judgment` TR-7.2: API命名规范，与后端接口对应
- **Notes**: 前端代码放在ai-ui/src/api/lawyers目录下

## [x] Task 8: 前端页面开发
- **Priority**: high
- **Depends On**: Task 7
- **Description**: 
  - 创建坐席管理页面(callAgent.vue)
  - 创建来电记录页面(callRecord.vue)
  - 创建工单管理页面(callTicket.vue)
  - 创建统计分析页面(callStatistics.vue)
  - 创建坐席工作台页面(callWorkbench.vue)
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `human-judgment` TR-8.1: 页面布局合理，操作便捷
  - `human-judgment` TR-8.2: 数据展示完整，交互流畅
  - `human-judgment` TR-8.3: 坐席工作台便于快速处理来电
- **Notes**: 前端代码放在ai-ui/src/views/lawyers/callCenter目录下

## [x] Task 9: 菜单配置与权限设置
- **Priority**: medium
- **Depends On**: Task 2, Task 3, Task 4, Task 5, Task 6
- **Description**: 
  - 在系统菜单中添加话务系统相关菜单
  - 配置坐席管理、来电记录、工单管理等权限标识
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5
- **Test Requirements**:
  - `programmatic` TR-9.1: 菜单配置成功，页面可访问
  - `programmatic` TR-9.2: 权限控制正常，无权限用户无法访问
- **Notes**: 通过系统管理后台配置菜单和权限

## [x] Task 10: 系统集成测试与调试
- **Priority**: high
- **Depends On**: Task 2, Task 3, Task 4, Task 5, Task 6, Task 7, Task 8, Task 9
- **Description**: 
  - 测试前后端接口联调
  - 测试坐席管理完整流程
  - 测试来电记录与工单关联流程
  - 测试统计分析功能
- **Acceptance Criteria Addressed**: AC-1, AC-2, AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `programmatic` TR-10.1: 所有接口返回正确状态码
  - `human-judgment` TR-10.2: 端到端流程顺畅，无异常
- **Notes**: 覆盖主要业务场景
