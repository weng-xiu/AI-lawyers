'use strict'

/**
 * 系统用户管理单测：锁定部门树过滤、用户列表加载、状态启停、新增/修改/删除、
 * 重置密码与分配角色、导入导出，以及律师资料/坐席配置默认值。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import User from '@/views/system/user/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listUser, getUser, delUser, addUser, updateUser, resetUserPwd,
  changeUserStatus, deptTreeSelect
} from '@/api/system/user'

jest.mock('@/api/system/user', () => ({
  listUser: jest.fn(),
  getUser: jest.fn(),
  delUser: jest.fn(),
  addUser: jest.fn(),
  updateUser: jest.fn(),
  resetUserPwd: jest.fn(),
  changeUserStatus: jest.fn(),
  deptTreeSelect: jest.fn()
}))

jest.mock('@/utils/auth', () => ({ getToken: jest.fn(() => 'mock-token') }))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.mixin({
  computed: {
    dict() {
      return {
        type: {
          sys_normal_disable: [{ label: '正常', value: '0' }, { label: '停用', value: '1' }],
          sys_user_sex: [{ label: '男', value: '0' }, { label: '女', value: '1' }, { label: '未知', value: '2' }]
        }
      }
    }
  }
})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.resetForm = jest.fn()
localVue.prototype.parseTime = value => (value ? '2026-11-01 09:00:00' : '')
localVue.prototype.download = jest.fn()
localVue.prototype.getConfigKey = jest.fn(() => Promise.resolve({ msg: 'Init@123' }))

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const userRows = [
  { userId: 1, userName: 'admin', nickName: '管理员', deptName: '总部', phonenumber: '13800000001', status: '0', lawyerFlag: '0', createTime: '2026-11-01 09:00:00' },
  { userId: 2, userName: 'lawyer', nickName: '王律师', deptName: '律师部', phonenumber: '13800000002', status: '1', lawyerFlag: '1', createTime: '2026-11-02 09:00:00' }
]

const deptTree = [
  {
    id: 1,
    label: '总部',
    children: [
      { id: 2, label: '停用部', disabled: true },
      { id: 3, label: '律师部', children: [{ id: 4, label: '诉讼组' }] }
    ]
  }
]

const wrappers = []

function mountPage() {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()), msgSuccess: jest.fn() }
  const routerPush = jest.fn()
  const alert = jest.fn()
  const prompt = jest.fn(() => Promise.resolve({ value: 'New@123' }))
  const wrapper = mount(User, {
    localVue,
    stubs: {
      Treeselect: { props: ['value'], template: '<div class="treeselect-stub" />' },
      Splitpanes: { template: '<div><slot /></div>' },
      Pane: { template: '<div><slot /></div>' }
    },
    mocks: {
      $store: { getters: { device: 'desktop', permissions: ['*:*:*'], roles: ['admin'] } },
      $modal: modal,
      $router: { push: routerPush },
      $alert: alert,
      $prompt: prompt
    }
  })
  wrappers.push(wrapper)
  return { wrapper, modal, routerPush, alert, prompt }
}

describe('系统用户管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listUser.mockResolvedValue({ rows: userRows, total: 2 })
    getDeptTreeMock()
    getUser.mockResolvedValue({
      data: { ...userRows[0] },
      posts: [{ postId: 1, postName: '专职律师' }],
      roles: [{ roleId: 2, roleName: '普通角色' }],
      postIds: [1],
      roleIds: [2]
    })
    delUser.mockResolvedValue({})
    addUser.mockResolvedValue({})
    updateUser.mockResolvedValue({})
    resetUserPwd.mockResolvedValue({})
    changeUserStatus.mockResolvedValue({})
  })

  function getDeptTreeMock() {
    deptTreeSelect.mockResolvedValue({ data: JSON.parse(JSON.stringify(deptTree)) })
  }

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.destroy())
  })

  test('初始化加载用户列表、部门树与默认密码', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listUser).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.userList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.deptOptions).toHaveLength(1)
    expect(wrapper.vm.enabledDeptOptions[0].children.map(d => d.id)).toEqual([3])
    expect(wrapper.vm.enabledDeptOptions[0].children[0].children[0].id).toBe(4)
    expect(wrapper.vm.initPassword).toBe('Init@123')
    expect(wrapper.vm.upload.headers.Authorization).toBe('Bearer mock-token')
  })

  test('部门树过滤剔除禁用节点，名称筛选按包含匹配', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.filterNode('', { label: '总部' })).toBe(true)
    expect(wrapper.vm.filterNode('律师', { label: '律师部' })).toBe(true)
    expect(wrapper.vm.filterNode('财务', { label: '律师部' })).toBe(false)

    const list = [
      { id: 1, label: '总部', children: [{ id: 2, label: '停用', disabled: true }, { id: 3, label: '启用' }] },
      { id: 4, label: '停用分部', disabled: true }
    ]
    expect(wrapper.vm.filterDisabledDept(list).map(d => d.id)).toEqual([1])
    expect(list[0].children.map(d => d.id)).toEqual([3])
  })

  test('点击部门节点按部门重查，名称变化驱动树过滤', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleNodeClick({ id: 3 })
    expect(wrapper.vm.queryParams.deptId).toBe(3)
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(getList).toHaveBeenCalledTimes(1)

    const filter = jest.spyOn(wrapper.vm.$refs.tree, 'filter')
    wrapper.vm.deptName = '律师'
    await wrapper.vm.$nextTick()
    expect(filter).toHaveBeenCalledWith('律师')
  })

  test('用户启停确认后调用接口，取消失败时回滚状态', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleStatusChange({ ...userRows[0], status: '0' })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: expect.stringContaining('启用'),
      tip: expect.stringContaining('立即可登录')
    }))
    expect(changeUserStatus).toHaveBeenCalledWith(1, '0')
    expect(modal.msgSuccess).toHaveBeenCalledWith('启用成功')

    modal.dangerConfirm.mockRejectedValue(new Error('cancel'))
    const row = { ...userRows[1], status: '1' }
    wrapper.vm.handleStatusChange(row)
    await flush()
    expect(row.status).toBe('0')
  })

  test('新增用户重置表单并带出岗位/角色与初始密码', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    await flush()

    expect(getUser).toHaveBeenCalled()
    expect(wrapper.vm.postOptions).toHaveLength(1)
    expect(wrapper.vm.roleOptions).toHaveLength(1)
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加用户')
    expect(wrapper.vm.form.status).toBe('0')
    expect(wrapper.vm.form.lawyerFlag).toBe('0')
    expect(wrapper.vm.form.callMode).toBe('0')
    expect(wrapper.vm.form.postIds).toEqual([])
    expect(wrapper.vm.form.roleIds).toEqual([])
    expect(wrapper.vm.form.password).toBe('Init@123')
  })

  test('修改用户回填详情与角色岗位关联', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleUpdate(userRows[1])
    await flush()

    expect(getUser).toHaveBeenCalledWith(2)
    expect(wrapper.vm.form.userName).toBe('admin')
    expect(wrapper.vm.form.postIds).toEqual([1])
    expect(wrapper.vm.form.roleIds).toEqual([2])
    expect(wrapper.vm.form.password).toBe('')
    expect(wrapper.vm.title).toBe('修改用户')
    expect(wrapper.vm.open).toBe(true)
  })

  test('提交校验通过后按有无用户ID走新增或修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleAdd()
    await flush()
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.submitForm()
    await flush()

    expect(addUser).toHaveBeenCalled()
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)
    expect(getList).toHaveBeenCalled()

    wrapper.vm.handleUpdate(userRows[0])
    await flush()
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.submitForm()
    await flush()

    expect(updateUser).toHaveBeenCalledWith(expect.objectContaining({ userId: 1 }))
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')

    addUser.mockClear()
    updateUser.mockClear()
    wrapper.vm.$refs.form.validate = cb => cb(false)
    wrapper.vm.submitForm()
    expect(addUser).not.toHaveBeenCalled()
    expect(updateUser).not.toHaveBeenCalled()
  })

  test('多选映射主键', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([{ userId: 1 }])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('删除单条与批量用户', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleDelete(userRows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '个用户' }))
    expect(delUser).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
    expect(getList).toHaveBeenCalled()

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delUser).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('重置密码与分配角色', async () => {
    const { wrapper, modal, routerPush, prompt } = mountPage()
    await flush()

    wrapper.vm.handleResetPwd(userRows[0])
    await flush()

    expect(prompt).toHaveBeenCalledWith('请输入"admin"的新密码', '提示', expect.any(Object))
    expect(resetUserPwd).toHaveBeenCalledWith(1, 'New@123')
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功，新密码是：New@123')

    wrapper.vm.handleCommand('handleResetPwd', userRows[1])
    await flush()
    expect(resetUserPwd).toHaveBeenLastCalledWith(2, 'New@123')

    wrapper.vm.handleCommand('handleAuthRole', userRows[0])
    expect(routerPush).toHaveBeenCalledWith('/system/user-auth/role/1')

    resetUserPwd.mockClear()
    wrapper.vm.handleCommand('unknown', userRows[0])
    expect(resetUserPwd).not.toHaveBeenCalled()
  })

  test('重置查询清空日期与部门并重查', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})
    const setCurrentKey = jest.spyOn(wrapper.vm.$refs.tree, 'setCurrentKey')

    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']
    wrapper.vm.queryParams.deptId = 3
    wrapper.vm.resetQuery()

    expect(wrapper.vm.dateRange).toEqual([])
    expect(wrapper.vm.queryParams.deptId).toBeUndefined()
    expect(setCurrentKey).toHaveBeenCalledWith(null)
    expect(getList).toHaveBeenCalledTimes(1)
  })

  test('导出/导入模板与上传流程', async () => {
    const { wrapper, alert } = mountPage()
    await flush()

    wrapper.vm.handleExport()
    expect(wrapper.vm.download).toHaveBeenCalledWith(
      'system/user/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^user_\d+\.xlsx$/)
    )

    wrapper.vm.importTemplate()
    expect(wrapper.vm.download).toHaveBeenLastCalledWith(
      'system/user/importTemplate',
      {},
      expect.stringMatching(/^user_template_\d+\.xlsx$/)
    )

    wrapper.vm.handleImport()
    expect(wrapper.vm.upload.open).toBe(true)
    expect(wrapper.vm.upload.title).toBe('用户导入')

    wrapper.vm.handleFileUploadProgress()
    expect(wrapper.vm.upload.isUploading).toBe(true)

    wrapper.vm.$refs.upload = { clearFiles: jest.fn(), submit: jest.fn() }
    wrapper.vm.handleFileSuccess({ msg: '导入成功' })
    expect(wrapper.vm.upload.open).toBe(false)
    expect(wrapper.vm.upload.isUploading).toBe(false)
    expect(wrapper.vm.$refs.upload.clearFiles).toHaveBeenCalled()
    expect(alert).toHaveBeenCalledWith(expect.stringContaining('导入成功'), '导入结果', { dangerouslyUseHTMLString: true })

    wrapper.vm.submitFileForm()
    expect(wrapper.vm.$refs.upload.submit).toHaveBeenCalled()
  })

  test('取消关闭弹窗并重置表单', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.open = true
    wrapper.vm.form.userName = '测试'
    wrapper.vm.cancel()

    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.userName).toBeUndefined()
    expect(wrapper.vm.form.status).toBe('0')
  })
})
