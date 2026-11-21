'use strict'

/**
 * 菜单管理单测：锁定树形列表、上级菜单树、图标选择、新增/修改提交与删除。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Menu from '@/views/system/menu/index.vue'
import { handleTree } from '@/utils/ruoyi'
import { listMenu, getMenu, delMenu, addMenu, updateMenu } from '@/api/system/menu'

jest.mock('@/api/system/menu', () => ({
  listMenu: jest.fn(),
  getMenu: jest.fn(),
  delMenu: jest.fn(),
  addMenu: jest.fn(),
  updateMenu: jest.fn()
}))

// IconSelect 依赖 require.context，测试中直接替换为占位组件
jest.mock('@/components/IconSelect', () => ({
  __esModule: true,
  default: { name: 'IconSelect', props: ['activeIcon'], template: '<div class="icon-select-stub" />' }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.directive('hasRole', {})
localVue.prototype.handleTree = handleTree
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_show_hide: [
      { label: '显示', value: '0' },
      { label: '隐藏', value: '1' }
    ],
    sys_normal_disable: [
      { label: '正常', value: '0' },
      { label: '停用', value: '1' }
    ]
  }
}

const flatMenus = [
  { menuId: 1, parentId: 0, menuName: '系统管理', icon: 'system', orderNum: 1, menuType: 'M', visible: '0', status: '0' },
  { menuId: 2, parentId: 1, menuName: '用户管理', icon: 'user', orderNum: 1, menuType: 'C', path: 'user', visible: '0', status: '0' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const wrapper = mount(Menu, {
    localVue,
    stubs: {
      'right-toolbar': true,
      'svg-icon': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' },
      treeselect: { props: ['value', 'options', 'normalizer', 'placeholder', 'showCount'], template: '<div class="treeselect-stub" />' },
      'icon-select': { name: 'IconSelect', template: '<div class="icon-select-stub" />' }
    },
    mocks: { $modal: modal, dict }
  })
  return { wrapper, modal }
}

describe('Menu 菜单管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listMenu.mockResolvedValue({ data: flatMenus })
    getMenu.mockResolvedValue({
      data: { menuId: 2, parentId: 1, menuName: '用户管理', icon: 'user', menuType: 'C', path: 'user', orderNum: 1, visible: '0', status: '0' }
    })
    delMenu.mockResolvedValue({})
    addMenu.mockResolvedValue({})
    updateMenu.mockResolvedValue({})
  })

  test('创建时查询菜单并构建树形结构', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listMenu).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.menuList).toHaveLength(1)
    expect(wrapper.vm.menuList[0].children).toHaveLength(1)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('系统管理')
    expect(wrapper.text()).toContain('用户管理')
  })

  test('normalizer 移除空 children 并映射菜单字段', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.normalizer({ menuId: 9, menuName: '测试菜单', children: [] })).toEqual({
      id: 9,
      label: '测试菜单',
      children: undefined
    })
  })

  test('新增根级菜单时构建主类目树', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    await flush()

    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加菜单')
    expect(wrapper.vm.form.parentId).toBe(0)
    expect(wrapper.vm.form.menuType).toBe('M')
    expect(wrapper.vm.menuOptions).toHaveLength(1)
    expect(wrapper.vm.menuOptions[0].menuName).toBe('主类目')
    expect(wrapper.vm.menuOptions[0].children).toHaveLength(1)
  })

  test('从行内新增子菜单时继承上级编号', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd({ menuId: 1 })
    await flush()

    expect(wrapper.vm.form.parentId).toBe(1)
    expect(wrapper.vm.title).toBe('添加菜单')
  })

  test('修改读取详情并加载上级菜单树', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleUpdate({ menuId: 2 })
    await flush()

    expect(getMenu).toHaveBeenCalledWith(2)
    expect(wrapper.vm.form.menuName).toBe('用户管理')
    expect(wrapper.vm.title).toBe('修改菜单')
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.menuOptions[0].menuName).toBe('主类目')
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { parentId: 0, menuName: '档案管理', menuType: 'M', orderNum: 2, icon: 'archive', visible: '0', status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addMenu).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')

    wrapper.vm.form = { menuId: 2, parentId: 1, menuName: '用户管理', menuType: 'C', orderNum: 1, path: 'user' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateMenu).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('图标选择与展开折叠联动', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.selected('el-icon-user')
    expect(wrapper.vm.form.icon).toBe('el-icon-user')

    expect(wrapper.vm.isExpandAll).toBe(false)
    wrapper.vm.toggleExpandAll()
    expect(wrapper.vm.isExpandAll).toBe(true)
    expect(wrapper.vm.refreshTable).toBe(false)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.refreshTable).toBe(true)
  })

  test('删除菜单刷新列表，重置查询重新加载', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ menuId: 2, menuName: '用户管理' })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      count: 1,
      countNoun: '个菜单',
      content: '将删除菜单「用户管理」'
    }))
    expect(delMenu).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listMenu).toHaveBeenCalledTimes(3)

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete({ menuId: 2 })
    await flush()
    expect(delMenu).toHaveBeenCalledTimes(1)
  })
})
