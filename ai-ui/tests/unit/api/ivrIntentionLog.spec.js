'use strict'

/**
 * 意图命中日志 API 单测：锁定日志列表、详情、按话单查询与新增契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrIntentionLog'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listIntentionLog', [{ pageNum: 1 }], {
    url: '/lawyers/ivr/intentionLog/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getIntentionLog', [41], { url: '/lawyers/ivr/intentionLog/41', method: 'get' }],
  ['getIntentionLogByRecordId', [88], {
    url: '/lawyers/ivr/intentionLog/record/88',
    method: 'get'
  }],
  ['addIntentionLog', [{ recordId: 88 }], {
    url: '/lawyers/ivr/intentionLog',
    method: 'post',
    data: { recordId: 88 }
  }]
]

describe('ivrIntentionLog 接口契约', () => {
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
