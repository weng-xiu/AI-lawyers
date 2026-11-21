'use strict'

/**
 * 参数配置单测：锁定列表加载、查询重置、多选、新增/修改提交、删除、导出与刷新缓存。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Config from '@/views/system/config/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import { listConfig, getConfig, delConfig, addConfig, updateConfig, refreshCache } from '@/api/system/config'

jest.mock('@/api/system/config', () => ({
  listConfig: jest.fn(),
  getConfig: jest.fn(),
  delConfig: jest.fn(),
  addConfig: jest.fn(),
  updateConfig: jest.fn(),
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
    sys_yes_no: [
      { label: '是', value: 'Y' },
      { label: '否', value: 'N' }
    ]
  }
}

const rows = [
  { configId: 1, configName: '主框架页-默认皮肤', configKey: 'sys.index.skinName', configValue: 'skin-blue', configType: 'Y', createTime: '2026-01-01' },
  { configId: 2, configName: '用户管理-账号初始密码', configKey: 'sys.user.initPassword', configValue: '123456', configType: 'Y', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(Config, {
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

describe('Config 参数配置', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listConfig.mockResolvedValue({ rows, total: rows.length })
    getConfig.mockResolvedValue({ data: { configId: 1, configName: '主框架页-默认皮肤', configKey: 'sys.index.skinName', configValue: 'skin-blue', configType: 'Y' } })
    delConfig.mockResolvedValue({})
    addConfig.mockResolvedValue({})
    updateConfig.mockResolvedValue({})
    refreshCache.mockResolvedValue({})
  })

  test('创建时携带日期范围查询并结束 loading', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listConfig).toHaveBeenCalledTimes(1)
    expect(listConfig.mock.calls[0][0]).toEqual(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.configList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('sys.index.skinName')
  })

  test('查询重置页码，重置清空日期范围', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listConfig).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listConfig).toHaveBeenCalledTimes(3)
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
    expect(wrapper.vm.title).toBe('添加参数')
    expect(wrapper.vm.form.configType).toBe('Y')

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getConfig).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改参数')
    expect(wrapper.vm.form.configKey).toBe('sys.index.skinName')
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { configName: '案件编号前缀', configKey: 'case.code.prefix', configValue: 'CASE', configType: 'Y' }
    wrapper.vm.submitForm()
    await flush()
    expect(addConfig).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { configId: 1, configName: '主框架页-默认皮肤', configKey: 'sys.index.skinName', configValue: 'skin-blue', configType: 'Y' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateConfig).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ configId: 1 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条配置' }))
    expect(delConfig).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delConfig).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('导出与刷新缓存分别调用对应能力', async () => {
    const { wrapper, modal, download } = mountPage()
    await flush()

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'system/config/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^config_\d+\.xlsx$/)
    )

    wrapper.vm.handleRefreshCache()
    await flush()
    expect(refreshCache).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('刷新成功')
  })
})
