'use strict'

/**
 * 坐席关怀模式单测：锁定字号/高对比/主题对 html 根节点类名与属性的作用、
 * localStorage 持久化与刷新恢复，避免投屏工位机显示偏好丢失。
 */
import careMode, {
  FONT_SCALES,
  THEMES,
  state,
  applyCareMode,
  setFontScale,
  setHighContrast,
  setTheme,
  initCareMode
} from '@/utils/careMode'

const STORAGE_KEY = 'ai-ui-care-mode'

function htmlClasses() {
  return Array.from(document.documentElement.classList)
}

beforeEach(() => {
  localStorage.clear()
  state.fontScale = 'normal'
  state.highContrast = false
  state.theme = 'light'
  state.ready = false
  applyCareMode()
})

describe('关怀模式常量', () => {
  test('三档字号与两套主题配置', () => {
    expect(FONT_SCALES.map(item => item.value)).toEqual(['normal', 'large', 'xlarge'])
    expect(FONT_SCALES.map(item => item.size)).toEqual(['14px', '18px', '22px'])
    expect(THEMES.map(item => item.value)).toEqual(['light', 'dark'])
  })
})

describe('设置项应用与持久化', () => {
  test('切换大字/超大字号写入类名与数据属性', () => {
    setFontScale('large')
    expect(state.fontScale).toBe('large')
    expect(htmlClasses()).toContain('care-large')
    expect(document.documentElement.getAttribute('data-font-scale')).toBe('large')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)).fontScale).toBe('large')

    setFontScale('xlarge')
    expect(htmlClasses()).toContain('care-xlarge')
    expect(htmlClasses()).not.toContain('care-large')
  })

  test('非法字号直接忽略', () => {
    setFontScale('huge')

    expect(state.fontScale).toBe('normal')
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })

  test('高对比开关同步类名与属性', () => {
    setHighContrast(true)
    expect(htmlClasses()).toContain('care-high-contrast')
    expect(document.documentElement.getAttribute('data-high-contrast')).toBe('true')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)).highContrast).toBe(true)

    setHighContrast(0)
    expect(htmlClasses()).not.toContain('care-high-contrast')
    expect(document.documentElement.getAttribute('data-high-contrast')).toBe('false')
  })

  test('主题切换与非法值忽略', () => {
    setTheme('dark')
    expect(state.theme).toBe('dark')
    expect(htmlClasses()).toContain('theme-dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')

    setTheme('blue')
    expect(state.theme).toBe('dark')
  })
})

describe('刷新恢复', () => {
  test('initCareMode 读取本地配置并应用一次', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      fontScale: 'xlarge',
      highContrast: true,
      theme: 'dark'
    }))

    initCareMode()

    expect(state).toMatchObject({ fontScale: 'xlarge', highContrast: true, theme: 'dark', ready: true })
    expect(htmlClasses()).toEqual(expect.arrayContaining(['care-xlarge', 'care-high-contrast', 'theme-dark']))

    // ready 后不再重复读取
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ fontScale: 'normal' }))
    initCareMode()
    expect(state.fontScale).toBe('xlarge')
  })

  test('本地配置损坏时回落到默认值', () => {
    localStorage.setItem(STORAGE_KEY, '{bad json')

    initCareMode()

    expect(state).toMatchObject({ fontScale: 'normal', highContrast: false, theme: 'light' })
  })

  test('本地配置含非法枚举值时忽略对应字段', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      fontScale: 'huge',
      highContrast: false,
      theme: 'blue'
    }))

    initCareMode()

    expect(state.fontScale).toBe('normal')
    expect(state.theme).toBe('light')
  })
})

describe('Vue 插件安装', () => {
  test('install 注入 $care 并立即初始化', () => {
    function FakeVue() {}
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ fontScale: 'large' }))

    careMode.install(FakeVue)

    expect(FakeVue.prototype.$care).toEqual(expect.objectContaining({
      state,
      fontScales: FONT_SCALES,
      themes: THEMES
    }))
    expect(FakeVue.prototype.$care.setFontScale).toBe(setFontScale)
    expect(state.fontScale).toBe('large')
    expect(htmlClasses()).toContain('care-large')
  })
})
