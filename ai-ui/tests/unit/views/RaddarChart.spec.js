'use strict'

/**
 * RaddarChart 雷达图单测：锁定指标配置、初始化主题与实例释放。
 */
import { mount } from '@vue/test-utils'
import * as echarts from 'echarts'
import RaddarChart from '@/views/dashboard/RaddarChart.vue'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn(),
    dispose: jest.fn()
  }))
}))

async function mountChart() {
  const wrapper = mount(RaddarChart)
  await wrapper.vm.$nextTick()
  const chart = echarts.init.mock.results[echarts.init.mock.results.length - 1].value
  return { wrapper, chart }
}

describe('RaddarChart', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('以 judicial 主题渲染六维雷达图', async () => {
    const { wrapper, chart } = await mountChart()

    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$el, 'judicial')
    const option = chart.setOption.mock.calls[0][0]
    expect(option.radar.indicator).toHaveLength(6)
    expect(option.legend.data).toEqual(['Allocated Budget', 'Expected Spending', 'Actual Spending'])
    expect(option.series[0].type).toBe('radar')
    expect(option.series[0].data).toHaveLength(3)
  })

  test('容器高度默认 300px，可自定义', async () => {
    const wrapper = mount(RaddarChart, { propsData: { height: '260px' } })
    await wrapper.vm.$nextTick()

    expect(wrapper.attributes('style')).toContain('height: 260px')
  })

  test('销毁时释放图表实例', async () => {
    const { wrapper, chart } = await mountChart()

    wrapper.destroy()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
