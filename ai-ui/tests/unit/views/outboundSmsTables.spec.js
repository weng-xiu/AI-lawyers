import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import OutboundTask from '@/views/lawyers/outbound/task/index.vue'
import OutboundCallee from '@/views/lawyers/outbound/callee/index.vue'
import OutboundResult from '@/views/lawyers/outbound/result.vue'
import SmsConfig from '@/views/lawyers/sms/config.vue'
import SmsLog from '@/views/lawyers/sms/log.vue'
import SmsTemplate from '@/views/lawyers/sms/template.vue'
import { listTask } from '@/api/lawyers/outboundTask'
import { listCallee } from '@/api/lawyers/outboundCallee'
import { listResult } from '@/api/lawyers/outboundResult'
import { listSmsConfig, listSmsLog, listSmsTemplate, listEnabledTemplates, listEnabledConfigs } from '@/api/lawyers/sms'

jest.mock('@/api/lawyers/outboundTask', () => ({
  listTask: jest.fn(),
  getTask: jest.fn(),
  delTask: jest.fn(),
  addTask: jest.fn(),
  updateTask: jest.fn(),
  generateTaskNo: jest.fn(() => Promise.resolve({ data: 'T20261101' })),
  startTask: jest.fn(),
  pauseTask: jest.fn(),
  stopTask: jest.fn(),
  executeTask: jest.fn()
}))
jest.mock('@/api/lawyers/outboundCallee', () => ({
  listCallee: jest.fn(),
  getCallee: jest.fn(),
  delCallee: jest.fn(),
  addCallee: jest.fn(),
  updateCallee: jest.fn(),
  delCalleeByTaskId: jest.fn(),
  batchAddCallee: jest.fn()
}))
jest.mock('@/api/lawyers/outboundResult', () => ({
  listResult: jest.fn(),
  getTaskResultStatistics: jest.fn()
}))
jest.mock('@/api/lawyers/sms', () => ({
  listSmsConfig: jest.fn(),
  getSmsConfig: jest.fn(),
  addSmsConfig: jest.fn(),
  updateSmsConfig: jest.fn(),
  delSmsConfig: jest.fn(),
  listSmsLog: jest.fn(),
  delSmsLog: jest.fn(),
  sendSms: jest.fn(),
  listEnabledTemplates: jest.fn(),
  listSmsTemplate: jest.fn(),
  getSmsTemplate: jest.fn(),
  addSmsTemplate: jest.fn(),
  updateSmsTemplate: jest.fn(),
  delSmsTemplate: jest.fn(),
  listEnabledConfigs: jest.fn()
}))
jest.mock('@/api/lawyers/ivrFlow', () => ({ getPublishedFlows: jest.fn(() => Promise.resolve({ data: [] })) }))
jest.mock('@/utils/polling', () => ({ createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() })) }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = () => '2026-11-01 10:00'
localVue.prototype.resetForm = jest.fn()
localVue.prototype.download = jest.fn()

const stubs = {
  'right-toolbar': true,
  pagination: true,
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  },
  // 弹窗/表单组件由 main.js 全局注册，测试里以 stub 占位避免告警
  'pro-dialog': { template: '<div class="pro-dialog-stub"><slot /></div>' },
  'pro-form': { template: '<div class="pro-form-stub"><slot /></div>' },
  'pro-form-item': { template: '<div class="pro-form-item-stub"><slot /></div>' }
}

const mocks = routeQuery => ({
  $route: { path: '/lawyers/outbound', query: routeQuery || {} },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    undo: jest.fn(),
    msgWarning: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
})

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component, routeQuery) {
  return mount(component, { localVue, stubs, mocks: mocks(routeQuery) })
}

