'use strict'

/**
 * LineChart 折线图单测：锁定数据驱动配置、数据更新与销毁。
 */
import { mount } from '@vue/test-utils'
import * as echarts from 'echarts'
import LineChart from '@/views/dashboard/LineChart.vue'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn(),
    dispose: jest.fn()
  }))
}))

const chartData = {
  expectedData: [100, 120, 161, 134, 105, 160, 165],
  actualData: [120, 82, 91, 154, 162, 140, 145]
}

async function mountChart(data = chartData) {
  const wrapper = mount(LineChart, { propsData: { chartData: data } })
  await wrapper.vm.$nextTick()
  const chart = echarts.init.mock.results[echarts.init.mock.results.length - 1].value
  return { wrapper, chart }
}

describe('LineChart', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('以 judicial 主题按 chartData 绘制双折线', async () => {
    const { chart } = await mountChart()

    expect(echarts.init).toHaveBeenCalledWith(expect.anything(), 'judicial')
    const option = chart.setOption.mock.calls[0][0]
    expect(option.legend.data).toEqual(['expected', 'actual'])
    expect(option.series[0]).toMatchObject({ name: 'expected', type: 'line', data: chartData.expectedData })
    expect(option.series[1]).toMatchObject({ name: 'actual', type: 'line', data: chartData.actualData })
  })

  test('chartData 更新后重新设置配置', async () => {
    const { wrapper, chart } = await mountChart()
    chart.setOption.mockClear()

    await wrapper.setProps({ chartData: { expectedData: [1, 2], actualData: [3, 4] } })

    expect(chart.setOption).toHaveBeenCalledTimes(1)
    const option = chart.setOption.mock.calls[0][0]
    expect(option.series[0].data).toEqual([1, 2])
    expect(option.series[1].data).toEqual([3, 4])
  })

  test('销毁时释放图表实例', async () => {
    const { wrapper, chart } = await mountChart()

    wrapper.destroy()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
