'use strict'

/**
 * AuthRole 用户授权角色单测：锁定角色加载、可选判断、选择提交与返回。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import AuthRole from '@/views/system/user/authRole.vue'
import Pagination from '@/components/Pagination/index.vue'
import { getAuthRole, updateAuthRole } from '@/api/system/user'

jest.mock('@/api/system/user', () => ({
  getAuthRole: jest.fn(),
  updateAuthRole: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('pagination', Pagination)
// 模板中的 parseTime 在真实应用里由全局过滤器注入
localVue.mixin({ methods: { parseTime: value => value } })

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const roles = [
  { roleId: 1, roleName: '管理员', roleKey: 'admin', status: '0', flag: true, createTime: '2026-01-01' },
  { roleId: 2, roleName: '访客', roleKey: 'guest', status: '1', flag: false, createTime: '2026-01-02' }
]

function mountAuthRole(userId = 9) {
  const msgSuccess = jest.fn()
  const closeOpenPage = jest.fn()
  const wrapper = mount(AuthRole, {
    localVue,
    mocks: {
      $route: { params: userId ? { userId } : {} },
      $modal: { msgSuccess },
      $tab: { closeOpenPage }
    }
  })
  return { wrapper, msgSuccess, closeOpenPage }
}

describe('AuthRole', () => {
  beforeEach(() => {
    getAuthRole.mockReset()
    updateAuthRole.mockReset()
    getAuthRole.mockResolvedValue({ user: { userId: 9, nickName: '张三', userName: 'zhangsan' }, roles })
  })

  test('无用户编号时不请求角色数据', () => {
    const { wrapper } = mountAuthRole(null)

    expect(getAuthRole).not.toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(true)
  })

  test('加载用户与角色列表并结束 loading', async () => {
    const { wrapper } = mountAuthRole()
    await flush()
    await wrapper.vm.$nextTick()

    expect(getAuthRole).toHaveBeenCalledWith(9)
    expect(wrapper.vm.form.nickName).toBe('张三')
    expect(wrapper.vm.roles).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  test('仅正常状态角色可勾选', () => {
    const { wrapper } = mountAuthRole()

    expect(wrapper.vm.checkSelectable(roles[0])).toBe(true)
    expect(wrapper.vm.checkSelectable(roles[1])).toBe(false)
    expect(wrapper.vm.getRowKey(roles[0])).toBe(1)
  })

  test('点击行切换选中，禁用角色不响应', () => {
    const { wrapper } = mountAuthRole()
    const toggle = jest.spyOn(wrapper.vm.$refs.table, 'toggleRowSelection').mockImplementation(() => {})

    wrapper.vm.clickRow(roles[0])
    expect(toggle).toHaveBeenCalledWith(roles[0])

    toggle.mockClear()
    wrapper.vm.clickRow(roles[1])
    expect(toggle).not.toHaveBeenCalled()
  })

  test('提交选中角色并返回用户页', async () => {
    updateAuthRole.mockResolvedValue({})
    const { wrapper, msgSuccess, closeOpenPage } = mountAuthRole()
    wrapper.vm.form.userId = 9
    wrapper.vm.handleSelectionChange([roles[0], roles[2] || roles[1]])

    wrapper.vm.submitForm()
    await flush()

    expect(updateAuthRole).toHaveBeenCalledWith({ userId: 9, roleIds: '1,2' })
    expect(msgSuccess).toHaveBeenCalledWith('授权成功')
    expect(closeOpenPage).toHaveBeenCalledWith({ path: '/system/user' })
  })
})
