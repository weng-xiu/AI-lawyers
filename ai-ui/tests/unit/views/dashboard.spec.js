'use strict'

/**
 * 法务首页看板单测：锁定 30 秒轮询、看板数据装配、指标卡与三图数据映射、销毁释放。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Dashboard from '@/views/lawyers/dashboard/index.vue'
import { dashboardData } from '@/api/lawyers/stat'
import { createPoller } from '@/utils/polling'

jest.mock('@/api/lawyers/stat', () => ({ dashboardData: jest.fn() }))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

jest.mock('echarts', () => ({
  init: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const payload = {
  callSummary: { totalCalls: 100, answeredCalls: 80, missedCalls: 10, transferredCalls: 5, avgDuration: 92 },
  agentStatusSummary: { onlineAgents: 6, totalAgents: 8 },
  satisfactionSummary: { avgSatisfaction: 4.6 },
  aiRatio: { totalSessions: 50, handoffSessions: 12 },
  callTrend: [{ timeLabel: '09:00', totalCalls: 10, answeredCalls: 8, missedCalls: 1, transferredCalls: 1 }],
  categoryPie: [{ name: '婚姻家庭', value: 30 }],
  agentLoad: [{ agentName: '张律师', currentLoad: 2 }],
  queueNow: [{ queueName: '咨询队列', waitingCount: 3 }],
  outboundProgress: [{ status: '已完成', count: 10 }],
  bizMetrics: {
    careUsage: { careCalls: 20, totalCalls: 100, careCallers: 8, totalCallers: 40, careCallRate: 20, careCallerRate: 20 },
    portalSatisfaction: { avgOverall: 4.5, avgProfessionalism: 4.6, avgResponsiveness: 4.4, avgQuality: 4.5, evalCount: 30 },
    languageDist: [{ lang: 'zh-CN', langName: '普通话', value: 40 }, { lang: 'yue-CN', langName: '粤语', value: 10 }],
    channelSessions: [{ channelName: '公众号', value: 12 }],
    channelBinds: [{ channelName: '公众号', value: 30 }]
  }
}

function mountPage() {
  const wrapper = mount(Dashboard, { localVue })
  return { wrapper }
}

describe('Dashboard 法务首页看板', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    const echarts = require('echarts')
    echarts.init.mockImplementation(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
    dashboardData.mockResolvedValue({ data: payload })
  })

  test('创建时以 30 秒轮询并立即启动', () => {
    mountPage()

    expect(createPoller).toHaveBeenCalledWith(expect.any(Function), 30000, { immediate: true })
    expect(createPoller.mock.results[0].value.start).toHaveBeenCalledTimes(1)
  })

  test('加载数据装配全部区块并渲染五张图表', async () => {
    const echarts = require('echarts')
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(dashboardData).toHaveBeenCalledWith({})
    expect(wrapper.vm.callSummary).toEqual(payload.callSummary)
    expect(wrapper.vm.callTrend).toEqual(payload.callTrend)
    expect(wrapper.vm.categoryPie).toEqual(payload.categoryPie)
    expect(wrapper.vm.outboundProgress).toEqual(payload.outboundProgress)
    expect(wrapper.vm.bizMetrics).toEqual(payload.bizMetrics)
    expect(echarts.init).toHaveBeenCalledTimes(5)
    expect(wrapper.vm.trendChart.setOption).toHaveBeenCalled()
    const trendOption = wrapper.vm.trendChart.setOption.mock.calls[0][0]
    expect(trendOption.xAxis.data).toEqual(['09:00'])
    expect(trendOption.series[0].data).toEqual([10])
    expect(trendOption.series[2].data).toEqual([1])
    expect(wrapper.text()).toContain('关怀号码通话占比')
  })

  test('选择区间后传递起止时间参数', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.rangeTime = ['2026-11-01', '2026-11-07']

    await wrapper.vm.loadData()

    expect(dashboardData).toHaveBeenLastCalledWith({ beginTime: '2026-11-01', endTime: '2026-11-07' })
  })

  test('指标卡计算 AI 独立解决量与坐席在线比', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()

    const cards = wrapper.vm.statCards
    expect(cards).toHaveLength(8)
    expect(cards[0].value).toBe(100)
    expect(cards[4].value).toBe(92)
    expect(cards[5].value).toBe('6/8')
    expect(cards[7].value).toBe(38)
  })

  test('关怀模式与满意度子面板回退空对象', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.bizCare).toEqual({})
    expect(wrapper.vm.bizSatis).toEqual({})

    wrapper.vm.bizMetrics = payload.bizMetrics
    expect(wrapper.vm.bizCare.careCalls).toBe(20)
    expect(wrapper.vm.bizSatis.avgOverall).toBe(4.5)
  })

  test('数值、进度与状态文案映射容错', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.num('12')).toBe(12)
    expect(wrapper.vm.num('bad')).toBe(0)
    expect(wrapper.vm.progressPct({ completedCount: 4, totalCount: 8 })).toBe(50)
    expect(wrapper.vm.progressPct({ completedCount: 9, totalCount: 8 })).toBe(100)
    expect(wrapper.vm.progressPct({ totalCount: 0 })).toBe(0)

    expect(wrapper.vm.statusLabel('1')).toBe('在线')
    expect(wrapper.vm.statusLabel('9')).toBe('9')
    expect(wrapper.vm.statusTag('2')).toBe('warning')
    expect(wrapper.vm.statusTag('9')).toBe('')
    expect(wrapper.vm.callStatusLabel('5')).toBe('话后整理')
    expect(wrapper.vm.callStatusLabel('9')).toBe('9')
  })

  test('图表重复渲染复用实例，销毁时停止轮询并释放图表', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    const echarts = require('echarts')
    expect(echarts.init).toHaveBeenCalledTimes(5)
    const charts = {
      trend: wrapper.vm.trendChart,
      category: wrapper.vm.categoryChart,
      ai: wrapper.vm.aiChart,
      lang: wrapper.vm.langChart,
      channel: wrapper.vm.channelChart
    }

    wrapper.destroy()

    expect(createPoller.mock.results[0].value.stop).toHaveBeenCalledTimes(1)
    Object.keys(charts).forEach(key => {
      expect(charts[key].dispose).toHaveBeenCalledTimes(1)
    })
  })
})
