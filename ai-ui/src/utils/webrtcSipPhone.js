/**
 * 基于 JsSIP 的 WebRTC 网页软电话
 *
 * 设计要点（避免反复注册）：
 * 1. UA 单例：同一个分机只创建一次 JsSIP.UA，由 JsSIP 内部负责
 *    WebSocket 断线重连（connection_recovery_*）与 SIP 注册刷新（register_expires）。
 * 2. 业务层不应在 5 秒轮询里反复调用 register/unregister，否则会打断 JsSIP 的
 *    指数退避重连，导致"不断新建 UA、不断连 5066"。
 * 3. register() 幂等：相同 extension/domain/wsUrl 已在注册或已注册时直接复用。
 * 4. unregister() 仅在签出/分机变更/退出时调用，真正销毁 UA。
 */
import JsSIP from 'jssip'
import { Message } from 'element-ui'
import callSocket from './callSocket'

// 默认 FreeSWITCH WebSocket 地址（开发环境）
const DEFAULT_SIP_WS_URL = 'ws://localhost:5066'
const DEFAULT_SIP_DOMAIN = '198.18.0.1'
const DEFAULT_SIP_PASSWORD = '1234'

let phoneInstance = null

class WebRtcSipPhone {
  constructor() {
    this.ua = null
    this.currentSession = null
    this.audioElement = null
    this.remoteAudio = null
    this.localAudio = null
    this.listeners = {}
    this.registered = false
    this.registrationStatus = 'offline' // offline | registering | connected | registered | disconnected | failed
    // 当前 UA 对应的注册参数，用于判断 register() 是否需要重建
    this._activeKey = null
    this.extension = null
    this.lastError = null
  }

  /**
   * 注册 SIP 分机（幂等）。
   * @param {Object} opts { extension, password, domain, wsUrl }
   * @returns {Promise<void>} resolve 仅表示首次注册成功；重复调用直接 resolve。
   */
  register(opts = {}) {
    const extension = opts.extension
    const password = opts.password || DEFAULT_SIP_PASSWORD
    const domain = opts.domain || DEFAULT_SIP_DOMAIN
    const wsUrl = opts.wsUrl || DEFAULT_SIP_WS_URL
    if (!extension) {
      return Promise.reject(new Error('缺少分机号'))
    }

    const key = `${extension}|${domain}|${wsUrl}`

    // 已经注册到同一个目标 —— 直接复用
    if (this.ua && this._activeKey === key) {
      if (this.registered) return Promise.resolve()
      if (this.registrationStatus === 'registering' || this.registrationStatus === 'connected') {
        // 正在握手中，复用当前 UA；返回一个一次性 promise 等结果
        return new Promise((resolve, reject) => {
          const onReg = () => { cleanup(); resolve() }
          const onFail = () => { cleanup(); reject(new Error('SIP 注册失败')) }
          const cleanup = () => {
            this.off('registered', onReg)
            this.off('registrationFailed', onFail)
          }
          this.on('registered', onReg)
          this.on('registrationFailed', onFail)
        })
      }
      // 已存在 UA 但当前未注册（例如断线中）—— 不重建，交给 JsSIP 自动重连
      return Promise.resolve()
    }

    // 分机/地址变更：先销毁旧 UA
    this._destroyUa()

    this.registrationStatus = 'registering'
    this.lastError = null
    this.extension = extension
    this._activeKey = key
    this.emit('statusChange', this.registrationStatus)

    const socket = new JsSIP.WebSocketInterface(wsUrl)
    const configuration = {
      sockets: [socket],
      uri: `sip:${extension}@${domain}`,
      password,
      register: true,
      // 注册有效期（秒），JsSIP 会在到期前自动刷新注册
      register_expires: 300,
      session_timers: false,
      // WebSocket 断线自动重连退避（秒）
      // 最小 10s、最大 60s：FreeSWITCH 未启动时避免频繁刷屏，
      // 服务恢复后最长 60s 内自动连上，无需刷新页面
      connection_recovery_min_interval: 10,
      connection_recovery_max_interval: 60
    }
    console.info('[SIP] 开始注册分机', extension, '->', wsUrl)

    try {
      this.ua = new JsSIP.UA(configuration)
    } catch (e) {
      this.registrationStatus = 'failed'
      this.lastError = e.message || String(e)
      this.emit('statusChange', this.registrationStatus)
      return Promise.reject(e)
    }

    this._bindUaEvents()
    this.ua.start()

    return new Promise((resolve, reject) => {
      const onReg = () => { cleanup(); resolve() }
      const onFail = () => { cleanup(); reject(new Error('SIP 注册失败')) }
      const cleanup = () => {
        this.off('registered', onReg)
        this.off('registrationFailed', onFail)
      }
      this.on('registered', onReg)
      this.on('registrationFailed', onFail)
      // 注意：不再监听 disconnected 来 reject，因为断线后 JsSIP 会自动重连，
      // 不应把它当成"注册失败"抛给业务层反复重试
    })
  }

