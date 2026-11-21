'use strict'

/**
 * 咨询登记台账单测：锁定列表加载与统计计算、类型/满意度映射、模板与来电记录自动填充、
 * 新增/编辑/删除/导出，以及编辑态"先转工单再保存"的联动。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import CallLedger from '@/views/lawyers/callCenter/callLedger.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listLedger, getLedger, addLedger, updateLedger, delLedger, generateLedgerNo,
  getLedgerTemplates, autoFillLedger, transferLedgerToTicket
} from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callCenter', () => ({
  listLedger: jest.fn(),
  getLedger: jest.fn(),
  addLedger: jest.fn(),
  updateLedger: jest.fn(),
  delLedger: jest.fn(),
  generateLedgerNo: jest.fn(),
  getLedgerTemplates: jest.fn(),
  autoFillLedger: jest.fn(),
  transferLedgerToTicket: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.prototype.addDateRange = addDateRange
localVue.prototype.download = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { ledgerId: 1, ledgerNo: 'LD001', callerName: '张三', callerPhone: '13800000001', serviceType: '1', satisfaction: '1', consultDuration: 30 },
  { ledgerId: 2, ledgerNo: 'LD002', callerName: '李四', callerPhone: '13800000002', serviceType: '2', satisfaction: '2', consultDuration: 45 },
  { ledgerId: 3, ledgerNo: 'LD003', callerName: '王五', callerPhone: '13800000003', serviceType: '3', satisfaction: '3' },
  { ledgerId: 4, ledgerNo: 'LD004', callerName: '赵六', callerPhone: '13800000004', serviceType: '4', satisfaction: null }
]

const wrappers = []

function mountPage() {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()) }
  const message = { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
  const wrapper = mount(CallLedger, {
    localVue,
    stubs: { 'user-select': { props: ['value'], template: '<div class="user-select-stub" />' } },
    mocks: { $modal: modal, $message: message }
  })
  wrappers.push(wrapper)
  return { wrapper, modal, message }
}

async function openForm(wrapper) {
  wrapper.vm.formOpen = true
  await wrapper.vm.$nextTick()
  wrapper.vm.$refs.ledgerForm.validate = cb => cb(true)
}

describe('CallLedger 咨询登记台账', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    localStorage.clear()
    listLedger.mockResolvedValue({ rows, total: 4 })
    getLedger.mockResolvedValue({ data: { ...rows[0], consultContent: '咨询内容' } })
    addLedger.mockResolvedValue({})
    updateLedger.mockResolvedValue({})
    delLedger.mockResolvedValue({})
    generateLedgerNo.mockResolvedValue({ data: 'LD009' })
    getLedgerTemplates.mockResolvedValue({ data: [{ templateName: '合同模板', categoryName: '合同纠纷', subCategory: '解除', serviceType: '2', sourceChannel: '现场咨询', contentTemplate: '模板内容' }] })
    autoFillLedger.mockResolvedValue({
      data: { recordId: 9, callerName: '李四', callerPhone: '13900000000', categoryId: 2, categoryName: '劳动争议', consultContent: '自动填充内容', serviceType: '2', consultDuration: 20 }
    })
    transferLedgerToTicket.mockResolvedValue({ data: { ticketId: 88 } })
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.destroy())
  })

  test('初始化加载登记列表与模板，并按服务方式/满意度计算统计', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listLedger).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(getLedgerTemplates).toHaveBeenCalled()
    expect(wrapper.vm.ledgerList).toHaveLength(4)
    expect(wrapper.vm.total).toBe(4)
    expect(wrapper.vm.templates).toHaveLength(1)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.statistics).toEqual({
      totalCount: 4,
      phoneCount: 1,
      siteCount: 1,
      onlineCount: 1,
      videoCount: 1,
      avgSatisfaction: '80.0'
    })
    expect(wrapper.text()).toContain('LD001')
  })

  test('列表加载失败结束加载，无满意度数据时均值为 0', async () => {
    listLedger.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.loading).toBe(false)

    wrapper.vm.ledgerList = rows.map(r => ({ ...r, satisfaction: null }))
    wrapper.vm.total = 4
    wrapper.vm.calcStatistics()
    expect(wrapper.vm.statistics.avgSatisfaction).toBe(0)
  })

  test('查询字段顺序与服务方式/满意度选项固定', async () => {
    const { wrapper } = mountPage()
    await flush()

    const items = wrapper.vm.queryFormItems
    expect(items.map(i => i.prop)).toEqual(['dateRange', 'categoryName', 'callerName', 'callerPhone', 'lawyerId', 'serviceType', 'satisfaction'])
    expect(items[5].options).toHaveLength(5)
    expect(items[6].options[0]).toEqual({ label: '全部', value: '' })
  })

  test('类型/满意度/性别映射与标签语义色', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.getConsultTypeTag('任意')).toBe('info')
    expect(wrapper.vm.getServiceTypeLabel('1')).toBe('电话咨询')
    expect(wrapper.vm.getServiceTypeLabel('9')).toBe('未知')
    expect(wrapper.vm.getServiceTypeTag('2')).toBe('success')
    expect(wrapper.vm.getServiceTypeTag('9')).toBe('info')
    expect(wrapper.vm.getSatisfactionLabel('4')).toBe('不满意')
    expect(wrapper.vm.getSatisfactionStars('1')).toBe(5)
    expect(wrapper.vm.getSatisfactionStars('4')).toBe(1)
    expect(wrapper.vm.getSatisfactionStars(null)).toBe(0)
    expect(wrapper.vm.getGenderLabel(1)).toBe('女')
    expect(wrapper.vm.getGenderLabel(9)).toBe('未知')
  })

  test('多选映射主键，详情按登记ID加载', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([{ ledgerId: 1 }, { ledgerId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])

    wrapper.vm.handleDetail(rows[0])
    await flush()

    expect(getLedger).toHaveBeenCalledWith(1)
    expect(wrapper.vm.detailForm.ledgerNo).toBe('LD001')
    expect(wrapper.vm.detailOpen).toBe(true)
  })

  test('查询重置页码，重置清空日期范围与承办律师', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(getList).toHaveBeenCalledTimes(1)

    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']
    wrapper.vm.queryParams.lawyerId = 7
    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.lawyerId).toBeUndefined()
    expect(getList).toHaveBeenCalledTimes(2)
  })

  test('新增登记自动生成编号并带出默认值', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    await flush()

    expect(generateLedgerNo).toHaveBeenCalled()
    expect(wrapper.vm.form.ledgerNo).toBe('LD009')
    expect(wrapper.vm.form.serviceType).toBe('1')
    expect(wrapper.vm.form.satisfaction).toBe('2')
    expect(wrapper.vm.form.callerGender).toBe('2')
    expect(wrapper.vm.formOpen).toBe(true)
    expect(wrapper.vm.formTitle).toBe('新增咨询登记')
  })

  test('按模板新增登记预填分类/服务方式/内容模板', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleApplyTemplate(wrapper.vm.templates[0])
    await flush()

    expect(wrapper.vm.form.ledgerNo).toBe('LD009')
    expect(wrapper.vm.form.categoryName).toBe('合同纠纷')
    expect(wrapper.vm.form.subCategory).toBe('解除')
    expect(wrapper.vm.form.serviceType).toBe('2')
    expect(wrapper.vm.form.consultContent).toBe('模板内容')
    expect(wrapper.vm.formTitle).toBe('新增登记（模板：合同模板）')
  })

  test('来电记录自动填充：未填ID提示，填充时保留缺失字段原值', async () => {
    const { wrapper, message } = mountPage()
    await flush()

    wrapper.vm.handleAutoFill()
    expect(message.warning).toHaveBeenCalledWith('请输入来电记录ID')
    expect(autoFillLedger).not.toHaveBeenCalled()

    wrapper.vm.form.callerAddress = '原地址'
    wrapper.vm.form.lawyerName = '原律师'
    wrapper.vm.autoFillRecordId = '9'
    wrapper.vm.handleAutoFill()
    await flush()

    expect(autoFillLedger).toHaveBeenCalledWith('9')
    expect(wrapper.vm.form.recordId).toBe(9)
    expect(wrapper.vm.form.callerName).toBe('李四')
    expect(wrapper.vm.form.categoryName).toBe('劳动争议')
    expect(wrapper.vm.form.consultDuration).toBe(20)
    expect(wrapper.vm.form.callerAddress).toBe('原地址')
    expect(wrapper.vm.form.lawyerName).toBe('原律师')
    expect(message.success).toHaveBeenCalledWith('已从来电记录自动填充信息')
  })

  test('编辑登记回填表单与来电记录ID', async () => {
    const { wrapper } = mountPage()
    await flush()
    getLedger.mockResolvedValue({ data: { ...rows[0], recordId: 12 } })

    wrapper.vm.handleEdit(rows[0])
    await flush()

    expect(getLedger).toHaveBeenCalledWith(1)
    expect(wrapper.vm.form.ledgerNo).toBe('LD001')
    expect(wrapper.vm.autoFillRecordId).toBe('12')
    expect(wrapper.vm.formTitle).toBe('编辑咨询登记')
    expect(wrapper.vm.formOpen).toBe(true)
  })

  test('新增与修改提交后关闭弹窗并刷新列表', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.resetForm()
    wrapper.vm.form.callerName = '张三'
    wrapper.vm.form.callerPhone = '13800000001'
    await openForm(wrapper)
    wrapper.vm.submitForm()
    await flush()

    expect(addLedger).toHaveBeenCalledWith(expect.objectContaining({ callerName: '张三' }))
    expect(message.success).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.formOpen).toBe(false)
    expect(getList).toHaveBeenCalled()
    expect(wrapper.vm.submitting).toBe(false)

    wrapper.vm.resetForm()
    wrapper.vm.form.ledgerId = 1
    await openForm(wrapper)
    wrapper.vm.submitForm()
    await flush()

    expect(updateLedger).toHaveBeenCalledWith(expect.objectContaining({ ledgerId: 1 }))
    expect(message.success).toHaveBeenCalledWith('修改成功')
  })

  test('编辑态勾选转工单时先转工单再保存', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.resetForm()
    wrapper.vm.form.ledgerId = 1
    wrapper.vm.transferTicketFlag = true
    await openForm(wrapper)
    wrapper.vm.submitForm()
    await flush()

    expect(transferLedgerToTicket).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('已转为工单 #88')
    expect(wrapper.vm.form.ticketId).toBe(88)
    expect(updateLedger).toHaveBeenCalled()
  })

  test('校验不通过不提交', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.formOpen = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.ledgerForm.validate = cb => cb(false)
    wrapper.vm.submitForm()
    await flush()

    expect(addLedger).not.toHaveBeenCalled()
    expect(updateLedger).not.toHaveBeenCalled()
  })

  test('删除/导出/打印操作', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ content: expect.stringContaining('LD001') }))
    expect(delLedger).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('删除成功')

    wrapper.vm.handleExport()
    expect(wrapper.vm.download).toHaveBeenCalledWith(
      'lawyers/call/ledger/export',
      expect.any(Object),
      expect.stringContaining('ledger_')
    )

    wrapper.vm.ids = []
    wrapper.vm.handleBatchPrint()
    expect(message.warning).toHaveBeenCalledWith('请先选择要打印的记录')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleBatchPrint()
    expect(message.success).toHaveBeenCalledWith('批量打印任务已提交')

    wrapper.vm.handlePrint()
    expect(message.success).toHaveBeenCalledWith('打印任务已提交')
  })

  test('选择承办律师时同步回填律师姓名', () => {
    const { wrapper } = mountPage()

    wrapper.vm.onLawyerChange(5, { nickName: '王律师' })
    expect(wrapper.vm.form.lawyerName).toBe('王律师')

    wrapper.vm.onLawyerChange(null, { userName: '李律师' })
    expect(wrapper.vm.form.lawyerName).toBe('李律师')

    wrapper.vm.onLawyerChange(null, null)
    expect(wrapper.vm.form.lawyerName).toBe('')
  })
})
