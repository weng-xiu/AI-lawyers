'use strict'

/**
 * Register 注册页单测：锁定验证码、密码一致性校验、注册成功提示与失败刷新。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Register from '@/views/register.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { getCodeImg, register } from '@/api/login'

jest.mock('@/api/login', () => ({
  getCodeImg: jest.fn(),
  register: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountRegister() {
  const alert = jest.fn(() => Promise.resolve())
  const push = jest.fn()
  const wrapper = mount(Register, {
    localVue,
    mocks: { $alert: alert, $router: { push } }
  })
  return { wrapper, alert, push }
}

describe('Register', () => {
  beforeEach(() => {
    getCodeImg.mockReset()
    getCodeImg.mockResolvedValue({ img: 'code-img', uuid: 'uuid-2' })
    register.mockReset()
  })

  test('挂载时加载验证码', async () => {
    const { wrapper } = mountRegister()
    await flush()

    expect(wrapper.vm.codeUrl).toBe('data:image/gif;base64,code-img')
    expect(wrapper.vm.registerForm.uuid).toBe('uuid-2')
  })

  test('两次密码不一致时阻止提交', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    const { wrapper } = mountRegister()
    await flush()
    wrapper.vm.registerForm.username = 'newuser'
    wrapper.vm.registerForm.password = '12345'
    wrapper.vm.registerForm.confirmPassword = '54321'

    wrapper.vm.handleRegister()
    await flush()
    await flush()

    expect(register).not.toHaveBeenCalled()
  })

  test('注册成功后提示并跳转登录页', async () => {
    getCodeImg.mockResolvedValue({ captchaEnabled: false })
    register.mockResolvedValue({})
    const { wrapper, alert, push } = mountRegister()
    await flush()
    wrapper.vm.registerForm.username = 'newuser'
    wrapper.vm.registerForm.password = '12345'
    wrapper.vm.registerForm.confirmPassword = '12345'

    wrapper.vm.handleRegister()
    await flush()
    await flush()
    await flush()

    expect(register).toHaveBeenCalledWith(wrapper.vm.registerForm)
    expect(alert).toHaveBeenCalledWith(
      expect.stringContaining('恭喜你，您的账号 newuser 注册成功！'),
      '系统提示',
      expect.objectContaining({ dangerouslyUseHTMLString: true, type: 'success' })
    )
    expect(push).toHaveBeenCalledWith('/login')
  })

  test('注册失败后结束加载并刷新验证码', async () => {
    register.mockRejectedValue(new Error('bad'))
    const { wrapper } = mountRegister()
    await flush()
    wrapper.vm.registerForm.username = 'newuser'
    wrapper.vm.registerForm.password = '12345'
    wrapper.vm.registerForm.confirmPassword = '12345'
    wrapper.vm.registerForm.code = '1234'

    wrapper.vm.handleRegister()
    await flush()
    await flush()
    await flush()

    expect(wrapper.vm.loading).toBe(false)
    expect(getCodeImg).toHaveBeenCalledTimes(2)
  })
})
