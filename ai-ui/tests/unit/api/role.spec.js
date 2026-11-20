'use strict'

/**
 * 系统角色 API 单测：锁定角色 CRUD、数据权限、状态变更与授权用户接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/role'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listRole', [{ pageNum: 1 }], { url: '/system/role/list', method: 'get', params: { pageNum: 1 } }],
  ['getRole', [3], { url: '/system/role/3', method: 'get' }],
  ['addRole', [{ roleId: 1 }], { url: '/system/role', method: 'post', data: { roleId: 1 } }],
  ['updateRole', [{ roleId: 1 }], { url: '/system/role', method: 'put', data: { roleId: 1 } }],
  ['dataScope', [{ roleId: 1, dataScope: '1' }], { url: '/system/role/dataScope', method: 'put', data: { roleId: 1, dataScope: '1' } }],
  ['changeRoleStatus', [3, '0'], { url: '/system/role/changeStatus', method: 'put', data: { roleId: 3, status: '0' } }],
  ['delRole', ['1,2'], { url: '/system/role/1,2', method: 'delete' }],
  ['allocatedUserList', [{ roleId: 1 }], { url: '/system/role/authUser/allocatedList', method: 'get', params: { roleId: 1 } }],
  ['unallocatedUserList', [{ roleId: 1 }], { url: '/system/role/authUser/unallocatedList', method: 'get', params: { roleId: 1 } }],
  ['authUserCancel', [{ userId: 1 }], { url: '/system/role/authUser/cancel', method: 'put', data: { userId: 1 } }],
  ['authUserCancelAll', [{ roleId: 1, userIds: '1,2' }], {
    url: '/system/role/authUser/cancelAll',
    method: 'put',
    params: { roleId: 1, userIds: '1,2' }
  }],
  ['authUserSelectAll', [{ roleId: 1, userIds: '1,2' }], {
    url: '/system/role/authUser/selectAll',
    method: 'put',
    params: { roleId: 1, userIds: '1,2' }
  }],
  ['deptTreeSelect', [3], { url: '/system/role/deptTree/3', method: 'get' }]
]

describe('system/role 接口契约', () => {
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
    expect(Object.keys(api).sort()).toEqual(cases.map(item => item[0]).sort())
  })
})
