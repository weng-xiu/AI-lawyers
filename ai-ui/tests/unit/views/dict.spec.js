'use strict'

/**
 * 字典类型单测：锁定列表加载、查询重置、多选、新增/修改提交、删除、导出与刷新缓存。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Dict from '@/views/system/dict/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import { listType, getType, delType, addType, updateType, refreshCache } from '@/api/system/dict/type'

jest.mock('@/api/system/dict/type', () => ({
  listType: jest.fn(),
  getType: jest.fn(),
  delType: jest.fn(),
  addType: jest.fn(),
  updateType: jest.fn(),
  refreshCache: jest.fn()
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
  { dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex', status: '0', remark: '性别列表', createTime: '2026-01-01' },
  { dictId: 2, dictName: '系统开关', dictType: 'sys_normal_disable', status: '0', remark: '开关列表', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const dispatch = jest.fn()
  const wrapper = mount(Dict, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'router-link': { template: '<a class="router-link-stub"><slot /></a>' },
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, download, dict, $store: { dispatch } }
  })
  return { wrapper, modal, download, dispatch }
}

describe('Dict 字典类型', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listType.mockResolvedValue({ rows, total: rows.length })
    getType.mockResolvedValue({ data: { dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex', status: '0' } })
    delType.mockResolvedValue({})
    addType.mockResolvedValue({})
    updateType.mockResolvedValue({})
    refreshCache.mockResolvedValue({})
  })

  test('创建时携带日期范围查询并结束 loading', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listType).toHaveBeenCalledTimes(1)
    expect(listType.mock.calls[0][0]).toEqual(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.typeList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('用户性别')
    expect(wrapper.text()).toContain('sys_user_sex')
  })

  test('查询重置页码，重置清空日期范围', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listType).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listType).toHaveBeenCalledTimes(3)
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
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('新增初始化表单，修改读取详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加字典类型')
    expect(wrapper.vm.form.status).toBe('0')

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getType).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改字典类型')
    expect(wrapper.vm.form.dictName).toBe('用户性别')
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { dictName: '案件类型', dictType: 'case_type', status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(addType).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex', status: '0' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateType).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ dictId: 2 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条字典' }))
    expect(delType).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delType).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('导出携带查询条件，刷新缓存后清理字典缓存', async () => {
    const { wrapper, modal, download, dispatch } = mountPage()
    await flush()

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'system/dict/type/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^type_\d+\.xlsx$/)
    )

    wrapper.vm.handleRefreshCache()
    await flush()
    expect(refreshCache).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('刷新成功')
    expect(dispatch).toHaveBeenCalledWith('dict/cleanDict')
  })
})
