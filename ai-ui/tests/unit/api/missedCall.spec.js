'use strict'

/**
 * 未接来电 API 单测：锁定 CRUD、统计、回拨标记与语音留言处理接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/missedCall'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listMissedCall', [{ pageNum: 1 }], {
    url: '/lawyers/call/missed/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getMissedCall', [8], { url: '/lawyers/call/missed/8', method: 'get' }],
  ['addMissedCall', [{ phone: '13800000000' }], {
    url: '/lawyers/call/missed',
    method: 'post',
    data: { phone: '13800000000' }
  }],
  ['updateMissedCall', [{ missedCallId: 8 }], {
    url: '/lawyers/call/missed',
    method: 'put',
    data: { missedCallId: 8 }
  }],
  ['delMissedCall', ['8,9'], { url: '/lawyers/call/missed/8,9', method: 'delete' }],
  ['getMissedCallStats', [], { url: '/lawyers/call/missed/stats', method: 'get' }],
  ['callbackMissedCall', [8], { url: '/lawyers/call/missed/callback/8', method: 'put' }],
  ['fetchVoiceBlob', [8], {
    url: '/lawyers/call/missed/8/voice/play',
    method: 'get',
    responseType: 'blob'
  }],
  ['transferVoiceToTicket', [8], {
    url: '/lawyers/call/missed/8/transfer',
    method: 'post'
  }]
]

describe('missedCall 接口契约', () => {
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
