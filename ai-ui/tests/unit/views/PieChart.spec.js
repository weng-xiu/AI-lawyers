'use strict'

/**
 * PieChart 饼图单测：锁定南丁格尔玫瑰配置与实例释放。
 */
import { mount } from '@vue/test-utils'
import * as echarts from 'echarts'
import PieChart from '@/views/dashboard/PieChart.vue'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn(),
    dispose: jest.fn()
  }))
}))

async function mountChart() {
  const wrapper = mount(PieChart)
  await wrapper.vm.$nextTick()
  const chart = echarts.init.mock.results[echarts.init.mock.results.length - 1].value
  return { wrapper, chart }
}

describe('PieChart', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  test('默认容器样式与玫瑰图配置正确', async () => {
    const { wrapper, chart } = await mountChart()

    expect(wrapper.classes()).toContain('chart')
    expect(wrapper.attributes('style')).toContain('height: 300px')

    const option = chart.setOption.mock.calls[0][0]
    expect(option.series[0]).toMatchObject({
      name: 'WEEKLY WRITE ARTICLES',
      type: 'pie',
      roseType: 'radius'
    })
    expect(option.series[0].data).toHaveLength(5)
    expect(option.legend.data).toHaveLength(5)
  })

  test('自定义尺寸透传到容器', async () => {
    const wrapper = mount(PieChart, { propsData: { width: '320px', height: '200px', className: 'pie-small' } })
    await wrapper.vm.$nextTick()

    expect(wrapper.classes()).toEqual(['pie-small'])
    expect(wrapper.attributes('style')).toContain('height: 200px')
    expect(wrapper.attributes('style')).toContain('width: 320px')
  })

  test('销毁时释放图表实例', async () => {
    const { wrapper, chart } = await mountChart()

    wrapper.destroy()

    expect(chart.dispose).toHaveBeenCalled()
  })
})
