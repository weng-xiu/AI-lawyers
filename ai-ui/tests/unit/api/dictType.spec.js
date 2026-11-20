'use strict'

/**
 * 字典类型 API 单测：锁定类型 CRUD、刷新缓存与下拉选项契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/dict/type'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listType', [{ dictName: '是否' }], {
    url: '/system/dict/type/list',
    method: 'get',
    params: { dictName: '是否' }
  }],
  ['getType', [5], { url: '/system/dict/type/5', method: 'get' }],
  ['addType', [{ dictType: 'law_type' }], {
    url: '/system/dict/type',
    method: 'post',
    data: { dictType: 'law_type' }
  }],
  ['updateType', [{ dictId: 5 }], {
    url: '/system/dict/type',
    method: 'put',
    data: { dictId: 5 }
  }],
  ['delType', [5], { url: '/system/dict/type/5', method: 'delete' }],
  ['refreshCache', [], { url: '/system/dict/type/refreshCache', method: 'delete' }],
  ['optionselect', [], { url: '/system/dict/type/optionselect', method: 'get' }]
]

describe('dict type 接口契约', () => {
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
