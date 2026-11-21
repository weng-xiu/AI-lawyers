'use strict'

/**
 * Breadcrumb 组件单测：锁定路由层级推导、首页补位、noRedirect 与跳转行为。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Breadcrumb from '@/components/Breadcrumb/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountBreadcrumb({ path, matched = [], defaultRoutes = [] }) {
  const push = jest.fn()
  const wrapper = mount(Breadcrumb, {
    localVue,
    mocks: {
      $route: { path, matched },
      $store: { getters: { defaultRoutes } },
      $router: { push }
    }
  })
  return { wrapper, push }
}

function itemTexts(wrapper) {
  return wrapper.findAll('.el-breadcrumb__inner').wrappers.map(item => item.text())
}

describe('Breadcrumb', () => {
  test('首页路由不重复补位', () => {
    const { wrapper } = mountBreadcrumb({
      path: '/index',
      matched: [{ name: 'Index', path: '/index', meta: { title: '首页' } }]
    })

    expect(itemTexts(wrapper)).toEqual(['首页'])
    expect(wrapper.findAll('a')).toHaveLength(0)
    expect(wrapper.find('.no-redirect').text()).toBe('首页')
  })

  test('普通页面自动前置首页，末级不可点击', () => {
    const { wrapper } = mountBreadcrumb({
      path: '/system/user',
      matched: [
        { name: 'System', path: '/system', meta: { title: '系统管理' } },
        { name: 'User', path: '/system/user', meta: { title: '用户管理' } }
      ]
    })

    expect(itemTexts(wrapper)).toEqual(['首页', '系统管理', '用户管理'])
    expect(wrapper.findAll('a')).toHaveLength(2)
    expect(wrapper.find('.no-redirect').text()).toBe('用户管理')
  })

  test('redirect 为 noRedirect 的层级渲染为不可点击文本', () => {
    const { wrapper } = mountBreadcrumb({
      path: '/a/b',
      matched: [
        { path: '/a', redirect: 'noRedirect', meta: { title: '一级' } },
        { path: '/a/b', meta: { title: '二级' } }
      ]
    })

    const texts = wrapper.findAll('.no-redirect').wrappers.map(item => item.text())
    expect(texts).toEqual(['一级', '二级'])
    expect(wrapper.findAll('a')).toHaveLength(1)
  })

  test('点击链接按 path 跳转', async () => {
    const { wrapper, push } = mountBreadcrumb({
      path: '/system/user',
      matched: [
        { name: 'System', path: '/system', meta: { title: '系统管理' } },
        { name: 'User', path: '/system/user', meta: { title: '用户管理' } }
      ]
    })

    const links = wrapper.findAll('a')
    await links.at(0).trigger('click')
    expect(push).toHaveBeenLastCalledWith('/index')

    await links.at(1).trigger('click')
    expect(push).toHaveBeenLastCalledWith('/system')
  })

  test('配置 redirect 时优先跳转 redirect', async () => {
    const { wrapper, push } = mountBreadcrumb({
      path: '/x/z',
      matched: [
        { path: '/x', redirect: '/y', meta: { title: '重定向页' } },
        { path: '/x/z', meta: { title: '末级' } }
      ]
    })

    await wrapper.findAll('a').at(1).trigger('click')

    expect(push).toHaveBeenCalledWith('/y')
  })

  test('breadcrumb 为 false 的层级被过滤', () => {
    const { wrapper } = mountBreadcrumb({
      path: '/p/q',
      matched: [
        { path: '/p', meta: { title: '隐藏层级', breadcrumb: false } },
        { path: '/p/q', meta: { title: '显示层级' } }
      ]
    })

    expect(itemTexts(wrapper)).toEqual(['首页', '显示层级'])
  })

  test('多级路径按 defaultRoutes 递归还原层级', () => {
    const defaultRoutes = [{
      path: '/a',
      name: 'A',
      meta: { title: '模块A' },
      children: [{
        path: 'b',
        name: 'B',
        meta: { title: '模块B' },
        children: [{ path: 'c', name: 'C', meta: { title: '模块C' } }]
      }]
    }]

    const { wrapper } = mountBreadcrumb({ path: '/a/b/c', defaultRoutes })

    expect(itemTexts(wrapper)).toEqual(['首页', '模块A', '模块B', '模块C'])
  })
})
