'use strict'

/**
 * 外呼名单 API 单测：锁定名单 CRUD、按任务查询/删除与批量导入契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/outboundCallee'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listCallee', [{ taskId: 6 }], {
    url: '/lawyers/outbound/callee/list',
    method: 'get',
    params: { taskId: 6 }
  }],
  ['getCallee', [51], { url: '/lawyers/outbound/callee/51', method: 'get' }],
  ['getCalleesByTaskId', [6], { url: '/lawyers/outbound/callee/task/6', method: 'get' }],
  ['addCallee', [{ phone: '13800000000' }], {
    url: '/lawyers/outbound/callee',
    method: 'post',
    data: { phone: '13800000000' }
  }],
  ['updateCallee', [{ calleeId: 51 }], {
    url: '/lawyers/outbound/callee',
    method: 'put',
    data: { calleeId: 51 }
  }],
  ['delCallee', ['51,52'], { url: '/lawyers/outbound/callee/51,52', method: 'delete' }],
  ['delCalleeByTaskId', [6], { url: '/lawyers/outbound/callee/task/6', method: 'delete' }],
  ['batchAddCallee', [6, [{ phone: '13800000001' }]], {
    url: '/lawyers/outbound/callee/batch/6',
    method: 'post',
    data: [{ phone: '13800000001' }]
  }]
]

describe('outboundCallee 接口契约', () => {
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
