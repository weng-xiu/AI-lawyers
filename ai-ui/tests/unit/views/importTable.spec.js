'use strict'

/**
 * ImportTable 导入表弹窗单测：锁定列表加载、查询重置与导入校验。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ImportTable from '@/views/tool/gen/importTable.vue'
import Pagination from '@/components/Pagination/index.vue'
import { listDbTable, importTable } from '@/api/tool/gen'

jest.mock('@/api/tool/gen', () => ({
  listDbTable: jest.fn(),
  importTable: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('pagination', Pagination)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const tables = [
  { tableName: 'law_case', tableComment: '案件表', createTime: '2026-01-01', updateTime: '2026-01-02' },
  { tableName: 'law_user', tableComment: '用户表', createTime: '2026-01-03', updateTime: '2026-01-04' }
]

function mountImport() {
  const msgError = jest.fn()
  const msgSuccess = jest.fn()
  const resetForm = jest.fn()
  const wrapper = mount(ImportTable, {
    localVue,
    mocks: {
      $modal: { msgError, msgSuccess },
      resetForm
    }
  })
  return { wrapper, msgError, msgSuccess, resetForm }
}

describe('ImportTable', () => {
  beforeEach(() => {
    listDbTable.mockReset()
    importTable.mockReset()
    listDbTable.mockResolvedValue({ code: 200, rows: tables, total: 2 })
  })

  test('show 加载可导入表列表并打开弹窗', async () => {
    const { wrapper } = mountImport()

    wrapper.vm.show()
    await flush()

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.vm.dbTableList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
  })

  test('查询重置页码后重新请求，重置清空查询条件', async () => {
    const { wrapper, resetForm } = mountImport()
    wrapper.vm.queryParams.pageNum = 3

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listDbTable).toHaveBeenCalledTimes(1)

    wrapper.vm.resetQuery()
    expect(resetForm).toHaveBeenCalledWith('queryForm')
    expect(listDbTable).toHaveBeenCalledTimes(2)
  })

  test('未勾选表时提示且不导入', () => {
    const { wrapper, msgError } = mountImport()

    wrapper.vm.handleImportTable()

    expect(msgError).toHaveBeenCalledWith('请选择要导入的表')
    expect(importTable).not.toHaveBeenCalled()
  })

  test('选择表后导入成功关闭弹窗并抛出 ok', async () => {
    importTable.mockResolvedValue({ code: 200, msg: '导入成功' })
    const { wrapper, msgSuccess } = mountImport()
    wrapper.vm.visible = true
    wrapper.vm.tables = ['law_case', 'law_user']

    wrapper.vm.handleImportTable()
    await flush()

    expect(importTable).toHaveBeenCalledWith({ tables: 'law_case,law_user' })
    expect(msgSuccess).toHaveBeenCalledWith('导入成功')
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.emitted('ok')).toHaveLength(1)
  })
})
