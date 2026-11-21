'use strict'

/**
 * 站内消息 API 单测：锁定列表、未读数、详情、已读与删除契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/message'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listMessage', [{ pageNum: 1 }], {
    url: '/lawyers/message/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['unreadCount', [], { url: '/lawyers/message/unreadCount', method: 'get' }],
  ['getMessage', [12], { url: '/lawyers/message/12', method: 'get' }],
  ['readMessage', [12], { url: '/lawyers/message/read/12', method: 'put' }],
  ['readAllMessage', [], { url: '/lawyers/message/readAll', method: 'put' }],
  ['delMessage', ['12,13'], { url: '/lawyers/message/12,13', method: 'delete' }]
]

describe('message 接口契约', () => {
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
