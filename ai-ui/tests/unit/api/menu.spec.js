'use strict'

/**
 * 路由菜单 API 单测：锁定 getRouters 请求契约。
 */
import request from '@/utils/request'
import { getRouters } from '@/api/menu'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

describe('menu 路由接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test('getRouters 以 GET 请求当前用户路由', async () => {
    const result = await getRouters()

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith({ url: '/getRouters', method: 'get' })
    expect(result).toEqual({ code: 200 })
  })
})
