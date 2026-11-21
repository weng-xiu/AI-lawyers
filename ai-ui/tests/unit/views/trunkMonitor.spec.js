'use strict'

/**
 * 中继监控单测：锁定轮询刷新、线路/运营商/告警/PJSIP 装配、趋势图渲染与告警处理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TrunkMonitor from '@/views/lawyers/callCenter/trunkMonitor.vue'
import {
  trunkOverview, trunkStatusList, carrierStat, trunkTrend, trunkAlarmList,
  handleTrunkAlarm, pjsipEndpoints, pjsipQueues, pjsipSummary, pjsipRegistrations
} from '@/api/lawyers/trunk'
import { createPoller } from '@/utils/polling'

jest.mock('@/api/lawyers/trunk', () => ({
  trunkOverview: jest.fn(),
  trunkStatusList: jest.fn(),
  carrierStat: jest.fn(),
  trunkTrend: jest.fn(),
  trunkAlarmList: jest.fn(),
  handleTrunkAlarm: jest.fn(),
  trunkHealthCheck: jest.fn(),
  pjsipEndpoints: jest.fn(),
  pjsipQueues: jest.fn(),
  pjsipSummary: jest.fn(),
  pjsipRegistrations: jest.fn()
}))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage() {
  const modal = { msgSuccess: jest.fn() }
  const confirm = jest.fn(() => Promise.resolve({ value: '已联系运营商' }))
  const wrapper = mount(TrunkMonitor, {
    localVue,
    mocks: { $modal: modal, $prompt: confirm }
  })
  return { wrapper, modal, confirm }
}

describe('TrunkMonitor 中继监控', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    trunkOverview.mockResolvedValue({ data: { totalCalls: 100, connectRate: 0.855, activeTrunks: 3, totalTrunks: 4 } })
    trunkStatusList.mockResolvedValue({
      data: [{ trunkId: 1, trunkName: '移动主线', carrier: 'CM', healthStatus: 1, currentConcurrent: 5, maxConcurrent: 10, successRate: 0.9 }]
    })
    carrierStat.mockResolvedValue({ data: [{ carrierName: '移动', totalCalls: 60, successRate: 0.9 }] })
    trunkTrend.mockResolvedValue({ data: [{ statMinute: '10:00', connectRate: 0.855 }] })
    trunkAlarmList.mockResolvedValue({ rows: [{ alarmId: 9, severity: 'HIGH', alarmText: '链路中断' }] })
    handleTrunkAlarm.mockResolvedValue({})
    pjsipSummary.mockResolvedValue({ data: { configDir: '/etc/asterisk', pjsipExists: true, queuesExists: false } })
    pjsipEndpoints.mockResolvedValue({ data: [{ name: '1001', contacts: ['sip:1001@1.1.1.1'] }] })
    pjsipQueues.mockResolvedValue({ data: [{ name: 'support', members: ['PJSIP/1001'] }] })
    pjsipRegistrations.mockResolvedValue({
      data: { available: true, registrations: [{ name: '1001', deviceState: 'Not in use' }] }
    })
  })

  test('创建时以 15 秒轮询并立即启动', () => {
    mountPage()

    expect(createPoller).toHaveBeenCalledWith(expect.any(Function), 15000, { immediate: true })
    expect(createPoller.mock.results[0].value.start).toHaveBeenCalledTimes(1)
  })

  test('全量刷新并行加载线路、告警与 PJSIP 数据', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.refreshAll()
    await flush()

    expect(trunkOverview).toHaveBeenCalledTimes(1)
    expect(trunkStatusList).toHaveBeenCalledTimes(1)
    expect(carrierStat).toHaveBeenCalledTimes(1)
    expect(trunkTrend).toHaveBeenCalledWith({ trunkId: undefined, dimension: 'CONNECT_RATE', limit: 30 })
    expect(trunkAlarmList).toHaveBeenCalledWith({ status: '0', pageSize: 20 })
    expect(pjsipSummary).toHaveBeenCalledTimes(1)
    expect(pjsipEndpoints).toHaveBeenCalledTimes(1)
    expect(pjsipQueues).toHaveBeenCalledTimes(1)
    expect(pjsipRegistrations).toHaveBeenCalledTimes(1)

    expect(wrapper.vm.overview.totalCalls).toBe(100)
    expect(wrapper.vm.trunkStatus).toHaveLength(1)
    expect(wrapper.vm.carrierStat).toHaveLength(1)
    expect(wrapper.vm.alarms).toHaveLength(1)
    expect(wrapper.vm.pjsipEndpoints).toHaveLength(1)
    expect(wrapper.vm.pjsipQueues).toHaveLength(1)
    expect(wrapper.vm.pjsipRegs).toHaveLength(1)
    expect(wrapper.text()).toContain('实时线路状态')
    expect(wrapper.text()).toContain('链路中断')
  })

  test('趋势图按接通率换算百分比，空数据展示占位标题', async () => {
    const echarts = require('echarts')
    const { wrapper } = mountPage()
    await wrapper.vm.loadTrend()

    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.trendChart, 'judicial')
    const option = wrapper.vm.chart.setOption.mock.calls[0][0]
    expect(option.xAxis.data).toEqual(['10:00'])
    expect(option.series[0].data).toEqual(['85.5'])
    expect(option.title.text).toBe('')

    wrapper.vm.chart.setOption.mockClear()
    trunkTrend.mockResolvedValue({ data: [] })
    await wrapper.vm.loadTrend()
    expect(wrapper.vm.chart.setOption.mock.calls[0][0].title.text).toBe('暂无趋势数据')
  })

  test('告警处理弹窗确认后提交说明并刷新告警', async () => {
    const { wrapper, modal, confirm } = mountPage()
    await flush()

    wrapper.vm.handleAlarm({ alarmId: 9 })
    await flush()

    expect(confirm).toHaveBeenCalledWith('处理说明', '处理告警', { inputType: 'textarea' })
    expect(handleTrunkAlarm).toHaveBeenCalledWith({ alarmId: 9, handleRemark: '已联系运营商', status: '1' })
    expect(modal.msgSuccess).toHaveBeenCalledWith('已处理')
    expect(trunkAlarmList).toHaveBeenCalledWith({ status: '0', pageSize: 20 })
  })

  test('百分比与并发占比计算容错', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.rate(0.855)).toBe('85.5%')
    expect(wrapper.vm.rate(null)).toBe('-')
    expect(wrapper.vm.pct(5, 10)).toBe(50)
    expect(wrapper.vm.pct(20, 10)).toBe(100)
    expect(wrapper.vm.pct(1, 0)).toBe(0)
  })

  test('运营商、健康状态与告警级别映射', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.carrierName('CM')).toBe('移动')
    expect(wrapper.vm.carrierName('XX')).toBe('XX')
    expect(wrapper.vm.carrierTag('CT')).toBe('danger')
    expect(wrapper.vm.carrierTag('XX')).toBe('')
    expect(wrapper.vm.healthName(2)).toBe('亚健康')
    expect(wrapper.vm.healthName(9)).toBe('未知')
    expect(wrapper.vm.healthTag(3)).toBe('danger')
    expect(wrapper.vm.healthTag(9)).toBe('')
    expect(wrapper.vm.alarmTag('HIGH')).toBe('danger')
    expect(wrapper.vm.alarmTag('OTHER')).toBe('info')
  })

  test('注册态与 Contact 状态标签映射', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.regStateTag('Not in use')).toBe('success')
    expect(wrapper.vm.regStateTag('In use')).toBe('primary')
    expect(wrapper.vm.regStateTag('Unavailable')).toBe('danger')
    expect(wrapper.vm.regStateTag('')).toBe('info')
    expect(wrapper.vm.contactTag('Avail')).toBe('success')
    expect(wrapper.vm.contactTag('Rejected')).toBe('danger')
    expect(wrapper.vm.contactTag('Other')).toBe('info')
  })

  test('PJSIP 提示文案与注册数按可用性计算', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadPjsip()

    expect(wrapper.vm.pjsipHint).toBe('/etc/asterisk · pjsip.conf 已找到 · queues.conf 未找到')
    expect(wrapper.vm.pjsipRegCount).toBe(1)

    wrapper.vm.pjsipSummary = { error: '解析失败' }
    expect(wrapper.vm.pjsipHint).toBe('配置解析异常：解析失败')

    wrapper.vm.pjsipRegInfo = { available: false }
    expect(wrapper.vm.pjsipRegCount).toBe(0)
  })

  test('销毁时停止轮询并释放图表', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadTrend()
    const chart = wrapper.vm.chart

    wrapper.destroy()

    expect(createPoller.mock.results[0].value.stop).toHaveBeenCalledTimes(1)
    expect(chart.dispose).toHaveBeenCalledTimes(1)
  })
})
