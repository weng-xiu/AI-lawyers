'use strict'

/**
 * BarChart 柱状图单测：锁定主题初始化、堆叠配置、缩放与销毁。
 */
import { mount } from '@vue/test-utils'
import * as echarts from 'echarts'
import BarChart from '@/views/dashboard/BarChart.vue'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn(),
    dispose: jest.fn()
  }))
}))

async function mountChart() {
  const wrapper = mount(BarChart)
  await wrapper.vm.$nextTick()
  const chart = echarts.init.mock.results[echarts.init.mock.results.length - 1].value
  return { wrapper, chart }
}

describe('BarChart', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.useFakeTimers()
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  test('以 judicial 主题初始化并渲染三组堆叠柱', async () => {
    const { wrapper, chart } = await mountChart()

    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$el, 'judicial')
    const option = chart.setOption.mock.calls[0][0]
    expect(option.xAxis[0].data).toHaveLength(7)
    expect(option.series).toHaveLength(3)
    expect(option.series[0]).toMatchObject({ name: 'pageA', type: 'bar', stack: 'vistors' })
  })

  test('容器尺寸按 props 生效，窗口缩放时重绘', async () => {
    const wrapper = mount(BarChart, { propsData: { width: '50%', height: '280px', className: 'custom-chart' } })
    await wrapper.vm.$nextTick()
    const chart = echarts.init.mock.results[echarts.init.mock.results.length - 1].value

    expect(wrapper.classes()).toContain('custom-chart')
    expect(wrapper.attributes('style')).toContain('height: 280px')

    window.dispatchEvent(new Event('resize'))
    jest.advanceTimersByTime(100)
    expect(chart.resize).toHaveBeenCalled()
  })

  test('销毁时释放图表实例', async () => {
    const { wrapper, chart } = await mountChart()

    wrapper.destroy()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
