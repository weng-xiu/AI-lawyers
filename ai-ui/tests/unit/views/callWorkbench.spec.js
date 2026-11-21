'use strict'

/**
 * 坐席工作台首页单测：锁定汇总数据装配（横幅/KPI/团队/最近通话）、
 * 今天/本周末筛选与分页、待办处理、公告映射与状态文案映射。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CallWorkbench from '@/views/lawyers/callCenter/callWorkbench.vue'
import { getWorkbenchSummary } from '@/api/lawyers/callCenter'
import { listNotice } from '@/api/system/notice'
import { getWorkbenchTodos, processTodo } from '@/api/lawyers/workbench'

jest.mock('@/api/lawyers/callCenter', () => ({
  getWorkbenchSummary: jest.fn()
}))

jest.mock('@/api/system/notice', () => ({
  listNotice: jest.fn()
}))

jest.mock('@/api/lawyers/workbench', () => ({
  getWorkbenchTodos: jest.fn(),
  processTodo: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const routerPush = jest.fn()
const msgSuccess = jest.fn()

const today = new Date().toISOString().substring(0, 10)

const summary = {
  todayCallStats: { todayCalls: 8, todayServiceDuration: 3720 },
  totalStats: { totalCount: 1234, avgRating: 4.567, avgDuration: 95 },
  teamStats: [{ callCount: 10 }, { callCount: 5 }],
  onlineAgentCount: 6,
  recentCalls: [
    {
      callerNumber: '13800000000',
      callerName: '张三',
      duration: 65,
      status: '1',
      callTime: today + ' 09:00:00',
      callType: '2'
    },
    { duration: 0, status: '0', callTime: '2020-01-01 09:00:00' }
  ]
}

function mountPage() {
  return mount(CallWorkbench, {
    localVue,
    stubs: {
      TableEmpty: {
        template: '<div class="table-empty-stub" />',
        props: ['text', 'hint', 'actionText', 'actionIcon', 'icon', 'compact']
      }
    },
    mocks: {
      $store: { getters: { name: '李律师' } },
      $router: { push: routerPush },
      $modal: { msgSuccess }
    }
  })
}

describe('CallWorkbench 坐席工作台首页', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getWorkbenchSummary.mockResolvedValue({ data: summary })
    getWorkbenchTodos.mockResolvedValue({ data: [] })
    listNotice.mockResolvedValue({ rows: [] })
    processTodo.mockResolvedValue({})
  })

  test('创建时加载汇总、待办与公告并初始化日期与用户名', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.currentDate).toContain('年')
    expect(wrapper.vm.currentDate).toContain('星期')
    expect(wrapper.vm.userName).toBe('李律师')
    expect(getWorkbenchSummary).toHaveBeenCalledTimes(1)
    expect(getWorkbenchTodos).toHaveBeenCalledTimes(1)
    expect(listNotice).toHaveBeenCalledWith({ pageNum: 1, pageSize: 6, status: '1' })
  })

  test('汇总数据装配横幅、KPI 卡、团队概览与最近通话', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.bannerStats).toEqual([
      { value: 8, label: '今日通话' },
      { value: 6, label: '在线坐席' },
      { value: '4.6%', label: '满意度' }
    ])

    const cards = wrapper.vm.statCards
    expect(cards[0].value).toBe('1,234')
    expect(cards[1].value).toBe(8)
    expect(cards[1].trend).toBe('+8')
    expect(cards[2].value).toBe('1时2分')
    expect(cards[3].value).toBe('4.6分')

    expect(wrapper.vm.teamItems.map(t => t.value)).toEqual(['15', 2, '4.6%', '1分35秒'])

    expect(wrapper.vm.allCalls[0]).toMatchObject({
      callerNumber: '13800000000',
      callerName: '张三',
      duration: 65,
      status: '1',
      callType: '2'
    })
    expect(wrapper.vm.allCalls[1]).toMatchObject({
      callerNumber: '-',
      callerName: '-',
      content: '-',
      callTime: '2020-01-01 09:00:00'
    })
  })

  test('汇总接口异常时保持初始占位值', async () => {
    getWorkbenchSummary.mockRejectedValue(new Error('boom'))
    getWorkbenchTodos.mockRejectedValue(new Error('boom'))
    listNotice.mockRejectedValue(new Error('boom'))

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.statCards[0].value).toBe('-')
    expect(wrapper.vm.todoLoading).toBe(false)
    expect(wrapper.vm.noticeLoading).toBe(false)
  })

  test('最近通话按今天/本周筛选并按页大小分页', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.filteredCalls).toHaveLength(1)
    expect(wrapper.vm.pagedCalls).toHaveLength(1)

    wrapper.vm.allCalls = Array.from({ length: 8 }, (_, i) => ({
      callerNumber: '138' + i,
      callTime: today + ' 10:00:00'
    }))
    expect(wrapper.vm.filteredCalls).toHaveLength(8)
    expect(wrapper.vm.pagedCalls).toHaveLength(6)

    wrapper.vm.currentPage = 2
    expect(wrapper.vm.pagedCalls).toHaveLength(2)

    wrapper.vm.callFilter = 'week'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.currentPage).toBe(1)
    expect(wrapper.vm.filteredCalls).toHaveLength(8)
  })

  test('待办只保留未完成项且最多展示 6 条', async () => {
    const rows = Array.from({ length: 7 }, (_, i) => ({ todoId: i + 1, status: '0' }))
    rows.push({ todoId: 99, status: '1' })
    getWorkbenchTodos.mockResolvedValue({ data: rows })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.todoList).toHaveLength(6)
    expect(wrapper.vm.todoList.every(t => t.status === '0')).toBe(true)
    expect(wrapper.vm.pendingTodoCount).toBe(6)
  })

  test('公告映射标题/内容与截取日期，首条标记为新', async () => {
    listNotice.mockResolvedValue({
      rows: [
        { noticeId: 1, noticeTitle: '值班安排', noticeContent: '内容', createTime: '2026-11-20 09:00:00' },
        { noticeId: 2, noticeTitle: '系统升级', createTime: null }
      ]
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.noticeList[0]).toEqual({
      noticeId: 1,
      title: '值班安排',
      content: '内容',
      date: '2026-11-20'
    })
    expect(wrapper.vm.noticeList[1].date).toBe('-')
    expect(wrapper.find('.wb-notice-item .el-tag').exists()).toBe(true)
  })

  test('点击公告打开详情弹窗，点击待办跳转待办页', async () => {
    const wrapper = mountPage()
    wrapper.vm.showNotice({ title: '通知', date: '2026-11-20', content: '正文' })

    expect(wrapper.vm.noticeOpen).toBe(true)
    expect(wrapper.vm.curNotice).toEqual({ title: '通知', date: '2026-11-20', content: '正文' })

    wrapper.vm.handleTodoClick({ todoId: 1 })
    expect(routerPush).toHaveBeenCalledWith('/workbench/todo')

    wrapper.vm.handleQuick('/inbound/callPopup')
    expect(routerPush).toHaveBeenCalledWith('/inbound/callPopup')
    routerPush.mockClear()
    wrapper.vm.handleQuick('')
    expect(routerPush).not.toHaveBeenCalled()
  })

  test('标记待办完成调用接口并移出列表', async () => {
    const wrapper = mountPage()
    await flushPromises()
    wrapper.vm.todoList = [{ todoId: 1 }, { todoId: 2 }]

    wrapper.vm.markTodoDone({ todoId: 1 })
    await flushPromises()

    expect(processTodo).toHaveBeenCalledWith(1)
    expect(msgSuccess).toHaveBeenCalledWith('已标记完成')
    expect(wrapper.vm.todoList).toEqual([{ todoId: 2 }])
  })

  test('日历待办红点按日期前缀匹配', () => {
    const wrapper = mountPage()
    wrapper.vm.todoList = [{ dueDate: '2026-11-21 10:00' }, { dueDate: null }]

    expect(wrapper.vm.hasTodoOnDate('2026-11-21')).toBe(true)
    expect(wrapper.vm.hasTodoOnDate('2026-11-22')).toBe(false)
  })

  test('类型/状态/优先级文案与标签色映射及兜底', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.serviceLabel({ callType: '1' })).toBe('语音')
    expect(wrapper.vm.serviceLabel({ callType: '2' })).toBe('图文')
    expect(wrapper.vm.serviceLabel({ category: '3' })).toBe('视频')
    expect(wrapper.vm.serviceLabel({})).toBe('语音')
    expect(wrapper.vm.serviceTag({ callType: '2' })).toBe('success')
    expect(wrapper.vm.serviceTag({})).toBe('primary')

    expect(wrapper.vm.statusLabel('0')).toBe('未接听')
    expect(wrapper.vm.statusLabel('1')).toBe('已接听')
    expect(wrapper.vm.statusLabel('2')).toBe('已转接')
    expect(wrapper.vm.statusLabel('3')).toBe('漏接')
    expect(wrapper.vm.statusLabel('9')).toBe('-')
    expect(wrapper.vm.statusTag('3')).toBe('danger')
    expect(wrapper.vm.statusTag('9')).toBe('info')

    expect(wrapper.vm.priorityLabel('1')).toBe('紧急')
    expect(wrapper.vm.priorityLabel('9')).toBe('普通')
    expect(wrapper.vm.priorityTag('1')).toBe('danger')
    expect(wrapper.vm.priorityTag('9')).toBe('info')
  })

  test('时长格式化覆盖秒/分/时与空值', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.fmtDuration(0)).toBe('0秒')
    expect(wrapper.vm.fmtDuration(45)).toBe('45秒')
    expect(wrapper.vm.fmtDuration(95)).toBe('1分35秒')
    expect(wrapper.vm.fmtDuration(120)).toBe('2分')
    expect(wrapper.vm.fmtDuration(3720)).toBe('1时2分')
  })

  test('问候语随时段落在预设集合内', () => {
    const wrapper = mountPage()

    expect(['夜深了', '上午好', '中午好', '下午好', '晚上好']).toContain(wrapper.vm.greeting)
  })
})
