# 公众端（aiuser-ui）无障碍 axe 基线扫描报告

- 扫描日期：2026-09-19
- 工具：Playwright + @axe-core/playwright（axe-core）
- 规则标签：WCAG 2.0/2.1 Level A、AA
- 环境：dev server http://localhost:8091（vue-cli 自动分配，避开已被旧项目占用的 81/8081），后端接口由测试统一 mock（空态），不影响 DOM 违规判定
- 对照标准：GB/T 37668-2019《互联网内容无障碍可访问性技术要求与测试方法》基础项

## 一、各页面违规汇总

| 页面 | 违规总数 | critical | serious | moderate | minor |
| --- | --- | --- | --- | --- | --- |
| 用户登录 /login | 1 | 0 | 2 | 0 | 0 |
| 用户注册 /register | 3 | 2 | 6 | 0 | 0 |
| 服务导航 /services（匿名） | 0 | 0 | 0 | 0 | 0 |
| 我的工单 /ticket | 0 | 0 | 0 | 0 | 0 |
| 渠道绑定 /channels | 0 | 0 | 0 | 0 | 0 |
| 提交法律咨询 /consultation/submit | 0 | 0 | 0 | 0 | 0 |
| 咨询结果 /consultation/result | 0 | 0 | 0 | 0 | 0 |
| 咨询历史 /consultation/history | 0 | 0 | 0 | 0 | 0 |
| 咨询评价 /consultation/evaluation | 0 | 0 | 0 | 0 | 0 |
| **合计（违规节点数）** | — | **2** | **8** | 0 | 0 |

## 二、违规规则分布（去重）

| 规则 | 影响级别 | 说明 | 命中页面（节点数） |
| --- | --- | --- | --- |
| aria-required-attr | critical | Required ARIA attributes must be provided | register(2) |
| color-contrast | serious | Elements must meet minimum color contrast ratio thresholds | login(2)、register(3) |
| nested-interactive | serious | Interactive controls must not be nested | register(3) |

## 三、critical / serious 违规明细（含元素定位）

### 用户登录 /login

#### color-contrast [serious] — Elements must meet minimum color contrast ratio thresholds

> Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds

1. `<span data-v-26084dc2="">登 录</span>`
   - 定位：`span > span`
   - 判定：Fix any of the following: Element has insufficient color contrast of 2.78 (foreground color: #ffffff, background color: #409eff, font size: 10.5pt (14px), font weight: normal). Expected contrast ratio of 4.5:1
2. `<span class="el-link--inner">立即注册</span>`
   - 定位：`.el-link--inner`
   - 判定：Fix any of the following: Element has insufficient color contrast of 2.78 (foreground color: #409eff, background color: #ffffff, font size: 10.5pt (14px), font weight: normal). Expected contrast ratio of 4.5:1

### 用户注册 /register

#### aria-required-attr [critical] — Required ARIA attributes must be provided

> Ensures elements with ARIA roles have all required ARIA attributes

1. `<label data-v-63ae9146="" role="radio" tabindex="-1" class="el-radio">`
   - 定位：`.el-radio[role="radio"]:nth-child(1)`
   - 判定：Fix any of the following: Required ARIA attribute not present: aria-checked
2. `<label data-v-63ae9146="" role="radio" tabindex="-1" class="el-radio">`
   - 定位：`.el-radio[role="radio"]:nth-child(2)`
   - 判定：Fix any of the following: Required ARIA attribute not present: aria-checked

#### color-contrast [serious] — Elements must meet minimum color contrast ratio thresholds

> Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds

1. `<span class="el-radio__label">未知<!----></span>`
   - 定位：`.is-checked.el-radio[role="radio"] > .el-radio__label`
   - 判定：Fix any of the following: Element has insufficient color contrast of 2.78 (foreground color: #409eff, background color: #ffffff, font size: 10.5pt (14px), font weight: normal). Expected contrast ratio of 4.5:1
2. `<span>注 册</span>`
   - 定位：`.el-button--primary > span`
   - 判定：Fix any of the following: Element has insufficient color contrast of 2.78 (foreground color: #ffffff, background color: #409eff, font size: 10.5pt (14px), font weight: normal). Expected contrast ratio of 4.5:1
3. `<span>返回登录</span>`
   - 定位：`.el-button--text > span`
   - 判定：Fix any of the following: Element has insufficient color contrast of 2.78 (foreground color: #409eff, background color: #ffffff, font size: 10.5pt (14px), font weight: normal). Expected contrast ratio of 4.5:1

#### nested-interactive [serious] — Interactive controls must not be nested

> Ensures interactive controls are not nested as they are not always announced by screen readers or can cause focus problems for assistive technologies

1. `<label data-v-63ae9146="" role="radio" tabindex="-1" class="el-radio">`
   - 定位：`.el-radio[role="radio"]:nth-child(1)`
   - 判定：Fix any of the following: Using a negative tabindex on an element inside an interactive control does not prevent assistive technologies from focusing the element (even with aria-hidden="true")
2. `<label data-v-63ae9146="" role="radio" tabindex="-1" class="el-radio">`
   - 定位：`.el-radio[role="radio"]:nth-child(2)`
   - 判定：Fix any of the following: Using a negative tabindex on an element inside an interactive control does not prevent assistive technologies from focusing the element (even with aria-hidden="true")
3. `<label data-v-63ae9146="" role="radio" aria-checked="true" tabindex="0" class="el-radio is-checked">`
   - 定位：`.is-checked.el-radio[role="radio"]`
   - 判定：Fix any of the following: Using a negative tabindex on an element inside an interactive control does not prevent assistive technologies from focusing the element (even with aria-hidden="true")

## 四、结论与修复建议

- 本报告为**存量基线**，记录整改前违规水位；修复后重跑 `npm run test:a11y` 回归对比。
- 实测阻断项集中在**登录、注册两页**（其余 7 页 critical/serious 为 0）：
  1. `aria-required-attr`（critical）：注册页性别 `el-radio` 渲染出的 `role="radio"` 节点缺 `aria-checked`，属 ElementUI 2.15.14 单选组件在该用法下的 ARIA 缺陷，建议升级/替换为显式 `aria-checked` 绑定或改用原生 radio。
  2. `nested-interactive`（serious）：同一性别单选项的交互式嵌套，随上一条一并处理。
  3. `color-contrast`（serious）：ElementUI 主色 `#409eff` 在白底（2.78:1）与白字主按钮上均低于 4.5:1，登录/注册按钮及链接受影响；建议在政务主题中加深主色（约 `#2070d0` 及更深可达标）。
- html lang、图片 alt、表单可访问名等 GB/T 37668-2019 基础项本次自动化扫描**已通过**，不在阻断清单内。
- axe 只能覆盖约 30% 无障碍问题；焦点管理、读屏顺序、语义完整性、登录/注册页未渲染关怀模式入口等仍需 NVDA 真机走查（另见 F2 检测计划）。
