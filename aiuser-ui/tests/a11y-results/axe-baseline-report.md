# 公众端（aiuser-ui）无障碍 axe 扫描回归报告

- 扫描日期：2026-10-11
- 工具：Playwright + @axe-core/playwright（axe-core）
- 规则标签：WCAG 2.0/2.1 Level A、AA
- 环境：dev server http://localhost:8091（vue-cli 自动分配，避开已被旧项目占用的 81/8081），后端接口由测试统一 mock（空态），不影响 DOM 违规判定
- 对照标准：GB/T 37668-2019《互联网内容无障碍可访问性技术要求与测试方法》基础项

## 一、各页面违规汇总

| 页面 | 违规总数 | critical | serious | moderate | minor |
| --- | --- | --- | --- | --- | --- |
| 用户登录 /login | 0 | 0 | 0 | 0 | 0 |
| 用户注册 /register | 0 | 0 | 0 | 0 | 0 |
| 服务导航 /services（匿名） | 0 | 0 | 0 | 0 | 0 |
| 我的工单 /ticket | 0 | 0 | 0 | 0 | 0 |
| 渠道绑定 /channels | 0 | 0 | 0 | 0 | 0 |
| 提交法律咨询 /consultation/submit | 0 | 0 | 0 | 0 | 0 |
| 咨询结果 /consultation/result | 0 | 0 | 0 | 0 | 0 |
| 咨询历史 /consultation/history | 0 | 0 | 0 | 0 | 0 |
| 咨询评价 /consultation/evaluation | 0 | 0 | 0 | 0 | 0 |
| **合计（违规节点数）** | — | **0** | **0** | 0 | 0 |

## 二、违规规则分布（去重）

| 规则 | 影响级别 | 说明 | 命中页面（节点数） |
| --- | --- | --- | --- |

## 三、critical / serious 违规明细（含元素定位）

## 四、结论与整改说明

- V1 整改（司法蓝主题 #1A3C6E 重建 Element 主题 + 注册页性别单选改原生 radio）后回归：**全部 9 页 violations=0，critical / serious 已清零**。
- 整改前基线（2026-10-11）阻断项及对应修复：
  1. `aria-required-attr`（critical）+ `nested-interactive`（serious）：注册页 `el-radio`（ElementUI 2.15.14）渲染的 `role="radio"` 节点缺 `aria-checked` 且交互嵌套 → 已替换为原生 radio（label + input，accent-color 司法蓝），ARIA 语义完整。
  2. `color-contrast`（serious）：ElementUI 默认蓝 #409eff 白底对比度仅 2.78:1 → 已以司法蓝 #1A3C6E（白底 10.97:1）重建主题，并清除 ConsultationResult 等页面的硬编码色值。
- html lang、图片 alt、表单可访问名等 GB/T 37668-2019 基础项持续通过。
- axe 只能覆盖约 30% 无障碍问题；焦点管理、读屏顺序、语义完整性等仍需 NVDA 真机走查（另见 F2 检测计划）。
