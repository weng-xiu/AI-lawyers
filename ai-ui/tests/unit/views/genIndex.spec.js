'use strict'

/**
 * 代码生成列表单测：锁定列表加载、排序、生成/同步/预览/编辑/删除与查询重置。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Gen from '@/views/tool/gen/index.vue'
import { addDateRange } from '@/utils/ruoyi'
import { listTable, previewTable, delTable, genCode, synchDb } from '@/api/tool/gen'

jest.mock('@/api/tool/gen', () => ({
  listTable: jest.fn(),
  previewTable: jest.fn(),
  delTable: jest.fn(),
  genCode: jest.fn(),
  synchDb: jest.fn()
}))

jest.mock('highlight.js/lib/highlight', () => ({
  registerLanguage: jest.fn(),
  highlight: jest.fn(() => ({ value: '<span>code</span>' }))
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.directive('hasRole', {})
localVue.directive('clipboard', {})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { tableId: 1, tableName: 'sys_user', tableComment: '用户表', className: 'SysUser', genType: '0', createTime: '2026-01-01' },
  { tableId: 2, tableName: 'sys_dept', tableComment: '部门表', className: 'SysDept', genType: '1', genPath: '/opt/gen', createTime: '2026-01-02' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgError: jest.fn()
  }
  const zip = jest.fn()
  const openPage = jest.fn()
  const importShow = jest.fn()
  const createShow = jest.fn()
  const wrapper = mount(Gen, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'import-table': { name: 'ImportTable', methods: { show: importShow }, template: '<div class="import-table-stub" />' },
      'create-table': { name: 'CreateTable', methods: { show: createShow }, template: '<div class="create-table-stub" />' }
    },
    mocks: {
      $modal: modal,
      $download: { zip },
      $tab: { openPage },
      $route: { query: {} }
    }
  })
  return { wrapper, modal, zip, openPage, importShow, createShow }
}

describe('Gen 代码生成', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listTable.mockResolvedValue({ rows, total: rows.length })
    previewTable.mockResolvedValue({ data: { '/vm/java/domain.java.vm': 'class SysUser {}' } })
    delTable.mockResolvedValue({})
    genCode.mockResolvedValue({})
    synchDb.mockResolvedValue({})
  })

  test('创建时按默认排序查询并携带日期范围', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listTable).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      orderByColumn: 'createTime',
      isAsc: 'descending',
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.tableList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('sys_user')
    expect(wrapper.text()).toContain('部门表')
  })

  test('查询重置页码，重置清空日期范围并恢复默认排序', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']
    const sort = jest.fn()
    wrapper.vm.$refs.tables.sort = sort

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listTable).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(sort).toHaveBeenCalledWith('createTime', 'descending')
  })

  test('排序参数写入查询条件并重新查询', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSortChange({ prop: 'updateTime', order: 'ascending' })
    await flush()

    expect(wrapper.vm.queryParams.orderByColumn).toBe('updateTime')
    expect(wrapper.vm.queryParams.isAsc).toBe('ascending')
    expect(listTable).toHaveBeenCalledTimes(2)
  })

  test('多选联动编号、表名与按钮状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.tableNames).toEqual(['sys_user'])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.single).toBe(true)
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('生成代码按自定义路径与压缩包区分，未选择时提示', async () => {
    const { wrapper, modal, zip } = mountPage()
    await flush()

    wrapper.vm.handleGenTable({})
    expect(modal.msgError).toHaveBeenCalledWith('请选择要生成的数据')

    wrapper.vm.handleGenTable(rows[0])
    expect(zip).toHaveBeenCalledWith('/tool/gen/batchGenCode?tables=sys_user', 'ruoyi.zip')

    wrapper.vm.handleGenTable(rows[1])
    await flush()
    expect(genCode).toHaveBeenCalledWith('sys_dept')
    expect(modal.msgSuccess).toHaveBeenCalledWith('成功生成到自定义路径：/opt/gen')
  })

  test('同步数据库确认后执行，取消则不执行', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleSynchDb(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '确认要强制同步「sys_user」表结构吗？'
    }))
    expect(synchDb).toHaveBeenCalledWith('sys_user')
    expect(modal.msgSuccess).toHaveBeenCalledWith('同步成功')

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleSynchDb(rows[0])
    await flush()
    expect(synchDb).toHaveBeenCalledTimes(1)
  })

  test('预览打开弹窗并默认定位实体文件', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handlePreview(rows[0])
    await flush()

    expect(previewTable).toHaveBeenCalledWith(1)
    expect(wrapper.vm.preview.open).toBe(true)
    expect(wrapper.vm.preview.activeName).toBe('domain.java')
    expect(wrapper.vm.preview.data['/vm/java/domain.java.vm']).toBe('class SysUser {}')
  })

  test('代码高亮按文件后缀识别语言并回退空内容', () => {
    const { wrapper } = mountPage()
    const hljs = require('highlight.js/lib/highlight')

    expect(wrapper.vm.highlightedCode('class A {}', '/vm/java/domain.java.vm')).toBe('<span>code</span>')
    expect(hljs.highlight).toHaveBeenCalledWith('java', 'class A {}', true)

    hljs.highlight.mockReturnValue({ value: '' })
    expect(wrapper.vm.highlightedCode('', '/vm/vue/index.vue.vm')).toBe('&nbsp;')
  })

  test('编辑与打开导入/创建弹窗', async () => {
    const { wrapper, openPage, importShow, createShow } = mountPage()
    await flush()

    wrapper.vm.handleEditTable(rows[0])
    expect(openPage).toHaveBeenCalledWith('修改[sys_user]生成配置', '/tool/gen-edit/index/1', { pageNum: 1 })

    wrapper.vm.openImportTable()
    expect(importShow).toHaveBeenCalledTimes(1)

    wrapper.vm.openCreateTable()
    expect(createShow).toHaveBeenCalledTimes(1)
  })

  test('删除单行与批量删除按数量提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '张表' }))
    expect(delTable).toHaveBeenCalledWith(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delTable).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))
  })

  test('复制回调提示成功', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.clipboardSuccess()
    expect(modal.msgSuccess).toHaveBeenCalledWith('复制成功')
  })
})
