'use strict'

/**
 * 部门管理 API 单测：锁定部门 CRUD 与排除子节点查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/dept'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listDept', [{ deptName: '法务部' }], {
    url: '/system/dept/list',
    method: 'get',
    params: { deptName: '法务部' }
  }],
  ['listDeptExcludeChild', [100], {
    url: '/system/dept/list/exclude/100',
    method: 'get'
  }],
  ['getDept', [100], { url: '/system/dept/100', method: 'get' }],
  ['addDept', [{ deptName: '法务部' }], {
    url: '/system/dept',
    method: 'post',
    data: { deptName: '法务部' }
  }],
  ['updateDept', [{ deptId: 100 }], {
    url: '/system/dept',
    method: 'put',
    data: { deptId: 100 }
  }],
  ['delDept', [100], { url: '/system/dept/100', method: 'delete' }]
]

describe('dept 接口契约', () => {
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
