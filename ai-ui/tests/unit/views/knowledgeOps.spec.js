'use strict'

/**
 * 知识库运营分析单测：锁定默认区间、概览可用性降级、未命中/命中列表与转草稿流程。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import KnowledgeOps from '@/views/lawyers/knowledge/KnowledgeOps.vue'
import {
  getKnowledgeOpsOverview, listUnmatchedQuestions, listTopKnowledge, draftFromQuestion
} from '@/api/lawyers/knowledge'

jest.mock('@/api/lawyers/knowledge', () => ({
  getKnowledgeOpsOverview: jest.fn(),
  listUnmatchedQuestions: jest.fn(),
  listTopKnowledge: jest.fn(),
  draftFromQuestion: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = value => (value ? '2026-11-01 10:00' : '')

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const overview = {
  total: 100,
  hitCount: 80,
  missCount: 20,
  pendingCount: 5,
  hitRate: 80,
  avgLatencyMs: 320,
  beginTime: '2026-11-01',
  endTime: '2026-11-07',
  available: true
}

function mountPage() {
  const modal = { msgSuccess: jest.fn() }
  const confirm = jest.fn(() => Promise.resolve())
  const wrapper = mount(KnowledgeOps, {
    localVue,
    propsData: { visible: false },
    stubs: { pagination: true },
    mocks: { $modal: modal, $confirm: confirm }
  })
  return { wrapper, modal, confirm }
}

describe('KnowledgeOps 知识库运营分析', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getKnowledgeOpsOverview.mockResolvedValue({ data: overview })
    listUnmatchedQuestions.mockResolvedValue({
      rows: [
        { queryText: '物业费纠纷怎么处理', queryHash: 'h1', askCount: 5, converted: 0, firstTime: '2026-11-01', lastTime: '2026-11-02' },
        { queryText: '劳动合同到期', queryHash: 'h2', askCount: 3, converted: 1 }
      ],
      total: 2
    })
    listTopKnowledge.mockResolvedValue({ data: [{ knowledgeId: 9, title: '物业纠纷指引', hitCount: 12, lastTime: '2026-11-02' }] })
    draftFromQuestion.mockResolvedValue({ msg: '草稿已生成' })
  })

  test('打开时生成默认近七天区间并加载全部数据', async () => {
    const { wrapper } = mountPage()

    wrapper.vm.handleOpen()
    await flush()

    expect(wrapper.vm.dateRange).toHaveLength(2)
    expect(wrapper.vm.dateRange[0]).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(wrapper.vm.query.pageNum).toBe(1)
    expect(getKnowledgeOpsOverview).toHaveBeenCalledWith(expect.objectContaining({
      beginTime: expect.stringMatching(/ 00:00:00$/),
      endTime: expect.stringMatching(/ 23:59:59$/)
    }))
    expect(listUnmatchedQuestions).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10
    }))
    expect(listTopKnowledge).toHaveBeenCalledWith(expect.objectContaining({ limit: 10 }))
    expect(wrapper.vm.overview).toEqual(overview)
    expect(wrapper.vm.unmatchedList).toHaveLength(2)
    expect(wrapper.vm.unmatchedTotal).toBe(2)
    expect(wrapper.vm.topList).toHaveLength(1)
    expect(wrapper.vm.overviewLoading).toBe(false)
  })

  test('已存在区间时不重置默认区间', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.dateRange = ['2026-10-01', '2026-10-31']

    wrapper.vm.handleOpen()
    await flush()

    expect(wrapper.vm.dateRange).toEqual(['2026-10-01', '2026-10-31'])
  })

  test('查询日志表不可用时清空列表并跳过明细查询', async () => {
    getKnowledgeOpsOverview.mockResolvedValue({ data: { available: false } })
    const { wrapper } = mountPage()
    wrapper.vm.dateRange = ['2026-11-01', '2026-11-07']

    wrapper.vm.fetchAll()
    await flush()

    expect(wrapper.vm.unmatchedList).toEqual([])
    expect(wrapper.vm.unmatchedTotal).toBe(0)
    expect(wrapper.vm.topList).toEqual([])
    expect(listUnmatchedQuestions).not.toHaveBeenCalled()
    expect(listTopKnowledge).not.toHaveBeenCalled()
    expect(wrapper.vm.overview.available).toBe(false)
  })

  test('概览请求失败时降级为不可用', async () => {
    getKnowledgeOpsOverview.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()

    const available = await wrapper.vm.fetchOverview()

    expect(available).toBe(false)
    expect(wrapper.vm.overview).toEqual({ available: false })
    expect(wrapper.vm.overviewLoading).toBe(false)
  })

  test('无区间时参数起止时间为 undefined', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.buildParams()).toEqual({ beginTime: undefined, endTime: undefined })
  })

  test('概览卡按默认值与百分比生成', () => {
    const { wrapper } = mountPage()

    const empty = wrapper.vm.cards
    expect(empty).toHaveLength(6)
    expect(empty[0]).toEqual({ label: '总问句', value: 0, tone: 'primary' })
    expect(empty[4].value).toBe('0%')

    wrapper.vm.overview = overview
    expect(wrapper.vm.cards[4].value).toBe('80%')
    expect(wrapper.vm.cards[5].value).toBe('320 ms')
  })

  test('转草稿长问句截断并携带问句与哈希', async () => {
    const { wrapper, modal, confirm } = mountPage()
    const longText = '问'.repeat(130)

    wrapper.vm.handleDraft({ queryText: longText, queryHash: 'h1' })
    await flush()

    expect(confirm).toHaveBeenCalledWith(
      expect.stringContaining(longText.substring(0, 120) + '…'),
      '转知识草稿',
      expect.objectContaining({ confirmButtonText: '生成草稿' })
    )
    expect(draftFromQuestion).toHaveBeenCalledWith({ question: longText, queryHash: 'h1' })
    expect(modal.msgSuccess).toHaveBeenCalledWith('草稿已生成')
    expect(getKnowledgeOpsOverview).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.draftingHash).toBeNull()
  })

  test('转草稿取消或缺少问句时不调用接口', async () => {
    const { wrapper, confirm } = mountPage()

    wrapper.vm.handleDraft(null)
    wrapper.vm.handleDraft({ queryHash: 'h1' })
    expect(confirm).not.toHaveBeenCalled()

    confirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDraft({ queryText: '短问句', queryHash: 'h2' })
    await flush()

    expect(draftFromQuestion).not.toHaveBeenCalled()
    expect(wrapper.vm.draftingHash).toBeNull()
  })

  test('关闭弹窗向父级同步 visible', () => {
    const { wrapper } = mountPage()

    wrapper.vm.handleClose()

    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })

  test('日期格式化补零', () => {
    const { wrapper } = mountPage()
    const date = new Date(2026, 0, 5)

    expect(wrapper.vm.formatDate(date)).toBe('2026-01-05')
  })
})
