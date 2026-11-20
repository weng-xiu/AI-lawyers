'use strict'

/**
 * 缓存监控 API 单测：锁定缓存详情、名称/键名/值查询与三类清理契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/cache'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getCache', [], { url: '/monitor/cache', method: 'get' }],
  ['listCacheName', [], { url: '/monitor/cache/getNames', method: 'get' }],
  ['listCacheKey', ['login_tokens'], {
    url: '/monitor/cache/getKeys/login_tokens',
    method: 'get'
  }],
  ['getCacheValue', ['login_tokens', 'token-1'], {
    url: '/monitor/cache/getValue/login_tokens/token-1',
    method: 'get'
  }],
  ['clearCacheName', ['login_tokens'], {
    url: '/monitor/cache/clearCacheName/login_tokens',
    method: 'delete'
  }],
  ['clearCacheKey', ['token-1'], {
    url: '/monitor/cache/clearCacheKey/token-1',
    method: 'delete'
  }],
  ['clearCacheAll', [], { url: '/monitor/cache/clearCacheAll', method: 'delete' }]
]

describe('cache 接口契约', () => {
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
