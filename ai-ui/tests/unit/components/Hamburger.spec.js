'use strict'

/**
 * Hamburger 折叠按钮单测：锁定激活态旋转类名与 toggleClick 事件。
 */
import { mount } from '@vue/test-utils'
import Hamburger from '@/components/Hamburger/index.vue'

describe('Hamburger', () => {
  test('默认未激活时不带 is-active 类名', () => {
    const wrapper = mount(Hamburger)

    expect(wrapper.find('svg.hamburger').exists()).toBe(true)
    expect(wrapper.find('svg').classes()).not.toContain('is-active')
  })

  test('isActive 为真时附加 is-active 类名', () => {
    const wrapper = mount(Hamburger, { propsData: { isActive: true } })

    expect(wrapper.find('svg').classes()).toContain('is-active')
  })

  test('点击按钮抛出 toggleClick 事件', async () => {
    const wrapper = mount(Hamburger)

    await wrapper.trigger('click')

    expect(wrapper.emitted('toggleClick')).toHaveLength(1)
  })
})
