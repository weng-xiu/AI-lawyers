'use strict'

/**
 * 操作日志单测：锁定列表加载、字典回显、详情弹窗、多选、删除/清空与导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Operlog from '@/views/monitor/operlog/index.vue'
import { addDateRange, selectDictLabel } from '@/utils/ruoyi'
import { list, delOperlog, cleanOperlog } from '@/api/monitor/operlog'

jest.mock('@/api/monitor/operlog', () => ({
  list: jest.fn(),
  delOperlog: jest.fn(),
  cleanOperlog: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.selectDictLabel = selectDictLabel
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_oper_type: [
      { label: '新增', value: '1' },
      { label: '修改', value: '2' }
    ],
    sys_common_status: [
      { label: '成功', value: '0' },
      { label: '失败', value: '1' }
    ]
  }
}

const rows = [
  {
    operId: 1,
    title: '用户管理',
    businessType: '1',
    operName: 'admin',
    operIp: '127.0.0.1',
    operLocation: '内网',
    status: 0,
    operTime: '2026-01-01 10:00:00',
    costTime: 12
  },
  {
    operId: 2,
    title: '角色管理',
    businessType: '2',
    operName: 'lawyer',
    operIp: '127.0.0.2',
    operLocation: '内网',
    status: 1,
    operTime: '2026-01-02 10:00:00',
    costTime: 30,
    errorMsg: '参数校验失败'
  }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(Operlog, {
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

describe('Operlog 操作日志', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    list.mockResolvedValue({ rows, total: rows.length })
    delOperlog.mockResolvedValue({})
    cleanOperlog.mockResolvedValue({})
  })

  test('创建时携带日期范围查询并结束 loading', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(list).toHaveBeenCalledTimes(1)
    expect(list.mock.calls[0][0]).toEqual(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.list).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).toContain('12毫秒')
  })

  test('操作类型按字典回显，未命中保留原值', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.typeFormat({ businessType: '1' })).toBe('新增')
    expect(wrapper.vm.typeFormat({ businessType: '2' })).toBe('修改')
    expect(wrapper.vm.typeFormat({ businessType: '9' })).toBe('9')
  })

  test('查询重置页码，重置清空日期范围并恢复默认排序', async () => {
    const { wrapper } = mountPage()
    await flush()
    const sort = jest.fn()
    wrapper.vm.$refs.tables.sort = sort
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(list).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(sort).toHaveBeenCalledWith('operTime', 'descending')
  })

  test('排序参数写入查询条件并重新查询', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSortChange({ prop: 'costTime', order: 'descending' })
    await flush()

    expect(wrapper.vm.queryParams.orderByColumn).toBe('costTime')
    expect(wrapper.vm.queryParams.isAsc).toBe('descending')
    expect(list).toHaveBeenCalledTimes(2)
  })

  test('多选联动 ids 与批量禁用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0], rows[1]])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('查看详情打开弹窗并展示请求与异常信息', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleView(rows[1])
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.form).toBe(rows[1])
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('visible')).toBe(true)
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ operId: 1 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条日志' }))
    expect(delOperlog).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delOperlog).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('清空日志与导出分别调用对应能力', async () => {
    const { wrapper, modal, download } = mountPage()
    await flush()

    wrapper.vm.handleClean()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'clear' }))
    expect(cleanOperlog).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('清空成功')

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'monitor/operlog/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^operlog_\d+\.xlsx$/)
    )
  })

  test('取消确认时不执行删除与清空', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.handleDelete({ operId: 1 })
    wrapper.vm.handleClean()
    await flush()

    expect(delOperlog).not.toHaveBeenCalled()
    expect(cleanOperlog).not.toHaveBeenCalled()
  })
})
