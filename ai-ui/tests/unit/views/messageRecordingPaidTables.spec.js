import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import Message from '@/views/lawyers/message/index.vue'
import Recording from '@/views/lawyers/recording/index.vue'
import PaidService from '@/views/lawyers/paidService/index.vue'
import { listMessage } from '@/api/lawyers/message'
import { listRecord } from '@/api/lawyers/recording'
import { listPaidService } from '@/api/lawyers/paidService'

jest.mock('@/api/lawyers/message', () => ({
  listMessage: jest.fn(),
  getMessage: jest.fn(),
  readMessage: jest.fn(),
  readAllMessage: jest.fn(),
  delMessage: jest.fn()
}))
jest.mock('@/api/lawyers/recording', () => ({
  listRecord: jest.fn(),
  getRecord: jest.fn(),
  playUrl: jest.fn(id => '/dev-api/lawyers/call/record/' + id + '/play'),
  downloadUrl: jest.fn(id => '/dev-api/lawyers/call/record/' + id + '/download'),
  fetchAudioBlob: jest.fn(() => Promise.resolve(new Blob()))
}))
jest.mock('@/api/lawyers/paidService', () => ({
  listPaidService: jest.fn(),
  getPaidService: jest.fn(),
  delPaidService: jest.fn(),
  addPaidService: jest.fn(),
  updatePaidService: jest.fn(),
  exportPaidService: jest.fn()
}))
jest.mock('@/utils/auth', () => ({ getToken: jest.fn(() => 'token') }))

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
  $route: { path: '/lawyers/message', query: {} },
  $router: { push: jest.fn() },
  $store: { state: { user: { name: 'tester', token: 'token' } } },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn(),
    undo: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn() }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component) {
  return mount(component, { localVue, stubs, mocks })
}

function cellTextOf(wrapper, label) {
  const index = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(0).findAll('td').at(index).text()
}

function toolbarButtonTexts(wrapper) {
  return wrapper.findAll('.pro-table .mb8 button').wrappers
    .map(b => b.text())
    .filter(t => t.length > 0)
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('message 域列表页 ProTable 迁移', () => {
  it('站内消息：日期区间插槽、类型/优先级/状态标签与未读操作保持', async () => {
    listMessage.mockResolvedValue({
      rows: [
        {
          messageId: 1,
          msgType: '3',
          title: '风险预警：敏感话术',
          priority: '1',
          sender: '质检中心',
          isRead: '0',
          createTime: '2026-11-01 10:00'
        },
        {
          messageId: 2,
          msgType: '1',
          title: '系统维护通知',
          priority: '2',
          sender: '系统',
          isRead: '1',
          createTime: '2026-11-01 09:00'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(Message)
    await flush()

    expect(listMessage).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      msgType: undefined,
      isRead: undefined,
      title: undefined
    }))
    expect(listMessage.mock.calls[0][0].params).toEqual({ beginTime: undefined, endTime: undefined })

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无站内消息')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '类型', '标题', '优先级', '发送方', '状态', '发送时间', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['消息类型', '阅读状态', '标题', '发送时间'])
    expect(table.props('queryFormItems')[0].options).toHaveLength(6)

    expect(cellTextOf(wrapper, '类型')).toBe('风险预警')
    expect(cellTextOf(wrapper, '标题')).toBe('风险预警：敏感话术')
    expect(cellTextOf(wrapper, '优先级')).toBe('紧急')
    expect(cellTextOf(wrapper, '发送方')).toBe('质检中心')
    expect(cellTextOf(wrapper, '状态')).toBe('未读')
    // 发送时间沿用后端原始字段，不做 parseTime 转换
    expect(cellTextOf(wrapper, '发送时间')).toBe('2026-11-01 10:00')

    // 未读行有「已读」，已读行没有
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toContain('已读')
    expect(secondButtons).not.toContain('已读')
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['全部已读', '删除']))

    // 独立 dateRange 走查询插槽，重置时一并清空并重查
    expect(wrapper.find('.el-form .el-date-editor').exists()).toBe(true)
    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    // addDateRange 会把 params 挂到查询对象上，这里按字段校验筛选已复位
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.pageSize).toBe(10)
    expect(wrapper.vm.queryParams.msgType).toBeUndefined()
    expect(wrapper.vm.queryParams.isRead).toBeUndefined()
    expect(wrapper.vm.queryParams.title).toBeUndefined()
    expect(listMessage).toHaveBeenCalledTimes(2)

    // 多选联动：删除按钮由 multiple 控制
    wrapper.vm.handleSelectionChange([{ messageId: 1 }, { messageId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)
    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.multiple).toBe(true)
  })
})

