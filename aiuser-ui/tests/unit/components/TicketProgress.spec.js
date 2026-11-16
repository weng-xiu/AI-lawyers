'use strict'

/**
 * P5① 公众端组件单测：工单进度可视化
 * 覆盖：四节点渲染、状态映射（待处理/处理中/完成/归档）、非法状态兜底、aria-current 语义。
 */
import { mount } from '@vue/test-utils'
import TicketProgress from '@/components/TicketProgress'

const mountProgress = status => mount(TicketProgress, { propsData: { status } })

describe('TicketProgress', () => {
  it('固定渲染四个进度节点', () => {
    const wrapper = mountProgress('0')
    expect(wrapper.findAll('.tp-step')).toHaveLength(4)
    expect(wrapper.findAll('.tp-label').wrappers.map(w => w.text()))
      .toEqual(['已提交', '待受理', '办理中', '已办结'])
  })

  it('待处理状态定位到「待受理」节点', () => {
    const wrapper = mountProgress('0')
    expect(wrapper.vm.activeIndex).toBe(1)
    expect(wrapper.vm.currentLabel).toBe('待受理')
    expect(wrapper.attributes('aria-label')).toBe('工单进度：待受理')
    expect(wrapper.findAll('.tp-step').at(1).attributes('aria-current')).toBe('step')
  })

  it('处理中状态定位到「办理中」并把前序节点标记为已完成', () => {
    const wrapper = mountProgress('1')
    expect(wrapper.vm.activeIndex).toBe(2)
    const steps = wrapper.findAll('.tp-step')
    expect(steps.at(0).classes()).toContain('is-done')
    expect(steps.at(1).classes()).toContain('is-done')
    expect(steps.at(2).classes()).toContain('is-current')
    expect(steps.at(2).attributes('aria-current')).toBe('step')
  })

  it.each(['2', '3'])('已完成/已归档状态（%s）都定位到「已办结」', status => {
    const wrapper = mountProgress(status)
    expect(wrapper.vm.activeIndex).toBe(3)
    expect(wrapper.findAll('.tp-step').at(3).classes()).toContain('is-current')
    expect(wrapper.findAll('.tp-step').at(3).attributes('aria-current')).toBe('step')
  })

  it('未知状态兜底到「已提交」节点', () => {
    const wrapper = mountProgress('999')
    expect(wrapper.vm.activeIndex).toBe(0)
    expect(wrapper.vm.currentLabel).toBe('已提交')
  })

  it('数字状态与字符串状态等价', () => {
    const wrapper = mountProgress(2)
    expect(wrapper.vm.activeIndex).toBe(3)
  })
})
