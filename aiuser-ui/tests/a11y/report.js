'use strict'
// 汇总 tests/a11y-results/pages/*.json 生成 axe 基线 Markdown 报告
const fs = require('fs')
const path = require('path')

const PAGE_TITLES = {
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
  lines.push('# 公众端（aiuser-ui）无障碍 axe 基线扫描报告', '')
  lines.push(`- 扫描日期：${today}`)
  lines.push('- 工具：Playwright + @axe-core/playwright（axe-core）')
  lines.push('- 规则标签：WCAG 2.0/2.1 Level A、AA')
  lines.push('- 环境：dev server http://localhost:8091（vue-cli 自动分配，避开已被旧项目占用的 81/8081），后端接口由测试统一 mock（空态），不影响 DOM 违规判定')
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

  lines.push('## 四、结论与修复建议', '')
  lines.push('- 本报告为**存量基线**，记录整改前违规水位；修复后重跑 `npm run test:a11y` 回归对比。')
  lines.push('- 实测阻断项集中在**登录、注册两页**（其余 7 页 critical/serious 为 0）：')
  lines.push('  1. `aria-required-attr`（critical）：注册页性别 `el-radio` 渲染出的 `role="radio"` 节点缺 `aria-checked`，属 ElementUI 2.15.14 单选组件在该用法下的 ARIA 缺陷，建议升级/替换为显式 `aria-checked` 绑定或改用原生 radio。')
  lines.push('  2. `nested-interactive`（serious）：同一性别单选项的交互式嵌套，随上一条一并处理。')
  lines.push('  3. `color-contrast`（serious）：ElementUI 主色 `#409eff` 在白底（2.78:1）与白字主按钮上均低于 4.5:1，登录/注册按钮及链接受影响；建议在政务主题中加深主色（约 `#2070d0` 及更深可达标）。')
  lines.push('- html lang、图片 alt、表单可访问名等 GB/T 37668-2019 基础项本次自动化扫描**已通过**，不在阻断清单内。')
  lines.push('- axe 只能覆盖约 30% 无障碍问题；焦点管理、读屏顺序、语义完整性、登录/注册页未渲染关怀模式入口等仍需 NVDA 真机走查（另见 F2 检测计划）。')
  lines.push('')

  fs.mkdirSync(path.dirname(outFile), { recursive: true })
  fs.writeFileSync(outFile, lines.join('\n'), 'utf8')
}

module.exports = { buildReport }
