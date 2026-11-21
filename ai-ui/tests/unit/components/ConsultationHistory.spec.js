'use strict'

/**
 * ConsultationHistory 咨询历史单测：锁定登录校验、列表渲染、详情、删除与分页。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ConsultationHistory from '@/components/ConsultationHistory/index.vue'
import { getUserConsultations, delConsultation } from '@/api/lawyers/consultation'

jest.mock('@/api/lawyers/consultation', () => ({
  getUserConsultations: jest.fn(),
  delConsultation: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

function flush() {
  return new Promise(resolve => setTimeout(resolve, 0))
}

const records = [
  { consultationId: 1, categoryName: '婚姻家庭', status: '1', question: '离婚财产如何分割？', answer: '按共同财产处理', createTime: '2026-01-02 10:00:00' },
  { consultationId: 2, categoryName: '劳动争议', status: '0', question: '拖欠工资怎么办？', answer: '', createTime: '2026-01-03 11:00:00' }
]

function mountHistory(userId = 7) {
  const message = { success: jest.fn(), error: jest.fn() }
  const dangerConfirm = jest.fn(() => Promise.resolve())
  const wrapper = mount(ConsultationHistory, {
    localVue,
    mocks: {
      $store: { getters: { userId } },
      $message: message,
      $modal: { dangerConfirm }
    }
  })
  return { wrapper, message, dangerConfirm }
}

describe('ConsultationHistory', () => {
  beforeEach(() => {
    getUserConsultations.mockReset()
    delConsultation.mockReset()
    getUserConsultations.mockResolvedValue({ data: records.map(record => ({ ...record })) })
  })

  test('未登录时提示且不请求接口', async () => {
    const { wrapper, message } = mountHistory(null)
    await flush()

    expect(message.error).toHaveBeenCalledWith('用户未登录')
    expect(getUserConsultations).not.toHaveBeenCalled()
    expect(wrapper.find('.empty-history').exists()).toBe(true)
  })

  test('加载历史后渲染时间线与分页', async () => {
    const { wrapper } = mountHistory()
    await flush()

    expect(getUserConsultations).toHaveBeenCalledWith(7)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.findAll('.el-timeline-item')).toHaveLength(2)
    expect(wrapper.text()).toContain('婚姻家庭')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.find('.el-pagination').exists()).toBe(true)
  })

  test('格式化时间兼容空值与日期', () => {
    const { wrapper } = mountHistory()

    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime('2026-01-02 10:00:00')).toBe(new Date('2026-01-02 10:00:00').toLocaleString())
  })

  test('查看详情填充当前记录', () => {
    const { wrapper } = mountHistory()

    wrapper.vm.viewDetail(records[1])

    expect(wrapper.vm.currentRecord).toEqual(records[1])
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  test('确认删除后移除记录并提示', async () => {
    delConsultation.mockResolvedValue({})
    const { wrapper, message, dangerConfirm } = mountHistory()
    await flush()

    wrapper.vm.deleteRecord(records[0], 0)
    await flush()

    expect(dangerConfirm).toHaveBeenCalled()
    expect(delConsultation).toHaveBeenCalledWith(1)
    expect(wrapper.vm.historyList).toHaveLength(1)
    expect(wrapper.vm.total).toBe(1)
    expect(message.success).toHaveBeenCalledWith('删除成功')
  })

  test('取消删除时不调用接口', async () => {
    const { wrapper, dangerConfirm } = mountHistory()
    await flush()
    dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.deleteRecord(records[0], 0)
    await flush()

    expect(delConsultation).not.toHaveBeenCalled()
    expect(wrapper.vm.historyList).toHaveLength(2)
  })

  test('分页变化更新参数并重新请求', async () => {
    const { wrapper } = mountHistory()
    await flush()
    getUserConsultations.mockClear()

    wrapper.vm.handleSizeChange(20)
    expect(wrapper.vm.queryParams.pageSize).toBe(20)
    expect(getUserConsultations).toHaveBeenCalledTimes(1)

    wrapper.vm.handleCurrentChange(3)
    expect(wrapper.vm.queryParams.pageNum).toBe(3)
    expect(getUserConsultations).toHaveBeenCalledTimes(2)
  })
})
