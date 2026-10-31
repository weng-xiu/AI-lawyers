'use strict'
// F2 无障碍 axe-core 全路由扫描（公众端 11 条路由）
// 依据 GB/T 37668-2019 基础项对齐 WCAG 2.1 A/AA；产出每页原始结果与汇总报告。
// 默认只采集并打印摘要（便于本地排查）；设置 A11Y_GATE=1 时对 critical/serious 违规阻断
// （`npm run test:a11y:gate`），可直接接入任意 CI。
const { test, expect } = require('@playwright/test')
const AxeBuilder = require('@axe-core/playwright').default
const fs = require('fs')
const path = require('path')
const { mockApi } = require('./mock-api')
const { buildReport } = require('./report')

const OUT_DIR = path.join(__dirname, '..', 'a11y-results')
const PAGES_DIR = path.join(OUT_DIR, 'pages')

// CI 门禁开关：仅阻断 critical/serious（moderate/minor 保留基线观察）
const GATE = process.env.A11Y_GATE === '1'

// [路由名, 路径, 是否需要登录, 扫描前额外操作]
const PAGES = [
  ['home', '/', false],
  ['login', '/login', false],
  ['register', '/register', false],
  ['services', '/services', false],
  ['my-tickets', '/ticket', true],
  ['my-channels', '/channels', true],
  ['submit-question', '/consultation/submit', true],
  ['consultation-result', '/consultation/result?id=1', true],
  ['consultation-history', '/consultation/history', true],
  ['evaluation', '/consultation/evaluation?id=1', true],
  ['messages', '/messages', true]
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
      console.log(`[${name}] violations=${results.violations.length} critical/serious=${blocking.length}`)
      blocking.forEach(v => console.log(`  - ${v.id} [${v.impact}] x${v.nodes.length}`))
      // 门禁模式下阻断：单页 critical/serious 必须为 0，否则 CI 失败并指明违规项
      if (GATE)
      {
        expect(blocking.length,
          `[${name}] 存在 ${blocking.length} 项 critical/serious 无障碍违规：`
          + blocking.map(v => `${v.id}(${v.nodes.length})`).join('、')).toBe(0)
      }
    } finally {
      await context.close()
    }
  })
}

test.afterAll(() => {
  buildReport(PAGES_DIR, path.join(OUT_DIR, 'axe-baseline-report.md'))
})
