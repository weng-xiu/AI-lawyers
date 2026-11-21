'use strict'

/**
 * Profile 个人中心单测：锁定资料加载、分组回显与页签定位。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Profile from '@/views/system/user/profile/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { getUserProfile } from '@/api/system/user'

// 子组件各自有独立用例，此处隔离其依赖（userAvatar 会间接引入 store 中的图片资源）
jest.mock('@/views/system/user/profile/userAvatar', () => ({
  __esModule: true,
  default: { name: 'UserAvatar', render: h => h('div', { class: 'user-avatar-stub' }) }
}))
jest.mock('@/views/system/user/profile/userInfo', () => ({
  __esModule: true,
  default: { name: 'UserInfo', render: h => h('div', { class: 'user-info-stub' }) }
}))
jest.mock('@/views/system/user/profile/resetPwd', () => ({
  __esModule: true,
  default: { name: 'ResetPwd', render: h => h('div', { class: 'reset-pwd-stub' }) }
}))

jest.mock('@/api/system/user', () => ({ getUserProfile: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('svg-icon', SvgIcon)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const profile = {
  userName: 'zhangsan',
  phonenumber: '13800000000',
  email: 'zhangsan@example.com',
  createTime: '2026-01-01 10:00:00',
  dept: { deptName: '法务部' }
}

function mountProfile(params = {}) {
  const wrapper = mount(Profile, {
    localVue,
    mocks: { $route: { params } }
  })
  return wrapper
}

describe('Profile', () => {
  beforeEach(() => {
    getUserProfile.mockReset()
    getUserProfile.mockResolvedValue({ data: profile, roleGroup: '律师', postGroup: '法务' })
  })

  test('加载个人资料并渲染信息列表', async () => {
    const wrapper = mountProfile()
    await flush()

    expect(wrapper.vm.user).toEqual(profile)
    expect(wrapper.vm.roleGroup).toBe('律师')
    expect(wrapper.vm.postGroup).toBe('法务')
    expect(wrapper.text()).toContain('zhangsan')
    expect(wrapper.text()).toContain('13800000000')
    expect(wrapper.text()).toContain('法务部 / 法务')
    expect(wrapper.text()).toContain('律师')
  })

  test('无路由参数时默认展示基本资料页签', () => {
    const wrapper = mountProfile()

    expect(wrapper.vm.selectedTab).toBe('userinfo')
  })

  test('路由参数可指定默认页签', () => {
    const wrapper = mountProfile({ activeTab: 'resetPwd' })

    expect(wrapper.vm.selectedTab).toBe('resetPwd')
  })

  test('子系统组件均被挂载', () => {
    const wrapper = mountProfile()

    expect(wrapper.find('.user-avatar-stub').exists()).toBe(true)
    expect(wrapper.find('.user-info-stub').exists()).toBe(true)
    expect(wrapper.find('.reset-pwd-stub').exists()).toBe(true)
  })
})
