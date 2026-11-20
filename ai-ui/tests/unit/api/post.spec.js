'use strict'

/**
 * 岗位管理 API 单测：锁定岗位 CRUD 接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/post'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listPost', [{ pageNum: 1 }], { url: '/system/post/list', method: 'get', params: { pageNum: 1 } }],
  ['getPost', [2], { url: '/system/post/2', method: 'get' }],
  ['addPost', [{ postName: '法务' }], {
    url: '/system/post',
    method: 'post',
    data: { postName: '法务' }
  }],
  ['updatePost', [{ postId: 2 }], {
    url: '/system/post',
    method: 'put',
    data: { postId: 2 }
  }],
  ['delPost', [2], { url: '/system/post/2', method: 'delete' }]
]

describe('post 接口契约', () => {
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
