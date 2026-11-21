'use strict'

/**
 * 咨询分类单测：锁定树形列表与列表回退、下拉树选项、新增修改提交、展开折叠与删除导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Category from '@/views/lawyers/category/index.vue'
import { handleTree } from '@/utils/ruoyi'
import {
  listConsultationCategory, getConsultationCategory, delConsultationCategory,
  addConsultationCategory, updateConsultationCategory, listConsultationCategoryTree
} from '@/api/lawyers/consultationCategory'

jest.mock('@/api/lawyers/consultationCategory', () => ({
  listConsultationCategory: jest.fn(),
  getConsultationCategory: jest.fn(),
  delConsultationCategory: jest.fn(),
  addConsultationCategory: jest.fn(),
  updateConsultationCategory: jest.fn(),
  listConsultationCategoryTree: jest.fn(),
  exportConsultationCategory: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.handleTree = handleTree
localVue.prototype.parseTime = value => (value ? '2026-11-01' : '')
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

const treeRows = [
  {
    categoryId: 1,
    categoryName: '婚姻家庭',
    orderNum: 1,
    status: '0',
    createTime: '2026-11-01',
    children: [{ categoryId: 11, parentId: 1, categoryName: '离婚纠纷', orderNum: 1, status: '0' }]
  }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(Category, {
    localVue,
    stubs: {
      'right-toolbar': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' },
      'user-select': { props: ['value'], template: '<div class="user-select-stub" />' }
    },
    mocks: { $modal: modal, download, dict }
  })
  return { wrapper, modal, download }
}

describe('Category 咨询分类', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listConsultationCategoryTree.mockResolvedValue({ data: treeRows })
    listConsultationCategory.mockResolvedValue({ rows: [{ categoryId: 1, parentId: 0, categoryName: '婚姻家庭', orderNum: 1, status: '0' }] })
    getConsultationCategory.mockResolvedValue({
      data: { categoryId: 11, parentId: 1, categoryName: '离婚纠纷', orderNum: 1, status: '0' }
    })
    delConsultationCategory.mockResolvedValue({})
    addConsultationCategory.mockResolvedValue({})
    updateConsultationCategory.mockResolvedValue({})
  })

  test('创建时加载树形分类与下拉树选项', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listConsultationCategoryTree).toHaveBeenCalledTimes(2)
    expect(wrapper.vm.categoryList).toEqual(treeRows)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.categoryOptions[0]).toEqual({ categoryId: 0, categoryName: '主类目' })
    expect(wrapper.vm.categoryOptions[1]).toEqual({ categoryId: 1, categoryName: '婚姻家庭' })
    expect(wrapper.vm.categoryOptions[2].categoryName).toContain('离婚纠纷')
    expect(wrapper.text()).toContain('婚姻家庭')
  })

  test('树接口返回空时回退列表接口并转换为树', async () => {
    listConsultationCategoryTree.mockResolvedValue({ data: [] })
    const { wrapper } = mountPage()
    await flush()

    expect(listConsultationCategory).toHaveBeenCalled()
    expect(wrapper.vm.categoryList).toHaveLength(1)
    expect(wrapper.vm.categoryList[0].categoryName).toBe('婚姻家庭')
  })

  test('树接口失败时回退列表接口并结束加载', async () => {
    listConsultationCategoryTree.mockRejectedValue(new Error('fail'))
    const { wrapper } = mountPage()
    await flush()

    expect(listConsultationCategory).toHaveBeenCalled()
    expect(wrapper.vm.categoryList).toHaveLength(1)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.categoryOptions[0]).toEqual({ categoryId: 0, categoryName: '主类目' })
  })

  test('normalizer 移除空 children 并映射字段', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.normalizer({ categoryId: 3, categoryName: '继承', children: [] })).toEqual({
      id: 3,
      label: '继承',
      children: undefined
    })
  })

  test('新增根级与子级分类并打开弹窗', () => {
    const { wrapper } = mountPage()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加咨询分类')
    expect(wrapper.vm.form.parentId).toBe(0)
    expect(wrapper.vm.form.status).toBe('0')

    wrapper.vm.handleAdd({ categoryId: 1 })
    expect(wrapper.vm.form.parentId).toBe(1)
  })

  test('修改读取分类详情并回填上级', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleUpdate({ categoryId: 11, parentId: 1 })
    await flush()

    expect(getConsultationCategory).toHaveBeenCalledWith(11)
    expect(wrapper.vm.form.categoryName).toBe('离婚纠纷')
    expect(wrapper.vm.title).toBe('修改咨询分类')
    expect(wrapper.vm.open).toBe(true)
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { parentId: 0, categoryName: '继承纠纷', orderNum: 2, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addConsultationCategory).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { categoryId: 11, parentId: 1, categoryName: '离婚纠纷', orderNum: 1 }
    wrapper.vm.submitForm()
    await flush()
    expect(updateConsultationCategory).toHaveBeenCalledWith(wrapper.vm.form)
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
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.refreshTable).toBe(true)
  })

  test('删除分类刷新列表，取消确认不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(treeRows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将删除咨询分类「婚姻家庭」',
      count: 1,
      countNoun: '个分类'
    }))
    expect(delConsultationCategory).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete(treeRows[0])
    await flush()
    expect(delConsultationCategory).toHaveBeenCalledTimes(1)
  })

  test('取消关闭弹窗并重置表单，支持导出', async () => {
    const { wrapper, download } = mountPage()
    await flush()
    wrapper.vm.open = true
    wrapper.vm.form = { categoryName: '临时分类' }

    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.status).toBe('0')
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'lawyers/category/export',
      expect.objectContaining({ categoryName: undefined }),
      expect.stringMatching(/^category_\d+\.xlsx$/)
    )
  })
})
