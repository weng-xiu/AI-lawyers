'use strict'

/**
 * AI 律师实时辅助面板单测：锁定会话加载与状态映射、分析与小结触发、
 * Copilot 要素/法条/工单渲染、建议埋点、法条溯源、代执行建单与进度查询、话术复制。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import AiAssistPanel from '@/views/lawyers/callCenter/AiAssistPanel.vue'
import { getAiAssistByRecord, analyzeAiAssist, summarizeAiAssist } from '@/api/lawyers/aiAssist'
import { copilotFeedback, getCopilotChunk, createCopilotTicket, queryCopilotTicket } from '@/api/lawyers/copilot'

jest.mock('@/api/lawyers/aiAssist', () => ({
  getAiAssistByRecord: jest.fn(),
  analyzeAiAssist: jest.fn(),
  summarizeAiAssist: jest.fn()
}))

jest.mock('@/api/lawyers/copilot', () => ({
  copilotFeedback: jest.fn(),
  getCopilotChunk: jest.fn(),
  createCopilotTicket: jest.fn(),
  queryCopilotTicket: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const sessionData = {
  sessionId: 7,
  sessionStatus: '1',
  intentCategory: '合同纠纷',
  recommendLaws: JSON.stringify([{ title: '中华人民共和国民法典第五百六十三条' }]),
  recommendScripts: JSON.stringify(['先确认合同签订时间与履行情况']),
  callSummary: '来电人咨询合同解除条件'
}

const copilotData = {
  seq: 3,
  element: {
    disputeType: '合同纠纷',
    urgency: 'urgent',
    claims: ['要求解除合同'],
    keyFacts: ['合同签订于2025年'],
    degraded: false
  },
  laws: [{ chunkId: 11, title: '民法典', lawArticle: '第五百六十三条' }],
  tickets: [{ ticketId: 21, ticketNo: 'T021', title: '合同解除咨询', status: '2', contentSnippet: '已完成调解' }],
  actions: [{ action: 'createTicket', label: '生成工单', payload: { title: '合同解除工单', content: '草稿', priority: '1' } }]
}

function mountPanel(options = {}) {
  const message = {
    success: jest.fn(),
    warning: jest.fn(),
    info: jest.fn()
  }
  const wrapper = mount(AiAssistPanel, {
    localVue,
    propsData: {
      recordId: 100,
      liveText: '实时字幕内容',
      copilot: null,
      ...options.propsData
    },
    mocks: { $message: message }
  })
  return { wrapper, message }
}

describe('AiAssistPanel AI 律师实时辅助面板', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getAiAssistByRecord.mockResolvedValue({ data: { ...sessionData } })
    analyzeAiAssist.mockResolvedValue({ data: { ...sessionData, sessionStatus: '2' } })
    summarizeAiAssist.mockResolvedValue({ data: { ...sessionData, sessionStatus: '3' } })
    copilotFeedback.mockResolvedValue({})
    getCopilotChunk.mockResolvedValue({ data: { title: '民法典', lawArticle: '第五百六十三条', source: '全国人大', chunkContent: '原文内容' } })
    createCopilotTicket.mockResolvedValue({ data: { ticketNo: 'GD20261121001' } })
    queryCopilotTicket.mockResolvedValue({ data: { ticketNo: 'T021', title: '合同解除咨询', status: '2', speech: '建议话术' } })
  })

  test('未关联人工通话时保持待命且不拉取会话', async () => {
    const { wrapper } = mountPanel({ propsData: { recordId: null } })
    await flush()

    expect(getAiAssistByRecord).not.toHaveBeenCalled()
    expect(wrapper.vm.session).toBeNull()
    expect(wrapper.vm.statusText).toBe('待命')
    expect(wrapper.vm.statusType).toBe('info')
    expect(wrapper.text()).toContain('AI 辅助待命')
  })

  test('关联通话后按记录ID加载会话并渲染意图/法条/话术/小结', async () => {
    const { wrapper } = mountPanel()
    await flush()

    expect(getAiAssistByRecord).toHaveBeenCalledWith(100)
    expect(wrapper.vm.session.sessionId).toBe(7)
    expect(wrapper.vm.statusText).toBe('分析中')
    expect(wrapper.vm.statusType).toBe('warning')
    expect(wrapper.vm.recommendLaws).toHaveLength(1)
    expect(wrapper.vm.recommendScripts).toEqual(['先确认合同签订时间与履行情况'])
    expect(wrapper.text()).toContain('合同纠纷')
    expect(wrapper.text()).toContain('来电人咨询合同解除条件')
  })

  test('会话加载失败清空会话，非法推荐 JSON 兜底为空数组', async () => {
    getAiAssistByRecord.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPanel()
    await flush()

    expect(wrapper.vm.session).toBeNull()
    expect(wrapper.text()).toContain('初始化中')

    wrapper.vm.session = { sessionStatus: '9', recommendLaws: 'bad-json', recommendScripts: '{bad' }
    expect(wrapper.vm.recommendLaws).toEqual([])
    expect(wrapper.vm.recommendScripts).toEqual([])
    expect(wrapper.vm.statusText).toBe('异常')
    expect(wrapper.vm.statusType).toBe('danger')

    wrapper.vm.session.sessionStatus = 'unknown'
    expect(wrapper.vm.statusText).toBe('未知')
    expect(wrapper.vm.statusType).toBe('info')
  })

  test('智能分析上送实时字幕并更新会话状态', async () => {
    const { wrapper, message } = mountPanel()
    await flush()

    wrapper.vm.handleAnalyze()
    await flush()

    expect(analyzeAiAssist).toHaveBeenCalledWith({ sessionId: 7, callSummary: '实时字幕内容' })
    expect(wrapper.vm.session.sessionStatus).toBe('2')
    expect(wrapper.vm.analyzing).toBe(false)
    expect(message.success).toHaveBeenCalledWith('AI 分析完成')
  })

  test('无会话时分析/小结直接返回，不调用接口', async () => {
    const { wrapper } = mountPanel({ propsData: { recordId: null } })
    await flush()

    wrapper.vm.handleAnalyze()
    wrapper.vm.handleSummarize()

    expect(analyzeAiAssist).not.toHaveBeenCalled()
    expect(summarizeAiAssist).not.toHaveBeenCalled()
  })

  test('生成小结上送实时字幕并在结束后复位加载态', async () => {
    const { wrapper, message } = mountPanel()
    await flush()

    wrapper.vm.handleSummarize()
    await flush()

    expect(summarizeAiAssist).toHaveBeenCalledWith({ sessionId: 7, callSummary: '实时字幕内容' })
    expect(wrapper.vm.session.sessionStatus).toBe('3')
    expect(wrapper.vm.summarizing).toBe(false)
    expect(message.success).toHaveBeenCalledWith('AI 小结已生成')
  })

  test('Copilot 要素/法条/相似工单/建议操作渲染，点击法条溯源', async () => {
    const { wrapper } = mountPanel({ propsData: { copilot: copilotData } })
    await flush()

    expect(wrapper.text()).toContain('实时案情要素')
    expect(wrapper.text()).toContain('合同纠纷')
    expect(wrapper.text()).toContain('紧急')
    expect(wrapper.text()).toContain('推荐法条')
    expect(wrapper.text()).toContain('相似工单（已办结）')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('生成工单')

    await wrapper.find('.cp-law-link').trigger('click')
    await flush()
    expect(getCopilotChunk).toHaveBeenCalledWith(11)
    expect(wrapper.vm.lawDetail.chunkContent).toBe('原文内容')
    expect(wrapper.vm.lawDialogVisible).toBe(true)
    expect(wrapper.vm.lawLoading).toBe(false)
  })

  test('建议埋点携带记录ID，法条无溯源内容给出提示', () => {
    const { wrapper, message } = mountPanel()
    wrapper.vm.sendFeedback('LAW', '11', 'ADOPT')

    expect(copilotFeedback).toHaveBeenCalledWith({
      recordId: 100,
      suggestionType: 'LAW',
      suggestionRef: '11',
      action: 'ADOPT'
    })

    wrapper.vm.openLaw(null)
    expect(message.warning).toHaveBeenCalledWith('该法条暂无溯源内容')
    expect(getCopilotChunk).not.toHaveBeenCalled()
  })

  test('采用并关闭记录 LAW 采纳后关闭弹窗', () => {
    const { wrapper } = mountPanel()
    wrapper.vm.openedChunkId = 11
    wrapper.vm.lawDialogVisible = true

    wrapper.vm.adoptOpenedLaw()

    expect(copilotFeedback).toHaveBeenCalledWith(expect.objectContaining({
      suggestionType: 'LAW',
      suggestionRef: '11',
      action: 'ADOPT'
    }))
    expect(wrapper.vm.lawDialogVisible).toBe(false)
  })

  test('建议操作：建单预填草稿并提交，缺少工单ID给出提示', async () => {
    const { wrapper, message } = mountPanel()
    await flush()

    wrapper.vm.handleSuggestedAction(copilotData.actions[0])
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.ticketDialogVisible).toBe(true)
    expect(wrapper.vm.ticketForm).toEqual(expect.objectContaining({
      title: '合同解除工单',
      content: '草稿',
      priority: '1',
      recordId: 100
    }))

    wrapper.vm.$refs.ticketForm.validate = cb => cb(true)
    wrapper.vm.submitCreateTicket()
    await flush()

    expect(createCopilotTicket).toHaveBeenCalledWith({
      title: '合同解除工单',
      content: '草稿',
      priority: '1',
      recordId: 100
    })
    expect(message.success).toHaveBeenCalledWith('工单已创建：GD20261121001')
    expect(wrapper.vm.ticketDialogVisible).toBe(false)
    expect(wrapper.vm.ticketSubmitting).toBe(false)

    wrapper.vm.handleSuggestedAction({ action: 'queryTicket', payload: {} })
    expect(message.warning).toHaveBeenCalledWith('缺少工单ID')
    expect(queryCopilotTicket).not.toHaveBeenCalled()
  })

  test('校验不通过时不提交建单', async () => {
    const { wrapper } = mountPanel()
    await flush()

    wrapper.vm.handleSuggestedAction(copilotData.actions[0])
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.ticketForm.validate = cb => cb(false)
    wrapper.vm.submitCreateTicket()
    await flush()

    expect(createCopilotTicket).not.toHaveBeenCalled()
    expect(wrapper.vm.ticketSubmitting).toBe(false)
  })

  test('查询工单进度并复制播报话术', async () => {
    const { wrapper, message } = mountPanel()
    await flush()

    wrapper.vm.handleSuggestedAction({ action: 'queryTicket', payload: { ticketId: 21 } })
    await flush()

    expect(queryCopilotTicket).toHaveBeenCalledWith(21)
    expect(wrapper.vm.progressDialogVisible).toBe(true)
    expect(wrapper.vm.progressData.ticketNo).toBe('T021')
    expect(wrapper.vm.progressLoading).toBe(false)

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: jest.fn(() => Promise.resolve()) }
    })
    wrapper.vm.copySpeech()
    await flush()

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('建议话术')
    expect(message.success).toHaveBeenCalledWith('话术已复制')
  })

  test('无播报话术不复制；无剪贴板能力时走兜底复制', () => {
    const { wrapper, message } = mountPanel()

    wrapper.vm.copySpeech()
    expect(message.success).not.toHaveBeenCalled()

    wrapper.vm.progressData = { speech: '兜底话术' }
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    const fallback = jest.spyOn(wrapper.vm, 'fallbackCopy')
    wrapper.vm.copySpeech()

    expect(fallback).toHaveBeenCalledWith('兜底话术', expect.any(Function))
  })

  test('工单状态文案/语义色委托统一映射', () => {
    const { wrapper } = mountPanel()

    expect(wrapper.vm.ticketStatusLabel('0')).toBe('待处理')
    expect(wrapper.vm.ticketStatusTag('2')).toBe('success')
    expect(wrapper.vm.ticketStatusClass('1')).toBe('tag-status--processing')
    expect(wrapper.vm.ticketStatusLabel('9')).toBe('未知')
  })
})
