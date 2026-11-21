'use strict'

/**
 * 来电弹屏单测：锁定主叫画像加载与标签解析、标签页懒加载、WS/SIP 通话状态同步、
 * 字幕与 Copilot 桥接、快捷建单/知识速查/外派转办、通话动作与快捷键处理。
 */
import Vue from 'vue'
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CallPopup from '@/views/lawyers/callCenter/callPopup.vue'
import { getCallerProfile, getCallerHistory, getCallerTickets, getCallerTrack, updateCallerProfile } from '@/api/lawyers/callPopup'
import {
  autoFillLedger, addLedger, transferCall, holdCall, resumeCall, hangupCall,
  afterWork, listAgent, addTicket, transferOutTicket
} from '@/api/lawyers/callCenter'
import { searchKnowledge } from '@/api/lawyers/knowledge'
import { listExternalOrg } from '@/api/lawyers/externalOrg'
import callSocket from '@/utils/callSocket'
import { getSipPhone } from '@/utils/webrtcSipPhone'
import { createPoller } from '@/utils/polling'
import { loadShortcuts, saveShortcuts, resetShortcuts, formatKeys, matchShortcut } from '@/utils/shortcuts'

jest.mock('@/api/lawyers/callPopup', () => ({
  getCallerProfile: jest.fn(),
  getCallerHistory: jest.fn(),
  getCallerTickets: jest.fn(),
  getCallerTrack: jest.fn(),
  updateCallerProfile: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({
  autoFillLedger: jest.fn(),
  addLedger: jest.fn(),
  transferCall: jest.fn(),
  holdCall: jest.fn(),
  resumeCall: jest.fn(),
  hangupCall: jest.fn(),
  afterWork: jest.fn(),
  listAgent: jest.fn(),
  addTicket: jest.fn(),
  transferOutTicket: jest.fn()
}))

jest.mock('@/api/lawyers/knowledge', () => ({ searchKnowledge: jest.fn() }))
jest.mock('@/api/lawyers/externalOrg', () => ({ listExternalOrg: jest.fn() }))

jest.mock('@/utils/callSocket', () => ({
  __esModule: true,
  default: { on: jest.fn(), off: jest.fn() }
}))

jest.mock('@/utils/webrtcSipPhone', () => ({ getSipPhone: jest.fn() }))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

jest.mock('@/utils/shortcuts', () => ({
  loadShortcuts: jest.fn(),
  saveShortcuts: jest.fn(),
  resetShortcuts: jest.fn(),
  formatKeys: jest.fn(),
  matchShortcut: jest.fn()
}))

jest.mock('@/utils/request', () => jest.fn())

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const defaultShortcuts = [
  { id: 'hold', label: '保持/恢复', keys: 'Ctrl+1', method: 'handleHold', perm: 'lawyers:call:hold' },
  { id: 'hangup', label: '挂断', keys: 'Ctrl+2', method: 'handleHangup', perm: 'lawyers:call:hangup' }
]

const wrappers = []

function mountPage(query = {}) {
  const state = Vue.observable({
    agent: { agentId: 7, sipCall: null },
    user: { id: 2 }
  })
  const dispatch = jest.fn(() => Promise.resolve({ callStatus: '1' }))
  const modal = {
    msgWarning: jest.fn(),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    dangerConfirm: jest.fn(() => Promise.resolve())
  }
  const message = { success: jest.fn(), warning: jest.fn(), error: jest.fn(), info: jest.fn() }
  const auth = { hasPermi: jest.fn(() => true) }
  const wrapper = mount(CallPopup, {
    localVue,
    stubs: { AiAssistPanel: true, VoiceCaption: true },
    mocks: {
      $store: { state, dispatch },
      $route: { query: { callerNumber: '13800000001', recordId: '5', ...query }, params: {} },
      $modal: modal,
      $message: message,
      $auth: auth
    }
  })
  wrappers.push(wrapper)
  return { wrapper, state, dispatch, modal, message, auth }
}

describe('CallPopup 来电弹屏', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getCallerProfile.mockResolvedValue({
      data: {
        callerName: '张三',
        callerNumber: '13800000001',
        tags: 'VIP,老客户',
        highFreqProblem: '合同解除',
        profile: { callerName: '张三', callerGender: '1', callerAge: 35, callerAddress: '北京市', profileId: 9 }
      }
    })
    getCallerHistory.mockResolvedValue({ data: [{ recordId: 1 }] })
    getCallerTickets.mockResolvedValue({ data: [{ ticketId: 2 }] })
    getCallerTrack.mockResolvedValue({ data: [{ trackId: 3 }] })
    updateCallerProfile.mockResolvedValue({})
    autoFillLedger.mockResolvedValue({ data: { callerName: '张三' } })
    addLedger.mockResolvedValue({})
    transferCall.mockResolvedValue({})
    holdCall.mockResolvedValue({})
    resumeCall.mockResolvedValue({})
    hangupCall.mockResolvedValue({})
    afterWork.mockResolvedValue({})
    listAgent.mockResolvedValue({ rows: [{ agentId: 7, agentName: '自己' }, { agentId: 8, agentName: '赵坐席' }] })
    addTicket.mockResolvedValue({})
    transferOutTicket.mockResolvedValue({})
    searchKnowledge.mockResolvedValue({ data: [{ id: 1, title: '合同', content: '内容' }] })
    listExternalOrg.mockResolvedValue({ rows: [{ orgId: 1, orgName: '协同机构' }] })
    getSipPhone.mockReturnValue({ on: jest.fn(), off: jest.fn() })
    createPoller.mockReturnValue({ start: jest.fn(), stop: jest.fn() })
    loadShortcuts.mockReturnValue(defaultShortcuts.map(s => ({ ...s })))
    saveShortcuts.mockImplementation(draft => draft)
    resetShortcuts.mockReturnValue(defaultShortcuts.map(s => ({ ...s })))
    formatKeys.mockReturnValue('Ctrl+3')
    matchShortcut.mockReturnValue(false)
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.destroy())
  })

  test('呼出场景直接进入通话并加载画像，标签与头像正确解析', async () => {
    const { wrapper, state } = mountPage({ direction: 'out', callStatus: '1' })
    await flush()

    expect(wrapper.vm.direction).toBe('out')
    expect(wrapper.vm.connected).toBe(true)
    expect(wrapper.vm.callStatus).toBe('1')
    expect(wrapper.vm.callStatusText).toBe('通话中')
    expect(getCallerProfile).toHaveBeenCalledWith('13800000001')
    expect(wrapper.vm.profileData.callerGender).toBe('1')
    expect(wrapper.vm.profileData.address).toBe('北京市')
    expect(wrapper.vm.profileData.profileId).toBe(9)
    expect(wrapper.vm.avatarText).toBe('张')
    expect(wrapper.vm.tagsArray).toEqual(['VIP', '老客户'])
    expect(createPoller).toHaveBeenCalled()
    expect(callSocket.on).toHaveBeenCalledWith('ANSWERED', wrapper.vm.onWsAnswered)
    expect(getSipPhone().on).toHaveBeenCalledWith('sessionEnded', wrapper.vm.onSipHangup)
    expect(state.user.id).toBe(2)

    wrapper.vm.clearTimer()
  })

  test('呼入场景等待接听，画像接口失败不影响页面', async () => {
    getCallerProfile.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage({ direction: 'in' })
    await flush()

    expect(wrapper.vm.direction).toBe('in')
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.callEnded).toBe(false)
    expect(wrapper.vm.callStatusText).toBe('空闲')
    expect(wrapper.vm.profileData).toEqual({})
    expect(wrapper.vm.avatarText).toBe('?')
    expect(wrapper.vm.tagsArray).toEqual([])
  })

  test('切换标签页按需加载通话/工单/轨迹，缺少号码时不请求', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.activeTab = 'call'
    await flush()
    expect(getCallerHistory).toHaveBeenCalledWith('13800000001', 20)
    expect(wrapper.vm.historyList).toHaveLength(1)
    expect(wrapper.vm.tabLoading).toBe(false)

    wrapper.vm.activeTab = 'ticket'
    await flush()
    expect(getCallerTickets).toHaveBeenCalledWith('13800000001')
    expect(wrapper.vm.ticketList).toHaveLength(1)

    wrapper.vm.activeTab = 'track'
    await flush()
    expect(getCallerTrack).toHaveBeenCalledWith('13800000001')
    expect(wrapper.vm.trackList).toHaveLength(1)

    wrapper.vm.profileData = {}
    wrapper.vm.activeTab = 'base'
    wrapper.vm.historyList = []
    wrapper.vm.activeTab = 'call'
    expect(getCallerHistory).toHaveBeenCalledTimes(1)
    wrapper.vm.clearTimer()
  })

  test('WS 事件同步通话状态，过滤非本坐席事件', async () => {
    const { wrapper } = mountPage({ direction: 'in' })
    await flush()

    wrapper.vm.onWsAnswered({ agentId: 99, recordId: 1 })
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.currentRecordId).toBeNull()

    wrapper.vm.onWsAnswered({ agentId: 7, recordId: 12 })
    expect(wrapper.vm.connected).toBe(true)
    expect(wrapper.vm.callStatus).toBe('1')
    expect(wrapper.vm.currentRecordId).toBe(12)

    wrapper.vm.liveCaptionText = '字幕'
    wrapper.vm.copilotData = { seq: 2, element: {}, laws: [{}], tickets: [] }
    wrapper.vm.onWsHangup({ agentId: 7 })
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.callEnded).toBe(true)
    expect(wrapper.vm.callStatus).toBe('0')
    expect(wrapper.vm.liveCaptionText).toBe('')
    expect(wrapper.vm.copilotData.seq).toBe(0)

    wrapper.vm.onWsCallStart({ agentId: 7, recordId: 13 })
    expect(wrapper.vm.currentRecordId).toBe(13)
    expect(wrapper.vm.connected).toBe(true)

    expect(wrapper.vm.isMyEvent(null)).toBe(true)
    expect(wrapper.vm.isMyEvent({})).toBe(true)
    expect(wrapper.vm.isMyEvent({ agentId: '7' })).toBe(true)
    wrapper.vm.onWsDtmf({ agentId: 99 })

    wrapper.vm.clearTimer()
  })

  test('SIP 状态监听同步接听/挂断，字幕与 Copilot 帧按序合并', async () => {
    const { wrapper, state } = mountPage({ direction: 'in' })
    await flush()

    state.agent.sipCall = { state: 'answered' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.connected).toBe(true)
    expect(wrapper.vm.callStatus).toBe('1')

    state.agent.sipCall = null
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.callEnded).toBe(true)

    wrapper.vm.onCaptionFinal('第一段')
    wrapper.vm.onCaptionFinal('第二段')
    expect(wrapper.vm.liveCaptionText).toBe('\n第一段\n第二段')

    wrapper.vm.onCaptionFinal('x'.repeat(2100))
    expect(wrapper.vm.liveCaptionText.length).toBe(2000)

    wrapper.vm.onCopilotElement({ seq: 2, disputeType: '合同' })
    expect(wrapper.vm.copilotData.element.disputeType).toBe('合同')
    wrapper.vm.onCopilotLaws({ seq: 2, laws: [{ chunkId: 1 }] })
    expect(wrapper.vm.copilotData.laws).toHaveLength(1)
    wrapper.vm.onCopilotTickets({ seq: 2, tickets: [{ ticketId: 3 }] })
    expect(wrapper.vm.copilotData.tickets).toHaveLength(1)
    wrapper.vm.onCopilotActions({ seq: 2, actions: [{ action: 'createTicket' }] })
    expect(wrapper.vm.copilotData.actions).toHaveLength(1)

    wrapper.vm.onCopilotLaws({ seq: 1, laws: [] })
    expect(wrapper.vm.copilotData.laws).toHaveLength(1)
    wrapper.vm.onCopilotActions({ seq: 3, actions: [{ action: 'queryTicket' }] })
    expect(wrapper.vm.copilotData.seq).toBe(3)
    expect(wrapper.vm.copilotData.element).toBeNull()

    wrapper.vm.clearTimer()
  })

  test('手动接听按振铃态校验，并同步坐席状态刷新', async () => {
    const { wrapper, state, dispatch, message } = mountPage({ direction: 'in' })
    await flush()

    wrapper.vm.handleAnswer()
    expect(message.warning).toHaveBeenCalledWith('当前没有振铃中的来电')

    state.agent.sipCall = { direction: 'incoming', state: 'ringing' }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.incomingRinging).toBe(true)

    dispatch.mockResolvedValueOnce(true)
    wrapper.vm.handleAnswer()
    expect(dispatch).toHaveBeenCalledWith('agent/sipAnswer')
    expect(wrapper.vm.connected).toBe(true)
    expect(message.success).toHaveBeenCalledWith('正在接听来电')

    dispatch.mockReturnValueOnce(false)
    wrapper.vm.handleAnswer()
    expect(message.error).toHaveBeenCalledWith('接听失败，请检查麦克风权限或分机注册状态')
    wrapper.vm.clearTimer()
  })

  test('同步坐席状态：无坐席跳过，通话中自动计时，空闲自动结束', async () => {
    const { wrapper, dispatch } = mountPage({ direction: 'in' })
    await flush()

    const originalAgentId = wrapper.vm.$store.state.agent.agentId
    wrapper.vm.$store.state.agent.agentId = null
    expect(wrapper.vm.syncFromAgent()).toBeUndefined()
    wrapper.vm.$store.state.agent.agentId = originalAgentId

    dispatch.mockResolvedValueOnce({ callStatus: '1' })
    await wrapper.vm.syncFromAgent()
    expect(wrapper.vm.connected).toBe(true)
    expect(wrapper.vm.callStatus).toBe('1')

    dispatch.mockResolvedValueOnce({ callStatus: '0' })
    await wrapper.vm.syncFromAgent()
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.callEnded).toBe(true)

    dispatch.mockResolvedValueOnce(null)
    await wrapper.vm.syncFromAgent()
    expect(dispatch).toHaveBeenCalledWith('agent/refresh')
    wrapper.vm.clearTimer()
  })

  test('一键登记台账：无关联记录提示，有关联时自动填充后新增', async () => {
    const { wrapper, message } = mountPage({ recordId: '' })
    await flush()
    wrapper.vm.currentRecordId = null

    wrapper.vm.handleLedger()
    expect(message.warning).toHaveBeenCalledWith('无关联来电记录，请手动登记')
    expect(autoFillLedger).not.toHaveBeenCalled()

    wrapper.vm.currentRecordId = 21
    wrapper.vm.handleLedger()
    await flush()
    expect(autoFillLedger).toHaveBeenCalledWith(21)
    expect(addLedger).toHaveBeenCalledWith({ callerName: '张三' })
    expect(message.success).toHaveBeenCalledWith('已一键登记台账')
    wrapper.vm.clearTimer()
  })

  test('快速建单：预填标题并提交后切到工单标签', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.openQuickTicket()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.quickTicketOpen).toBe(true)
    expect(wrapper.vm.quickTicketForm.title).toBe('张三 来电咨询')
    expect(wrapper.vm.quickTicketForm.priority).toBe('1')

    wrapper.vm.quickTicketForm.content = '诉求详情'
    wrapper.vm.$refs.quickTicketForm.validate = cb => cb(true)
    wrapper.vm.submitQuickTicket()
    await flush()

    expect(addTicket).toHaveBeenCalledWith(expect.objectContaining({
      title: '张三 来电咨询',
      content: '诉求详情',
      callerNumber: '13800000001',
      callerName: '张三',
      recordId: '5',
      assignUserId: 2,
      direction: 'in'
    }))
    expect(modal.msgSuccess).toHaveBeenCalledWith('工单已创建')
    expect(wrapper.vm.quickTicketOpen).toBe(false)
    expect(wrapper.vm.activeTab).toBe('ticket')
    expect(getCallerTickets).toHaveBeenCalled()
    expect(wrapper.vm.quickTicketSubmitting).toBe(false)
    wrapper.vm.clearTimer()
  })

  test('知识速查：按高频问题自动检索，空关键词给出提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.openKnowledge()
    await flush()

    expect(wrapper.vm.knowledgeOpen).toBe(true)
    expect(wrapper.vm.knowledgeKeyword).toBe('合同解除')
    expect(searchKnowledge).toHaveBeenCalledWith('合同解除')
    expect(wrapper.vm.knowledgeList).toHaveLength(1)
    expect(wrapper.vm.knowledgeLoading).toBe(false)

    wrapper.vm.knowledgeKeyword = '  '
    wrapper.vm.searchKnowledgeBase()
    expect(modal.msgWarning).toHaveBeenCalledWith('请输入检索关键词')

    searchKnowledge.mockRejectedValue(new Error('fail'))
    wrapper.vm.knowledgeKeyword = '劳动'
    wrapper.vm.searchKnowledgeBase()
    await flush()
    expect(wrapper.vm.knowledgeList).toEqual([])
    expect(wrapper.vm.knowledgeLoading).toBe(false)
    wrapper.vm.clearTimer()
  })

  test('复制知识内容：剪贴板成功与无内容/降级异常提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: jest.fn(() => Promise.resolve()) }
    })
    wrapper.vm.copyKnowledge({ title: '标题', content: '正文' })
    await flush()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('标题\n正文')
    expect(modal.msgSuccess).toHaveBeenCalledWith('已复制，可粘贴到话后整理')

    wrapper.vm.copyKnowledge({})
    expect(modal.msgWarning).toHaveBeenCalledWith('该知识没有可复制内容')

    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    document.execCommand = jest.fn(() => { throw new Error('unsupported') })
    wrapper.vm.copyKnowledge({ title: '标题' })
    expect(modal.msgError).toHaveBeenCalledWith('复制失败，请手动选择文本')
    wrapper.vm.clearTimer()
  })

  test('外派转办：无工单提示建单，校验必填后提交并刷新', async () => {
    const { wrapper, modal, dispatch } = mountPage()
    await flush()

    wrapper.vm.ticketList = []
    wrapper.vm.openTransferOut()
    await flush()
    expect(modal.msgWarning).toHaveBeenCalledWith('该来电暂无工单，请先使用「快速建单」创建')
    expect(listExternalOrg).toHaveBeenCalledWith({ pageNum: 1, pageSize: 200, status: '0' })
    expect(wrapper.vm.orgOptions).toHaveLength(1)

    wrapper.vm.transferOutForm = { ticketId: null, orgId: null, remark: '' }
    wrapper.vm.submitTransferOut()
    expect(transferOutTicket).not.toHaveBeenCalled()

    wrapper.vm.transferOutForm = { ticketId: 2, orgId: 1, remark: '转办' }
    wrapper.vm.transferOutOpen = true
    wrapper.vm.submitTransferOut()
    await flush()

    expect(transferOutTicket).toHaveBeenCalledWith({ ticketId: 2, orgId: 1, remark: '转办' })
    expect(modal.msgSuccess).toHaveBeenCalledWith('转办已发起')
    expect(wrapper.vm.transferOutOpen).toBe(false)
    expect(dispatch).toHaveBeenCalledWith('agent/refresh')
    expect(wrapper.vm.transferOutSubmitting).toBe(false)
    wrapper.vm.clearTimer()
  })

  test('转接与通话动作：坐席校验、保持/恢复、话后整理与挂断', async () => {
    const { wrapper, state, dispatch, modal, message } = mountPage()
    await flush()

    const originAgentId = state.agent.agentId
    state.agent.agentId = null
    wrapper.vm.handleTransfer()
    wrapper.vm.handleHold()
    wrapper.vm.handleAfterWork()
    wrapper.vm.handleHangup()
    expect(message.warning).toHaveBeenCalledWith('请先在顶部签入坐席')
    state.agent.agentId = originAgentId

    wrapper.vm.handleTransfer()
    await flush()
    expect(wrapper.vm.onlineAgentOptions.map(a => a.agentId)).toEqual([8])
    expect(wrapper.vm.transferOpen).toBe(true)

    wrapper.vm.confirmTransfer()
    expect(transferCall).not.toHaveBeenCalled()
    wrapper.vm.transferTargetId = 8
    wrapper.vm.confirmTransfer()
    await flush()
    expect(transferCall).toHaveBeenCalledWith({ agentId: 7, toAgentId: 8, remark: '' })
    expect(message.success).toHaveBeenCalledWith('转接请求已提交')
    expect(wrapper.vm.transferOpen).toBe(false)

    wrapper.vm.callStatus = '1'
    wrapper.vm.handleHold()
    await flush()
    expect(holdCall).toHaveBeenCalledWith({ agentId: 7 })
    expect(message.success).toHaveBeenCalledWith('通话已保持')

    wrapper.vm.callStatus = '2'
    wrapper.vm.handleHold()
    await flush()
    expect(resumeCall).toHaveBeenCalledWith({ agentId: 7 })
    expect(message.success).toHaveBeenCalledWith('通话已恢复')

    wrapper.vm.handleAfterWork()
    await flush()
    expect(afterWork).toHaveBeenCalledWith({ agentId: 7 })
    expect(message.success).toHaveBeenCalledWith('已进入话后整理')

    wrapper.vm.connected = true
    wrapper.vm.handleHangup()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ content: '确定挂断当前通话吗？' }))
    expect(hangupCall).toHaveBeenCalledWith({ agentId: 7 })
    expect(wrapper.vm.connected).toBe(false)
    expect(message.success).toHaveBeenCalledWith('通话已挂断')
    wrapper.vm.clearTimer()
  })

  test('画像编辑与保存：字段映射、护理模式转数字并回拉画像', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    wrapper.vm.profileData.careMode = '1'
    wrapper.vm.profileData.languagePreference = ''

    wrapper.vm.handleEditProfile()
    expect(wrapper.vm.editOpen).toBe(true)
    expect(wrapper.vm.profileForm.careMode).toBe(1)
    expect(wrapper.vm.profileForm.languagePreference).toBe('zh-CN')
    expect(wrapper.vm.profileForm.callerAddress).toBe('北京市')

    getCallerProfile.mockClear()
    wrapper.vm.submitProfile()
    await flush()

    expect(updateCallerProfile).toHaveBeenCalledWith(expect.objectContaining({ careMode: 1 }))
    expect(message.success).toHaveBeenCalledWith('保存成功')
    expect(wrapper.vm.editOpen).toBe(false)
    expect(getCallerProfile).toHaveBeenCalledWith('13800000001')
    wrapper.vm.clearTimer()
  })

  test('SIP 挂断：未接通且未振铃时忽略，接通后复位并刷新坐席', async () => {
    const { wrapper, dispatch, message } = mountPage({ direction: 'in' })
    await flush()

    wrapper.vm.onSipHangup()
    expect(message.info).not.toHaveBeenCalled()

    wrapper.vm.connected = true
    wrapper.vm.onSipHangup()
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.callEnded).toBe(true)
    expect(message.info).toHaveBeenCalledWith('对方已挂断')
    expect(dispatch).toHaveBeenCalledWith('agent/refresh')
    wrapper.vm.clearTimer()
  })

  test('快捷键：输入框/弹窗豁免、权限校验与动作分发', async () => {
    const { wrapper, modal, auth } = mountPage()
    await flush()
    const preventDefault = jest.fn()
    const hold = jest.spyOn(wrapper.vm, 'handleHold').mockImplementation(() => {})
    matchShortcut.mockReturnValue(true)

    wrapper.vm.handleKeydown({ target: { tagName: 'INPUT' }, preventDefault })
    expect(hold).not.toHaveBeenCalled()
    expect(preventDefault).not.toHaveBeenCalled()

    wrapper.vm.transferOpen = true
    wrapper.vm.handleKeydown({ target: { tagName: 'DIV' }, preventDefault })
    wrapper.vm.transferOpen = false
    expect(hold).not.toHaveBeenCalled()

    auth.hasPermi.mockReturnValue(false)
    wrapper.vm.handleKeydown({ target: { tagName: 'DIV' }, preventDefault })
    expect(modal.msgWarning).toHaveBeenCalledWith('当前账号无「保持/恢复」权限，请联系管理员')
    expect(hold).not.toHaveBeenCalled()

    auth.hasPermi.mockReturnValue(true)
    wrapper.vm.handleKeydown({ target: { tagName: 'DIV' }, preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(hold).toHaveBeenCalled()

    expect(wrapper.vm.shortcutLabel('hold')).toBe('Ctrl+1')
    expect(wrapper.vm.shortcutLabel('none')).toBe('')

    wrapper.vm.shortcuts = [{ id: 'x', label: '无方法', keys: 'Ctrl+9', method: 'notExist' }]
    preventDefault.mockClear()
    wrapper.vm.handleKeydown({ target: { tagName: 'DIV' }, preventDefault })
    expect(preventDefault).not.toHaveBeenCalled()
    wrapper.vm.clearTimer()
  })

  test('快捷键设置：捕获校验、恢复默认与保存', () => {
    const { wrapper, modal } = mountPage()
    const e = { preventDefault: jest.fn(), stopPropagation: jest.fn() }

    wrapper.vm.openShortcutSettings()
    expect(wrapper.vm.shortcutOpen).toBe(true)
    expect(wrapper.vm.shortcutDraft).toHaveLength(2)
    expect(wrapper.vm.shortcutDraft[0]).not.toBe(wrapper.vm.shortcuts[0])

    formatKeys.mockReturnValue('')
    wrapper.vm.captureShortcut(wrapper.vm.shortcutDraft[0], e)
    expect(modal.msgWarning).toHaveBeenCalledWith('请使用 Ctrl/Alt 组合键或 F1–F12 功能键')

    formatKeys.mockReturnValue('Ctrl+1')
    wrapper.vm.captureShortcut(wrapper.vm.shortcutDraft[1], e)
    expect(modal.msgWarning).toHaveBeenCalledWith('该快捷键已被「保持/恢复」占用')

    formatKeys.mockReturnValue('Ctrl+3')
    wrapper.vm.captureShortcut(wrapper.vm.shortcutDraft[1], e)
    expect(wrapper.vm.shortcutDraft[1].keys).toBe('Ctrl+3')

    wrapper.vm.resetShortcutDraft()
    expect(modal.msgSuccess).toHaveBeenCalledWith('已恢复默认快捷键，保存后生效')

    wrapper.vm.saveShortcutSettings()
    expect(saveShortcuts).toHaveBeenCalled()
    expect(wrapper.vm.shortcutOpen).toBe(false)
    expect(modal.msgSuccess).toHaveBeenCalledWith('快捷键已保存')
    wrapper.vm.clearTimer()
  })

  test('文案与语义色映射统一', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.genderLabel('0')).toBe('男')
    expect(wrapper.vm.genderLabel(9)).toBe('未知')
    expect(wrapper.vm.riskLabel('2')).toBe('高风险')
    expect(wrapper.vm.riskLabel(9)).toBe('低风险')
    expect(wrapper.vm.recordStatusLabel('1')).toBe('已完成')
    expect(wrapper.vm.recordStatusTag('3')).toBe('danger')
    expect(wrapper.vm.recordStatusClass('0')).toBe('tag-status--processing')
    expect(wrapper.vm.priorityLabel('1')).toBe('紧急')
    expect(wrapper.vm.priorityLabel(9)).toBe('普通')
    expect(wrapper.vm.ticketStatusLabel('1')).toBe('处理中')
    expect(wrapper.vm.ticketStatusTag('2')).toBe('success')
    expect(wrapper.vm.ticketStatusClass('1')).toBe('tag-status--processing')
    expect(wrapper.vm.tagType(0)).toBe('warning')
    expect(wrapper.vm.tagType(1)).toBe('')
    expect(wrapper.vm.tagType(5)).toBe('')
    expect(wrapper.vm.formatDuration(45)).toBe('45秒')
    expect(wrapper.vm.formatDuration(125)).toBe('2分5秒')
    expect(wrapper.vm.formatDuration(120)).toBe('2分')
    expect(wrapper.vm.formatDuration('bad')).toBe('0秒')
    expect(wrapper.vm.formatTime(3661)).toBe('01:01:01')
    wrapper.vm.clearTimer()
  })
})
