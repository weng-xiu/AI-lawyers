import Vue from 'vue'
import store from '@/store'
import http from '@/utils/http'

/**
 * F2 适老化关怀模式（P0）
 *
 * - 三档字号：normal(基准14px) / large(大字18px) / xlarge(超大22px)
 * - 高对比主题：文字/背景对比度 >= 4.5:1
 * - 点击热区：主要控件 >= 44px
 * - localStorage 本地持久化；登录态下自动同步到后端（/lawyers/portal/preference），
 *   坐席端弹屏也能看到并维护该偏好。
 */

const STORAGE_KEY = 'ai-lawyers-care-mode'
const LANG_KEY = 'ai-lawyers-language'

export const FONT_SCALES = [
  { value: 'normal', label: '标准', size: '14px' },
  { value: 'large', label: '大字', size: '18px' },
  { value: 'xlarge', label: '超大', size: '22px' }
]

export const state = Vue.observable({
  fontScale: 'normal',
  highContrast: false,
  languagePreference: localStorage.getItem(LANG_KEY) || 'zh-CN',
  ready: false
})

function readLocal() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const obj = JSON.parse(raw)
      if (FONT_SCALES.some(f => f.value === obj.fontScale)) state.fontScale = obj.fontScale
      state.highContrast = !!obj.highContrast
    }
  } catch (e) { /* 忽略损坏的本地配置 */ }
}

function persist() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    fontScale: state.fontScale,
    highContrast: state.highContrast
  }))
  localStorage.setItem(LANG_KEY, state.languagePreference)
}

/** 将关怀模式应用到 <html> 根节点，全局 CSS 据此放大 ElementUI 组件 */
export function applyCareMode() {
  const el = document.documentElement
  el.classList.remove('care-large', 'care-xlarge', 'care-high-contrast')
  if (state.fontScale === 'large') el.classList.add('care-large')
  if (state.fontScale === 'xlarge') el.classList.add('care-xlarge')
  if (state.highContrast) el.classList.add('care-high-contrast')
  el.setAttribute('data-font-scale', state.fontScale)
  el.setAttribute('data-high-contrast', String(state.highContrast))
}

/** 登录后把本地偏好同步到服务端（失败静默，不影响正常使用） */
export function syncPreferenceToServer() {
  if (!store.getters.token) return
  return http({
    url: '/lawyers/portal/preference',
    method: 'post',
    data: {
      languagePreference: state.languagePreference,
      careMode: state.highContrast || state.fontScale !== 'normal' ? 1 : 0
    }
  }).catch(() => {})
}

export function setFontScale(value) {
  if (!FONT_SCALES.some(f => f.value === value)) return
  state.fontScale = value
  persist()
  applyCareMode()
  syncPreferenceToServer()
}

export function setHighContrast(enabled) {
  state.highContrast = !!enabled
  persist()
  applyCareMode()
  syncPreferenceToServer()
}

export function setLanguage(lang) {
  state.languagePreference = lang === 'yue-CN' ? 'yue-CN' : 'zh-CN'
  persist()
  syncPreferenceToServer()
}

export function initCareMode() {
  if (state.ready) return
  readLocal()
  applyCareMode()
  state.ready = true
}

export default {
  install(VueInstance) {
    initCareMode()
    VueInstance.prototype.$care = {
      state,
      fontScales: FONT_SCALES,
      setFontScale,
      setHighContrast,
      setLanguage,
      syncPreferenceToServer
    }
  }
}
