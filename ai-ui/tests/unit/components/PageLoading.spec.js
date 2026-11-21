'use strict'

/**
 * PageLoading 骨架屏单测：锁定默认行数/动画与最小高度配置。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import PageLoading from '@/components/PageLoading/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('PageLoading', () => {
  test('默认渲染带最小高度的骨架屏', () => {
    const wrapper = mount(PageLoading, { localVue })

    expect(wrapper.find('.page-loading').attributes('style')).toContain('min-height: 180px')
    expect(wrapper.find('.el-skeleton').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'ElSkeleton' }).props('rows')).toBe(6)
    expect(wrapper.findComponent({ name: 'ElSkeleton' }).props('animated')).toBe(true)
  })

  test('支持自定义行数、动画与最小高度', () => {
    const wrapper = mount(PageLoading, {
      localVue,
      propsData: { rows: 3, animated: false, minHeight: '320px' }
    })

    const skeleton = wrapper.findComponent({ name: 'ElSkeleton' })
    expect(skeleton.props('rows')).toBe(3)
    expect(skeleton.props('animated')).toBe(false)
    expect(wrapper.find('.page-loading').attributes('style')).toContain('min-height: 320px')
  })
})
