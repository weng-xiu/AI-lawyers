'use strict'

/**
 * 权限路由单测：锁定菜单树到路由对象的转换、重名路由去重、ParentView 子路由扁平化，
 * 以及按权限/角色过滤动态路由的行为。
 */
import router from '@/router'
import { getRouters } from '@/api/menu'
import auth from '@/plugins/auth'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'
import permission, { filterDynamicRoutes, loadView } from '@/store/modules/permission'

jest.mock('@/router', () => ({
  __esModule: true,
  default: { addRoutes: jest.fn() },
  constantRoutes: [{ path: '/', component: 'Layout' }],
  dynamicRoutes: [
    { path: '/perm', permissions: ['system:user:list'] },
    { path: '/role', roles: ['admin'] },
    { path: '/plain' }
  ]
}))
jest.mock('@/api/menu', () => ({ getRouters: jest.fn() }))
jest.mock('@/plugins/auth', () => ({
  __esModule: true,
  default: { hasPermiOr: jest.fn(), hasRoleOr: jest.fn() }
}))
jest.mock('@/layout/index', () => ({ __esModule: true, default: { name: 'Layout' } }))
jest.mock('@/components/ParentView', () => ({ __esModule: true, default: { name: 'ParentView' } }))
jest.mock('@/layout/components/InnerLink', () => ({ __esModule: true, default: { name: 'InnerLink' } }))

const { GenerateRoutes } = permission.actions

beforeEach(() => {
  jest.clearAllMocks()
})

describe('GenerateRoutes 路由生成', () => {
  test('转换组件、追加 404 通配路由并分发四类路由', async () => {
    getRouters.mockResolvedValue({
      data: [{
        path: '/system',
        name: 'System',
        component: 'Layout',
        children: [
          { path: 'user', name: 'User', component: 'system/user/index' },
          { path: 'ext', name: 'Ext', component: 'InnerLink', meta: { link: 'http://example.com' } }
        ]
      }]
    })
    const commit = jest.fn()

    const routes = await GenerateRoutes({ commit })

    expect(routes[routes.length - 1]).toEqual({ path: '*', redirect: '/404', hidden: true })
    expect(routes[0].component).toBe(Layout)
    expect(routes[0].children[0].component).toBeInstanceOf(Function)
    expect(routes[0].children[1].component).toBe(InnerLink)
    expect(router.addRoutes).toHaveBeenCalled()
    expect(commit).toHaveBeenCalledWith('SET_ROUTES', routes)
    expect(commit).toHaveBeenCalledWith('SET_SIDEBAR_ROUTERS', expect.any(Array))
    expect(commit).toHaveBeenCalledWith('SET_DEFAULT_ROUTES', expect.any(Array))
    expect(commit).toHaveBeenCalledWith('SET_TOPBAR_ROUTES', expect.any(Array))
  })

  test('空子节点的 ParentView 删除 children 与 redirect', async () => {
    getRouters.mockResolvedValue({
      data: [{ path: '/blank', name: 'Blank', component: 'ParentView', children: [] }]
    })

    const routes = await GenerateRoutes({ commit: jest.fn() })

    expect(routes[0].component).toBe(ParentView)
    expect(routes[0].children).toBeUndefined()
    expect(routes[0].redirect).toBeUndefined()
  })

  test('重名子路由自动追加序号避免 vue-router 警告', async () => {
    getRouters.mockResolvedValue({
      data: [
        { path: '/a', name: 'A', component: 'Layout', children: [{ path: 'x', name: 'Same', component: 'x' }] },
        { path: '/b', name: 'B', component: 'Layout', children: [{ path: 'y', name: 'Same', component: 'y' }] },
        { path: '/c', name: 'C', component: 'Layout', children: [{ path: 'z', name: 'Same', component: 'z' }] }
      ]
    })

    const routes = await GenerateRoutes({ commit: jest.fn() })

    expect(routes[0].children[0].name).toBe('Same')
    expect(routes[1].children[0].name).toBe('Same2')
    expect(routes[2].children[0].name).toBe('Same3')
  })

  test('ParentView 子路由路径与父级合并后扁平化', async () => {
    getRouters.mockResolvedValue({
      data: [{
        path: '/layout',
        name: 'LayoutRoute',
        component: 'Layout',
        children: [{
          path: 'parent',
          name: 'Parent',
          component: 'ParentView',
          children: [{ path: 'child', name: 'Child', component: 'parent/child/index' }]
        }]
      }]
    })

    const routes = await GenerateRoutes({ commit: jest.fn() })

    expect(routes[0].children[0].path).toBe('parent/child')
    expect(routes[0].children[0].component).toBeInstanceOf(Function)
  })
})

describe('filterDynamicRoutes 动态路由过滤', () => {
  test('按权限或角色保留路由，无权限声明的路由忽略', () => {
    auth.hasPermiOr.mockReturnValue(true)
    auth.hasRoleOr.mockReturnValue(false)

    const routes = filterDynamicRoutes([
      { path: '/perm', permissions: ['system:user:list'] },
      { path: '/role', roles: ['admin'] },
      { path: '/plain' }
    ])

    expect(routes.map(item => item.path)).toEqual(['/perm'])
    expect(auth.hasPermiOr).toHaveBeenCalledWith(['system:user:list'])
    expect(auth.hasRoleOr).toHaveBeenCalledWith(['admin'])
  })

  test('权限与角色均不满足时返回空数组', () => {
    auth.hasPermiOr.mockReturnValue(false)
    auth.hasRoleOr.mockReturnValue(false)

    expect(filterDynamicRoutes([
      { path: '/perm', permissions: ['a'] },
      { path: '/role', roles: ['b'] }
    ])).toEqual([])
  })
})

describe('loadView 组件懒加载', () => {
  test('非开发环境返回动态 import 函数', () => {
    const component = loadView('system/user/index')

    expect(component).toBeInstanceOf(Function)
  })
})
