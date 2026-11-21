'use strict'

/**
 * WebRTC 软电话单测：mock JsSIP 与业务 WS，锁定注册幂等、状态机、
 * 来电事件透传与通话控制（接听/挂断/外呼/DTMF/保持）。
 */
const mockUaInstances = []
let mockUaThrow = false

jest.mock('jssip', () => {
  class MockUA {
    constructor(configuration) {
      if (mockUaThrow) throw new Error('UA 初始化失败')
      this.configuration = configuration
      this.handlers = {}
      this.started = false
      this.stopped = false
      this.unregisterArgs = null
      this.callArgs = null
      mockUaInstances.push(this)
    }
    on(name, fn) {
      (this.handlers[name] = this.handlers[name] || []).push(fn)
    }
    trigger(name, payload) {
      (this.handlers[name] || []).forEach(fn => fn(payload))
    }
    start() { this.started = true }
    stop() { this.stopped = true }
    unregister(args) { this.unregisterArgs = args }
    call(target, options) { this.callArgs = { target, options } }
  }
  const WebSocketInterface = jest.fn(function (url) { this.url = url })
  return { __esModule: true, default: { UA: MockUA, WebSocketInterface }, UA: MockUA, WebSocketInterface }
})

jest.mock('element-ui', () => ({ __esModule: true, Message: { warning: jest.fn() } }))

jest.mock('@/utils/callSocket', () => ({
  __esModule: true,
  default: { dispatch: jest.fn() }
}))

const callSocket = require('@/utils/callSocket').default
const { Message } = require('element-ui')

// 每次加载都获得全新的软电话单例，避免用例间状态串扰
function loadPhone() {
  let phone
  jest.isolateModules(() => {
    phone = require('@/utils/webrtcSipPhone').getSipPhone()
  })
  return phone
}

// 构造会话桩：记录事件回调并支持手动触发
function makeSession(direction = 'incoming') {
  const handlers = {}
  return {
    direction,
    request: { headers: { 'Call-ID': [{ raw: 'call-1' }] } },
    remote_identity: { display_name: '张三', uri: { user: '1002' } },
    local_identity: { uri: { user: '1001' } },
    isInProgress: true,
    isEstablished: jest.fn(() => false),
    answer: jest.fn(),
    terminate: jest.fn(),
    sendDTMF: jest.fn(),
    hold: jest.fn(),
    unhold: jest.fn(),
    on(name, fn) {
      (handlers[name] = handlers[name] || []).push(fn)
    },
    trigger(name, payload) {
      (handlers[name] || []).forEach(fn => fn(payload))
    }
  }
}

// 注册并模拟 JsSIP 回报注册成功
async function registeredPhone(opts = { extension: '1001' }) {
  const phone = loadPhone()
  const pending = phone.register(opts)
  mockUaInstances[mockUaInstances.length - 1].trigger('registered')
  await pending
  return phone
}

beforeEach(() => {
  mockUaInstances.length = 0
  mockUaThrow = false
  callSocket.dispatch.mockClear()
  Message.warning.mockClear()
  jest.spyOn(console, 'info').mockImplementation(() => {})
  jest.spyOn(console, 'log').mockImplementation(() => {})
  jest.spyOn(console, 'warn').mockImplementation(() => {})
})

afterEach(() => {
  jest.restoreAllMocks()
})

