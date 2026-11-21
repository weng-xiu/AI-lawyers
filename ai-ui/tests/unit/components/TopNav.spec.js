'use strict'

/**
 * TopNav 顶部菜单单测：锁定一级菜单推导、激活路由、菜单跳转与宽度折叠。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TopNav from '@/components/TopNav/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

jest.mock('@/router', () => ({ constantRoutes: [{ path: '/index', meta: { title: '首页' } }] }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

function createRouters() {
  return [
    {
      path: '/business',
      meta: { title: '业务', icon: 'business' },
      children: [{ path: 'workOrder', meta: { title: '工单' } }]
    },
    { path: '/', children: [{ path: 'index', meta: { title: '首页' } }] },
    { path: '/hidden', hidden: true, meta: { title: '隐藏页' } }
  ]
}

function mountTopNav(routePath = '/business/workOrder') {
  const dispatch = jest.fn()
  const commit = jest.fn()
  const push = jest.fn()
  const wrapper = mount(TopNav, {
    localVue,
    mocks: {
      $store: {
        state: {
          settings: { theme: '#1A3C6E' },
          permission: { topbarRouters: createRouters() }
        },
        dispatch,
        commit
      },
      $route: { path: routePath, meta: {} },
      $router: { push }
    }
  })
  return { wrapper, dispatch, commit, push }
}

describe('TopNav', () => {
  test('顶部菜单过滤隐藏项并展开根路由子项', () => {
    const { wrapper } = mountTopNav()

    expect(wrapper.vm.topMenus).toHaveLength(2)
    expect(wrapper.vm.topMenus[0].meta.title).toBe('业务')
    expect(wrapper.vm.topMenus[1].meta.title).toBe('首页')
  })

  test('子菜单拼接父级路径并记录 parentPath', () => {
    const { wrapper } = mountTopNav()

    const child = wrapper.vm.childrenMenus.find(item => item.path === '/business/workOrder')
    expect(child).toBeTruthy()
    expect(child.parentPath).toBe('/business')
  })

  test('多级路径激活一级菜单并展开侧边栏', () => {
    const { wrapper, dispatch, commit } = mountTopNav('/business/workOrder')

    expect(wrapper.vm.activeMenu).toBe('/business')
    expect(dispatch).toHaveBeenCalledWith('app/toggleSideBarHide', false)
    expect(commit).toHaveBeenCalledWith('SET_SIDEBAR_ROUTERS', expect.any(Array))
  })

  test('隐藏侧边栏的路由直接激活并收起侧边栏', () => {
    const { wrapper, dispatch } = mountTopNav('/index')

    expect(wrapper.vm.activeMenu).toBe('/index')
    expect(dispatch).toHaveBeenCalledWith('app/toggleSideBarHide', true)
  })

  test('外链菜单新窗口打开', () => {
    const open = jest.spyOn(window, 'open').mockImplementation(() => {})
    const { wrapper, push } = mountTopNav()

    wrapper.vm.handleSelect('https://example.com/help', [])

    expect(open).toHaveBeenCalledWith('https://example.com/help', '_blank')
    expect(push).not.toHaveBeenCalled()
    open.mockRestore()
  })

  test('无子路由的菜单按路径跳转并收起侧边栏', () => {
    const { wrapper, push, dispatch } = mountTopNav()

    wrapper.vm.handleSelect('/index', [])

    expect(push).toHaveBeenCalledWith({ path: '/index' })
    expect(dispatch).toHaveBeenCalledWith('app/toggleSideBarHide', true)
  })

  test('点击带子路由的一级菜单展开联动菜单', () => {
    const { wrapper, commit, dispatch } = mountTopNav()

    wrapper.vm.handleSelect('/business', [])

    expect(commit).toHaveBeenCalledWith('SET_SIDEBAR_ROUTERS', expect.any(Array))
    expect(dispatch).toHaveBeenCalledWith('app/toggleSideBarHide', false)
  })

  test('按窗口宽度计算可见菜单数量', () => {
    const { wrapper } = mountTopNav()
    const rect = jest.spyOn(document.body, 'getBoundingClientRect').mockReturnValue({ width: 1530 })

    wrapper.vm.setVisibleNumber()

    expect(wrapper.vm.visibleNumber).toBe(6)
    rect.mockRestore()
  })

  test('可见数量足够时渲染一级菜单项', async () => {
    const { wrapper } = mountTopNav()
    wrapper.vm.visibleNumber = 5
    await wrapper.vm.$nextTick()

    const items = wrapper.findAll('.el-menu-item')
    expect(items.length).toBeGreaterThanOrEqual(2)
    expect(wrapper.text()).toContain('业务')
  })
})
