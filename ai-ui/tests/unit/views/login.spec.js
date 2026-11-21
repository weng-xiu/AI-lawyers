'use strict'

/**
 * Login 登录页单测：锁定验证码加载、记住密码、登录跳转与失败刷新。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Cookies from 'js-cookie'
import Login from '@/views/login.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { getCodeImg } from '@/api/login'

jest.mock('@/api/login', () => ({ getCodeImg: jest.fn() }))
jest.mock('@/utils/jsencrypt', () => ({
  encrypt: value => 'enc:' + value,
  decrypt: value => String(value).replace('enc:', '')
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountLogin(options = {}) {
  const dispatch = jest.fn(() => Promise.resolve())
  const push = jest.fn(() => Promise.resolve())
  const route = options.route || { query: {} }
  const wrapper = mount(Login, {
    localVue,
    mocks: {
      $store: { dispatch },
      $router: { push },
      $route: route
    }
  })
  return { wrapper, dispatch, push }
}

describe('Login', () => {
  beforeEach(() => {
    getCodeImg.mockReset()
    getCodeImg.mockResolvedValue({ img: 'base64-code', uuid: 'uuid-1' })
    ;['username', 'password', 'rememberMe'].forEach(key => Cookies.remove(key))
  })

  test('挂载时加载验证码并写入图片与 uuid', async () => {
    const { wrapper } = mountLogin()
    await flush()

    expect(wrapper.vm.codeUrl).toBe('data:image/gif;base64,base64-code')
    expect(wrapper.vm.loginForm.uuid).toBe('uuid-1')
    expect(wrapper.vm.captchaEnabled).toBe(true)
    expect(wrapper.find('.login-code-img').attributes('src')).toBe('data:image/gif;base64,base64-code')
  })

  test('验证码关闭时隐藏验证码区域', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    const { wrapper } = mountLogin()
    await flush()

    expect(wrapper.vm.captchaEnabled).toBe(false)
    expect(wrapper.find('.login-code').exists()).toBe(false)
    expect(wrapper.vm.codeUrl).toBe('')
  })

  test('勾选记住密码时写入 Cookie 并跳转首页', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    const { wrapper, dispatch, push } = mountLogin()
    await flush()
    wrapper.vm.loginForm.username = 'admin'
    wrapper.vm.loginForm.password = 'admin123'
    wrapper.vm.loginForm.rememberMe = true

    wrapper.vm.handleLogin()
    await flush()
    await flush()

    expect(dispatch).toHaveBeenCalledWith('Login', expect.objectContaining({
      username: 'admin',
      password: 'admin123',
      rememberMe: true
    }))
    expect(Cookies.get('username')).toBe('admin')
    expect(Cookies.get('password')).toBe('enc:admin123')
    expect(push).toHaveBeenCalledWith({ path: '/' })
  })

  test('未勾选记住密码时清除已有 Cookie', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    Cookies.set('username', 'old')
    const { wrapper } = mountLogin()
    await flush()

    wrapper.vm.handleLogin()
    await flush()
    await flush()

    expect(Cookies.get('username')).toBeUndefined()
  })

  test('回填已记住的账号与解密后的密码', async () => {
    Cookies.set('username', 'tom')
    Cookies.set('password', 'enc:secret')
    Cookies.set('rememberMe', 'true')
    const { wrapper } = mountLogin()
    await flush()

    expect(wrapper.vm.loginForm.username).toBe('tom')
    expect(wrapper.vm.loginForm.password).toBe('secret')
    expect(wrapper.vm.loginForm.rememberMe).toBe(true)
  })

  test('登录失败后结束加载并刷新验证码', async () => {
    const { wrapper, dispatch } = mountLogin()
    await flush()
    dispatch.mockImplementation(() => Promise.reject(new Error('bad')))
    wrapper.vm.loginForm.code = '1234'
    wrapper.vm.$refs.loginForm.validate = callback => callback(true)

    wrapper.vm.handleLogin()
    await flush()
    await flush()
    await flush()

    expect(dispatch).toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
    expect(getCodeImg).toHaveBeenCalledTimes(2)
  })

  test('登录成功后跳转 redirect 指定地址', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    const { wrapper, push } = mountLogin({ route: { query: { redirect: '/system/user' } } })
    await flush()

    wrapper.vm.handleLogin()
    await flush()
    await flush()

    expect(push).toHaveBeenCalledWith({ path: '/system/user' })
  })
})
