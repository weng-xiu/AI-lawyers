'use strict'

/**
 * 404 页面单测：锁定提示文案与返回首页入口。
 */
import { mount } from '@vue/test-utils'
import Page404 from '@/views/error/404.vue'

describe('Page404', () => {
  test('渲染 404 文案与返回首页链接', () => {
    const wrapper = mount(Page404, { stubs: { 'router-link': true } })

    expect(wrapper.vm.message).toBe('找不到网页！')
    expect(wrapper.text()).toContain('404错误!')
    expect(wrapper.text()).toContain('找不到网页！')
    expect(wrapper.find('.bullshit__return-home').exists()).toBe(true)
    expect(wrapper.findAll('.pic-404__child')).toHaveLength(3)
  })
})
