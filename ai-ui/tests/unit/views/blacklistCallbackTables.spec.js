'use strict'

/**
 * U 系列存量迁移单测：blacklist / callback 域三个列表页迁移 V3 ProTable。
 * 覆盖：初始化按默认分页拉取、时间区间经 addDateRange 转 beginTime/endTime、
 *       标签与缺失值占位渲染、ProTable 重置后清空筛选（含日期区间）并重查。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import BlacklistPage from '@/views/lawyers/blacklist/index.vue'
import CallbackPage from '@/views/lawyers/callback/index.vue'
import CallbackTaskPage from '@/views/lawyers/callback/task.vue'
import { listBlacklist } from '@/api/lawyers/blacklist'
import { listCallback, getCallbackStats, getSatisfactionTrend } from '@/api/lawyers/callback'
import { listReturnVisitTask, getReturnVisitTaskStats } from '@/api/lawyers/returnVisitTask'

jest.mock('@/api/lawyers/blacklist', () => ({
  listBlacklist: jest.fn(),
  getBlacklist: jest.fn(),
  addBlacklist: jest.fn(),
  updateBlacklist: jest.fn(),
  delBlacklist: jest.fn(),
  checkPhone: jest.fn()
}))

jest.mock('@/api/lawyers/callback', () => ({
  listCallback: jest.fn(),
  getCallback: jest.fn(),
  addCallback: jest.fn(),
  updateCallback: jest.fn(),
  delCallback: jest.fn(),
  getCallbackStats: jest.fn(),
  getSatisfactionTrend: jest.fn()
}))

jest.mock('@/api/lawyers/returnVisitTask', () => ({
  listReturnVisitTask: jest.fn(),
  getReturnVisitTask: jest.fn(),
  addReturnVisitTask: jest.fn(),
  updateReturnVisitTask: jest.fn(),
  delReturnVisitTask: jest.fn(),
  getReturnVisitTaskStats: jest.fn()
}))

jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.parseTime = () => '2026-11-01 10:00'
localVue.prototype.resetForm = () => {}
localVue.prototype.addDateRange = (params, dateRange) => {
  const p = Object.assign({}, params)
  if (dateRange && dateRange.length === 2) {
    p.beginTime = dateRange[0]
    p.endTime = dateRange[1]
  }
  return p
}

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn(),
  dangerConfirm: jest.fn(() => Promise.resolve()),
  undo: jest.fn()
}

/** 全局注册组件的统一 stub，聚焦页面骨架迁移本身 */
const globalStubs = {
  'right-toolbar': { template: '<div class="rt-stub"><slot name="toolbar-right" /></div>' },
  pagination: { template: '<div class="pagination-stub" />' },
  TableEmpty: {
    template: '<div class="empty-stub">{{ text }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  },
  'user-select': {
    template: '<input class="user-select-stub" />',
    props: ['value', 'placeholder', 'clearable']
  },
  // 弹窗组件由 main.js 全局注册，测试里以 stub 占位避免告警
  'pro-dialog': { template: '<div class="pro-dialog-stub"><slot /></div>' },
  'pro-form': { template: '<div class="pro-form-stub"><slot /></div>' }
}

/** 按表头文案定位列下标（含选择列，与 tbody 单元格一一对应） */
function columnIndexOf(wrapper, label) {
  return wrapper.findAll('.el-table__header-wrapper th')
    .wrappers.findIndex(th => th.text().includes(label))
}

/** 取指定行指定列的文本 */
function cellText(wrapper, rowIndex, label) {
  const index = columnIndexOf(wrapper, label)
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(rowIndex).findAll('td').at(index).text()
}

function mountPage(Page) {
  return mount(Page, {
    localVue,
    stubs: globalStubs,
    mocks: {
      $modal: modal,
      $message: { success: jest.fn(), error: jest.fn() },
      $route: { query: {} }
    }
  })
}

/** 点击 ProTable 查询区的「重置」按钮（搜索在前、重置在后） */
async function clickReset(wrapper) {
  const resetBtn = wrapper.findAll('.el-form .el-button')
    .wrappers.find(btn => btn.text().includes('重置'))
  expect(resetBtn).toBeTruthy()
  await resetBtn.trigger('click')
  await flushPromises()
}

describe('blacklist / callback 域列表页（ProTable 迁移）', () => {
  let wrapper

  beforeEach(() => {
    localStorage.clear()
    jest.clearAllMocks()
    getCallbackStats.mockResolvedValue({ data: {} })
    getSatisfactionTrend.mockResolvedValue({ data: [] })
    getReturnVisitTaskStats.mockResolvedValue({ data: {} })
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  describe('blacklist 名单管理', () => {
    const rows = [
      { id: 1, phoneNumber: '13800000001', listType: '1', reason: '多次骚扰', status: '1', effectiveStart: '2026-10-01 00:00:00', createTime: '2026-10-01 09:00' },
      { id: 2, phoneNumber: '13800000002', listType: '2', reason: null, status: '0', createTime: '2026-10-02 09:00' }
    ]

    it('初始化按默认分页加载，名单类型标签与状态开关渲染', async () => {
      listBlacklist.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(BlacklistPage)
      await flushPromises()

      expect(listBlacklist).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        phoneNumber: undefined,
        listType: undefined,
        status: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('黑名单')
      expect(body).toContain('白名单')
      expect(body).toContain('多次骚扰')
      expect(body).toContain('2026-11-01 10:00')

      // 每行一个启停开关
      expect(wrapper.findAll('.el-table__body .el-switch').length).toBe(rows.length)
    })

    it('重置后清空筛选条件并重查', async () => {
      listBlacklist.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(BlacklistPage)
      await flushPromises()

      wrapper.vm.queryParams.phoneNumber = '138'
      wrapper.vm.queryParams.listType = '1'
      wrapper.vm.queryParams.status = '0'
      await clickReset(wrapper)

      expect(listBlacklist).toHaveBeenCalledTimes(2)
      expect(listBlacklist.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        phoneNumber: undefined,
        listType: undefined,
        status: undefined
      })
    })
  })

  describe('callback 客户回访', () => {
    const rows = [
      { callbackId: 1, callerNumber: '13800000001', callerName: '张先生', visitTime: '2026-11-01 09:00', visitBy: '李律师', satisfaction: '1', status: '1', visitOpinion: '沟通顺畅' },
      { callbackId: 2, callerNumber: '13800000002', callerName: '王女士', visitTime: null, visitBy: null, satisfaction: null, status: '0', visitOpinion: null }
    ]

    it('初始化按默认分页加载，满意度/状态标签与缺失值占位渲染', async () => {
      listCallback.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(CallbackPage)
      await flushPromises()

      expect(listCallback).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        callerName: undefined,
        satisfaction: undefined,
        status: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('非常满意')
      expect(body).toContain('已完成')
      expect(body).toContain('待回访')
      expect(body).toContain('李律师')
      expect(body).toContain('未评价')
      // 缺失值降级：visitTime / visitBy 渲染占位符
      expect(cellText(wrapper, 1, '回访时间')).toBe('-')
      expect(cellText(wrapper, 1, '回访人')).toBe('-')
    })

    it('回访时间区间经 addDateRange 转为 beginTime/endTime，重置后清空区间', async () => {
      listCallback.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(CallbackPage)
      await flushPromises()

      // 区间控件由 #query-dateRange 插槽渲染
      expect(wrapper.find('.el-form .el-date-editor').exists()).toBe(true)

      wrapper.vm.dateRange = ['2026-10-01', '2026-10-31']
      wrapper.vm.queryParams.callerNumber = '138'
      await clickReset(wrapper)

      expect(listCallback).toHaveBeenCalledTimes(2)
      const params = listCallback.mock.calls[1][0]
      expect(params.pageNum).toBe(1)
      expect(params.callerNumber).toBeUndefined()
      expect(params.beginTime).toBeUndefined()
      expect(params.endTime).toBeUndefined()
      expect(wrapper.vm.dateRange).toEqual([])
    })
  })

  describe('callback/task 回访任务', () => {
    const rows = [
      { taskId: 1, taskNo: 'RV20261101001', callerNumber: '13800000001', callerName: '张先生', priority: '1', planTime: '2026-11-02 09:00', actualTime: null, assignee: '李律师', status: '2', visitResult: '未接通' },
      { taskId: 2, taskNo: 'RV20261101002', callerNumber: '13800000002', callerName: '王女士', priority: '3', planTime: '2026-11-02 10:00', actualTime: '2026-11-02 10:30', assignee: null, status: '0', visitResult: null }
    ]

    it('初始化按默认分页加载，优先级/状态标签与受理人占位渲染', async () => {
      listReturnVisitTask.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(CallbackTaskPage)
      await flushPromises()

      expect(listReturnVisitTask).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        taskNo: undefined,
        callerNumber: undefined,
        callerName: undefined,
        assignee: undefined,
        assigneeId: undefined,
        priority: undefined,
        status: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('高')
      expect(body).toContain('低')
      expect(body).toContain('已逾期')
      expect(body).toContain('待回访')
      expect(body).toContain('李律师')
      // 缺失值降级：actualTime / assignee 渲染占位符
      expect(cellText(wrapper, 0, '实际回访时间')).toBe('-')
      expect(cellText(wrapper, 1, '受理人')).toBe('-')
    })

    it('重置后清空筛选条件与计划时间区间并重查', async () => {
      listReturnVisitTask.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(CallbackTaskPage)
      await flushPromises()

      wrapper.vm.dateRange = ['2026-11-01', '2026-11-30']
      wrapper.vm.queryParams.taskNo = 'RV2026'
      wrapper.vm.queryParams.priority = '1'
      wrapper.vm.queryParams.pageNum = 2
      await clickReset(wrapper)

      expect(listReturnVisitTask).toHaveBeenCalledTimes(2)
      const params = listReturnVisitTask.mock.calls[1][0]
      expect(params.pageNum).toBe(1)
      expect(params.taskNo).toBeUndefined()
      expect(params.priority).toBeUndefined()
      expect(params.beginTime).toBeUndefined()
      expect(wrapper.vm.dateRange).toEqual([])
    })
  })
})
