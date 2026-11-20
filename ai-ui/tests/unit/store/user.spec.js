'use strict'

/**
 * 用户 store 单测：锁定登录、用户信息回显（头像补全/角色兜底/密码提示）与登出清理逻辑。
 */
import router from '@/router'
import { MessageBox } from 'element-ui'
import { login, logout, getInfo } from '@/api/login'
import { setToken, removeToken, getToken } from '@/utils/auth'
import user from '@/store/modules/user'

jest.mock('@/router', () => ({ __esModule: true, default: { push: jest.fn() } }))
jest.mock('element-ui', () => ({ MessageBox: { confirm: jest.fn(() => Promise.resolve()) } }))
jest.mock('@/api/login', () => ({
  login: jest.fn(),
  logout: jest.fn(),
  getInfo: jest.fn()
}))
jest.mock('@/utils/auth', () => ({
  getToken: jest.fn(),
  setToken: jest.fn(),
  removeToken: jest.fn()
}))
jest.mock('@/assets/images/profile.jpg', () => 'default-avatar.png')

const { mutations, actions } = user
const ORIGIN_BASE_API = process.env.VUE_APP_BASE_API

beforeAll(() => {
  process.env.VUE_APP_BASE_API = '/dev-api'
})

afterAll(() => {
  process.env.VUE_APP_BASE_API = ORIGIN_BASE_API
})

beforeEach(() => {
  jest.clearAllMocks()
})

function createState(overrides = {}) {
  return {
    token: '',
    id: '',
    name: '',
    nickName: '',
    avatar: '',
    roles: [],
    permissions: [],
    ...overrides
  }
}

describe('user mutations', () => {
  test('各字段写入', () => {
    const state = createState()

    mutations.SET_TOKEN(state, 't')
    mutations.SET_ID(state, 9)
    mutations.SET_NAME(state, 'admin')
    mutations.SET_NICK_NAME(state, '管理员')
    mutations.SET_AVATAR(state, 'a.png')
    mutations.SET_ROLES(state, ['admin'])
    mutations.SET_PERMISSIONS(state, ['p'])

    expect(state).toEqual({
      token: 't',
      id: 9,
      name: 'admin',
      nickName: '管理员',
      avatar: 'a.png',
      roles: ['admin'],
      permissions: ['p']
    })
  })

  test('初始 state 从本地 token 读取', () => {
    expect(getToken).toBeDefined()
    expect(user.state.token).toBe(getToken())
  })
})

describe('Login', () => {
  test('登录成功写入 token', async () => {
    login.mockResolvedValue({ token: 'tk' })
    const commit = jest.fn()

    await actions.Login({ commit }, { username: ' admin ', password: 'p', code: '1', uuid: 'u' })

    expect(login).toHaveBeenCalledWith('admin', 'p', '1', 'u')
    expect(setToken).toHaveBeenCalledWith('tk')
    expect(commit).toHaveBeenCalledWith('SET_TOKEN', 'tk')
  })

  test('登录失败透传错误', async () => {
    const error = new Error('密码错误')
    login.mockRejectedValue(error)

    await expect(actions.Login({ commit: jest.fn() }, { username: 'admin', password: 'p' }))
      .rejects.toBe(error)
  })
})

