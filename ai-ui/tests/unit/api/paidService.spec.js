'use strict'

/**
 * 有偿法律服务 API 单测：锁定服务 CRUD 与导出契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/paidService'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listPaidService', [{ pageNum: 1 }], {
    url: '/lawyers/paidLegalService/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getPaidService', [9], { url: '/lawyers/paidLegalService/9', method: 'get' }],
  ['addPaidService', [{ serviceName: '合同审查' }], {
    url: '/lawyers/paidLegalService',
    method: 'post',
    data: { serviceName: '合同审查' }
  }],
  ['updatePaidService', [{ paidId: 9 }], {
    url: '/lawyers/paidLegalService',
    method: 'put',
    data: { paidId: 9 }
  }],
  ['delPaidService', [9], { url: '/lawyers/paidLegalService/9', method: 'delete' }],
  ['exportPaidService', [{ serviceName: '合同审查' }], {
    url: '/lawyers/paidLegalService/export',
    method: 'post',
    params: { serviceName: '合同审查' }
  }]
]

describe('paidService 接口契约', () => {
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
