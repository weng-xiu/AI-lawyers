'use strict'

/**
 * Screenfull 全屏按钮单测：锁定全屏能力检测、change 监听注册/注销与图标切换。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import screenfull from 'screenfull'
import Screenfull from '@/components/Screenfull/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

jest.mock('screenfull', () => ({
  isEnabled: true,
  isFullscreen: false,
  toggle: jest.fn(),
  on: jest.fn(),
  off: jest.fn()
}))

const localVue = createLocalVue()
localVue.component('svg-icon', SvgIcon)

function mountScreenfull() {
  const message = jest.fn()
  const wrapper = mount(Screenfull, {
    localVue,
    mocks: { $message: message }
  })
  return { wrapper, message }
}

describe('Screenfull', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    screenfull.isEnabled = true
    screenfull.isFullscreen = false
  })

  test('挂载时注册 change 监听并渲染全屏图标', () => {
    const { wrapper } = mountScreenfull()

    expect(screenfull.on).toHaveBeenCalledWith('change', expect.any(Function))
    expect(wrapper.vm.isFullscreen).toBe(false)
    expect(wrapper.find('use').element.getAttribute('xlink:href')).toBe('#icon-fullscreen')
  })

  test('浏览器支持时点击调用 toggle', async () => {
    const { wrapper, message } = mountScreenfull()

    await wrapper.find('svg').trigger('click')

    expect(screenfull.toggle).toHaveBeenCalledTimes(1)
    expect(message).not.toHaveBeenCalled()
  })

  test('浏览器不支持全屏时提示警告且不调用 toggle', async () => {
    screenfull.isEnabled = false
    const { wrapper, message } = mountScreenfull()

    expect(screenfull.on).not.toHaveBeenCalled()
    await wrapper.find('svg').trigger('click')

    expect(screenfull.toggle).not.toHaveBeenCalled()
    expect(message).toHaveBeenCalledWith({ message: '你的浏览器不支持全屏', type: 'warning' })
  })

  test('change 回调同步全屏状态并切换为退出图标', async () => {
    const { wrapper } = mountScreenfull()

    screenfull.isFullscreen = true
    wrapper.vm.change()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.isFullscreen).toBe(true)
    expect(wrapper.find('use').element.getAttribute('xlink:href')).toBe('#icon-exit-fullscreen')
  })

  test('销毁时移除 change 监听', () => {
    const { wrapper } = mountScreenfull()
    const handler = screenfull.on.mock.calls[0][1]

    wrapper.destroy()

    expect(screenfull.off).toHaveBeenCalledWith('change', handler)
  })
})
