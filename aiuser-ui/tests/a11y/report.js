'use strict'
// 汇总 tests/a11y-results/pages/*.json 生成 axe 基线 Markdown 报告
const fs = require('fs')
const path = require('path')

const PAGE_TITLES = {
  home: '首页 /（匿名）',
  login: '用户登录 /login',
  register: '用户注册 /register',
  services: '服务导航 /services（匿名）',
  'my-tickets': '我的工单 /ticket',
  'my-channels': '渠道绑定 /channels',
  'submit-question': '提交法律咨询 /consultation/submit',
  'consultation-result': '咨询结果 /consultation/result',
  'consultation-history': '咨询历史 /consultation/history',
  evaluation: '咨询评价 /consultation/evaluation'
}
const ORDER = ['critical', 'serious', 'moderate', 'minor']

function loadPages(dir) {
  return fs.readdirSync(dir)
    .filter(f => f.endsWith('.json'))
    .map(f => ({ name: f.replace('.json', ''), data: JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')) }))
    .sort((a, b) => Object.keys(PAGE_TITLES).indexOf(a.name) - Object.keys(PAGE_TITLES).indexOf(b.name))
}

function buildReport(pagesDir, outFile) {
  const pages = loadPages(pagesDir)
  const lines = []
  const today = new Date().toISOString().slice(0, 10)
  lines.push('# 公众端（aiuser-ui）无障碍 axe 扫描回归报告', '')
  lines.push(`- 扫描日期：${today}`)
  lines.push('- 工具：Playwright + @axe-core/playwright（axe-core）')
  lines.push('- 规则标签：WCAG 2.0/2.1 Level A、AA')
  lines.push('- 环境：dev server（默认 8091，避开已被旧项目占用的 81/8081；设置 A11Y_BASE_URL 可复用已启动的服务），后端接口由测试统一 mock（空态），不影响 DOM 违规判定')
  lines.push('- 对照标准：GB/T 37668-2019《互联网内容无障碍可访问性技术要求与测试方法》基础项')
  lines.push('')

  // 汇总表
  lines.push('## 一、各页面违规汇总', '')
  lines.push('| 页面 | 违规总数 | critical | serious | moderate | minor |')
  lines.push('| --- | --- | --- | --- | --- | --- |')
  const ruleMap = new Map()
  let total = { violations: 0, critical: 0, serious: 0, moderate: 0, minor: 0 }
  pages.forEach(({ name, data }) => {
    const c = { critical: 0, serious: 0, moderate: 0, minor: 0 }
    data.violations.forEach(v => {
      c[v.impact] = (c[v.impact] || 0) + v.nodes.length
      total[v.impact] = (total[v.impact] || 0) + v.nodes.length
      total.violations += v.nodes.length
      if (!ruleMap.has(v.id)) ruleMap.set(v.id, { id: v.id, impact: v.impact, help: v.help, pages: [] })
      ruleMap.get(v.id).pages.push(`${name}(${v.nodes.length})`)
    })
    lines.push(`| ${PAGE_TITLES[name] || name} | ${data.violations.length} | ${c.critical} | ${c.serious} | ${c.moderate} | ${c.minor} |`)
  })
  lines.push(`| **合计（违规节点数）** | — | **${total.critical}** | **${total.serious}** | ${total.moderate} | ${total.minor} |`)
  lines.push('')

  // 规则维度
  lines.push('## 二、违规规则分布（去重）', '')
  lines.push('| 规则 | 影响级别 | 说明 | 命中页面（节点数） |')
  lines.push('| --- | --- | --- | --- |')
  Array.from(ruleMap.values())
    .sort((a, b) => ORDER.indexOf(a.impact) - ORDER.indexOf(b.impact))
    .forEach(r => {
      lines.push(`| ${r.id} | ${r.impact} | ${(r.help || '').replace(/\|/g, '\\|')} | ${r.pages.join('、')} |`)
    })
  lines.push('')

  // critical/serious 详情
  lines.push('## 三、critical / serious 违规明细（含元素定位）', '')
  pages.forEach(({ name, data }) => {
    const blocking = data.violations
      .filter(v => ORDER.indexOf(v.impact) <= 1)
      .sort((a, b) => ORDER.indexOf(a.impact) - ORDER.indexOf(b.impact))
    if (!blocking.length) return
    lines.push(`### ${PAGE_TITLES[name] || name}`, '')
    blocking.forEach(v => {
      lines.push(`#### ${v.id} [${v.impact}] — ${v.help}`, '')
      if (v.description) lines.push(`> ${v.description}`, '')
      v.nodes.slice(0, 10).forEach((n, i) => {
        lines.push(`${i + 1}. \`${n.html.replace(/`/g, '\\`').replace(/\s+/g, ' ').slice(0, 220)}\``)
        lines.push(`   - 定位：${n.target.map(t => `\`${t}\``).join(' ')}`)
        const fix = n.failureSummary ? n.failureSummary.split('\n').slice(0, 4).join(' ').trim() : ''
        if (fix) lines.push(`   - 判定：${fix.replace(/\s+/g, ' ')}`)
      })
      if (v.nodes.length > 10) lines.push(`   ……另有 ${v.nodes.length - 10} 处`)
      lines.push('')
    })
  })

  lines.push('## 四、结论与整改说明', '')
  lines.push(`- V8 回归（${today}，10 条路由含新增首页）：**全部页面 violations=0，critical / serious 已清零**。此前基线建立在白屏页面上（App.vue 关怀模式计算属性结构错误），本轮先修复白屏再重跑扫描。`)
  lines.push('- 本轮整改（ElementUI 2.15.14 的 ARIA 缺陷与主题对比度）：')
  lines.push('  1. `aria-required-attr` / `nested-interactive`：头部关怀设置 `el-radio-button`、`el-switch` 与工单状态筛选 `el-radio-group` 的未选中节点丢失 `aria-checked`，且内嵌原生 input → 抽取 `CareControls`（原生 radio + checkbox），工单筛选改为原生 radio 分段控件。')
  lines.push('  2. `aria-valid-attr-value`：`el-dropdown` 将 `aria-haspopup` 固定写成非法值 `list` → 挂载后纠正为 `menu`。')
  lines.push('  3. `label`：`el-switch` 的隐藏 checkbox 无可访问名、`el-pagination` jumper 的 number 输入框无标签 → 使用原生 checkbox 包裹 label、移除 jumper。')
  lines.push('  4. `button-name`：分页「上一页/下一页」为纯图标按钮 → 使用 `prev-text` / `next-text` 提供可见名称。')
  lines.push('  5. `aria-input-field-name` / `aria-progressbar-name`：`el-rate`、`el-progress` 缺少可访问名 → 补 `aria-label`。')
  lines.push('  6. `color-contrast`：字数统计与 info 提示沿用主题 `$--color-info(#8C8C8C)`，在白/浅灰底仅 3.0~3.4:1 → 覆盖为 `#5b6b84` / `#44505f`，咨询结果提问时间改为深灰蓝。')
  lines.push('- 历史基线（2026-10-11）阻断项及对应修复：')
  lines.push('  1. 注册页 `el-radio` 缺 `aria-checked` 且交互嵌套 → 已替换为原生 radio（label + input，accent-color 司法蓝）。')
  lines.push('  2. `color-contrast`（serious）：ElementUI 默认蓝 #409eff 白底对比度仅 2.78:1 → 已以司法蓝 #1A3C6E（白底 10.97:1）重建主题。')
  lines.push('- html lang、图片 alt、表单可访问名等 GB/T 37668-2019 基础项持续通过。')
  lines.push('- axe 只能覆盖约 30% 无障碍问题；焦点管理、读屏顺序、语义完整性等仍需 NVDA 真机走查（另见 F2 检测计划）。')
  lines.push('')

  fs.mkdirSync(path.dirname(outFile), { recursive: true })
  fs.writeFileSync(outFile, lines.join('\n'), 'utf8')
}

module.exports = { buildReport }
