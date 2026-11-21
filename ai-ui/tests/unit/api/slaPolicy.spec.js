'use strict'

/**
 * SLA 策略 API 单测：锁定策略 CRUD 与看板聚合数据契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/slaPolicy'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listSlaPolicy', [{ pageNum: 1 }], {
    url: '/lawyers/sla/policy/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getSlaPolicy', [4], { url: '/lawyers/sla/policy/4', method: 'get' }],
  ['addSlaPolicy', [{ policyName: '首响 30 秒' }], {
    url: '/lawyers/sla/policy',
    method: 'post',
    data: { policyName: '首响 30 秒' }
  }],
  ['updateSlaPolicy', [{ policyId: 4 }], {
    url: '/lawyers/sla/policy',
    method: 'put',
    data: { policyId: 4 }
  }],
  ['delSlaPolicy', ['4,5'], { url: '/lawyers/sla/policy/4,5', method: 'delete' }],
  ['slaBoardData', [{ days: 7 }], {
    url: '/lawyers/sla/board/data',
    method: 'get',
    params: { days: 7 }
  }]
]

describe('slaPolicy 接口契约', () => {
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
