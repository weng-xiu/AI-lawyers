import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import IvrExecutionLog from '@/views/lawyers/ivr/executionLog.vue'
import IvrFlow from '@/views/lawyers/ivr/flow/index.vue'
import IvrIntention from '@/views/lawyers/ivr/intention/index.vue'
import Knowledge from '@/views/lawyers/knowledge/index.vue'
import { listExecutionLog } from '@/api/lawyers/ivrExecutionLog'
import { listFlow } from '@/api/lawyers/ivrFlow'
import { listIntention } from '@/api/lawyers/ivrIntention'
import { listKnowledge } from '@/api/lawyers/knowledge'
import { getValidCategories } from '@/api/lawyers/category'

jest.mock('@/api/lawyers/ivrExecutionLog', () => ({
  listExecutionLog: jest.fn(),
  getExecutionLog: jest.fn()
}))
jest.mock('@/api/lawyers/ivrFlow', () => ({
  listFlow: jest.fn(),
  getFlow: jest.fn(),
  delFlow: jest.fn(),
  addFlow: jest.fn(),
  updateFlow: jest.fn(),
  publishFlow: jest.fn(),
  getPublishedFlows: jest.fn(() => Promise.resolve({ data: [] }))
}))
jest.mock('@/api/lawyers/ivrEngine', () => ({ executeFlow: jest.fn() }))
jest.mock('@/api/lawyers/ivrIntention', () => ({
  listIntention: jest.fn(),
  getIntention: jest.fn(),
  delIntention: jest.fn(),
  addIntention: jest.fn(),
  updateIntention: jest.fn()
}))
jest.mock('@/api/lawyers/knowledge', () => ({
  listKnowledge: jest.fn(),
  getKnowledge: jest.fn(),
  delKnowledge: jest.fn(),
  addKnowledge: jest.fn(),
  updateKnowledge: jest.fn(),
  exportKnowledge: jest.fn(),
  importTemplate: jest.fn(),
  importKnowledge: jest.fn(),
  auditKnowledge: jest.fn(),
  getRagIndexInfo: jest.fn(() => Promise.resolve({ data: {} })),
  rebuildRagIndex: jest.fn(),
  testRagRerank: jest.fn(),
  draftFromCall: jest.fn(),
  draftFromTicket: jest.fn(),
  scanDraft: jest.fn(),
  getKnowledgeOpsOverview: jest.fn(),
  listUnmatchedQuestions: jest.fn(),
  listTopKnowledge: jest.fn(),
  draftFromQuestion: jest.fn()
}))
jest.mock('@/api/lawyers/category', () => ({
  getValidCategories: jest.fn(() => Promise.resolve({ data: [] }))
}))
jest.mock('@/utils/draft', () => ({
  createDraftMixin: () => ({
    methods: {
      draftRestorePrompt: jest.fn(),
      lockSubmit: jest.fn(),
      unlockSubmit: jest.fn(),
      draftClear: jest.fn()
    }
  })
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
    template: '<span class="dict-tag-stub">状态{{ value }}</span>',
    props: ['options', 'value']
  },
  'knowledge-ops': { template: '<div class="knowledge-ops-stub" />' }
}

const mocks = routeQuery => ({
  $route: { path: '/lawyers/knowledge', query: routeQuery || {} },
  $store: { state: { user: { name: 'tester', token: 'token' } } },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    undo: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
  dict: { type: { lawyers_audit_status: [] } },
  draftSubmitting: false
})

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component, routeQuery) {
  return mount(component, { localVue, stubs, mocks: mocks(routeQuery) })
}

