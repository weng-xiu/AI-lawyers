'use strict'

/**
 * IVR 执行日志 API 单测：锁定日志查询、按会话查询与新增/修改契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrExecutionLog'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listExecutionLog', [{ pageNum: 1 }], {
    url: '/lawyers/ivr/executionLog/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getExecutionLog', [31], { url: '/lawyers/ivr/executionLog/31', method: 'get' }],
  ['getExecutionLogBySessionId', ['s-1'], {
    url: '/lawyers/ivr/executionLog/session/s-1',
    method: 'get'
  }],
  ['addExecutionLog', [{ flowId: 5 }], {
    url: '/lawyers/ivr/executionLog',
    method: 'post',
    data: { flowId: 5 }
  }],
  ['updateExecutionLog', [{ execId: 31 }], {
    url: '/lawyers/ivr/executionLog',
    method: 'put',
    data: { execId: 31 }
  }]
]

describe('ivrExecutionLog 接口契约', () => {
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
