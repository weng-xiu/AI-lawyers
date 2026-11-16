'use strict'

/**
 * P5① 公众端组件单测：消息提醒 Messages
 * 覆盖：列表/未读数加载、空态、筛选、单条已读、全部已读、工单深链、文案与时间格式化。
 */
jest.mock('@/utils/a11y', () => ({ announce: jest.fn() }))
jest.mock('@/api/portal', () => ({
  getMyMessages: jest.fn(),
  getUnreadCount: jest.fn(),
  readMessage: jest.fn(),
  readAllMessages: jest.fn()
}))

import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Messages from '@/views/Messages'
import { announce } from '@/utils/a11y'
import { getMyMessages, getUnreadCount, readMessage, readAllMessages } from '@/api/portal'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { messageId: 1, msgType: '5', isRead: '0', title: '工单已受理', bizType: 'ticket', bizId: 88, createTime: '2026-11-16T09:30:00' },
  { messageId: 2, msgType: '1', isRead: '1', title: '系统通知', createTime: '2026-11-15T18:00:00' }
]

const mountMessages = (overrides = {}) => {
  const $router = { push: jest.fn() }
  const $message = { success: jest.fn() }
  const wrapper = mount(Messages, {
    localVue,
    mocks: { $router, $message },
    ...overrides
  })
  return { wrapper, $router, $message }
}

describe('Messages', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getMyMessages.mockResolvedValue({ rows, total: 2 })
    getUnreadCount.mockResolvedValue({ data: 3 })
    readMessage.mockResolvedValue({})
    readAllMessages.mockResolvedValue({})
  })

  it('创建时加载消息列表与未读数', async () => {
    const { wrapper } = mountMessages()
    await flushPromises()
    expect(getMyMessages).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.messageList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.unread).toBe(3)
    expect(announce).toHaveBeenCalledWith('消息加载完成，共 2 条', 'polite')
  })

  it('无消息时展示空态与查看工单按钮', async () => {
    getMyMessages.mockResolvedValue({ rows: [], total: 0 })
    const { wrapper } = mountMessages()
    await flushPromises()
    expect(wrapper.find('.empty-box').exists()).toBe(true)
    expect(wrapper.find('.empty-box').text()).toContain('暂无消息提醒')
  })

  it('切换到未读筛选会重置页码并重新请求', async () => {
    const { wrapper } = mountMessages()
    await flushPromises()
    wrapper.vm.query.pageNum = 3
    wrapper.vm.handleFilter('0')
    expect(wrapper.vm.query.isRead).toBe('0')
    expect(wrapper.vm.query.pageNum).toBe(1)
    expect(getMyMessages).toHaveBeenLastCalledWith(expect.objectContaining({ isRead: '0', pageNum: 1 }))
  })

  it('未读判断仅对 isRead=0 生效', () => {
    const { wrapper } = mountMessages()
    expect(wrapper.vm.isUnread({ isRead: '0' })).toBe(true)
    expect(wrapper.vm.isUnread({ isRead: '1' })).toBe(false)
  })

  it('标记单条已读后本地状态与未读数同步', async () => {
    const { wrapper } = mountMessages()
    await flushPromises()
    const item = wrapper.vm.messageList[0]
    wrapper.vm.handleRead(item)
    await flushPromises()
    expect(readMessage).toHaveBeenCalledWith(1)
    expect(item.isRead).toBe('1')
    expect(wrapper.vm.unread).toBe(2)
    expect(wrapper.vm.readingId).toBeNull()
    expect(announce).toHaveBeenCalledWith('已标记为已读', 'polite')
  })

  it('未读数为 0 时「全部已读」不发请求', async () => {
    const { wrapper } = mountMessages()
    await flushPromises()
    wrapper.vm.unread = 0
    await wrapper.vm.handleReadAll()
    expect(readAllMessages).not.toHaveBeenCalled()
  })

  it('全部已读成功后将列表置为已读并刷新列表', async () => {
    const { wrapper, $message } = mountMessages()
    await flushPromises()
    await wrapper.vm.handleReadAll()
    expect(readAllMessages).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.messageList.every(item => item.isRead === '1')).toBe(true)
    expect(wrapper.vm.unread).toBe(0)
    expect($message.success).toHaveBeenCalledWith('已全部标记为已读')
    expect(getMyMessages).toHaveBeenCalledTimes(2)
  })

  it('已读消息点击「查看工单进度」直接深链跳转', async () => {
    const { wrapper, $router } = mountMessages()
    await flushPromises()
    await wrapper.vm.goTicket({ messageId: 2, bizId: 88, isRead: '1' })
    expect(readMessage).not.toHaveBeenCalled()
    expect($router.push).toHaveBeenCalledWith({ path: '/ticket', query: { ticketId: '88' } })
  })

  it('未读消息先标记已读再深链跳转', async () => {
    const { wrapper, $router } = mountMessages()
    await flushPromises()
    wrapper.vm.goTicket({ messageId: 1, bizId: 88, isRead: '0' })
    await flushPromises()
    expect(readMessage).toHaveBeenCalledWith(1)
    expect($router.push).toHaveBeenCalledWith({ path: '/ticket', query: { ticketId: '88' } })
  })

  it('消息类型与时间格式化兜底', () => {
    const { wrapper } = mountMessages()
    expect(wrapper.vm.msgTypeText('5')).toBe('工单通知')
    expect(wrapper.vm.msgTypeText('99')).toBe('其他消息')
    expect(wrapper.vm.msgTypeTag('3')).toBe('danger')
    expect(wrapper.vm.msgTypeTag('unknown')).toBe('info')
    expect(wrapper.vm.formatTime('2026-11-16T09:30:00')).toBe('2026-11-16 09:30')
    expect(wrapper.vm.formatTime(null)).toBe('-')
  })
})
