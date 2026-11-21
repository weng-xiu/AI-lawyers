'use strict'

/**
 * 工单管理单测：锁定列表/统计卡、状态与优先级映射、流转动作与外派转办、
 * 工单查看时间线（含兜底组装）、新增/编辑/删除/导出、转派与批量处理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import WorkOrder from '@/views/lawyers/callCenter/workOrder.vue'
import { addDateRange } from '@/utils/ruoyi'
import request from '@/utils/request'
import {
  listTicket, getTicket, addTicket, updateTicket, delTicket, processTicket,
  completeTicket, archiveTicket, generateTicketNo, listTicketActions, transferOutTicket
} from '@/api/lawyers/callCenter'
import { listExternalOrg } from '@/api/lawyers/externalOrg'

jest.mock('@/api/lawyers/callCenter', () => ({
  listTicket: jest.fn(),
  getTicket: jest.fn(),
  addTicket: jest.fn(),
  updateTicket: jest.fn(),
  delTicket: jest.fn(),
  processTicket: jest.fn(),
  completeTicket: jest.fn(),
  archiveTicket: jest.fn(),
  generateTicketNo: jest.fn(),
  listTicketActions: jest.fn(),
  transferOutTicket: jest.fn()
}))

jest.mock('@/api/lawyers/externalOrg', () => ({
  listExternalOrg: jest.fn()
}))

jest.mock('@/utils/request', () => jest.fn())

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.prototype.addDateRange = addDateRange
localVue.prototype.download = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  {
    ticketId: 1,
    ticketNo: 'GD001',
    callerNumber: '13800000001',
    title: '合同纠纷',
    content: '客户咨询合同解除与违约责任承担方式',
    priority: 1,
    assignUserName: '王律师',
    status: '0',
    createTime: '2026-11-01 09:00:00'
  },
  {
    ticketId: 2,
    ticketNo: 'GD002',
    callerNumber: '13800000002',
    title: '劳动争议',
    content: '咨询仲裁时效',
    priority: 3,
    assignUserName: '李律师',
    status: '2',
    createTime: '2026-11-02 09:00:00'
  }
]

const wrappers = []

function mountPage(query = {}) {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()) }
  const message = { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
  const routerPush = jest.fn()
  const confirm = jest.fn(() => Promise.reject(new Error('cancel')))
  const wrapper = mount(WorkOrder, {
    localVue,
    stubs: { 'user-select': { props: ['value'], template: '<div class="user-select-stub" />' } },
    mocks: {
      $modal: modal,
      $message: message,
      $router: { push: routerPush },
      $route: { query, path: '/lawyers/callCenter/workOrder' },
      $store: { state: { user: { name: 'tester' } } },
      $confirm: confirm
    }
  })
  wrappers.push(wrapper)
  return { wrapper, modal, message, routerPush, confirm }
}

async function openForm(wrapper) {
  wrapper.vm.formOpen = true
  await wrapper.vm.$nextTick()
  wrapper.vm.$refs.ticketForm.validate = cb => cb(true)
}

describe('WorkOrder 工单管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    localStorage.clear()
    request.mockResolvedValue({ data: [] })
    const totals = { '0': 5, '1': 3, '2': 8, '3': 2 }
    listTicket.mockImplementation(params => {
      if (params.pageSize === 1 && params.status !== undefined) {
        return Promise.resolve({ rows: [], total: totals[params.status] })
      }
      return Promise.resolve({ rows, total: rows.length })
    })
    getTicket.mockResolvedValue({ data: { ...rows[0] } })
    addTicket.mockResolvedValue({})
    updateTicket.mockResolvedValue({})
    delTicket.mockResolvedValue({})
    processTicket.mockResolvedValue({})
    completeTicket.mockResolvedValue({})
    archiveTicket.mockResolvedValue({})
    generateTicketNo.mockResolvedValue({ data: 'GD009' })
    listTicketActions.mockImplementation((bizType, status) => Promise.resolve({
      data: status === '0'
        ? [{ actionCode: 'start', actionName: '受理' }, { actionCode: 'transfer' }]
        : []
    }))
    transferOutTicket.mockResolvedValue({})
    listExternalOrg.mockResolvedValue({ rows: [{ orgId: 1, orgName: '协同A' }] })
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.destroy())
  })

  test('初始化加载工单与四态统计，预填工单号查询', async () => {
    const { wrapper } = mountPage({ ticketNo: 'GD001' })
    await flush()

    expect(listTicket).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      ticketNo: 'GD001',
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.orderList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.statData).toEqual({ pending: 5, processing: 3, completed: 8, visited: 2 })
    expect(listTicketActions).toHaveBeenCalledWith('HOTLINE', '0')
    expect(wrapper.vm.actionsOf('0')).toHaveLength(2)
    expect(wrapper.vm.actionsOf('1')).toEqual([])
    expect(wrapper.text()).toContain('GD001')
  })

  test('列表加载失败结束加载', async () => {
    listTicket.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.loading).toBe(false)
  })

  test('查询字段顺序固定，状态/优先级映射复用统一语义色', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.queryFormItems.map(i => i.prop)).toEqual(['ticketNo', 'title', 'assignUserId', 'status', 'dateRange'])
    expect(wrapper.vm.queryFormItems[3].options).toHaveLength(5)

    expect(wrapper.vm.getPriorityLabel(1)).toBe('高')
    expect(wrapper.vm.getPriorityLabel(9)).toBe(9)
    expect(wrapper.vm.getPriorityTag(1)).toBe('danger')
    expect(wrapper.vm.getPriorityTag(2)).toBe('warning')
    expect(wrapper.vm.getPriorityTag(9)).toBe('info')
    expect(wrapper.vm.getStatusLabel('0')).toBe('待处理')
    expect(wrapper.vm.getStatusTag('2')).toBe('success')
    expect(wrapper.vm.getStatusClass('1')).toBe('tag-status--processing')
    expect(wrapper.vm.getConsultTypeTag('合同纠纷')).toBe('info')

    expect(wrapper.vm.flowActionName({ actionCode: 'start' })).toBe('受理')
    expect(wrapper.vm.flowActionName({ actionCode: 'transfer', actionName: '转办' })).toBe('转办')
    expect(wrapper.vm.flowActionName({ actionCode: 'custom' })).toBe('custom')
  })

  test('流转动作：受理确认后调用受理接口，转派打开外派弹窗', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    const calcStatData = jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.handleFlowAction(rows[0], { actionCode: 'start' })
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ content: '确认受理该工单？' }))
    expect(processTicket).toHaveBeenCalledWith({ ticketId: 1, processContent: '工单已受理' })
    expect(message.success).toHaveBeenCalledWith('受理成功')
    expect(getList).toHaveBeenCalled()
    expect(calcStatData).toHaveBeenCalled()

    modal.dangerConfirm.mockClear()
    wrapper.vm.handleFlowAction(rows[0], { actionCode: 'transfer' })
    await flush()

    expect(modal.dangerConfirm).not.toHaveBeenCalled()
    expect(listExternalOrg).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100, status: '0' })
    expect(wrapper.vm.transferOrgVisible).toBe(true)
    expect(wrapper.vm.externalOrgs).toHaveLength(1)

    wrapper.vm.handleFlowAction(rows[0], { actionCode: 'unknown' })
    expect(modal.dangerConfirm).not.toHaveBeenCalled()
  })

  test('外派转办校验机构必填并提交后关闭弹窗', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.transferOrgRow = rows[0]
    wrapper.vm.transferOrgForm = { orgId: null, remark: '' }
    wrapper.vm.confirmExternalTransfer()
    expect(message.warning).toHaveBeenCalledWith('请选择外派协同机构')
    expect(transferOutTicket).not.toHaveBeenCalled()

    wrapper.vm.transferOrgForm = { orgId: 1, remark: '转办说明' }
    wrapper.vm.transferOrgVisible = true
    wrapper.vm.confirmExternalTransfer()
    await flush()

    expect(transferOutTicket).toHaveBeenCalledWith({ ticketId: 1, orgId: 1, remark: '转办说明' })
    expect(message.success).toHaveBeenCalledWith('外派成功')
    expect(wrapper.vm.transferOrgVisible).toBe(false)
  })

  test('统计卡筛选按状态重查，查询重置页码与插槽字段', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.filterByStatus('2')
    expect(wrapper.vm.queryParams.status).toBe('2')
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(getList).toHaveBeenCalledTimes(1)

    wrapper.vm.queryParams.pageNum = 4
    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)

    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']
    wrapper.vm.queryParams.assignUserId = 3
    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.assignUserId).toBeUndefined()
    expect(getList).toHaveBeenCalledTimes(3)
  })

  test('多选映射主键并维护全选态，全选委托表格', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([{ ticketId: 1 }, { ticketId: 2 }])
    expect(wrapper.vm.selectedIds).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)
    expect(wrapper.vm.isAllSelected).toBe(true)

    wrapper.vm.handleSelectionChange([{ ticketId: 1 }])
    expect(wrapper.vm.isAllSelected).toBe(false)

    const toggle = jest.fn()
    wrapper.vm.$refs.proTable.$refs.table.toggleAllSelection = toggle
    wrapper.vm.handleSelectAll(true)
    expect(toggle).toHaveBeenCalled()
  })

  test('批量办理：未选提示，选中后逐条办结', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.handleBatchProcess()
    expect(message.warning).toHaveBeenCalledWith('请先选择要处理的工单')

    wrapper.vm.selectedIds = [1, 2]
    wrapper.vm.handleBatchProcess()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 2, countNoun: '条工单' }))
    expect(completeTicket).toHaveBeenCalledTimes(2)
    expect(completeTicket).toHaveBeenCalledWith({ ticketId: 1 })
    expect(message.success).toHaveBeenCalledWith('批量处理成功')
    expect(wrapper.vm.loading).toBe(false)
  })

  test('导出去除分页参数', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleExport()

    expect(wrapper.vm.download).toHaveBeenCalledWith(
      'lawyers/call/ticket/export',
      expect.not.objectContaining({ pageNum: expect.anything() }),
      expect.stringContaining('workOrder_')
    )
  })

  test('新增工单自动生成编号，提交后清草稿并刷新', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleAdd()
    await flush()

    expect(generateTicketNo).toHaveBeenCalled()
    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.form.ticketNo).toBe('GD009')
    expect(wrapper.vm.form.priority).toBe(2)
    expect(wrapper.vm.formOpen).toBe(true)

    wrapper.vm.form.title = '新工单'
    wrapper.vm.form.content = '工单内容'
    await openForm(wrapper)
    wrapper.vm.submitForm()
    await flush()

    expect(addTicket).toHaveBeenCalledWith(expect.objectContaining({ title: '新工单', content: '工单内容' }))
    expect(message.success).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.formOpen).toBe(false)
    expect(wrapper.vm.draftSubmitting).toBe(false)
    expect(getList).toHaveBeenCalled()
  })

  test('编辑工单回填并补默认优先级，提交走修改接口', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    getTicket.mockResolvedValue({ data: { ticketId: 1, ticketNo: 'GD001', title: '合同纠纷', content: '内容' } })

    wrapper.vm.handleEdit(rows[0])
    await flush()

    expect(getTicket).toHaveBeenCalledWith(1)
    expect(wrapper.vm.isEdit).toBe(true)
    expect(wrapper.vm.form.ticketNo).toBe('GD001')
    expect(wrapper.vm.form.priority).toBe(2)
    expect(wrapper.vm.formOpen).toBe(true)

    await openForm(wrapper)
    wrapper.vm.submitForm()
    await flush()

    expect(updateTicket).toHaveBeenCalledWith(expect.objectContaining({ ticketId: 1 }))
    expect(message.success).toHaveBeenCalledWith('修改成功')
  })

  test('查看工单加载时间线，接口无数据时按工单字段兜底组装', async () => {
    const { wrapper } = mountPage()
    await flush()
    getTicket.mockResolvedValue({
      data: {
        ...rows[0],
        createTime: '2026-11-01 09:00:00',
        assignTime: '2026-11-01 10:00:00',
        completeTime: '2026-11-01 11:00:00',
        archiveTime: '2026-11-01 12:00:00'
      }
    })

    wrapper.vm.handleView(rows[0])
    await flush()

    expect(getTicket).toHaveBeenCalledWith(1)
    expect(request).toHaveBeenCalledWith({ url: '/lawyers/call/ticket/1/timeline', method: 'get' })
    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.activeTab).toBe('basic')
    expect(wrapper.vm.timelineList.map(i => i.title)).toEqual(['工单创建', '开始处理', '处理完成', '工单归档'])

    request.mockResolvedValue({ data: [{ operateTime: '2026-11-03 09:00:00', nodeName: '受理', operator: '王律师', content: '已受理' }] })
    wrapper.vm.handleView(rows[0])
    await flush()

    expect(wrapper.vm.timelineList).toEqual([expect.objectContaining({
      time: '2026-11-03 09:00:00',
      title: '受理',
      user: '王律师',
      description: '已受理'
    })])
  })

  test('无流转数据时兜底为工单信息节点', () => {
    const { wrapper } = mountPage()

    wrapper.vm.currentOrder = { content: '工单内容', assignUserName: '王律师' }
    wrapper.vm.buildFallbackTimeline()

    expect(wrapper.vm.timelineList).toEqual([expect.objectContaining({
      title: '工单信息',
      user: '王律师',
      description: '工单内容'
    })])
  })

  test('删除工单确认后刷新列表与统计', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.handleDelete(rows[0])
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ content: expect.stringContaining('GD001') }))
    expect(delTicket).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('删除成功')
  })

  test('转派工单：未选办理人提示，单条转派后关闭抽屉', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.currentOrder = rows[0]
    wrapper.vm.handleTransfer()
    expect(wrapper.vm.transferOpen).toBe(true)
    expect(wrapper.vm.transferRow.ticketId).toBe(1)

    wrapper.vm.confirmTransfer()
    expect(message.warning).toHaveBeenCalledWith('请选择新办理人')

    wrapper.vm.transferUserId = 8
    wrapper.vm.transferUserName = '赵律师'
    wrapper.vm.drawerVisible = true
    wrapper.vm.confirmTransfer()
    await flush()

    expect(processTicket).toHaveBeenCalledWith({
      ticketId: 1,
      processContent: '工单转派给：赵律师',
      assignUserId: 8,
      assignUserName: '赵律师'
    })
    expect(message.success).toHaveBeenCalledWith('工单已转派')
    expect(wrapper.vm.transferOpen).toBe(false)
    expect(wrapper.vm.drawerVisible).toBe(false)
  })

  test('批量转派：未选提示，选中后循环转派', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.handleBatchTransfer()
    expect(message.warning).toHaveBeenCalledWith('请先选择要转派的工单')

    wrapper.vm.selectedIds = [1, 2]
    wrapper.vm.handleBatchTransfer()
    expect(wrapper.vm.transferOpen).toBe(true)
    expect(wrapper.vm.transferIds).toEqual([1, 2])

    wrapper.vm.transferUserId = 8
    wrapper.vm.confirmTransfer()
    await flush()

    expect(processTicket).toHaveBeenCalledTimes(2)
    expect(message.success).toHaveBeenCalledWith('已成功转派 2 条工单')
    expect(wrapper.vm.transferOpen).toBe(false)
  })

  test('退回/归档/回访操作', async () => {
    const { wrapper, modal, message, routerPush } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    jest.spyOn(wrapper.vm, 'calcStatData').mockImplementation(() => {})

    wrapper.vm.currentOrder = rows[0]
    wrapper.vm.drawerVisible = true
    wrapper.vm.handleReturn()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ content: '确认将该工单退回待处理？' }))
    expect(updateTicket).toHaveBeenCalledWith({ ticketId: 1, status: '0', processContent: '工单退回待处理' })
    expect(message.success).toHaveBeenCalledWith('工单已退回')
    expect(wrapper.vm.drawerVisible).toBe(false)

    wrapper.vm.drawerVisible = true
    wrapper.vm.handleArchive()
    await flush()
    expect(archiveTicket).toHaveBeenCalledWith({ ticketId: 1 })
    expect(message.success).toHaveBeenCalledWith('工单已归档')

    wrapper.vm.handleVisit()
    expect(routerPush).toHaveBeenCalledWith({
      path: '/business/callback',
      query: { phone: '13800000001', ticketNo: 'GD001' }
    })
  })

  test('办理人/转派人变更回填姓名', () => {
    const { wrapper } = mountPage()

    wrapper.vm.onAssignUserChange(3, { nickName: '王律师' })
    expect(wrapper.vm.form.assignUserName).toBe('王律师')

    wrapper.vm.onAssignUserChange(null, null)
    expect(wrapper.vm.form.assignUserName).toBe('')

    wrapper.vm.onTransferUserChange(8, { userName: '赵律师' })
    expect(wrapper.vm.transferUserName).toBe('赵律师')

    wrapper.vm.onTransferUserChange(null, null)
    expect(wrapper.vm.transferUserName).toBe('')
  })
})
