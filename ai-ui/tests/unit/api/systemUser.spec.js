'use strict'

/**
 * 系统用户 API 单测：锁定用户 CRUD、重置密码、个人中心与授权角色接口的契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/user'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listUser', [{ pageNum: 1 }], { url: '/system/user/list', method: 'get', params: { pageNum: 1 } }],
  ['getUser', [3], { url: '/system/user/3', method: 'get' }],
  ['getUser', [undefined], { url: '/system/user/', method: 'get' }],
  ['addUser', [{ userId: 1 }], { url: '/system/user', method: 'post', data: { userId: 1 } }],
  ['updateUser', [{ userId: 1 }], { url: '/system/user', method: 'put', data: { userId: 1 } }],
  ['delUser', ['1,2'], { url: '/system/user/1,2', method: 'delete' }],
  ['resetUserPwd', [3, 'pwd'], { url: '/system/user/resetPwd', method: 'put', data: { userId: 3, password: 'pwd' } }],
  ['changeUserStatus', [3, '0'], { url: '/system/user/changeStatus', method: 'put', data: { userId: 3, status: '0' } }],
  ['getUserProfile', [], { url: '/system/user/profile', method: 'get' }],
  ['updateUserProfile', [{ nickName: 'a' }], { url: '/system/user/profile', method: 'put', data: { nickName: 'a' } }],
  ['updateUserPwd', ['old', 'new'], { url: '/system/user/profile/updatePwd', method: 'put', data: { oldPassword: 'old', newPassword: 'new' } }],
  ['uploadAvatar', [{ file: 'f' }], {
    url: '/system/user/profile/avatar',
    method: 'post',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    data: { file: 'f' }
  }],
  ['getAuthRole', [3], { url: '/system/user/authRole/3', method: 'get' }],
  ['updateAuthRole', [{ userId: 3 }], { url: '/system/user/authRole', method: 'put', params: { userId: 3 } }],
  ['deptTreeSelect', [], { url: '/system/user/deptTree', method: 'get' }]
]

describe('system/user 接口契约', () => {
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
