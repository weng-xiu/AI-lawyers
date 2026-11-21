'use strict'

/**
 * 命令面板聚合搜索 API 单测：锁定关键字与默认条数参数契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/palette'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

describe('palette 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test('searchPalette 默认 limit 为 5', async () => {
    const result = await api.searchPalette('工伤')

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith({
      url: '/lawyers/search/palette',
      method: 'get',
      params: { keyword: '工伤', limit: 5 }
    })
    expect(result).toEqual({ code: 200 })
  })

  test('searchPalette 支持自定义条数', () => {
    api.searchPalette('合同', 10)

    expect(request).toHaveBeenCalledWith({
      url: '/lawyers/search/palette',
      method: 'get',
      params: { keyword: '合同', limit: 10 }
    })
  })

  test('仅导出 searchPalette', () => {
    expect(Object.keys(api)).toEqual(['searchPalette'])
  })
})
