'use strict'

/**
 * 部门管理单测：锁定树形列表、部门树选项、展开折叠、新增/修改提交与删除。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Dept from '@/views/system/dept/index.vue'
import { handleTree } from '@/utils/ruoyi'
import { listDept, getDept, delDept, addDept, updateDept, listDeptExcludeChild } from '@/api/system/dept'

jest.mock('@/api/system/dept', () => ({
  listDept: jest.fn(),
  getDept: jest.fn(),
  delDept: jest.fn(),
  addDept: jest.fn(),
  updateDept: jest.fn(),
  listDeptExcludeChild: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.handleTree = handleTree
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

const flatDepts = [
  { deptId: 100, parentId: 0, deptName: '总公司', orderNum: 0, status: '0', createTime: '2026-01-01' },
  { deptId: 101, parentId: 100, deptName: '研发部', orderNum: 1, status: '0', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const wrapper = mount(Dept, {
    localVue,
    stubs: {
      'right-toolbar': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' },
      treeselect: { props: ['value', 'options', 'normalizer', 'placeholder', 'showCount'], template: '<div class="treeselect-stub" />' }
    },
    mocks: { $modal: modal, dict }
  })
  return { wrapper, modal }
}

describe('Dept 部门管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listDept.mockResolvedValue({ data: flatDepts })
    listDeptExcludeChild.mockResolvedValue({ data: [] })
    getDept.mockResolvedValue({
      data: { deptId: 101, parentId: 100, parentName: '总公司', deptName: '研发部', orderNum: 1, status: '0' }
    })
    delDept.mockResolvedValue({})
    addDept.mockResolvedValue({})
    updateDept.mockResolvedValue({})
  })

  test('创建时查询部门并构建树形结构', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listDept).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.deptList).toHaveLength(1)
    expect(wrapper.vm.deptList[0].children).toHaveLength(1)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('总公司')
    expect(wrapper.text()).toContain('研发部')
  })

  test('normalizer 移除空 children 并映射部门字段', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.normalizer({ deptId: 1, deptName: '法务部', children: [] })).toEqual({
      id: 1,
      label: '法务部',
      children: undefined
    })
  })

  test('新增支持根级与子级，并加载部门树选项', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    await flush()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加部门')
    expect(wrapper.vm.form.parentId).toBeUndefined()
    expect(wrapper.vm.form.status).toBe('0')
    expect(wrapper.vm.deptOptions).toHaveLength(1)

    wrapper.vm.handleAdd({ deptId: 100 })
    await flush()
    expect(wrapper.vm.form.parentId).toBe(100)
  })

  test('修改读取详情，无可用上级时回填父级占位', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleUpdate({ deptId: 101 })
    await flush()

    expect(getDept).toHaveBeenCalledWith(101)
    expect(listDeptExcludeChild).toHaveBeenCalledWith(101)
    expect(wrapper.vm.title).toBe('修改部门')
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.form.deptName).toBe('研发部')
    expect(wrapper.vm.deptOptions).toEqual([
      { deptId: 100, deptName: '总公司', children: [] }
    ])
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { parentId: 100, deptName: '法务部', orderNum: 3, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addDept).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { deptId: 101, parentId: 100, deptName: '研发部', orderNum: 1, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateDept).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('展开折叠切换后重新渲染表格', async () => {
    const { wrapper } = mountPage()
    await flush()
    expect(wrapper.vm.isExpandAll).toBe(true)

    wrapper.vm.toggleExpandAll()
    expect(wrapper.vm.isExpandAll).toBe(false)
    expect(wrapper.vm.refreshTable).toBe(false)

    await wrapper.vm.$nextTick()
    expect(wrapper.vm.refreshTable).toBe(true)
  })

  test('删除部门刷新列表，取消确认不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ deptId: 101, deptName: '研发部' })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      count: 1,
      countNoun: '个部门',
      content: '将删除部门「研发部」'
    }))
    expect(delDept).toHaveBeenCalledWith(101)
    expect(listDept).toHaveBeenCalledTimes(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete({ deptId: 101 })
    await flush()
    expect(delDept).toHaveBeenCalledTimes(1)
  })

  test('取消关闭弹窗并重置表单，重置查询重新加载', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.open = true
    wrapper.vm.form = { deptName: '临时部门' }

    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.status).toBe('0')
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listDept).toHaveBeenCalledTimes(2)
  })
})
