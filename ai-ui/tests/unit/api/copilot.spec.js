'use strict'

/**
 * Copilot 实时辅助 API 单测：锁定法条溯源、反馈埋点、采纳率与代执行契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/copilot'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getCopilotChunk', ['c-1'], { url: '/lawyers/copilot/chunk/c-1', method: 'get' }],
  ['copilotFeedback', [{ action: 'ADOPT' }], {
    url: '/lawyers/copilot/feedback',
    method: 'post',
    data: { action: 'ADOPT' }
  }],
  ['getCopilotAdoption', [{ dimension: 'agent' }], {
    url: '/lawyers/copilot/adoption',
    method: 'get',
    params: { dimension: 'agent' }
  }],
  ['createCopilotTicket', [{ title: '工伤咨询' }], {
    url: '/lawyers/copilot/actions/createTicket',
    method: 'post',
    data: { title: '工伤咨询' }
  }],
  ['queryCopilotTicket', [66], {
    url: '/lawyers/copilot/actions/queryTicket/66',
    method: 'get'
  }]
]

describe('copilot 接口契约', () => {
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
