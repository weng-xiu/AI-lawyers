'use strict'

/**
 * 登录认证 API 单测：锁定登录/注册免 token 头、用户信息、退出与验证码超时契约。
 */
import request from '@/utils/request'
import * as api from '@/api/login'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['login', ['admin', '123456', '1234', 'uuid-1'], {
    url: '/login',
    headers: { isToken: false, repeatSubmit: false },
    method: 'post',
    data: { username: 'admin', password: '123456', code: '1234', uuid: 'uuid-1' }
  }],
  ['register', [{ username: 'u1', password: 'p1' }], {
    url: '/register',
    headers: { isToken: false },
    method: 'post',
    data: { username: 'u1', password: 'p1' }
  }],
  ['getInfo', [], { url: '/getInfo', method: 'get' }],
  ['logout', [], { url: '/logout', method: 'post' }],
  ['getCodeImg', [], {
    url: '/captchaImage',
    headers: { isToken: false },
    method: 'get',
    timeout: 20000
  }]
]

describe('login 接口契约', () => {
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
