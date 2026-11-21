'use strict'

/**
 * 修改密码面板单测：锁定密码一致性校验、提交提示与关闭行为。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ResetPwd from '@/views/system/user/profile/resetPwd.vue'
import { updateUserPwd } from '@/api/system/user'

jest.mock('@/api/system/user', () => ({ updateUserPwd: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountResetPwd() {
  const msgSuccess = jest.fn()
  const closePage = jest.fn()
  const wrapper = mount(ResetPwd, {
    localVue,
    mocks: {
      $modal: { msgSuccess },
      $tab: { closePage }
    }
  })
  return { wrapper, msgSuccess, closePage }
}

describe('ResetPwd', () => {
  beforeEach(() => {
    updateUserPwd.mockReset()
  })

  test('两次密码不一致时不提交', async () => {
    const { wrapper } = mountResetPwd()
    wrapper.vm.user.oldPassword = 'old123'
    wrapper.vm.user.newPassword = 'new123'
    wrapper.vm.user.confirmPassword = 'other123'

    wrapper.vm.submit()
    await flush()

    expect(updateUserPwd).not.toHaveBeenCalled()
  })

  test('新密码不满足长度或含非法字符时不提交', async () => {
    const { wrapper } = mountResetPwd()
    wrapper.vm.user.oldPassword = 'old123'
    wrapper.vm.user.newPassword = '123'
    wrapper.vm.user.confirmPassword = '123'

    wrapper.vm.submit()
    await flush()
    expect(updateUserPwd).not.toHaveBeenCalled()

    wrapper.vm.user.newPassword = 'abc<def>'
    wrapper.vm.user.confirmPassword = 'abc<def>'
    wrapper.vm.submit()
    await flush()
    expect(updateUserPwd).not.toHaveBeenCalled()
  })

  test('校验通过后调用修改接口并提示成功', async () => {
    updateUserPwd.mockResolvedValue({})
    const { wrapper, msgSuccess } = mountResetPwd()
    wrapper.vm.user.oldPassword = 'old123'
    wrapper.vm.user.newPassword = 'new123'
    wrapper.vm.user.confirmPassword = 'new123'

    wrapper.vm.submit()
    await flush()
    await flush()

    expect(updateUserPwd).toHaveBeenCalledWith('old123', 'new123')
    expect(msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('关闭时关闭当前页签', () => {
    const { wrapper, closePage } = mountResetPwd()

    wrapper.vm.close()

    expect(closePage).toHaveBeenCalled()
  })
})
