'use strict'

/**
 * Index 首页单测：锁定欢迎区、功能卡片与版本信息。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Index from '@/views/index.vue'

// 首页仅负责布局，咨询子组件单独测试；此处隔离其接口依赖
jest.mock('@/components/LegalConsultation', () => ({
  __esModule: true,
  default: { name: 'LegalConsultation', render: h => h('div', { class: 'legal-consultation-stub' }) }
}))
jest.mock('@/components/ConsultationHistory', () => ({
  __esModule: true,
  default: { name: 'ConsultationHistory', render: h => h('div', { class: 'consultation-history-stub' }) }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountIndex() {
  return mount(Index, { localVue })
}

describe('Index', () => {
  test('渲染欢迎区与咨询组件', () => {
    const wrapper = mountIndex()

    expect(wrapper.text()).toContain('AI法律咨询系统')
    expect(wrapper.text()).toContain('智能法律咨询，为您提供专业的法律建议')
    expect(wrapper.find('.legal-consultation-stub').exists()).toBe(true)
    expect(wrapper.find('.consultation-history-stub').exists()).toBe(true)
  })

  test('展示系统功能与版本号', () => {
    const wrapper = mountIndex()

    expect(wrapper.text()).toContain('智能法律咨询')
    expect(wrapper.text()).toContain('咨询记录管理')
    expect(wrapper.text()).toContain('使用指南')
    expect(wrapper.text()).toContain('v1.0.0')
  })

  test('跳转外链时新窗口打开', () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => {})
    const wrapper = mountIndex()

    wrapper.vm.goTarget('https://example.com')

    expect(open).toHaveBeenCalledWith('https://example.com', '_blank')
    open.mockRestore()
  })
})
