'use strict'

/**
 * 操作日志 API 单测：锁定日志列表、单条删除与清空契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/operlog'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['list', [{ pageNum: 1 }], {
    url: '/monitor/operlog/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['delOperlog', [9], { url: '/monitor/operlog/9', method: 'delete' }],
  ['cleanOperlog', [], { url: '/monitor/operlog/clean', method: 'delete' }]
]

describe('operlog 接口契约', () => {
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
