'use strict'

/**
 * 运营统计 API 单测：锁定大屏聚合与坐席效能报表契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/stat'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['dashboardData', [{ days: 7 }], {
    url: '/lawyers/dashboard/data',
    method: 'get',
    params: { days: 7 }
  }],
  ['listAgentPerformance', [{ startDate: '2026-01-01' }], {
    url: '/lawyers/performance/agent/list',
    method: 'get',
    params: { startDate: '2026-01-01' }
  }]
]

describe('stat 接口契约', () => {
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
