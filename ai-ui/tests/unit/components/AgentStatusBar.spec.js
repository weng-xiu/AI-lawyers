'use strict'

/**
 * AgentStatusBar 坐席状态栏单测：锁定权限展示、状态信息、签入/签出与来电推送。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import AgentStatusBar from '@/components/CallCenter/AgentStatusBar.vue'
import callSocket from '@/utils/callSocket'
import { getMyAgent } from '@/api/lawyers/callCenter'

jest.mock('@/utils/callSocket', () => ({
  __esModule: true,
  default: { on: jest.fn(), off: jest.fn() }
}))
jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))
jest.mock('@/api/lawyers/callCenter', () => ({ getMyAgent: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})

const defaultAgent = {
  agentId: 1001,
  agentName: '客服小王',
  sipExtension: '8001',
  status: '1',
  callStatus: '0',
  callMode: '0',
  loginTime: '2026-11-21 10:00:00'
}

const wrappers = []
const flush = () => Promise.resolve().then(() => Promise.resolve())

async function mountBar(options = {}) {
  const {
    agent = defaultAgent,
    permissions = ['lawyers:call:agent:status'],
    sipStatus = 'registered',
    routePath = '/index'
  } = options
  const dispatch = jest.fn(type => Promise.resolve(agent))
  const push = jest.fn(() => Promise.resolve())
  const replace = jest.fn(() => Promise.resolve())
  const message = { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
  const notify = jest.fn()
  const dangerConfirm = jest.fn(() => Promise.resolve())

  const wrapper = mount(AgentStatusBar, {
    localVue,
    mocks: {
      $store: {
        state: { agent: { agent, agentId: agent ? agent.agentId : null } },
        getters: {
          nickName: 'zhangsan',
          userId: 7,
          permissions,
          sipStatus,
          sipError: ''
        },
        dispatch
      },
      $route: { path: routePath },
      $router: { push, replace },
      $message: message,
      $notify: notify,
      $modal: { dangerConfirm }
    }
  })
  wrappers.push(wrapper)
  await flush()
  return { wrapper, dispatch, push, replace, message, notify, dangerConfirm }
}

describe('AgentStatusBar', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.useFakeTimers()
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop().destroy()
    jest.useRealTimers()
  })

  test('无坐席权限时隐藏状态栏但仍注册实时事件', async () => {
    const { wrapper } = await mountBar({ permissions: [] })

    expect(wrapper.find('.agent-status-bar').exists()).toBe(false)
    expect(callSocket.on).toHaveBeenCalledWith('INBOUND_RING', expect.any(Function))
    expect(callSocket.on).toHaveBeenCalledWith('HANGUP', expect.any(Function))
  })

  test('已签入展示工号、分机状态与签入时长', async () => {
    const { wrapper } = await mountBar()
    wrapper.vm.now = Date.parse('2026-11-21T10:01:05')

    expect(wrapper.text()).toContain('工号 1001')
    expect(wrapper.text()).toContain('客服小王')
    expect(wrapper.text()).toContain('分机8001 已就绪')
    expect(wrapper.vm.signinDuration).toBe('00:01:05')
  })

  test('未签入展示签入入口并回填绑定工号', async () => {
    getMyAgent.mockResolvedValue({ data: { agentId: 1001, agentName: '小王' } })
    const { wrapper } = await mountBar({ agent: null })

    expect(wrapper.text()).toContain('未签入')
    wrapper.vm.openLogin()
    await flush()

    expect(wrapper.vm.loginOpen).toBe(true)
    expect(wrapper.vm.loginForm).toMatchObject({ agentId: '1001', agentName: '小王', bound: true })
  })

  test('签入校验数字工号并提交登录信息', async () => {
    const { wrapper, dispatch, message } = await mountBar({ agent: null })
    wrapper.vm.loginForm.agentId = 'abc'
    wrapper.vm.confirmLogin()
    expect(message.warning).toHaveBeenCalledWith('请输入数字工号')

    wrapper.vm.loginForm.agentId = '1001'
    wrapper.vm.confirmLogin()
    await flush()

    expect(dispatch).toHaveBeenCalledWith('agent/login', { agentId: 1001, userId: 7, ip: '' })
    expect(message.success).toHaveBeenCalledWith('签入成功')
    expect(wrapper.vm.loginOpen).toBe(false)
  })

  test('切换状态成功后提示并刷新通话状态', async () => {
    const { wrapper, dispatch, message } = await mountBar()

    wrapper.vm.handleStatusChange('2')
    await flush()

    expect(dispatch).toHaveBeenCalledWith('agent/setStatus', '2')
    expect(message.success).toHaveBeenCalledWith('状态已更新')
  })

  test('通话状态变化时自动跳转来电弹屏', async () => {
    const { wrapper, push } = await mountBar()
    const agent = { ...defaultAgent, callStatus: '1', currentCallPhone: '13800000000', currentCallId: 66 }

    wrapper.vm.syncCallState(agent)

    expect(push).toHaveBeenCalledWith({
      path: '/inbound/callPopup',
      query: { callerNumber: '13800000000', direction: 'in', callStatus: '1', recordId: 66 }
    })
    expect(wrapper.vm.lastCallStatus).toBe('1')
  })

  test('收到来电推送时跳转弹屏并弹出提醒', async () => {
    const { wrapper, push, notify } = await mountBar()

    wrapper.vm.onWsInboundRing({ caller: '13900000000', recordId: 8 })

    expect(push).toHaveBeenCalledWith({
      path: '/inbound/callPopup',
      query: { callerNumber: '13900000000', direction: 'in', callStatus: '1', recordId: 8 }
    })
    expect(notify).toHaveBeenCalledWith(expect.objectContaining({
      title: '来电提醒',
      message: '来电号码：13900000000',
      type: 'warning'
    }))
  })

  test('通话中点击外呼给出提示且不打开拨号框', async () => {
    const agent = { ...defaultAgent, callStatus: '1' }
    const { wrapper, dispatch, message } = await mountBar({ agent })

    wrapper.vm.openDial()

    expect(message.warning).toHaveBeenCalledWith('当前通话中，请先挂机')
    expect(dispatch).not.toHaveBeenCalledWith('agent/openDialer')
  })

  test('签出确认后调用登出并提示', async () => {
    const { wrapper, dispatch, message, dangerConfirm } = await mountBar()

    wrapper.vm.handleLogout()
    await flush()

    expect(dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'caution' }))
    expect(dispatch).toHaveBeenCalledWith('agent/logout')
    expect(message.success).toHaveBeenCalledWith('已签出')
  })

  test('SIP 关闭时固定展示未启用', async () => {
    process.env.VUE_APP_SIP_ENABLED = 'false'
    const { wrapper } = await mountBar({ sipStatus: 'failed' })

    expect(wrapper.vm.sipStatusText).toBe('未启用')
    expect(wrapper.vm.sipTagType).toBe('info')

    delete process.env.VUE_APP_SIP_ENABLED
  })
})
