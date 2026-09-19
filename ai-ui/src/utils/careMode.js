import Vue from 'vue'

/**
 * F2 坐席端关怀模式（第十五部分 4.2 第 4 项，V2.11）
 *
 * - 三档字号：normal(基准14px) / large(大字18px) / xlarge(超大22px)
 * - 高对比主题：正文/控件对比度 >= 4.5:1
 * - 主要控件点击热区 >= 44px
 * - localStorage 按工位浏览器持久化，刷新/重登自动恢复
 *
 * 与公众端 aiuser-ui/utils/careMode.js 的边界：
 * - 存储键独立（ai-ui-care-mode），坐席工位机与公众 H5 互不影响；
 * - 不调用 /lawyers/portal/preference 同步后端：该接口按"公众用户手机号→
 *   来电人档案 AiCallerProfile"语义写入，坐席 sys_user 无对应档案；
 *   坐席显示偏好属工位机/浏览器设置（含投屏场景），本地持久化即可。
 * - 弹屏中"关怀模式（F2）"开关是来电人档案标记（profileForm.careMode），
 *   服务于来电人，与本工具（坐席本人屏幕显示）是两个维度，互不干扰。
 */

const STORAGE_KEY = 'ai-ui-care-mode'

export const FONT_SCALES = [
  { value: 'normal', label: '标准', size: '14px' },
  { value: 'large', label: '大字', size: '18px' },
  { value: 'xlarge', label: '超大', size: '22px' }
]

export const state = Vue.observable({
  fontScale: 'normal',
  highContrast: false,
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
}

/** 将关怀模式应用到 <html> 根节点，全局 CSS（care-mode.scss）据此放大 ElementUI 组件 */
export function applyCareMode() {
  const el = document.documentElement
  el.classList.remove('care-large', 'care-xlarge', 'care-high-contrast')
  if (state.fontScale === 'large') el.classList.add('care-large')
  if (state.fontScale === 'xlarge') el.classList.add('care-xlarge')
  if (state.highContrast) el.classList.add('care-high-contrast')
  el.setAttribute('data-font-scale', state.fontScale)
  el.setAttribute('data-high-contrast', String(state.highContrast))
}

export function setFontScale(value) {
  if (!FONT_SCALES.some(f => f.value === value)) return
  state.fontScale = value
  persist()
  applyCareMode()
}

export function setHighContrast(enabled) {
  state.highContrast = !!enabled
  persist()
  applyCareMode()
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
      setHighContrast
    }
  }
}
