# 智能法律咨询需求分析

## 1. 功能概述

智能法律咨询功能旨在为用户提供基于AI的法律咨询服务。用户可以通过输入法律问题，系统将基于法律知识库和自然语言处理技术，自动生成简要的法律解答。

## 2. 用户角色

### 2.1 普通用户
- 可以提交法律咨询问题
- 查看历史咨询记录
- 对咨询结果进行评价

### 2.2 管理员
- 管理法律知识库
- 查看所有用户的咨询记录
- 维护系统配置

## 3. 功能需求

### 3.1 用户端功能

#### 3.1.1 问题提交
- 用户可以通过文本输入框提交法律问题
- 支持问题分类选择（如婚姻家庭、劳动纠纷、合同纠纷等）
- 支持上传相关文档（可选）
- 提交后显示处理中状态

#### 3.1.2 咨询结果展示
- 展示AI生成的法律解答
- 提供相关法律条文参考
- 显示解答的置信度
- 提供相关案例参考（如有）

#### 3.1.3 历史记录
- 用户可查看历史咨询记录
- 支持按时间、分类筛选
- 支持关键词搜索

#### 3.1.4 评价功能
- 用户可对咨询结果进行满意度评价
- 支持文字反馈
- 评价数据用于模型优化

### 3.2 管理端功能

#### 3.2.1 知识库管理
- 添加、修改、删除法律条文
- 知识库分类管理
- 批量导入法律知识
- 知识库内容审核

#### 3.2.2 咨询记录管理
- 查看所有用户的咨询记录
- 支持多条件筛选查询
- 导出咨询数据
- 统计分析功能

#### 3.2.3 系统配置
- AI模型参数配置
- 咨询分类管理
- 系统提示词配置

## 4. 非功能需求

### 4.1 性能需求
- 问题响应时间不超过5秒
- 支持并发用户数不少于100人
- 系统可用性达到99.9%

### 4.2 安全需求
- 用户数据加密存储
- 咨询内容隐私保护
- 防止SQL注入和XSS攻击
- 操作日志记录

### 4.3 可用性需求
- 界面简洁友好，操作直观
- 支持移动端访问
- 提供操作指引

## 5. 技术架构

### 5.1 前端技术
- Vue.js框架
- Element UI组件库
- Axios HTTP客户端

### 5.2 后端技术
- Spring Boot框架
- MyBatis数据持久层
- MySQL数据库

### 5.3 AI技术
- 自然语言处理模型
- 法律知识图谱
- 问答匹配算法

## 6. 数据模型设计

### 6.1 法律咨询记录表(ai_legal_consultation)
```sql
create table ai_legal_consultation (
  consultation_id    bigint(20)      not null auto_increment    comment '咨询ID',
  user_id           bigint(20)      not null                   comment '用户ID',
  question          text            not null                   comment '咨询问题',
  category_id       bigint(20)      default null               comment '问题分类ID',
  answer            text                                       comment 'AI回答',
  confidence        decimal(5,2)    default null               comment '回答置信度',
  status            char(1)         default '0'                comment '状态（0处理中 1已完成 2失败）',
  attachment_path   varchar(500)    default null               comment '附件路径',
  rating            int(1)          default null               comment '用户评分(1-5)',
  feedback          varchar(500)    default null               comment '用户反馈',
  create_time       datetime                                   comment '创建时间',
  update_time       datetime                                   comment '更新时间',
  primary key (consultation_id)
) engine=innodb comment = '法律咨询记录表';
```

### 6.2 法律知识库表(ai_legal_knowledge)
```sql
create table ai_legal_knowledge (
  knowledge_id      bigint(20)      not null auto_increment    comment '知识ID',
  title             varchar(200)    not null                   comment '知识标题',
  category_id       bigint(20)      not null                   comment '分类ID',
  content           text            not null                   comment '知识内容',
  law_article       varchar(100)    default null               comment '法律条文',
  source            varchar(200)    default null               comment '来源',
  keywords          varchar(500)    default null               comment '关键词',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (knowledge_id)
) engine=innodb comment = '法律知识库表';
```

### 6.3 咨询分类表(ai_consultation_category)
```sql
create table ai_consultation_category (
  category_id       bigint(20)      not null auto_increment    comment '分类ID',
  category_name     varchar(50)     not null                   comment '分类名称',
  parent_id         bigint(20)      default 0                  comment '父分类ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  status            char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (category_id)
) engine=innodb comment = '咨询分类表';
```

