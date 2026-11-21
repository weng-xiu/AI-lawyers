'use strict'

/**
 * PanThumb 头像组件单测：锁定尺寸/层级样式、背景图与插槽内容渲染。
 */
import { mount } from '@vue/test-utils'
import PanThumb from '@/components/PanThumb/index.vue'

describe('PanThumb', () => {
  test('默认尺寸与层级应用在容器上', () => {
    const wrapper = mount(PanThumb, {
      propsData: { image: 'http://example.com/avatar.png' }
    })

    const style = wrapper.find('.pan-item').attributes('style')
    expect(style).toContain('height: 150px')
    expect(style).toContain('width: 150px')
    expect(style).toContain('z-index: 1')
  })

  test('支持自定义尺寸与层级', () => {
    const wrapper = mount(PanThumb, {
      propsData: { image: 'avatar.png', width: '200px', height: '240px', zIndex: 9 }
    })

    const style = wrapper.find('.pan-item').attributes('style')
    expect(style).toContain('height: 240px')
    expect(style).toContain('width: 200px')
    expect(style).toContain('z-index: 9')
  })

  test('图片渲染为背景图', () => {
    const wrapper = mount(PanThumb, {
      propsData: { image: 'http://example.com/avatar.png' }
    })

    expect(wrapper.find('.pan-thumb').attributes('style')).toContain('url(http://example.com/avatar.png)')
  })

  test('默认插槽内容渲染在信息层', () => {
    const wrapper = mount(PanThumb, {
      propsData: { image: 'avatar.png' },
      slots: { default: '<h3>张三</h3><p>律师</p>' }
    })

    expect(wrapper.find('.pan-info h3').text()).toBe('张三')
    expect(wrapper.find('.pan-info p').text()).toBe('律师')
  })
})
