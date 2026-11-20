'use strict'

/**
 * 页签插件单测：覆盖刷新、关闭（当前/指定/全部/左/右/其他）与新增、修改页签。
 */
import store from '@/store'
import router from '@/router'
import tab from '@/plugins/tab'

jest.mock('@/store', () => ({
  dispatch: jest.fn(() => Promise.resolve({ visitedViews: [] }))
}))
jest.mock('@/router', () => ({
  currentRoute: { path: '/current', query: {}, matched: [] },
  replace: jest.fn(),
  push: jest.fn()
}))

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

beforeEach(() => {
  jest.clearAllMocks()
  store.dispatch.mockResolvedValue({ visitedViews: [] })
  router.currentRoute = { path: '/current', query: { id: 1 }, matched: [] }
})

describe('refreshPage', () => {
  test('指定页签时移除缓存并跳转 redirect', async () => {
    const obj = { name: 'User', path: '/user', query: { id: 1 } }

    await tab.refreshPage(obj)

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delCachedView', obj)
    expect(router.replace).toHaveBeenCalledWith({ path: '/redirect/user', query: { id: 1 } })
  })

  test('未指定页签时从 matched 中推导当前组件名', async () => {
    router.currentRoute = {
      path: '/user',
      query: {},
      matched: [
        { components: { default: { name: 'Layout' } } },
        { components: { default: { name: 'User' } } }
      ]
    }

    await tab.refreshPage()

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delCachedView', {
      name: 'User',
      path: '/user',
      query: {}
    })
    expect(router.replace).toHaveBeenCalledWith({ path: '/redirect/user', query: {} })
  })
})

describe('closeOpenPage / closePage', () => {
  test('closeOpenPage 关闭当前页并按需打开目标页', () => {
    const target = { path: '/next' }

    tab.closeOpenPage(target)

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delView', router.currentRoute)
    expect(router.push).toHaveBeenCalledWith(target)
  })

  test('closeOpenPage 不传目标页时不跳转', () => {
    tab.closeOpenPage()

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delView', router.currentRoute)
    expect(router.push).not.toHaveBeenCalled()
  })

  test('closePage 不传参时跳转最后一个已访问页签', async () => {
    store.dispatch.mockResolvedValueOnce({
      visitedViews: [{ fullPath: '/a' }, { fullPath: '/b' }]
    })

    await tab.closePage()

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delView', router.currentRoute)
    expect(router.push).toHaveBeenCalledWith('/b')
  })

  test('closePage 无剩余页签时跳转首页', async () => {
    await tab.closePage()

    expect(router.push).toHaveBeenCalledWith('/')
  })

  test('closePage 传入对象时只关闭不跳转', () => {
    const view = { path: '/x' }

    tab.closePage(view)

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delView', view)
    expect(router.push).not.toHaveBeenCalled()
  })
})

describe('批量关闭页签', () => {
  test('closeAllPage 调用 delAllViews', () => {
    tab.closeAllPage()

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delAllViews')
  })

  test('closeLeftPage / closeRightPage 未传参时使用当前路由', () => {
    tab.closeLeftPage()
    tab.closeRightPage()

    expect(store.dispatch).toHaveBeenNthCalledWith(1, 'tagsView/delLeftTags', router.currentRoute)
    expect(store.dispatch).toHaveBeenNthCalledWith(2, 'tagsView/delRightTags', router.currentRoute)
  })

  test('closeOtherPage 支持指定页签', () => {
    const view = { path: '/keep' }

    tab.closeOtherPage(view)

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/delOthersViews', view)
  })
})

describe('openPage / updatePage', () => {
  test('openPage 添加页签并携带查询参数跳转', () => {
    tab.openPage('用户管理', '/system/user', { id: 2 })

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/addView', {
      path: '/system/user',
      meta: { title: '用户管理' }
    })
    expect(router.push).toHaveBeenCalledWith({ path: '/system/user', query: { id: 2 } })
  })

  test('updatePage 透传 updateVisitedView', () => {
    const view = { path: '/x', meta: { title: '新标题' } }

    tab.updatePage(view)

    expect(store.dispatch).toHaveBeenCalledWith('tagsView/updateVisitedView', view)
  })
})