describe('GetInfo', () => {
  test('头像为空使用默认头像，角色权限正常写入', async () => {
    getInfo.mockResolvedValue({
      user: { userId: 9, userName: 'admin', nickName: '管理员', avatar: '' },
      roles: ['admin'],
      permissions: ['system:user:list']
    })
    const commit = jest.fn()

    await actions.GetInfo({ commit, state: createState() })

    expect(commit).toHaveBeenCalledWith('SET_ROLES', ['admin'])
    expect(commit).toHaveBeenCalledWith('SET_PERMISSIONS', ['system:user:list'])
    expect(commit).toHaveBeenCalledWith('SET_ID', 9)
    expect(commit).toHaveBeenCalledWith('SET_NAME', 'admin')
    expect(commit).toHaveBeenCalledWith('SET_NICK_NAME', '管理员')
    expect(commit).toHaveBeenCalledWith('SET_AVATAR', 'default-avatar.png')
    expect(MessageBox.confirm).not.toHaveBeenCalled()
  })

  test('相对头像拼接接口前缀，绝对链接原样保留', async () => {
    getInfo.mockResolvedValue({
      user: { userId: 1, userName: 'a', nickName: 'a', avatar: '/avatar/1.png' },
      roles: ['admin'],
      permissions: []
    })
    const commit = jest.fn()
    await actions.GetInfo({ commit, state: createState() })
    expect(commit).toHaveBeenCalledWith('SET_AVATAR', '/dev-api/avatar/1.png')

    getInfo.mockResolvedValue({
      user: { userId: 1, userName: 'a', nickName: 'a', avatar: 'https://cdn/a.png' },
      roles: ['admin'],
      permissions: []
    })
    await actions.GetInfo({ commit, state: createState() })
    expect(commit).toHaveBeenLastCalledWith('SET_AVATAR', 'https://cdn/a.png')
  })

  test('角色为空时兜底 ROLE_DEFAULT', async () => {
    getInfo.mockResolvedValue({ user: { userId: 1, userName: 'a', nickName: 'a' }, roles: [] })
    const commit = jest.fn()

    await actions.GetInfo({ commit, state: createState() })

    expect(commit).toHaveBeenCalledWith('SET_ROLES', ['ROLE_DEFAULT'])
    expect(commit).not.toHaveBeenCalledWith('SET_PERMISSIONS', expect.anything())
  })

  test('初始密码提示并在确认后跳转修改密码', async () => {
    getInfo.mockResolvedValue({
      user: { userId: 1, userName: 'a', nickName: 'a' },
      roles: ['admin'],
      permissions: [],
      isDefaultModifyPwd: true
    })
    MessageBox.confirm.mockResolvedValue('confirm')

    await actions.GetInfo({ commit: jest.fn(), state: createState() })

    expect(MessageBox.confirm).toHaveBeenCalledWith(
      '您的密码还是初始密码，请修改密码！',
      '安全提示',
      expect.objectContaining({ type: 'warning' })
    )
    expect(router.push).toHaveBeenCalledWith({ name: 'Profile', params: { activeTab: 'resetPwd' } })
  })

  test('密码过期提示，初始密码场景不再重复提示', async () => {
    getInfo.mockResolvedValue({
      user: { userId: 1, userName: 'a', nickName: 'a' },
      roles: ['admin'],
      permissions: [],
      isDefaultModifyPwd: false,
      isPasswordExpired: true
    })

    await actions.GetInfo({ commit: jest.fn(), state: createState() })

    expect(MessageBox.confirm).toHaveBeenCalledWith(
      '您的密码已过期，请尽快修改密码！',
      '安全提示',
      expect.objectContaining({ type: 'warning' })
    )
  })

  test('接口失败透传错误', async () => {
    const error = new Error('获取用户信息失败')
    getInfo.mockRejectedValue(error)

    await expect(actions.GetInfo({ commit: jest.fn(), state: createState() })).rejects.toBe(error)
  })
})

describe('LogOut 与 FedLogOut', () => {
  test('退出成功清空 token/角色/权限', async () => {
    logout.mockResolvedValue({})
    const commit = jest.fn()

    await actions.LogOut({ commit, state: createState({ token: 'tk' }) })

    expect(logout).toHaveBeenCalledWith('tk')
    expect(commit).toHaveBeenCalledWith('SET_TOKEN', '')
    expect(commit).toHaveBeenCalledWith('SET_ROLES', [])
    expect(commit).toHaveBeenCalledWith('SET_PERMISSIONS', [])
    expect(removeToken).toHaveBeenCalled()
  })

  test('退出接口失败透传错误', async () => {
    const error = new Error('退出失败')
    logout.mockRejectedValue(error)

    await expect(actions.LogOut({ commit: jest.fn(), state: createState() })).rejects.toBe(error)
  })

  test('前端登出仅清本地 token', async () => {
    const commit = jest.fn()

    await actions.FedLogOut({ commit })

    expect(commit).toHaveBeenCalledWith('SET_TOKEN', '')
    expect(removeToken).toHaveBeenCalled()
  })
})