  _bindUaEvents() {
    this.ua.on('connected', () => {
      this.registrationStatus = 'connected'
      this.emit('statusChange', this.registrationStatus)
    })
    this.ua.on('disconnected', (data) => {
      this.registered = false
      this.registrationStatus = 'disconnected'
      // JsSIP disconnected 事件的 data.error 可能是布尔值 true（连接失败）
      // 或 Error 对象，这里统一提取可读信息
      let errMsg = ''
      if (data) {
        if (data.socket && data.socket.url) errMsg += data.socket.url
        if (data.error instanceof Error) {
          errMsg += (errMsg ? ' ' : '') + data.error.message
        } else if (typeof data.error === 'string') {
          errMsg += (errMsg ? ' ' : '') + data.error
        } else if (data.error === true) {
          errMsg += (errMsg ? ' ' : '') + '连接被拒绝或服务未启动'
        } else if (data.cause) {
          errMsg += (errMsg ? ' ' : '') + data.cause
        }
      }
      this.lastError = errMsg || null
      this.emit('statusChange', this.registrationStatus)
      // JsSIP 内部会按 connection_recovery_* 自动重连，这里不要销毁 UA
    })
    this.ua.on('registered', () => {
      this.registered = true
      this.registrationStatus = 'registered'
      this.lastError = null
      this.emit('statusChange', this.registrationStatus)
      this.emit('registered')
      console.log('[SIP] 分机已注册:', this.extension)
    })
    this.ua.on('unregistered', (data) => {
      this.registered = false
      this.registrationStatus = 'unregistered'
      this.lastError = (data && (data.cause || '')) || null
      this.emit('statusChange', this.registrationStatus)
      console.log('[SIP] 分机已注销:', this.extension, data && data.cause)
    })
    this.ua.on('registrationFailed', (data) => {
      this.registered = false
      this.registrationStatus = 'failed'
      this.lastError = (data && (data.cause || '')) + ' ' +
                       ((data && data.response && data.response.status_code) || '') || 'registration failed'
      this.emit('statusChange', this.registrationStatus)
      this.emit('registrationFailed', data)
      console.warn('[SIP] 注册失败（JsSIP 将自动重试）:',
        data && data.cause,
        data && data.response && data.response.status_code,
        data && data.response && data.response.reason_phrase)
      // JsSIP 默认会按 register_expires/退避策略自动重试注册，
      // 这里不销毁 UA，避免业务层重复创建
    })
    this.ua.on('newRTCSession', (data) => {
      const session = data.session
      const isIncoming = session.direction === 'incoming'
      this._attachSession(session, isIncoming)
    })
  }

  _destroyUa() {
    if (this.ua) {
      try {
        if (this.registered) {
          this.ua.unregister({ all: true })
        }
        this.ua.stop()
      } catch (e) {}
      this.ua = null
    }
    this._activeKey = null
    this.registered = false
    this.registrationStatus = 'offline'
  }

