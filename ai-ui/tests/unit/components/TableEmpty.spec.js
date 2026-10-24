'use strict'

/**
 * U7 组件单测：TableEmpty 空态引导动作
 * 覆盖：文案/提示展示、无动作文案时不渲染按钮、动作按钮事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TableEmpty from '@/components/TableEmpty/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('TableEmpty', () => {
  it('展示主文案与下一步提示', () => {
    const wrapper = mount(TableEmpty, {
      localVue,
      propsData: { text: '知识库还没有内容', hint: '使用「导入」批量建库' }
    })
    expect(wrapper.find('.table-empty__text').text()).toBe('知识库还没有内容')
    expect(wrapper.find('.table-empty__hint').text()).toBe('使用「导入」批量建库')
  })

  it('未配置动作文案时不渲染动作按钮', () => {
    const wrapper = mount(TableEmpty, { localVue, propsData: { text: '暂无数据' } })
    expect(wrapper.find('.table-empty__action').exists()).toBe(false)
  })

  it('配置动作文案后点击发出 action 事件', async () => {
    const wrapper = mount(TableEmpty, {
      localVue,
      propsData: { text: '还没有外呼任务', actionText: '新建外呼任务', actionIcon: 'el-icon-plus' }
    })
    const action = wrapper.find('.table-empty__action')
    expect(action.exists()).toBe(true)
    expect(action.text()).toBe('新建外呼任务')
    await action.trigger('click')
    expect(wrapper.emitted('action')).toHaveLength(1)
  })
})
