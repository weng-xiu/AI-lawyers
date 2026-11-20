'use strict'

/**
 * 通话转接记录 API 单测：锁定转接记录查询、新增、删除与导出契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/callTransfer'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listTransfer', [{ pageNum: 1 }], {
    url: '/lawyers/call/transfer/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getTransfer', [5], { url: '/lawyers/call/transfer/5', method: 'get' }],
  ['addTransfer', [{ recordId: 88 }], {
    url: '/lawyers/call/transfer',
    method: 'post',
    data: { recordId: 88 }
  }],
  ['delTransfer', ['5,6'], { url: '/lawyers/call/transfer/5,6', method: 'delete' }],
  ['getTransfersByRecordId', [88], {
    url: '/lawyers/call/transfer/record/88',
    method: 'get'
  }],
  ['exportTransfer', [{ recordId: 88 }], {
    url: '/lawyers/call/transfer/export',
    method: 'post',
    params: { recordId: 88 }
  }]
]

describe('callTransfer 接口契约', () => {
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
