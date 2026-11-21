'use strict'

/**
 * 视频咨询 API 单测：锁定咨询 CRUD 与日志查询/新增契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/videoConsult'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listVideoConsult', [{ pageNum: 1 }], {
    url: '/lawyers/videoConsult/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getVideoConsult', [5], { url: '/lawyers/videoConsult/5', method: 'get' }],
  ['addVideoConsult', [{ userId: 2 }], {
    url: '/lawyers/videoConsult',
    method: 'post',
    data: { userId: 2 }
  }],
  ['updateVideoConsult', [{ consultId: 5 }], {
    url: '/lawyers/videoConsult',
    method: 'put',
    data: { consultId: 5 }
  }],
  ['delVideoConsult', ['5,6'], { url: '/lawyers/videoConsult/5,6', method: 'delete' }],
  ['getVideoConsultLogs', [5], { url: '/lawyers/videoConsult/log/5', method: 'get' }],
  ['addVideoConsultLog', [{ consultId: 5 }], {
    url: '/lawyers/videoConsult/log',
    method: 'post',
    data: { consultId: 5 }
  }]
]

describe('videoConsult 接口契约', () => {
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
