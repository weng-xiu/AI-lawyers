'use strict'
// F2 无障碍 axe-core 全路由扫描（公众端 9 条路由）
// 依据 GB/T 37668-2019 基础项对齐 WCAG 2.1 A/AA；产出每页原始结果与汇总报告。
const { test } = require('@playwright/test')
const AxeBuilder = require('@axe-core/playwright').default
const fs = require('fs')
const path = require('path')
const { mockApi } = require('./mock-api')
const { buildReport } = require('./report')

const OUT_DIR = path.join(__dirname, '..', 'a11y-results')
const PAGES_DIR = path.join(OUT_DIR, 'pages')

// [路由名, 路径, 是否需要登录, 扫描前额外操作]
const PAGES = [
  ['login', '/login', false],
  ['register', '/register', false],
  ['services', '/services', false],
  ['my-tickets', '/ticket', true],
  ['my-channels', '/channels', true],
  ['submit-question', '/consultation/submit', true],
  ['consultation-result', '/consultation/result?id=1', true],
  ['consultation-history', '/consultation/history', true],
  ['evaluation', '/consultation/evaluation?id=1', true]
]

fs.mkdirSync(PAGES_DIR, { recursive: true })

async function scanPage(page, name, url, needAuth) {
  await mockApi(page)
  if (needAuth) {
    // 先落到同源页面再写入登录态，随后导航触发守卫放行
    await page.goto('/login')
    await page.evaluate(() => localStorage.setItem('token', 'axe-scan-mock-token'))
  }
  await page.goto(url, { waitUntil: 'networkidle' })
  // 等待 Vue 渲染与 v-loading 解除
  await page.waitForTimeout(800)

  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag21a', 'wcag2aa', 'wcag21aa'])
    .analyze()

  fs.writeFileSync(path.join(PAGES_DIR, `${name}.json`), JSON.stringify(results, null, 2))
  return results
}

for (const [name, url, needAuth] of PAGES) {
  test(`axe 扫描：${name} (${url})`, async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 800 } })
    const page = await context.newPage()
    try {
      const results = await scanPage(page, name, url, needAuth)
      const blocking = results.violations.filter(v => ['critical', 'serious'].includes(v.impact))
      // 基线阶段不阻断流水线，仅打印摘要；修复期可改为 expect(blocking.length).toBe(0)
      console.log(`[${name}] violations=${results.violations.length} critical/serious=${blocking.length}`)
      blocking.forEach(v => console.log(`  - ${v.id} [${v.impact}] x${v.nodes.length}`))
    } finally {
      await context.close()
    }
  })
}

test.afterAll(() => {
  buildReport(PAGES_DIR, path.join(OUT_DIR, 'axe-baseline-report.md'))
})
