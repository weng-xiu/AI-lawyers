/**
 * 基于 JsSIP 的 WebRTC 网页软电话
 *
 * 用于坐席签入后通过浏览器向 FreeSWITCH 注册 SIP 分机，
 * 实现真正的语音通话（RTP/SRTP via WebRTC），而不仅仅是
 * 业务状态通道（callSocket 仅传输 JSON 事件）。
 *
 * FreeSWITCH internal profile 默认开启 ws-port 5066 / wss-port 7443。
 */
import JsSIP from 'jssip'
import { Message } from 'element-ui'
import callSocket from './callSocket'

// 默认 FreeSWITCH WebSocket 地址（开发环境）
const DEFAULT_SIP_WS_URL = 'ws://198.18.0.1:5066'
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
    this.registrationStatus = 'offline' // offline | registering | registered | failed
  }

  /**
   * 注册 SIP 分机
   * @param {Object} opts { extension, password, domain, wsUrl }
   */
  register(opts = {}) {
    if (this.ua) {
      this.unregister()
    }
    const extension = opts.extension
    const password = opts.password || DEFAULT_SIP_PASSWORD
    const domain = opts.domain || DEFAULT_SIP_DOMAIN
    const wsUrl = opts.wsUrl || DEFAULT_SIP_WS_URL
    if (!extension) {
      return Promise.reject(new Error('缺少分机号'))
    }

    this.registrationStatus = 'registering'
    this.lastError = null
    this.emit('statusChange', this.registrationStatus)

    const socket = new JsSIP.WebSocketInterface(wsUrl)
    const configuration = {
      sockets: [socket],
      uri: `sip:${extension}@${domain}`,
      password,
      register: true,
      session_timers: false,
      connection_recovery_min_interval: 2,
      connection_recovery_max_interval:30
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
    this.extension = extension

    return new Promise((resolve, reject) => {
      const onReg = () => { cleanup(); resolve() }
      const onFail = () => { cleanup(); reject(new Error('SIP 注册失败')) }
      const onConn = () => { cleanup(); reject(new Error('SIP WebSocket 连接失败')) }
      const cleanup = () => {
        this.off('registered', onReg)
        this.off('registrationFailed', onFail)
        this.off('disconnected', onConn)
      }
      this.on('registered', onReg)
      this.on('registrationFailed', onFail)
      this.on('disconnected', onConn)
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
      this.lastError = (data && (data.socket && data.socket.url) + ' ' + (data.error && (data.error.message || ''))) || null
      this.emit('statusChange', this.registrationStatus)
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
      this.lastError = (data && (data.cause || '') + ' ' + ((data.response && data.response.status_code) || '')) || 'registration failed'
      this.emit('statusChange', this.registrationStatus)
      this.emit('registrationFailed', data)
      console.warn('[SIP] 注册失败:', data && data.cause, data && data.response && data.response.status_code, data && data.response && data.response.reason_phrase)
    })
    this.ua.on('newRTCSession', (data) => {
      const session = data.session
      const isIncoming = session.direction === 'incoming'
      this._attachSession(session, isIncoming)
    })
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
      // 确保本地有用户交互才会播放，用户点"接听"时已满足
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

  /** 注销并销毁 UA */
  unregister() {
    if (this.ua) {
      try {
        if (this.registered) {
          this.ua.unregister({ all: true })
        }
        this.ua.stop()
      } catch (e) {}
      this.ua = null
    }
    this.registered = false
    this.registrationStatus = 'offline'
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
