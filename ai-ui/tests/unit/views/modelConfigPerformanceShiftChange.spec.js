import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import ModelConfig from '@/views/lawyers/modelConfig/index.vue'
import AgentPerformance from '@/views/lawyers/performance/agent.vue'
import ShiftChange from '@/views/lawyers/schedule/shiftChange.vue'
import { listModelConfig } from '@/api/lawyers/modelConfig'
import { listAgentPerformance } from '@/api/lawyers/stat'
import { listAgent } from '@/api/lawyers/callCenter'
import { listShiftChange, listMyShiftChange } from '@/api/lawyers/shiftChange'

jest.mock('@/api/lawyers/modelConfig', () => ({
  listModelConfig: jest.fn(),
  getModelConfig: jest.fn(),
  delModelConfig: jest.fn(),
  addModelConfig: jest.fn(),
  updateModelConfig: jest.fn(),
  setDefaultModelConfig: jest.fn(),
  testModelConnection: jest.fn()
}))
jest.mock('@/api/lawyers/stat', () => ({
  listAgentPerformance: jest.fn()
}))
jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn()
}))
jest.mock('@/api/lawyers/shiftChange', () => ({
  listShiftChange: jest.fn(),
  listMyShiftChange: jest.fn(),
  listMySchedules: jest.fn(() => Promise.resolve({ data: [] })),
  listSwapTargets: jest.fn(() => Promise.resolve({ data: [] })),
  applyShiftChange: jest.fn(),
  approveShiftChange: jest.fn(),
  rejectShiftChange: jest.fn(),
  cancelShiftChange: jest.fn()
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
  },
  'dict-tag': {
    template: '<span class="dict-tag-stub">{{ value }}</span>',
    props: ['options', 'value']
  },
  'pro-dialog': { template: '<div class="pro-dialog-stub"><slot /></div>', props: ['value', 'title', 'width', 'confirmLoading'] },
  'pro-form': true
}

const mocks = {
  $route: { path: '/lawyers/modelConfig', query: {} },
  $store: { state: { user: { name: 'tester', token: 'token' } } },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    msgLoading: jest.fn(),
    closeLoading: jest.fn(),
    undo: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
  dict: {
    type: {
      ai_model_type: [{ label: '大模型', value: '1' }, { label: '向量模型', value: '2' }],
      sys_normal_disable: [{ label: '正常', value: '0' }, { label: '停用', value: '1' }],
      sys_yes_no: [{ label: '是', value: 'Y' }, { label: '否', value: 'N' }]
    }
  }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component, overrides = {}) {
  return mount(component, { localVue, stubs, mocks: { ...mocks, ...overrides } })
}

function cellTextOf(wrapper, label) {
  const index = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(0).findAll('td').at(index).text()
}

function toolbarButtonTexts(wrapper) {
  return wrapper.findAll('.pro-table .mb8 button').wrappers.map(b => b.text()).filter(t => t.length > 0)
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('modelConfig 域列表页 ProTable 迁移', () => {
  it('AI模型配置：字典查询项/单价空值/默认标识与操作按钮保持', async () => {
    listModelConfig.mockResolvedValue({
      rows: [
        {
          configId: 1,
          modelType: '1',
          modelName: '通义千问',
          apiUrl: 'https://dashscope.aliyuncs.com',
          apiKey: 'sk-xxx',
          maxTokens: 2048,
          inputPrice: 0.002,
          outputPrice: null,
          temperature: 0.7,
          isDefault: 'N',
          status: '0'
        },
        {
          configId: 2,
          modelType: '2',
          modelName: '向量模型',
          apiUrl: 'https://example.com',
          apiKey: 'sk-yyy',
          maxTokens: 1024,
          inputPrice: null,
          outputPrice: 0.001,
          temperature: 0.3,
          isDefault: 'Y',
          status: '1'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(ModelConfig)
    await flush()

    expect(listModelConfig).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      modelType: null,
      status: null
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无AI模型配置')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '配置ID', '模型类型', '模型名称', 'API地址', 'API密钥', '最大Token数',
      '输入单价(元/千Token)', '输出单价(元/千Token)', '温度', '默认配置', '状态', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['模型类型', '状态'])
    expect(table.props('queryFormItems')[0].options).toEqual([{ label: '大模型', value: '1' }, { label: '向量模型', value: '2' }])
    expect(table.props('queryFormItems')[1].options).toEqual([{ label: '正常', value: '0' }, { label: '停用', value: '1' }])

    expect(cellTextOf(wrapper, '配置ID')).toBe('1')
    expect(cellTextOf(wrapper, '模型类型')).toBe('1')
    expect(cellTextOf(wrapper, '模型名称')).toBe('通义千问')
    expect(cellTextOf(wrapper, '输入单价')).toBe('0.002')
    expect(cellTextOf(wrapper, '输出单价')).toBe('—')
    expect(cellTextOf(wrapper, '默认配置')).toBe('N')
    expect(cellTextOf(wrapper, '状态')).toBe('0')

    // 仅非默认行出现「设为默认」
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toEqual(expect.arrayContaining(['修改', '删除', '测试连接', '设为默认']))
    expect(secondButtons).not.toContain('设为默认')
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['新增', '修改', '删除']))

    wrapper.vm.handleSelectionChange([{ configId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)
    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.pageSize).toBe(10)
    expect(wrapper.vm.queryParams.modelType).toBeUndefined()
    expect(wrapper.vm.queryParams.status).toBeUndefined()
    expect(listModelConfig).toHaveBeenCalledTimes(2)
  })

  it('AI模型配置：空态展示内建文案', async () => {
    listModelConfig.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(ModelConfig)
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无AI模型配置')
  })
})

describe('performance.agent 列表页 ProTable 迁移', () => {
  it('坐席效能：坐席下拉/时间插槽/状态标签与满意度空值口径保持', async () => {
    listAgent.mockResolvedValue({ rows: [{ agentId: 1, agentName: '张律师' }] })
    listAgentPerformance.mockResolvedValue({
      rows: [
        {
          agentName: '张律师',
          status: '1',
          callStatus: '1',
          loginTime: '2026-11-01 09:00',
          signedInMinutes: 120,
          inboundCalls: 10,
          outboundCalls: 5,
          transferredOut: 1,
          talkDuration: 600,
          avgTalkDuration: 60,
          aiAssistCount: 3,
          avgSatisfaction: 4.5
        },
        {
          agentName: '李律师',
          status: '0',
          callStatus: '0',
          loginTime: '2026-11-01 10:00',
          signedInMinutes: 0,
          inboundCalls: 0,
          outboundCalls: 0,
          transferredOut: 0,
          talkDuration: 0,
          avgTalkDuration: 0,
          aiAssistCount: 0,
          avgSatisfaction: 0
        }
      ],
      total: 2
    })
    const wrapper = mountPage(AgentPerformance, { $route: { path: '/lawyers/performance/agent', query: {} } })
    await flush()

    expect(listAgent).toHaveBeenCalledWith({ pageSize: 999, status: '1' })
    expect(listAgentPerformance).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      agentId: undefined,
      beginTime: undefined,
      endTime: undefined
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('emptyText')).toBe('暂无坐席效能数据')
    expect(table.props('tableAttrs')).toEqual({ border: true })
    expect(table.props('columns').map(c => c.label)).toEqual([
      '坐席名称', '状态', '通话状态', '签入时间', '签入时长(分)', '呼入接听', '呼出',
      '转接', '通话时长(秒)', '均通话(秒)', 'AI协访', '满意度均值'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['坐席名称', '开始时间', '结束时间'])
    expect(table.props('queryFormItems')[0].options).toEqual([{ label: '张律师(1)', value: 1 }])

    expect(cellTextOf(wrapper, '坐席名称')).toBe('张律师')
    expect(cellTextOf(wrapper, '状态')).toBe('在线')
    expect(cellTextOf(wrapper, '通话状态')).toBe('通话中')
    expect(cellTextOf(wrapper, '签入时间')).toBe('2026-11-01 09:00')
    expect(cellTextOf(wrapper, '呼入接听')).toBe('10')
    expect(cellTextOf(wrapper, '满意度均值')).toBe('4.5')

    // 无满意度数据展示 '-'
    expect(cellTextOf(wrapper, '状态')).toBe('在线')
    const secondRow = wrapper.findAll('.el-table__body tr').at(1).findAll('td')
    const satisfactionIndex = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes('满意度均值'))
    expect(secondRow.at(satisfactionIndex).text()).toBe('-')

    // 开始/结束时间为独立 datetime 选择器，走查询插槽
    expect(wrapper.findAll('.el-form .el-date-editor').length).toBe(2)
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['导出']))

    wrapper.vm.queryParams.agentId = 1
    wrapper.vm.queryParams.beginTime = '2026-10-01 00:00:00'
    wrapper.vm.queryParams.endTime = '2026-10-02 00:00:00'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.agentId).toBeUndefined()
    expect(wrapper.vm.queryParams.beginTime).toBeUndefined()
    expect(wrapper.vm.queryParams.endTime).toBeUndefined()
    expect(listAgentPerformance).toHaveBeenCalledTimes(2)
  })
})