describe('recording 域列表页 ProTable 迁移', () => {
  it('通话录音：ASR 标签/时长格式/空值与播放交互保持，操作列不再固定右侧', async () => {
    listRecord.mockResolvedValue({
      rows: [
        {
          recordId: 1,
          callerNumber: '13800138000',
          calleeNumber: null,
          agentName: null,
          callDuration: 65,
          recordDuration: 65,
          asrStatus: '1',
          callTime: '2026-11-01 10:00',
          transcript: '客户咨询劳动合同解除',
          recordFile: 'record-1.wav'
        },
        {
          recordId: 2,
          callerNumber: '13900139000',
          calleeNumber: '12348',
          agentName: '李律师',
          callDuration: 30,
          recordDuration: null,
          asrStatus: '0',
          callTime: '2026-11-01 11:00',
          transcript: null,
          recordFile: 'record-2.wav'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(Recording)
    await flush()

    expect(listRecord).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      callerNumber: undefined,
      calleeNumber: undefined,
      asrStatus: undefined,
      onlyRecording: true
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('emptyText')).toBe('暂无录音记录')
    expect(table.props('tableAttrs')).toEqual({ border: true })
    expect(table.props('columns').map(c => c.label)).toEqual([
      '录音ID', '主叫号码', '被叫号码', '坐席', '通话时长', '录音时长', 'ASR状态', '通话时间', '录音文件', '操作'
    ])
    // 团队约定：不使用 fixed="right"
    expect(table.props('columns').every(c => c.fixed === undefined)).toBe(true)
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['主叫号码', '被叫号码', 'ASR转写', '通话日期'])

    expect(cellTextOf(wrapper, '录音ID')).toBe('1')
    expect(cellTextOf(wrapper, '被叫号码')).toBe('-')
    expect(cellTextOf(wrapper, '坐席')).toBe('-')
    expect(cellTextOf(wrapper, '通话时长')).toBe('01:05')
    expect(cellTextOf(wrapper, '录音时长')).toBe('01:05')
    expect(cellTextOf(wrapper, 'ASR状态')).toBe('已转写')
    expect(cellTextOf(wrapper, '通话时间')).toBe('2026-11-01 10:00')

    // 录音文件列保留播放按钮；操作列保留下载与查看转写（未转写禁用）
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers
    expect(firstButtons.map(b => b.text())).toEqual(expect.arrayContaining(['播放', '下载', '查看转写']))
    const disabledTranscript = secondButtons.find(b => b.text() === '查看转写')
    expect(disabledTranscript.attributes('disabled')).toBe('disabled')

    // 播放态由 playingId 驱动，停止后复位
    wrapper.vm.playingId = 1
    wrapper.vm.handleStop()
    expect(wrapper.vm.playingId).toBeNull()
    expect(wrapper.vm.audioUrl).toBe('')

    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    // addDateRange 会把 params 挂到查询对象上，这里按字段校验筛选已复位
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.pageSize).toBe(10)
    expect(wrapper.vm.queryParams.callerNumber).toBeUndefined()
    expect(wrapper.vm.queryParams.calleeNumber).toBeUndefined()
    expect(wrapper.vm.queryParams.asrStatus).toBeUndefined()
    expect(listRecord).toHaveBeenCalledTimes(2)
  })
})

describe('paidService 域列表页 ProTable 迁移', () => {
  it('有偿法律服务：金额/支付与服务状态标签、多选与导出工具条保持', async () => {
    listPaidService.mockResolvedValue({
      rows: [
        {
          paidId: 1,
          serviceNo: 'SF20261106001',
          customerName: '张三',
          customerPhone: '13800138000',
          serviceType: '代书',
          amount: 120.5,
          paymentStatus: '1',
          serviceStatus: '2',
          handlerName: '李律师',
          createTime: '2026-11-01 10:00'
        },
        {
          paidId: 2,
          serviceNo: 'SF20261106002',
          customerName: '王五',
          customerPhone: '13700137000',
          serviceType: '调解',
          amount: null,
          paymentStatus: '0',
          serviceStatus: '0',
          handlerName: null,
          createTime: '2026-11-01 11:00'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(PaidService)
    await flush()

    expect(listPaidService).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      serviceNo: undefined,
      customerName: undefined,
      customerPhone: undefined,
      serviceType: undefined,
      paymentStatus: undefined,
      serviceStatus: undefined
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无有偿法律服务记录')
    expect(table.props('queryFormItems').map(i => i.label)).toEqual([
      '服务编号', '客户姓名', '客户电话', '服务类型', '支付状态', '服务状态', '创建时间'
    ])
    expect(table.props('queryFormItems')[3].options).toHaveLength(4)

    expect(cellTextOf(wrapper, '服务编号')).toBe('SF20261106001')
    expect(cellTextOf(wrapper, '服务类型')).toBe('代书')
    expect(cellTextOf(wrapper, '服务金额')).toBe('¥120.50')
    expect(cellTextOf(wrapper, '支付状态')).toBe('已付')
    expect(cellTextOf(wrapper, '服务状态')).toBe('已完成')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')
    expect(cellTextOf(wrapper, '操作')).toContain('详情')

    // 金额为空时展示 '-'（第二行）
    const amountCells = wrapper.findAll('.el-table__body tr').at(1).findAll('td')
    const amountIndex = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes('服务金额'))
    expect(amountCells.at(amountIndex).text()).toBe('-')

    // 工具条保留新增/修改/删除/导出
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['新增', '修改', '删除', '导出']))

    // 多选联动：单选可修改、多选可删除
    wrapper.vm.handleSelectionChange([{ paidId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)
    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)

    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    // addDateRange 会把 params 挂到查询对象上，这里按字段校验筛选已复位
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.pageSize).toBe(10)
    expect(wrapper.vm.queryParams.serviceNo).toBeUndefined()
    expect(wrapper.vm.queryParams.customerName).toBeUndefined()
    expect(wrapper.vm.queryParams.customerPhone).toBeUndefined()
    expect(wrapper.vm.queryParams.serviceType).toBeUndefined()
    expect(wrapper.vm.queryParams.paymentStatus).toBeUndefined()
    expect(wrapper.vm.queryParams.serviceStatus).toBeUndefined()
    expect(listPaidService).toHaveBeenCalledTimes(2)
  })

  it('有偿法律服务：空态展示内建文案', async () => {
    listPaidService.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(PaidService)
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无有偿法律服务记录')
  })
})
