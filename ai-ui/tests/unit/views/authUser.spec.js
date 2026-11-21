'use strict'

/**
 * 角色授权用户单测：锁定授权列表加载、用户选择弹窗、单条/批量取消授权与关闭返回。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import AuthUser from '@/views/system/role/authUser.vue'
import { allocatedUserList, authUserCancel, authUserCancelAll } from '@/api/system/role'

jest.mock('@/api/system/role', () => ({
  allocatedUserList: jest.fn(),
  authUserCancel: jest.fn(),
  authUserCancelAll: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_normal_disable: [
      { label: '正常', value: '0' },
      { label: '停用', value: '1' }
    ]
  }
}

const rows = [
  { userId: 1, userName: 'admin', nickName: '管理员', email: 'admin@test.com', phonenumber: '13800000000', status: '0', createTime: '2026-01-01' },
  { userId: 2, userName: 'legal', nickName: '法务', email: 'legal@test.com', phonenumber: '13900000000', status: '1', createTime: '2026-01-02' }
]

function mountPage(givenRoute = { params: { roleId: 2 } }) {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const closeOpenPage = jest.fn()
  const show = jest.fn()
  const wrapper = mount(AuthUser, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'select-user': { name: 'SelectUser', methods: { show }, template: '<div class="select-user-stub" />' },
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, $tab: { closeOpenPage }, $route: givenRoute, dict }
  })
  return { wrapper, modal, closeOpenPage, show }
}

describe('AuthUser 角色授权用户', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    allocatedUserList.mockResolvedValue({ rows, total: rows.length })
    authUserCancel.mockResolvedValue({})
    authUserCancelAll.mockResolvedValue({})
  })

  test('创建时按路由角色编号查询授权用户', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.queryParams.roleId).toBe(2)
    expect(allocatedUserList).toHaveBeenCalledWith(expect.objectContaining({ roleId: 2, pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.userList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('legal')
  })

  test('无角色编号时不查询列表', async () => {
    mountPage({ params: {} })
    await flush()

    expect(allocatedUserList).not.toHaveBeenCalled()
  })

  test('查询重置页码，重置表单后重新查询', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(allocatedUserList).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(allocatedUserList).toHaveBeenCalledTimes(3)
  })

  test('多选联动用户编号与批量按钮状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.userIds).toEqual([1])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.userIds).toEqual([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('打开用户选择弹窗与关闭返回角色列表', async () => {
    const { wrapper, show, closeOpenPage } = mountPage()
    await flush()

    wrapper.vm.openSelectUser()
    expect(show).toHaveBeenCalledTimes(1)

    wrapper.vm.handleClose()
    expect(closeOpenPage).toHaveBeenCalledWith({ path: '/system/role' })
  })

  test('取消单个用户授权后刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.cancelAuthUser(rows[1])
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '确认要取消用户「legal」的本角色授权吗？'
    }))
    expect(authUserCancel).toHaveBeenCalledWith({ userId: 2, roleId: 2 })
    expect(allocatedUserList).toHaveBeenCalledTimes(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('取消授权成功')
  })

  test('取消确认时不提交取消授权', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.cancelAuthUser(rows[0])
    await flush()

    expect(authUserCancel).not.toHaveBeenCalled()
  })

  test('批量取消授权拼接用户编号并提示数量', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.userIds = [1, 2]

    wrapper.vm.cancelAuthUserAll()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      count: 2,
      countNoun: '个用户',
      content: '将批量取消选中用户的本角色授权'
    }))
    expect(authUserCancelAll).toHaveBeenCalledWith({ roleId: 2, userIds: '1,2' })
    expect(modal.msgSuccess).toHaveBeenCalledWith('取消授权成功')
    expect(allocatedUserList).toHaveBeenCalledTimes(2)
  })
})
