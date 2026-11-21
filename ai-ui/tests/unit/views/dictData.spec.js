'use strict'

/**
 * 字典数据单测：锁定按类型加载、重置回默认类型、多选、新增/修改提交、删除与缓存清理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import DictData from '@/views/system/dict/data.vue'
import { listData, getData, delData, addData, updateData } from '@/api/system/dict/data'
import { optionselect, getType } from '@/api/system/dict/type'

jest.mock('@/api/system/dict/data', () => ({
  listData: jest.fn(),
  getData: jest.fn(),
  delData: jest.fn(),
  addData: jest.fn(),
  updateData: jest.fn()
}))

jest.mock('@/api/system/dict/type', () => ({
  optionselect: jest.fn(),
  getType: jest.fn()
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
  { dictCode: 1, dictLabel: '男', dictValue: '0', dictSort: 1, status: '0', listClass: 'default', cssClass: '', createTime: '2026-01-01' },
  { dictCode: 2, dictLabel: '女', dictValue: '1', dictSort: 2, status: '0', listClass: 'primary', cssClass: '', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const dispatch = jest.fn()
  const closeOpenPage = jest.fn()
  const wrapper = mount(DictData, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: {
      $modal: modal,
      download,
      dict,
      $store: { dispatch },
      $route: { params: { dictId: '1' } },
      $tab: { closeOpenPage }
    }
  })
  return { wrapper, modal, download, dispatch, closeOpenPage }
}

describe('DictData 字典数据', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getType.mockResolvedValue({ data: { dictType: 'sys_user_sex' } })
    optionselect.mockResolvedValue({ data: [{ dictId: 1, dictName: '用户性别', dictType: 'sys_user_sex' }] })
    listData.mockResolvedValue({ rows, total: rows.length })
    getData.mockResolvedValue({ data: { dictCode: 1, dictLabel: '男', dictValue: '0', dictSort: 1, status: '0', listClass: 'default' } })
    delData.mockResolvedValue({})
    addData.mockResolvedValue({})
    updateData.mockResolvedValue({})
  })

  test('按路由字典类型加载数据并缓存默认类型', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getType).toHaveBeenCalledWith('1')
    expect(wrapper.vm.queryParams.dictType).toBe('sys_user_sex')
    expect(wrapper.vm.defaultDictType).toBe('sys_user_sex')
    expect(listData).toHaveBeenCalledWith(expect.objectContaining({ dictType: 'sys_user_sex' }))
    expect(wrapper.vm.typeOptions).toHaveLength(1)
    expect(wrapper.vm.dataList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
  })

  test('重置恢复默认字典类型并重置页码', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.dictType = 'sys_job_group'
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listData).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(wrapper.vm.queryParams.dictType).toBe('sys_user_sex')
    expect(listData).toHaveBeenCalledTimes(3)
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

  test('新增继承当前字典类型，修改读取详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加字典数据')
    expect(wrapper.vm.form.dictType).toBe('sys_user_sex')
    expect(wrapper.vm.form.status).toBe('0')

    wrapper.vm.handleUpdate(rows[1])
    await flush()
    expect(getData).toHaveBeenCalledWith(2)
    expect(wrapper.vm.title).toBe('修改字典数据')
    expect(wrapper.vm.form.dictLabel).toBe('男')
  })

  test('提交后清理该类型字典缓存', async () => {
    const { wrapper, modal, dispatch } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { dictType: 'sys_user_sex', dictLabel: '未知', dictValue: '2', dictSort: 3, status: '0', listClass: 'default' }
    wrapper.vm.submitForm()
    await flush()
    expect(addData).toHaveBeenCalledWith(wrapper.vm.form)
    expect(dispatch).toHaveBeenCalledWith('dict/removeDict', 'sys_user_sex')
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { dictCode: 1, dictType: 'sys_user_sex', dictLabel: '男', dictValue: '0', dictSort: 1, status: '0', listClass: 'default' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateData).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除后刷新并清理缓存', async () => {
    const { wrapper, modal, dispatch } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ dictCode: 2 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条字典数据' }))
    expect(delData).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
    expect(dispatch).toHaveBeenCalledWith('dict/removeDict', 'sys_user_sex')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delData).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('导出携带查询条件，关闭返回字典类型列表', async () => {
    const { wrapper, download, closeOpenPage } = mountPage()
    await flush()

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'system/dict/data/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10, dictType: 'sys_user_sex' }),
      expect.stringMatching(/^data_\d+\.xlsx$/)
    )

    wrapper.vm.handleClose()
    expect(closeOpenPage).toHaveBeenCalledWith({ path: '/system/dict' })
  })
})
