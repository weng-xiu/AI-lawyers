'use strict'

/**
 * F9 换班申请与审批单测：
 * 状态/类型文案、待审批行操作按钮、全部/我的视角切换、
 * 申请弹窗按变更类型拉取互换对象或接收坐席、提交载荷、审批与撤销走对应接口。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ShiftChange from '@/views/lawyers/schedule/shiftChange.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listShiftChange,
  listMyShiftChange,
  listMySchedules,
  listSwapTargets,
  applyShiftChange,
  approveShiftChange,
  rejectShiftChange,
  cancelShiftChange
} from '@/api/lawyers/shiftChange'
import { listAgent } from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/shiftChange', () => ({
  listShiftChange: jest.fn(),
  listMyShiftChange: jest.fn(),
  listMySchedules: jest.fn(),
  listSwapTargets: jest.fn(),
  applyShiftChange: jest.fn(),
  approveShiftChange: jest.fn(),
  rejectShiftChange: jest.fn(),
  cancelShiftChange: jest.fn()
}))

jest.mock('@/api/lawyers/callCenter', () => ({
  listAgent: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.addDateRange = addDateRange
localVue.prototype.parseTime = () => '2026-11-01 10:00'

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn(),
  dangerConfirm: jest.fn(() => Promise.resolve())
}

function requestRow(overrides = {}) {
  return Object.assign({
    requestId: 9,
    applicantAgentId: 101,
    applicantAgentName: '张律师',
    applicantScheduleId: 11,
    applicantScheduleDate: '2026-11-05',
    applicantShiftName: '早班',
    changeType: 'SWAP',
    targetAgentId: 202,
    targetAgentName: '李律师',
    targetScheduleId: 22,
    targetScheduleDate: '2026-11-05',
    targetShiftName: '晚班',
    reason: '临时调休',
    status: '0',
    createTime: '2026-11-01 09:00:00'
  }, overrides)
}

function mySchedule(overrides = {}) {
  return Object.assign({
    scheduleId: 11,
    agentId: 101,
    scheduleDate: '2026-11-05',
    shiftId: 1,
    shiftName: '早班',
    startTime: '09:00',
    endTime: '18:00',
    status: 1
  }, overrides)
}

function mountPage(rows = [requestRow()]) {
  listShiftChange.mockResolvedValue({ rows, total: rows.length })
  listMyShiftChange.mockResolvedValue({ rows, total: rows.length })
  return mount(ShiftChange, {
    localVue,
    stubs: { pagination: { template: '<div class="pagination-stub" />' } },
    mocks: { $modal: modal }
  })
}

describe('ShiftChange F9 换班申请与审批', () => {
  let wrapper

  beforeEach(() => {
    jest.clearAllMocks()
    listMySchedules.mockResolvedValue({ data: [mySchedule()] })
    listSwapTargets.mockResolvedValue({
      data: [{ scheduleId: 22, agentId: 202, agentName: '李律师', scheduleDate: '2026-11-05', shiftName: '晚班', startTime: '18:00', endTime: '23:00' }]
    })
    listAgent.mockResolvedValue({ rows: [{ agentId: 202, agentName: '李律师' }] })
    applyShiftChange.mockResolvedValue({})
    approveShiftChange.mockResolvedValue({})
    rejectShiftChange.mockResolvedValue({})
    cancelShiftChange.mockResolvedValue({})
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    wrapper = null
  })

  it('状态/类型文案与待审批判定兼容字符串与数字', async () => {
    wrapper = mountPage([
      requestRow({ requestId: 1, status: '0' }),
      requestRow({ requestId: 2, status: '1', changeType: 'TRANSFER', targetScheduleId: null, targetShiftName: null }),
      requestRow({ requestId: 3, status: '2' }),
      requestRow({ requestId: 4, status: '3' })
    ])
    await flushPromises()

    expect(wrapper.vm.statusLabel('0')).toBe('待审批')
    expect(wrapper.vm.statusLabel(1)).toBe('已通过')
    expect(wrapper.vm.statusTag('2')).toBe('danger')
    expect(wrapper.vm.typeLabel('SWAP')).toBe('互换换班')
    expect(wrapper.vm.typeLabel('TRANSFER')).toBe('转班')
    expect(wrapper.vm.isPending('0')).toBe(true)
    expect(wrapper.vm.isPending(0)).toBe(true)
    expect(wrapper.vm.isPending('1')).toBe(false)
    expect(wrapper.vm.requestList).toHaveLength(4)
  })

  it('仅待审批行展示通过/驳回/撤销按钮', async () => {
    wrapper = mountPage([requestRow({ requestId: 1, status: '0' }), requestRow({ requestId: 2, status: '1' })])
    await flushPromises()

    const texts = wrapper.findAll('.el-table__body .el-button').wrappers.map(btn => btn.text())
    expect(texts.filter(text => text.includes('通过'))).toHaveLength(1)
    expect(texts.filter(text => text.includes('驳回'))).toHaveLength(1)
    expect(texts.filter(text => text.includes('撤销'))).toHaveLength(1)
    expect(wrapper.findAll('.el-table__body .sc-sub').wrappers.map(item => item.text()).join('|')).toContain('已处理')
  })

  it('默认查看全部申请，切换「我的申请」走独立接口且不带申请人过滤', async () => {
    wrapper = mountPage()
    await flushPromises()

    expect(listShiftChange).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.buildQuery().params).toEqual({ beginTime: undefined, endTime: undefined })

    wrapper.vm.scope = 'mine'
    wrapper.vm.handleScopeChange()
    await flushPromises()

    expect(listMyShiftChange).toHaveBeenCalledTimes(1)
    expect(listMyShiftChange.mock.calls[0][0].applicantAgentId).toBeUndefined()
    expect(listShiftChange).toHaveBeenCalledTimes(1)
  })

  it('申请弹窗默认互换，选本人排班后拉取同日对方排班', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openApply()
    await flushPromises()

    expect(wrapper.vm.applyForm.changeType).toBe('SWAP')
    expect(wrapper.vm.mySchedules).toHaveLength(1)
    expect(wrapper.vm.myScheduleLabel(wrapper.vm.mySchedules[0])).toBe('2026-11-05 早班（09:00-18:00）')

    wrapper.vm.applyForm.applicantScheduleId = 11
    wrapper.vm.handleApplicantScheduleChange()
    await flushPromises()

    expect(listSwapTargets).toHaveBeenCalledWith(11)
    expect(wrapper.vm.swapTargets).toHaveLength(1)
    expect(wrapper.vm.swapTargetLabel(wrapper.vm.swapTargets[0])).toBe('李律师 晚班（18:00-23:00）')
  })

  it('切换为转班时清空互换对象并加载接收坐席', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openApply()
    wrapper.vm.applyForm.applicantScheduleId = 11
    wrapper.vm.applyForm.targetScheduleId = 22
    await flushPromises()

    wrapper.vm.applyForm.changeType = 'TRANSFER'
    wrapper.vm.handleTypeChange()
    await flushPromises()

    expect(wrapper.vm.applyForm.targetScheduleId).toBeUndefined()
    expect(wrapper.vm.agentList).toHaveLength(1)
    expect(listSwapTargets).toHaveBeenCalledTimes(0)
  })

  it('提交载荷按类型区分互换对象与接收坐席', async () => {
    wrapper = mountPage()
    wrapper.vm.openApply()
    await flushPromises()

    wrapper.vm.applyForm.applicantScheduleId = 11
    wrapper.vm.applyForm.targetScheduleId = 22
    wrapper.vm.applyForm.reason = '临时调休'
    expect(wrapper.vm.buildApplyPayload()).toEqual({
      changeType: 'SWAP',
      applicantScheduleId: 11,
      targetScheduleId: 22,
      reason: '临时调休'
    })

    wrapper.vm.applyForm.changeType = 'TRANSFER'
    wrapper.vm.applyForm.targetAgentId = 202
    expect(wrapper.vm.buildApplyPayload()).toEqual({
      changeType: 'TRANSFER',
      applicantScheduleId: 11,
      targetAgentId: 202,
      reason: '临时调休'
    })
  })

  it('提交申请调用接口并提示等待审批', async () => {
    wrapper = mountPage()
    wrapper.vm.applyOpen = true
    await wrapper.vm.$nextTick()
    wrapper.vm.applyForm = { changeType: 'SWAP', applicantScheduleId: 11, targetScheduleId: 22, reason: '临时调休' }
    // 弹窗内表单校验走 Element 异步校验，这里只关心提交载荷
    wrapper.vm.$refs.applyForm.validate = callback => callback(true)

    wrapper.vm.submitApply()
    await flushPromises()

    expect(applyShiftChange).toHaveBeenCalledWith({
      changeType: 'SWAP',
      applicantScheduleId: 11,
      targetScheduleId: 22,
      reason: '临时调休'
    })
    expect(modal.msgSuccess).toHaveBeenCalledWith('申请已提交，等待班组长审批')
    expect(wrapper.vm.applyOpen).toBe(false)
  })

  it('审批通过/驳回分别调用对应接口并携带审批意见', async () => {
    wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openApprove(requestRow({ requestId: 5 }), 'approve')
    wrapper.vm.approveForm.approveRemark = '已与双方确认'
    wrapper.vm.submitApprove()
    await flushPromises()
    expect(approveShiftChange).toHaveBeenCalledWith(5, '已与双方确认')

    wrapper.vm.openApprove(requestRow({ requestId: 6 }), 'reject')
    wrapper.vm.approveForm.approveRemark = '当日人员不足'
    wrapper.vm.submitApprove()
    await flushPromises()
    expect(rejectShiftChange).toHaveBeenCalledWith(6, '当日人员不足')
  })

  it('撤销前二次确认，确认后调用撤销接口并刷新列表', async () => {
    wrapper = mountPage()
    await flushPromises()

    await wrapper.vm.handleCancel(requestRow({ requestId: 7 }))
    await flushPromises()

    expect(modal.dangerConfirm).toHaveBeenCalled()
    expect(cancelShiftChange).toHaveBeenCalledWith(7)
    expect(modal.msgSuccess).toHaveBeenCalledWith('已撤销')
  })
})
