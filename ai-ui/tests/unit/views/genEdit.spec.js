'use strict'

/**
 * 代码生成编辑页单测：锁定表信息加载、字典/菜单下拉、表单校验汇总提交与字段拖拽排序。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import GenEdit from '@/views/tool/gen/editTable.vue'
import { handleTree } from '@/utils/ruoyi'
import { getGenTable, updateGenTable } from '@/api/tool/gen'
import { optionselect as getDictOptionselect } from '@/api/system/dict/type'
import { listMenu as getMenuTreeselect } from '@/api/system/menu'

jest.mock('@/api/tool/gen', () => ({
  getGenTable: jest.fn(),
  updateGenTable: jest.fn()
}))

jest.mock('@/api/system/dict/type', () => ({
  optionselect: jest.fn()
}))

jest.mock('@/api/system/menu', () => ({
  listMenu: jest.fn()
}))

jest.mock('sortablejs', () => ({
  __esModule: true,
  default: { create: jest.fn(() => ({ destroy: jest.fn() })) }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.prototype.handleTree = handleTree

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const columns = [
  { columnId: 1, columnName: 'user_id', columnComment: '用户ID', columnType: 'bigint', javaType: 'Long', javaField: 'userId', isInsert: '1', isEdit: '1', isList: '1', isQuery: '0', queryType: 'EQ', isRequired: '1', htmlType: 'input', sort: 1 },
  { columnId: 2, columnName: 'user_name', columnComment: '用户名称', columnType: 'varchar', javaType: 'String', javaField: 'userName', isInsert: '1', isEdit: '1', isList: '1', isQuery: '1', queryType: 'LIKE', isRequired: '1', htmlType: 'input', sort: 2 }
]

const tableInfo = {
  tableId: 7,
  tableName: 'sys_user',
  tableComment: '用户表',
  className: 'SysUser',
  tplCategory: 'crud',
  genType: '0'
}

function mountPage(options = {}) {
  const modal = { msgSuccess: jest.fn(), msgError: jest.fn() }
  const closeOpenPage = jest.fn()
  const wrapper = mount(GenEdit, {
    localVue,
    stubs: {
      'basic-info-form': { name: 'BasicInfoForm', template: '<div class="basic-info-stub" />' },
      'gen-info-form': { name: 'GenInfoForm', template: '<div class="gen-info-stub" />' }
    },
    mocks: {
      $modal: modal,
      $tab: { closeOpenPage },
      $route: options.route || { params: { tableId: 7 }, query: { pageNum: 2 } }
    }
  })
  return { wrapper, modal, closeOpenPage }
}

function attachForms(wrapper, options = {}) {
  const basicModel = options.basicModel || { tableName: 'sys_user', tableComment: '用户表' }
  const genModel = options.genModel || {
    tplCategory: 'crud',
    treeCode: 'dept_id',
    treeName: 'dept_name',
    treeParentCode: 'parent_id',
    parentMenuId: 1
  }
  wrapper.vm.$refs.basicInfo.$refs.basicInfoForm = {
    validate: options.basicValidate || (callback => callback(true)),
    model: basicModel
  }
  wrapper.vm.$refs.genInfo.$refs.genInfoForm = {
    validate: options.genValidate || (callback => callback(true)),
    model: genModel
  }
}

describe('GenEdit 代码生成编辑', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getGenTable.mockResolvedValue({ data: { rows: columns, info: tableInfo, tables: [tableInfo] } })
    getDictOptionselect.mockResolvedValue({
      data: [{ dictType: 'sys_normal_disable', dictName: '系统开关' }]
    })
    getMenuTreeselect.mockResolvedValue({
      data: [
        { menuId: 1, menuName: '系统管理' },
        { menuId: 2, parentId: 1, menuName: '用户管理' }
      ]
    })
    updateGenTable.mockResolvedValue({ code: 200, msg: '操作成功' })
  })

  test('创建时按路由表编号加载列、表信息、字典与菜单树', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getGenTable).toHaveBeenCalledWith(7)
    expect(getDictOptionselect).toHaveBeenCalledTimes(1)
    expect(getMenuTreeselect).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.columns).toHaveLength(2)
    expect(wrapper.vm.info).toEqual(tableInfo)
    expect(wrapper.vm.tables).toHaveLength(1)
    expect(wrapper.vm.dictOptions).toHaveLength(1)
    expect(wrapper.vm.dictOptions[0].dictType).toBe('sys_normal_disable')
    expect(wrapper.vm.menus).toHaveLength(1)
    expect(wrapper.vm.menus[0].menuName).toBe('系统管理')
    expect(wrapper.vm.menus[0].children[0].menuName).toBe('用户管理')
  })

  test('无表编号时不发起任何查询', async () => {
    mountPage({ route: { params: {}, query: {} } })
    await flush()

    expect(getGenTable).not.toHaveBeenCalled()
    expect(getDictOptionselect).not.toHaveBeenCalled()
    expect(getMenuTreeselect).not.toHaveBeenCalled()
  })

  test('提交合并表单并携带列与树参数，成功后关闭当前页', async () => {
    const { wrapper, modal, closeOpenPage } = mountPage()
    await flush()
    attachForms(wrapper)

    wrapper.vm.submitForm()
    await flush()

    expect(updateGenTable).toHaveBeenCalledWith(expect.objectContaining({
      tableName: 'sys_user',
      tableComment: '用户表',
      tplCategory: 'crud',
      columns: wrapper.vm.columns,
      params: {
        treeCode: 'dept_id',
        treeName: 'dept_name',
        treeParentCode: 'parent_id',
        parentMenuId: 1
      }
    }))
    expect(modal.msgSuccess).toHaveBeenCalledWith('操作成功')
    expect(closeOpenPage).toHaveBeenCalledWith({
      path: '/tool/gen',
      query: { t: expect.any(Number), pageNum: 2 }
    })
  })

  test('表单校验未通过时提示且不提交', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    attachForms(wrapper, { genValidate: callback => callback(false) })

    wrapper.vm.submitForm()
    await flush()

    expect(updateGenTable).not.toHaveBeenCalled()
    expect(modal.msgError).toHaveBeenCalledWith('表单校验未通过，请重新检查提交内容')
  })

  test('接口返回非成功码时保留当前页', async () => {
    const { wrapper, closeOpenPage } = mountPage()
    await flush()
    attachForms(wrapper)
    updateGenTable.mockResolvedValue({ code: 500, msg: '保存失败' })

    wrapper.vm.submitForm()
    await flush()

    expect(closeOpenPage).not.toHaveBeenCalled()
  })

  test('getFormPromise 将校验结果包装为 Promise', async () => {
    const { wrapper } = mountPage()
    await flush()

    await expect(wrapper.vm.getFormPromise({ validate: callback => callback(true) })).resolves.toBe(true)
    await expect(wrapper.vm.getFormPromise({ validate: callback => callback(false) })).resolves.toBe(false)
  })

  test('拖拽结束后调整列顺序并重排序号', async () => {
    const Sortable = require('sortablejs').default
    const { wrapper } = mountPage()
    await flush()
    const options = Sortable.create.mock.calls[0][1]

    expect(Sortable.create.mock.calls[0][1].handle).toBe('.allowDrag')
    wrapper.vm.columns = [
      { columnId: 1, sort: 1 },
      { columnId: 2, sort: 2 },
      { columnId: 3, sort: 3 }
    ]

    options.onEnd({ oldIndex: 0, newIndex: 2 })

    expect(wrapper.vm.columns.map(item => item.columnId)).toEqual([2, 3, 1])
    expect(wrapper.vm.columns.map(item => item.sort)).toEqual([1, 2, 3])
  })

  test('关闭时携带时间戳回到列表页', async () => {
    const { wrapper, closeOpenPage } = mountPage()
    await flush()

    wrapper.vm.close()

    expect(closeOpenPage).toHaveBeenCalledWith({
      path: '/tool/gen',
      query: { t: expect.any(Number), pageNum: 2 }
    })
  })
})
