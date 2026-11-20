'use strict'

/**
 * 咨询分类 API 单测：锁定分类 CRUD、有效分类查询与导出接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/category'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listCategory', [{ pageNum: 1 }], {
    url: '/lawyers/category/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getCategory', [3], { url: '/lawyers/category/3', method: 'get' }],
  ['getValidCategories', [], { url: '/lawyers/category/valid', method: 'get' }],
  ['addCategory', [{ categoryName: '婚姻家事' }], {
    url: '/lawyers/category',
    method: 'post',
    data: { categoryName: '婚姻家事' }
  }],
  ['updateCategory', [{ categoryId: 3 }], {
    url: '/lawyers/category',
    method: 'put',
    data: { categoryId: 3 }
  }],
  ['delCategory', [3], { url: '/lawyers/category/3', method: 'delete' }],
  ['exportCategory', [{ categoryName: '婚姻家事' }], {
    url: '/lawyers/category/export',
    method: 'post',
    params: { categoryName: '婚姻家事' }
  }]
]

describe('category 接口契约', () => {
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