  _attachSession(session, isIncoming) {
    this.currentSession = session
    const callInfo = {
      callId: (session.request && session.request.headers && session.request.headers['Call-ID'] &&
               session.request.headers['Call-ID'][0] && session.request.headers['Call-ID'][0].raw) ||
               (session.id || ''),
      from: session.remote_identity && session.remote_identity.display_name ||
            (session.remote_identity && session.remote_identity.uri && session.remote_identity.uri.user),
      to: session.local_identity && session.local_identity.uri && session.local_identity.uri.user
    }

    session.on('peerconnection', (e) => {
      const pc = e.peerconnection
      pc.ontrack = (ev) => {
        if (!this.remoteAudio) {
          this.remoteAudio = document.createElement('audio')
          this.remoteAudio.autoplay = true
          this.remoteAudio.setAttribute('playsinline', 'true')
        }
        if (ev.streams && ev.streams[0]) {
          this.remoteAudio.srcObject = ev.streams[0]
        } else {
          const stream = new MediaStream()
          stream.addTrack(ev.track)
          this.remoteAudio.srcObject = stream
        }
        this.remoteAudio.play().catch(err => {
          console.warn('[SIP] 自动播放被拦截，等待用户交互:', err)
        })
      }
    })

    if (isIncoming) {
      this.emit('incomingCall', callInfo)
      // 同时通过业务 WS 通知弹屏（callSocket 已连）
      callSocket.dispatch({
        type: 'SIP_INCOMING',
        data: { extension: this.extension, from: callInfo.from, callId: callInfo.callId }
      })
    }

    session.on('connecting', () => {
      this.emit('sessionConnecting', { isIncoming, callId: callInfo.callId })
    })
    session.on('progress', () => {
      this.emit('sessionProgress', { isIncoming, callId: callInfo.callId })
    })
    session.on('accepted', (data) => {
      this.emit('sessionAccepted', { isIncoming, callId: callInfo.callId, data })
      callSocket.dispatch({
        type: isIncoming ? 'SIP_ANSWERED' : 'SIP_CALL_CONNECTED',
        data: { extension: this.extension, callId: callInfo.callId, from: callInfo.from }
      })
    })
    session.on('failed', (data) => {
      this.emit('sessionFailed', { isIncoming, callId: callInfo.callId, cause: data && data.cause })
      this.currentSession = null
    })
    session.on('ended', () => {
      this.emit('sessionEnded', { isIncoming, callId: callInfo.callId })
      this.currentSession = null
      if (this.remoteAudio) {
        this.remoteAudio.srcObject = null
      }
      callSocket.dispatch({
        type: 'SIP_HANGUP',
        data: { extension: this.extension, callId: callInfo.callId }
      })
    })
    session.on('confirmed', () => {
      this.emit('sessionConfirmed', { isIncoming, callId: callInfo.callId })
    })
    session.on('hold', () => this.emit('sessionHold'))
    session.on('unhold', () => this.emit('sessionUnhold'))
  }

  /** 接听来电 */
  answer() {
    if (this.currentSession && this.currentSession.isInProgress && this.currentSession.direction === 'incoming') {
      const constraints = {
        mediaConstraints: { audio: true, video: false },
        rtcOfferConstraints: { offerToReceiveAudio: true, offerToReceiveVideo: false }
      }
      this.currentSession.answer(constraints)
      return true
    }
    return false
  }

  /** 挂断当前通话 */
  terminate() {
    if (this.currentSession) {
      try { this.currentSession.terminate() } catch (e) {}
      this.currentSession = null
      return true
    }
    return false
  }

  /** 外呼（浏览器侧发起，主要用于测试；正式外呼由后端 CTI 控制） */
  call(target, opts = {}) {
    if (!this.ua || !this.registered) {
      Message.warning('SIP 分机未注册，无法外呼')
      return false
    }
    const eventHandlers = opts.eventHandlers || {}
    this.ua.call(target, {
      eventHandlers,
      mediaConstraints: { audio: true, video: false }
    })
    return true
  }

  /** 发送 DTMF */
  sendDTMF(digit) {
    if (this.currentSession && this.currentSession.isEstablished()) {
      this.currentSession.sendDTMF({ type: 'application/dtmf-relay', content: digit.toString() })
      return true
    }
    return false
  }

  hold() {
    if (this.currentSession && this.currentSession.isEstablished()) {
      this.currentSession.hold()
    }
  }

  unhold() {
    if (this.currentSession && this.currentSession.isEstablished()) {
      this.currentSession.unhold()
    }
  }

  /**
   * 注销并销毁 UA。仅在签出 / 分机变更 / 退出系统时调用。
   * 注意：WebSocket 断线重连由 JsSIP 内部处理，业务层不要在轮询中调用本方法。
   */
  unregister() {
    this._destroyUa()
    this.emit('statusChange', this.registrationStatus)
  }

  on(event, fn) {
    (this.listeners[event] = this.listeners[event] || []).push(fn)
    return this
  }

  off(event, fn) {
    if (!this.listeners[event]) return
    this.listeners[event] = this.listeners[event].filter(f => f !== fn)
  }

  emit(event, ...args) {
    (this.listeners[event] || []).forEach(fn => {
      try { fn(...args) } catch (e) { console.error('[SIP] listener error', e) }
    })
  }

  getStatus() {
    return this.registrationStatus
  }
}

export function getSipPhone() {
  if (!phoneInstance) {
    phoneInstance = new WebRtcSipPhone()
  }
  return phoneInstance
}

export default getSipPhone
