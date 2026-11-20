'use strict'

/**
 * 调度日志 API 单测：锁定日志列表、单条删除与清空契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/jobLog'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listJobLog', [{ pageNum: 1 }], {
    url: '/monitor/jobLog/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['delJobLog', [7], { url: '/monitor/jobLog/7', method: 'delete' }],
  ['cleanJobLog', [], { url: '/monitor/jobLog/clean', method: 'delete' }]
]

describe('jobLog 接口契约', () => {
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