function cellTextOf(wrapper, propLabel) {
  const index = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(propLabel))
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(0).findAll('td').at(index).text()
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('outbound 域列表页 ProTable 迁移', () => {
  it('外呼任务：初始化分页查询，任务类型/状态标签与列口径保持', async () => {
    listTask.mockResolvedValue({
      rows: [{ taskName: '满意度回访', taskNo: 'T20261101', taskType: '2', status: '1', totalCount: 10, completedCount: 4, answeredCount: 3, createTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(OutboundTask)
    await flush()

    expect(listTask).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    const table = wrapper.findComponent(ProTable)
    expect(table.props('columns').map(c => c.label)).toEqual([
      '任务名称', '任务编号', '任务类型', '总号码数', '已完成', '已接听', '状态', '创建时间', '操作'
    ])
    const body = wrapper.find('.el-table__body-wrapper').text()
    expect(body).toContain('满意度回访')
    expect(body).toContain('回访')
    expect(body).toContain('进行中')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')

    // 重建空态主操作仍指向新增任务
    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
  })

  it('外呼号码：路由带入任务 ID 时隐藏任务筛选，性别/呼叫状态标签正常', async () => {
    listCallee.mockResolvedValue({
      rows: [{ calleeName: '王女士', phoneNumber: '13800000000', gender: '1', area: '杭州', callCount: 2, callStatus: '1', lastCallTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(OutboundCallee, { taskId: '3' })
    await flush()

    expect(listCallee).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10, taskId: '3' }))
    const labels = wrapper.findComponent(ProTable).props('queryFormItems').map(i => i.label)
    expect(labels).toEqual(['姓名', '手机号', '呼叫状态'])
    expect(wrapper.findComponent(ProTable).props('hasSelection')).toBe(true)

    const body = wrapper.find('.el-table__body-wrapper').text()
    expect(body).toContain('女')
    expect(body).toContain('已接听')
    expect(cellTextOf(wrapper, '最近呼叫时间')).toBe('2026-11-01 10:00')

    // 重置保留路由带入的任务 ID
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.taskId).toBe('3')
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
  })

  it('外呼结果：任务下拉走查询插槽，状态/时长/重试列按原口径渲染', async () => {
    listTask.mockResolvedValue({ rows: [{ taskId: 1, taskName: '满意度回访' }] })
    listResult.mockResolvedValue({
      rows: [{ resultId: 1, calleeNumber: '13800000000', calleeName: null, taskId: 1, callStatus: '2', callDuration: 75, hangupCause: null, retryCount: null, callTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(OutboundResult)
    await flush()

    expect(listResult).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    const table = wrapper.findComponent(ProTable)
    expect(table.props('showToolbar')).toBe(false)
    expect(table.props('stripe')).toBe(false)
    expect(wrapper.find('.el-form .el-select').exists()).toBe(true)

    expect(cellTextOf(wrapper, '被叫姓名')).toBe('-')
    expect(cellTextOf(wrapper, '呼叫状态')).toBe('已接通')
    expect(cellTextOf(wrapper, '通话时长')).toBe('1分15秒')
    expect(cellTextOf(wrapper, '挂断原因')).toBe('-')
    expect(cellTextOf(wrapper, '重试次数')).toBe('0')
  })
})

describe('sms 域列表页 ProTable 迁移', () => {
  it('短信通道：初始化查询并渲染供应商标签，重置清空筛选', async () => {
    listSmsConfig.mockResolvedValue({
      rows: [{ configId: 1, configName: '主通道', provider: 'aliyun', signName: 'AI律师', dailyLimit: 1000, status: '1', createTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(SmsConfig)
    await flush()

    expect(listSmsConfig).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, configName: undefined, provider: undefined, status: undefined })
    expect(cellTextOf(wrapper, '供应商')).toBe('阿里云')
    expect(wrapper.findAll('.el-table__body .el-switch').length).toBe(1)

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, configName: undefined, provider: undefined, status: undefined })
    expect(listSmsConfig).toHaveBeenCalledTimes(2)
  })

  it('短信记录：模板下拉复用启用模板选项，发送状态标签正常', async () => {
    listSmsLog.mockResolvedValue({
      rows: [{ logId: 1, phone: '13800000000', templateName: '排队通知', content: '您前面还有2位', sendStatus: '1', providerMsgId: 'MSG1', failReason: null, createTime: '2026-11-01 10:00' }],
      total: 1
    })
    listEnabledTemplates.mockResolvedValue({ data: [{ templateId: 1, templateName: '排队通知' }] })
    const wrapper = mountPage(SmsLog)
    await flush()

    expect(listSmsLog).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    const templateItem = wrapper.findComponent(ProTable).props('queryFormItems').find(i => i.prop === 'templateId')
    expect(templateItem.options).toEqual([{ label: '排队通知', value: 1 }])
    expect(cellTextOf(wrapper, '状态')).toBe('成功')
    expect(cellTextOf(wrapper, '回执ID')).toBe('MSG1')
  })

  it('短信模板：场景标签与启用开关渲染，重置清空筛选', async () => {
    listSmsTemplate.mockResolvedValue({
      rows: [{ templateId: 1, templateName: '排队通知', content: '您前面还有2位', sceneType: 'queue', configName: '主通道', status: '1' }],
      total: 1
    })
    listEnabledConfigs.mockResolvedValue({ data: [{ configId: 1, configName: '主通道' }] })
    const wrapper = mountPage(SmsTemplate)
    await flush()

    expect(listSmsTemplate).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, templateName: undefined, sceneType: undefined, status: undefined })
    expect(cellTextOf(wrapper, '场景')).toBe('排队通知')
    expect(wrapper.findAll('.el-table__body .el-switch').length).toBe(1)

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.sceneType).toBeUndefined()
  })
})
