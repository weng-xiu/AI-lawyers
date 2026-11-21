'use strict'

/**
 * 坐席监控单测：锁定列表加载、状态映射、详情查看与强制下线确认流程。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import CallAgent from '@/views/lawyers/callCenter/callAgent.vue'
import { listAgent, getAgent, agentLogout } from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn(),
  getAgent: jest.fn(),
  agentLogout: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})

const parseTime = jest.fn(() => '2026-11-01 10:00')
localVue.prototype.parseTime = parseTime
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { agentId: 101, agentName: '张律师', userId: 1, userName: 'zhang', nickName: '张律师', deptName: '法务部', phonenumber: '13800000000', sipExtension: '8001', status: '1', callStatus: '1', callMode: '1', loginTime: '2026-11-01 09:00' },
  { agentId: 102, agentName: '李律师', userId: null, status: '0', callStatus: '0', callMode: '0' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const push = jest.fn()
  const wrapper = mount(CallAgent, {
    localVue,
    mocks: { $modal: modal, $router: { push } }
  })
  return { wrapper, modal, push }
}

describe('CallAgent 坐席监控', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listAgent.mockResolvedValue({ rows, total: 2 })
    getAgent.mockResolvedValue({ data: { ...rows[0], logoutTime: '2026-11-01 18:00', createTime: '2026-10-01' } })
    agentLogout.mockResolvedValue({})
  })

  test('创建时查询坐席列表并渲染关键字段', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listAgent).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.agentList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('张律师')
    expect(wrapper.text()).toContain('8001')
  })

  test('坐席与通话状态映射包含默认值', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.getStatusType('2')).toBe('warning')
    expect(wrapper.vm.getStatusType('9')).toBe('info')
    expect(wrapper.vm.getStatusLabel('0')).toBe('离线')
    expect(wrapper.vm.getStatusLabel('3')).toBe('休息')
    expect(wrapper.vm.getStatusLabel('9')).toBe('未知')

    expect(wrapper.vm.getCallStatusType('4')).toBe('success')
    expect(wrapper.vm.getCallStatusType('9')).toBe('info')
    expect(wrapper.vm.getCallStatusLabel('1')).toBe('通话中')
    expect(wrapper.vm.getCallStatusLabel('5')).toBe('话后整理')
    expect(wrapper.vm.getCallStatusLabel('9')).toBe('空闲')
  })

  test('列 formatter 输出应答模式与登录时间', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.columns[8].formatter({ callMode: '1' })).toBe('手动')
    expect(wrapper.vm.columns[8].formatter({ callMode: '0' })).toBe('自动')
    expect(wrapper.vm.columns[9].formatter(rows[0])).toBe('2026-11-01 10:00')
    expect(parseTime).toHaveBeenCalledWith('2026-11-01 09:00')
  })

  test('查询重置页码，重置复用查询逻辑', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 4

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listAgent).toHaveBeenCalledTimes(2)

    wrapper.vm.queryParams.pageNum = 6
    wrapper.vm.resetQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listAgent).toHaveBeenCalledTimes(3)
  })

  test('跳转用户管理与查看坐席详情', async () => {
    const { wrapper, push } = mountPage()
    await flush()

    wrapper.vm.goUserManage()
    expect(push).toHaveBeenCalledWith('/system/user')

    wrapper.vm.handleDetail(rows[0])
    await flush()
    expect(getAgent).toHaveBeenCalledWith(101)
    expect(wrapper.vm.detailForm.agentName).toBe('张律师')
    expect(wrapper.vm.detailOpen).toBe(true)
  })

  test('强制下线确认后调用接口并刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleLogout(rows[0])
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '将强制坐席 "张律师" 退出签入状态',
      count: 1,
      countNoun: '名坐席'
    }))
    expect(agentLogout).toHaveBeenCalledWith({ agentId: 101 })
    expect(listAgent).toHaveBeenCalledTimes(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('操作成功')
  })

  test('取消强制下线时不调用接口', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.handleLogout(rows[0])
    await flush()

    expect(agentLogout).not.toHaveBeenCalled()
    expect(modal.msgSuccess).not.toHaveBeenCalled()
  })
})
