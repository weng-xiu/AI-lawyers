'use strict'

/**
 * P8 关键链路 E2E：登录 → 坐席签入 → 模拟来电（WebSocket 推送）→ 通话 → 挂机 → 工单登记
 *
 * 设计要点：
 * 1. 后端接口全部经 page.route mock（统一分发 dev-api 前缀请求），无需启动 Java 后端；
 * 2. 呼叫事件走 FakeWebSocket：init script 拦截 window.WebSocket，/ws/call、/ws/voice
 *    返回假实例（立即 open），测试用 window.__pushCallEvent 模拟服务端推送；
 * 3. 坐席 callStatus 经 agentState 与 mock 联动，保证弹屏 5s 轮询状态一致；
 * 4. 断言覆盖：登录跳转、签入状态、来电弹屏横幅与提醒、通话计时、挂机确认、建单回执与列表。
 */
const { test, expect } = require('@playwright/test')

const CALLER = '13800001111'
const TICKET_NO = 'GT202610190001'
const AGENT_ID = 1001

/** getRouters 动态菜单：仅注册关键链路涉及的两个页面 */
const MENUS = [
  {
    name: 'Inbound',
    path: '/inbound',
    hidden: false,
    redirect: 'noRedirect',
    component: 'Layout',
    alwaysShow: true,
    meta: { title: '呼叫中心', icon: 'phone', noCache: false, link: null },
    children: [
      { name: 'CallPopup', path: 'callPopup', hidden: false, component: 'lawyers/callCenter/callPopup', meta: { title: '来电弹屏', icon: 'phone', noCache: false, link: null } }
    ]
  },
  {
    name: 'Business',
    path: '/business',
    hidden: false,
    redirect: 'noRedirect',
    component: 'Layout',
    alwaysShow: true,
    meta: { title: '业务办理', icon: 'form', noCache: false, link: null },
    children: [
      { name: 'WorkOrder', path: 'workOrder', hidden: false, component: 'lawyers/callCenter/workOrder', meta: { title: '工单登记', icon: 'form', noCache: false, link: null } }
    ]
  }
]

const CALLER_PROFILE = {
  callerNumber: CALLER,
  callerName: '张三',
  address: '广东广州',
  tags: '劳动争议,合同纠纷',
  intentPrediction: '劳动仲裁咨询',
  intentConfidence: 86,
  riskLevel: '0',
  monthCallCount: 2,
  callCount: 5
}

