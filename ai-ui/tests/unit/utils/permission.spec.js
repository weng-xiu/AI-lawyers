'use strict'

/**
 * 权限校验工具单测：锁定 checkPermi / checkRole 对通配符、具体权限、超级角色与非法入参的判定。
 */
import store from '@/store'
import { checkPermi, checkRole } from '@/utils/permission'

jest.mock('@/store', () => ({
  __esModule: true,
  default: {
    getters: {
      permissions: [],
      roles: []
    }
  }
}))

beforeEach(() => {
  store.getters.permissions = []
  store.getters.roles = []
})

describe('checkPermi 字符权限校验', () => {
  test('拥有通配符权限时放行任意权限', () => {
    store.getters.permissions = ['*:*:*']

    expect(checkPermi(['system:user:add'])).toBe(true)
    expect(checkPermi(['lawyers:ticket:list'])).toBe(true)
  })

  test('仅当权限列表命中时才放行', () => {
    store.getters.permissions = ['system:user:add']

    expect(checkPermi(['system:user:add', 'system:user:edit'])).toBe(true)
    expect(checkPermi(['system:user:edit'])).toBe(false)
  })

  test('权限列表为空时一律拒绝', () => {
    expect(checkPermi(['system:user:add'])).toBe(false)
  })

  test('入参不是非空数组时报错并拒绝', () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {})

    expect(checkPermi([])).toBe(false)
    expect(checkPermi('system:user:add')).toBe(false)
    expect(checkPermi(null)).toBe(false)

    expect(errorSpy).toHaveBeenCalledTimes(3)
    errorSpy.mockRestore()
  })
})

describe('checkRole 角色权限校验', () => {
  test('admin 角色放行任意角色', () => {
    store.getters.roles = ['admin']

    expect(checkRole(['editor'])).toBe(true)
  })

  test('仅当角色列表命中时才放行', () => {
    store.getters.roles = ['editor']

    expect(checkRole(['admin', 'editor'])).toBe(true)
    expect(checkRole(['admin'])).toBe(false)
  })

  test('入参不是非空数组时报错并拒绝', () => {
    const errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {})

    expect(checkRole([])).toBe(false)
    expect(checkRole(undefined)).toBe(false)

    expect(errorSpy).toHaveBeenCalledTimes(2)
    errorSpy.mockRestore()
  })
})
