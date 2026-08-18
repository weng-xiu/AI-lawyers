/**
 * 呼叫事件 WebSocket 客户端
 *
 * 连接后端 /ws/call/{userId}，实时接收：
 *   - INBOUND_RING  来电弹屏（分配到本坐席的来话）
 *   - QUEUED       排队通知
 *   - ANSWERED     通话接通
 *   - BRIDGED      通话桥接
 *   - HANGUP       通话挂断
 *   - DTMF         按键
 *   - AGENT_LOGIN / AGENT_LOGOUT / AGENT_STATUS / CALL_START / CALL_END 坐席状态
 *
 * 通过 on(type, handler) 注册业务回调，断线自动重连。
 */

const RECONNECT_DELAY = 3000

class CallSocket {
  constructor() {
    this.ws = null
    this.userId = null
    this.handlers = {}
    this.reconnectTimer = null
    this.manualClose = false
  }

  connect(userId) {
    if (!userId) return
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      if (this.userId === userId) return
      this.close()
    }
    this.userId = userId
    this.manualClose = false
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${proto}://${window.location.host}/ws/call/${userId}`
    try {
      this.ws = new WebSocket(url)
    } catch (e) {
      console.warn('[CallWS] 连接失败', e)
      this.scheduleReconnect()
      return
    }
    this.ws.onopen = () => {
      console.info('[CallWS] 已连接', url)
    }
    this.ws.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        this.dispatch(msg.type, msg.data)
      } catch (e) {
        // 忽略非 JSON 消息
      }
    }
    this.ws.onclose = () => {
      if (!this.manualClose) this.scheduleReconnect()
    }
    this.ws.onerror = () => {
      try { this.ws.close() } catch (e) {}
    }
  }

  on(type, handler) {
    if (!type || typeof handler !== 'function') return
    if (!this.handlers[type]) this.handlers[type] = []
    this.handlers[type].push(handler)
  }

  off(type, handler) {
    if (!this.handlers[type]) return
    if (!handler) {
      delete this.handlers[type]
      return
    }
    this.handlers[type] = this.handlers[type].filter(h => h !== handler)
  }

  dispatch(type, data) {
    const hs = this.handlers[type]
    if (hs) hs.forEach(h => { try { h(data) } catch (e) { console.error(e) } })
    // 通配监听
    const all = this.handlers['*']
    if (all) all.forEach(h => { try { h(type, data) } catch (e) {} })
  }

  scheduleReconnect() {
    if (this.manualClose) return
    if (this.reconnectTimer) return
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      if (this.userId) this.connect(this.userId)
    }, RECONNECT_DELAY)
  }

  close() {
    this.manualClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      try { this.ws.close() } catch (e) {}
      this.ws = null
    }
  }
}

export default new CallSocket()
