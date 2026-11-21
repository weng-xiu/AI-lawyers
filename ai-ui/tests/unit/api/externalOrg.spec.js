'use strict'

/**
 * 协同机构台账 API 单测：锁定机构 CRUD 接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/externalOrg'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listExternalOrg', [{ externalType: 'HOSPITAL' }], {
    url: '/lawyers/external/org/list',
    method: 'get',
    params: { externalType: 'HOSPITAL' }
  }],
  ['getExternalOrg', [6], { url: '/lawyers/external/org/6', method: 'get' }],
  ['addExternalOrg', [{ orgName: '第一医院' }], {
    url: '/lawyers/external/org',
    method: 'post',
    data: { orgName: '第一医院' }
  }],
  ['updateExternalOrg', [{ orgId: 6 }], {
    url: '/lawyers/external/org',
    method: 'put',
    data: { orgId: 6 }
  }],
  ['delExternalOrg', ['6,7'], { url: '/lawyers/external/org/6,7', method: 'delete' }]
]

describe('externalOrg 接口契约', () => {
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
