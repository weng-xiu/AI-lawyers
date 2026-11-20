'use strict'

/**
 * 登录日志 API 单测：锁定日志列表、删除、账号解锁与清空契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/logininfor'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['list', [{ pageNum: 1 }], {
    url: '/monitor/logininfor/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['delLogininfor', [6], { url: '/monitor/logininfor/6', method: 'delete' }],
  ['unlockLogininfor', ['admin'], {
    url: '/monitor/logininfor/unlock/admin',
    method: 'get'
  }],
  ['cleanLogininfor', [], { url: '/monitor/logininfor/clean', method: 'delete' }]
]

describe('logininfor 接口契约', () => {
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
