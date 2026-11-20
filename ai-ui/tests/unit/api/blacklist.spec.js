'use strict'

/**
 * 黑白名单 API 单测：锁定名单 CRUD 与号码检查契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/blacklist'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listBlacklist', [{ pageNum: 1 }], {
    url: '/lawyers/blacklist/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getBlacklist', [2], { url: '/lawyers/blacklist/2', method: 'get' }],
  ['addBlacklist', [{ phone: '13800000000' }], {
    url: '/lawyers/blacklist',
    method: 'post',
    data: { phone: '13800000000' }
  }],
  ['updateBlacklist', [{ id: 2 }], {
    url: '/lawyers/blacklist',
    method: 'put',
    data: { id: 2 }
  }],
  ['delBlacklist', ['2,3'], { url: '/lawyers/blacklist/2,3', method: 'delete' }],
  ['checkPhone', ['13800000000'], {
    url: '/lawyers/blacklist/check/13800000000',
    method: 'get'
  }]
]

describe('blacklist 接口契约', () => {
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
