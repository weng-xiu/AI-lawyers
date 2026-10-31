'use strict'

/**
 * F9 15 分钟粒度话务预测与对比验收单测：
 * 首日自动选中、96 时段图表数据、峰值时段排序、验收指标格式与达标标签、
 * 无实际数据/无预测数据的降级文案，以及切换天数重新拉取。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import * as echarts from 'echarts'
import ForecastSlot from '@/views/lawyers/schedule/forecastSlot.vue'
import { previewSlotForecast, getSlotAccuracy } from '@/api/lawyers/forecast'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({
    setOption: jest.fn(),
    resize: jest.fn(),
    dispose: jest.fn()
  }))
}))

jest.mock('@/api/lawyers/forecast', () => ({
  previewSlotForecast: jest.fn(),
  getSlotAccuracy: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function slotTime(index) {
  const hour = Math.floor(index / 4)
  const minute = (index % 4) * 15
  return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0')
}

/** 构造某天的 96 个时段；volumes 为 slot → 预测进线量 */
function dayRows(date, weekday, volumes) {
  const rows = []
  for (let slot = 0; slot < 96; slot++) {
    const volume = volumes[slot] || 0
    rows.push({
      forecastDate: date,
      weekday: weekday,
      slot: slot,
      beginTime: slotTime(slot),
      endTime: slotTime(slot + 1),
      timeRange: slotTime(slot) + '-' + slotTime(slot + 1),
      volume: volume,
      agents: volume > 0 ? Math.ceil(volume / 10) : 0,
      factor: 1,
      dayType: null,
      dayName: null
    })
  }
  return rows
}

function accuracyPayload(overrides = {}) {
  return Object.assign({
    available: true,
    days: 7,
    slotCount: 192,
    totalForecast: 200,
    totalActual: 200,
    mae: 0.104,
    mape: 0.101,
    accuracy: 0.9,
    perDate: [
      { date: '2026-10-29', weekday: 3, forecastTotal: 100, actualTotal: 90, absError: 10, mae: 0.104, mape: 0.111, accuracy: 0.889 }
    ],
    params: { historyWeeks: 8, compareDays: 7, slotMinutes: 15, source: 'ai_stat_minute/call_total' }
  }, overrides)
}

function mountPage() {
  return mount(ForecastSlot, {
    localVue,
    mocks: { $modal: { msgError: jest.fn() } }
  })
}

describe('ForecastSlot F9 15 分钟预测与对比验收', () => {
  let wrapper

  beforeEach(() => {
    jest.clearAllMocks()
    previewSlotForecast.mockResolvedValue({ data: dayRows('2026-11-02', 1, { 33: 35, 68: 80 }) })
    getSlotAccuracy.mockResolvedValue({ data: accuracyPayload() })
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  it('created 拉取 7 天预测与验收，自动选中首日', async () => {
    wrapper = mountPage()
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(previewSlotForecast).toHaveBeenCalledWith(7)
    expect(getSlotAccuracy).toHaveBeenCalledWith(7)
    expect(wrapper.vm.dateOptions).toEqual([{ value: '2026-11-02', label: '2026-11-02（周一）' }])
    expect(wrapper.vm.selectedDate).toBe('2026-11-02')
    expect(wrapper.vm.currentRows).toHaveLength(96)
    expect(wrapper.vm.totalVolume).toBe(115)
    expect(wrapper.vm.selectedVolume).toBe(115)
    // 峰值时段按话量倒序取前 5，0 量时段不展示
    expect(wrapper.vm.peakSlot.timeRange).toBe('17:00-17:15')
    expect(wrapper.vm.peakSlots).toHaveLength(2)
    expect(wrapper.vm.peakSlots[0].volume).toBe(80)
  })

  it('图表 x 轴为整日 96 个时段，含预测与坐席两个系列', async () => {
    wrapper = mountPage()
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.slotChart, 'judicial')
    const chart = echarts.init.mock.results[0].value
    expect(chart.setOption).toHaveBeenCalled()
    const option = wrapper.vm.buildChartOption(wrapper.vm.currentRows)
    expect(option.xAxis.data).toHaveLength(96)
    expect(option.xAxis.data[0]).toBe('00:00')
    expect(option.xAxis.data[95]).toBe('23:45')
    expect(option.series).toHaveLength(2)
    expect(option.series[0].data[33]).toBe(35)
    expect(option.series[1].data[33]).toBe(4)
  })

  it('切换预测天数后按新天数重新拉取', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.days = 14
    wrapper.vm.loadPreview()
    await flushPromises()

    expect(previewSlotForecast).toHaveBeenLastCalledWith(14)
  })

  it('验收指标格式化为百分比，按准确率给出达标标签', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.formatNumber(0.104)).toBe('0.104')
    expect(wrapper.vm.formatNumber(null)).toBe('-')
    expect(wrapper.vm.formatPercent(0.9)).toBe('90.0%')
    expect(wrapper.vm.formatPercent(null)).toBe('-')

    expect(wrapper.vm.accuracyTag).toBe('success')
    expect(wrapper.vm.accuracyTagText).toBe('达标')

    getSlotAccuracy.mockResolvedValue({ data: accuracyPayload({ accuracy: 0.72 }) })
    wrapper.vm.loadAccuracy()
    await flushPromises()
    expect(wrapper.vm.accuracyTag).toBe('warning')
    expect(wrapper.vm.accuracyTagText).toBe('接近阈值')

    getSlotAccuracy.mockResolvedValue({ data: accuracyPayload({ accuracy: 0.5 }) })
    wrapper.vm.loadAccuracy()
    await flushPromises()
    expect(wrapper.vm.accuracyTag).toBe('danger')
    expect(wrapper.vm.accuracyTagText).toBe('待优化')
  })

  it('无可对比实际话务时展示降级文案且不展示逐日表', async () => {
    getSlotAccuracy.mockResolvedValue({ data: accuracyPayload({ available: false, slotCount: 0, mae: null, mape: null, accuracy: null, perDate: [] }) })
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.accuracyTag).toBe('info')
    expect(wrapper.vm.accuracyTagText).toBe('无数据')
    const emptyText = wrapper.findAll('.fs-empty').wrappers.map(item => item.text()).join('|')
    expect(emptyText).toContain('暂无可对比的实际话务量')
    expect(wrapper.find('.fs-acc-metrics').exists()).toBe(false)
  })

  it('无预测数据时不渲染时段与峰值表', async () => {
    previewSlotForecast.mockResolvedValue({ data: [] })
    wrapper = mountPage()
    await flushPromises()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.dateOptions).toHaveLength(0)
    expect(wrapper.vm.selectedDate).toBe(null)
    expect(wrapper.vm.currentRows).toHaveLength(0)
    expect(wrapper.vm.peakSlot).toBe(null)
    expect(wrapper.vm.buildChartOption([]).xAxis.data).toHaveLength(0)
    expect(wrapper.findAll('.fs-empty').wrappers.map(item => item.text()).join('|')).toContain('暂无预测数据')
  })
})
