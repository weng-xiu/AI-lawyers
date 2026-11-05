import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import KnowledgeAudit from '@/views/lawyers/knowledgeAudit/index.vue'
import AgentConfig from '@/views/lawyers/agent/config.vue'
import SystemPrompt from '@/views/lawyers/systemPrompt/index.vue'
import { listKnowledge } from '@/api/lawyers/knowledge'
import { listAuditLog } from '@/api/lawyers/knowledgeAudit'
import { listAgentConfig } from '@/api/lawyers/agent'
import { listSystemPrompt } from '@/api/lawyers/systemPrompt'

jest.mock('@/api/lawyers/knowledge', () => ({
  listKnowledge: jest.fn(),
  markValidStatus: jest.fn()
}))
jest.mock('@/api/lawyers/knowledgeAudit', () => ({
  listAuditLog: jest.fn(),
  submitAudit: jest.fn(),
  approveKnowledge: jest.fn(),
  rejectKnowledge: jest.fn(),
  publishKnowledge: jest.fn(),
  offlineKnowledge: jest.fn()
}))
jest.mock('@/api/lawyers/agent', () => ({
  listAgentConfig: jest.fn(),
  getAgentConfig: jest.fn(),
  addAgentConfig: jest.fn(),
  updateAgentConfig: jest.fn(),
  delAgentConfig: jest.fn(),
  testAgentConfig: jest.fn()
}))
jest.mock('@/api/lawyers/systemPrompt', () => ({
  listSystemPrompt: jest.fn(),
  getSystemPrompt: jest.fn(),
  delSystemPrompt: jest.fn(),
  addSystemPrompt: jest.fn(),
  updateSystemPrompt: jest.fn(),
  setDefaultSystemPrompt: jest.fn(),
  previewSystemPrompt: jest.fn()
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
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}|{{ actionText }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  },
  'dict-tag': {
    template: '<span class="dict-tag-stub">{{ value }}</span>',
    props: ['options', 'value']
  }
}

const mocks = {
  $route: { path: '/lawyers/knowledgeAudit', query: {} },
  $store: { state: { user: { name: 'tester', token: 'token' } } },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    undo: jest.fn(),
    loading: jest.fn(),
    closeLoading: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() },
  dict: {
    type: {
      ai_prompt_type: [{ label: '通用', value: '1' }, { label: '问答', value: '2' }],
      sys_normal_disable: [{ label: '正常', value: '0' }, { label: '停用', value: '1' }],
      sys_yes_no: [{ label: '是', value: 'Y' }, { label: '否', value: 'N' }]
    }
  }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component) {
  return mount(component, { localVue, stubs, mocks })
}

function proTableByKey(wrapper, key) {
  return wrapper.findAllComponents(ProTable).wrappers.find(t => t.props('persistKey') === key)
}

