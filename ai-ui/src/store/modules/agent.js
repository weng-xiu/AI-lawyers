import { agentLogin, agentLogout, getCurrentAgent, updateAgentStatus, updateCallMode } from '@/api/lawyers/callCenter'
import callSocket from '@/utils/callSocket'
import { getSipPhone } from '@/utils/webrtcSipPhone'
import { Message } from 'element-ui'
import store from '@/store'

// 上一次已通知用户的 SIP 注册状态，避免重连/轮询时重复弹提示
let lastNotifiedSipStatus = 'offline'

const BINDING_KEY = 'AI_AGENT_BINDING'
// SIP 分机注册配置（FreeSWITCH internal ws 端口 5066）
const SIP_WS_URL = process.env.VUE_APP_SIP_WS_URL || 'ws://198.18.0.1:5066'
const SIP_DOMAIN = process.env.VUE_APP_SIP_DOMAIN || '198.18.0.1'
const SIP_PASSWORD = process.env.VUE_APP_SIP_PASSWORD || '1234'

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

// 根据坐席记录启动/关闭浏览器侧 SIP 分机（WebRTC）
let sipRegisterPromise = null
function ensureSipPhone(agent) {
  const phone = getSipPhone()
  if (!agent || agent.status === '0' || !agent.sipExtension) {
    phone.unregister()
    sipRegisterPromise = null
    return
  }
  // 已注册到同一分机则跳过
  if (phone.registered && phone.extension === agent.sipExtension) return
  // 正在注册中（registering/connected），复用同一个 Promise，避免重入杀掉正在握手的连接
  if (sipRegisterPromise && phone.extension === agent.sipExtension &&
      (phone.registrationStatus === 'registering' || phone.registrationStatus === 'connected')) {
    return
  }
  sipRegisterPromise = phone.register({
    extension: agent.sipExtension,
    password: SIP_PASSWORD,
    domain: SIP_DOMAIN,
    wsUrl: SIP_WS_URL
  }).catch(err => {
    console.warn('[agent-store] SIP 自动注册失败:', err && err.message)
  }).then(() => {
    // 注册成功/失败后允许后续重试
    setTimeout(() => { sipRegisterPromise = null }, 2000)
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
  sipCall: null
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
      return dispatch('refresh').then(agent => {
        // 坐席已被签出或记录不存在时，清理本地绑定
        if (!agent || agent.status === '0') {
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
      return dispatch('refresh')
    })
  },

  logout({ commit, state }) {
    return agentLogout({ agentId: state.agentId, userId: state.userId }).then(() => {
      try { localStorage.removeItem(BINDING_KEY) } catch (e) {}
      callSocket.close()
      connectedUserId = null
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
      commit('SET_AGENT', null)
      return null
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
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
