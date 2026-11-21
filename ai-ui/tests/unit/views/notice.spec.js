'use strict'

/**
 * 通知公告单测：锁定列表加载、查询重置、多选、新增/修改提交、删除。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Notice from '@/views/system/notice/index.vue'
import { listNotice, getNotice, delNotice, addNotice, updateNotice } from '@/api/system/notice'

jest.mock('@/api/system/notice', () => ({
  listNotice: jest.fn(),
  getNotice: jest.fn(),
  delNotice: jest.fn(),
  addNotice: jest.fn(),
  updateNotice: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_notice_type: [
      { label: '通知', value: '1' },
      { label: '公告', value: '2' }
    ],
    sys_notice_status: [
      { label: '正常', value: '0' },
      { label: '关闭', value: '1' }
    ]
  }
}

const rows = [
  { noticeId: 1, noticeTitle: '系统维护通知', noticeType: '1', status: '0', createBy: 'admin', createTime: '2026-01-01' },
  { noticeId: 2, noticeTitle: '版本发布公告', noticeType: '2', status: '1', createBy: 'lawyer', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const wrapper = mount(Notice, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      editor: { props: ['value', 'minHeight'], template: '<textarea class="editor-stub" />' },
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, dict }
  })
  return { wrapper, modal }
}

describe('Notice 通知公告', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listNotice.mockResolvedValue({ rows, total: rows.length })
    getNotice.mockResolvedValue({ data: { noticeId: 1, noticeTitle: '系统维护通知', noticeType: '1', status: '0' } })
    delNotice.mockResolvedValue({})
    addNotice.mockResolvedValue({})
    updateNotice.mockResolvedValue({})
  })

  test('创建时查询公告列表并结束 loading', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listNotice).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.noticeList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('系统维护通知')
    expect(wrapper.text()).toContain('版本发布公告')
  })

  test('查询重置页码，重置清空表单', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listNotice).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listNotice).toHaveBeenCalledTimes(3)
  })

  test('多选联动 ids 与按钮禁用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange(rows)
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('新增初始化表单，修改读取详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加公告')
    expect(wrapper.vm.form.status).toBe('0')

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getNotice).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改公告')
    expect(wrapper.vm.form.noticeTitle).toBe('系统维护通知')
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { noticeTitle: '听证安排', noticeType: '1', status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addNotice).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { noticeId: 1, noticeTitle: '系统维护通知', noticeType: '1', status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateNotice).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除按数量提示，取消确认不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ noticeId: 2 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条公告' }))
    expect(delNotice).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delNotice).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))

    delNotice.mockClear()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete({ noticeId: 1 })
    await flush()
    expect(delNotice).not.toHaveBeenCalled()
  })
})
