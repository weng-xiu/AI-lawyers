'use strict'

/**
 * IndexV1 仪表盘单测：锁定图表数据切换逻辑。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import IndexV1 from '@/views/index_v1.vue'

jest.mock('@/views/dashboard/PanelGroup', () => ({
  __esModule: true,
  default: { name: 'PanelGroup', render: h => h('div', { class: 'panel-group-stub' }) }
}))
jest.mock('@/views/dashboard/LineChart', () => ({
  __esModule: true,
  default: { name: 'LineChart', render: h => h('div', { class: 'line-chart-stub' }) }
}))
jest.mock('@/views/dashboard/RaddarChart', () => ({
  __esModule: true,
  default: { name: 'RaddarChart', render: h => h('div', { class: 'raddar-chart-stub' }) }
}))
jest.mock('@/views/dashboard/PieChart', () => ({
  __esModule: true,
  default: { name: 'PieChart', render: h => h('div', { class: 'pie-chart-stub' }) }
}))
jest.mock('@/views/dashboard/BarChart', () => ({
  __esModule: true,
  default: { name: 'BarChart', render: h => h('div', { class: 'bar-chart-stub' }) }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('IndexV1', () => {
  test('默认展示访客数据并渲染全部图表组件', () => {
    const wrapper = mount(IndexV1, { localVue })

    expect(wrapper.vm.lineChartData.expectedData[0]).toBe(100)
    expect(wrapper.find('.panel-group-stub').exists()).toBe(true)
    expect(wrapper.find('.line-chart-stub').exists()).toBe(true)
    expect(wrapper.find('.raddar-chart-stub').exists()).toBe(true)
    expect(wrapper.find('.pie-chart-stub').exists()).toBe(true)
    expect(wrapper.find('.bar-chart-stub').exists()).toBe(true)
  })

  test('切换指标时同步折线图数据', async () => {
    const wrapper = mount(IndexV1, { localVue })

    wrapper.vm.handleSetLineChartData('messages')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.lineChartData.expectedData[0]).toBe(200)

    wrapper.vm.handleSetLineChartData('shoppings')
    expect(wrapper.vm.lineChartData.actualData[6]).toBe(130)
  })

  test('组件名为 Index', () => {
    const wrapper = mount(IndexV1, { localVue })

    expect(wrapper.vm.$options.name).toBe('Index')
  })
})
