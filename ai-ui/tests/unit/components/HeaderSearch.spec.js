'use strict'

/**
 * HeaderSearch 菜单搜索单测：锁定路由池生成、模糊搜索、键盘导航与跳转行为。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import path from 'path'
import HeaderSearch from '@/components/HeaderSearch/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

const defaultRoutes = [
  {
    path: '/system',
    meta: { title: '系统管理', icon: 'system' },
    redirect: 'noRedirect',
    children: [{ path: 'user', meta: { title: '用户管理', icon: 'user' } }]
  },
  { path: '/hidden', hidden: true, meta: { title: '隐藏页' } },
  { path: '/no-title', children: [{ path: 'child', meta: { title: '子页面' } }] },
  { path: 'https://example.com', meta: { title: '外链页面' }, query: '{"id":"1"}' }
]

function mountSearch(routes = defaultRoutes) {
  const push = jest.fn()
  const wrapper = mount(HeaderSearch, {
    localVue,
    mocks: {
      $store: {
        state: { settings: { theme: '#1A3C6E' } },
        getters: { defaultRoutes: routes }
      },
      $router: { push }
    }
  })
  return { wrapper, push }
}

describe('HeaderSearch', () => {
  test('generateRoutes 过滤隐藏路由并递归生成标题与路径', () => {
    const { wrapper } = mountSearch()

    const pool = wrapper.vm.generateRoutes(defaultRoutes)

    expect(pool.map(item => item.path)).toEqual([
      path.resolve('/', '/system', 'user'),
      path.resolve('/', '/no-title', 'child'),
      'https://example.com'
    ])
    expect(pool[0].title).toEqual(['系统管理', '用户管理'])
    expect(pool[0].icon).toBe('user')
    expect(pool[1].title).toEqual(['子页面'])
    expect(pool[2].query).toBe('{"id":"1"}')
  })

  test('挂载后生成搜索池并初始化 Fuse', async () => {
    const { wrapper } = mountSearch()

    expect(wrapper.vm.searchPool).toHaveLength(3)

    await wrapper.vm.$nextTick()

    expect(wrapper.vm.fuse).toBeTruthy()
  })

  test('点击图标打开搜索面板并载入全部结果', () => {
    const { wrapper } = mountSearch()

    wrapper.vm.click()

    expect(wrapper.vm.show).toBe(true)
    expect(wrapper.vm.options).toEqual(wrapper.vm.searchPool)
  })

  test('querySearch 按关键字过滤，空关键字恢复全量', async () => {
    const { wrapper } = mountSearch()
    await wrapper.vm.$nextTick()

    wrapper.vm.querySearch('用户')
    expect(wrapper.vm.options.length).toBeGreaterThan(0)
    expect(wrapper.vm.options[0].path).toBe(path.resolve('/', '/system', 'user'))

    wrapper.vm.querySearch('')
    expect(wrapper.vm.options).toEqual(wrapper.vm.searchPool)
  })

  test('上下键导航在结果首尾循环', () => {
    const { wrapper } = mountSearch()
    wrapper.vm.options = [{ path: '/a' }, { path: '/b' }, { path: '/c' }]

    wrapper.vm.navigateResult('up')
    expect(wrapper.vm.activeIndex).toBe(2)

    wrapper.vm.navigateResult('down')
    expect(wrapper.vm.activeIndex).toBe(0)
  })

  test('回车打开当前选中项', () => {
    const { wrapper, push } = mountSearch()
    wrapper.vm.options = [{ path: '/system/user' }]
    wrapper.vm.activeIndex = 0

    wrapper.vm.selectActiveResult()

    expect(push).toHaveBeenCalledWith('/system/user')
  })

  test('站内路径走 router.push，外链新窗口打开', async () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => {})
    const { wrapper, push } = mountSearch()

    wrapper.vm.change({ path: '/system/user', query: '{"id":"2"}' })
    expect(push).toHaveBeenCalledWith({ path: '/system/user', query: { id: '2' } })

    wrapper.vm.change({ path: 'https://example.com/help' })
    expect(open).toHaveBeenCalledWith('https://example.com/help', '_blank')
    expect(push).toHaveBeenCalledTimes(1)

    await wrapper.vm.$nextTick()
    expect(wrapper.vm.show).toBe(false)

    open.mockRestore()
  })

  test('close 清空搜索状态', () => {
    const { wrapper } = mountSearch()
    wrapper.vm.search = '用户'
    wrapper.vm.options = [{ path: '/system/user' }]
    wrapper.vm.activeIndex = 0
    wrapper.vm.show = true

    wrapper.vm.close()

    expect(wrapper.vm.search).toBe('')
    expect(wrapper.vm.options).toEqual([])
    expect(wrapper.vm.activeIndex).toBe(-1)
    expect(wrapper.vm.show).toBe(false)
  })

  test('activeStyle 仅高亮当前项', () => {
    const { wrapper } = mountSearch()

    wrapper.vm.activeIndex = 1
    expect(wrapper.vm.activeStyle(0)).toEqual({})
    expect(wrapper.vm.activeStyle(1)).toEqual({ 'background-color': '#1A3C6E', color: '#fff' })
  })
})
