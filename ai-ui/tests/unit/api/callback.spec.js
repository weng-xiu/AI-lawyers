'use strict'

/**
 * 客户回访 API 单测：锁定回访 CRUD、统计与满意度趋势接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/callback'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listCallback', [{ pageNum: 1 }], {
    url: '/lawyers/callback/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getCallback', [4], { url: '/lawyers/callback/4', method: 'get' }],
  ['addCallback', [{ satisfied: 5 }], {
    url: '/lawyers/callback',
    method: 'post',
    data: { satisfied: 5 }
  }],
  ['updateCallback', [{ callbackId: 4 }], {
    url: '/lawyers/callback',
    method: 'put',
    data: { callbackId: 4 }
  }],
  ['delCallback', ['4,5'], { url: '/lawyers/callback/4,5', method: 'delete' }],
  ['getCallbackStats', [], { url: '/lawyers/callback/stats', method: 'get' }],
  ['getSatisfactionTrend', [7], {
    url: '/lawyers/callback/satisfactionTrend',
    method: 'get',
    params: { days: 7 }
  }]
]

describe('callback 接口契约', () => {
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
