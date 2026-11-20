'use strict'

/**
 * 字典数据 API 单测：锁定字典数据 CRUD 与按类型查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/dict/data'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listData', [{ dictType: 'sys_yes_no' }], {
    url: '/system/dict/data/list',
    method: 'get',
    params: { dictType: 'sys_yes_no' }
  }],
  ['getData', [10], { url: '/system/dict/data/10', method: 'get' }],
  ['getDicts', ['sys_yes_no'], {
    url: '/system/dict/data/type/sys_yes_no',
    method: 'get'
  }],
  ['addData', [{ dictLabel: '是' }], {
    url: '/system/dict/data',
    method: 'post',
    data: { dictLabel: '是' }
  }],
  ['updateData', [{ dictCode: 10 }], {
    url: '/system/dict/data',
    method: 'put',
    data: { dictCode: 10 }
  }],
  ['delData', [10], { url: '/system/dict/data/10', method: 'delete' }]
]

describe('dict data 接口契约', () => {
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
