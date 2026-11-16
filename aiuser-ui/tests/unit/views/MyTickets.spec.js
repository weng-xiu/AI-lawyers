'use strict'

/**
 * P5① 公众端组件单测：我的工单 MyTickets
 * 覆盖：列表/未读数加载、状态筛选、消息深链定位与清理、字典映射、空态与跳转。
 */
jest.mock('@/utils/a11y', () => ({ announce: jest.fn() }))
jest.mock('@/api/portal', () => ({
  myTickets: jest.fn(),
  myTicketDetail: jest.fn(),
  getUnreadCount: jest.fn()
}))

import { mount, createLocalVue, RouterLinkStub } from '@vue/test-utils'
import ElementUI from 'element-ui'
import MyTickets from '@/views/MyTickets'
import { announce } from '@/utils/a11y'
import { myTickets, myTicketDetail, getUnreadCount } from '@/api/portal'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const ticketRows = [
  { ticketId: 1, ticketNo: 'T-001', status: '0', priority: '1', title: '工资拖欠咨询' }
]

const mountTickets = (query = {}) => {
  const $route = { query }
  const $router = { push: jest.fn(), replace: jest.fn().mockResolvedValue() }
  const wrapper = mount(MyTickets, {
    localVue,
    mocks: { $route, $router },
    stubs: { RouterLink: RouterLinkStub }
  })
  return { wrapper, $router, $route }
}

describe('MyTickets', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    myTickets.mockResolvedValue({ rows: ticketRows, total: 1 })
    myTicketDetail.mockResolvedValue({ data: null })
    getUnreadCount.mockResolvedValue({ data: 2 })
  })

  it('创建时加载工单列表与未读数', async () => {
    const { wrapper } = mountTickets()
    await flushPromises()
    expect(myTickets).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.ticketList).toHaveLength(1)
    expect(wrapper.vm.total).toBe(1)
    expect(wrapper.vm.unread).toBe(2)
    expect(announce).toHaveBeenCalledWith('工单加载完成，共 1 条，第 1 到 1 条', 'polite')
  })

  it('无工单时展示空态与咨询入口', async () => {
    myTickets.mockResolvedValue({ rows: [], total: 0 })
    const { wrapper } = mountTickets()
    await flushPromises()
    expect(wrapper.find('.empty-box').exists()).toBe(true)
    expect(wrapper.find('.empty-box').text()).toContain('暂无工单记录')
  })

  it('状态筛选会重置页码并重新请求', async () => {
    const { wrapper } = mountTickets()
    await flushPromises()
    wrapper.vm.query.pageNum = 4
    wrapper.vm.handleFilter('2')
    expect(wrapper.vm.query.status).toBe('2')
    expect(wrapper.vm.query.pageNum).toBe(1)
    expect(myTickets).toHaveBeenLastCalledWith(expect.objectContaining({ status: '2', pageNum: 1 }))
  })

  it('无深链参数时不请求工单详情', async () => {
    const { wrapper } = mountTickets()
    await flushPromises()
    expect(wrapper.vm.focusTicket).toBeNull()
    expect(myTicketDetail).not.toHaveBeenCalled()
  })

  it('按消息深链定位工单并播报', async () => {
    myTicketDetail.mockResolvedValue({ data: { ticketId: 7, ticketNo: 'T-007', status: '1' } })
    const { wrapper } = mountTickets({ ticketId: '7' })
    await flushPromises()
    expect(myTicketDetail).toHaveBeenCalledWith('7')
    expect(wrapper.vm.focusTicket.ticketId).toBe(7)
    expect(wrapper.vm.focusInList).toBe(false)
    expect(announce).toHaveBeenCalledWith('已定位到工单 T-007', 'polite')
  })

  it('深链工单已在当前列表页时 focusInList 为真', async () => {
    myTicketDetail.mockResolvedValue({ data: { ticketId: 1, ticketNo: 'T-001' } })
    const { wrapper } = mountTickets({ ticketId: '1' })
    await flushPromises()
    expect(wrapper.vm.focusInList).toBe(true)
  })

  it('深链详情请求失败时清理深链参数', async () => {
    myTicketDetail.mockRejectedValue(new Error('forbidden'))
    const { wrapper, $router } = mountTickets({ ticketId: '9' })
    await flushPromises()
    expect(wrapper.vm.focusTicket).toBeNull()
    expect(announce).toHaveBeenCalledWith('未能定位该工单，请稍后重试', 'assertive')
    expect($router.replace).toHaveBeenCalledWith({ path: '/ticket', query: {} })
  })

  it('收起定位工单时保留其他查询参数', () => {
    const { wrapper, $router } = mountTickets({ ticketId: '7', status: '2' })
    wrapper.vm.clearFocus()
    expect(wrapper.vm.focusTicket).toBeNull()
    expect($router.replace).toHaveBeenCalledWith({ path: '/ticket', query: { status: '2' } })
  })

  it('空态按钮跳转法律咨询', () => {
    const { wrapper, $router } = mountTickets()
    wrapper.vm.goConsult()
    expect($router.push).toHaveBeenCalledWith('/consultation/submit')
  })

  it('工单状态/优先级/转办字典映射与兜底', () => {
    const { wrapper } = mountTickets()
    expect(wrapper.vm.statusText('0')).toBe('待处理')
    expect(wrapper.vm.statusText('3')).toBe('已归档')
    expect(wrapper.vm.statusText('99')).toBe('待处理')
    expect(wrapper.vm.statusTag('2')).toBe('success')
    expect(wrapper.vm.statusTag('99')).toBe('warning')
    expect(wrapper.vm.priorityText('1')).toBe('紧急')
    expect(wrapper.vm.priorityText('99')).toBe('一般')
    expect(wrapper.vm.priorityTag('1')).toBe('danger')
    expect(wrapper.vm.priorityTag('3')).toBe('info')
  })

  it('转办机构与转办状态映射及时间格式化', () => {
    const { wrapper } = mountTickets()
    expect(wrapper.vm.externalTypeText('HOTLINE_12345')).toBe('12345政务热线')
    expect(wrapper.vm.externalTypeText('CUSTOM')).toBe('CUSTOM')
    expect(wrapper.vm.externalStatusText('DONE')).toBe('已办结')
    expect(wrapper.vm.externalStatusText('UNKNOWN')).toBe('UNKNOWN')
    expect(wrapper.vm.formatTime('2026-11-16T09:30:00')).toBe('2026-11-16 09:30')
    expect(wrapper.vm.formatTime(null)).toBe('-')
  })
})
