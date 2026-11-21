'use strict'

/**
 * 回访任务 API 单测：锁定任务 CRUD 与统计契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/returnVisitTask'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listReturnVisitTask', [{ pageNum: 1 }], {
    url: '/lawyers/returnVisitTask/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getReturnVisitTask', [14], { url: '/lawyers/returnVisitTask/14', method: 'get' }],
  ['addReturnVisitTask', [{ taskName: '结案回访' }], {
    url: '/lawyers/returnVisitTask',
    method: 'post',
    data: { taskName: '结案回访' }
  }],
  ['updateReturnVisitTask', [{ taskId: 14 }], {
    url: '/lawyers/returnVisitTask',
    method: 'put',
    data: { taskId: 14 }
  }],
  ['delReturnVisitTask', ['14,15'], {
    url: '/lawyers/returnVisitTask/14,15',
    method: 'delete'
  }],
  ['getReturnVisitTaskStats', [], { url: '/lawyers/returnVisitTask/stats', method: 'get' }]
]

describe('returnVisitTask 接口契约', () => {
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
