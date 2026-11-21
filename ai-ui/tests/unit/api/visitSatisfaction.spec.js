'use strict'

/**
 * 回访满意度看板 API 单测：锁定 F5 归因分析聚合接口的请求契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/visitSatisfaction'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['visitSatisfactionBoard', [{ days: 7 }], { url: '/lawyers/visit/satisfaction/board', method: 'get', params: { days: 7 } }],
  ['visitSatisfactionBoard', [], { url: '/lawyers/visit/satisfaction/board', method: 'get', params: undefined }]
]

describe('lawyers/visitSatisfaction 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test.each(cases)('visitSatisfactionBoard 按约定调用 request', async (name, args, expected) => {
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
