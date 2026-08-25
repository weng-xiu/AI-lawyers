import { agentLogin, agentLogout, getCurrentAgent, updateAgentStatus, updateCallMode } from '@/api/lawyers/callCenter'
import callSocket from '@/utils/callSocket'
import { getSipPhone } from '@/utils/webrtcSipPhone'
import { Message } from 'element-ui'
import store from '@/store'

// 上一次已通知用户的 SIP 注册状态，避免重连/轮询时重复弹提示
let lastNotifiedSipStatus = 'offline'

const BINDING_KEY = 'AI_AGENT_BINDING'
// SIP 分机注册配置（FreeSWITCH internal ws 端口 5066）
const SIP_WS_URL = process.env.VUE_APP_SIP_WS_URL || 'ws://localhost:5066'
const SIP_DOMAIN = process.env.VUE_APP_SIP_DOMAIN || '198.18.0.1'
const SIP_PASSWORD = process.env.VUE_APP_SIP_PASSWORD || '1234'

// 浏览器关闭/页面卸载时通过 sendBeacon 兜底签出的接口（匿名）
const AUTO_LOGOUT_URL = process.env.VUE_APP_BASE_API + '/lawyers/call/agent/autoLogout'

function readBinding() {
  try {
    const raw = localStorage.getItem(BINDING_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

function writeBinding(binding) {
  try {
    localStorage.setItem(BINDING_KEY, JSON.stringify(binding))
  } catch (e) {
    // 忽略隐私模式等场景下的存储失败
  }
}

// 已通过 WebSocket 连接过的 userId，避免与其他账号切换时重复连
let connectedUserId = null

// 标记 beforeunload 监听器是否已注册，避免重复绑定
let unloadBound = false

// 标志：本次页面卸载是否由用户按 F5/Ctrl+R 刷新触发
// 通过 keydown 监听提前置位，beforeunload 中读取后重置
let isReloading = false

/**
 * 浏览器关闭/标签页关闭/跳转到外站时，通过 sendBeacon 尽力而为地通知后端签出。
 * 页面刷新(F5/Ctrl+R)跳过，保证刷新前后签入状态连续。
 */
function bindUnloadHandler() {
  if (unloadBound || typeof window === 'undefined') return
  unloadBound = true

  // 监听刷新快捷键（F5 / Ctrl+R / Ctrl+Shift+R / Cmd+R）
  window.addEventListener('keydown', (e) => {
    const key = (e.key || '').toLowerCase()
    if (key === 'f5' || ((e.ctrlKey || e.metaKey) && key === 'r')) {
      isReloading = true
      // beforeunload 紧随其后触发，重置交由 beforeunload 处理
    }
  }, true)

  // beforeunload 在刷新、关闭、跳转到外站时都会触发
  window.addEventListener('beforeunload', () => {
    // 刷新场景：不发送签出请求，保持坐席在线
    if (isReloading) {
      isReloading = false
      return
    }

    const state = store.state.agent
    if (!state || state.agentId == null) return

    // 非刷新场景（关闭标签页/关闭浏览器/地址栏跳转外站）：发送签出 beacon
    const params = new URLSearchParams()
    params.append('agentId', String(state.agentId))
    if (state.userId != null) params.append('userId', String(state.userId))
    try {
      navigator.sendBeacon(AUTO_LOGOUT_URL, params)
    } catch (e) {
      // 兜底：部分老浏览器不支持 sendBeacon 时使用同步 XHR
      try {
        const xhr = new XMLHttpRequest()
        xhr.open('POST', AUTO_LOGOUT_URL, false)
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded')
        xhr.send(params.toString())
      } catch (ignored) {}
    }
    callSocket.close()
    connectedUserId = null
  })

  // pagehide 作为兜底（移动端 Safari 不触发 beforeunload）
  // event.persisted 为 true 表示页面进入 bfcache（前进/后退），不算关闭
  window.addEventListener('pagehide', (event) => {
    if (isReloading || event.persisted) return
    const state = store.state.agent
    if (!state || state.agentId == null) return
    const params = new URLSearchParams()
    params.append('agentId', String(state.agentId))
    if (state.userId != null) params.append('userId', String(state.userId))
    try { navigator.sendBeacon(AUTO_LOGOUT_URL, params) } catch (e) {}
  })
}

// 坐席在线（status=1 在线/2 忙碌/3 休息）时建立呼叫事件通道；离线则关闭
function ensureCallSocket(agent) {
  if (!agent || !agent.userId) {
    callSocket.close()
    connectedUserId = null
    return
  }
  // status: 0=离线 1=在线 2=忙碌 3=休息
  if (agent.status === '0') {
    callSocket.close()
    connectedUserId = null
    return
  }
  if (connectedUserId === agent.userId && callSocket.ws) return
  callSocket.connect(agent.userId)
  connectedUserId = agent.userId
}

// 浏览器 WebRTC SIP 软电话总开关
// 未部署 FreeSWITCH 的开发环境可在 .env.development 中设为 false，
// 此时签入后只建立业务 WebSocket，不再尝试注册 SIP 分机
const SIP_ENABLED = process.env.VUE_APP_SIP_ENABLED !== 'false'

// 根据坐席记录启动/关闭浏览器侧 SIP 分机（WebRTC）
// 设计：register()/unregister() 均幂等；5 秒轮询重复调用不会重建 UA，
// JsSIP 内部负责 WebSocket 断线重连与注册刷新。
let lastSipExtension = null
function ensureSipPhone(agent) {
  const phone = getSipPhone()
  // SIP 功能关闭：若之前已注册则注销，否则直接返回
  if (!SIP_ENABLED) {
    if (lastSipExtension != null) {
      phone.unregister()
      lastSipExtension = null
    }
    return
  }
  // 离线 / 无分机：仅在曾经注册过时才销毁
  if (!agent || agent.status === '0' || !agent.sipExtension) {
    if (lastSipExtension != null) {
      phone.unregister()
      lastSipExtension = null
    }
    return
  }
  // 分机未变化：register() 内部会判断 UA 是否存在并直接复用，
  // 这里再做一层短路，避免每 5 秒都进入 register() 创建 Promise
  if (lastSipExtension === agent.sipExtension) {
    return
  }
  lastSipExtension = agent.sipExtension
  phone.register({
    extension: agent.sipExtension,
    password: SIP_PASSWORD,
    domain: SIP_DOMAIN,
    wsUrl: SIP_WS_URL
  }).catch(err => {
    // 注册失败（如 FreeSWITCH 未启动）仅告警，不重置 lastSipExtension；
    // JsSIP 会在内部按退避策略自动重连，避免业务层反复创建 UA。
    console.warn('[agent-store] SIP 注册失败，JsSIP 将自动重试:', err && err.message)
  })
}

const state = {
  // 工号（坐席ID）
  agentId: null,
  // 绑定账号（登录用户ID）
  userId: null,
  // 当前坐席状态（ai_call_agent_status 行）
  agent: null,
  // SIP 注册状态：offline | registering | registered | failed | connected | disconnected
  sipStatus: 'offline',
  // SIP 注册/连接的最新错误信息（便于排障）
  sipError: null,
  // 当前 SIP 通话信息（来电/去电）
  sipCall: null,
  // 拖拽拨号框显隐（由坐席状态栏"外呼"按钮控制）
  dialerVisible: false
}

const mutations = {
  SET_BINDING(state, binding) {
    state.agentId = binding && binding.agentId != null ? binding.agentId : null
    state.userId = binding && binding.userId != null ? binding.userId : null
  },
  SET_AGENT(state, agent) {
    state.agent = agent || null
  },
  SET_SIP_STATUS(state, status) {
    state.sipStatus = status
  },
  SET_SIP_ERROR(state, error) {
    state.sipError = error || null
  },
  SET_SIP_CALL(state, call) {
    state.sipCall = call || null
  },
  SET_DIALER_VISIBLE(state, visible) {
    state.dialerVisible = !!visible
  },
  CLEAR(state) {
    state.agent = null
    state.agentId = null
    state.userId = null
    state.sipStatus = 'offline'
    state.sipCall = null
  }
}

const actions = {
  // 页面初始化：恢复本机保存的 工号-账号 绑定，并拉取最新状态
  restore({ commit, dispatch }) {
    const binding = readBinding()
    if (binding && binding.agentId != null) {
      commit('SET_BINDING', binding)
      // 注册浏览器关闭/刷新时的兜底签出（仅在本地存在绑定时才有意义）
      bindUnloadHandler()
      return dispatch('refresh').then(agent => {
        // 仅当后端明确返回"已离线(status=0)"时才清理本地绑定；
        // 网络异常/接口超时等情况保留绑定，等待下次 refresh 恢复，
        // 避免因短暂网络抖动导致用户被误判为"未签入"。
        if (agent && agent.status === '0') {
          try { localStorage.removeItem(BINDING_KEY) } catch (e) {}
          commit('CLEAR')
        }
      }).catch(() => {})
    }
    return Promise.resolve()
  },

  // 只记录绑定，不重复调用签入接口（供既有签入流程复用）
  remember({ commit }, { agentId, userId }) {
    const binding = { agentId, userId }
    commit('SET_BINDING', binding)
    writeBinding(binding)
  },

  login({ commit, dispatch }, { agentId, userId, ip }) {
    return agentLogin({ agentId, userId, ip: ip || '' }).then(() => {
      const binding = { agentId, userId }
      commit('SET_BINDING', binding)
      writeBinding(binding)
      // 签入成功后注册浏览器关闭/刷新时的兜底签出
      bindUnloadHandler()
      return dispatch('refresh')
    })
  },

  logout({ commit, state }) {
    return agentLogout({ agentId: state.agentId, userId: state.userId }).then(() => {
      try { localStorage.removeItem(BINDING_KEY) } catch (e) {}
      callSocket.close()
      connectedUserId = null
      lastSipExtension = null
      const phone = getSipPhone()
      phone.terminate()
      phone.unregister()
      commit('CLEAR')
    })
  },

  clearBinding({ commit }) {
    try { localStorage.removeItem(BINDING_KEY) } catch (e) {}
    commit('CLEAR')
  },

  refresh({ commit, state }) {
    if (state.agentId == null) {
      return Promise.resolve(null)
    }
    return getCurrentAgent(state.agentId).then(res => {
      const agent = res.data || null
      commit('SET_AGENT', agent)
      ensureCallSocket(agent)
      ensureSipPhone(agent)
      return agent
    }).catch(() => {
      // 关键修复：网络抖动/接口超时/后端瞬断/401 时，保留上一次的坐席状态，
      // 不置为 null，避免 UI 立即显示"未签入"并顺带关闭 WS/SIP 分机。
      // 401 由全局 request 拦截器统一弹框处理重新登录，此处无需额外处理。
      // 下一次 5 秒轮询会自动重试拉取最新状态。
      return state.agent
    })
  },

  // 绑定 SIP 软电话事件（App.vue 初始化时调用一次）
  bindSipPhone({ commit, state }) {
    const phone = getSipPhone()
    phone.on('statusChange', (status) => {
      commit('SET_SIP_STATUS', status)
      commit('SET_SIP_ERROR', phone.lastError)
      // 仅在关键状态跃迁时提示，避免重连/轮询重复打扰
      if (status === 'registered' && lastNotifiedSipStatus !== 'registered') {
        lastNotifiedSipStatus = 'registered'
        Message({
          message: `SIP 分机 ${phone.extension || ''} 注册成功，可接听来电`,
          type: 'success',
          duration: 3000
        })
      } else if (status === 'registering') {
        // 注册中不弹通知，仅更新标签
      } else if (status === 'failed' && lastNotifiedSipStatus !== 'failed') {
        lastNotifiedSipStatus = 'failed'
        Message({
          message: `SIP 分机注册失败：${phone.lastError || '请检查 FreeSWITCH 连接'}`,
          type: 'error',
          duration: 5000
        })
      } else if (status === 'disconnected' && lastNotifiedSipStatus === 'registered') {
        lastNotifiedSipStatus = 'disconnected'
        Message({
          message: 'SIP 连接已断开，JsSIP 将自动重连',
          type: 'warning',
          duration: 4000
        })
      } else if (status === 'connected' || status === 'unregistered') {
        // 不弹通知
      }
    })
    phone.on('incomingCall', (info) => {
      commit('SET_SIP_CALL', { ...info, direction: 'incoming', state: 'ringing' })
    })
    phone.on('sessionAccepted', ({ isIncoming }) => {
      if (state.sipCall) {
        commit('SET_SIP_CALL', { ...state.sipCall, state: 'answered' })
      }
    })
    phone.on('sessionEnded', () => {
      commit('SET_SIP_CALL', null)
    })
    phone.on('sessionFailed', () => {
      commit('SET_SIP_CALL', null)
    })
  },

  // 接听浏览器 SIP 来电
  sipAnswer() {
    return getSipPhone().answer()
  },

  // 挂断浏览器 SIP 通话
  sipHangup() {
    return getSipPhone().terminate()
  },

  // 手动重连/注册
  sipReconnect({ state }) {
    if (state.agent && state.agent.sipExtension) {
      return getSipPhone().register({
        extension: state.agent.sipExtension,
        password: SIP_PASSWORD,
        domain: SIP_DOMAIN,
        wsUrl: SIP_WS_URL
      })
    }
    return Promise.reject(new Error('坐席未绑定分机'))
  },

  // 建立/关闭呼叫 WebSocket；由签入/刷新时调用
  ensureCallSocket({ state }, agent) {
    ensureCallSocket(agent || state.agent)
  },

  closeCallSocket() {
    callSocket.close()
  },

  setStatus({ dispatch, state }, status) {
    if (state.agentId == null) return Promise.resolve()
    return updateAgentStatus({ agentId: state.agentId, status }).then(() => dispatch('refresh'))
  },

  setCallMode({ dispatch, state }, callMode) {
    if (state.agentId == null) return Promise.resolve()
    return updateCallMode({ agentId: state.agentId, callMode }).then(() => dispatch('refresh'))
  },

  // 打开拖拽拨号框（由坐席状态栏"外呼"按钮触发）
  openDialer({ commit }) {
    commit('SET_DIALER_VISIBLE', true)
  },
  // 关闭拖拽拨号框
  closeDialer({ commit }) {
    commit('SET_DIALER_VISIBLE', false)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