function cellTextOf(table, label) {
  const index = table.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return table.findAll('.el-table__body tr').at(0).findAll('td').at(index).text()
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('配置类列表页 ProTable 迁移', () => {
  it('知识审核：默认待审核筛选、状态标签与多表 persist-key 隔离', async () => {
    listKnowledge.mockResolvedValue({
      rows: [{
        knowledgeId: 1,
        title: '劳动合同解除指引',
        lawName: null,
        articleNo: null,
        publishVersion: null,
        auditStatus: '0',
        status: '1',
        validStatus: null,
        effectiveDate: null
      }],
      total: 1
    })
    const wrapper = mountPage(KnowledgeAudit)
    await flush()

    expect(listKnowledge).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined })
    const knowledgeTable = proTableByKey(wrapper, 'knowledgeAudit:knowledge')
    expect(knowledgeTable.props('persistKey')).toBe('knowledgeAudit:knowledge')
    expect(proTableByKey(wrapper, 'knowledgeAudit:log').props('persistKey')).toBe('knowledgeAudit:log')
    expect(knowledgeTable.props('queryFormItems').map(i => i.label)).toEqual(['标题', '审核状态', '效力状态'])

    expect(cellTextOf(knowledgeTable, '法律法规')).toBe('-')
    expect(cellTextOf(knowledgeTable, '条款号')).toBe('-')
    expect(cellTextOf(knowledgeTable, '版本')).toBe('v1')
    expect(cellTextOf(knowledgeTable, '审核状态')).toBe('待审核')
    expect(cellTextOf(knowledgeTable, '上架')).toBe('下架')
    expect(cellTextOf(knowledgeTable, '效力')).toBe('现行有效')
    expect(cellTextOf(knowledgeTable, '生效日期')).toBe('-')

    // 待审行：通过/驳回/标记效力，不出现提交审核
    const buttons = knowledgeTable.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    expect(buttons).toContain('通过')
    expect(buttons).toContain('驳回')
    expect(buttons).toContain('标记效力')
    expect(buttons).not.toContain('提交审核')

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined })
    expect(listKnowledge).toHaveBeenCalledTimes(2)
  })

  it('智能体配置：平台/上下文标签、启停开关与重置口径保持', async () => {
    listAgentConfig.mockResolvedValue({
      rows: [{
        agentId: 1,
        agentName: '普法助手',
        provider: 'local',
        enableContext: '0',
        contextRounds: 5,
        welcome: '您好，这里是12348法律服务热线',
        status: '1'
      }],
      total: 1
    })
    const wrapper = mountPage(AgentConfig)
    await flush()

    expect(listAgentConfig).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, agentName: undefined, provider: undefined, status: undefined })
    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyActionText')).toBe('新增智能体')
    expect(cellTextOf(table, '平台')).toBe('本地知识库')
    expect(cellTextOf(table, '多轮上下文')).toBe('关闭')
    expect(cellTextOf(table, '上下文轮数')).toBe('5')
    expect(table.findAll('.el-table__body tr').at(0).findAll('.el-switch').length).toBe(1)

    wrapper.vm.handleSelectionChange([{ agentId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)
    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, agentName: undefined, provider: undefined, status: undefined })
    expect(listAgentConfig).toHaveBeenCalledTimes(2)
  })

  it('系统提示词：字典查询项/内容截断/默认标识与重置口径保持', async () => {
    listSystemPrompt.mockResolvedValue({
      rows: [
        {
          promptId: 1,
          promptName: '通用法律咨询',
          promptType: '1',
          sceneType: '通用',
          content: 'A'.repeat(60),
          isDefault: 'N',
          status: '0'
        },
        {
          promptId: 2,
          promptName: '劳动纠纷',
          promptType: '2',
          sceneType: '劳动',
          content: '短内容',
          isDefault: 'Y',
          status: '1'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(SystemPrompt)
    await flush()

    expect(listSystemPrompt).toHaveBeenCalledWith({ pageNum: 1, pageSize: 10, promptName: null, promptType: null, status: null })
    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('queryFormItems')[1].options).toEqual([{ label: '通用', value: '1' }, { label: '问答', value: '2' }])
    expect(table.props('queryFormItems')[2].options).toEqual([{ label: '正常', value: '0' }, { label: '停用', value: '1' }])

    expect(cellTextOf(table, '提示词类型')).toBe('1')
    expect(cellTextOf(table, '内容')).toBe('A'.repeat(50) + '...')
    expect(cellTextOf(table, '默认配置')).toBe('N')
    expect(cellTextOf(table, '状态')).toBe('0')

    // 仅非默认行出现「设为默认」
    const firstButtons = table.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = table.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toContain('设为默认')
    expect(secondButtons).not.toContain('设为默认')

    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams).toEqual({ pageNum: 1, pageSize: 10, promptName: undefined, promptType: undefined, status: undefined })
    expect(listSystemPrompt).toHaveBeenCalledTimes(2)
  })

  it('系统提示词：空态主操作直接打开新增弹窗', async () => {
    listSystemPrompt.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(SystemPrompt)
    await flush()
    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加系统提示词配置')
  })
})
