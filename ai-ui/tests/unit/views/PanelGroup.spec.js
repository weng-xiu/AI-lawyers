'use strict'

/**
 * PanelGroup 指标卡单测：锁定四个入口文案与点击联动事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import PanelGroup from '@/views/dashboard/PanelGroup.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

function mountPanel() {
  return mount(PanelGroup, {
    localVue,
    stubs: { 'count-to': true }
  })
}

describe('PanelGroup', () => {
  test('渲染访客、消息、金额、订单四张卡片', () => {
    const wrapper = mountPanel()

    const texts = wrapper.findAll('.card-panel-text').wrappers.map(item => item.text())
    expect(texts).toEqual(['访客', '消息', '金额', '订单'])
    expect(wrapper.findAll('.card-panel')).toHaveLength(4)
  })

  test.each([
    [0, 'newVisitis'],
    [1, 'messages'],
    [2, 'purchases'],
    [3, 'shoppings']
  ])('点击第 %i 张卡片抛出 %s 事件', (index, type) => {
    const wrapper = mountPanel()

    wrapper.findAll('.card-panel').at(index).trigger('click')

    expect(wrapper.emitted('handleSetLineChartData').pop()).toEqual([type])
  })
})
