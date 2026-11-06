import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import Consultation from '@/views/lawyers/consultation/index.vue'
import SignRecord from '@/views/lawyers/signRecord/index.vue'
import SkillQueue from '@/views/lawyers/skill/queue.vue'
import VideoConsult from '@/views/lawyers/videoConsult/index.vue'
import { listConsultation } from '@/api/lawyers/consultation'
import { listSignRecord } from '@/api/lawyers/signRecord'
import { listQueue } from '@/api/lawyers/skill'
import { listVideoConsult } from '@/api/lawyers/videoConsult'

jest.mock('echarts', () => ({
  init: jest.fn(() => ({ setOption: jest.fn(), resize: jest.fn() }))
}))
jest.mock('@/api/lawyers/consultation', () => ({
  listConsultation: jest.fn(),
  getConsultation: jest.fn(),
  delConsultation: jest.fn(),
  addConsultation: jest.fn(),
  updateConsultation: jest.fn(),
  exportConsultation: jest.fn(),
  getConsultationStatistics: jest.fn(() => Promise.resolve({ data: {} })),
  getConsultationByCategory: jest.fn(() => Promise.resolve({ data: [] })),
  getConsultationByDate: jest.fn(() => Promise.resolve({ data: [] })),
  getConsultationChannelStats: jest.fn(() => Promise.resolve({ data: {} }))
}))
jest.mock('@/api/lawyers/category', () => ({
  getValidCategories: jest.fn(() => Promise.resolve({ data: [{ categoryId: 1, categoryName: '劳动纠纷' }] }))
}))
jest.mock('@/api/lawyers/signRecord', () => ({
  listSignRecord: jest.fn(),
  getSignRecord: jest.fn(),
  addSignRecord: jest.fn(),
  updateSignRecord: jest.fn(),
  delSignRecord: jest.fn()
}))
jest.mock('@/api/lawyers/skill', () => ({
  listQueue: jest.fn(),
  listQueuing: jest.fn(() => Promise.resolve({ data: [] })),
  assignManually: jest.fn(),
  kickQueue: jest.fn(),
  listEnabledGroups: jest.fn(() => Promise.resolve({ data: [{ groupId: 1, groupName: '法律咨询组' }] }))
}))
jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn(() => Promise.resolve({ rows: [{ agentId: 1, agentName: '张律师' }] }))
}))
jest.mock('@/api/lawyers/videoConsult', () => ({
  listVideoConsult: jest.fn(),
  getVideoConsult: jest.fn(),
  addVideoConsult: jest.fn(),
  updateVideoConsult: jest.fn(),
  delVideoConsult: jest.fn()
}))
jest.mock('@/utils/polling', () => ({
  createPoller: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
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
  }
}

const mocks = {
  $route: { path: '/lawyers/consultation', query: {} },
  $modal: {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn(),
    msgWarning: jest.fn()
  },
  $message: { success: jest.fn(), warning: jest.fn(), error: jest.fn(), info: jest.fn() },
  dict: {
    type: {
      consultation_status: [{ label: '待处理', value: '0' }, { label: '已完成', value: '1' }]
    }
  }
}

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountPage(component, path) {
  return mount(component, {
    localVue,
    stubs,
    mocks: { ...mocks, $route: { path, query: {} } }
  })
}

function cellTextOf(wrapper, label, rowIndex = 0) {
  const index = wrapper.findAll('.el-table__header-wrapper th').wrappers.findIndex(th => th.text().includes(label))
  expect(index).toBeGreaterThan(-1)
  return wrapper.findAll('.el-table__body tr').at(rowIndex).findAll('td').at(index).text()
}

function toolbarButtonTexts(wrapper) {
  return wrapper.findAll('.pro-table .mb8 button').wrappers.map(b => b.text()).filter(t => t.length > 0)
}

beforeEach(() => {
  localStorage.clear()
  jest.clearAllMocks()
})

describe('consultation 咨询域列表页 ProTable 迁移', () => {
  it('咨询记录：多渠道/分类/字典/评分区间与双日期插槽保持', async () => {
    listConsultation.mockResolvedValue({
      rows: [
        {
          consultationId: 1,
          userId: 1001,
          categoryName: '劳动纠纷',
          consultationChannel: '1',
          question: '被拖欠工资怎么办',
          answer: '建议先收集证据',
          status: '0',
          createTime: '2026-11-01 09:00:00'
        }
      ],
      total: 1
    })
    const wrapper = mountPage(Consultation, '/lawyers/consultation')
    await flush()

    expect(listConsultation).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      userId: undefined,
      categoryId: undefined,
      status: undefined,
      consultationChannel: undefined,
      // 原页在 created 中先 getList 再初始化评分区间，首次请求不携带评分上下限（保持原口径）
      minRating: undefined,
      maxRating: undefined
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无咨询记录')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '咨询ID', '用户ID', '咨询分类', '咨询渠道', '咨询问题', 'AI回答', '状态', '创建时间', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual([
      '用户ID', '咨询分类', '咨询渠道', '状态', '问题关键词', '回答关键词', '评分范围', '创建时间', '更新时间'
    ])
    expect(table.props('queryFormItems')[1].options).toEqual([{ label: '劳动纠纷', value: 1 }])
    expect(table.props('queryFormItems')[2].options).toEqual([
      { label: '语音', value: '1' },
      { label: '图文', value: '2' },
      { label: '视频', value: '3' }
    ])
    expect(table.props('queryFormItems')[3].options).toEqual([
      { label: '待处理', value: '0' },
      { label: '已完成', value: '1' }
    ])

    expect(cellTextOf(wrapper, '咨询ID')).toBe('1')
    expect(cellTextOf(wrapper, '用户ID')).toBe('1001')
    expect(cellTextOf(wrapper, '咨询分类')).toBe('劳动纠纷')
    expect(cellTextOf(wrapper, '咨询渠道')).toBe('语音')
    expect(cellTextOf(wrapper, '状态')).toBe('0')
    expect(cellTextOf(wrapper, '创建时间')).toBe('2026-11-01 10:00')

    // 评分区间 slider + 创建/更新两个日期区间均走查询插槽
    expect(wrapper.findAll('.el-form .el-slider').length).toBe(1)
    expect(wrapper.findAll('.el-form .el-date-editor').length).toBe(2)
    expect(wrapper.findAll('.el-form button').wrappers.map(b => b.text())).toEqual(expect.arrayContaining(['高级搜索']))
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['新增', '修改', '删除', '导出', '统计分析']))

    wrapper.vm.handleSelectionChange([{ consultationId: 1 }, { consultationId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.updateDateRange = ['2026-10-03', '2026-10-04']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.updateDateRange).toEqual([])
    expect(wrapper.vm.queryParams.ratingRange).toEqual([0, 5])
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.userId).toBeUndefined()
    expect(wrapper.vm.queryParams.categoryId).toBeUndefined()
    expect(wrapper.vm.queryParams.questionKeyword).toBeUndefined()
    expect(wrapper.vm.queryParams.answerKeyword).toBeUndefined()
    expect(listConsultation).toHaveBeenCalledTimes(2)
    // 重置后评分区间恢复 [0,5] 并带入评分上下限
    expect(listConsultation.mock.calls[1][0]).toEqual(expect.objectContaining({ minRating: 0, maxRating: 5 }))
  })

  it('咨询记录：空态展示内建文案', async () => {
    listConsultation.mockResolvedValue({ rows: [], total: 0 })
    const wrapper = mountPage(Consultation, '/lawyers/consultation')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无咨询记录')
  })
})