describe('分机注册', () => {
  test('缺少分机号直接拒绝', async () => {
    const phone = loadPhone()

    await expect(phone.register({})).rejects.toThrow('缺少分机号')
    expect(mockUaInstances).toHaveLength(0)
  })

  test('使用默认参数创建 UA 并在注册成功后 resolve', async () => {
    const phone = loadPhone()
    const statuses = []
    phone.on('statusChange', status => statuses.push(status))

    const pending = phone.register({ extension: '1001' })
    expect(phone.getStatus()).toBe('registering')
    expect(mockUaInstances).toHaveLength(1)

    const ua = mockUaInstances[0]
    expect(ua.configuration.uri).toBe('sip:1001@198.18.0.1')
    expect(ua.configuration.password).toBe('1234')
    expect(ua.configuration.register_expires).toBe(300)
    expect(ua.configuration.session_timers).toBe(false)
    expect(ua.configuration.sockets[0].url).toBe('ws://localhost:5066')
    expect(ua.started).toBe(true)

    ua.trigger('registered')
    await expect(pending).resolves.toBeUndefined()

    expect(phone.getStatus()).toBe('registered')
    expect(phone.registered).toBe(true)
    expect(statuses).toEqual(['registering', 'registered'])
  })

  test('支持自定义密码、域与 WebSocket 地址', () => {
    const phone = loadPhone()
    phone.register({ extension: '2002', password: 'pwd', domain: 'pbx.test', wsUrl: 'ws://pbx.test:8080' })

    const ua = mockUaInstances[0]
    expect(ua.configuration.uri).toBe('sip:2002@pbx.test')
    expect(ua.configuration.password).toBe('pwd')
    expect(ua.configuration.sockets[0].url).toBe('ws://pbx.test:8080')
  })

  test('同一分机已注册时直接复用，不重建 UA', async () => {
    const phone = await registeredPhone()

    await expect(phone.register({ extension: '1001' })).resolves.toBeUndefined()

    expect(mockUaInstances).toHaveLength(1)
  })

  test('握手中重复注册复用同一 UA 并共享注册结果', async () => {
    const phone = loadPhone()

    const first = phone.register({ extension: '1001' })
    const second = phone.register({ extension: '1001' })
    expect(mockUaInstances).toHaveLength(1)

    mockUaInstances[0].trigger('registered')

    await expect(first).resolves.toBeUndefined()
    await expect(second).resolves.toBeUndefined()
  })

  test('握手中注册失败时两个 Promise 均拒绝', async () => {
    const phone = loadPhone()

    const first = phone.register({ extension: '1001' })
    const second = phone.register({ extension: '1001' })
    mockUaInstances[0].trigger('registrationFailed', { cause: 'auth' })

    await expect(first).rejects.toThrow('SIP 注册失败')
    await expect(second).rejects.toThrow('SIP 注册失败')
  })

  test('断线中的同一分机不重建 UA，交给 JsSIP 自动重连', async () => {
    const phone = await registeredPhone()
    mockUaInstances[0].trigger('disconnected', { cause: 'lost' })

    await expect(phone.register({ extension: '1001' })).resolves.toBeUndefined()
    expect(mockUaInstances).toHaveLength(1)
  })

  test('分机变更时销毁旧 UA 并创建新 UA', async () => {
    const phone = await registeredPhone()
    const oldUa = mockUaInstances[0]

    const pending = phone.register({ extension: '2002', wsUrl: 'ws://other:5066' })

    expect(oldUa.unregisterArgs).toEqual({ all: true })
    expect(oldUa.stopped).toBe(true)
    expect(mockUaInstances).toHaveLength(2)
    expect(mockUaInstances[1].configuration.uri).toBe('sip:2002@198.18.0.1')

    mockUaInstances[1].trigger('registered')
    await expect(pending).resolves.toBeUndefined()
  })

  test('UA 构造异常时记录失败状态并拒绝', async () => {
    mockUaThrow = true
    const phone = loadPhone()

    await expect(phone.register({ extension: '1001' })).rejects.toThrow('UA 初始化失败')
    expect(phone.getStatus()).toBe('failed')
    expect(phone.lastError).toBe('UA 初始化失败')
  })
})

