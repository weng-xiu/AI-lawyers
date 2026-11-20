'use strict'

/**
 * 呼叫事件 WebSocket 客户端单测：锁定握手 URL、事件分发、普通断线退避重连、
 * 1008 鉴权失败停止重连与实例排空快速重连。
 */
import callSocket from '@/utils/callSocket'
import { getToken } from '@/utils/auth'
import { MessageBox } from 'element-ui'

jest.mock('@/utils/auth', () => ({ getToken: jest.fn() }))
jest.mock('element-ui', () => ({ MessageBox: { confirm: jest.fn() } }))
jest.mock('@/store', () => ({
  __esModule: true,
  default: { dispatch: jest.fn(() => Promise.resolve()) }
}))

let instances

class FakeWebSocket {
  constructor(url) {
    this.url = url
    this.readyState = FakeWebSocket.CONNECTING
    this.close = jest.fn(() => {
      this.readyState = FakeWebSocket.CLOSED
    })
    instances.push(this)
  }
}
FakeWebSocket.CONNECTING = 0
FakeWebSocket.OPEN = 1
FakeWebSocket.CLOSING = 2
FakeWebSocket.CLOSED = 3

function lastSocket() {
  return instances[instances.length - 1]
}

beforeEach(() => {
  jest.useFakeTimers()
  jest.clearAllMocks()
  instances = []
  global.WebSocket = FakeWebSocket
  getToken.mockReturnValue('token-abc')
  callSocket.close()
  callSocket.handlers = {}
  callSocket.userId = null
  callSocket.ws = null
  callSocket.reconnectTimer = null
  callSocket.reconnectAttempts = 0
  callSocket.manualClose = false
  callSocket.draining = false
  callSocket.reloginShowing = false
})

afterEach(() => {
  callSocket.close()
  jest.runOnlyPendingTimers()
  jest.useRealTimers()
})

describe('connect 建立连接', () => {
  test('未传 userId 不建立连接', () => {
    callSocket.connect(null)

    expect(instances).toHaveLength(0)
  })

  test('握手 URL 携带 token 且 onopen 重置重试次数', () => {
    callSocket.connect(9)
    const ws = lastSocket()

    expect(ws.url).toContain('/ws/call/9')
    expect(ws.url).toContain('token=token-abc')
    callSocket.reconnectAttempts = 3
    ws.onopen()
    expect(callSocket.reconnectAttempts).toBe(0)
  })

  test('已连接同用户时复用连接，不同用户先关闭旧连接', () => {
    callSocket.connect(9)
    const first = lastSocket()
    first.readyState = FakeWebSocket.OPEN
    callSocket.connect(9)
    expect(instances).toHaveLength(1)

    callSocket.connect(10)
    expect(first.close).toHaveBeenCalled()
    expect(instances).toHaveLength(2)
  })

  test('同一用户重复连接保持单实例', () => {
    callSocket.connect(9)
    callSocket.connect(9)

    expect(instances).toHaveLength(1)
  })
})

describe('消息分发', () => {
  test('合法 JSON 按 type 分发，非 JSON 忽略', () => {
    const handler = jest.fn()
    callSocket.on('INBOUND_RING', handler)
    callSocket.connect(9)
    const ws = lastSocket()

    ws.onmessage({ data: JSON.stringify({ type: 'INBOUND_RING', data: { callId: 1 } }) })
    ws.onmessage({ data: 'not-json' })

    expect(handler).toHaveBeenCalledWith({ callId: 1 })
  })

  test('通配监听接收 type 与 data，监听器异常不影响后续', () => {
    const bad = jest.fn(() => { throw new Error('监听器故障') })
    const wildcard = jest.fn()
    callSocket.on('HANGUP', bad)
    callSocket.on('*', wildcard)

    callSocket.dispatch('HANGUP', { callId: 1 })

    expect(wildcard).toHaveBeenCalledWith('HANGUP', { callId: 1 })
  })

  test('on 忽略非法参数，off 支持按类型与按引用移除', () => {
    const handler = jest.fn()
    callSocket.on(null, handler)
    callSocket.on('X', 'not-function')
    expect(callSocket.handlers).toEqual({})

    callSocket.on('X', handler)
    callSocket.off('X', handler)
    expect(callSocket.handlers.X).toEqual([])

    callSocket.on('X', handler)
    callSocket.off('X')
    expect(callSocket.handlers.X).toBeUndefined()
  })
})

describe('断线重连与鉴权失败', () => {
  test('普通断线按退避调度重连', () => {
    jest.spyOn(Math, 'random').mockReturnValue(0.5)
    callSocket.connect(9)
    const first = lastSocket()
    first.readyState = FakeWebSocket.CLOSED
    first.onclose({ code: 1006 })
    expect(callSocket.reconnectTimer).not.toBeNull()

    jest.advanceTimersByTime(3000)
    expect(instances).toHaveLength(2)
  })

  test('主动关闭后不再调度重连', () => {
    callSocket.connect(9)
    const ws = lastSocket()

    callSocket.close()
    ws.onclose({ code: 1006 })

    expect(callSocket.reconnectTimer).toBeNull()
  })

  test('1008 鉴权失败停止重连并引导重新登录', () => {
    MessageBox.confirm.mockReturnValue(new Promise(() => {}))
    const unauthorized = jest.fn()
    callSocket.on('UNAUTHORIZED', unauthorized)
    callSocket.connect(9)
    const ws = lastSocket()

    ws.onclose({ code: 1008, reason: 'token expired' })

    expect(unauthorized).toHaveBeenCalledWith({ reason: 'token expired' })
    expect(MessageBox.confirm).toHaveBeenCalled()
    expect(callSocket.manualClose).toBe(true)
    expect(callSocket.reconnectTimer).toBeNull()
    jest.advanceTimersByTime(30000)
    expect(instances).toHaveLength(1)
  })

  test('登录态已失效时跳过重连', () => {
    jest.spyOn(Math, 'random').mockReturnValue(0.5)
    callSocket.connect(9)
    const ws = lastSocket()
    getToken.mockReturnValue(null)
    ws.readyState = FakeWebSocket.CLOSED

    ws.onclose({ code: 1006 })
    jest.advanceTimersByTime(3000)

    expect(instances).toHaveLength(1)
  })

  test('收到排空通知后关闭旧连接并快速重连', () => {
    callSocket.connect(9)
    const first = lastSocket()

    first.onmessage({ data: JSON.stringify({ type: 'SERVER_DRAINING' }) })

    expect(first.close).toHaveBeenCalled()
    jest.advanceTimersByTime(400)
    expect(instances).toHaveLength(2)
  })

  test('未连接时排空通知不产生副作用', () => {
    callSocket.forceReconnect()

    expect(instances).toHaveLength(0)
  })

  test('重新登录提示进行中时不重复弹窗', () => {
    callSocket.reloginShowing = true
    callSocket.promptRelogin()

    expect(MessageBox.confirm).not.toHaveBeenCalled()
  })
})
