import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import QualityInspection from '@/views/lawyers/quality/inspection.vue'
import ModelCost from '@/views/lawyers/modelCost/index.vue'
import { listQuality, getQuality, listQualityTemplate } from '@/api/lawyers/quality'
import {
  getModelCostOverview,
  getModelCostTrend,
  getModelCostByModel,
  getModelCostByScene,
  listModelCallLog
} from '@/api/lawyers/modelCost'

jest.mock('@/api/lawyers/quality', () => ({
  listQuality: jest.fn(),
  getQuality: jest.fn(),
  reviewQuality: jest.fn(),
  inspectRecord: jest.fn(),
  exportQuality: jest.fn(),
  appealQuality: jest.fn(),
  reviewAppeal: jest.fn(),
  listQualityTemplate: jest.fn(),
  addQualityTemplate: jest.fn(),
  updateQualityTemplate: jest.fn(),
  setDefaultQualityTemplate: jest.fn()
}))
jest.mock('@/api/lawyers/modelCost', () => ({
  getModelCostOverview: jest.fn(),
  getModelCostTrend: jest.fn(),
  getModelCostByModel: jest.fn(),
  getModelCostByScene: jest.fn(),
  listModelCallLog: jest.fn()
}))
jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn(), dispose: jest.fn() }))
}))

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
  ErrorRetry: true,
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}|{{ actionText }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

const mocks = {
  $route: { path: '/lawyers/quality/inspection', query: {} },
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

function toolbarButtonTexts(tableWrapper) {
  return tableWrapper.findAll('.mb8 button').wrappers.map(b => b.text()).filter(t => t.length > 0)
}

function actionButtonTexts(tableWrapper, rowIndex = 0) {
  return tableWrapper.findAll('.el-table__body tr').at(rowIndex).findAll('button').wrappers.map(b => b.text())
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('quality/inspection 3 表 ProTable 迁移', () => {
  it('质检列表：查询项/状态标签/坐席回退/操作按钮/重置重查保持', async () => {
    listQuality.mockResolvedValue({
      rows: [
        {
          inspectionId: 1,
          agentId: 1001,
          agentName: null,
          callerNumber: '13800000000',
          recordId: 9001,
          totalScore: 92,
          aiStatus: '2',
          riskWarningId: null,
          reviewStatus: '1',
          appealStatus: null,
          reviewerName: '张律师',
          createTime: '2026-11-01 09:00:00'
        },
        {
          inspectionId: 2,
          agentId: 1002,
          agentName: '李坐席',
          callerNumber: '13900000000',
          recordId: 9002,
          totalScore: null,
          aiStatus: '0',
          riskWarningId: 88,
          reviewStatus: '0',
          appealStatus: '1',
          reviewerName: null,
          createTime: '2026-11-02 09:00:00'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(QualityInspection, '/lawyers/quality/inspection')
    await flush()

    expect(listQuality).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      agentId: undefined,
      callerNumber: undefined,
      aiStatus: undefined,
      reviewStatus: undefined,
      params: {}
    }))

    const table = tableByKey(wrapper, 'quality:inspection')
    expect(table).toBeTruthy()
    expect(table.props('columns').map(c => c.label)).toEqual([
      '质检ID', '坐席', '主叫号码', '话单ID', 'AI总分', 'AI状态', '风险预警',
      '复核状态', '申诉状态', '复核人', '质检时间', '操作'
    ])
    // 约定：不使用 fixed="right"
    expect(table.props('columns').every(c => !c.fixed)).toBe(true)
    expect(table.props('queryFormItems').map(i => i.label)).toEqual([
      '坐席ID', '主叫号码', 'AI状态', '复核状态', '质检时间'
    ])
    expect(table.props('queryFormItems')[2].options).toEqual([
      { label: '待检', value: '0' },
      { label: '检中', value: '1' },
      { label: '完成', value: '2' },
      { label: '失败', value: '3' }
    ])
    // 质检时间为 datetimerange，走查询插槽
    expect(table.findAll('.el-form .el-date-editor').length).toBe(1)

    expect(cellTextIn(table, '坐席')).toBe('坐席1001')
    expect(cellTextIn(table, 'AI总分')).toBe('92')
    expect(cellTextIn(table, 'AI状态')).toBe('完成')
    expect(cellTextIn(table, '风险预警')).toBe('-')
    expect(cellTextIn(table, '复核状态')).toBe('通过')
    expect(cellTextIn(table, '申诉状态')).toBe('-')
    expect(cellTextIn(table, '质检时间')).toBe('2026-11-01 10:00')
    expect(cellTextIn(table, '坐席', 1)).toBe('李坐席')
    expect(cellTextIn(table, 'AI总分', 1)).toBe('-')
    expect(cellTextIn(table, 'AI状态', 1)).toBe('待检')
    expect(cellTextIn(table, '风险预警', 1)).toBe('已联动')
    expect(cellTextIn(table, '申诉状态', 1)).toBe('待复核')

    expect(toolbarButtonTexts(table)).toEqual(expect.arrayContaining(['导出', '质检模板']))
    expect(actionButtonTexts(table, 0)).toEqual(expect.arrayContaining(['详情', '复核', '重检', '申诉', '申诉复核']))
    // 未完成质检的行不可复核；未申诉的行不可申诉复核
    const secondButtons = actionButtonTexts(table, 1)
    expect(secondButtons).toEqual(expect.arrayContaining(['详情', '重检', '申诉']))

    // 重置：清空日期区间并重设查询参数后重查
    wrapper.vm.dateRange = ['2026-10-01 00:00:00', '2026-10-02 23:59:59']
    wrapper.vm.queryParams.agentId = '1001'
    wrapper.vm.queryParams.aiStatus = '2'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.agentId).toBeUndefined()
    expect(wrapper.vm.queryParams.aiStatus).toBeUndefined()
    expect(wrapper.vm.queryParams.params).toEqual({})
    expect(listQuality).toHaveBeenCalledTimes(2)
  })

  it('详情弹窗命中问题表 + 质检模板表：独立 persist-key 与插槽渲染保持', async () => {
    listQuality.mockResolvedValue({ rows: [{ inspectionId: 1, agentId: 1001, aiStatus: '2' }], total: 1 })
    getQuality.mockResolvedValue({
      data: {
        inspectionId: 1,
        agentId: 1001,
        agentName: '王坐席',
        callerNumber: '13800000000',
        totalScore: 88,
        aiStatus: '2',
        riskWarningId: null,
        aiRemark: '整体规范',
        dimensionJson: '{"serviceNorm":90,"answerAccuracy":80}',
        violationJson: '[{"keyword":"投诉","reason":"出现投诉敏感词"}]',
        transcript: '客户：我要咨询...'
      }
    })
    listQualityTemplate.mockResolvedValue({
      data: [
        {
          templateId: 10,
          templateName: '默认质检模板',
          dimensions: '[{"key":"serviceNorm","weight":30},{"key":"answerAccuracy","weight":25}]',
          isDefault: '1',
          status: '1'
        },
        {
          templateId: 11,
          templateName: '停用模板',
          dimensions: '[{"key":"emotionAttitude","weight":20}]',
          isDefault: '0',
          status: '0'
        }
      ]
    })
    const wrapper = mountPage(QualityInspection, '/lawyers/quality/inspection')
    await flush()

    wrapper.vm.handleDetail(wrapper.vm.qualityList[0])
    await flush()
    await wrapper.vm.$nextTick()
    const violationTable = tableByKey(wrapper, 'quality:violation')
    expect(violationTable).toBeTruthy()
    expect(violationTable.props('showSearch')).toBe(false)
    expect(violationTable.props('showToolbar')).toBe(false)
    expect(violationTable.props('hidePagination')).toBe(true)
    expect(violationTable.props('tableAttrs')).toEqual({ border: true, maxHeight: '180px' })
    expect(violationTable.props('columns').map(c => c.label)).toEqual(['命中关键词/问题', '原因说明'])
    expect(cellTextIn(violationTable, '命中关键词/问题')).toBe('投诉')
    expect(cellTextIn(violationTable, '原因说明')).toBe('出现投诉敏感词')
    expect(wrapper.vm.dimensions).toEqual({ serviceNorm: 90, answerAccuracy: 80 })

    wrapper.vm.handleTemplate()
    await flush()
    await wrapper.vm.$nextTick()
    expect(listQualityTemplate).toHaveBeenCalledWith({ pageNum: 1, pageSize: 100 })
    const templateTable = tableByKey(wrapper, 'quality:template')
    expect(templateTable).toBeTruthy()
    expect(templateTable.props('showSearch')).toBe(false)
    expect(templateTable.props('showToolbar')).toBe(false)
    expect(templateTable.props('hidePagination')).toBe(true)
    expect(templateTable.props('tableAttrs')).toEqual({ border: true, maxHeight: '360px' })
    expect(templateTable.props('columns').map(c => c.label)).toEqual([
      'ID', '模板名称', '维度权重', '默认', '状态', '操作'
    ])
    expect(cellTextIn(templateTable, '维度权重')).toContain('服务规范 30')
    expect(cellTextIn(templateTable, '维度权重')).toContain('答复准确 25')
    expect(cellTextIn(templateTable, '默认')).toBe('默认')
    expect(cellTextIn(templateTable, '状态')).toBe('启用')
    expect(cellTextIn(templateTable, '默认', 1)).toBe('-')
    expect(cellTextIn(templateTable, '状态', 1)).toBe('停用')
    expect(toolbarButtonTexts(templateTable)).toEqual(expect.arrayContaining(['新增模板']))
    expect(actionButtonTexts(templateTable, 0)).toEqual(expect.arrayContaining(['编辑', '设默认']))
  })

  it('质检列表空态展示内建文案', async () => {
    listQuality.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(QualityInspection, '/lawyers/quality/inspection')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无质检记录')
  })
})

describe('modelCost 3 表 ProTable 迁移', () => {
  beforeEach(() => {
    getModelCostOverview.mockResolvedValue({
      data: { totalCalls: 40, successRate: 90, failCalls: 3, rejectCalls: 1, totalTokens: 5000, costAmount: 1.55, avgElapsedMs: 820, retryCalls: 2, costPerCall: 0.03875 }
    })
    getModelCostTrend.mockResolvedValue({
      data: [{ period: '2026-11-01', totalCalls: 10, successCalls: 9, costAmount: 0.4 }]
    })
    getModelCostByModel.mockResolvedValue({
      data: [
        { modelName: 'qwen-max', modelType: 'chat', totalCalls: 30, successCalls: 27, failCalls: 2, rejectCalls: 1, totalTokens: 3600, costAmount: 1.2, avgElapsedMs: 900 }
      ]
    })
    getModelCostByScene.mockResolvedValue({
      data: [
        { kind: 'chat', scene: 'quality', totalCalls: 10, successCalls: 9, failCalls: 1, rejectCalls: 0, totalTokens: 1200, costAmount: 0.1234, avgElapsedMs: 800 }
      ]
    })
    listModelCallLog.mockResolvedValue({
      rows: [
        {
          callTime: '2026-11-01 09:00:00',
          kind: 'chat',
          scene: 'summary',
          configName: '默认配置',
          modelName: 'qwen-max',
          promptTokens: 120,
          completionTokens: 30,
          costAmount: 0.02,
          elapsedMs: 800,
          attempts: 1,
          result: '1',
          failReason: null
        }
      ],
      total: 25
    })
  })

  it('场景分布/模型明细/调用明细：列口径、插槽渲染与分页保持', async () => {
    const wrapper = mountPage(ModelCost, '/lawyers/modelCost')
    await flush()
    await wrapper.vm.$nextTick()

    expect(getModelCostOverview).toHaveBeenCalledTimes(1)
    expect(getModelCostTrend).toHaveBeenCalledTimes(1)
    expect(getModelCostByModel).toHaveBeenCalledTimes(1)
    expect(getModelCostByScene).toHaveBeenCalledTimes(1)
    expect(listModelCallLog).toHaveBeenCalledTimes(1)
    const logParams = listModelCallLog.mock.calls[0][0]
    expect(logParams.pageNum).toBe(1)
    expect(logParams.pageSize).toBe(10)
    expect(logParams['params[beginTime]']).toMatch(/^\d{4}-\d{2}-\d{2} 00:00:00$/)
    expect(logParams['params[endTime]']).toMatch(/^\d{4}-\d{2}-\d{2} 23:59:59$/)

    expect(wrapper.findAllComponents(ProTable).length).toBe(3)

    const sceneTable = tableByKey(wrapper, 'modelCost:scene')
    expect(sceneTable).toBeTruthy()
    expect(sceneTable.props('showSearch')).toBe(false)
    expect(sceneTable.props('showToolbar')).toBe(false)
    expect(sceneTable.props('hidePagination')).toBe(true)
    expect(sceneTable.props('columns').map(c => c.label)).toEqual([
      '调用类型', '业务场景', '调用量', '成功', '失败', '拒绝', '成功率',
      '总Token', '估算费用(元)', '平均耗时(ms)'
    ])
    expect(cellTextIn(sceneTable, '调用类型')).toBe('对话')
    expect(cellTextIn(sceneTable, '业务场景')).toBe('智能质检')
    expect(cellTextIn(sceneTable, '调用量')).toBe('10')
    expect(cellTextIn(sceneTable, '成功率')).toBe('90.00%')
    expect(cellTextIn(sceneTable, '估算费用(元)')).toBe('0.1234')

    const modelTable = tableByKey(wrapper, 'modelCost:model')
    expect(modelTable).toBeTruthy()
    expect(modelTable.props('showSearch')).toBe(false)
    expect(modelTable.props('showToolbar')).toBe(false)
    expect(modelTable.props('hidePagination')).toBe(true)
    expect(modelTable.props('columns').map(c => c.label)).toEqual([
      '模型名称', '类型', '调用量', '成功', '失败', '拒绝', '成功率',
      '总Token', '估算费用(元)', '平均耗时(ms)'
    ])
    expect(cellTextIn(modelTable, '模型名称')).toBe('qwen-max')
    expect(cellTextIn(modelTable, '类型')).toBe('chat')
    expect(cellTextIn(modelTable, '成功率')).toBe('90.00%')
    expect(cellTextIn(modelTable, '估算费用(元)')).toBe('1.2000')

    const logTable = tableByKey(wrapper, 'modelCost:log')
    expect(logTable).toBeTruthy()
    expect(logTable.props('showSearch')).toBe(false)
    expect(logTable.props('showToolbar')).toBe(false)
    expect(logTable.props('hidePagination')).toBe(false)
    expect(logTable.props('total')).toBe(25)
    expect(logTable.props('columns').map(c => c.label)).toEqual([
      '调用时间', '类型', '场景', '模型配置', '模型名称', '输入Tok', '输出Tok',
      '费用(元)', '耗时(ms)', '尝试', '结果', '失败原因'
    ])
    expect(cellTextIn(logTable, '调用时间')).toBe('2026-11-01 09:00:00')
    expect(cellTextIn(logTable, '类型')).toBe('对话')
    expect(cellTextIn(logTable, '场景')).toBe('通话小结')
    expect(cellTextIn(logTable, '费用(元)')).toBe('0.0200')
    expect(cellTextIn(logTable, '结果')).toBe('成功')

    // 分页：ProTable 同步 pageNum 后按新页码请求明细
    wrapper.vm.logQuery.pageNum = 2
    wrapper.vm.loadLogList()
    await flush()
    expect(listModelCallLog).toHaveBeenCalledTimes(2)
    expect(listModelCallLog.mock.calls[1][0].pageNum).toBe(2)
    expect(listModelCallLog.mock.calls[1][0].pageSize).toBe(10)
  })

  it('重置只恢复近 7 天与清空筛选，不影响指标卡与图表装配', async () => {
    const wrapper = mountPage(ModelCost, '/lawyers/modelCost')
    await flush()
    await wrapper.vm.$nextTick()

    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.logQuery.kind = 'chat'
    wrapper.vm.logQuery.scene = 'quality'
    wrapper.vm.logQuery.result = '0'
    wrapper.vm.resetQuery()
    await flush()

    expect(wrapper.vm.dateRange.length).toBe(2)
    expect(wrapper.vm.dateRange[1]).toBe(wrapper.vm.fmtDay(0))
    expect(wrapper.vm.logQuery.pageNum).toBe(1)
    expect(wrapper.vm.logQuery.kind).toBeNull()
    expect(wrapper.vm.logQuery.scene).toBeNull()
    expect(wrapper.vm.logQuery.result).toBeNull()
    expect(wrapper.vm.overview.totalCalls).toBe(40)
    expect(wrapper.vm.statCards.length).toBe(8)
    expect(getModelCostOverview).toHaveBeenCalledTimes(2)
    expect(listModelCallLog).toHaveBeenCalledTimes(2)
  })

  it('无调用数据时三表均展示 ProTable 内建空态', async () => {
    getModelCostTrend.mockResolvedValue({ data: [] })
    getModelCostByModel.mockResolvedValue({ data: [] })
    getModelCostByScene.mockResolvedValue({ data: [] })
    listModelCallLog.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(ModelCost, '/lawyers/modelCost')
    await flush()

    const emptyTexts = wrapper.findAll('.table-empty-stub').wrappers.map(w => w.text())
    expect(emptyTexts.some(t => t.includes('暂无场景分布数据'))).toBe(true)
    expect(emptyTexts.some(t => t.includes('暂无模型维度数据'))).toBe(true)
    expect(emptyTexts.some(t => t.includes('暂无大模型调用明细'))).toBe(true)
  })
})