describe('signRecord 签署域列表页 ProTable 迁移', () => {
  it('签署记录：状态标签/时间空值/操作与重置保持', async () => {
    listSignRecord.mockResolvedValue({
      rows: [
        {
          signId: 1,
          businessNo: 'BIZ20261101001',
          signTemplate: '委托协议',
          signParty: '张律师',
          signPartyPhone: '13800000000',
          signStatus: '2',
          signTime: '2026-11-01 09:00:00',
          eSignId: 'ES-1',
          certificateNo: 'CERT-1'
        },
        {
          signId: 2,
          businessNo: 'BIZ20261101002',
          signTemplate: '授权书',
          signParty: '李律师',
          signPartyPhone: '13900000000',
          signStatus: '0',
          signTime: null,
          eSignId: null,
          certificateNo: null
        }
      ],
      total: 2
    })
    const wrapper = mountPage(SignRecord, '/lawyers/signRecord')
    await flush()

    expect(listSignRecord).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      businessNo: undefined,
      signParty: undefined,
      signPartyPhone: undefined,
      signStatus: undefined
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无签署记录')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '业务流水号', '签署模板', '签署方姓名', '签署方电话', '签署状态', '签署时间', '电子签章ID', '存证编号', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual([
      '业务流水号', '签署方姓名', '签署方电话', '签署状态', '签署时间'
    ])
    expect(table.props('queryFormItems')[3].options).toHaveLength(4)

    expect(cellTextOf(wrapper, '业务流水号')).toBe('BIZ20261101001')
    expect(cellTextOf(wrapper, '签署状态')).toBe('已完成')
    expect(cellTextOf(wrapper, '签署时间')).toBe('2026-11-01 09:00:00')
    expect(cellTextOf(wrapper, '签署时间', 1)).toBe('-')
    expect(cellTextOf(wrapper, '签署状态', 1)).toBe('待签署')
    expect(wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text()))
      .toEqual(expect.arrayContaining(['详情', '修改', '删除']))
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['新增', '修改', '删除', '导出']))

    wrapper.vm.handleSelectionChange([{ signId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.queryParams.signStatus = '2'
    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.businessNo).toBeUndefined()
    expect(wrapper.vm.queryParams.signParty).toBeUndefined()
    expect(wrapper.vm.queryParams.signStatus).toBeUndefined()
    expect(listSignRecord).toHaveBeenCalledTimes(2)
  })
})

