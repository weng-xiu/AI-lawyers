'use strict'

/**
 * SLA 看板单测：锁定轮询启动、看板数据装配、统计卡计算、指标格式化与图表渲染。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import SlaBoard from '@/views/lawyers/collab/slaBoard/index.vue'
import { slaBoardData } from '@/api/lawyers/slaPolicy'
import { createPoller } from '@/utils/polling'

jest.mock('@/api/lawyers/slaPolicy', () => ({ slaBoardData: jest.fn() }))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const boardData = {
  overview: {
    totalCount: 20,
    closedCount: 12,
    achievementRate: 91.5,
    closeRate: 60,
    openCount: 8,
    openOvertimeCount: 2,
    dueSoonCount: 3,
    totalOvertimeCount: 5
  },
  achieveByBizType: [{ bizType: 'INTERNAL', totalCount: 10, closedCount: 8, onTimeCount: 7 }],
  achieveByAssignee: [{ userName: '张律师', closedCount: 8, onTimeCount: 7, avgHandleMinutes: 30 }],
  overtimeTop: [{ ticketNo: 'T001', title: '超时工单', bizType: 'LEGAL_AID', priority: '1', status: '0', overdueMinutes: 120 }],
  dueSoon: [{ ticketNo: 'T002', title: '即将超时', assignUserName: '', dueTime: '2026-11-01 12:00', remainSeconds: 600 }],
  transferStat: [{ bizType: 'HOTLINE_12345', direction: 'OUT', transferCount: 4, doneCount: 2, avgHandleMinutes: 15 }]
}

function mountPage() {
  const wrapper = mount(SlaBoard, { localVue, mocks: { $modal: { msgSuccess: jest.fn() } } })
  return { wrapper }
}

describe('SlaBoard SLA 看板', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    slaBoardData.mockResolvedValue({ data: boardData })
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
  })

  test('创建时以 60 秒轮询并立即启动', () => {
    mountPage()

    expect(createPoller).toHaveBeenCalledWith(expect.any(Function), 60000, { immediate: true })
    expect(createPoller.mock.results[0].value.start).toHaveBeenCalledTimes(1)
  })

  test('加载数据携带预警窗口并装配各区块', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(slaBoardData).toHaveBeenCalledWith({ warnMinutes: 60 })
    expect(wrapper.vm.overview).toEqual(boardData.overview)
    expect(wrapper.vm.achieveByBizType).toEqual(boardData.achieveByBizType)
    expect(wrapper.vm.achieveByAssignee).toEqual(boardData.achieveByAssignee)
    expect(wrapper.vm.overtimeTop).toEqual(boardData.overtimeTop)
    expect(wrapper.vm.dueSoon).toEqual(boardData.dueSoon)
    expect(wrapper.vm.transferStat).toEqual(boardData.transferStat)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('工单 SLA 看板')
    expect(wrapper.text()).toContain('超时工单')
  })

  test('选择时间区间后追加起止时间参数', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.rangeTime = ['2026-11-01 00:00:00', '2026-11-02 00:00:00']

    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(slaBoardData).toHaveBeenLastCalledWith(expect.objectContaining({
      warnMinutes: 60,
      beginTime: '2026-11-01 00:00:00',
      endTime: '2026-11-02 00:00:00'
    }))
  })

  test('统计卡按概览数据生成八项指标', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.loadData()

    const cards = wrapper.vm.statCards
    expect(cards).toHaveLength(8)
    expect(cards[0]).toEqual({ label: '工单总量', value: 20, color: '#1A3C6E' })
    expect(cards[2]).toEqual({ label: '准时率(%)', value: 91.5, color: '#3B73B3' })
    expect(cards[7]).toEqual({ label: '超时合计', value: 5, color: '#8C4A55' })
  })

  test('图表初始化一次并在销毁时释放', async () => {
    const echarts = require('echarts')
    const { wrapper } = mountPage()

    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()
    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(echarts.init).toHaveBeenCalledTimes(1)
    expect(echarts.init).toHaveBeenCalledWith(wrapper.vm.$refs.bizChart, 'judicial')
    expect(wrapper.vm.bizChart.setOption).toHaveBeenCalled()

    wrapper.destroy()
    expect(wrapper.vm.bizChart.dispose).toHaveBeenCalledTimes(1)
    expect(createPoller.mock.results[0].value.stop).toHaveBeenCalledTimes(1)
  })

  test('数值工具函数容错与百分比换算', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.num('12')).toBe(12)
    expect(wrapper.vm.num('abc')).toBe(0)
    expect(wrapper.vm.num(undefined)).toBe(0)
    expect(wrapper.vm.rate(3, 4)).toBe(75)
    expect(wrapper.vm.rate(1, 0)).toBe(0)
    expect(wrapper.vm.rateColor(95)).toBe('#2B8C6E')
    expect(wrapper.vm.rateColor(80)).toBe('#E8923A')
    expect(wrapper.vm.rateColor(60)).toBe('#C63D4A')
  })

  test('业务口径文本与状态判断', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.bizText('INTERNAL')).toBe('热线工单')
    expect(wrapper.vm.bizText('HOTLINE_12345')).toBe('12345协同')
    expect(wrapper.vm.bizText('OTHER')).toBe('OTHER')
    expect(wrapper.vm.bizText(undefined)).toBe('-')
    expect(wrapper.vm.priorityText('1')).toBe('紧急')
    expect(wrapper.vm.priorityText(3)).toBe('低')
    expect(wrapper.vm.isOpen('1')).toBe(true)
    expect(wrapper.vm.isOpen('2')).toBe(false)
  })

  test('剩余时间文案与标签类型分级', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.remainText(0)).toBe('已超时')
    expect(wrapper.vm.remainText(600)).toBe('10 分钟')
    expect(wrapper.vm.remainText(7200)).toBe('2 小时')
    expect(wrapper.vm.remainType(600)).toBe('danger')
    expect(wrapper.vm.remainType(1800)).toBe('warning')
    expect(wrapper.vm.remainType(3600)).toBe('')
  })

  test('区间无数据时渲染空态提示', async () => {
    slaBoardData.mockResolvedValue({ data: {} })
    const { wrapper } = mountPage()

    await wrapper.vm.loadData()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.overtimeTop).toEqual([])
    expect(wrapper.vm.dueSoon).toEqual([])
    expect(wrapper.text()).toContain('区间内暂无超时工单')
    expect(wrapper.text()).toContain('当前无临近超时工单')
  })
})
