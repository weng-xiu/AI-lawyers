/**
 * 呼叫事件 WebSocket 客户端
 *
 * 连接后端 /ws/call/{userId}?token=xxx，实时接收：
 *   - INBOUND_RING  来电弹屏（分配到本坐席的来话）
 *   - QUEUED       排队通知
 *   - ANSWERED     通话接通
 *   - BRIDGED      通话桥接
 *   - HANGUP       通话挂断
 *   - DTMF         按键
 *   - AGENT_LOGIN / AGENT_LOGOUT / AGENT_STATUS / CALL_START / CALL_END 坐席状态
 *
 * 通过 on(type, handler) 注册业务回调，断线自动重连（指数退避 + 抖动）。
 * N5：握手必须携带 JWT；被服务端以 1008（策略违例/鉴权失败）关闭时不再盲目重连，
 * 触发 UNAUTHORIZED 事件并走与 HTTP 401 一致的重新登录流程。
 */

import { getToken } from '@/utils/auth'
import { MessageBox } from 'element-ui'

// 重连退避：3s 起步指数翻倍，封顶 30s，附加 ±20% 抖动避免多端同时重连
const RECONNECT_BASE_MS = 3000
const RECONNECT_MAX_MS = 30000
// 服务端 CloseReason.CloseCodes.VIOLATED_POLICY = 1008（鉴权失败/token 过期/userId 越权）
const CLOSE_POLICY_VIOLATION = 1008

class CallSocket {
  constructor() {
    this.ws = null
    this.userId = null
    this.handlers = {}
    this.reconnectTimer = null
    this.manualClose = false
    this.reconnectAttempts = 0
    this.reloginShowing = false
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
    // N5：握手期 JWT 鉴权，浏览器 WebSocket 无法自定义头，token 走 query 参数
    const token = getToken()
    const url = `${proto}://${window.location.host}/ws/call/${userId}` +
      (token ? `?token=${encodeURIComponent(token)}` : '')
    // 日志严禁打印完整 URL（query 中含 JWT）
    const safeUrl = `${proto}://${window.location.host}/ws/call/${userId}`
    try {
      this.ws = new WebSocket(url)
    } catch (e) {
      console.warn('[CallWS] 连接失败', e)
      this.scheduleReconnect()
      return
    }
    this.ws.onopen = () => {
      this.reconnectAttempts = 0
      console.info('[CallWS] 已连接', safeUrl)
    }
    this.ws.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        this.dispatch(msg.type, msg.data)
      } catch (e) {
        // 忽略非 JSON 消息
      }
    }
    this.ws.onclose = (evt) => {
      // N5：鉴权失败（token 缺失/过期/越权）→ 不重连，通知业务层并引导重新登录
      if (evt && evt.code === CLOSE_POLICY_VIOLATION) {
        this.manualClose = true
        this.clearReconnectTimer()
        console.warn('[CallWS] 握手鉴权失败（1008），停止重连:', evt.reason || 'unauthorized')
        this.dispatch('UNAUTHORIZED', { reason: evt.reason })
        this.promptRelogin()
        return
      }
      if (!this.manualClose) this.scheduleReconnect()
    }
    this.ws.onerror = () => {
      try { this.ws.close() } catch (e) {}
    }
  }

  /**
   * 与 request.js 的 401 处理保持一致：提示后 LogOut 回登录页；
   * 懒加载 store 规避 store/modules/agent.js ↔ 本文件的循环依赖。
   */
  promptRelogin() {
    if (this.reloginShowing) return
    this.reloginShowing = true
    MessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', {
      confirmButtonText: '重新登录',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      this.reloginShowing = false
      // eslint-disable-next-line global-require
      const store = require('@/store').default
      store.dispatch('LogOut').then(() => {
        window.location.href = '/index'
      })
    }).catch(() => {
      this.reloginShowing = false
    })
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
    // 指数退避 + ±20% 抖动
    const exp = Math.min(RECONNECT_BASE_MS * Math.pow(2, this.reconnectAttempts), RECONNECT_MAX_MS)
    const delay = Math.round(exp * (0.8 + Math.random() * 0.4))
    this.reconnectAttempts += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      // 重连前令牌已不存在（已退出登录）则放弃，避免无 token 握手被 1008 拒绝后空转
      if (this.userId && getToken()) {
        this.connect(this.userId)
      } else {
        console.info('[CallWS] 无有效登录态，跳过重连')
      }
    }, delay)
  }

  clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  close() {
    this.manualClose = true
    this.clearReconnectTimer()
    if (this.ws) {
      try { this.ws.close() } catch (e) {}
      this.ws = null
    }
  }
}

export default new CallSocket()