describe('注册状态事件', () => {
  test('disconnected 汇总 socket 地址与错误原因', async () => {
    const phone = await registeredPhone()

    mockUaInstances[0].trigger('disconnected', {
      socket: { url: 'ws://localhost:5066' },
      error: true
    })

    expect(phone.getStatus()).toBe('disconnected')
    expect(phone.registered).toBe(false)
    expect(phone.lastError).toBe('ws://localhost:5066 连接被拒绝或服务未启动')
  })

  test('disconnected 支持 Error 对象作为错误原因', async () => {
    const phone = await registeredPhone()

    mockUaInstances[0].trigger('disconnected', { error: new Error('网络中断') })

    expect(phone.lastError).toBe('网络中断')
  })

  test('unregistered 记录注销原因', async () => {
    const phone = await registeredPhone()

    mockUaInstances[0].trigger('unregistered', { cause: 'bye' })

    expect(phone.getStatus()).toBe('unregistered')
    expect(phone.lastError).toBe('bye')
    expect(phone.registered).toBe(false)
  })

  test('registrationFailed 拼装状态码并标记失败', async () => {
    const phone = await registeredPhone()

    mockUaInstances[0].trigger('registrationFailed', {
      cause: 'auth',
      response: { status_code: 403, reason_phrase: 'Forbidden' }
    })

    expect(phone.getStatus()).toBe('failed')
    expect(phone.lastError).toBe('auth 403')
  })
})

describe('来电与通话控制', () => {
  test('来电触发 incomingCall 并推送 SIP_INCOMING 弹屏', async () => {
    const phone = await registeredPhone()
    const incoming = []
    phone.on('incomingCall', info => incoming.push(info))

    mockUaInstances[0].trigger('newRTCSession', { session: makeSession('incoming') })

    expect(incoming).toEqual([{ callId: 'call-1', from: '张三', to: '1001' }])
    expect(callSocket.dispatch).toHaveBeenCalledWith({
      type: 'SIP_INCOMING',
      data: { extension: '1001', from: '张三', callId: 'call-1' }
    })
  })

  test('会话接听与挂断推送对应业务事件', async () => {
    const phone = await registeredPhone()
    const accepted = []
    const ended = []
    phone.on('sessionAccepted', info => accepted.push(info))
    phone.on('sessionEnded', info => ended.push(info))
    const session = makeSession('incoming')
    mockUaInstances[0].trigger('newRTCSession', { session })

    session.trigger('accepted', { cause: 'ok' })
    expect(accepted).toEqual([{ isIncoming: true, callId: 'call-1', data: { cause: 'ok' } }])
    expect(callSocket.dispatch).toHaveBeenCalledWith({
      type: 'SIP_ANSWERED',
      data: { extension: '1001', callId: 'call-1', from: '张三' }
    })

    session.trigger('ended')
    expect(ended).toEqual([{ isIncoming: true, callId: 'call-1' }])
    expect(phone.currentSession).toBeNull()
    expect(callSocket.dispatch).toHaveBeenCalledWith({
      type: 'SIP_HANGUP',
      data: { extension: '1001', callId: 'call-1' }
    })
  })

  test('外呼会话接听推送 SIP_CALL_CONNECTED', async () => {
    const phone = await registeredPhone()
    const session = makeSession('outgoing')
    mockUaInstances[0].trigger('newRTCSession', { session })

    session.trigger('accepted')

    expect(callSocket.dispatch).toHaveBeenCalledWith({
      type: 'SIP_CALL_CONNECTED',
      data: { extension: '1001', callId: 'call-1', from: '张三' }
    })
  })

  test('peerconnection ontrack 挂载远端音频并自动播放', async () => {
    const play = jest.fn(() => Promise.resolve())
    jest.spyOn(HTMLMediaElement.prototype, 'play').mockImplementation(play)
    const phone = await registeredPhone()
    const pc = { ontrack: null }
    const session = makeSession('incoming')
    mockUaInstances[0].trigger('newRTCSession', { session })

    session.trigger('peerconnection', { peerconnection: pc })
    const stream = { id: 'remote-stream' }
    pc.ontrack({ streams: [stream] })

    expect(phone.remoteAudio).not.toBeNull()
    expect(phone.remoteAudio.autoplay).toBe(true)
    expect(phone.remoteAudio.srcObject).toBe(stream)
    expect(play).toHaveBeenCalled()
  })

  test('answer 仅对进行中的来电生效', async () => {
    const phone = await registeredPhone()
    expect(phone.answer()).toBe(false)

    const session = makeSession('incoming')
    mockUaInstances[0].trigger('newRTCSession', { session })

    expect(phone.answer()).toBe(true)
    expect(session.answer).toHaveBeenCalledWith({
      mediaConstraints: { audio: true, video: false },
      rtcOfferConstraints: { offerToReceiveAudio: true, offerToReceiveVideo: false }
    })

    session.direction = 'outgoing'
    expect(phone.answer()).toBe(false)
  })

  test('terminate 挂断当前会话并清空会话引用', async () => {
    const phone = await registeredPhone()
    expect(phone.terminate()).toBe(false)

    const session = makeSession('outgoing')
    mockUaInstances[0].trigger('newRTCSession', { session })

    expect(phone.terminate()).toBe(true)
    expect(session.terminate).toHaveBeenCalledTimes(1)
    expect(phone.currentSession).toBeNull()
  })

  test('未注册时外呼给出提示，注册后调用 UA.call', async () => {
    const phone = loadPhone()

    expect(phone.call('1002')).toBe(false)
    expect(Message.warning).toHaveBeenCalledWith('SIP 分机未注册，无法外呼')

    const pending = phone.register({ extension: '1001' })
    mockUaInstances[0].trigger('registered')
    await pending

    expect(phone.call('1002')).toBe(true)
    expect(mockUaInstances[0].callArgs.target).toBe('1002')
    expect(mockUaInstances[0].callArgs.options.mediaConstraints).toEqual({ audio: true, video: false })
    expect(mockUaInstances[0].callArgs.options.eventHandlers).toEqual({})
  })

  test('DTMF 与保持仅在通话建立后生效', async () => {
    const phone = await registeredPhone()
    expect(phone.sendDTMF(1)).toBe(false)

    const session = makeSession('outgoing')
    session.isEstablished.mockReturnValue(true)
    mockUaInstances[0].trigger('newRTCSession', { session })

    expect(phone.sendDTMF(5)).toBe(true)
    expect(session.sendDTMF).toHaveBeenCalledWith({ type: 'application/dtmf-relay', content: '5' })

    phone.hold()
    expect(session.hold).toHaveBeenCalledTimes(1)
    phone.unhold()
    expect(session.unhold).toHaveBeenCalledTimes(1)
  })
})

