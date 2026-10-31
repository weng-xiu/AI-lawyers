'use strict'

/**
 * F7 待办-工单联动单测：
 * 状态/优先级按字符串比较（后端字段为字符串，避免 '0' !== 0 导致「处理」按钮与文案失效），
 * 抽屉办结工单后不再由前端重复关闭待办（服务端已按 bizId 统一同步）。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Todo from '@/views/lawyers/workbench/todo.vue'
import {
  listTodo,
  getTodoTicket,
  processTodo,
  deferTodo,
  ignoreTodo
} from '@/api/lawyers/workbench'
import { completeTicket, processTicket } from '@/api/lawyers/callCenter'
import { createTransfer } from '@/api/lawyers/ticketTransfer'
import { listExternalOrg } from '@/api/lawyers/externalOrg'

jest.mock('@/api/lawyers/workbench', () => ({
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
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.parseTime = () => '2026-10-31 10:00'
localVue.prototype.resetForm = () => {}

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn(),
  // 危险操作确认：直接放行
  dangerConfirm: jest.fn(() => Promise.resolve())
}

function todoRow(overrides = {}) {
  return Object.assign({
    todoId: 1,
    todoTitle: '跟进工单',
    todoContent: '请及时回访',
    status: '0',
    priority: '2',
    dueDate: '2026-11-02',
    bizType: 'ticket',
    bizId: 88
  }, overrides)
}

function mountPage(rows = [todoRow()]) {
  listTodo.mockResolvedValue({ rows, total: rows.length })
  return mount(Todo, {
    localVue,
    stubs: {
      pagination: { template: '<div class="pagination-stub" />' },
      'right-toolbar': { template: '<div class="toolbar-stub" />' }
    },
    mocks: {
      $modal: modal,
      $store: { state: { user: { id: 1001, nickName: '张律师' } }, getters: { nickName: '张律师' } }
    }
  })
}

describe('Todo F7 待办-工单联动', () => {
  let wrapper

  beforeEach(() => {
    jest.clearAllMocks()
    getTodoTicket.mockResolvedValue({ data: null })
    completeTicket.mockResolvedValue({})
    processTicket.mockResolvedValue({})
    createTransfer.mockResolvedValue({})
    listExternalOrg.mockResolvedValue({ rows: [] })
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  it('状态/优先级文案按字符串匹配（兼容数字入参）', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(wrapper.vm.statusLabel('0')).toBe('待办')
    expect(wrapper.vm.statusLabel(1)).toBe('已完成')
    expect(wrapper.vm.statusLabel('3')).toBe('忽略')
    expect(wrapper.vm.priorityLabel('1')).toBe('紧急')
    expect(wrapper.vm.priorityLabel(2)).toBe('普通')
  })

  it('仅待办状态展示「处理」按钮，字符串状态同样生效', async () => {
    wrapper = mountPage([todoRow({ status: '0' }), todoRow({ todoId: 2, status: '1' })])
    await flushPromises()

    expect(wrapper.vm.isPendingTodo('0')).toBe(true)
    expect(wrapper.vm.isPendingTodo(0)).toBe(true)
    expect(wrapper.vm.isPendingTodo('1')).toBe(false)

    const actionButtons = wrapper.findAll('.el-table__body .el-button')
      .wrappers.filter(btn => btn.text().includes('处理'))
    expect(actionButtons.length).toBe(1)
  })

  it('新增/修改表单默认值与选项均为字符串，保证单选回显', async () => {
    wrapper = mountPage()

    wrapper.vm.reset()
    expect(wrapper.vm.form.status).toBe('0')
    expect(wrapper.vm.form.priority).toBe('2')

    // 弹窗首次打开后才渲染表单项
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    const radios = wrapper.findAll('.el-radio')
    expect(radios.length).toBe(3)
    radios.wrappers.forEach(radio => {
      expect(typeof radio.vm.$options.propsData.label).toBe('string')
    })
  })

  it('抽屉办结工单后由服务端同步关闭待办，前端不再重复请求', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.handleOpenTicket(todoRow({ todoId: 5 }))
    wrapper.vm.ticketDrawer.ticket = { ticketId: 88, status: '1', title: '法律咨询工单' }

    await wrapper.vm.handleCompleteTicket()
    await flushPromises()

    expect(completeTicket).toHaveBeenCalledWith({ ticketId: 88 })
    expect(processTodo).not.toHaveBeenCalled()
  })

  it('延后/忽略走独立接口，不关闭工单', async () => {
    wrapper = mountPage()

    await wrapper.vm.handleDefer(todoRow())
    await flushPromises()
    expect(deferTodo).toHaveBeenCalledWith(1)

    await wrapper.vm.handleIgnore(todoRow({ todoId: 3 }))
    await flushPromises()
    expect(ignoreTodo).toHaveBeenCalledWith(3)
  })
})
