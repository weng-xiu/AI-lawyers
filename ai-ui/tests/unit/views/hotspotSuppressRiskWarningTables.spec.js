import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import HotspotSuppress from '@/views/lawyers/hotspotSuppress/index.vue'
import RiskWarning from '@/views/lawyers/riskWarning/index.vue'
import { listHotspot, listHotspotLog } from '@/api/lawyers/hotspotSuppress'
import { listRiskWarning, listRiskWarningRule, listTransferOrgs } from '@/api/lawyers/riskWarning'

jest.mock('@/api/lawyers/hotspotSuppress', () => ({
  listHotspot: jest.fn(),
  getHotspot: jest.fn(),
  addHotspot: jest.fn(),
  updateHotspot: jest.fn(),
  delHotspot: jest.fn(),
  listHotspotLog: jest.fn()
}))
jest.mock('@/api/lawyers/riskWarning', () => ({
  listRiskWarning: jest.fn(),
  getRiskWarning: jest.fn(),
  addRiskWarning: jest.fn(),
  updateRiskWarning: jest.fn(),
  delRiskWarning: jest.fn(),
  exportRiskWarning: jest.fn(),
  listTransferOrgs: jest.fn(),
  transferRiskWarning: jest.fn(),
  listRiskWarningRule: jest.fn(),
  getRiskWarningRule: jest.fn(),
  addRiskWarningRule: jest.fn(),
  updateRiskWarningRule: jest.fn(),
  delRiskWarningRule: jest.fn()
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
  'pro-dialog': true,
  'pro-form': true,
  'user-select': true,
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}|{{ actionText }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

const mocks = {
  $route: { path: '/lawyers/hotspotSuppress', query: {} },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    undo: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn(), info: jest.fn() },
  dict: { type: {} }
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

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('hotspotSuppress 高频置底 2 表 ProTable 迁移', () => {
  it('规则表 + 命中日志弹窗表：筛选/标签/频次口径/开关与独立 persist-key 保持', async () => {
    listHotspot.mockResolvedValue({
      rows: [
        {
          suppressId: 1,
          ruleName: '骚扰号码拦截',
          matchType: 'PHONE',
          matchValue: '13800000000',
          action: 'REJECT',
          priorityLevel: -100,
          triggerCount: 0,
          windowSeconds: 0,
          hitCount: 12,
          status: '0',
          effectiveStart: null,
          effectiveEnd: '2026-12-31 23:59:59'
        },
        {
          suppressId: 2,
          ruleName: '高频催收',
          matchType: 'KEYWORD',
          matchValue: '催收',
          action: 'PRIORITY',
          priorityLevel: -200,
          triggerCount: 3,
          windowSeconds: 0,
          hitCount: 5,
          status: '1',
          effectiveStart: '2026-11-01 00:00:00',
          effectiveEnd: null
        }
      ],
      total: 2
    })
    listHotspotLog.mockResolvedValue({
      rows: [
        {
          hitTime: '2026-11-01 09:00:00',
          ruleName: '骚扰号码拦截',
          direction: 'INBOUND',
          callerNumber: '13800000000',
          calleeNumber: '12345',
          action: 'REJECT',
          priority: -100,
          channelUuid: 'uuid-1'
        }
      ],
      total: 1
    })
    const wrapper = mountPage(HotspotSuppress, '/lawyers/hotspotSuppress')
    await flush()

    expect(listHotspot).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      ruleName: undefined,
      matchType: undefined,
      action: undefined,
      status: undefined
    }))

    const ruleTable = tableByKey(wrapper, 'hotspotSuppress:rule')
    expect(ruleTable).toBeTruthy()
    expect(ruleTable.props('showToolbar')).toBe(false)
    expect(ruleTable.props('tableAttrs')).toEqual({ border: true })
    // 约定：不使用 fixed="right"
    expect(ruleTable.props('columns').every(c => !c.fixed)).toBe(true)
    expect(ruleTable.props('columns').map(c => c.label)).toEqual([
      'ID', '规则名称', '匹配方式', '匹配值', '处置动作', '置底优先级', '频次阈值',
      '累计命中', '状态', '生效开始', '生效结束', '操作'
    ])
    expect(ruleTable.props('queryFormItems').map(i => i.label)).toEqual(['规则名称', '匹配方式', '处置动作', '状态'])
    expect(ruleTable.props('queryFormItems')[1].options).toEqual([
      { label: '号码匹配', value: 'PHONE' },
      { label: '关键词匹配', value: 'KEYWORD' }
    ])

    expect(cellTextIn(ruleTable, 'ID')).toBe('1')
    expect(cellTextIn(ruleTable, '匹配方式')).toBe('号码匹配')
    expect(cellTextIn(ruleTable, '处置动作')).toBe('直接挂断')
    expect(cellTextIn(ruleTable, '频次阈值')).toBe('每次命中')
    expect(cellTextIn(ruleTable, '生效开始')).toBe('立即')
    expect(cellTextIn(ruleTable, '生效结束')).toBe('2026-11-01 10:00')
    expect(cellTextIn(ruleTable, '匹配方式', 1)).toBe('关键词匹配')
    expect(cellTextIn(ruleTable, '频次阈值', 1)).toBe('3 次 / 不限时')
    expect(cellTextIn(ruleTable, '生效结束', 1)).toBe('永久')
    expect(ruleTable.findAll('.el-table__body .el-switch').length).toBe(2)
    expect(toolbarButtonTexts(ruleTable)).toEqual(expect.arrayContaining(['新增', '命中日志']))
    expect(ruleTable.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text()))
      .toEqual(expect.arrayContaining(['日志', '编辑', '删除']))

    // 命中日志弹窗表：独立 persist-key、固定高度与独立查询项
    wrapper.vm.openLogDialog({ suppressId: 1, ruleName: '骚扰号码拦截' })
    await flush()
    await wrapper.vm.$nextTick()
    const logTable = tableByKey(wrapper, 'hotspotSuppress:log')
    expect(logTable).toBeTruthy()
    expect(logTable.props('showToolbar')).toBe(false)
    expect(logTable.props('tableAttrs')).toEqual({ border: true, maxHeight: '420px' })
    expect(logTable.props('queryFormItems').map(i => i.label)).toEqual(['主叫号码', '方向'])
    expect(listHotspotLog).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      suppressId: 1,
      callerNumber: undefined,
      direction: undefined
    }))
    expect(cellTextIn(logTable, '方向')).toBe('入站')
    expect(cellTextIn(logTable, '动作')).toBe('直接挂断')
    expect(cellTextIn(logTable, '主叫')).toBe('13800000000')

    // 规则表重置：重设查询参数并重查
    wrapper.vm.queryParams.ruleName = '骚扰'
    wrapper.vm.queryParams.matchType = 'PHONE'
    wrapper.vm.queryParams.status = '0'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.ruleName).toBeUndefined()
    expect(wrapper.vm.queryParams.matchType).toBeUndefined()
    expect(wrapper.vm.queryParams.status).toBeUndefined()
    expect(listHotspot).toHaveBeenCalledTimes(2)

    // 日志表重置：清空筛选但保留当前规则的 suppressId
    wrapper.vm.logQuery.direction = 'INBOUND'
    wrapper.vm.logQuery.callerNumber = '138'
    wrapper.vm.resetLogQuery()
    await flush()
    expect(wrapper.vm.logQuery.pageNum).toBe(1)
    expect(wrapper.vm.logQuery.direction).toBeUndefined()
    expect(wrapper.vm.logQuery.callerNumber).toBeUndefined()
    expect(wrapper.vm.logQuery.suppressId).toBe(1)
    expect(listHotspotLog).toHaveBeenCalledTimes(2)
  })

  it('规则表：空态展示内建文案', async () => {
    listHotspot.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(HotspotSuppress, '/lawyers/hotspotSuppress')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无高频置底规则')
  })
})

