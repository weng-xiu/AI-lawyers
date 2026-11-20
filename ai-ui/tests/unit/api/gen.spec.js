'use strict'

/**
 * 代码生成 API 单测：锁定表查询、导入、预览、生成与同步接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/tool/gen'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listTable', [{ pageNum: 1 }], { url: '/tool/gen/list', method: 'get', params: { pageNum: 1 } }],
  ['listDbTable', [{ tableName: 'sys' }], { url: '/tool/gen/db/list', method: 'get', params: { tableName: 'sys' } }],
  ['getGenTable', [5], { url: '/tool/gen/5', method: 'get' }],
  ['updateGenTable', [{ tableId: 5 }], { url: '/tool/gen', method: 'put', data: { tableId: 5 } }],
  ['importTable', [{ tables: 'sys_user' }], {
    url: '/tool/gen/importTable',
    method: 'post',
    params: { tables: 'sys_user' }
  }],
  ['createTable', [{ sql: 'create table t' }], {
    url: '/tool/gen/createTable',
    method: 'post',
    params: { sql: 'create table t' }
  }],
  ['previewTable', [5], { url: '/tool/gen/preview/5', method: 'get' }],
  ['delTable', [5], { url: '/tool/gen/5', method: 'delete' }],
  ['genCode', ['sys_user'], { url: '/tool/gen/genCode/sys_user', method: 'get' }],
  ['synchDb', ['sys_user'], { url: '/tool/gen/synchDb/sys_user', method: 'get' }]
]

describe('gen 接口契约', () => {
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
