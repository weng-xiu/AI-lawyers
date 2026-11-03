import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import StatisticsReport from '@/views/lawyers/statistics/index.vue'
import AdhocAnalysis from '@/views/lawyers/statistics/AdhocAnalysis.vue'
import {
  getCallReport,
  getServiceReport,
  getQualityReport,
  getBusinessReport,
  getAdhocCatalog,
  queryAdhoc,
  drillAdhoc
} from '@/api/lawyers/report'

jest.mock('@/api/lawyers/report', () => ({
  getCallReport: jest.fn(),
  getServiceReport: jest.fn(),
  getQualityReport: jest.fn(),
  getBusinessReport: jest.fn(),
  statBackfill: jest.fn(),
  statBackfillProgress: jest.fn(),
  getAdhocCatalog: jest.fn(),
  queryAdhoc: jest.fn(),
  drillAdhoc: jest.fn()
}))
jest.mock('@/utils/longTask', () => ({
  createLongTask: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})

const stubs = {
  'right-toolbar': true,
  pagination: true,
  TableEmpty: {
    template: '<div class="table-empty-stub">{{ text }}|{{ hint }}</div>',
    props: ['text', 'hint', 'actionText', 'actionIcon']
  }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountReport() {
  return mount(StatisticsReport, {
    localVue,
    stubs,
    mocks: {
      $route: { path: '/lawyers/statistics' },
      $modal: {
        msgWarning: jest.fn(),
        dangerConfirm: jest.fn(() => Promise.resolve()),
        notifyTaskStart: jest.fn(),
        notifyTaskDone: jest.fn(),
        notifyTaskFail: jest.fn()
      }
    }
  })
}

/** 第 index 张报表 ProTable 的首行文本 */
function firstRowText(wrapper, index) {
  return wrapper.findAllComponents(ProTable).at(index).findAll('.el-table__body tr').at(0).text()
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('statistics 报表域 ProTable 迁移', () => {
  it('初始化按近 7 天 + 日粒度请求呼叫报表，并渲染百分比列', async () => {
    getCallReport.mockResolvedValue({
      data: [{ period: '2026-11-01', totalCalls: 10, answeredCalls: 8, missedCalls: 2, transferredCalls: 0, answerRate: 80, avgDurationSec: 120 }]
    })
    const wrapper = mountReport()
    await flush()

    const params = getCallReport.mock.calls[0][0]
    expect(params.granularity).toBe('day')
    expect(params.beginTime).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(params.endTime).toMatch(/^\d{4}-\d{2}-\d{2}$/)

    // 4 张报表表均使用 ProTable（无查询区/工具条/分页）
    expect(wrapper.findAllComponents(ProTable).length).toBe(4)
    const callTable = wrapper.findAllComponents(ProTable).at(0)
    expect(callTable.props('showSearch')).toBe(false)
    expect(callTable.props('showToolbar')).toBe(false)
    expect(callTable.props('hidePagination')).toBe(true)
    expect(callTable.props('columns').map(c => c.label)).toEqual([
      '周期', '呼叫总量', '接通量', '未接量', '转接量', '接通率', '平均通话时长(秒)'
    ])
    expect(firstRowText(wrapper, 0)).toContain('2026-11-01')
    expect(firstRowText(wrapper, 0)).toContain('80%')
  })

  it('切换表格与粒度后按新参数请求对应报表，列口径各自独立', async () => {
    getCallReport.mockResolvedValue({ data: [] })
    getBusinessReport.mockResolvedValue({
      data: [{ period: '2026-11-02', totalTickets: 4, closedTickets: 3, closeRate: 75, overdueTickets: 1, avgCloseMinutes: 30 }]
    })
    const wrapper = mountReport()
    await flush()

    wrapper.vm.activeTab = 'business'
    wrapper.vm.granularity = 'week'
    wrapper.vm.loadData()
    await flush()

    expect(getBusinessReport).toHaveBeenCalledTimes(1)
    const params = getBusinessReport.mock.calls[0][0]
    expect(params.granularity).toBe('week')
    expect(firstRowText(wrapper, 3)).toContain('75%')

    const businessColumns = wrapper.findAllComponents(ProTable).at(3).props('columns')
    expect(businessColumns.map(c => c.label)).toEqual([
      '周期', '工单量', '办结量', '办结率', '超时量', '平均办结时长(分钟)'
    ])
  })

  it('空数据时使用 ProTable 内建空态文案', async () => {
    getCallReport.mockResolvedValue({ data: [] })
    const wrapper = mountReport()
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无呼叫报表数据')
  })
})

describe('AdhocAnalysis 自助分析 ProTable 迁移', () => {
  it('服务端目录驱动动态列，formatter 完成维度标签翻译', async () => {
    getAdhocCatalog.mockResolvedValue({
      data: {
        metrics: [{ key: 'calls', label: '呼叫量', group: '呼叫类' }],
        dimensions: [],
        crossMetrics: ['calls']
      }
    })
    queryAdhoc.mockResolvedValue({
      data: {
        columns: [
          { key: 'period', label: '周期' },
          { key: 'channel', label: '渠道' },
          { key: 'calls', label: '呼叫量' }
        ],
        rows: [{ period: '2026-11-01', channel: '1', calls: 12 }],
        labels: { channel: { '1': '热线' } },
        source: 'preagg'
      }
    })

    const wrapper = mount(AdhocAnalysis, {
      localVue,
      stubs,
      mocks: { $message: { warning: jest.fn() } }
    })
    await flush()

    expect(queryAdhoc).toHaveBeenCalledTimes(1)
    const params = queryAdhoc.mock.calls[0][0]
    expect(params.metrics).toBe('calls')
    expect(params.dimensions).toBe('')
    expect(params.granularity).toBe('day')

    const resultTable = wrapper.findAllComponents(ProTable).at(0)
    expect(resultTable.props('columns').map(c => c.label)).toEqual(['周期', '渠道', '呼叫量', '操作'])
    const rowText = resultTable.findAll('.el-table__body tr').at(0).text()
    expect(rowText).toContain('2026-11-01')
    expect(rowText).toContain('热线') // channel=1 经 labels 翻译
    expect(rowText).toContain('12')
    expect(rowText).toContain('下钻')
  })

  it('下钻按 pageNum/pageSize 请求明细，翻页后携带新页码', async () => {
    getAdhocCatalog.mockResolvedValue({
      data: {
        metrics: [{ key: 'calls', label: '呼叫量', group: '呼叫类' }],
        dimensions: [{ key: 'channel', label: '渠道', metrics: ['calls'] }],
        crossMetrics: ['calls']
      }
    })
    queryAdhoc.mockResolvedValue({
      data: {
        columns: [{ key: 'period', label: '周期' }, { key: 'calls', label: '呼叫量' }],
        rows: [{ period: '2026-11-01', calls: 12, _drill: { begin: '2026-11-01 00:00', end: '2026-11-01 23:59' } }],
        labels: {},
        source: 'preagg'
      }
    })
    drillAdhoc.mockResolvedValue({
      rows: [{ callerNumber: '13800000000', duration: 60, status: '1' }],
      total: 25
    })

    const wrapper = mount(AdhocAnalysis, {
      localVue,
      stubs,
      mocks: { $message: { warning: jest.fn() } }
    })
    await flush()

    // 点击结果行操作列的「下钻」
    await wrapper.findAllComponents(ProTable).at(0).findAll('.el-table__body .el-button').at(0).trigger('click')
    await flush()

    expect(drillAdhoc).toHaveBeenCalledTimes(1)
    const first = drillAdhoc.mock.calls[0][0]
    expect(first.pageNum).toBe(1)
    expect(first.pageSize).toBe(10)
    expect(first.beginTime).toBe('2026-11-01 00:00')

    // 下钻表使用 ProTable 且启用分页；翻页后 pageNum 生效
    const drillTable = wrapper.findAllComponents(ProTable).at(1)
    expect(drillTable.props('total')).toBe(25)
    wrapper.vm.drillQueryParams.pageNum = 2
    wrapper.vm.loadDrill()
    await flush()
    expect(drillAdhoc.mock.calls[1][0].pageNum).toBe(2)
    expect(drillAdhoc.mock.calls[1][0].pageSize).toBe(10)
  })
})