describe('注销与事件总线', () => {
  test('unregister 销毁 UA 并广播离线状态', async () => {
    const phone = await registeredPhone()
    const ua = mockUaInstances[0]
    const statuses = []
    phone.on('statusChange', status => statuses.push(status))

    phone.unregister()

    expect(ua.unregisterArgs).toEqual({ all: true })
    expect(ua.stopped).toBe(true)
    expect(phone.getStatus()).toBe('offline')
    expect(statuses).toEqual(['offline'])
  })

  test('on / off / emit 支持多监听与异常隔离', () => {
    const phone = loadPhone()
    const fn = jest.fn()
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {})

    phone.on('custom', fn)
    phone.emit('custom', 1, 2)
    expect(fn).toHaveBeenCalledWith(1, 2)

    phone.off('custom', fn)
    phone.emit('custom')
    expect(fn).toHaveBeenCalledTimes(1)

    phone.on('custom', () => { throw new Error('listener boom') })
    expect(() => phone.emit('custom')).not.toThrow()
    expect(errorSpy).toHaveBeenCalled()

    phone.off('unknown', fn)
  })

  test('getSipPhone 返回全局唯一实例且默认导出等价', () => {
    jest.isolateModules(() => {
      const mod = require('@/utils/webrtcSipPhone')
      const first = mod.getSipPhone()
      const second = mod.getSipPhone()

      expect(first).toBe(second)
      expect(mod.default).toBe(mod.getSipPhone)
      expect(first.getStatus()).toBe('offline')
    })
  })
})
