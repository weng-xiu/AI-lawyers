'use strict'

/**
 * 呼叫工单单测：锁定列表查询与错误态、状态/优先级映射、详情、处理流程与增删改。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import CallTicket from '@/views/lawyers/callCenter/callTicket.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listTicket, getTicket, addTicket, updateTicket, delTicket, processTicket, completeTicket, archiveTicket
} from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callCenter', () => ({
  listTicket: jest.fn(),
  getTicket: jest.fn(),
  addTicket: jest.fn(),
  updateTicket: jest.fn(),
  delTicket: jest.fn(),
  processTicket: jest.fn(),
  completeTicket: jest.fn(),
  archiveTicket: jest.fn()
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
  { ticketId: 1, ticketNo: 'T001', title: '合同纠纷咨询', status: '0', priority: '2', assignUserName: '张律师', recordId: 9001, createTime: '2026-11-01 09:00' },
  { ticketId: 2, ticketNo: 'T002', title: '劳动仲裁咨询', status: '1', priority: '1', assignUserName: '李律师', recordId: 9002, createTime: '2026-11-01 10:00' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const wrapper = mount(CallTicket, {
    localVue,
    stubs: { 'user-select': { props: ['value'], template: '<div class="user-select-stub" />' } },
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('CallTicket 呼叫工单', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listTicket.mockResolvedValue({ rows, total: 2 })
    getTicket.mockResolvedValue({ data: { ...rows[0], content: '客户咨询合同解除', processResult: '' } })
    addTicket.mockResolvedValue({})
    updateTicket.mockResolvedValue({})
    delTicket.mockResolvedValue({})
    processTicket.mockResolvedValue({})
    completeTicket.mockResolvedValue({})
    archiveTicket.mockResolvedValue({})
  })

  test('创建时携带日期范围查询工单', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listTicket).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.ticketList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.loadError).toBe(false)
    expect(wrapper.text()).toContain('合同纠纷咨询')
    expect(wrapper.text()).toContain('待处理')
  })

  test('查询失败切换错误重试态并结束加载', async () => {
    listTicket.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.loadError).toBe(true)
  })

  test('状态与优先级映射复用统一语义色', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.getStatusType('0')).toBe('warning')
    expect(wrapper.vm.getStatusType('2')).toBe('success')
    expect(wrapper.vm.getStatusLabel('1')).toBe('处理中')
    expect(wrapper.vm.getStatusClass('1')).toBe('tag-status--processing')
    expect(wrapper.vm.getStatusLabel('9')).toBe('未知')

    expect(wrapper.vm.getPriorityType('0')).toBe('info')
    expect(wrapper.vm.getPriorityType('2')).toBe('danger')
    expect(wrapper.vm.getPriorityType('9')).toBe('info')
    expect(wrapper.vm.getPriorityLabel('1')).toBe('中')
    expect(wrapper.vm.getPriorityLabel('9')).toBe('未知')
  })

  test('查询重置页码，重置清空日期范围', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listTicket).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(listTicket).toHaveBeenCalledTimes(3)
  })

  test('多选联动编号与按钮状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('新增重置表单，修改与详情读取工单', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('新增工单')
    expect(wrapper.vm.form.status).toBe('0')
    expect(wrapper.vm.form.priority).toBe('0')
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')

    wrapper.vm.handleUpdate(rows[1])
    await flush()
    expect(getTicket).toHaveBeenCalledWith(2)
    expect(wrapper.vm.title).toBe('修改工单')
    expect(wrapper.vm.form.ticketNo).toBe('T001')

    wrapper.vm.handleDetail(rows[0])
    await flush()
    expect(wrapper.vm.detailForm.title).toBe('合同纠纷咨询')
    expect(wrapper.vm.detailOpen).toBe(true)
  })

  test('处理人选择回填姓名，空值置空', () => {
    const { wrapper } = mountPage()

    wrapper.vm.onAssignUserChange(1, { nickName: '张律师' })
    expect(wrapper.vm.form.assignUserName).toBe('张律师')

    wrapper.vm.onAssignUserChange(2, { userName: 'legal' })
    expect(wrapper.vm.form.assignUserName).toBe('legal')

    wrapper.vm.onAssignUserChange(null, null)
    expect(wrapper.vm.form.assignUserName).toBe('')
  })

  test('开始处理与归档后提示并刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleProcess(rows[0])
    await flush()
    expect(processTicket).toHaveBeenCalledWith({ ticketId: 1 })
    expect(modal.msgSuccess).toHaveBeenCalledWith('工单开始处理')
    expect(listTicket).toHaveBeenCalledTimes(2)

    wrapper.vm.handleArchive(rows[1])
    await flush()
    expect(archiveTicket).toHaveBeenCalledWith({ ticketId: 2 })
    expect(modal.msgSuccess).toHaveBeenCalledWith('工单已归档')
    expect(listTicket).toHaveBeenCalledTimes(3)
  })

  test('完成工单打开处理弹窗并提交处理结果', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleComplete(rows[1])
    expect(wrapper.vm.processOpen).toBe(true)
    expect(wrapper.vm.processForm.ticketId).toBe(2)
    expect(wrapper.vm.processForm.processResult).toBeUndefined()

    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.processForm.validate = callback => callback(true)
    wrapper.vm.processForm.processResult = '已出具法律意见'
    wrapper.vm.submitProcess()
    await flush()

    expect(completeTicket).toHaveBeenCalledWith({
      ticketId: 2,
      processResult: '已出具法律意见'
    })
    expect(modal.msgSuccess).toHaveBeenCalledWith('工单已完成')
    expect(wrapper.vm.processOpen).toBe(false)
    expect(listTicket).toHaveBeenCalledTimes(2)
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { title: '新工单', content: '内容', priority: '1' }
    wrapper.vm.submitForm()
    await flush()
    expect(addTicket).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { ticketId: 2, title: '劳动仲裁咨询', content: '内容' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateTicket).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单条与批量删除按数量提示，取消不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条工单' }))
    expect(delTicket).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delTicket).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(delTicket).toHaveBeenCalledTimes(2)
  })

  test('取消关闭弹窗并重置表单', () => {
    const { wrapper } = mountPage()
    wrapper.vm.open = true
    wrapper.vm.form = { title: '临时工单' }

    wrapper.vm.cancel()

    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.title).toBeUndefined()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')
  })
})
