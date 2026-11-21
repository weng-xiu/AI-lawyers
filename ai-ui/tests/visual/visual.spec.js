'use strict'

/**
 * P5 视觉回归：逐页截图比对（基线按平台保存）
 *
 * 稳定性处理：
 * 1. 登录后复用同一 context，逐页 goto；
 * 2. 注入样式关闭动画/过渡，隐藏滚动条；
 * 3. 屏蔽 canvas（图表）与日期控件，避免动态内容造成误报；
 * 4. maxDiffPixelRatio 2%，用于容忍字体抗锯齿差异。
 */
const { test, expect } = require('@playwright/test')
const pages = require('./pages')

const group = {
  name: 'Visual',
  path: '/visual',
  hidden: false,
  redirect: 'noRedirect',
  component: 'Layout',
  alwaysShow: true,
  meta: { title: '视觉回归', icon: 'eye', noCache: true, link: null },
  children: pages.map((p, i) => ({
    name: 'Visual' + i,
    path: p.slug,
    hidden: false,
    component: p.component,
    meta: { title: p.name, icon: 'form', noCache: true, link: null }
  }))
}

test.describe('管理端 40 页视觉回归', () => {
  test.beforeEach(async ({ context }) => {
    // 业务页面可能建立呼叫 WebSocket，这里统一替换为空实现，避免连接噪音
    await context.addInitScript(() => {
      window.WebSocket = function () {
        this.readyState = 1
        this.send = function () {}
        this.close = function () {}
        this.addEventListener = function () {}
        this.removeEventListener = function () {}
      }
      window.WebSocket.OPEN = 1
      window.WebSocket.CONNECTING = 0
      window.WebSocket.CLOSING = 2
      window.WebSocket.CLOSED = 3
    })
  })

  for (const item of pages) {
    test(item.name, async ({ page }) => {
      await setupApiMocks(page)
      await login(page)
      await page.goto('/visual/' + item.slug)
      await page.waitForTimeout(1200)
      await page.addStyleTag({
        content: '*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}::-webkit-scrollbar{display:none}'
      })
      await expect(page).toHaveScreenshot(item.slug + '.png', {
        animations: 'disabled',
        caret: 'hide',
        maxDiffPixelRatio: 0.02,
        mask: [page.locator('canvas'), page.locator('.el-date-editor')]
      })
    })
  }
})

async function login(page) {
  await page.goto('/login')
  await page.fill('input[placeholder="账号"]', 'admin')
  await page.fill('input[placeholder="密码"]', 'admin123')
  await page.click('button:has-text("登 录")')
  await page.waitForURL('**/index')
}

/** 统一 mock：认证/路由固定返回，其余接口返回空数据集 */
async function setupApiMocks(page) {
  await page.route('**/dev-api/**', async route => {
    const req = route.request()
    const u = new URL(req.url())
    const api = u.pathname.replace(/^.*\/dev-api/, '')
    const json = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    const ok = extra => json(Object.assign({ code: 200, msg: '操作成功' }, extra))

    if (api === '/captchaImage') return ok({ captchaEnabled: false, uuid: 'visual', img: '' })
    if (api === '/login') return ok({ token: 'visual-token' })
    if (api === '/getInfo') {
      return ok({
        user: { userId: 1, userName: 'admin', nickName: '视觉回归', avatar: '', admin: true },
        roles: ['admin'],
        permissions: ['*:*:*']
      })
    }
    if (api === '/getRouters') return ok({ data: [group] })
    if (api === '/logout') return ok()
    return ok({ data: null, rows: [], total: 0 })
  })
}
