'use strict'

/**
 * 个人资料表单单测：锁定资料回填、校验与保存后同步。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import UserInfo from '@/views/system/user/profile/userInfo.vue'
import { updateUserProfile } from '@/api/system/user'

jest.mock('@/api/system/user', () => ({ updateUserProfile: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const profile = {
  nickName: '张三',
  phonenumber: '13800000000',
  email: 'zhangsan@example.com',
  sex: '0'
}

function mountUserInfo() {
  const msgSuccess = jest.fn()
  const closePage = jest.fn()
  const user = { ...profile }
  const wrapper = mount(UserInfo, {
    localVue,
    propsData: { user },
    mocks: {
      $modal: { msgSuccess },
      $tab: { closePage }
    }
  })
  return { wrapper, user, msgSuccess, closePage }
}

describe('UserInfo', () => {
  beforeEach(() => {
    updateUserProfile.mockReset()
  })

  test('传入用户资料后回填表单', () => {
    const { wrapper } = mountUserInfo()

    expect(wrapper.vm.form).toEqual(profile)
  })

  test('手机号或邮箱格式错误时不提交', async () => {
    const { wrapper } = mountUserInfo()
    wrapper.vm.form.phonenumber = '123'
    wrapper.vm.form.email = 'not-email'

    wrapper.vm.submit()
    await flush()

    expect(updateUserProfile).not.toHaveBeenCalled()
  })

  test('校验通过后保存并同步父级资料', async () => {
    updateUserProfile.mockResolvedValue({})
    const { wrapper, user, msgSuccess } = mountUserInfo()
    wrapper.vm.form.phonenumber = '13900000000'
    wrapper.vm.form.email = 'new@example.com'

    wrapper.vm.submit()
    await flush()
    await flush()

    expect(updateUserProfile).toHaveBeenCalledWith(wrapper.vm.form)
    expect(msgSuccess).toHaveBeenCalledWith('修改成功')
    expect(user.phonenumber).toBe('13900000000')
    expect(user.email).toBe('new@example.com')
  })

  test('关闭时关闭当前页签', () => {
    const { wrapper, closePage } = mountUserInfo()

    wrapper.vm.close()

    expect(closePage).toHaveBeenCalled()
  })
})
