import { agentLogin, agentLogout, getCurrentAgent, updateAgentStatus, updateCallMode } from '@/api/lawyers/callCenter'
import callSocket from '@/utils/callSocket'
import store from '@/store'

const BINDING_KEY = 'AI_AGENT_BINDING'

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

const state = {
  // 工号（坐席ID）
  agentId: null,
  // 绑定账号（登录用户ID）
  userId: null,
  // 当前坐席状态（ai_call_agent_status 行）
  agent: null
}

const mutations = {
  SET_BINDING(state, binding) {
    state.agentId = binding && binding.agentId != null ? binding.agentId : null
    state.userId = binding && binding.userId != null ? binding.userId : null
  },
  SET_AGENT(state, agent) {
    state.agent = agent || null
  },
  CLEAR(state) {
    state.agent = null
    state.agentId = null
    state.userId = null
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
      return agent
    }).catch(() => {
      commit('SET_AGENT', null)
      return null
    })
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