## 7. 接口设计

### 7.1 用户端接口

#### 7.1.1 提交法律咨询
```
POST /ai-admin/lawyers/consultation/submit
参数:
- question: 咨询问题
- categoryId: 问题分类ID
- attachment: 附件文件(可选)

返回:
- consultationId: 咨询ID
- status: 处理状态
```

#### 7.1.2 查询咨询结果
```
GET /ai-admin/lawyers/consultation/{consultationId}
参数:
- consultationId: 咨询ID

返回:
- question: 咨询问题
- answer: AI回答
- confidence: 置信度
- status: 状态
- createTime: 创建时间
```

#### 7.1.3 获取咨询历史
```
GET /ai-admin/lawyers/consultation/history
参数:
- pageNum: 页码
- pageSize: 每页数量
- categoryId: 分类ID(可选)
- keyword: 关键词(可选)

返回:
- total: 总数
- rows: 咨询记录列表
```

#### 7.1.4 提交评价
```
POST /ai-admin/lawyers/consultation/rating
参数:
- consultationId: 咨询ID
- rating: 评分(1-5)
- feedback: 反馈内容(可选)

返回:
- success: 是否成功
```

### 7.2 管理端接口

#### 7.2.1 知识库管理
```
POST /ai-admin/lawyers/knowledge/add
PUT /ai-admin/lawyers/knowledge/edit
DELETE /ai-admin/lawyers/knowledge/{knowledgeIds}
GET /ai-admin/lawyers/knowledge/list
GET /ai-admin/lawyers/knowledge/{knowledgeId}
```

#### 7.2.2 咨询记录管理
```
GET /ai-admin/lawyers/consultation/list
GET /ai-admin/lawyers/consultation/{consultationId}
GET /ai-admin/lawyers/consultation/export
```

#### 7.2.3 分类管理
```
POST /ai-admin/lawyers/category/add
PUT /ai-admin/lawyers/category/edit
DELETE /ai-admin/lawyers/category/{categoryIds}
GET /ai-admin/lawyers/category/list
GET /ai-admin/lawyers/category/{categoryId}
```

## 8. 页面设计

### 8.1 用户端页面

#### 8.1.1 法律咨询页面(/ai-ui/src/views/lawyers/consultation/index.vue)
- 问题输入区域
- 分类选择
- 文件上传
- 提交按钮
- 历史记录标签页

#### 8.1.2 咨询结果页面(/ai-ui/src/views/lawyers/consultation/result.vue)
- 问题展示
- AI回答展示
- 置信度显示
- 相关法律条文
- 评价区域

#### 8.1.3 历史记录页面(/ai-ui/src/views/lawyers/consultation/history.vue)
- 搜索条件
- 记录列表
- 分页组件

### 8.2 管理端页面

#### 8.2.1 知识库管理页面(/ai-ui/src/views/lawyers/knowledge/index.vue)
- 知识库列表
- 添加/编辑弹窗
- 搜索筛选
- 批量操作

#### 8.2.2 咨询记录管理页面(/ai-ui/src/views/lawyers/consultation/manage.vue)
- 咨询记录列表
- 详情查看
- 数据导出
- 统计图表

#### 8.2.3 分类管理页面(/ai-ui/src/views/lawyers/category/index.vue)
- 分类树形结构
- 添加/编辑分类
- 拖拽排序

## 9. 实施计划

### 9.1 第一阶段（2周）
- 数据库设计与创建
- 基础框架搭建
- 用户端问题提交功能
- 管理端知识库管理功能

### 9.2 第二阶段（2周）
- AI模型集成
- 用户端咨询结果展示
- 历史记录功能
- 评价功能

### 9.3 第三阶段（1周）
- 管理端咨询记录管理
- 分类管理
- 系统配置
- 测试与优化

## 10. 风险与对策

### 10.1 技术风险
- AI模型准确性不足：通过持续训练和优化模型，引入人工审核机制
- 系统性能问题：采用缓存技术，优化数据库查询

### 10.2 业务风险
- 法律责任风险：添加免责声明，明确AI建议仅供参考
- 数据隐私风险：加强数据加密，严格控制访问权限

## 11. 后续扩展

- 支持语音输入和输出
- 集成更多法律领域的专业知识
- 开发移动端APP
- 引入律师在线咨询服务