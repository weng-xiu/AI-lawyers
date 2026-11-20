'use strict'

/**
 * 来电弹屏 API 单测：锁定客户画像、历史、工单、轨迹与资料更新契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/callPopup'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getCallerProfile', ['13800000000'], {
    url: '/lawyers/call/popup/profile/13800000000',
    method: 'get'
  }],
  ['getCallerHistory', ['13800000000', 5], {
    url: '/lawyers/call/popup/history/13800000000',
    method: 'get',
    params: { limit: 5 }
  }],
  ['getCallerTickets', ['13800000000'], {
    url: '/lawyers/call/popup/tickets/13800000000',
    method: 'get'
  }],
  ['getCallerTrack', ['13800000000'], {
    url: '/lawyers/call/popup/track/13800000000',
    method: 'get'
  }],
  ['updateCallerProfile', [{ profileId: 1 }], {
    url: '/lawyers/call/popup/profile',
    method: 'put',
    data: { profileId: 1 }
  }]
]

describe('callPopup 接口契约', () => {
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
