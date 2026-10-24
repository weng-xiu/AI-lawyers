'use strict'

/**
 * U7 组件单测：GuideTour 新手引导
 * 覆盖：默认 4 步、步骤前进/回退边界、重新打开归零、完成/跳过事件、前往页面。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import GuideTour, { DEFAULT_GUIDE_STEPS } from '@/components/GuideTour/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountTour(propsData = {}) {
  const push = jest.fn(() => Promise.resolve())
  const wrapper = mount(GuideTour, {
    localVue,
    propsData: Object.assign({ visible: false }, propsData),
    mocks: {
      $route: { path: '/dashboard' },
      $router: { push }
    }
  })
  return { wrapper, push }
}

describe('GuideTour', () => {
  it('默认提供工作台/工单/巡场/知识库 4 步', () => {
    expect(DEFAULT_GUIDE_STEPS).toHaveLength(4)
    expect(DEFAULT_GUIDE_STEPS.map(s => s.path)).toEqual([
      '/lawyers/callCenter/callWorkbench',
      '/lawyers/callCenter/callTicket',
      '/lawyers/queueMonitor',
      '/lawyers/knowledge'
    ])
  })

  it('步骤前进与回退在边界内收敛', async () => {
    const { wrapper } = mountTour({ visible: true })
    expect(wrapper.vm.currentIndex).toBe(0)
    wrapper.vm.prev()
    expect(wrapper.vm.currentIndex).toBe(0)
    for (let i = 0; i < 10; i += 1) wrapper.vm.next()
    expect(wrapper.vm.currentIndex).toBe(3)
    expect(wrapper.vm.isLast).toBe(true)
    wrapper.vm.prev()
    expect(wrapper.vm.currentIndex).toBe(2)
  })

  it('重新打开时回到第 1 步', async () => {
    const { wrapper } = mountTour({ visible: true })
    wrapper.vm.next()
    expect(wrapper.vm.currentIndex).toBe(1)
    await wrapper.setProps({ visible: false })
    await wrapper.setProps({ visible: true })
    expect(wrapper.vm.currentIndex).toBe(0)
  })

  it('完成与跳过分别发出 done/skip 并关闭', async () => {
    const { wrapper } = mountTour({ visible: true })
    wrapper.vm.finish()
    expect(wrapper.emitted('done')).toHaveLength(1)
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false)

    wrapper.vm.handleSkip()
    expect(wrapper.emitted('skip')).toHaveLength(1)
  })

  it('「去这个页面看看」发出 navigate 并跳转路由', () => {
    const { wrapper, push } = mountTour({ visible: true })
    wrapper.vm.goStep()
    expect(wrapper.emitted('navigate')[0][0]).toBe('/lawyers/callCenter/callWorkbench')
    expect(push).toHaveBeenCalledWith('/lawyers/callCenter/callWorkbench')
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false)
  })

  it('当前页与目标一致时不重复跳转', () => {
    const { wrapper, push } = mountTour({ visible: true })
    wrapper.vm.$route.path = '/lawyers/callCenter/callWorkbench'
    wrapper.vm.goStep()
    expect(push).not.toHaveBeenCalled()
  })
})
