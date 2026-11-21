'use strict'

/**
 * 用户法律咨询单测：锁定分类与历史加载、咨询提交、详情补拉、评价提交与附件处理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Consultation from '@/views/lawyers/user/consultation.vue'
import {
  submitConsultation, getUserHistory, getConsultationResult, submitEvaluation, getQuestionCategories
} from '@/api/lawyers/userConsultation'

jest.mock('@/api/lawyers/userConsultation', () => ({
  submitConsultation: jest.fn(),
  getUserHistory: jest.fn(),
  getConsultationResult: jest.fn(),
  submitEvaluation: jest.fn(),
  getQuestionCategories: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const historyRows = [
  { consultationId: 1, category: '合同纠纷', content: '合同如何解除', status: 'processing', createTime: '2026-11-01 09:00' },
  { consultationId: 2, category: '劳动争议', content: '加班费怎么算', status: 'completed', createTime: '2026-11-01 10:00' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const wrapper = mount(Consultation, {
    localVue,
    stubs: { pagination: true },
    mocks: {
      $modal: modal,
      $store: { state: { user: { userId: 7 } } }
    }
  })
  return { wrapper, modal }
}

describe('Consultation 用户法律咨询', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getQuestionCategories.mockResolvedValue({
      data: [
        { label: '合同纠纷', value: 'contract' },
        { label: '劳动争议', value: 'labor' }
      ]
    })
    getUserHistory.mockResolvedValue({ rows: historyRows, total: 2 })
    getConsultationResult.mockResolvedValue({
      data: { consultationId: 1, category: '合同纠纷', content: '合同如何解除', status: 'completed', aiAnswer: '可协商解除', confidence: 90 }
    })
    submitConsultation.mockResolvedValue({})
    submitEvaluation.mockResolvedValue({})
  })

  test('创建时加载分类与当前用户咨询历史', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getQuestionCategories).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.categories).toHaveLength(2)
    expect(wrapper.vm.queryParams.userId).toBe(7)
    expect(getUserHistory).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10, userId: 7 }))
    expect(wrapper.vm.consultationList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('合同如何解除')
  })

  test('提交咨询携带附件并重置表单刷新历史', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    let sent = null
    submitConsultation.mockImplementation(payload => {
      sent = { ...payload }
      return Promise.resolve()
    })
    wrapper.vm.$refs.consultationForm.validate = callback => callback(true)
    wrapper.vm.consultationForm = { category: 'contract', content: '合同解除需要哪些条件', attachments: '' }
    wrapper.vm.fileList = [{ name: '合同.pdf', url: 'http://file/1.pdf' }]

    wrapper.vm.submitForm()
    await flush()

    expect(sent).toEqual({
      category: 'contract',
      content: '合同解除需要哪些条件',
      attachments: JSON.stringify([{ name: '合同.pdf', url: 'http://file/1.pdf' }])
    })
    expect(submitConsultation).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('咨询提交成功，正在处理中...')
    expect(wrapper.vm.consultationForm.content).toBe('')
    expect(wrapper.vm.fileList).toEqual([])
    expect(getUserHistory).toHaveBeenCalledTimes(2)
  })

  test('表单校验未通过时不提交', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.$refs.consultationForm.validate = callback => callback(false)

    wrapper.vm.submitForm()
    await flush()

    expect(submitConsultation).not.toHaveBeenCalled()
  })

  test('查看处理中且无回答的咨询时补拉结果并更新列表', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.viewDetail(historyRows[0])
    await flush()

    expect(getConsultationResult).toHaveBeenCalledWith(1)
    expect(wrapper.vm.currentConsultation.aiAnswer).toBe('可协商解除')
    expect(wrapper.vm.consultationList[0].aiAnswer).toBe('可协商解除')
    expect(wrapper.vm.detailDialogVisible).toBe(true)
  })

  test('查看已完成咨询不重复请求结果', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.viewDetail(historyRows[1])

    expect(getConsultationResult).not.toHaveBeenCalled()
    expect(wrapper.vm.detailDialogVisible).toBe(true)
    expect(wrapper.vm.currentConsultation.status).toBe('completed')
  })

  test('打开评价弹窗并提交评价', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.showEvaluationDialog(historyRows[1])
    expect(wrapper.vm.evaluationForm.consultationId).toBe(2)
    expect(wrapper.vm.evaluationDialogVisible).toBe(true)

    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.evaluationForm.validate = callback => callback(true)
    let sent = null
    submitEvaluation.mockImplementation(payload => {
      sent = { ...payload }
      return Promise.resolve()
    })
    wrapper.vm.evaluationForm.rating = 4
    wrapper.vm.evaluationForm.feedback = '服务专业'
    wrapper.vm.submitEvaluation()
    await flush()

    expect(sent).toEqual({
      consultationId: 2,
      rating: 4,
      feedback: '服务专业',
      suggestions: ''
    })
    expect(submitEvaluation).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('评价提交成功')
    expect(wrapper.vm.evaluationDialogVisible).toBe(false)
    expect(wrapper.vm.evaluationForm.rating).toBe(5)
    expect(getUserHistory).toHaveBeenCalledTimes(2)
  })

  test('状态类型与文案映射', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.getStatusType('processing')).toBe('warning')
    expect(wrapper.vm.getStatusType('completed')).toBe('success')
    expect(wrapper.vm.getStatusType('failed')).toBe('danger')
    expect(wrapper.vm.getStatusType('pending')).toBe('info')
    expect(wrapper.vm.getStatusType('unknown')).toBe('info')
    expect(wrapper.vm.getStatusText('pending')).toBe('待处理')
    expect(wrapper.vm.getStatusText('processing')).toBe('处理中')
    expect(wrapper.vm.getStatusText('completed')).toBe('已完成')
    expect(wrapper.vm.getStatusText('unknown')).toBe('未知')
  })

  test('附件变更同步文件列表，移除前需确认', async () => {
    const { wrapper, modal } = mountPage()

    wrapper.vm.handleChange({}, [{ name: '证据.pdf' }])
    expect(wrapper.vm.fileList).toEqual([{ name: '证据.pdf' }])

    const result = wrapper.vm.beforeRemove({ name: '证据.pdf' })
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将移除附件「证据.pdf」',
      count: 1,
      countNoun: '个附件'
    }))
    await expect(result).resolves.toBeUndefined()

    wrapper.vm.handlePreview({ name: '证据.pdf' })
    wrapper.vm.handleRemove({ name: '证据.pdf' }, [])
  })

  test('刷新历史记录复用查询', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.refreshHistory()

    expect(getUserHistory).toHaveBeenCalledTimes(2)
  })
})
