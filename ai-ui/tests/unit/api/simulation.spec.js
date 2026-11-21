'use strict'

/**
 * 仿真评测 API 单测：锁定 Copilot/机器人任务提交（纯文本体）与进度查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/simulation'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['submitCopilotSimulation', ['[{"input":"x"}]'], {
    url: '/lawyers/copilot/simulation/submit',
    method: 'post',
    headers: { 'Content-Type': 'text/plain;charset=utf-8' },
    data: '[{"input":"x"}]'
  }],
  ['submitCopilotSimulation', [], {
    url: '/lawyers/copilot/simulation/submit',
    method: 'post',
    headers: { 'Content-Type': 'text/plain;charset=utf-8' },
    data: ''
  }],
  ['getCopilotSimulationProgress', ['task-1'], {
    url: '/lawyers/copilot/simulation/progress',
    method: 'get',
    params: { taskId: 'task-1' }
  }],
  ['submitRobotSimulation', ['[{"input":"y"}]'], {
    url: '/lawyers/robot/simulation/submit',
    method: 'post',
    headers: { 'Content-Type': 'text/plain;charset=utf-8' },
    data: '[{"input":"y"}]'
  }],
  ['getRobotSimulationProgress', ['task-2'], {
    url: '/lawyers/robot/simulation/progress',
    method: 'get',
    params: { taskId: 'task-2' }
  }]
]

describe('simulation 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test.each(cases)('%s 按约定调用 request', async (name, args, expected) => {
    const result = await api[name](...args)

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith(expected)
    expect(result).toEqual({ code: 200 })
  })

  test('导出函数与用例表一一对应', () => {
    const covered = [...new Set(cases.map(item => item[0]))].sort()
    expect(Object.keys(api).sort()).toEqual(covered)
  })
})
