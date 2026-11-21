'use strict'

/**
 * 律师工作台单测：锁定工作台三类聚合数据加载、统计卡与通话状态映射、
 * 快捷入口跳转、以及 F7 通话一键生成工单（含关联待办与权限分支）。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Workbench from '@/views/lawyers/workbench/index.vue'
import { getWorkbenchStats, getWorkbenchTodos, getWorkbenchRecentCalls, addTodo } from '@/api/lawyers/workbench'
import { addTicket } from '@/api/lawyers/callCenter'
import { checkPermi } from '@/utils/permission'

jest.mock('@/api/lawyers/workbench', () => ({
  getWorkbenchStats: jest.fn(),
  getWorkbenchTodos: jest.fn(),
  getWorkbenchRecentCalls: jest.fn(),
  addTodo: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({
  addTicket: jest.fn()
}))

jest.mock('@/utils/permission', () => ({
  checkPermi: jest.fn()
}))

// 待办/公告面板内部链路会引入 store 与静态资源，这里以桩组件隔离
jest.mock('@/views/lawyers/workbench/todo', () => ({
  name: 'TodoPanel',
  template: '<div />'
}))
jest.mock('@/views/lawyers/workbench/notice', () => ({
  name: 'NoticePanel',
  template: '<div />'
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const routerPush = jest.fn()
const messageInfo = jest.fn()
const msgSuccess = jest.fn()
const todoPanelGetList = jest.fn()

const TodoPanelStub = {
  name: 'TodoPanel',
  template: '<div class="todo-panel-stub" />',
  methods: { getList: todoPanelGetList }
}
const NoticePanelStub = {
  name: 'NoticePanel',
  template: '<div class="notice-panel-stub" />'
}

function mountPage() {
  return mount(Workbench, {
    localVue,
    stubs: { TodoPanel: TodoPanelStub, NoticePanel: NoticePanelStub },
    mocks: {
      $store: { getters: { name: '张律师' } },
      $router: { push: routerPush },
      $message: { info: messageInfo },
      $modal: { msgSuccess }
    }
  })
}

describe('Workbench 律师工作台', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    checkPermi.mockReturnValue(true)
    getWorkbenchStats.mockResolvedValue({
      data: { todayCalls: 5, serviceDuration: 90, satisfaction: 96, onlineDuration: 125, agentStatus: '1' }
    })
    getWorkbenchTodos.mockResolvedValue({ data: [] })
    getWorkbenchRecentCalls.mockResolvedValue({ data: [] })
    addTicket.mockResolvedValue({ data: { ticketId: 9, title: '李四 · 劳动咨询', content: '内容' } })
    addTodo.mockResolvedValue({})
  })

  test('创建时初始化日期、用户名并并行加载三块数据', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.currentDate).toContain('年')
    expect(wrapper.vm.currentDate).toContain('星期')
    expect(wrapper.vm.userName).toBe('张律师')
    expect(getWorkbenchStats).toHaveBeenCalledTimes(1)
    expect(getWorkbenchTodos).toHaveBeenCalledTimes(1)
    expect(getWorkbenchRecentCalls).toHaveBeenCalledWith(5)
    expect(wrapper.vm.stats.todayCalls).toBe(5)
    expect(wrapper.vm.stats.agentStatus).toBe('1')
    expect(wrapper.vm.recordLoading).toBe(false)
  })

  test('聚合接口异常时保持默认数据并结束加载态', async () => {
    getWorkbenchStats.mockRejectedValue(new Error('boom'))
    getWorkbenchTodos.mockRejectedValue(new Error('boom'))
    getWorkbenchRecentCalls.mockRejectedValue(new Error('boom'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.stats.todayCalls).toBe(0)
    expect(wrapper.vm.todoLoading).toBe(false)
    expect(wrapper.vm.recordLoading).toBe(false)
  })

  test('统计卡与在线状态文案按工作台数据装配', () => {
    const wrapper = mountPage()
    wrapper.vm.stats = {
      todayCalls: 3,
      serviceDuration: 90,
      satisfaction: 98,
      onlineDuration: 125,
      agentStatus: '1'
    }

    const cards = wrapper.vm.statCards
    expect(cards).toHaveLength(4)
    expect(cards[0].value).toBe('3 次')
    expect(cards[1].value).toBe('1小时30分')
    expect(cards[2].value).toBe('98%')
    expect(cards[3].value).toBe('2小时5分')
    expect(wrapper.vm.agentStatusText).toBe('在线')

    wrapper.vm.stats.agentStatus = '9'
    expect(wrapper.vm.agentStatusText).toBe('离线')
  })

  test('时长格式化覆盖秒/分/小时与非法入参', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.formatDuration(0)).toBe('0分')
    expect(wrapper.vm.formatDuration('bad')).toBe('0分')
    expect(wrapper.vm.formatDuration(45)).toBe('45秒')
    expect(wrapper.vm.formatDuration(90)).toBe('1分')
    expect(wrapper.vm.formatDuration(3700)).toBe('1小时1分')
  })

  test('日历待办红点仅匹配当天且未完成的待办', () => {
    const wrapper = mountPage()
    wrapper.vm.todoList = [
      { dueDate: '2026-11-21 10:00', status: '0' },
      { dueDate: '2026-11-22 10:00', status: '1' }
    ]

    expect(wrapper.vm.hasTodoOnDate('2026-11-21')).toBe(true)
    expect(wrapper.vm.hasTodoOnDate('2026-11-22')).toBe(false)
    expect(wrapper.vm.hasTodoOnDate('2026-11-23')).toBe(false)
  })

  test('快捷入口有路径时跳转，无路径时提示敬请期待', () => {
    const wrapper = mountPage()

    wrapper.vm.handleQuickClick({ name: '来电弹屏', path: '/inbound/callPopup' })
    expect(routerPush).toHaveBeenCalledWith({ path: '/inbound/callPopup' })

    wrapper.vm.handleQuickClick({ name: '敬请期待' })
    expect(messageInfo).toHaveBeenCalledWith('敬请期待 敬请期待')
  })

  test('通话状态文案/颜色统一委托 statusTag 工具', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.recordStatusLabel('0')).toBe('接通中')
    expect(wrapper.vm.recordStatusTag('0')).toBe('')
    expect(wrapper.vm.recordStatusClass('0')).toBe('tag-status--processing')
    expect(wrapper.vm.recordStatusLabel('1')).toBe('已完成')
    expect(wrapper.vm.recordStatusTag('1')).toBe('success')
    expect(wrapper.vm.recordStatusLabel('3')).toBe('未接')
    expect(wrapper.vm.recordStatusLabel('9')).toBe('未知')
  })

  test('从通话记录生成工单时预填来电要素并打开弹窗', async () => {
    const wrapper = mountPage()

    wrapper.vm.handleCreateTicket({
      recordId: 7,
      callerName: '李四',
      callerNumber: '13800000000',
      categoryName: '劳动',
      duration: 65
    })
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.ticketOpen).toBe(true)
    expect(wrapper.vm.ticketForm).toMatchObject({
      title: '李四 · 劳动咨询',
      recordId: 7,
      callerName: '李四',
      priority: '1'
    })
    expect(wrapper.vm.ticketForm.content).toContain('来电人：李四')
    expect(wrapper.vm.ticketForm.content).toContain('咨询类别：劳动')
    expect(wrapper.vm.ticketForm.content).toContain('通话时长：1分')
    expect(wrapper.vm.$refs.ticketForm).toBeTruthy()
  })

  test('提交工单成功后同步生成关联待办并刷新待办列表', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.ticketOpen = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.ticketForm.validate = jest.fn(cb => cb(true))
    wrapper.vm.ticketForm.title = '李四 · 劳动咨询'
    wrapper.vm.ticketForm.recordId = 7
    wrapper.vm.ticketForm.content = '工单内容'

    let sent = null
    addTicket.mockImplementation(payload => {
      sent = JSON.parse(JSON.stringify(payload))
      return Promise.resolve({ data: { ticketId: 9, title: payload.title, content: payload.content } })
    })
    getWorkbenchTodos.mockClear()

    wrapper.vm.submitTicket()
    await flushPromises()

    expect(sent).toEqual({
      title: '李四 · 劳动咨询',
      recordId: 7,
      content: '工单内容',
      priority: '1',
      remark: undefined
    })
    expect(msgSuccess).toHaveBeenCalledWith('工单创建成功')
    expect(wrapper.vm.ticketOpen).toBe(false)
    expect(wrapper.vm.ticketSubmitting).toBe(false)
    expect(addTodo).toHaveBeenCalledWith({
      todoTitle: '处理工单：李四 · 劳动咨询',
      todoContent: '工单内容',
      priority: '1',
      bizType: 'ticket',
      bizId: 9
    })
    expect(getWorkbenchTodos).toHaveBeenCalledTimes(1)
    expect(todoPanelGetList).toHaveBeenCalled()
  })

  test('表单校验不通过时不提交工单', async () => {
    const wrapper = mountPage()

    wrapper.vm.ticketOpen = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.ticketForm.validate = jest.fn(cb => cb(false))

    wrapper.vm.submitTicket()
    await flushPromises()

    expect(addTicket).not.toHaveBeenCalled()
    expect(wrapper.vm.ticketSubmitting).toBe(false)
  })

  test('无权限或无工单ID时不生成关联待办', async () => {
    const wrapper = mountPage()

    wrapper.vm.createLinkedTodo({ ticketId: 9, title: 'X' })
    await flushPromises()
    expect(addTodo).toHaveBeenCalledTimes(1)

    jest.clearAllMocks()
    checkPermi.mockReturnValue(false)
    wrapper.vm.createLinkedTodo({ ticketId: 10, title: 'Y' })
    wrapper.vm.createLinkedTodo(null)
    await flushPromises()
    expect(addTodo).not.toHaveBeenCalled()
  })
})