describe('schedule.shiftChange 列表页 ProTable 迁移', () => {
  const requestRow = overrides => Object.assign({
    requestId: 9,
    applicantAgentName: '张律师',
    applicantScheduleDate: '2026-11-05',
    applicantShiftName: '早班',
    changeType: 'SWAP',
    targetAgentName: '李律师',
    targetScheduleDate: '2026-11-05',
    targetShiftName: '晚班',
    reason: '临时调休',
    status: '0',
    createTime: '2026-11-01 09:00:00'
  }, overrides)

  it('换班申请：视角/状态/日期插槽、待审批操作与审批信息口径保持', async () => {
    listShiftChange.mockResolvedValue({ rows: [requestRow(), requestRow({ requestId: 10, status: '1' })], total: 2 })
    listMyShiftChange.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(ShiftChange, { $route: { path: '/lawyers/schedule/shiftChange', query: {} } })
    await flush()

    expect(listShiftChange).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      status: undefined,
      changeType: undefined
    }))
    expect(listShiftChange.mock.calls[0][0].params).toEqual({ beginTime: undefined, endTime: undefined })

    const table = wrapper.findComponent(ProTable)
    expect(table.props('showToolbar')).toBe(false)
    expect(table.props('emptyText')).toBe('暂无换班申请')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '申请ID', '申请人 / 原班次', '类型', '接收坐席 / 目标班次', '申请原因', '状态', '审批信息', '申请时间', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['审批视角', '状态', '类型', '申请时间'])
    expect(table.props('queryFormItems')[1].options).toHaveLength(4)
    expect(table.props('queryFormItems')[2].options).toEqual([
      { label: '互换换班', value: 'SWAP' },
      { label: '转班', value: 'TRANSFER' }
    ])

    expect(cellTextOf(wrapper, '申请ID')).toBe('9')
    expect(cellTextOf(wrapper, '申请人')).toContain('张律师')
    expect(cellTextOf(wrapper, '申请人')).toContain('早班')
    expect(cellTextOf(wrapper, '类型')).toBe('互换换班')
    expect(cellTextOf(wrapper, '接收坐席')).toContain('李律师')
    expect(cellTextOf(wrapper, '申请原因')).toBe('临时调休')
    expect(cellTextOf(wrapper, '状态')).toBe('待审批')
    expect(cellTextOf(wrapper, '审批信息')).toContain('待审批')
    expect(cellTextOf(wrapper, '申请时间')).toBe('2026-11-01 09:00:00')

    // 仅待审批行展示通过/驳回/撤销
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toEqual(expect.arrayContaining(['通过', '驳回', '撤销']))
    expect(secondButtons).not.toContain('通过')
    expect(wrapper.findAll('.el-table__body .sc-sub').wrappers.map(i => i.text()).join('|')).toContain('已处理')

    // 查询区保留审批视角按钮组与申请入口
    expect(wrapper.findAll('.el-form .el-radio-button').length).toBe(2)
    expect(wrapper.findAll('.el-form button').wrappers.map(b => b.text())).toEqual(expect.arrayContaining(['申请换班']))
    wrapper.vm.openApply()
    expect(wrapper.vm.applyOpen).toBe(true)

    // 重置清空筛选与日期区间，但审批视角沿用原行为不复位
    wrapper.vm.scope = 'mine'
    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.queryParams.status = '0'
    wrapper.vm.queryParams.changeType = 'SWAP'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.scope).toBe('mine')
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.status).toBeUndefined()
    expect(wrapper.vm.queryParams.changeType).toBeUndefined()
    expect(listMyShiftChange).toHaveBeenCalledTimes(1)
  })

  it('换班申请：空态展示内建文案', async () => {
    listShiftChange.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(ShiftChange, { $route: { path: '/lawyers/schedule/shiftChange', query: {} } })
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无换班申请')
  })
})
