'use strict'

/**
 * 工单转办 API 单测：锁定转办流水查询、发起、重试与咨询时间线契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ticketTransfer'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listTransfer', [{ pageNum: 1 }], {
    url: '/lawyers/ticket/transfer/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getTransfer', [23], { url: '/lawyers/ticket/transfer/23', method: 'get' }],
  ['createTransfer', [{ ticketId: 66 }], {
    url: '/lawyers/ticket/transfer',
    method: 'post',
    data: { ticketId: 66 }
  }],
  ['retryTransfer', [23], {
    url: '/lawyers/ticket/transfer/retry/23',
    method: 'post'
  }],
  ['callerTimeline', [{ phone: '13800000000' }], {
    url: '/lawyers/channel/timeline',
    method: 'get',
    params: { phone: '13800000000' }
  }]
]

describe('ticketTransfer 接口契约', () => {
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
