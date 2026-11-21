'use strict'

/**
 * 授权用户选择弹窗单测：锁定弹窗展示、行点击多选、查询重置与授权提交。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import SelectUser from '@/views/system/role/selectUser.vue'
import { unallocatedUserList, authUserSelectAll } from '@/api/system/role'

jest.mock('@/api/system/role', () => ({
  unallocatedUserList: jest.fn(),
  authUserSelectAll: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
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
  { userId: 2, userName: 'legal', nickName: '法务', email: 'legal@test.com', phonenumber: '13900000000', status: '0', createTime: '2026-01-02' }
]

function mountDialog() {
  const modal = { msgSuccess: jest.fn(), msgError: jest.fn() }
  const wrapper = mount(SelectUser, {
    localVue,
    propsData: { roleId: 2 },
    stubs: {
      pagination: true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, dict }
  })
  return { wrapper, modal }
}

describe('SelectUser 授权用户选择', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    unallocatedUserList.mockResolvedValue({ rows, total: rows.length })
    authUserSelectAll.mockResolvedValue({ msg: '授权成功' })
  })

  test('show 携带角色编号加载未授权用户并展示弹窗', async () => {
    const { wrapper } = mountDialog()

    wrapper.vm.show()
    await flush()

    expect(wrapper.vm.queryParams.roleId).toBe(2)
    expect(unallocatedUserList).toHaveBeenCalledWith(expect.objectContaining({ roleId: 2, pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.userList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.visible).toBe(true)
  })

  test('行点击切换选中，多选同步用户编号', async () => {
    const { wrapper } = mountDialog()
    wrapper.vm.show()
    await flush()
    await wrapper.vm.$nextTick()

    const toggle = jest.spyOn(wrapper.vm.$refs.table, 'toggleRowSelection')
    wrapper.vm.clickRow(rows[0])
    expect(toggle).toHaveBeenCalledWith(rows[0])

    wrapper.vm.handleSelectionChange([rows[1]])
    expect(wrapper.vm.userIds).toEqual([2])
  })

  test('查询重置页码，重置表单后重新查询', async () => {
    const { wrapper } = mountDialog()
    wrapper.vm.show()
    await flush()
    wrapper.vm.queryParams.pageNum = 5

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(unallocatedUserList).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(unallocatedUserList).toHaveBeenCalledTimes(3)
  })

  test('未选择用户时提示且不提交', async () => {
    const { wrapper, modal } = mountDialog()
    wrapper.vm.show()
    await flush()

    wrapper.vm.handleSelectUser()

    expect(modal.msgError).toHaveBeenCalledWith('请选择要分配的用户')
    expect(authUserSelectAll).not.toHaveBeenCalled()
  })

  test('选择用户后提交授权并回传成功事件', async () => {
    const { wrapper, modal } = mountDialog()
    wrapper.vm.show()
    await flush()
    wrapper.vm.userIds = [1, 2]

    wrapper.vm.handleSelectUser()
    await flush()

    expect(authUserSelectAll).toHaveBeenCalledWith({ roleId: 2, userIds: '1,2' })
    expect(modal.msgSuccess).toHaveBeenCalledWith('授权成功')
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.emitted('ok')).toHaveLength(1)
  })
})
