'use strict'

/**
 * CreateTable 建表弹窗单测：锁定空内容提示、成功回调与异常分支。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CreateTable from '@/views/tool/gen/createTable.vue'
import { createTable } from '@/api/tool/gen'

jest.mock('@/api/tool/gen', () => ({ createTable: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountCreateTable() {
  const msgError = jest.fn()
  const msgSuccess = jest.fn()
  const wrapper = mount(CreateTable, {
    localVue,
    mocks: { $modal: { msgError, msgSuccess } },
    attachTo: document.body
  })
  return { wrapper, msgError, msgSuccess }
}

describe('CreateTable', () => {
  beforeEach(() => {
    createTable.mockReset()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  test('show 打开弹窗', () => {
    const { wrapper } = mountCreateTable()

    wrapper.vm.show()

    expect(wrapper.vm.visible).toBe(true)
  })

  test('内容为空时提示且不请求接口', () => {
    const { wrapper, msgError } = mountCreateTable()

    wrapper.vm.handleCreateTable()

    expect(msgError).toHaveBeenCalledWith('请输入建表语句')
    expect(createTable).not.toHaveBeenCalled()
  })

  test('创建成功后提示、关闭弹窗并抛出 ok', async () => {
    createTable.mockResolvedValue({ code: 200, msg: '创建成功' })
    const { wrapper, msgSuccess } = mountCreateTable()
    wrapper.vm.visible = true
    wrapper.vm.content = 'CREATE TABLE demo (id int);'

    wrapper.vm.handleCreateTable()
    await flush()

    expect(createTable).toHaveBeenCalledWith({ sql: 'CREATE TABLE demo (id int);' })
    expect(msgSuccess).toHaveBeenCalledWith('创建成功')
    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.emitted('ok')).toHaveLength(1)
  })

  test('接口返回非 200 时保持弹窗并提示信息', async () => {
    createTable.mockResolvedValue({ code: 500, msg: 'SQL 语法错误' })
    const { wrapper, msgSuccess } = mountCreateTable()
    wrapper.vm.visible = true
    wrapper.vm.content = 'bad sql'

    wrapper.vm.handleCreateTable()
    await flush()

    expect(msgSuccess).toHaveBeenCalledWith('SQL 语法错误')
    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.emitted('ok')).toBeUndefined()
  })
})
