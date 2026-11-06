import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import QueueMonitor from '@/views/lawyers/queueMonitor/index.vue'
import Schedule from '@/views/lawyers/schedule/index.vue'
import { listQueue, listDeadLetter, replayDeadLetter } from '@/api/lawyers/queueMonitor'
import { createPoller } from '@/utils/polling'
import { listShift, listEnabledShifts, listSchedule, todaySchedule } from '@/api/lawyers/schedule'
import { listAgent } from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/queueMonitor', () => ({
  listQueue: jest.fn(),
  listDeadLetter: jest.fn(),
  replayDeadLetter: jest.fn(),
  removeDeadLetter: jest.fn()
}))
jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn()
}))
jest.mock('@/api/lawyers/schedule', () => ({
  listShift: jest.fn(),
  getShift: jest.fn(),
  addShift: jest.fn(),
  updateShift: jest.fn(),
  delShift: jest.fn(),
  listEnabledShifts: jest.fn(),
  listSchedule: jest.fn(),
  getSchedule: jest.fn(),
  addSchedule: jest.fn(),
  updateSchedule: jest.fn(),
  delSchedule: jest.fn(),
  batchSchedule: jest.fn(),
  checkIn: jest.fn(),
  checkOut: jest.fn(),
  todaySchedule: jest.fn()
}))
jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn()
}))
// 子页签组件会间接引入请求/静态资源，测试只关注两张列表表，直接以空组件替换
jest.mock('@/views/lawyers/schedule/forecastSlot.vue', () => ({
  default: { name: 'ForecastSlot', template: '<div />' }
}))
jest.mock('@/views/lawyers/schedule/shiftChange.vue', () => ({
  default: { name: 'ShiftChange', template: '<div />' }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = () => '2026-11-01 10:00'
localVue.prototype.resetForm = jest.fn()
localVue.prototype.download = jest.fn()
localVue.prototype.addDateRange = (params, dateRange) => {
  params.params = {
    beginTime: dateRange && dateRange[0],
    endTime: dateRange && dateRange[1]
  }
  return params
}

const stubs = {
  'right-toolbar': true,
  pagination: true,
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}|{{ actionText }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

const mocks = {
  $route: { path: '/lawyers/queueMonitor', query: {} },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    undo: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn(), info: jest.fn() }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component, path) {
  return mount(component, {
    localVue,
    stubs,
    mocks: { ...mocks, $route: { path, query: {} } }
  })
}

function tableByKey(wrapper, key) {
  return wrapper.findAllComponents(ProTable).wrappers.find(t => t.props('persistKey') === key)
}

function cellTextIn(tableWrapper, label, rowIndex = 0) {
  const index = tableWrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return tableWrapper.findAll('.el-table__body tr').at(rowIndex).findAll('td').at(index).text()
}

function rowButtonTexts(tableWrapper, rowIndex = 0) {
  return tableWrapper.findAll('.el-table__body tr').at(rowIndex).findAll('button').wrappers.map(b => b.text()).filter(t => t.length > 0)
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('queueMonitor 2 表 ProTable 迁移', () => {
  it('队列水位表 + 死信弹窗表：计数口径/告警样式、独立 persist-key 与重投保持', async () => {
    const poller = { start: jest.fn(), stop: jest.fn() }
    createPoller.mockImplementation(() => poller)
    listQueue.mockResolvedValue({
      data: [
        { queue: 'call-events', streamKey: 'stream:call', streamLen: 0, pending: 12, dead: 3 },
        { queue: 'ai-task', streamKey: 'stream:ai', streamLen: -1, pending: null, dead: 0 }
      ]
    })
    listDeadLetter.mockResolvedValue({
      rows: [{ id: 'dl-1', originId: 'msg-1', error: '消费超时', payload: '{"foo":"bar"}' }],
      total: 1
    })
    replayDeadLetter.mockResolvedValue({})

    const wrapper = mountPage(QueueMonitor, '/lawyers/queueMonitor')
    await flush()

    expect(listQueue).toHaveBeenCalledTimes(1)
    expect(createPoller).toHaveBeenCalled()
    expect(poller.start).toHaveBeenCalled()

    const queueTable = tableByKey(wrapper, 'queueMonitor:queue')
    expect(queueTable).toBeTruthy()
    expect(queueTable.props('showSearch')).toBe(false)
    expect(queueTable.props('showToolbar')).toBe(false)
    expect(queueTable.props('hidePagination')).toBe(true)
    expect(queueTable.props('tableAttrs')).toEqual({ border: true })
    expect(queueTable.props('columns').map(c => c.label)).toEqual([
      '队列', 'Stream Key', 'Stream 长度', '积压(未ACK)', '死信数', '操作'
    ])
    expect(queueTable.props('columns').every(c => !c.fixed)).toBe(true)

    expect(cellTextIn(queueTable, '队列')).toBe('call-events')
    expect(cellTextIn(queueTable, 'Stream 长度')).toBe('0')
    expect(cellTextIn(queueTable, '积压(未ACK)')).toBe('12')
    expect(cellTextIn(queueTable, '死信数')).toBe('3')
    expect(queueTable.find('.el-table__body tr td:nth-child(4) span').attributes('style')).toContain('font-weight: 600')
    expect(cellTextIn(queueTable, 'Stream 长度', 1)).toBe('查询失败')
    expect(rowButtonTexts(queueTable)).toEqual(['死信管理'])

    wrapper.vm.openDeadDialog({ queue: 'call-events' })
    await flush()
    await wrapper.vm.$nextTick()

    expect(listDeadLetter).toHaveBeenCalledWith({ queue: 'call-events', pageNum: 1, pageSize: 10 })
    const deadTable = tableByKey(wrapper, 'queueMonitor:dead')
    expect(deadTable).toBeTruthy()
    expect(deadTable.props('showSearch')).toBe(false)
    expect(deadTable.props('showToolbar')).toBe(false)
    expect(deadTable.props('tableAttrs')).toEqual({ border: true, maxHeight: '420px' })
    expect(deadTable.props('columns').map(c => c.label)).toEqual([
      '死信ID', '原始消息ID', '失败原因', '消息体', '操作'
    ])
    expect(cellTextIn(deadTable, '死信ID')).toBe('dl-1')
    expect(cellTextIn(deadTable, '失败原因')).toBe('消费超时')
    expect(deadTable.find('.payload-cell').text()).toContain('"foo":"bar"')
    expect(rowButtonTexts(deadTable)).toEqual(['重投', '删除'])

    wrapper.vm.handleReplay({ id: 'dl-1' })
    await flush()
    expect(replayDeadLetter).toHaveBeenCalledWith('call-events', 'dl-1')
    expect(mocks.$modal.msgSuccess).toHaveBeenCalledWith('重投成功')

    wrapper.destroy()
    expect(poller.stop).toHaveBeenCalled()
  })

  it('队列水位表：空态展示内建文案', async () => {
    createPoller.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    listQueue.mockResolvedValue({ data: [] })
    const wrapper = mountPage(QueueMonitor, '/lawyers/queueMonitor')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无队列水位数据')
    wrapper.destroy()
  })
})

describe('schedule 2 表 ProTable 迁移', () => {
  it('班次表 + 排班表：标签/开关/签到签退分支、查询插槽与重置口径保持', async () => {
    listShift.mockResolvedValue({
      rows: [
        { shiftId: 1, shiftName: '早班', startTime: '09:00', endTime: '18:00', crossDay: '0', breakMinutes: 60, status: '1', remark: null },
        { shiftId: 2, shiftName: '夜班', startTime: '22:00', endTime: '06:00', crossDay: 1, breakMinutes: null, status: '0', remark: '跨零点' }
      ]
    })
    listEnabledShifts.mockResolvedValue({ data: [{ shiftId: 1, shiftName: '早班' }] })
    listAgent.mockResolvedValue({ rows: [{ agentId: 101, agentName: '张三' }] })
    listSchedule.mockResolvedValue({
      rows: [
        { scheduleId: 1, agentName: null, shiftName: null, scheduleDate: '2026-11-01', scheduleType: '1', checkInTime: null, checkOutTime: null, status: 0 },
        { scheduleId: 2, agentName: '张三', shiftName: '早班', scheduleDate: '2026-11-01', scheduleType: '2', checkInTime: '2026-11-01 09:01', checkOutTime: null, status: 1 },
        { scheduleId: 3, agentName: '李四', shiftName: '早班', scheduleDate: '2026-11-01', scheduleType: '3', checkInTime: '2026-11-01 09:01', checkOutTime: '2026-11-01 18:02', status: 2 }
      ],
      total: 3
    })
    todaySchedule.mockResolvedValue({ data: [{ scheduleId: 9, agentName: '王五', shiftName: '早班', scheduleDate: '2026-11-06', status: 0 }] })

    const wrapper = mountPage(Schedule, '/lawyers/schedule')
    await flush()

    expect(listShift).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
    expect(listSchedule).toHaveBeenCalledTimes(1)
    const firstCall = listSchedule.mock.calls[0][0]
    expect(firstCall).toEqual(expect.objectContaining({ pageNum: 1, pageSize: 10, shiftId: undefined, agentId: undefined }))
    expect(firstCall.params.beginTime).toBeTruthy()
    expect(firstCall.params.endTime).toBeTruthy()

    // 班次表
    const shiftTable = tableByKey(wrapper, 'schedule:shift')
    expect(shiftTable).toBeTruthy()
    expect(shiftTable.props('showSearch')).toBe(false)
    expect(shiftTable.props('hidePagination')).toBe(true)
    expect(shiftTable.props('tableAttrs')).toEqual({ border: true })
    expect(shiftTable.props('columns').map(c => c.label)).toEqual([
      '班次名称', '上班时间', '下班时间', '是否跨天', '休息时间(分)', '状态', '备注', '操作'
    ])
    expect(shiftTable.props('columns').every(c => !c.fixed)).toBe(true)
    expect(cellTextIn(shiftTable, '班次名称')).toBe('早班')
    expect(cellTextIn(shiftTable, '是否跨天')).toBe('当日')
    expect(cellTextIn(shiftTable, '休息时间(分)')).toBe('60')
    expect(cellTextIn(shiftTable, '备注')).toBe('-')
    expect(cellTextIn(shiftTable, '是否跨天', 1)).toBe('跨天')
    expect(cellTextIn(shiftTable, '休息时间(分)', 1)).toBe('-')
    expect(cellTextIn(shiftTable, '备注', 1)).toBe('跨零点')
    expect(shiftTable.findAll('.el-table__body .el-switch').length).toBe(2)
    expect(rowButtonTexts(shiftTable)).toEqual(['编辑', '删除'])

    // 排班表
    const scheduleTable = tableByKey(wrapper, 'schedule:list')
    expect(scheduleTable).toBeTruthy()
    expect(scheduleTable.props('total')).toBe(3)
    expect(scheduleTable.props('columns').map(c => c.label)).toEqual([
      '排班ID', '坐席', '班次', '排班日期', '排班类型', '签到时间', '签退时间', '状态', '操作'
    ])
    expect(scheduleTable.props('columns').every(c => !c.fixed)).toBe(true)
    expect(scheduleTable.props('queryFormItems').map(i => i.label)).toEqual(['班次', '坐席', '日期范围'])
    expect(scheduleTable.props('queryFormItems')[0].options).toEqual([{ label: '早班', value: 1 }])
    expect(scheduleTable.props('queryFormItems')[1].options).toEqual([{ label: '张三', value: 101 }])
    expect(scheduleTable.findAll('.el-form .el-date-editor').length).toBe(1)
    expect(scheduleTable.find('.el-form button').text()).toBe('搜索')
    expect(scheduleTable.findAll('.el-form button').wrappers.map(b => b.text())).toContain('今日排班')

    expect(cellTextIn(scheduleTable, '坐席')).toBe('-')
    expect(cellTextIn(scheduleTable, '班次')).toBe('-')
    expect(cellTextIn(scheduleTable, '排班类型')).toBe('正常班')
    expect(cellTextIn(scheduleTable, '签到时间')).toBe('-')
    expect(cellTextIn(scheduleTable, '状态')).toBe('待签到')
    expect(cellTextIn(scheduleTable, '排班类型', 1)).toBe('加班')
    expect(cellTextIn(scheduleTable, '状态', 1)).toBe('已签到')
    expect(cellTextIn(scheduleTable, '状态', 2)).toBe('已签退')
    expect(rowButtonTexts(scheduleTable, 0)).toEqual(['签到', '编辑', '删除'])
    expect(rowButtonTexts(scheduleTable, 1)).toEqual(['签退', '编辑', '删除'])
    expect(rowButtonTexts(scheduleTable, 2)).toEqual(['编辑', '删除'])

    // 重置：清空班次/坐席并恢复默认近 7 天日期区间后重查
    wrapper.vm.scheduleQuery.shiftId = 1
    wrapper.vm.scheduleQuery.agentId = 101
    wrapper.vm.resetScheduleQuery()
    await flush()
    expect(wrapper.vm.scheduleQuery.pageNum).toBe(1)
    expect(wrapper.vm.scheduleQuery.shiftId).toBeUndefined()
    expect(wrapper.vm.scheduleQuery.agentId).toBeUndefined()
    expect(wrapper.vm.scheduleDateRange.length).toBe(2)
    expect(listSchedule).toHaveBeenCalledTimes(2)

    // 今日排班：直接替换列表数据与总数
    wrapper.vm.loadToday()
    await flush()
    expect(wrapper.vm.scheduleTotal).toBe(1)
    expect(cellTextIn(scheduleTable, '坐席')).toBe('王五')
    expect(mocks.$modal.msgSuccess).toHaveBeenCalledWith('已加载今日排班')
  })

  it('班次表：空态展示内建文案', async () => {
    listShift.mockResolvedValue({ rows: [] })
    listEnabledShifts.mockResolvedValue({ data: [] })
    listAgent.mockResolvedValue({ rows: [] })
    listSchedule.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(Schedule, '/lawyers/schedule')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无班次')
  })
})
