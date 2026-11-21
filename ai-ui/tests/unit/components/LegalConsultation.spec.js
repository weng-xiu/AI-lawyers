'use strict'

/**
 * LegalConsultation 智能咨询单测：锁定分类加载、表单校验、提交结果与重置。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import LegalConsultation from '@/components/LegalConsultation/index.vue'
import { getValidCategories } from '@/api/lawyers/category'
import { addConsultation } from '@/api/lawyers/consultation'

jest.mock('@/api/lawyers/category', () => ({ getValidCategories: jest.fn() }))
jest.mock('@/api/lawyers/consultation', () => ({ addConsultation: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

function mountConsultation() {
  const message = { success: jest.fn(), error: jest.fn() }
  const push = jest.fn()
  const wrapper = mount(LegalConsultation, {
    localVue,
    mocks: {
      $store: { getters: { userId: 7, name: '张三' } },
      $router: { push },
      $message: message
    }
  })
  return { wrapper, message, push }
}

describe('LegalConsultation', () => {
  beforeEach(() => {
    getValidCategories.mockReset()
    addConsultation.mockReset()
    getValidCategories.mockResolvedValue({
      data: [{ categoryId: 1, categoryName: '婚姻家庭' }, { categoryId: 2, categoryName: '劳动争议' }]
    })
  })

  test('挂载后加载分类并渲染选项', async () => {
    const { wrapper } = mountConsultation()
    await flush()

    expect(wrapper.vm.categories).toHaveLength(2)
    const options = wrapper.findAllComponents({ name: 'ElOption' })
    expect(options.at(0).props('label')).toBe('婚姻家庭')
    expect(options.at(1).props('value')).toBe(2)
  })

  test('表单不合法时不会提交', async () => {
    const { wrapper } = mountConsultation()
    await flush()

    wrapper.vm.submitConsultation()
    await flush()

    expect(addConsultation).not.toHaveBeenCalled()
  })

  test('提交成功后展示回答并提示', async () => {
    addConsultation.mockResolvedValue({ data: { consultationId: 9, answer: '建议保留证据' } })
    const { wrapper, message } = mountConsultation()
    await flush()
    wrapper.vm.consultationForm.categoryId = 1
    wrapper.vm.consultationForm.question = '公司拖欠工资三个月，我该如何维权？'

    wrapper.vm.submitConsultation()
    await flush()

    expect(addConsultation).toHaveBeenCalledWith({
      userId: 7,
      question: '公司拖欠工资三个月，我该如何维权？',
      categoryId: 1,
      status: '0'
    })
    expect(wrapper.vm.currentConsultationId).toBe(9)
    expect(wrapper.vm.consultationResult).toBe('建议保留证据')
    expect(wrapper.vm.resultVisible).toBe(true)
    expect(wrapper.vm.loading).toBe(false)
    expect(message.success).toHaveBeenCalledWith('咨询提交成功')
  })

  test('提交失败时给出错误提示并结束加载', async () => {
    addConsultation.mockRejectedValue(new Error('network'))
    const { wrapper, message } = mountConsultation()
    await flush()
    wrapper.vm.consultationForm.categoryId = 1
    wrapper.vm.consultationForm.question = '公司拖欠工资三个月，我该如何维权？'

    wrapper.vm.submitConsultation()
    await flush()

    expect(message.error).toHaveBeenCalledWith('咨询提交失败，请稍后重试')
    expect(wrapper.vm.loading).toBe(false)
  })

  test('重置清空结果与表单', async () => {
    const { wrapper } = mountConsultation()
    await flush()
    wrapper.vm.consultationForm.categoryId = 1
    wrapper.vm.consultationForm.question = '问题描述'
    wrapper.vm.resultVisible = true
    wrapper.vm.consultationResult = '回答'
    wrapper.vm.currentConsultationId = 9

    wrapper.vm.resetForm()
    await flush()

    expect(wrapper.vm.consultationForm.categoryId).toBeUndefined()
    expect(wrapper.vm.resultVisible).toBe(false)
    expect(wrapper.vm.consultationResult).toBe('')
    expect(wrapper.vm.currentConsultationId).toBeNull()
  })

  test('保存记录与查看历史', () => {
    const { wrapper, message, push } = mountConsultation()

    wrapper.vm.saveRecord()
    expect(message.success).toHaveBeenCalledWith('咨询记录已保存')

    wrapper.vm.viewHistory()
    expect(push).toHaveBeenCalledWith('/lawyers/consultation/history')
  })
})
