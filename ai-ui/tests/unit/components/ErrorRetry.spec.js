'use strict'

/**
 * ErrorRetry 错误态单测：锁定默认文案、详情条件渲染与重试事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ErrorRetry from '@/components/ErrorRetry/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('ErrorRetry', () => {
  test('默认展示错误文案与重试按钮，不显示详情', () => {
    const wrapper = mount(ErrorRetry, { localVue })

    expect(wrapper.find('.error-retry__text').text()).toBe('数据加载失败')
    expect(wrapper.find('.error-retry__detail').exists()).toBe(false)
    expect(wrapper.find('.error-retry__btn').text()).toBe('重新加载')
  })

  test('支持自定义文案与详情', () => {
    const wrapper = mount(ErrorRetry, {
      localVue,
      propsData: {
        message: '列表加载失败',
        detail: '请求超时',
        retryText: '再试一次'
      }
    })

    expect(wrapper.find('.error-retry__text').text()).toBe('列表加载失败')
    expect(wrapper.find('.error-retry__detail').text()).toBe('请求超时')
    expect(wrapper.find('.error-retry__btn').text()).toBe('再试一次')
  })

  test('点击重试按钮抛出 retry 事件', async () => {
    const wrapper = mount(ErrorRetry, { localVue })

    await wrapper.find('.error-retry__btn').trigger('click')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