describe('riskWarning 双 Tab 2 表 ProTable 迁移', () => {
  it('预警记录 + 预警规则：选择联动/标签口径/详情转办分支/独立查询区与重置保持', async () => {
    listRiskWarning.mockResolvedValue({
      rows: [
        {
          warningId: 1,
          warningType: '敏感词',
          warningLevel: 1,
          sourceType: '通话',
          ruleName: null,
          suggestTransferType: null,
          customerName: '王先生',
          content: '我要投诉',
          status: 0,
          handlerName: null,
          triggerTime: '2026-11-01 09:00:00',
          transferId: null
        },
        {
          warningId: 2,
          warningType: '合规风险',
          warningLevel: 3,
          sourceType: '图文',
          ruleName: '违规承诺',
          suggestTransferType: 'MEDIATION',
          customerName: '李女士',
          content: '承诺胜诉',
          status: 3,
          handlerName: '张律师',
          triggerTime: '2026-11-01 10:00:00',
          transferId: 88,
          ticketNo: 'TK20261101001'
        }
      ],
      total: 2
    })
    listRiskWarningRule.mockResolvedValue({
      rows: [
        {
          ruleId: 10,
          ruleName: '敏感词规则',
          ruleType: '敏感词',
          ruleLevel: 1,
          keywords: '投诉,曝光',
          suggestTransferType: 'LEGAL_AID',
          suggestOrgId: 1,
          isEnabled: 1,
          createTime: '2026-10-01 08:00:00'
        },
        {
          ruleId: 11,
          ruleName: '行为规则',
          ruleType: '异常行为',
          ruleLevel: 3,
          keywords: '辱骂',
          suggestTransferType: null,
          suggestOrgId: 9,
          isEnabled: 0,
          createTime: '2026-10-02 08:00:00'
        }
      ],
      total: 2
    })
    listTransferOrgs.mockResolvedValue({ data: [{ orgId: 1, orgName: '市法律援助中心' }] })

    const wrapper = mountPage(RiskWarning, '/lawyers/riskWarning')
    await flush()

    expect(listRiskWarning).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      warningType: undefined,
      warningLevel: undefined,
      status: undefined,
      params: {}
    }))

    const warningTable = tableByKey(wrapper, 'riskWarning:warning')
    expect(warningTable).toBeTruthy()
    expect(warningTable.props('hasSelection')).toBe(true)
    expect(warningTable.props('columns').map(c => c.label)).toEqual([
      '预警ID', '预警类型', '预警级别', '来源类型', '命中规则', '建议转办',
      '客户姓名', '触发内容', '处理状态', '处理人', '触发时间', '操作'
    ])
    expect(warningTable.props('queryFormItems').map(i => i.label)).toEqual([
      '预警类型', '预警级别', '处理状态', '触发时间'
    ])
    expect(warningTable.props('queryFormItems')[2].options).toEqual([
      { label: '待处理', value: '0' },
      { label: '处理中', value: '1' },
      { label: '已处理', value: '2' },
      { label: '已忽略', value: '3' }
    ])

    expect(cellTextIn(warningTable, '预警类型')).toBe('敏感词')
    expect(cellTextIn(warningTable, '预警级别')).toBe('高')
    expect(cellTextIn(warningTable, '命中规则')).toBe('-')
    expect(cellTextIn(warningTable, '建议转办')).toBe('-')
    expect(cellTextIn(warningTable, '处理状态')).toBe('待处理')
    expect(cellTextIn(warningTable, '触发时间')).toBe('2026-11-01 10:00')
    expect(cellTextIn(warningTable, '建议转办', 1)).toBe('人民调解')
    expect(cellTextIn(warningTable, '处理状态', 1)).toBe('已忽略')
    expect(cellTextIn(warningTable, '操作', 1)).toContain('已转办 TK20261101001')
    // 无建议转办的行不出现「一键转办」
    const firstButtons = warningTable.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toEqual(expect.arrayContaining(['详情', '修改', '删除']))
    expect(firstButtons).not.toContain('一键转办')
    expect(toolbarButtonTexts(warningTable)).toEqual(expect.arrayContaining(['新增', '修改', '删除', '导出']))
    // 触发时间为区间选择器，走查询插槽
    expect(warningTable.findAll('.el-form .el-date-editor').length).toBe(1)

    wrapper.vm.handleSelectionChange([{ warningId: 1 }, { warningId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)

    // 查询区显隐在两张表之间独立
    warningTable.vm.showSearchSync = false
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.showSearch).toBe(false)
    expect(wrapper.vm.ruleShowSearch).toBe(true)

    // 预警记录重置：清空日期区间并重设查询参数
    wrapper.vm.dateRange = ['2026-10-01 00:00:00', '2026-10-02 23:59:59']
    wrapper.vm.queryParams.warningType = '敏感词'
    wrapper.vm.queryParams.status = '0'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.warningType).toBeUndefined()
    expect(wrapper.vm.queryParams.status).toBeUndefined()
    expect(listRiskWarning).toHaveBeenCalledTimes(2)

    // 规则表
    wrapper.vm.handleTabClick({ name: 'rule' })
    await flush()
    const ruleTable = tableByKey(wrapper, 'riskWarning:rule')
    expect(ruleTable).toBeTruthy()
    expect(ruleTable.props('hasSelection')).toBe(true)
    expect(ruleTable.props('showSearch')).toBe(true)
    expect(ruleTable.props('columns').map(c => c.label)).toEqual([
      '规则ID', '规则名称', '规则类型', '规则级别', '关键词', '建议转办条线',
      '默认建议机构', '是否启用', '创建时间', '操作'
    ])
    expect(ruleTable.props('queryFormItems').map(i => i.label)).toEqual(['规则名称', '规则类型', '规则级别'])

    expect(cellTextIn(ruleTable, '规则类型')).toBe('敏感词')
    expect(cellTextIn(ruleTable, '规则级别')).toBe('高')
    expect(cellTextIn(ruleTable, '建议转办条线')).toBe('法律援助')
    expect(cellTextIn(ruleTable, '默认建议机构')).toBe('市法律援助中心')
    expect(cellTextIn(ruleTable, '是否启用')).toBe('启用')
    expect(cellTextIn(ruleTable, '规则类型', 1)).toBe('异常行为')
    expect(cellTextIn(ruleTable, '建议转办条线', 1)).toBe('-')
    expect(cellTextIn(ruleTable, '默认建议机构', 1)).toBe('机构#9')
    expect(cellTextIn(ruleTable, '是否启用', 1)).toBe('禁用')
    expect(toolbarButtonTexts(ruleTable)).toEqual(expect.arrayContaining(['新增', '修改', '删除']))

    wrapper.vm.handleRuleSelectionChange([{ ruleId: 10 }])
    expect(wrapper.vm.ruleIds).toEqual([10])
    expect(wrapper.vm.ruleSingle).toBe(false)

    wrapper.vm.ruleQueryParams.ruleName = '敏感'
    wrapper.vm.ruleQueryParams.ruleType = '敏感词'
    wrapper.vm.resetRuleQuery()
    await flush()
    expect(wrapper.vm.ruleQueryParams.pageNum).toBe(1)
    expect(wrapper.vm.ruleQueryParams.ruleName).toBeUndefined()
    expect(wrapper.vm.ruleQueryParams.ruleType).toBeUndefined()
    expect(listRiskWarningRule).toHaveBeenCalledTimes(2)
  })

  it('预警记录表：空态展示内建文案', async () => {
    listRiskWarning.mockResolvedValue({ rows: [], total: 0 })
    listTransferOrgs.mockResolvedValue({ data: [] })
    const wrapper = mountPage(RiskWarning, '/lawyers/riskWarning')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无风险预警')
  })
})
