'use strict'

/**
 * 签署记录 API 单测：锁定记录 CRUD 与 blob 导出契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/signRecord'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listSignRecord', [{ pageNum: 1 }], {
    url: '/lawyers/signRecord/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getSignRecord', [17], { url: '/lawyers/signRecord/17', method: 'get' }],
  ['addSignRecord', [{ signType: '电子签' }], {
    url: '/lawyers/signRecord',
    method: 'post',
    data: { signType: '电子签' }
  }],
  ['updateSignRecord', [{ signId: 17 }], {
    url: '/lawyers/signRecord',
    method: 'put',
    data: { signId: 17 }
  }],
  ['delSignRecord', ['17,18'], { url: '/lawyers/signRecord/17,18', method: 'delete' }],
  ['exportSignRecord', [{ signType: '电子签' }], {
    url: '/lawyers/signRecord/export',
    method: 'get',
    params: { signType: '电子签' },
    responseType: 'blob'
  }]
]

describe('signRecord 接口契约', () => {
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