describe('skill.queue 排队域列表页 ProTable 迁移', () => {
  it('排队监控：技能组切换即查/状态标签/策略与等待时长口径保持', async () => {
    listQueue.mockResolvedValue({
      rows: [
        {
          queueId: 1,
          callerNumber: '13800000000',
          groupName: '法律咨询组',
          queueStatus: '0',
          agentName: null,
          strategyUsed: 'round_robin',
          waitDuration: 35,
          enqueueTime: '2026-11-01 09:00:00',
          dequeueTime: null
        },
        {
          queueId: 2,
          callerNumber: '13900000000',
          groupName: '法律咨询组',
          queueStatus: '1',
          agentName: '张律师',
          strategyUsed: 'manual',
          waitDuration: 10,
          enqueueTime: '2026-11-01 09:01:00',
          dequeueTime: '2026-11-01 09:01:10'
        }
      ],
      total: 2,
      data: { assigned: 1, overflow: 0, kicked: 0 }
    })
    const wrapper = mountPage(SkillQueue, '/lawyers/skill/queue')
    await flush()

    expect(listQueue).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      groupId: null,
      callerNumber: '',
      queueStatus: ''
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('showToolbar')).toBe(false)
    expect(table.props('tableAttrs')).toEqual({ border: true })
    expect(table.props('emptyText')).toBe('暂无排队数据')
    // 约定：不使用 fixed="right"
    expect(table.props('columns').every(c => !c.fixed)).toBe(true)
    expect(table.props('columns').map(c => c.label)).toEqual([
      '排队ID', '主叫号码', '技能组', '状态', '分配坐席', '策略', '等待时长', '入队时间', '出队时间', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual(['技能组', '主叫', '状态'])
    expect(table.props('queryFormItems')[2].options).toHaveLength(5)

    expect(cellTextOf(wrapper, '排队ID')).toBe('1')
    expect(cellTextOf(wrapper, '状态')).toBe('排队中')
    expect(cellTextOf(wrapper, '分配坐席')).toBe('-')
    expect(cellTextOf(wrapper, '策略')).toBe('轮询')
    expect(cellTextOf(wrapper, '等待时长')).toBe('35秒')
    expect(cellTextOf(wrapper, '状态', 1)).toBe('已分配')
    expect(cellTextOf(wrapper, '分配坐席', 1)).toBe('张律师')

    // 仅排队中的行展示手动分配/踢除
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toEqual(expect.arrayContaining(['手动分配', '踢除']))
    expect(secondButtons).not.toContain('手动分配')
    expect(wrapper.findAll('.el-form button').wrappers.map(b => b.text())).toEqual(expect.arrayContaining(['刷新']))

    // 技能组下拉保留 filterable
    expect(wrapper.vm.groupList).toHaveLength(1)
    expect(wrapper.findAll('.el-form .el-select').length).toBe(2)

    wrapper.vm.queryParams.groupId = 1
    wrapper.vm.queryParams.callerNumber = '138'
    wrapper.vm.queryParams.queueStatus = '0'
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.groupId).toBeNull()
    expect(wrapper.vm.queryParams.callerNumber).toBe('')
    expect(wrapper.vm.queryParams.queueStatus).toBe('')
    expect(listQueue).toHaveBeenCalledTimes(2)
  })

  it('排队监控：空态展示内建文案', async () => {
    listQueue.mockResolvedValue({ rows: [], total: 0, data: { assigned: 0, overflow: 0, kicked: 0 } })
    const wrapper = mountPage(SkillQueue, '/lawyers/skill/queue')
    await flush()

    expect(wrapper.find('.table-empty-stub').text()).toContain('暂无排队数据')
  })
})

