'use strict'

/**
 * 咨询分类（树形）API 单测：锁定列表/树查询、CRUD 与 GET 导出契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/consultationCategory'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listConsultationCategory', [{ pageNum: 1 }], {
    url: '/lawyers/category/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['listConsultationCategoryTree', [{ status: '0' }], {
    url: '/lawyers/category/tree',
    method: 'get',
    params: { status: '0' }
  }],
  ['getConsultationCategory', [3], { url: '/lawyers/category/3', method: 'get' }],
  ['addConsultationCategory', [{ categoryName: '劳动纠纷' }], {
    url: '/lawyers/category',
    method: 'post',
    data: { categoryName: '劳动纠纷' }
  }],
  ['updateConsultationCategory', [{ categoryId: 3 }], {
    url: '/lawyers/category',
    method: 'put',
    data: { categoryId: 3 }
  }],
  ['delConsultationCategory', [3], { url: '/lawyers/category/3', method: 'delete' }],
  ['exportConsultationCategory', [{ categoryName: '劳动纠纷' }], {
    url: '/lawyers/category/export',
    method: 'get',
    params: { categoryName: '劳动纠纷' }
  }]
]

describe('consultationCategory 接口契约', () => {
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