function cellTextOf(wrapper, label) {
  const index = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(0).findAll('td').at(index).text()
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('ivr 域列表页 ProTable 迁移', () => {
  it('IVR 执行日志：初始化分页查询，节点类型/结果标签与空值口径保持', async () => {
    listExecutionLog.mockResolvedValue({
      rows: [{ execId: 1, sessionId: 'sess-1', nodeType: 'menu', nodeName: null, execAction: null, execResult: '0', costMs: null, createTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(IvrExecutionLog)
    await flush()

    expect(listExecutionLog).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.findComponent(ProTable).props('columns').map(c => c.label)).toEqual([
      '日志ID', '会话ID', '节点类型', '节点名称', '执行动作', '执行结果', '耗时(ms)', '执行时间', '操作'
    ])
    expect(cellTextOf(wrapper, '节点类型')).toBe('菜单')
    expect(cellTextOf(wrapper, '节点名称')).toBe('-')
    expect(cellTextOf(wrapper, '执行动作')).toBe('-')
    expect(cellTextOf(wrapper, '执行结果')).toBe('失败')
    expect(cellTextOf(wrapper, '耗时(ms)')).toBe('-')
    expect(cellTextOf(wrapper, '执行时间')).toBe('2026-11-01 10:00')

    // 日期区间仍由独立 dateRange 承载，走查询插槽渲染
    expect(wrapper.find('.el-form .el-date-editor').exists()).toBe(true)
    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    // addDateRange 会把 params 挂到查询对象上，这里按字段校验筛选已复位
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.sessionId).toBeUndefined()
    expect(wrapper.vm.queryParams.nodeType).toBeUndefined()
    expect(wrapper.vm.queryParams.execResult).toBeUndefined()
    expect(listExecutionLog).toHaveBeenCalledTimes(2)
  })

  it('IVR 流程：状态/默认标签渲染，重置清空筛选并重查', async () => {
    listFlow.mockResolvedValue({
      rows: [
        { flowId: 1, flowName: '主流程', flowCode: 'main', version: 1, status: '0', isDefault: '1', createTime: '2026-11-01 10:00' },
        { flowId: 2, flowName: '夜间流程', flowCode: 'night', version: 2, status: '1', isDefault: '0', createTime: '2026-11-01 10:00' }
      ],
      total: 2
    })
    const wrapper = mountPage(IvrFlow)
    await flush()

    expect(listFlow).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, flowName: undefined, flowCode: undefined, status: undefined })
    expect(cellTextOf(wrapper, '流程名称')).toBe('主流程')
    expect(cellTextOf(wrapper, '状态')).toBe('草稿')
    expect(cellTextOf(wrapper, '是否默认')).toBe('是')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')
    const body = wrapper.find('.el-table__body-wrapper').text()
    expect(body).toContain('已发布')
    expect(body).toContain('否')
    // 发布按钮仅草稿行出现（首行有，次行无）
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toContain('发布')
    expect(secondButtons).not.toContain('发布')

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, flowName: undefined, flowCode: undefined, status: undefined })
    expect(listFlow).toHaveBeenCalledTimes(2)
  })

  it('IVR 意图：启用/停用标签渲染，重置清空筛选并重查', async () => {
    listIntention.mockResolvedValue({
      rows: [{ intentionId: 1, intentionName: '合同纠纷', intentionCode: 'contract', keywords: '合同,违约', priority: 10, status: '1', createTime: '2026-11-01 10:00' }],
      total: 1
    })
    const wrapper = mountPage(IvrIntention)
    await flush()

    expect(listIntention).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, intentionName: undefined, intentionCode: undefined, status: undefined })
    expect(cellTextOf(wrapper, '意图名称')).toBe('合同纠纷')
    expect(cellTextOf(wrapper, '匹配关键词')).toBe('合同,违约')
    expect(cellTextOf(wrapper, '优先级')).toBe('10')
    expect(cellTextOf(wrapper, '状态')).toBe('停用')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, intentionName: undefined, intentionCode: undefined, status: undefined })
    expect(listIntention).toHaveBeenCalledTimes(2)
  })
})

describe('knowledge 域列表页 ProTable 迁移', () => {
  it('知识库：初始化分页查询，分类选项/审核状态/多选与重置口径保持', async () => {
    listKnowledge.mockResolvedValue({
      rows: [
        { knowledgeId: 1, title: '劳动合同解除', categoryName: '劳动纠纷', keywords: '解除,赔偿', auditStatus: '0', viewCount: 12, createTime: '2026-11-01 10:00' },
        { knowledgeId: 2, title: '借款利息', categoryName: '合同纠纷', keywords: '利息', auditStatus: '1', viewCount: 3, createTime: '2026-11-01 10:00' }
      ],
      total: 2
    })
    getValidCategories.mockResolvedValue({ data: [{ categoryId: 1, categoryName: '劳动纠纷' }] })
    const wrapper = mountPage(Knowledge)
    await flush()

    expect(listKnowledge).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, title: undefined, categoryId: undefined, keywords: undefined })
    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyActionText')).toBe('新增知识')
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['知识标题', '知识分类', '关键词'])
    expect(table.props('queryFormItems')[1].options).toEqual([{ label: '劳动纠纷', value: 1 }])
    expect(cellTextOf(wrapper, '知识标题')).toBe('劳动合同解除')
    expect(cellTextOf(wrapper, '审核状态')).toBe('状态0')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')

    // 审核仅待审行展示
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toContain('审核')
    expect(secondButtons).not.toContain('审核')

    // 多选联动：单选可修改、多选可删除
    wrapper.vm.handleSelectionChange([{ knowledgeId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)
    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, title: undefined, categoryId: undefined, keywords: undefined })
    expect(listKnowledge).toHaveBeenCalledTimes(2)
  })

  it('知识库：命令面板带入标题时预填查询条件', async () => {
    listKnowledge.mockResolvedValue({ rows: [], total: 0 })
    getValidCategories.mockResolvedValue({ data: [] })
    const wrapper = mountPage(Knowledge, { title: '劳动合同' })
    await flush()
    expect(listKnowledge).toHaveBeenCalledWith(expect.objectContaining({ title: '劳动合同' }))
    expect(wrapper.vm.queryParams.title).toBe('劳动合同')
  })
})
