'use strict'

/**
 * 坐席 store 单测：锁定签入/签出、状态刷新、SIP 软电话事件与呼叫通道开关的行为，
 * 防止 5 秒轮询下重复建链、离线误清绑定等回归问题。
 */
import { getSipPhone } from '@/utils/webrtcSipPhone'
import callSocket from '@/utils/callSocket'
import { Message } from 'element-ui'
import {
  agentLogin,
  agentLogout,
  getCurrentAgent,
  updateAgentStatus,
  updateCallMode
} from '@/api/lawyers/callCenter'
import agentModule from '@/store/modules/agent'

jest.mock('@/utils/callSocket', () => ({
  __esModule: true,
  default: { connect: jest.fn(), close: jest.fn(), ws: null }
}))
jest.mock('@/utils/webrtcSipPhone', () => ({ getSipPhone: jest.fn() }))
jest.mock('@/api/lawyers/callCenter', () => ({
  agentLogin: jest.fn(),
  agentLogout: jest.fn(),
  getCurrentAgent: jest.fn(),
  updateAgentStatus: jest.fn(),
  updateCallMode: jest.fn()
}))
jest.mock('element-ui', () => ({ Message: jest.fn() }))
jest.mock('@/store', () => ({ __esModule: true, default: { state: { agent: {} } } }))

const { mutations, actions } = agentModule
const BINDING_KEY = 'AI_AGENT_BINDING'

function createState() {
  return {
    agentId: null,
    userId: null,
    agent: null,
    sipStatus: 'offline',
    sipError: null,
    sipCall: null,
    dialerVisible: false
  }
}

let phone
let commit
let dispatch

beforeEach(() => {
  jest.clearAllMocks()
  localStorage.clear()
  phone = {
    on: jest.fn(),
    register: jest.fn(() => Promise.resolve()),
    unregister: jest.fn(),
    terminate: jest.fn(),
    answer: jest.fn(() => Promise.resolve()),
    lastError: null,
    extension: '8001'
  }
  getSipPhone.mockReturnValue(phone)
  commit = jest.fn()
  dispatch = jest.fn(() => Promise.resolve())
})

describe('agent mutations', () => {
  test('绑定/坐席/通话/拨号框状态写入', () => {
    const state = createState()

    mutations.SET_BINDING(state, { agentId: 8, userId: 9 })
    expect(state.agentId).toBe(8)
    expect(state.userId).toBe(9)

    mutations.SET_BINDING(state, null)
    expect(state.agentId).toBeNull()
    expect(state.userId).toBeNull()

    mutations.SET_AGENT(state, { agentId: 8 })
    expect(state.agent).toEqual({ agentId: 8 })

    mutations.SET_SIP_STATUS(state, 'registered')
    mutations.SET_SIP_ERROR(state, '超时')
    mutations.SET_SIP_CALL(state, { direction: 'incoming' })
    mutations.SET_DIALER_VISIBLE(state, 1)
    expect(state.sipStatus).toBe('registered')
    expect(state.sipError).toBe('超时')
    expect(state.sipCall).toEqual({ direction: 'incoming' })
    expect(state.dialerVisible).toBe(true)

    mutations.SET_DIALER_VISIBLE(state, 0)
    expect(state.dialerVisible).toBe(false)
  })

  test('清空仅重置坐席与通话，不动 SIP 错误之外的其他字段', () => {
    const state = createState()
    mutations.SET_BINDING(state, { agentId: 8, userId: 9 })
    mutations.SET_AGENT(state, { status: '1' })
    mutations.SET_SIP_CALL(state, { id: 1 })
    mutations.SET_SIP_STATUS(state, 'connected')

    mutations.CLEAR(state)

    expect(state).toMatchObject({
      agent: null,
      agentId: null,
      userId: null,
      sipStatus: 'offline',
      sipCall: null
    })
  })
})

