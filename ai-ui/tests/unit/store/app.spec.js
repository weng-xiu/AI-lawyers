'use strict'

/**
 * 应用布局 store 单测：覆盖初始状态、侧边栏/设备/尺寸变更与 action 提交。
 */
import Cookies from 'js-cookie'
import app from '@/store/modules/app'

jest.mock('js-cookie', () => ({
  get: jest.fn(),
  set: jest.fn()
}))

function freshState() {
  return {
    sidebar: {
      opened: true,
      withoutAnimation: false,
      hide: false
    },
    device: 'desktop',
    size: 'medium'
  }
}

describe('app 模块定义', () => {
  test('开启命名空间并暴露 state/mutations/actions', () => {
    expect(app.namespaced).toBe(true)
    expect(app.state).toBeDefined()
    expect(app.mutations).toBeDefined()
    expect(app.actions).toBeDefined()
  })

  test('未读取到 Cookie 时默认展开侧边栏且尺寸为 medium', () => {
    expect(app.state.sidebar.opened).toBe(true)
    expect(app.state.device).toBe('desktop')
    expect(app.state.size).toBe('medium')
  })
})

describe('app mutations', () => {
  beforeEach(() => {
    Cookies.set.mockClear()
  })

  test('TOGGLE_SIDEBAR 切换展开状态并写入 Cookie', () => {
    const state = freshState()

    app.mutations.TOGGLE_SIDEBAR(state)
    expect(state.sidebar.opened).toBe(false)
    expect(state.sidebar.withoutAnimation).toBe(false)
    expect(Cookies.set).toHaveBeenCalledWith('sidebarStatus', 0)

    app.mutations.TOGGLE_SIDEBAR(state)
    expect(state.sidebar.opened).toBe(true)
    expect(Cookies.set).toHaveBeenCalledWith('sidebarStatus', 1)
  })

  test('TOGGLE_SIDEBAR 在侧边栏隐藏时不响应', () => {
    const state = freshState()
    state.sidebar.hide = true

    const result = app.mutations.TOGGLE_SIDEBAR(state)

    expect(result).toBe(false)
    expect(state.sidebar.opened).toBe(true)
    expect(Cookies.set).not.toHaveBeenCalled()
  })

  test('CLOSE_SIDEBAR 关闭侧边栏并记录动画标记', () => {
    const state = freshState()

    app.mutations.CLOSE_SIDEBAR(state, true)

    expect(Cookies.set).toHaveBeenCalledWith('sidebarStatus', 0)
    expect(state.sidebar.opened).toBe(false)
    expect(state.sidebar.withoutAnimation).toBe(true)
  })

  test('TOGGLE_DEVICE / SET_SIZE / SET_SIDEBAR_HIDE 更新对应字段', () => {
    const state = freshState()

    app.mutations.TOGGLE_DEVICE(state, 'mobile')
    app.mutations.SET_SIZE(state, 'small')
    app.mutations.SET_SIDEBAR_HIDE(state, true)

    expect(state.device).toBe('mobile')
    expect(state.size).toBe('small')
    expect(Cookies.set).toHaveBeenCalledWith('size', 'small')
    expect(state.sidebar.hide).toBe(true)
  })
})

describe('app actions', () => {
  test('各 action 提交对应 mutation', () => {
    const commit = jest.fn()

    app.actions.toggleSideBar({ commit })
    app.actions.closeSideBar({ commit }, { withoutAnimation: true })
    app.actions.toggleDevice({ commit }, 'mobile')
    app.actions.setSize({ commit }, 'small')
    app.actions.toggleSideBarHide({ commit }, true)

    expect(commit).toHaveBeenNthCalledWith(1, 'TOGGLE_SIDEBAR')
    expect(commit).toHaveBeenNthCalledWith(2, 'CLOSE_SIDEBAR', true)
    expect(commit).toHaveBeenNthCalledWith(3, 'TOGGLE_DEVICE', 'mobile')
    expect(commit).toHaveBeenNthCalledWith(4, 'SET_SIZE', 'small')
    expect(commit).toHaveBeenNthCalledWith(5, 'SET_SIDEBAR_HIDE', true)
  })
})
