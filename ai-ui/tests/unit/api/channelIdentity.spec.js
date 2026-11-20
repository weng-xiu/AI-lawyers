'use strict'

/**
 * 渠道身份绑定 API 单测：锁定绑定列表、发起/确认/解绑与档案绑定查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/channelIdentity'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listChannelIdentity', [{ pageNum: 1 }], {
    url: '/lawyers/channel/identity/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['requestBind', [{ profileId: 1 }], {
    url: '/lawyers/channel/identity/requestBind',
    method: 'post',
    data: { profileId: 1 }
  }],
  ['confirmBind', [3, { code: '1234' }], {
    url: '/lawyers/channel/identity/confirm/3',
    method: 'post',
    data: { code: '1234' }
  }],
  ['confirmBind', [3], {
    url: '/lawyers/channel/identity/confirm/3',
    method: 'post',
    data: {}
  }],
  ['unbindIdentity', [3], {
    url: '/lawyers/channel/identity/unbind/3',
    method: 'post'
  }],
  ['listBoundByProfile', [9], {
    url: '/lawyers/channel/identity/bound/9',
    method: 'get'
  }]
]

describe('channelIdentity 接口契约', () => {
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
