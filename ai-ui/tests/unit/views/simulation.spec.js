'use strict'

/**
 * 模型评测页单测：锁定类型切换、用例 JSON 校验、长任务轮询回调与报告指标计算。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Simulation from '@/views/lawyers/simulation/index.vue'
import {
  submitCopilotSimulation, getCopilotSimulationProgress,
  submitRobotSimulation, getRobotSimulationProgress
} from '@/api/lawyers/simulation'
import { createLongTask } from '@/utils/longTask'

jest.mock('@/api/lawyers/simulation', () => ({
  submitCopilotSimulation: jest.fn(),
  getCopilotSimulationProgress: jest.fn(),
  submitRobotSimulation: jest.fn(),
  getRobotSimulationProgress: jest.fn()
}))

jest.mock('@/utils/longTask', () => ({
  createLongTask: jest.fn(() => ({ start: jest.fn(), stop: jest.fn() }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const report = {
  casePassRate: 90,
  checkPassRate: 85,
  passedCases: 9,
  totalCases: 10,
  passedChecks: 17,
  totalChecks: 20,
  passedTurns: 18,
  totalTurns: 20,
  details: [{ caseId: 'c1', name: '咨询用例', passed: true, passedChecks: 2, totalChecks: 2, checks: [], turns: [] }]
}

function mountPage() {
  const modal = {
    msgError: jest.fn(),
    msgSuccess: jest.fn(),
    notifyTaskStart: jest.fn(),
    notifyTaskDone: jest.fn(),
    notifyTaskFail: jest.fn()
  }
  const wrapper = mount(Simulation, {
    localVue,
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('Simulation 模型评测', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    createLongTask.mockImplementation(() => ({ start: jest.fn(), stop: jest.fn() }))
    submitCopilotSimulation.mockResolvedValue({ taskId: 'task-1', msg: '后台执行中' })
    submitRobotSimulation.mockResolvedValue({ taskId: 'task-2', msg: '后台执行中' })
    getCopilotSimulationProgress.mockResolvedValue({ data: { status: 'RUNNING', percent: 40 } })
    getRobotSimulationProgress.mockResolvedValue({ data: { status: 'RUNNING', percent: 30 } })
  })

  test('切换评测类型清空结果并停止轮询', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.task = { taskId: 'task-1', status: 'RUNNING' }
    wrapper.vm.report = report
    await wrapper.vm.handleSubmit()
    const longTask = wrapper.vm.longTask

    wrapper.vm.kind = 'robot'
    wrapper.vm.handleKindChange()

    expect(wrapper.vm.task).toBeNull()
    expect(wrapper.vm.report).toBeNull()
    expect(longTask.stop).toHaveBeenCalledTimes(1)
  })

  test('用例集 JSON 非法时提示且不提交', async () => {
    const { wrapper, modal } = mountPage()
    wrapper.vm.casesJson = '{bad json'

    wrapper.vm.handleSubmit()
    await flush()

    expect(modal.msgError).toHaveBeenCalledWith(expect.stringContaining('用例集不是合法 JSON'))
    expect(submitCopilotSimulation).not.toHaveBeenCalled()
    expect(wrapper.vm.submitting).toBe(false)
  })

  test('默认用例集提交坐席辅助评测并启动轮询', async () => {
    const { wrapper, modal } = mountPage()

    wrapper.vm.handleSubmit()
    await flush()

    expect(submitCopilotSimulation).toHaveBeenCalledWith('')
    expect(wrapper.vm.task).toEqual({ taskId: 'task-1', status: 'RUNNING', percent: 0 })
    expect(modal.notifyTaskStart).toHaveBeenCalledWith('评测任务已提交', '后台执行中')
    expect(createLongTask).toHaveBeenCalledWith(expect.objectContaining({ interval: 2000 }))
    expect(wrapper.vm.longTask.start).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.submitting).toBe(false)
  })

  test('自定义用例集提交语音机器人评测', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.kind = 'robot'
    wrapper.vm.casesJson = ' {"cases":[]} '

    wrapper.vm.handleSubmit()
    await flush()

    expect(submitRobotSimulation).toHaveBeenCalledWith('{"cases":[]}')
    expect(wrapper.vm.task.taskId).toBe('task-2')
  })

  test('长任务回调更新进度、报告与终态通知', async () => {
    const { wrapper, modal } = mountPage()
    wrapper.vm.task = { taskId: 'task-1', status: 'RUNNING', percent: 0 }
    wrapper.vm.startPolling()
    const conf = createLongTask.mock.calls[0][0]

    getCopilotSimulationProgress.mockResolvedValue({ data: { status: 'RUNNING', percent: 60 } })
    await expect(conf.fetchProgress()).resolves.toEqual({ status: 'RUNNING', percent: 60 })
    expect(getCopilotSimulationProgress).toHaveBeenCalledWith('task-1')

    conf.onUpdate({ status: 'RUNNING', percent: 60 })
    expect(wrapper.vm.task.percent).toBe(60)

    conf.onUpdate({ status: 'SUCCESS', percent: 100, report })
    expect(wrapper.vm.report).toBe(report)

    conf.onDone({ report })
    expect(modal.notifyTaskDone).toHaveBeenCalledWith('坐席辅助评测完成', '用例通过率 90%（9/10）')

    conf.onFail({ errorMsg: '服务异常' })
    expect(modal.notifyTaskFail).toHaveBeenCalledWith('坐席辅助评测失败', '服务异常')

    conf.onFail({})
    expect(modal.notifyTaskFail).toHaveBeenLastCalledWith('坐席辅助评测失败', '任务执行失败，请稍后重试')
  })

  test('语音机器人轮询使用机器人进度接口', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.kind = 'robot'
    wrapper.vm.task = { taskId: 'task-2', status: 'RUNNING', percent: 0 }

    wrapper.vm.startPolling()
    const conf = createLongTask.mock.calls[0][0]
    await conf.fetchProgress()

    expect(getRobotSimulationProgress).toHaveBeenCalledWith('task-2')
    expect(getCopilotSimulationProgress).not.toHaveBeenCalled()
  })

  test('手动刷新进度与报告，无任务时忽略', async () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.loadProgress()).toBeUndefined()
    expect(getCopilotSimulationProgress).not.toHaveBeenCalled()

    wrapper.vm.task = { taskId: 'task-1' }
    getCopilotSimulationProgress.mockResolvedValue({ data: { status: 'SUCCESS', report } })
    await wrapper.vm.loadProgress()

    expect(wrapper.vm.task.status).toBe('SUCCESS')
    expect(wrapper.vm.report).toBe(report)
  })

  test('状态标签与文案映射', () => {
    const { wrapper } = mountPage()
    wrapper.vm.task = { status: 'SUCCESS' }
    expect(wrapper.vm.statusTagType).toBe('success')
    expect(wrapper.vm.statusText).toBe('已完成')

    wrapper.vm.task = { status: 'FAILED' }
    expect(wrapper.vm.statusTagType).toBe('danger')
    expect(wrapper.vm.statusText).toBe('失败')

    wrapper.vm.task = { status: 'RUNNING' }
    expect(wrapper.vm.statusTagType).toBe('warning')
    expect(wrapper.vm.statusText).toBe('执行中')

    wrapper.vm.task = { status: 'UNKNOWN' }
    expect(wrapper.vm.statusText).toBe('UNKNOWN')
  })

  test('报告概览卡按类型计算轮次通过率', () => {
    const { wrapper } = mountPage()
    wrapper.vm.task = { status: 'SUCCESS' }
    wrapper.vm.report = report

    const copilotCards = wrapper.vm.summaryCards
    expect(copilotCards[0]).toEqual({ label: '用例通过率', value: '90%', color: '#409EFF' })
    expect(copilotCards[3].value).toBe('17/20')

    wrapper.vm.kind = 'robot'
    const robotCards = wrapper.vm.summaryCards
    expect(robotCards[1].value).toBe('90%')
    expect(robotCards[3].value).toBe('18/20')
    expect(wrapper.vm.robotCaseRows).toEqual(report.details)
  })

  test('轮次统计、模式与分数格式化', () => {
    const { wrapper } = mountPage()
    wrapper.vm.report = report

    expect(wrapper.vm.turnSummary({ turns: [{ passed: true }, { passed: false }] })).toBe('1/2')
    expect(wrapper.vm.turnSummary({})).toBe('0/0')
    expect(wrapper.vm.modeText('keyword')).toBe('关键词')
    expect(wrapper.vm.modeText('semantic')).toBe('语义')
    expect(wrapper.vm.modeText('custom')).toBe('custom')
    expect(wrapper.vm.fmtScore(0.856)).toBe('0.86')
    expect(wrapper.vm.fmtScore(null)).toBe('')
    expect(wrapper.vm.fmtScore(undefined)).toBe('')
    expect(wrapper.vm.robotCaseRows).toEqual(report.details)
  })

  test('销毁时停止轮询任务', async () => {
    const { wrapper } = mountPage()
    await wrapper.vm.handleSubmit()
    const longTask = wrapper.vm.longTask

    wrapper.destroy()

    expect(longTask.stop).toHaveBeenCalledTimes(1)
  })
})
