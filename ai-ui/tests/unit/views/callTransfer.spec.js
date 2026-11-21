'use strict'

/**
 * 呼叫转接单测：锁定列表加载、坐席下拉、新增提交、批量删除与导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import CallTransfer from '@/views/lawyers/callCenter/callTransfer.vue'
import { listTransfer, getTransfer, addTransfer, delTransfer } from '@/api/lawyers/callTransfer'
import { listAgent } from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callTransfer', () => ({
  listTransfer: jest.fn(),
  getTransfer: jest.fn(),
  addTransfer: jest.fn(),
  delTransfer: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = value => (value ? '2026-11-01 10:00' : null)
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const agents = [
  { agentId: 101, agentName: '张律师' },
  { agentId: 102, agentName: '李律师' }
]

const rows = [
  { transferId: 1, recordId: 9001, fromAgentName: '张律师', toAgentName: '李律师', reason: '专业不符', transferTime: '2026-11-01 10:00' },
  { transferId: 2, recordId: 9002, fromAgentName: '李律师', toAgentName: '张律师', reason: '客户要求', transferTime: '2026-11-01 11:00' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(CallTransfer, {
    localVue,
    mocks: { $modal: modal, download }
  })
  return { wrapper, modal, download }
}

describe('CallTransfer 呼叫转接', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listTransfer.mockResolvedValue({ rows, total: 2 })
    getTransfer.mockResolvedValue({ data: { ...rows[0], createBy: 'admin', createTime: '2026-11-01' } })
    addTransfer.mockResolvedValue({})
    delTransfer.mockResolvedValue({})
    listAgent.mockResolvedValue({ rows: agents })
  })

  test('创建时并行加载坐席选项与转接列表', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listAgent).toHaveBeenCalledWith({ pageSize: 999, status: '1' })
    expect(listTransfer).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.agentOptions).toHaveLength(2)
    expect(wrapper.vm.transferList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('专业不符')
  })

  test('查询下拉选项由坐席列表映射生成', async () => {
    const { wrapper } = mountPage()
    await flush()

    const items = wrapper.vm.queryFormItems
    expect(items).toHaveLength(3)
    expect(items[1].prop).toBe('fromAgentId')
    expect(items[1].options).toEqual([
      { label: '张律师(101)', value: 101 },
      { label: '李律师(102)', value: 102 }
    ])
    expect(items[2].options).toEqual(items[1].options)
  })

  test('坐席选择回填名称，未知坐席置空', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.onFromAgentChange(101)
    expect(wrapper.vm.form.fromAgentName).toBe('张律师')

    wrapper.vm.onToAgentChange(999)
    expect(wrapper.vm.form.toAgentName).toBe('')

    wrapper.vm.onToAgentChange(102)
    expect(wrapper.vm.form.toAgentName).toBe('李律师')
  })

  test('新增重置表单并打开弹窗', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()

    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('新增转接记录')
    expect(wrapper.vm.form.recordId).toBeUndefined()
    expect(wrapper.vm.form.transferId).toBeUndefined()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')
  })

  test('多选联动转接编号与删除按钮状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('查看转接详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleDetail(rows[1])
    await flush()

    expect(getTransfer).toHaveBeenCalledWith(2)
    expect(wrapper.vm.detailForm.transferId).toBe(1)
    expect(wrapper.vm.detailOpen).toBe(true)
  })

  test('提交新增后关闭弹窗并刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)
    wrapper.vm.form = { recordId: 9003, fromAgentId: 101, toAgentId: 102, reason: '转接' }

    wrapper.vm.submitForm()
    await flush()

    expect(addTransfer).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)
    expect(listTransfer).toHaveBeenCalledTimes(2)
  })

  test('删除单条与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将删除选中的转接记录',
      count: 1,
      countNoun: '条转接记录'
    }))
    expect(delTransfer).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delTransfer).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('取消确认时不删除，取消弹窗重置表单', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(delTransfer).not.toHaveBeenCalled()

    wrapper.vm.open = true
    wrapper.vm.form = { recordId: 9001 }
    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.recordId).toBeUndefined()
  })

  test('查询与重置复用列表加载并支持导出', async () => {
    const { wrapper, download } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listTransfer).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(listTransfer).toHaveBeenCalledTimes(3)

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'lawyers/call/transfer/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^transfer_\d+\.xlsx$/)
    )
  })
})
