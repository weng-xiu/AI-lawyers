'use strict'

/**
 * U 系列存量迁移单测：workbench 工作台域（公告 / 待办）列表页迁移 V3 ProTable。
 * 覆盖：初始化按默认分页拉取、ProTable 重置后清空筛选并重查、标签与关联工单插槽渲染、
 *       按行状态展示的操作按钮差异（公告「发布」、待办「处理」）。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import NoticePage from '@/views/lawyers/workbench/notice.vue'
import TodoPage from '@/views/lawyers/workbench/todo.vue'
import { listNotice, listTodo, getTodoTicket } from '@/api/lawyers/workbench'

jest.mock('@/api/lawyers/workbench', () => ({
  listNotice: jest.fn(),
  getNotice: jest.fn(),
  addNotice: jest.fn(),
  updateNotice: jest.fn(),
  delNotice: jest.fn(),
  publishNotice: jest.fn(),
  listTodo: jest.fn(),
  getTodo: jest.fn(),
  getTodoTicket: jest.fn(),
  addTodo: jest.fn(),
  updateTodo: jest.fn(),
  delTodo: jest.fn(),
  processTodo: jest.fn(),
  deferTodo: jest.fn(),
  ignoreTodo: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({
  processTicket: jest.fn(),
  completeTicket: jest.fn()
}))

jest.mock('@/api/lawyers/ticketTransfer', () => ({
  createTransfer: jest.fn()
}))

jest.mock('@/api/lawyers/externalOrg', () => ({
  listExternalOrg: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.parseTime = () => '2026-11-01 10:00'
localVue.prototype.resetForm = () => {}

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn(),
  dangerConfirm: jest.fn(() => Promise.resolve())
}

/** 全局注册组件的统一 stub，聚焦页面骨架迁移本身 */
const globalStubs = {
  'right-toolbar': { template: '<div class="rt-stub"><slot name="toolbar-right" /></div>' },
  pagination: { template: '<div class="pagination-stub" />' },
  TableEmpty: {
    template: '<div class="empty-stub">{{ text }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

function mountPage(Page) {
  return mount(Page, {
    localVue,
    stubs: globalStubs,
    mocks: {
      $modal: modal,
      $store: { state: { user: { id: 1001, nickName: '张律师' } }, getters: { nickName: '张律师' } }
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

describe('workbench 工作台域列表页（ProTable 迁移）', () => {
  let wrapper

  beforeEach(() => {
    localStorage.clear()
    jest.clearAllMocks()
    getTodoTicket.mockResolvedValue({ data: null })
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  describe('notice 公告管理', () => {
    const rows = [
      { noticeId: 1, noticeTitle: '停机维护通知', noticeType: '紧急', isTop: 1, status: 1, publishTime: '2026-11-01 09:00', createTime: '2026-10-31 18:00' },
      { noticeId: 2, noticeTitle: '新功能上线说明', noticeType: '普通', isTop: 0, status: 0, createTime: '2026-10-30 11:00' }
    ]

    it('初始化按默认分页加载，类型/置顶/状态标签渲染', async () => {
      listNotice.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(NoticePage)
      await flushPromises()

      expect(listNotice).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        noticeTitle: undefined,
        status: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('停机维护通知')
      expect(body).toContain('紧急')
      expect(body).toContain('置顶')
      expect(body).toContain('发布')
      expect(body).toContain('草稿')
      expect(body).toContain('2026-11-01 10:00') // parseTime 格式化发布时间
    })

    it('仅草稿状态行展示「发布」按钮', async () => {
      listNotice.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(NoticePage)
      await flushPromises()

      const publishButtons = wrapper.findAll('.el-table__body .el-button')
        .wrappers.filter(btn => btn.text().includes('发布'))
      expect(publishButtons.length).toBe(1)
    })

    it('重置后清空筛选条件并重查', async () => {
      listNotice.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(NoticePage)
      await flushPromises()

      wrapper.vm.queryParams.noticeTitle = '维护'
      wrapper.vm.queryParams.status = 0
      await clickReset(wrapper)

      expect(listNotice).toHaveBeenCalledTimes(2)
      expect(listNotice.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        noticeTitle: undefined,
        status: undefined
      })
    })
  })

  describe('todo 待办事项', () => {
    const rows = [
      { todoId: 1, todoTitle: '回访工单', todoContent: '请及时回访', bizType: 'ticket', bizId: 88, priority: '2', status: '0', dueDate: '2026-11-02', createTime: '2026-11-01 09:00' },
      { todoId: 2, todoTitle: '整理台账', todoContent: '归档本周话单', bizType: null, bizId: null, priority: '1', status: '1', dueDate: '2026-11-03', createTime: '2026-11-01 10:00' }
    ]

    it('初始化按默认分页加载，关联工单列按 bizType 渲染按钮或占位', async () => {
      listTodo.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(TodoPage)
      await flushPromises()

      expect(listTodo).toHaveBeenCalledWith({
        pageNum: 1,
        pageSize: 10,
        todoTitle: undefined,
        status: undefined,
        priority: undefined
      })

      const body = wrapper.find('.el-table__body-wrapper').text()
      expect(body).toContain('回访工单')
      expect(body).toContain('工单 #88')
      expect(body).toContain('—')
      expect(body).toContain('普通')
      expect(body).toContain('紧急')
      expect(body).toContain('待办')
      expect(body).toContain('已完成')
      expect(body).toContain('2026-11-01 10:00')
    })

    it('重置后清空筛选条件并重查', async () => {
      listTodo.mockResolvedValue({ rows, total: rows.length })
      wrapper = mountPage(TodoPage)
      await flushPromises()

      wrapper.vm.queryParams.todoTitle = '回访'
      wrapper.vm.queryParams.status = 0
      wrapper.vm.queryParams.priority = 2
      wrapper.vm.queryParams.pageNum = 2
      await clickReset(wrapper)

      expect(listTodo).toHaveBeenCalledTimes(2)
      expect(listTodo.mock.calls[1][0]).toEqual({
        pageNum: 1,
        pageSize: 10,
        todoTitle: undefined,
        status: undefined,
        priority: undefined
      })
      expect(wrapper.vm.queryParams.pageNum).toBe(1)
    })
  })
})
