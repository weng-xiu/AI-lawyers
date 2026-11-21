'use strict'

/**
 * 登录日志单测：锁定列表加载、查询重置、多选、删除/清空/解锁与导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Logininfor from '@/views/monitor/logininfor/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import { list, delLogininfor, cleanLogininfor, unlockLogininfor } from '@/api/monitor/logininfor'

jest.mock('@/api/monitor/logininfor', () => ({
  list: jest.fn(),
  delLogininfor: jest.fn(),
  cleanLogininfor: jest.fn(),
  unlockLogininfor: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
// main.js 全局挂载的工具方法
localVue.prototype.addDateRange = addDateRange
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

// 组件通过 dicts 选项声明字典；测试直接注入字典数据，聚焦页面逻辑
const dict = {
  type: {
    sys_common_status: [
      { label: '成功', value: '0' },
      { label: '失败', value: '1' }
    ]
  }
}

const rows = [
  {
    infoId: 1,
    userName: 'admin',
    ipaddr: '127.0.0.1',
    loginLocation: '内网',
    browser: 'Chrome',
    os: 'Windows',
    status: '0',
    msg: '登录成功',
    loginTime: '2026-01-01 10:00:00'
  },
  {
    infoId: 2,
    userName: 'lawyer',
    ipaddr: '127.0.0.2',
    loginLocation: '内网',
    browser: 'Firefox',
    os: 'macOS',
    status: '1',
    msg: '密码错误',
    loginTime: '2026-01-02 10:00:00'
  }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const wrapper = mount(Logininfor, {
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

describe('Logininfor 登录日志', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    list.mockResolvedValue({ rows, total: rows.length })
    delLogininfor.mockResolvedValue({})
    cleanLogininfor.mockResolvedValue({})
    unlockLogininfor.mockResolvedValue({})
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
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('lawyer')
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
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(sort).toHaveBeenCalledWith('loginTime', 'descending')
  })

  test('排序参数写入查询条件并重新查询', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSortChange({ prop: 'userName', order: 'ascending' })
    await flush()

    expect(wrapper.vm.queryParams.orderByColumn).toBe('userName')
    expect(wrapper.vm.queryParams.isAsc).toBe('ascending')
    expect(list).toHaveBeenCalledTimes(2)
  })

  test('多选联动 ids、单选禁用与用户名', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)
    expect(wrapper.vm.selectName).toEqual(['admin'])

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete({ infoId: 1 })
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条日志' }))
    expect(delLogininfor).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delLogininfor).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('清空日志与解锁用户分别调用对应接口', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleClean()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'clear' }))
    expect(cleanLogininfor).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('清空成功')

    wrapper.vm.selectName = 'admin'
    wrapper.vm.handleUnlock()
    await flush()
    expect(unlockLogininfor).toHaveBeenCalledWith('admin')
    expect(modal.msgSuccess).toHaveBeenCalledWith('用户admin解锁成功')
  })

  test('取消确认时不执行删除与清空', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.handleDelete({ infoId: 1 })
    wrapper.vm.handleClean()
    await flush()

    expect(delLogininfor).not.toHaveBeenCalled()
    expect(cleanLogininfor).not.toHaveBeenCalled()
  })

  test('导出携带查询条件与时间戳文件名', async () => {
    const { wrapper, download } = mountPage()
    await flush()

    wrapper.vm.handleExport()

    expect(download).toHaveBeenCalledWith(
      'monitor/logininfor/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^logininfor_\d+\.xlsx$/)
    )
  })
})
