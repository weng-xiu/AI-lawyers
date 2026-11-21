'use strict'

/**
 * 模型成本 API 单测：锁定概览、趋势、维度分布与调用明细契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/modelCost'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getModelCostOverview', [{ startDate: '2026-01-01' }], {
    url: '/lawyers/modelCost/overview',
    method: 'get',
    params: { startDate: '2026-01-01' }
  }],
  ['getModelCostTrend', [{ days: 7 }], {
    url: '/lawyers/modelCost/trend',
    method: 'get',
    params: { days: 7 }
  }],
  ['getModelCostByModel', [{ days: 7 }], {
    url: '/lawyers/modelCost/byModel',
    method: 'get',
    params: { days: 7 }
  }],
  ['getModelCostByScene', [{ days: 7 }], {
    url: '/lawyers/modelCost/byScene',
    method: 'get',
    params: { days: 7 }
  }],
  ['listModelCallLog', [{ pageNum: 1 }], {
    url: '/lawyers/modelCost/logList',
    method: 'get',
    params: { pageNum: 1 }
  }]
]

describe('modelCost 接口契约', () => {
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