describe('videoConsult 视频咨询列表页 ProTable 迁移', () => {
  it('视频咨询：状态/时长/满意度/核验与进行中加入分支保持', async () => {
    listVideoConsult.mockResolvedValue({
      rows: [
        {
          consultId: 1,
          consultNo: 'VC20261101001',
          customerName: '王先生',
          customerPhone: '13800000000',
          agentName: '张律师',
          roomNo: 'ROOM-1',
          status: '1',
          duration: 150,
          satisfaction: 0,
          identityVerify: '1',
          startTime: '2026-11-01 09:00:00'
        },
        {
          consultId: 2,
          consultNo: 'VC20261101002',
          customerName: '李女士',
          customerPhone: '13900000000',
          agentName: '李律师',
          roomNo: 'ROOM-2',
          status: '2',
          duration: 0,
          satisfaction: 5,
          identityVerify: '0',
          startTime: '2026-11-01 10:00:00'
        }
      ],
      total: 2
    })
    const wrapper = mountPage(VideoConsult, '/lawyers/videoConsult')
    await flush()

    expect(listVideoConsult).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      consultNo: undefined,
      customerName: undefined,
      customerPhone: undefined,
      status: undefined
    }))

    const table = wrapper.findComponent(ProTable)
    expect(table.props('hasSelection')).toBe(true)
    expect(table.props('emptyText')).toBe('暂无视频咨询')
    expect(table.props('columns').map(c => c.label)).toEqual([
      '咨询编号', '客户姓名', '客户电话', '坐席', '房间号', '状态', '时长', '满意度', '核验', '开始时间', '操作'
    ])
    expect(table.props('queryFormItems').map(i => i.label)).toEqual([
      '咨询编号', '客户姓名', '客户电话', '状态', '开始时间'
    ])
    expect(table.props('queryFormItems')[3].options).toHaveLength(4)

    expect(cellTextOf(wrapper, '咨询编号')).toBe('VC20261101001')
    expect(cellTextOf(wrapper, '客户姓名')).toBe('王先生')
    expect(cellTextOf(wrapper, '状态')).toBe('进行中')
    expect(cellTextOf(wrapper, '时长')).toBe('2分30秒')
    expect(cellTextOf(wrapper, '满意度')).toBe('-')
    expect(cellTextOf(wrapper, '核验')).toBe('已核验')
    expect(cellTextOf(wrapper, '开始时间')).toBe('2026-11-01 10:00')
    expect(cellTextOf(wrapper, '满意度', 1)).toBe('5分')
    expect(cellTextOf(wrapper, '时长', 1)).toBe('-')

    // 仅进行中的行展示「加入」
    const firstButtons = wrapper.findAll('.el-table__body tr').at(0).findAll('button').wrappers.map(b => b.text())
    const secondButtons = wrapper.findAll('.el-table__body tr').at(1).findAll('button').wrappers.map(b => b.text())
    expect(firstButtons).toEqual(expect.arrayContaining(['详情', '修改', '加入']))
    expect(secondButtons).not.toContain('加入')
    expect(toolbarButtonTexts(wrapper)).toEqual(expect.arrayContaining(['新增', '删除', '导出']))

    wrapper.vm.handleSelectionChange([{ consultId: 1 }, { consultId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.dateRange = ['2026-10-01', '2026-10-02']
    wrapper.vm.resetQuery()
    await flush()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(wrapper.vm.queryParams.consultNo).toBeUndefined()
    expect(wrapper.vm.queryParams.customerName).toBeUndefined()
    expect(wrapper.vm.queryParams.status).toBeUndefined()
    expect(listVideoConsult).toHaveBeenCalledTimes(2)
  })
})