describe('agent actions 绑定与签入签出', () => {
  test('remember 仅记录绑定并落本地存储', () => {
    actions.remember({ commit }, { agentId: 8, userId: 9 })

    expect(commit).toHaveBeenCalledWith('SET_BINDING', { agentId: 8, userId: 9 })
    expect(JSON.parse(localStorage.getItem(BINDING_KEY))).toEqual({ agentId: 8, userId: 9 })
  })

  test('restore 无本地绑定时直接完成', async () => {
    const result = await actions.restore({ commit, dispatch })

    expect(result).toBeUndefined()
    expect(dispatch).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
  })

  test('restore 后端确认离线时清除绑定与状态', async () => {
    localStorage.setItem(BINDING_KEY, JSON.stringify({ agentId: 8, userId: 9 }))
    dispatch.mockResolvedValue({ status: '0' })

    await actions.restore({ commit, dispatch })

    expect(commit).toHaveBeenCalledWith('SET_BINDING', { agentId: 8, userId: 9 })
    expect(dispatch).toHaveBeenCalledWith('refresh')
    expect(localStorage.getItem(BINDING_KEY)).toBeNull()
    expect(commit).toHaveBeenCalledWith('CLEAR')
  })

  test('restore 刷新失败时保留本地绑定，避免网络抖动误判未签入', async () => {
    localStorage.setItem(BINDING_KEY, JSON.stringify({ agentId: 8, userId: 9 }))
    dispatch.mockRejectedValue(new Error('网络异常'))

    await actions.restore({ commit, dispatch })

    expect(localStorage.getItem(BINDING_KEY)).not.toBeNull()
    expect(commit).not.toHaveBeenCalledWith('CLEAR')
  })

  test('login 成功后落绑定并刷新坐席', async () => {
    agentLogin.mockResolvedValue({ code: 200 })
    dispatch.mockResolvedValue({ status: '1' })

    await actions.login({ commit, dispatch }, { agentId: 8, userId: 9, ip: '' })

    expect(agentLogin).toHaveBeenCalledWith({ agentId: 8, userId: 9, ip: '' })
    expect(commit).toHaveBeenCalledWith('SET_BINDING', { agentId: 8, userId: 9 })
    expect(JSON.parse(localStorage.getItem(BINDING_KEY))).toEqual({ agentId: 8, userId: 9 })
    expect(dispatch).toHaveBeenCalledWith('refresh')
  })

  test('logout 成功后关闭呼叫通道与 SIP 并清空状态', async () => {
    const state = { ...createState(), agentId: 8, userId: 9 }
    agentLogout.mockResolvedValue({ code: 200 })

    await actions.logout({ commit, state })

    expect(agentLogout).toHaveBeenCalledWith({ agentId: 8, userId: 9 })
    expect(callSocket.close).toHaveBeenCalled()
    expect(phone.terminate).toHaveBeenCalled()
    expect(phone.unregister).toHaveBeenCalled()
    expect(localStorage.getItem(BINDING_KEY)).toBeNull()
    expect(commit).toHaveBeenCalledWith('CLEAR')
  })

  test('clearBinding 仅清绑定与状态', () => {
    localStorage.setItem(BINDING_KEY, JSON.stringify({ agentId: 8 }))

    actions.clearBinding({ commit })

    expect(localStorage.getItem(BINDING_KEY)).toBeNull()
    expect(commit).toHaveBeenCalledWith('CLEAR')
  })
})

describe('agent actions 状态刷新', () => {
  test('未绑定工号时 refresh 直接返回 null', async () => {
    const state = createState()

    await expect(actions.refresh({ commit, state })).resolves.toBeNull()
    expect(getCurrentAgent).not.toHaveBeenCalled()
  })

  test('在线坐席刷新后建立 WS 并注册 SIP 分机', async () => {
    const state = { ...createState(), agentId: 8 }
    getCurrentAgent.mockResolvedValue({
      data: { userId: 9, status: '1', sipExtension: '8001' }
    })

    const agent = await actions.refresh({ commit, state })

    expect(agent).toEqual({ userId: 9, status: '1', sipExtension: '8001' })
    expect(commit).toHaveBeenCalledWith('SET_AGENT', agent)
    expect(callSocket.connect).toHaveBeenCalledWith(9)
    expect(phone.register).toHaveBeenCalledWith({
      extension: '8001',
      password: '1234',
      domain: '198.18.0.1',
      wsUrl: 'ws://localhost:5066'
    })
  })

  test('离线坐席刷新后关闭呼叫通道且不注册分机', async () => {
    const state = { ...createState(), agentId: 8, agent: { userId: 9, status: '1' } }
    getCurrentAgent.mockResolvedValue({ data: { userId: 9, status: '0', sipExtension: '8001' } })

    await actions.refresh({ commit, state })

    expect(callSocket.close).toHaveBeenCalled()
    expect(phone.register).not.toHaveBeenCalled()
  })

  test('刷新失败时保留上一次坐席状态', async () => {
    const previous = { userId: 9, status: '1' }
    const state = { ...createState(), agentId: 8, agent: previous }
    getCurrentAgent.mockRejectedValue(new Error('请求超时'))

    await expect(actions.refresh({ commit, state })).resolves.toBe(previous)
    expect(commit).not.toHaveBeenCalledWith('SET_AGENT', expect.anything())
  })

  test('已绑定工号时 setStatus 调用接口并刷新', async () => {
    const state = { ...createState(), agentId: 8 }
    updateAgentStatus.mockResolvedValue({ code: 200 })

    await actions.setStatus({ dispatch, state }, '2')

    expect(updateAgentStatus).toHaveBeenCalledWith({ agentId: 8, status: '2' })
    expect(dispatch).toHaveBeenCalledWith('refresh')
  })

  test('未绑定工号时 setStatus/setCallMode 不调用接口', async () => {
    const state = createState()

    await expect(actions.setStatus({ dispatch, state }, '1')).resolves.toBeUndefined()
    await expect(actions.setCallMode({ dispatch, state }, 'auto')).resolves.toBeUndefined()
    expect(updateAgentStatus).not.toHaveBeenCalled()
    expect(updateCallMode).not.toHaveBeenCalled()
  })

  test('已绑定工号时 setCallMode 调用接口并刷新', async () => {
    const state = { ...createState(), agentId: 8 }
    updateCallMode.mockResolvedValue({ code: 200 })

    await actions.setCallMode({ dispatch, state }, 'auto')

    expect(updateCallMode).toHaveBeenCalledWith({ agentId: 8, callMode: 'auto' })
    expect(dispatch).toHaveBeenCalledWith('refresh')
  })
})

