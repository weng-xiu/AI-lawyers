'use strict'

/**
 * 角色管理单测：锁定列表查询、多选、新增/修改提交、状态切换、数据权限与菜单/部门树操作。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Role from '@/views/system/role/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listRole, getRole, delRole, addRole, updateRole, dataScope, changeRoleStatus, deptTreeSelect
} from '@/api/system/role'
import { treeselect as menuTreeselect, roleMenuTreeselect } from '@/api/system/menu'

jest.mock('@/api/system/role', () => ({
  listRole: jest.fn(),
  getRole: jest.fn(),
  delRole: jest.fn(),
  addRole: jest.fn(),
  updateRole: jest.fn(),
  dataScope: jest.fn(),
  changeRoleStatus: jest.fn(),
  deptTreeSelect: jest.fn()
}))

jest.mock('@/api/system/menu', () => ({
  treeselect: jest.fn(),
  roleMenuTreeselect: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.addDateRange = addDateRange
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
  { roleId: 1, roleName: '超级管理员', roleKey: 'admin', roleSort: 0, status: '0', createTime: '2026-01-01' },
  { roleId: 2, roleName: '普通角色', roleKey: 'common', roleSort: 1, status: '1', createTime: '2026-01-02' }
]

const menuTree = [
  { id: 1, label: '系统管理', children: [{ id: 2, label: '用户管理' }] }
]

function makeTreeRef() {
  return {
    setChecked: jest.fn(),
    setCheckedKeys: jest.fn(),
    setCheckedNodes: jest.fn(),
    getCheckedKeys: jest.fn(() => [2]),
    getHalfCheckedKeys: jest.fn(() => [1]),
    store: { nodesMap: { 1: { expanded: false }, 2: { expanded: false } } }
  }
}

function mountPage() {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()), msgSuccess: jest.fn() }
  const download = jest.fn()
  const push = jest.fn()
  const wrapper = mount(Role, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true
    },
    mocks: { $modal: modal, download, $router: { push }, dict }
  })
  return { wrapper, modal, download, push }
}

describe('Role 角色管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listRole.mockResolvedValue({ rows, total: rows.length })
    getRole.mockResolvedValue({
      data: { roleId: 2, roleName: '普通角色', roleKey: 'common', roleSort: 1, status: '1', remark: '测试角色' }
    })
    delRole.mockResolvedValue({})
    addRole.mockResolvedValue({})
    updateRole.mockResolvedValue({})
    dataScope.mockResolvedValue({})
    changeRoleStatus.mockResolvedValue({})
    deptTreeSelect.mockResolvedValue({ depts: [{ id: 100, label: '总公司' }], checkedKeys: [100] })
    menuTreeselect.mockResolvedValue({ data: menuTree })
    roleMenuTreeselect.mockResolvedValue({ menus: menuTree, checkedKeys: [2] })
  })

  test('创建时携带日期范围查询角色列表', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listRole).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.roleList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('超级管理员')
    expect(wrapper.text()).toContain('普通角色')
  })

  test('查询重置页码，重置清空日期范围并重新加载', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listRole).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listRole).toHaveBeenCalledTimes(3)
  })

  test('多选联动 ids 与按钮禁用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('新增重置表单并加载菜单树', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    await flush()

    expect(menuTreeselect).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.menuOptions).toEqual(menuTree)
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加角色')
    expect(wrapper.vm.form).toEqual(expect.objectContaining({
      roleSort: 0,
      status: '0',
      menuIds: [],
      deptIds: [],
      menuCheckStrictly: true,
      deptCheckStrictly: true
    }))
  })

  test('修改读取角色详情与已选菜单', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.$refs.menu = makeTreeRef()

    wrapper.vm.handleUpdate(rows[1])
    await flush()

    expect(getRole).toHaveBeenCalledWith(2)
    expect(roleMenuTreeselect).toHaveBeenCalledWith(2)
    expect(wrapper.vm.form.roleName).toBe('普通角色')
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('修改角色')
    expect(wrapper.vm.menuOptions).toEqual(menuTree)
  })

  test('提交时按是否带主键区分新增与修改并收集菜单权限', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)
    wrapper.vm.$refs.menu = makeTreeRef()

    wrapper.vm.form = { roleName: '法务角色', roleKey: 'legal', roleSort: 2, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addRole).toHaveBeenCalledWith(wrapper.vm.form)
    expect(wrapper.vm.form.menuIds).toEqual([1, 2])
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.$refs.menu = makeTreeRef()
    wrapper.vm.form = { roleId: 2, roleName: '普通角色', roleKey: 'common', roleSort: 1 }
    wrapper.vm.submitForm()
    await flush()
    expect(updateRole).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('状态开关确认后调用接口，取消时回滚开关值', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const row = { ...rows[0], status: '0' }

    wrapper.vm.handleStatusChange(row)
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '确认要"启用"角色「超级管理员」吗？'
    }))
    expect(changeRoleStatus).toHaveBeenCalledWith(1, '0')
    expect(modal.msgSuccess).toHaveBeenCalledWith('启用成功')

    const row2 = { ...rows[0], status: '0' }
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleStatusChange(row2)
    await flush()
    expect(changeRoleStatus).toHaveBeenCalledTimes(1)
    expect(row2.status).toBe('1')
  })

  test('分配数据权限加载部门树并提交权限范围', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.$refs.menu = makeTreeRef()
    wrapper.vm.$refs.dept = makeTreeRef()

    wrapper.vm.handleDataScope(rows[1])
    await flush()

    expect(getRole).toHaveBeenCalledWith(2)
    expect(deptTreeSelect).toHaveBeenCalledWith(2)
    expect(wrapper.vm.deptOptions).toEqual([{ id: 100, label: '总公司' }])
    expect(wrapper.vm.openDataScope).toBe(true)
    expect(wrapper.vm.title).toBe('分配数据权限')
    expect(wrapper.vm.$refs.dept.getCheckedKeys()).toEqual([100])

    wrapper.vm.submitDataScope()
    await flush()
    expect(dataScope).toHaveBeenCalledWith(wrapper.vm.form)
    expect(wrapper.vm.form.deptIds).toEqual([100])
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
    expect(wrapper.vm.openDataScope).toBe(false)
  })

  test('权限范围切换到非自定义时清空部门勾选', async () => {
    const { wrapper } = mountPage()
    await flush()
    const deptRef = makeTreeRef()
    wrapper.vm.$refs.dept = deptRef

    wrapper.vm.dataScopeSelectChange('2')
    expect(deptRef.setCheckedKeys).not.toHaveBeenCalled()

    wrapper.vm.dataScopeSelectChange('3')
    expect(deptRef.setCheckedKeys).toHaveBeenCalledWith([])
  })

  test('更多操作按指令分发到数据权限与分配用户', async () => {
    const { wrapper, push } = mountPage()
    await flush()
    wrapper.vm.$refs.menu = makeTreeRef()
    wrapper.vm.$refs.dept = makeTreeRef()

    wrapper.vm.handleCommand('handleAuthUser', rows[1])
    expect(push).toHaveBeenCalledWith('/system/role-auth/user/2')

    wrapper.vm.handleCommand('handleDataScope', rows[1])
    await flush()
    expect(deptTreeSelect).toHaveBeenCalledWith(2)

    wrapper.vm.handleCommand('unknown', rows[1])
    expect(deptTreeSelect).toHaveBeenCalledTimes(1)
  })

  test('树操作支持展开折叠、全选与父子联动', async () => {
    const { wrapper } = mountPage()
    await flush()
    const menuRef = makeTreeRef()
    const deptRef = makeTreeRef()
    wrapper.vm.$refs.menu = menuRef
    wrapper.vm.$refs.dept = deptRef
    wrapper.vm.menuOptions = [{ id: 1 }, { id: 2 }]
    wrapper.vm.deptOptions = [{ id: 100 }]
    deptRef.store.nodesMap[100] = { expanded: true }

    wrapper.vm.handleCheckedTreeExpand(true, 'menu')
    expect(menuRef.store.nodesMap[1].expanded).toBe(true)
    expect(menuRef.store.nodesMap[2].expanded).toBe(true)

    wrapper.vm.handleCheckedTreeExpand(false, 'dept')
    expect(deptRef.store.nodesMap[100].expanded).toBe(false)

    wrapper.vm.handleCheckedTreeNodeAll(true, 'menu')
    expect(menuRef.setCheckedNodes).toHaveBeenCalledWith(wrapper.vm.menuOptions)
    wrapper.vm.handleCheckedTreeNodeAll(false, 'dept')
    expect(deptRef.setCheckedNodes).toHaveBeenCalledWith([])

    wrapper.vm.handleCheckedTreeConnect(false, 'menu')
    expect(wrapper.vm.form.menuCheckStrictly).toBe(false)
    wrapper.vm.handleCheckedTreeConnect(true, 'dept')
    expect(wrapper.vm.form.deptCheckStrictly).toBe(true)
  })

  test('删除单行与批量删除按数量提示，取消确认不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[1])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '个角色' }))
    expect(delRole).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
    expect(listRole).toHaveBeenCalledTimes(2)

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delRole).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(delRole).toHaveBeenCalledTimes(2)
  })

  test('取消关闭弹窗重置表单并支持导出', async () => {
    const { wrapper, download } = mountPage()
    await flush()
    wrapper.vm.$refs.menu = makeTreeRef()
    wrapper.vm.open = true
    wrapper.vm.form = { roleName: '临时角色' }

    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.status).toBe('0')
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')

    wrapper.vm.openDataScope = true
    wrapper.vm.cancelDataScope()
    expect(wrapper.vm.openDataScope).toBe(false)

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'system/role/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^role_\d+\.xlsx$/)
    )
  })
})
