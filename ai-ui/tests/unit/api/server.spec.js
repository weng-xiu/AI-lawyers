'use strict'

/**
 * 服务监控 API 单测：锁定服务信息查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/server'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

describe('server 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test('getServer 以 GET 请求服务信息', async () => {
    const result = await api.getServer()

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith({ url: '/monitor/server', method: 'get' })
    expect(result).toEqual({ code: 200 })
  })

  test('仅导出 getServer', () => {
    expect(Object.keys(api)).toEqual(['getServer'])
  })
})