describe('agent actions SIP 软电话', () => {
  test('bindSipPhone 注册事件并在注册成功时提示一次', () => {
    const state = createState()
    actions.bindSipPhone({ commit, state })

    const names = phone.on.mock.calls.map(call => call[0])
    expect(names).toEqual([
      'statusChange',
      'incomingCall',
      'sessionAccepted',
      'sessionEnded',
      'sessionFailed'
    ])

    const onStatusChange = phone.on.mock.calls.find(call => call[0] === 'statusChange')[1]
    onStatusChange('registered')
    onStatusChange('registered')
    expect(Message).toHaveBeenCalledTimes(1)
    expect(Message).toHaveBeenCalledWith(expect.objectContaining({ type: 'success' }))

    onStatusChange('failed')
    expect(Message).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'error' }))
  })

  test('来电事件写入 sipCall，接听与挂断更新状态', () => {
    const state = createState()
    actions.bindSipPhone({ commit, state })
    const onIncoming = phone.on.mock.calls.find(call => call[0] === 'incomingCall')[1]
    const onAccepted = phone.on.mock.calls.find(call => call[0] === 'sessionAccepted')[1]

    onIncoming({ caller: '13800000000' })
    expect(commit).toHaveBeenCalledWith('SET_SIP_CALL', {
      caller: '13800000000',
      direction: 'incoming',
      state: 'ringing'
    })

    // 无当前通话时接听事件不写入
    onAccepted({ isIncoming: true })
    expect(commit).not.toHaveBeenCalledWith('SET_SIP_CALL', expect.objectContaining({ state: 'answered' }))
  })

  test('挂断/失败事件清空当前通话', () => {
    const state = createState()
    actions.bindSipPhone({ commit, state })

    phone.on.mock.calls.find(call => call[0] === 'sessionEnded')[1]()
    phone.on.mock.calls.find(call => call[0] === 'sessionFailed')[1]()

    expect(commit).toHaveBeenCalledWith('SET_SIP_CALL', null)
  })

  test('sipAnswer/sipHangup 委托软电话实例', async () => {
    await actions.sipAnswer()
    actions.sipHangup()

    expect(phone.answer).toHaveBeenCalled()
    expect(phone.terminate).toHaveBeenCalled()
  })

  test('sipReconnect 未绑定分机时拒绝，绑定后按配置重新注册', async () => {
    await expect(actions.sipReconnect({ state: createState() }))
      .rejects.toThrow('坐席未绑定分机')

    const state = { ...createState(), agent: { sipExtension: '8001' } }
    await actions.sipReconnect({ state })

    expect(phone.register).toHaveBeenCalledWith({
      extension: '8001',
      password: '1234',
      domain: '198.18.0.1',
      wsUrl: 'ws://localhost:5066'
    })
  })

  test('呼叫通道与拨号框开关', () => {
    actions.ensureCallSocket({ state: createState() }, { userId: 7, status: '1' })
    expect(callSocket.connect).toHaveBeenCalledWith(7)

    actions.closeCallSocket()
    expect(callSocket.close).toHaveBeenCalled()

    actions.openDialer({ commit })
    actions.closeDialer({ commit })
    expect(commit).toHaveBeenCalledWith('SET_DIALER_VISIBLE', true)
    expect(commit).toHaveBeenCalledWith('SET_DIALER_VISIBLE', false)
  })
})
