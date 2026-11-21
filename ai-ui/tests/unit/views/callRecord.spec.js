'use strict'

/**
 * 通话记录单测：锁定命令面板预填查询、列表三态、状态映射、详情与关联工单/转接、
 * AI 通话小结生成、由话单生成工单、统计分析图表与话单增删改。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import * as echarts from 'echarts'
import ProTable from '@/components/ProTable/index.vue'
import CallRecord from '@/views/lawyers/callCenter/callRecord.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listRecord, getRecord, addRecord, updateRecord, delRecord,
  getCallStatistics, getCallStatisticsByCategory, getCallStatisticsByDate,
  addTicket, generateTicketNo, listTicket, getTransfersByRecordId, listAgent, generateAiSummary
} from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callCenter', () => ({
  listRecord: jest.fn(),
  getRecord: jest.fn(),
  addRecord: jest.fn(),
  updateRecord: jest.fn(),
  delRecord: jest.fn(),
  getCallStatistics: jest.fn(),
  getCallStatisticsByCategory: jest.fn(),
  getCallStatisticsByDate: jest.fn(),
  addTicket: jest.fn(),
  generateTicketNo: jest.fn(),
  listTicket: jest.fn(),
  getTransfersByRecordId: jest.fn(),
  listAgent: jest.fn(),
  generateAiSummary: jest.fn()
}))

jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.parseTime = value => (value ? '2026-11-01 10:00' : '')
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  {
    recordId: 1,
    callerNumber: '13800000001',
    callerName: '张三',
    agentId: 3,
    agentName: '王坐席',
    consultationCategory: '合同纠纷',
    consultationContent: '咨询合同解除',
    status: '1',
    callDuration: 120,
    callTime: '2026-11-01 10:00:00',
    createTime: '2026-11-01 10:05:00'
  },
  {
    recordId: 2,
    callerNumber: '13800000002',
    callerName: '李四',
    agentId: 4,
    agentName: '赵坐席',
    status: '0',
    callDuration: 0
  }
]

function mountPage(routeQuery = {}) {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const routerPush = jest.fn()
  const wrapper = mount(CallRecord, {
    localVue,
    mocks: {
      $modal: modal,
      $router: { push: routerPush },
      $route: { query: routeQuery, path: '/lawyers/callCenter/callRecord' }
    }
  })
  return { wrapper, modal, routerPush }
}

describe('CallRecord 通话记录', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    localStorage.clear()
    listRecord.mockResolvedValue({ rows, total: 2 })
    getRecord.mockResolvedValue({ data: { ...rows[0] } })
    addRecord.mockResolvedValue({})
    updateRecord.mockResolvedValue({})
    delRecord.mockResolvedValue({})
    listAgent.mockResolvedValue({ rows: [{ agentId: 3, agentName: '王坐席' }] })
    listTicket.mockResolvedValue({ rows: [{ ticketId: 21, ticketNo: 'T021', status: '2' }] })
    getTransfersByRecordId.mockResolvedValue({ data: [{ transferId: 1, targetAgentName: '李律师' }] })
    generateAiSummary.mockResolvedValue({ data: { ...rows[0], summaryStatus: '2', aiSummary: '小结内容' } })
    generateTicketNo.mockResolvedValue({ data: 'GD20261121001' })
    addTicket.mockResolvedValue({})
    getCallStatistics.mockResolvedValue({
      data: {
        totalCount: 100, todayCount: 8, weekCount: 30, monthCount: 60,
        answeredCount: 80, unansweredCount: 15, transferredCount: 5, avgDuration: 95
      }
    })
    getCallStatisticsByCategory.mockResolvedValue({ data: [{ category_name: '合同纠纷', count: 6 }] })
    getCallStatisticsByDate.mockResolvedValue({ data: [{ date: '2026-11-01', count: 3 }] })
  })

  test('初始化加载话单与坐席，命令面板预填来电号码查询', async () => {
    const { wrapper } = mountPage({ callerNumber: '13900001111' })
    await flush()

    expect(listRecord).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      callerNumber: '13900001111',
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(listAgent).toHaveBeenCalledWith({ pageNum: 1, pageSize: 1000 })
    expect(wrapper.vm.agentOptions).toHaveLength(1)
    expect(wrapper.vm.recordList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.loadError).toBe(false)
    expect(wrapper.text()).toContain('13800000001')
    expect(wrapper.text()).toContain('合同纠纷')
  })

  test('列表加载失败切换错误重试态并结束加载', async () => {
    listRecord.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.loadError).toBe(true)
  })

  test('查询字段按坐席选项动态生成，状态/小结映射与统一语义色一致', async () => {
    const { wrapper } = mountPage()
    await flush()

    const items = wrapper.vm.queryFormItems
    expect(items.map(i => i.prop)).toEqual(['callerNumber', 'agentId', 'status', 'consultationCategory', 'dateRange'])
    expect(items[1].options).toEqual([{ label: '王坐席(3)', value: 3 }])
    expect(items[2].options).toHaveLength(4)

    expect(wrapper.vm.getStatusType('0')).toBe('danger')
    expect(wrapper.vm.getStatusType('2')).toBe('warning')
    expect(wrapper.vm.getStatusLabel('1')).toBe('已接')
    expect(wrapper.vm.getStatusLabel('9')).toBe('未知')

    expect(wrapper.vm.summaryStatusType('2')).toBe('success')
    expect(wrapper.vm.summaryStatusType('9')).toBe('info')
    expect(wrapper.vm.summaryStatusLabel('0')).toBe('待生成')
    expect(wrapper.vm.summaryStatusLabel('3')).toBe('生成失败')
  })

  test('多选与单选可用态随选择变化，查询重置页码', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleSelectionChange([{ recordId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)

    wrapper.vm.queryParams.pageNum = 5
    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(getList).toHaveBeenCalledTimes(1)

    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']
    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(getList).toHaveBeenCalledTimes(2)
  })

  test('详情加载话单及关联工单/转接记录', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleDetail(rows[0])
    await flush()

    expect(getRecord).toHaveBeenCalledWith(1)
    expect(wrapper.vm.detailForm.callerNumber).toBe('13800000001')
    expect(wrapper.vm.detailOpen).toBe(true)
    expect(listTicket).toHaveBeenCalledWith({ recordId: 1, pageNum: 1, pageSize: 10 })
    expect(getTransfersByRecordId).toHaveBeenCalledWith(1)
    expect(wrapper.vm.detailTickets).toHaveLength(1)
    expect(wrapper.vm.detailTransfers[0].targetAgentName).toBe('李律师')
    expect(wrapper.vm.detailLoading).toBe(false)
  })

  test('AI 通话小结生成成功更新详情，失败回拉话单同步状态', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.detailForm = { recordId: 1, callerNumber: '13800000001' }

    wrapper.vm.handleGenerateSummary(false)
    await flush()

    expect(generateAiSummary).toHaveBeenCalledWith(1, false)
    expect(modal.msgSuccess).toHaveBeenCalledWith('AI 通话小结已生成')
    expect(wrapper.vm.detailForm.aiSummary).toBe('小结内容')
    expect(wrapper.vm.summaryLoading).toBe(false)

    generateAiSummary.mockRejectedValue(new Error('ai down'))
    getRecord.mockResolvedValue({ data: { ...rows[0], summaryStatus: '3', aiSummaryFailReason: '模型超时' } })
    wrapper.vm.handleGenerateSummary(true)
    await flush()

    expect(wrapper.vm.detailForm.summaryStatus).toBe('3')
    expect(wrapper.vm.detailForm.aiSummaryFailReason).toBe('模型超时')
    expect(wrapper.vm.summaryLoading).toBe(false)
  })

  test('由话单生成工单：优先预填 AI 小结并提交后跳转', async () => {
    const { wrapper, routerPush } = mountPage()
    await flush()
    getRecord.mockResolvedValue({
      data: {
        recordId: 1,
        callerNumber: '13800000001',
        callerName: '张三',
        consultationCategory: '合同纠纷',
        consultationContent: '咨询内容',
        aiSummary: 'AI 生成的案情小结'
      }
    })

    wrapper.vm.handleCreateTicket(rows[0])
    await flush()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.ticketOpen).toBe(true)
    expect(wrapper.vm.ticketForm.title).toBe('合同纠纷工单 - 13800000001')
    expect(wrapper.vm.ticketForm.content).toBe('AI 生成的案情小结')
    expect(wrapper.vm.ticketForm.recordId).toBe(1)

    wrapper.vm.$refs.ticketForm.validate = cb => cb(true)
    wrapper.vm.submitTicket()
    await flush()

    expect(generateTicketNo).toHaveBeenCalled()
    expect(addTicket).toHaveBeenCalledWith(expect.objectContaining({
      ticketNo: 'GD20261121001',
      status: '0',
      recordId: 1
    }))
    expect(routerPush).toHaveBeenCalledWith('/business/workOrder')
    expect(wrapper.vm.ticketOpen).toBe(false)
  })

  test('工单号生成失败时使用本地兜底工单号', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.ticketForm = { recordId: 1, title: '测试工单', content: '内容', priority: '2' }
    wrapper.vm.ticketOpen = true
    await wrapper.vm.$nextTick()

    generateTicketNo.mockResolvedValue({})
    wrapper.vm.$refs.ticketForm.validate = cb => cb(true)
    wrapper.vm.submitTicket()
    await flush()

    expect(addTicket).toHaveBeenCalledWith(expect.objectContaining({
      ticketNo: expect.stringMatching(/^GD\d+$/)
    }))
  })

  test('统计弹窗拉取三维数据并绘制分类/日期图表', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleStatistics()
    await flush()
    await flush()

    expect(wrapper.vm.statisticsOpen).toBe(true)
    expect(getCallStatistics).toHaveBeenCalled()
    expect(getCallStatisticsByCategory).toHaveBeenCalled()
    expect(getCallStatisticsByDate).toHaveBeenCalledWith(7)
    expect(wrapper.vm.statistics.totalCount).toBe(100)
    expect(echarts.init).toHaveBeenCalledTimes(2)
  })

  test('新增/修改/删除话单与取消复位', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('新增来电记录')
    await wrapper.vm.$nextTick()
    wrapper.vm.form.callerNumber = '13900000000'
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.submitForm()
    await flush()

    expect(addRecord).toHaveBeenCalledWith(expect.objectContaining({ callerNumber: '13900000000' }))
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)
    expect(getList).toHaveBeenCalled()

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getRecord).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改来电记录')
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.form.recordId = 1
    wrapper.vm.submitForm()
    await flush()

    expect(updateRecord).toHaveBeenCalledWith(expect.objectContaining({ recordId: 1 }))
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条来电记录' }))
    expect(delRecord).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.open = true
    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
  })

  test('批量删除传入选中的记录ID集合', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 2 }))
    expect(delRecord).toHaveBeenCalledWith([1, 2])
  })

  test('坐席变更回填坐席姓名', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.form = { agentId: undefined }
    wrapper.vm.onAgentChange(3)
    expect(wrapper.vm.form.agentName).toBe('王坐席')

    wrapper.vm.onAgentChange(99)
    expect(wrapper.vm.form.agentName).toBe('')
  })
})
