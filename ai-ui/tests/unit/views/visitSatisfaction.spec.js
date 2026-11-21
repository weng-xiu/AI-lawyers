'use strict'

/**
 * 回访满意度看板单测：锁定轮询启动、区间参数、指标卡与排行装配、三图渲染及空态。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import VisitSatisfaction from '@/views/lawyers/collab/visitSatisfaction/index.vue'
import { visitSatisfactionBoard } from '@/api/lawyers/visitSatisfaction'
import { createPoller } from '@/utils/polling'

jest.mock('@/api/lawyers/visitSatisfaction', () => ({ visitSatisfactionBoard: jest.fn() }))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

jest.mock('echarts', () => ({
  init: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const boardData = {
  callbackOverview: { visitCount: 30, satisfiedRate: 88.5, unsatisfiedCount: 4 },
  callbackTrend: [{ statDate: '2026-11-01', verySatisfied: 5, satisfied: 3, normal: 1, unsatisfied: 1 }],
  visitorRanking: [{ userName: '张律师', visitCount: 12, satisfiedCount: 10, unsatisfiedCount: 1, avgScore: 3.5 }],
  evaluation: { avgOverall: 4.2, avgProfessionalism: 4.4, avgResponsiveness: 4.1, avgQuality: 4.3, positiveRate: 82, negativeRate: 6, evalCount: 50 },
  reasonTop: [{ reason: '解答不清晰', count: 3 }, { reason: '等待过久', count: 5 }],
  sentiment: { positive: 60, neutral: 20, negative: 10, positiveRate: 66.7 },
  taskOverview: { totalCount: 40, pendingCount: 10, completedCount: 30, overdueCount: 3, pendingHighCount: 2 },
  overdueSummary: { level1Count: 2, level2Count: 1, level3Count: 1 },
  overdueTasks: [{ taskNo: 'RV001', callerName: '王先生', assignee: '张律师', priority: '1', overdueMinutes: 90, level: 2 }]
}

function mountPage() {
  const wrapper = mount(VisitSatisfaction, { localVue })
  return { wrapper }
}

describe('VisitSatisfaction 回访满意度看板', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    const echarts = require('echarts')
    echarts.init.mockImplementation(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
    visitSatisfactionBoard.mockResolvedValue({ data: boardData })
  })

  test('创建时以 60 秒轮询并立即启动', () => {
    mountPage()

    expect(createPoller).toHaveBeenCalledWith(expect.any(Function), 60000, { immediate: true })
    expect(createPoller.mock.results[0].value.start).toHaveBeenCalledTimes(1)
  })

  test('加载数据装配各区块并渲染三张图表', async () => {
    const echarts = require('echarts')
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(visitSatisfactionBoard).toHaveBeenCalledWith({})
    expect(wrapper.vm.callbackOverview).toEqual(boardData.callbackOverview)
    expect(wrapper.vm.visitorRanking).toEqual(boardData.visitorRanking)
    expect(wrapper.vm.reasonTop).toEqual(boardData.reasonTop)
    expect(wrapper.vm.overdueTasks).toEqual(boardData.overdueTasks)
    expect(wrapper.vm.loading).toBe(false)
    expect(echarts.init).toHaveBeenCalledTimes(3)
    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.trendChart, 'judicial')
    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.sentimentChart, 'judicial')
    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.reasonChart, 'judicial')
    expect(wrapper.vm.charts.trend.setOption).toHaveBeenCalled()
    expect(wrapper.text()).toContain('智能回访 · 满意度归因看板')
    expect(wrapper.text()).toContain('张律师')
  })

  test('选择区间后拼接起止时间参数', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.rangeTime = ['2026-11-01', '2026-11-07']

    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(visitSatisfactionBoard).toHaveBeenLastCalledWith({
      beginTime: '2026-11-01 00:00:00',
      endTime: '2026-11-07 23:59:59'
    })
  })

  test('指标卡、评价维度与完成率计算', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()

    const cards = wrapper.vm.statCards
    expect(cards).toHaveLength(8)
    expect(cards[0]).toEqual({ label: '电话回访量', value: 30, color: '#1A3C6E' })
    expect(cards[3]).toEqual({ label: '图文均分', value: 4.2, color: '#3B73B3' })
    expect(cards[6]).toEqual({ label: '回访已逾期', value: 3, color: '#E8923A' })

    expect(wrapper.vm.evalDimRows).toHaveLength(4)
    expect(wrapper.vm.evalDimRows[0]).toEqual({ dim: '总体评价', score: 4.2 })
    expect(wrapper.vm.taskCompleteRate).toBe(75)
  })

  test('任务完成率在无总量时为 0', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.taskCompleteRate).toBe(0)
  })

  test('数值、评分与日期格式化工具', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.num('12')).toBe(12)
    expect(wrapper.vm.num('bad')).toBe(0)
    expect(wrapper.vm.scorePct(4.2)).toBe(84)
    expect(wrapper.vm.scorePct(9)).toBe(100)
    expect(wrapper.vm.scorePct(-1)).toBe(0)
    expect(wrapper.vm.scoreColor(4.2)).toBe('#2B8C6E')
    expect(wrapper.vm.scoreColor(3.2)).toBe('#E8B64A')
    expect(wrapper.vm.scoreColor(2)).toBe('#C63D4A')
    expect(wrapper.vm.fmtDate('2026-11-01 09:00:00')).toBe('11-01')
    expect(wrapper.vm.fmtDate(null)).toBe('')
    expect(wrapper.vm.priorityText('1')).toBe('高')
    expect(wrapper.vm.priorityText('3')).toBe('低')
    expect(wrapper.vm.priorityText('9')).toBe('-')
  })

  test('图表缩放调用所有已初始化实例', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    wrapper.vm.resizeCharts()

    expect(wrapper.vm.charts.trend.resize).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.charts.sentiment.resize).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.charts.reason.resize).toHaveBeenCalledTimes(1)
  })

  test('空数据展示各分区空态文案', async () => {
    visitSatisfactionBoard.mockResolvedValue({ data: {} })
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.visitorRanking).toEqual([])
    expect(wrapper.vm.reasonTop).toEqual([])
    expect(wrapper.text()).toContain('区间内暂无负向文本可归因')
    expect(wrapper.text()).toContain('区间内暂无回访员数据')
    expect(wrapper.text()).toContain('当前无逾期任务')
  })

  test('销毁时停止轮询并释放全部图表', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()
    const charts = wrapper.vm.charts

    wrapper.destroy()

    expect(createPoller.mock.results[0].value.stop).toHaveBeenCalledTimes(1)
    expect(charts.trend.dispose).toHaveBeenCalledTimes(1)
    expect(charts.sentiment.dispose).toHaveBeenCalledTimes(1)
    expect(charts.reason.dispose).toHaveBeenCalledTimes(1)
  })
})
