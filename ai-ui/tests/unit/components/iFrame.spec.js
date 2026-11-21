'use strict'

/**
 * iFrame 内嵌页单测：锁定 src 透传、初始高度、loading 消失与窗口缩放重算。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import IFrame from '@/components/iFrame/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('iFrame', () => {
  beforeEach(() => {
    jest.useFakeTimers()
  })

  afterEach(() => {
    jest.useRealTimers()
    window.onresize = null
  })

  test('渲染 iframe 并透传 src', () => {
    const wrapper = mount(IFrame, { localVue, propsData: { src: 'https://example.com' } })

    expect(wrapper.find('iframe').attributes('src')).toBe('https://example.com')
    expect(wrapper.find('iframe').attributes('frameborder')).toBe('no')
  })

  test('初始高度按视口高度扣除 94.5px', () => {
    const wrapper = mount(IFrame, { localVue, propsData: { src: 'https://example.com' } })

    const expected = document.documentElement.clientHeight - 94.5 + 'px;'
    expect(wrapper.vm.height).toBe(expected)
    expect(wrapper.attributes('style')).toContain(expected)
  })

  test('挂载 300ms 后结束 loading', () => {
    const wrapper = mount(IFrame, { localVue, propsData: { src: 'https://example.com' } })
    expect(wrapper.vm.loading).toBe(true)

    jest.advanceTimersByTime(300)
    expect(wrapper.vm.loading).toBe(false)
  })

  test('窗口缩放时重新计算高度', () => {
    const wrapper = mount(IFrame, { localVue, propsData: { src: 'https://example.com' } })
    expect(typeof window.onresize).toBe('function')

    const originalHeight = document.documentElement.clientHeight
    Object.defineProperty(document.documentElement, 'clientHeight', {
      configurable: true,
      value: 1000
    })

    window.onresize()

    expect(wrapper.vm.height).toBe('905.5px;')

    Object.defineProperty(document.documentElement, 'clientHeight', {
      configurable: true,
      value: originalHeight
    })
  })
})