test.describe('管理端关键链路', () => {
  let page

  test.beforeEach(async ({ context }) => {
    // FakeWebSocket：/ws/call、/ws/voice 立即 open，支持测试侧模拟服务端推送
    await context.addInitScript(() => {
      const RealWS = window.WebSocket
      function FakeWS(url) {
        this.url = url
        this.readyState = 1 // OPEN
        this._handlers = {}
        const self = this
        setTimeout(() => { if (self.onopen) self.onopen({ type: 'open' }) }, 0)
      }
      FakeWS.prototype.send = function () {}
      FakeWS.prototype.close = function () {
        this.readyState = 3
        if (this.onclose) this.onclose({ code: 1000, reason: '' })
      }
      FakeWS.prototype.addEventListener = function (t, fn) { (this._handlers[t] = this._handlers[t] || []).push(fn) }
      FakeWS.prototype.removeEventListener = function (t, fn) {
        const a = this._handlers[t]
        if (a) { const i = a.indexOf(fn); if (i > -1) a.splice(i, 1) }
      }
      FakeWS.prototype.fire = function (t, evt) { (this._handlers[t] || []).forEach(fn => fn(evt)) }
      window.WebSocket = function (url) {
        const inst = new FakeWS(url)
        if (/\/ws\/call\//.test(url)) window.__fakeCallWS = inst
        else if (/\/ws\/voice\//.test(url)) window.__fakeVoiceWS = inst
        return inst
      }
      window.WebSocket.OPEN = 1
      window.WebSocket.CONNECTING = 0
      window.WebSocket.CLOSING = 2
      window.WebSocket.CLOSED = 3
      window.__pushCallEvent = function (payload) {
        const ws = window.__fakeCallWS
        if (!ws) return false
        const data = JSON.stringify(payload)
        if (ws.onmessage) ws.onmessage({ data })
        ws.fire('message', { data })
        return true
      }
      return true
    })
    page = await context.newPage()
    // 屏蔽无关噪音，保留错误便于排查
    page.on('pageerror', err => console.warn('[pageerror]', err.message))
    await setupApiMocks(page)
  })

  test.afterEach(async () => {
    if (page) await page.close()
  })

  test('登录→签入→来电弹屏→挂机→建单', async () => {
    // ---------- 1. 登录 ----------
    await page.goto('/login')
    await expect(page).toHaveURL(/login/)
    await page.fill('input[placeholder="账号"]', 'admin')
    await page.fill('input[placeholder="密码"]', 'admin123')
    await page.click('button:has-text("登 录")')
    await page.waitForURL('**/index')
    // '/' 首页为话务台工作台（callWorkbench），横幅展示当前账号昵称
    await expect(page.locator('.wb-banner-text h2')).toContainText('admin')
    // 顶部坐席状态条可见且未签入
    await expect(page.locator('.agent-status-bar')).toBeVisible()
    await expect(page.locator('.agent-status-bar')).toContainText('未签入')

    // ---------- 2. 坐席签入 ----------
    await page.click('.agent-status-bar button:has-text("签入")')
    await expect(page.locator('.el-dialog:has-text("坐席签入")')).toBeVisible()
    const dialog = page.locator('.el-dialog:has-text("坐席签入")')
    const agentInput = dialog.locator('input[placeholder="请输入坐席工号（数字）"]')
    // mock 返回账号已绑定坐席：工号自动预填并禁用；未绑定时手动输入
    if (await agentInput.isEnabled()) {
      await agentInput.fill(String(AGENT_ID))
    }
    await expect(agentInput).toHaveValue(String(AGENT_ID))
    await dialog.locator('button:has-text("签 入")').click()
    await expect(page.locator('.el-message--success').filter({ hasText: '签入成功' })).toBeVisible()
    await expect(page.locator('.agent-status-bar')).toContainText(`工号 ${AGENT_ID}`)
    await expect(page.locator('.agent-status-bar')).toContainText('1001号坐席')
    await expect(page.locator('.agent-status-bar')).toContainText('已签入')

    // ---------- 3. 模拟来电（WebSocket 推送 INBOUND_RING）----------
    await pushCall({ type: 'INBOUND_RING', data: { agentId: AGENT_ID, caller: CALLER, recordId: 9001 } })
    await page.waitForURL('**/inbound/callPopup**')
    await expect(page.locator('.cp-banner-main')).toHaveText('来电接入')
    await expect(page.locator('.cp-banner-number')).toHaveText(CALLER)
    await expect(page.locator('.cp-banner-sub')).toContainText('等待接听')
    // 来电提醒通知
    await expect(page.locator('.el-notification')).toContainText('来电提醒')
    await expect(page.locator('.el-notification')).toContainText(CALLER)
    // 来电人档案渲染（姓名 + 标签）
    await expect(page.locator('.cp-caller-name')).toContainText('张三')
    await expect(page.locator('.cp-caller-tags')).toContainText('劳动争议')

    // ---------- 4. 接通（ANSWERED）与通话计时 ----------
    setAgentCallStatus('1')
    await pushCall({ type: 'ANSWERED', data: { agentId: AGENT_ID, caller: CALLER, recordId: 9001 } })
    await expect(page.locator('.cp-banner-sub')).toContainText('通话中', { timeout: 10000 })
    // 挂机按钮从禁用变为可用
    await expect(page.locator('.cp-action-bar button:has-text("挂机")')).toBeEnabled()

    // ---------- 5. 挂机（含二次确认）----------
    await page.click('.cp-action-bar button:has-text("挂机")')
    await expect(page.locator('.el-message-box:has-text("确定挂断当前通话吗？")')).toBeVisible()
    // $confirm 默认按钮文案为「确定」（无空格）
    await page.click('.el-message-box button:has-text("确定")')
    await expect(page.locator('.el-message--success').filter({ hasText: '通话已挂断' })).toBeVisible()
    await expect(page.locator('.cp-banner-sub')).toContainText('通话已结束')
    // 同步坐席空闲状态：整页刷新后 restore/轮询不应再把用户拉回来电弹屏
    setAgentCallStatus('0')

    // ---------- 6. 工单登记（建单）----------
    await page.goto('/business/workOrder')
    await expect(page.locator('button:has-text("新增工单")')).toBeVisible()
    await page.click('button:has-text("新增工单")')
    const orderDialog = page.locator('.el-dialog:has-text("新增工单")')
    await expect(orderDialog).toBeVisible()
    await expect(orderDialog.locator('input[placeholder="系统自动生成"]')).toHaveValue(TICKET_NO)
    await orderDialog.locator('input[placeholder="请输入工单标题"]').fill('劳动仲裁流程咨询')
    await orderDialog.locator('input[placeholder="请输入来电号码"]').fill(CALLER)
    await orderDialog.locator('input[placeholder="请输入来电人"]').fill('张三')
    await orderDialog.locator('textarea[placeholder="请输入工单内容"]').fill('咨询劳动仲裁申请流程与材料清单')
    await orderDialog.locator('button:has-text("确 定")').click()
    await expect(page.locator('.el-message--success').filter({ hasText: '新增成功' })).toBeVisible()
    // 列表回显新工单
    await expect(page.locator('.el-table__body-wrapper')).toContainText(TICKET_NO)
    await expect(page.locator('.el-table__body-wrapper')).toContainText('劳动仲裁流程咨询')
  })

  /** 在页面上下文向 FakeWebSocket 推送呼叫事件 */
  async function pushCall(payload) {
    const ok = await page.evaluate(p => window.__pushCallEvent(p), payload)
    if (!ok) throw new Error('FakeWebSocket 未就绪，无法推送事件: ' + JSON.stringify(payload))
  }

  /** 动态调整坐席 callStatus，保证弹屏 5s 轮询与事件推送状态一致 */
  function setAgentCallStatus(status) {
    state.agent.callStatus = status
  }
})

/** 页面级共享状态：坐席实时 callStatus 由测试用例驱动 */
const state = {
  agent: { callStatus: '0' },
  tickets: [{ ticketId: 1, ticketNo: 'GT202610180000', title: '历史工单', status: '2', priority: '2' }]
}

/** 后端接口 mock：单一路由按路径分发（真实后端无感，浏览器内全部拦截） */
async function setupApiMocks(page) {
  await page.route('**/dev-api/**', async route => {
    const req = route.request()
    const method = req.method()
    const u = new URL(req.url())
    const api = u.pathname.replace(/^.*\/dev-api/, '')
    const json = body => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    const ok = extra => json(Object.assign({ code: 200, msg: '操作成功' }, extra))

    // ---- 认证与路由 ----
    if (api === '/captchaImage') return ok({ captchaEnabled: false, uuid: 'e2e', img: '' })
    if (api === '/login') return ok({ token: 'e2e-token' })
    if (api === '/getInfo') {
      return ok({
        user: { userId: 1, userName: 'admin', nickName: 'E2E管理员', avatar: '', admin: true },
        roles: ['admin'],
        permissions: ['*:*:*']
      })
    }
    if (api === '/getRouters') return ok({ data: MENUS })

    // ---- 坐席签入/状态（注意顺序：current 在 agent 通配之前）----
    if (api === '/lawyers/call/agent/my') {
      return ok({ data: { agentId: AGENT_ID, agentName: '1001号坐席', sipExtension: '1001' } })
    }
    if (api === '/lawyers/call/agent/login' && method === 'POST') return ok()
    if (api.startsWith('/lawyers/call/agent/current/')) {
      return ok({
        data: {
          agentId: AGENT_ID,
          userId: 1,
          agentName: '1001号坐席',
          status: '1',
          callStatus: state.agent.callStatus,
          loginTime: '2026-10-19 09:00:00',
          sipExtension: '1001'
        }
      })
    }
    if (api.startsWith('/lawyers/call/agent/')) return ok()

    // ---- 来电弹屏 ----
    if (api === '/lawyers/call/popup/profile/' + CALLER) return ok({ data: CALLER_PROFILE })
    if (api.startsWith('/lawyers/call/popup/')) return ok({ data: [] })

    // ---- 工单 ----
    if (api === '/lawyers/call/ticket/list') return ok({ rows: state.tickets, total: state.tickets.length })
    if (api === '/lawyers/call/ticket/generateNo') return ok({ data: TICKET_NO })
    if (api === '/lawyers/call/ticket' && method === 'POST') {
      // 落入 mock 数据集：提交后 getList 轮询可回显新工单
      try {
        const body = JSON.parse(req.postData() || '{}')
        state.tickets.unshift(Object.assign({ ticketId: state.tickets.length + 1, status: '1', priority: '2' }, body))
      } catch (e) { /* 忽略非法报文 */ }
      return ok({ msg: '新增成功' })
    }
    if (api.startsWith('/lawyers/ticket-flow/actions/')) return ok({ data: [] })
    if (api === '/system/user/list') {
      return ok({ rows: [{ userId: 1, userName: 'admin', nickName: '管理员', dept: { deptName: '热线组' } }], total: 1 })
    }

    // ---- 工作台/首页/杂项 ----
    if (api === '/lawyers/call/record/workbench') {
      return ok({ data: { todayTotal: 12, todayAnswered: 9, queueing: 1, avgAnswerSec: 18 } })
    }
    if (api === '/system/notice/list') return ok({ rows: [], total: 0 })
    if (api === '/logout') return ok()
    return ok({ data: null, rows: [], total: 0 })
  })
}
