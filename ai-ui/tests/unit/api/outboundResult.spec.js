'use strict'

/**
 * 外呼结果 API 单测：锁定结果查询、按任务/名单查询、统计与新增契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/outboundResult'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listResult', [{ taskId: 6 }], {
    url: '/lawyers/outbound/result/list',
    method: 'get',
    params: { taskId: 6 }
  }],
  ['getResult', [61], { url: '/lawyers/outbound/result/61', method: 'get' }],
  ['getResultsByTaskId', [6], { url: '/lawyers/outbound/result/task/6', method: 'get' }],
  ['getResultByCalleeId', [51], { url: '/lawyers/outbound/result/callee/51', method: 'get' }],
  ['getTaskResultStatistics', [6], {
    url: '/lawyers/outbound/result/statistics/6',
    method: 'get'
  }],
  ['addResult', [{ taskId: 6, callStatus: 'ANSWERED' }], {
    url: '/lawyers/outbound/result',
    method: 'post',
    data: { taskId: 6, callStatus: 'ANSWERED' }
  }]
]

describe('outboundResult 接口契约', () => {
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
