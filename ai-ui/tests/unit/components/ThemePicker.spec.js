'use strict'

/**
 * ThemePicker 主题切换单测：锁定主题同步、色阶计算、样式替换与 change 事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ThemePicker from '@/components/ThemePicker/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

// 组件通过 XHR 拉取 theme-chalk 样式，这里用同步返回的桩替代真实请求
class MockXHR {
  open() {}
  send() {
    this.readyState = 4
    this.status = 200
    this.responseText = 'body{color:#1a3c6e}'
    this.onreadystatechange()
  }
}

function mountThemePicker(theme = '#1A3C6E') {
  return mount(ThemePicker, {
    localVue,
    mocks: { $store: { state: { settings: { theme } } } }
  })
}

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

describe('ThemePicker', () => {
  beforeEach(() => {
    global.XMLHttpRequest = MockXHR
  })

  afterEach(() => {
    const tag = document.getElementById('chalk-style')
    if (tag && tag.parentNode) tag.parentNode.removeChild(tag)
  })

  test('挂载时同步 store 主题并透传预置颜色', async () => {
    const wrapper = mountThemePicker('#f5222d')
    await flush()

    expect(wrapper.vm.theme).toBe('#f5222d')
    const picker = wrapper.findComponent({ name: 'ElColorPicker' })
    expect(picker.exists()).toBe(true)
    expect(picker.props('predefine')).toHaveLength(8)
  })

  test('getThemeCluster 生成 12 段主题色阶', async () => {
    const wrapper = mountThemePicker('#1A3C6E')
    await flush()

    const clusters = wrapper.vm.getThemeCluster('1890ff')

    expect(clusters).toHaveLength(12)
    expect(clusters[0]).toBe('1890ff')
    expect(clusters[1]).toBe('24,144,255')
    expect(clusters[2]).toBe('#2f9bff')
    expect(clusters[11]).toBe('#1682e6')
  })

  test('updateStyle 按色阶批量替换颜色', async () => {
    const wrapper = mountThemePicker('#1A3C6E')
    await flush()

    const oldCluster = wrapper.vm.getThemeCluster('1890ff')
    const newCluster = wrapper.vm.getThemeCluster('f5222d')

    expect(wrapper.vm.updateStyle('a{color:#1890ff}', oldCluster, newCluster)).toBe('a{color:#f5222d}')
  })

  test('非字符串主题值直接忽略', async () => {
    const wrapper = mountThemePicker('#1A3C6E')
    await flush()

    const before = (wrapper.emitted('change') || []).length
    await wrapper.vm.setTheme(null)

    expect((wrapper.emitted('change') || []).length).toBe(before)
  })

  test('设置主题时生成样式标签并抛出 change 事件', async () => {
    const wrapper = mountThemePicker('#1A3C6E')
    await flush()

    await wrapper.vm.setTheme('#1890ff')

    const styleTag = document.getElementById('chalk-style')
    expect(styleTag).toBeTruthy()
    expect(styleTag.innerText).toContain('1890ff')
    expect(wrapper.emitted('change').pop()).toEqual(['#1890ff'])
  })
})
