'use strict'

/**
 * Token 存取工具单测：锁定 Admin-Token Cookie 的写入、读取与删除。
 */
import Cookies from 'js-cookie'
import { getToken, setToken, removeToken } from '@/utils/auth'

const TOKEN_KEY = 'Admin-Token'

afterEach(() => {
  Cookies.remove(TOKEN_KEY)
})

describe('Token Cookie 读写', () => {
  test('初始状态无 token', () => {
    expect(getToken()).toBeUndefined()
  })

  test('写入后可读取到相同值', () => {
    setToken('mock-token-123')

    expect(getToken()).toBe('mock-token-123')
  })

  test('删除后 token 被清空', () => {
    setToken('mock-token-123')
    removeToken()

    expect(getToken()).toBeUndefined()
  })
})
