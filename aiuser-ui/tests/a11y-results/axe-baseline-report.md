# 公众端（aiuser-ui）无障碍 axe 扫描回归报告

- 扫描日期：2026-10-31
- 工具：Playwright + @axe-core/playwright（axe-core）
- 规则标签：WCAG 2.0/2.1 Level A、AA
- 环境：dev server（默认 8091，避开已被旧项目占用的 81/8081；设置 A11Y_BASE_URL 可复用已启动的服务），后端接口由测试统一 mock（空态），不影响 DOM 违规判定
- 对照标准：GB/T 37668-2019《互联网内容无障碍可访问性技术要求与测试方法》基础项

## 一、各页面违规汇总

| 页面 | 违规总数 | critical | serious | moderate | minor |
| --- | --- | --- | --- | --- | --- |
| messages | 0 | 0 | 0 | 0 | 0 |
| 首页 /（匿名） | 0 | 0 | 0 | 0 | 0 |
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

- V8 回归（2026-10-31，10 条路由含新增首页）：**全部页面 violations=0，critical / serious 已清零**。此前基线建立在白屏页面上（App.vue 关怀模式计算属性结构错误），本轮先修复白屏再重跑扫描。
- 本轮整改（ElementUI 2.15.14 的 ARIA 缺陷与主题对比度）：
  1. `aria-required-attr` / `nested-interactive`：头部关怀设置 `el-radio-button`、`el-switch` 与工单状态筛选 `el-radio-group` 的未选中节点丢失 `aria-checked`，且内嵌原生 input → 抽取 `CareControls`（原生 radio + checkbox），工单筛选改为原生 radio 分段控件。
  2. `aria-valid-attr-value`：`el-dropdown` 将 `aria-haspopup` 固定写成非法值 `list` → 挂载后纠正为 `menu`。
  3. `label`：`el-switch` 的隐藏 checkbox 无可访问名、`el-pagination` jumper 的 number 输入框无标签 → 使用原生 checkbox 包裹 label、移除 jumper。
  4. `button-name`：分页「上一页/下一页」为纯图标按钮 → 使用 `prev-text` / `next-text` 提供可见名称。
  5. `aria-input-field-name` / `aria-progressbar-name`：`el-rate`、`el-progress` 缺少可访问名 → 补 `aria-label`。
  6. `color-contrast`：字数统计与 info 提示沿用主题 `$--color-info(#8C8C8C)`，在白/浅灰底仅 3.0~3.4:1 → 覆盖为 `#5b6b84` / `#44505f`，咨询结果提问时间改为深灰蓝。
- 历史基线（2026-10-11）阻断项及对应修复：
  1. 注册页 `el-radio` 缺 `aria-checked` 且交互嵌套 → 已替换为原生 radio（label + input，accent-color 司法蓝）。
  2. `color-contrast`（serious）：ElementUI 默认蓝 #409eff 白底对比度仅 2.78:1 → 已以司法蓝 #1A3C6E（白底 10.97:1）重建主题。
- html lang、图片 alt、表单可访问名等 GB/T 37668-2019 基础项持续通过。
- axe 只能覆盖约 30% 无障碍问题；焦点管理、读屏顺序、语义完整性等仍需 NVDA 真机走查（另见 F2 检测计划）。
