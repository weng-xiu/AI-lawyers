'use strict'

/**
 * SvgIcon 组件单测：锁定内部图标 use 引用、外部图标遮罩渲染与事件透传。
 */
import { mount } from '@vue/test-utils'
import SvgIcon from '@/components/SvgIcon/index.vue'

describe('SvgIcon', () => {
  test('内部图标渲染 svg 并拼接 #icon- 前缀', () => {
    const wrapper = mount(SvgIcon, { propsData: { iconClass: 'dashboard' } })

    const svg = wrapper.find('svg')
    expect(svg.exists()).toBe(true)
    expect(svg.classes()).toContain('svg-icon')
    expect(svg.find('use').element.getAttribute('xlink:href')).toBe('#icon-dashboard')
  })

  test('传入 className 时追加自定义类名', () => {
    const wrapper = mount(SvgIcon, { propsData: { iconClass: 'user', className: 'custom-icon' } })

    expect(wrapper.find('svg').classes()).toEqual(expect.arrayContaining(['svg-icon', 'custom-icon']))
  })

  test('外部图标改为遮罩 div 渲染', () => {
    const wrapper = mount(SvgIcon, { propsData: { iconClass: 'http://example.com/logo.svg' } })

    expect(wrapper.find('svg').exists()).toBe(false)
    const external = wrapper.find('.svg-external-icon')
    expect(external.exists()).toBe(true)
    expect(external.attributes('style')).toContain('url(http://example.com/logo.svg)')
  })

  test('透传原生点击事件', () => {
    const onClick = jest.fn()
    const wrapper = mount(SvgIcon, {
      propsData: { iconClass: 'bell' },
      listeners: { click: onClick }
    })

    wrapper.trigger('click')

    expect(onClick).toHaveBeenCalledTimes(1)
  })
})
