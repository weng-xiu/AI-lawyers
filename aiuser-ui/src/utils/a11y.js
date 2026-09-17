/**
 * F2 适老化无障碍工具（P0）
 *
 * - speak：基于浏览器原生 SpeechSynthesis 的语音播报（语音验证码/语音求助引导），
 *   无需第三方依赖、无后端 TTS；不支持的浏览器优雅降级。
 * - announce：向全局 aria-live 区域写入文本，供读屏软件（NVDA/VoiceOver）播报
 *   咨询结果、排队状态、错误提示等动态变化。
 */

export const speechSupported = typeof window !== 'undefined' && 'speechSynthesis' in window

/**
 * 语音朗读
 * @param {string} text 待朗读文本
 * @param {object} opts { interrupt: 是否打断当前朗读，默认 true；lang: 默认中文；rate/pitch }
 */
export function speak(text, opts = {}) {
  if (!speechSupported || !text) return false
  const { interrupt = true, lang = 'zh-CN', rate = 0.95, pitch = 1 } = opts
  try {
    if (interrupt) window.speechSynthesis.cancel()
    const utter = new SpeechSynthesisUtterance(String(text))
    utter.lang = lang
    utter.rate = rate
    utter.pitch = pitch
    // 优先选取中文语音
    const voices = window.speechSynthesis.getVoices()
    const zh = voices.find(v => /^zh(-|_)?/i.test(v.lang))
    if (zh) utter.voice = zh
    window.speechSynthesis.speak(utter)
    return true
  } catch (e) {
    return false
  }
}

/** 停止朗读 */
export function stopSpeak() {
  if (speechSupported) window.speechSynthesis.cancel()
}

/**
 * 向全局 aria-live 播报区写文本（#a11y-announcer，由 App.vue 提供）
 * @param {string} message
 * @param {'polite'|'assertive'} politeness 错误/紧急用 assertive，普通动态用 polite
 */
export function announce(message, politeness = 'polite') {
  if (!message) return
  const region = document.getElementById('a11y-announcer')
  if (!region) return
  const target = politeness === 'assertive'
    ? region.querySelector('[data-role="assertive"]')
    : region.querySelector('[data-role="polite"]')
  if (!target) return
  // 先清空再写，确保读屏器在连续相同内容时也会重新播报
  target.textContent = ''
  // 下一帧写入，兼容多数读屏器的变化检测
  requestAnimationFrame(() => { target.textContent = String(message) })
}

export default { speak, stopSpeak, announce, speechSupported }
