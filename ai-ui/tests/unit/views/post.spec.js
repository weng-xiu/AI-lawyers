'use strict'

/**
 * 岗位管理单测：锁定列表加载、查询重置、多选、新增/修改提交、删除与导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Post from '@/views/system/post/index.vue'
import { listPost, getPost, delPost, addPost, updatePost } from '@/api/system/post'

jest.mock('@/api/system/post', () => ({
  listPost: jest.fn(),
  getPost: jest.fn(),
  delPost: jest.fn(),
  addPost: jest.fn(),
  updatePost: jest.fn()
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
  { postId: 1, postCode: 'ceo', postName: '董事长', postSort: 1, status: '0', createTime: '2026-01-01' },
  { postId: 2, postCode: 'se', postName: '项目经理', postSort: 2, status: '1', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(Post, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, download, dict }
  })
  return { wrapper, modal, download }
}

describe('Post 岗位管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listPost.mockResolvedValue({ rows, total: rows.length })
    getPost.mockResolvedValue({ data: { postId: 1, postCode: 'ceo', postName: '董事长', postSort: 1, status: '0' } })
    delPost.mockResolvedValue({})
    addPost.mockResolvedValue({})
    updatePost.mockResolvedValue({})
  })

  test('创建时查询岗位列表并结束 loading', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listPost).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.postList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('董事长')
    expect(wrapper.text()).toContain('项目经理')
  })

  test('查询重置页码，重置清空表单', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listPost).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listPost).toHaveBeenCalledTimes(3)
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

  test('新增初始化表单，修改读取详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加岗位')
    expect(wrapper.vm.form).toEqual(expect.objectContaining({ postSort: 0, status: '0' }))

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getPost).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改岗位')
    expect(wrapper.vm.form.postName).toBe('董事长')
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { postName: '法务专员', postCode: 'legal', postSort: 3, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addPost).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { postId: 1, postName: '董事长', postCode: 'ceo', postSort: 1, status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(updatePost).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ postId: 1 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '个岗位' }))
    expect(delPost).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delPost).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('取消确认时不删除并支持导出', async () => {
    const { wrapper, modal, download } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.handleDelete({ postId: 1 })
    await flush()
    expect(delPost).not.toHaveBeenCalled()

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'system/post/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^post_\d+\.xlsx$/)
    )
  })
})
