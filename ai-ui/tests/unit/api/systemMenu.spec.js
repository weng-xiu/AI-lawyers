'use strict'

/**
 * 菜单管理 API 单测：锁定菜单 CRUD 与下拉树查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/menu'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listMenu', [{ menuName: '系统管理' }], {
    url: '/system/menu/list',
    method: 'get',
    params: { menuName: '系统管理' }
  }],
  ['getMenu', [1], { url: '/system/menu/1', method: 'get' }],
  ['treeselect', [], { url: '/system/menu/treeselect', method: 'get' }],
  ['roleMenuTreeselect', [2], {
    url: '/system/menu/roleMenuTreeselect/2',
    method: 'get'
  }],
  ['addMenu', [{ menuName: '风险预警' }], {
    url: '/system/menu',
    method: 'post',
    data: { menuName: '风险预警' }
  }],
  ['updateMenu', [{ menuId: 1 }], {
    url: '/system/menu',
    method: 'put',
    data: { menuId: 1 }
  }],
  ['delMenu', [1], { url: '/system/menu/1', method: 'delete' }]
]

describe('menu 接口契约', () => {
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
