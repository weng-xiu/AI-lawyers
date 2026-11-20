'use strict'

/**
 * 权限插件单测：覆盖权限/角色的单项、任一、全部判定与通配规则。
 */
import store from '@/store'
import auth from '@/plugins/auth'

jest.mock('@/store', () => ({
  getters: {
    permissions: [],
    roles: []
  }
}))

beforeEach(() => {
  store.getters.permissions = []
  store.getters.roles = []
})

describe('hasPermi', () => {
  test('拥有通配权限时任意权限均通过', () => {
    store.getters.permissions = ['*:*:*']

    expect(auth.hasPermi('lawyers:quality:list')).toBe(true)
  })

  test('精确匹配权限通过，缺失或不传时返回 false', () => {
    store.getters.permissions = ['lawyers:quality:list']

    expect(auth.hasPermi('lawyers:quality:list')).toBe(true)
    expect(auth.hasPermi('lawyers:quality:edit')).toBe(false)
    expect(auth.hasPermi('')).toBe(false)
    expect(auth.hasPermi(undefined)).toBe(false)
  })
})

describe('hasPermiOr / hasPermiAnd', () => {
  test('hasPermiOr 命中任意一项即为 true', () => {
    store.getters.permissions = ['a:b:c']

    expect(auth.hasPermiOr(['x:y:z', 'a:b:c'])).toBe(true)
    expect(auth.hasPermiOr(['x:y:z'])).toBe(false)
  })

  test('hasPermiAnd 需全部命中', () => {
    store.getters.permissions = ['a:b:c', 'd:e:f']

    expect(auth.hasPermiAnd(['a:b:c', 'd:e:f'])).toBe(true)
    expect(auth.hasPermiAnd(['a:b:c', 'x:y:z'])).toBe(false)
  })
})

describe('hasRole', () => {
  test('超管角色与精确角色通过，缺失返回 false', () => {
    store.getters.roles = ['admin']

    expect(auth.hasRole('任意角色')).toBe(true)
    expect(auth.hasRole('')).toBe(false)
  })

  test('普通角色需精确匹配', () => {
    store.getters.roles = ['lawyer']

    expect(auth.hasRole('lawyer')).toBe(true)
    expect(auth.hasRole('manager')).toBe(false)
  })
})

describe('hasRoleOr / hasRoleAnd', () => {
  test('hasRoleOr 命中任意一项即为 true', () => {
    store.getters.roles = ['lawyer']

    expect(auth.hasRoleOr(['manager', 'lawyer'])).toBe(true)
    expect(auth.hasRoleOr(['manager'])).toBe(false)
  })

  test('hasRoleAnd 需全部命中', () => {
    store.getters.roles = ['lawyer', 'manager']

    expect(auth.hasRoleAnd(['lawyer', 'manager'])).toBe(true)
    expect(auth.hasRoleAnd(['lawyer', 'admin'])).toBe(false)
  })
})
