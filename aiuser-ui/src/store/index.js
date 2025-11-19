import Vue from 'vue'
import Vuex from 'vuex'
import { login as userLogin, getInfo, logout as userLogout } from '@/api/user'

Vue.use(Vuex)

export default new Vuex.Store({
  state: {
    userInfo: null,
    token: localStorage.getItem('token') || null
  },
  getters: {
    token: state => state.token,
    user: state => state.userInfo,
    isLoggedIn: state => !!state.token
  },
  mutations: {
    SET_USER_INFO(state, userInfo) {
      state.userInfo = userInfo
    },
    SET_TOKEN(state, token) {
      state.token = token
      localStorage.setItem('token', token)
    },
    CLEAR_USER(state) {
      state.userInfo = null
      state.token = null
      localStorage.removeItem('token')
    }
  },
  actions: {
    // 登录
    login({ commit }, loginForm) {
      return new Promise((resolve, reject) => {
        userLogin(loginForm).then(response => {
          const { token } = response
          commit('SET_TOKEN', token)
          resolve(response)
        }).catch(error => {
          reject(error)
        })
      })
    },
    
    // 获取用户信息
    getInfo({ commit, state }) {
      return new Promise((resolve, reject) => {
        getInfo().then(response => {
          const { user } = response
          
          // 验证用户类型，只允许普通用户('01')登录用户端
          if (user.userType !== '01') {
            commit('CLEAR_USER')
            reject(new Error('无权限访问用户端'))
            return
          }
          
          commit('SET_USER_INFO', user)
          resolve(response)
        }).catch(error => {
          reject(error)
        })
      })
    },
    
    // 登出
    logout({ commit }) {
      return new Promise((resolve, reject) => {
        userLogout().then(() => {
          commit('CLEAR_USER')
          resolve()
        }).catch(error => {
          commit('CLEAR_USER')
          reject(error)
        })
      })
    },
    
    // 重置token
    resetToken({ commit }) {
      return new Promise(resolve => {
        commit('CLEAR_USER')
        resolve()
      })
    }
  },
  modules: {
  }
})