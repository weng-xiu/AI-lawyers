'use strict'

/**
 * 图文会话单测：锁定会话列表/筛选、WS 实时消息、发送与失败回填、结束/转接及轮询降级。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Chat from '@/views/lawyers/chat/index.vue'
import {
  listChatSession, listChatMessage, sendChatMessage,
  closeChatSession, transferChatSession, markRead
} from '@/api/lawyers/chat'
import { createPoller } from '@/utils/polling'

jest.mock('@/api/lawyers/chat', () => ({
  listChatSession: jest.fn(),
  getChatSessionStats: jest.fn(),
  listChatMessage: jest.fn(),
  sendChatMessage: jest.fn(),
  closeChatSession: jest.fn(),
  transferChatSession: jest.fn(),
  markRead: jest.fn()
}))

jest.mock('@/utils/auth', () => ({ getToken: jest.fn(() => 'tok') }))

jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

class WebSocketMock {
  constructor(url) {
    this.url = url
    this.readyState = 0
    WebSocketMock.instances.push(this)
  }
  close() {
    this.readyState = 3
  }
}
WebSocketMock.OPEN = 1
WebSocketMock.instances = []

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.prototype.parseTime = value => (value ? '2026-11-01 08:00' : '')

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const sessions = [
  { sessionId: 1, sessionNo: 'S001', customerName: '张三', customerPhone: '13800138000', lastMessage: '合同问题', channel: '1', status: '0', unreadCount: 2 },
  { sessionId: 2, sessionNo: 'S002', customerName: '李四', customerPhone: '13900139000', lastMessage: '劳动仲裁', channel: '2', status: '1', unreadCount: 0 }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve())
  }
  const message = { success: jest.fn(), info: jest.fn() }
  const wrapper = mount(Chat, {
    localVue,
    stubs: { 'user-select': { props: ['value'], template: '<div class="user-select-stub" />' } },
    mocks: { $modal: modal, $message: message }
  })
  return { wrapper, modal, message }
}

describe('Chat 图文会话', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    WebSocketMock.instances = []
    global.WebSocket = WebSocketMock
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    listChatSession.mockResolvedValue({ rows: sessions })
    listChatMessage.mockResolvedValue({
      rows: [{ messageId: 1, senderType: '1', msgType: '1', content: '你好' }]
    })
    sendChatMessage.mockResolvedValue({})
    closeChatSession.mockResolvedValue({})
    transferChatSession.mockResolvedValue({})
    markRead.mockResolvedValue({})
  })

  test('创建时加载会话列表并渲染概览', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listChatSession).toHaveBeenCalledWith({ pageNum: 1, pageSize: 50 })
    expect(wrapper.vm.sessionList).toHaveLength(2)
    expect(wrapper.vm.sessionLoading).toBe(false)
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('合同问题')
    expect(wrapper.text()).toContain('请选择一个会话开始对话')
  })

  test('关键词按客户名/电话/最后消息过滤会话', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.searchKeyword = '李四'
    expect(wrapper.vm.filteredSessions).toHaveLength(1)
    expect(wrapper.vm.filteredSessions[0].sessionId).toBe(2)

    wrapper.vm.searchKeyword = '1380013'
    expect(wrapper.vm.filteredSessions[0].sessionId).toBe(1)

    wrapper.vm.searchKeyword = '仲裁'
    expect(wrapper.vm.filteredSessions[0].sessionId).toBe(2)

    wrapper.vm.searchKeyword = ''
    expect(wrapper.vm.filteredSessions).toHaveLength(2)
  })

  test('选择会话加载消息并连接房间 WS', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.selectSession(sessions[0])
    await flush()

    expect(wrapper.vm.currentSession).toBe(sessions[0])
    expect(listChatMessage).toHaveBeenCalledWith(1)
    expect(wrapper.vm.messageList).toHaveLength(1)
    expect(WebSocketMock.instances).toHaveLength(1)
    expect(WebSocketMock.instances[0].url).toContain('/ws/chat/1?token=tok')
    expect(markRead).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('你好')
  })

  test('WS 推送去重后插入消息并标记已读', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    await flush()
    const ws = WebSocketMock.instances[0]

    ws.onmessage({ data: 'not-json' })
    expect(wrapper.vm.messageList).toHaveLength(1)

    ws.onmessage({ data: JSON.stringify({ messageId: 1, content: '重复' }) })
    expect(wrapper.vm.messageList).toHaveLength(1)

    ws.onmessage({ data: JSON.stringify({ messageId: 2, content: '新消息' }) })
    expect(wrapper.vm.messageList).toHaveLength(2)
    expect(wrapper.vm.messageList[1].content).toBe('新消息')
    expect(markRead).toHaveBeenCalledTimes(1)
    expect(markRead).toHaveBeenCalledWith(1)
  })

  test('WS 排空通知后自动重连当前会话', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    const ws = WebSocketMock.instances[0]

    ws.onmessage({ data: JSON.stringify({ type: 'SERVER_DRAINING' }) })
    await new Promise(resolve => setTimeout(resolve, 400))

    expect(WebSocketMock.instances).toHaveLength(2)
    expect(WebSocketMock.instances[1].url).toContain('/ws/chat/1')
  })

  test('发送消息成功后清空输入并刷新消息与会话', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    await flush()
    wrapper.vm.inputText = '  请问怎么起诉  '

    wrapper.vm.handleSend()
    await flush()

    expect(sendChatMessage).toHaveBeenCalledWith({ sessionId: 1, content: '请问怎么起诉', msgType: '1' })
    expect(wrapper.vm.inputText).toBe('')
    expect(listChatMessage).toHaveBeenCalledTimes(2)
    expect(listChatSession).toHaveBeenCalledTimes(2)
  })

  test('空白消息不发送，发送失败回填输入框', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.inputText = '   '
    wrapper.vm.handleSend()
    expect(sendChatMessage).not.toHaveBeenCalled()

    wrapper.vm.selectSession(sessions[0])
    await flush()
    wrapper.vm.inputText = '您好'
    sendChatMessage.mockRejectedValueOnce(new Error('fail'))
    wrapper.vm.handleSend()
    await flush()

    expect(wrapper.vm.inputText).toBe('您好')
  })

  test('结束会话确认后清空当前会话并刷新列表', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    await flush()
    modal.dangerConfirm.mockClear()

    wrapper.vm.handleClose()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '确认结束当前会话？'
    }))
    expect(closeChatSession).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('会话已结束')
    expect(wrapper.vm.currentSession).toBeNull()
    expect(wrapper.vm.messageList).toEqual([])
    expect(listChatSession).toHaveBeenCalledTimes(2)
  })

  test('转接会话选择受理人后提交', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    await flush()

    wrapper.vm.handleTransfer()
    expect(wrapper.vm.transferOpen).toBe(true)
    expect(wrapper.vm.transferUserId).toBeNull()

    wrapper.vm.onTransferUserChange(9, { nickName: '王律师' })
    expect(wrapper.vm.transferUserId).toBe(9)
    expect(wrapper.vm.transferUserName).toBe('王律师')

    wrapper.vm.onTransferUserChange(10, { userName: 'li' })
    expect(wrapper.vm.transferUserName).toBe('li')

    wrapper.vm.confirmTransfer()
    await flush()
    expect(transferChatSession).toHaveBeenCalledWith({ sessionId: 1, userId: 10, assignee: 'li' })
    expect(message.success).toHaveBeenCalledWith('转接成功')
    expect(wrapper.vm.transferOpen).toBe(false)
  })

  test('渠道、会话状态与时间格式化', () => {
    const { wrapper } = mountPage()
    const own = new Date()
    const other = new Date(2020, 0, 2, 3, 4)

    expect(wrapper.vm.channelText('2')).toBe('视频辅助')
    expect(wrapper.vm.channelText('9')).toBe('图文')
    expect(wrapper.vm.channelTag('1')).toBe('primary')
    expect(wrapper.vm.channelTag('9')).toBe('info')
    expect(wrapper.vm.sessionStatusText('2')).toBe('转人工')
    expect(wrapper.vm.sessionStatusText('9')).toBe('未知')
    expect(wrapper.vm.sessionStatusTag('0')).toBe('success')
    expect(wrapper.vm.sessionStatusTag('9')).toBe('info')
    expect(wrapper.vm.formatTime(null)).toBe('')
    expect(wrapper.vm.formatTime(own)).toBe(
      String(own.getHours()).padStart(2, '0') + ':' + String(own.getMinutes()).padStart(2, '0')
    )
    expect(wrapper.vm.formatTime(other)).toBe('1/2 03:04')
  })

  test('上传占位提示与轮询降级逻辑', async () => {
    const { wrapper, message } = mountPage()
    await flush()

    wrapper.vm.handleUpload('image')
    expect(message.info).toHaveBeenCalledWith('文件上传功能建设中，当前仅支持文字消息')

    expect(createPoller).toHaveBeenCalledWith(expect.any(Function), 5000)
    const poll = createPoller.mock.calls[0][0]
    wrapper.vm.currentSession = sessions[0]
    wrapper.vm.chatWs = { readyState: 1 }
    listChatMessage.mockClear()
    await poll()
    expect(listChatMessage).not.toHaveBeenCalled()

    wrapper.vm.chatWs = null
    await poll()
    expect(listChatMessage).toHaveBeenCalledWith(1)
  })

  test('销毁时停止轮询并关闭 WS', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.selectSession(sessions[0])
    await flush()
    const ws = WebSocketMock.instances[0]
    const stop = jest.spyOn(wrapper.vm.poller, 'stop')

    wrapper.destroy()

    expect(stop).toHaveBeenCalledTimes(1)
    expect(ws.readyState).toBe(3)
  })
})
