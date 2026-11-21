'use strict'

/**
 * 表单构建器单测：锁定组件克隆/新增、表单数据组装、代码生成导出、复制删除与页签组件切换。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Build from '@/views/tool/build/index.vue'
import drawingDefault from '@/utils/generator/drawingDefault'
import { inputComponents, layoutComponents, formConf } from '@/utils/generator/config'

jest.mock('vuedraggable', () => ({
  __esModule: true,
  default: {
    name: 'Draggable',
    props: ['list', 'animation', 'group', 'sort', 'draggable', 'clone'],
    render(h) {
      return h('div', { class: 'draggable-stub' }, this.$slots.default)
    }
  }
}))

jest.mock('@/utils/generator/render', () => ({
  __esModule: true,
  default: {
    name: 'Render',
    props: ['conf'],
    render(h) {
      return h('div', { class: 'render-stub' })
    }
  }
}))

jest.mock('clipboard', () => ({
  __esModule: true,
  default: jest.fn(() => ({ on: jest.fn() }))
}))

jest.mock('@/assets/logo/logo.png', () => 'logo.png', { virtual: true })

jest.mock('js-beautify', () => ({
  __esModule: true,
  default: { html: jest.fn((code, conf) => 'beautified:' + code) }
}))

jest.mock('@/utils/generator/html', () => ({
  makeUpHtml: jest.fn(() => '<el-form/>'),
  vueTemplate: jest.fn(code => `<template>${code}</template>`),
  vueScript: jest.fn(code => `<script>${code}</script>`),
  cssStyle: jest.fn(() => '<style>.a{}</style>')
}))

jest.mock('@/utils/generator/js', () => ({
  makeUpJs: jest.fn(() => 'export default {}')
}))

jest.mock('@/utils/generator/css', () => ({
  makeUpCss: jest.fn(() => '.a{}')
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))
const defaultSnapshot = JSON.parse(JSON.stringify(drawingDefault))

function mountPage() {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()) }
  const saveAs = jest.fn()
  const notify = jest.fn()
  const message = { error: jest.fn() }
  const wrapper = mount(Build, {
    localVue,
    stubs: {
      'svg-icon': true,
      'draggable-item': true,
      'right-panel': true,
      'code-type-dialog': true
    },
    mocks: { $modal: modal, $download: { saveAs }, $notify: notify, $message: message }
  })
  return { wrapper, modal, saveAs, notify, message }
}

describe('Build 表单构建器', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    drawingDefault.splice(0, drawingDefault.length, ...JSON.parse(JSON.stringify(defaultSnapshot)))
  })

  test('创建时初始化画布组件并注册剪贴板', async () => {
    const { wrapper } = mountPage()
    await flush()

    const Clipboard = require('clipboard').default
    expect(Clipboard).toHaveBeenCalledWith('#copyNode', expect.objectContaining({ text: expect.any(Function) }))
    expect(wrapper.vm.drawingList).toBe(drawingDefault)
    expect(wrapper.vm.activeData).toBe(drawingDefault[0])
    expect(wrapper.vm.activeId).toBe(drawingDefault[0].formId)
    expect(wrapper.vm.formConf).toBe(formConf)
    expect(wrapper.vm.inputComponents).toBe(inputComponents)
  })

  test('克隆单列组件生成编号、绑定字段与占位符', async () => {
    const { wrapper } = mountPage()
    await flush()
    const origin = inputComponents[0]

    const clone = wrapper.vm.cloneComponent(origin)

    expect(clone.formId).toBe(101)
    expect(clone.layout).toBe('colFormItem')
    expect(clone.vModel).toBe('field101')
    expect(clone.span).toBe(formConf.span)
    expect(clone.placeholder).toBe('请输入单行文本')
    expect(origin.vModel).toBeUndefined()
  })

  test('克隆行容器时移除标题并生成组件名', async () => {
    const { wrapper } = mountPage()
    await flush()
    const row = layoutComponents.find(item => item.layout === 'rowFormItem') || { layout: 'rowFormItem', label: '行容器' }

    const clone = wrapper.vm.cloneComponent(row)

    expect(clone.formId).toBe(101)
    expect(clone.componentName).toBe('row101')
    expect(clone.gutter).toBe(formConf.gutter)
    expect(clone.label).toBeUndefined()
  })

  test('点选组件追加到画布并激活', async () => {
    const { wrapper } = mountPage()
    await flush()
    const before = wrapper.vm.drawingList.length

    wrapper.vm.addComponent(inputComponents[0])

    expect(wrapper.vm.drawingList).toHaveLength(before + 1)
    expect(wrapper.vm.activeId).toBe(101)
    expect(wrapper.vm.activeData).toBe(wrapper.vm.drawingList[before])
  })

  test('拖拽跨容器结束时激活新组件', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.cloneComponent(inputComponents[0])

    wrapper.vm.onEnd({ from: 'left', to: 'center' })

    expect(wrapper.vm.activeId).toBe(101)
    expect(wrapper.vm.activeData.vModel).toBe('field101')
  })

  test('组装表单数据深拷贝画布组件', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.AssembleFormData()

    expect(wrapper.vm.formData.fields).toEqual(wrapper.vm.drawingList)
    expect(wrapper.vm.formData.fields).not.toBe(wrapper.vm.drawingList)
    expect(wrapper.vm.formData.labelWidth).toBe(formConf.labelWidth)
  })

  test('生成代码按操作类型分发到导出与运行', async () => {
    const { wrapper, saveAs } = mountPage()
    await flush()

    wrapper.vm.download()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.showFileName).toBe(true)
    wrapper.vm.generate({ type: 'download', fileName: 'form.vue' })
    expect(saveAs).toHaveBeenCalledWith(expect.any(Blob), 'form.vue')

    wrapper.vm.run()
    expect(wrapper.vm.showFileName).toBe(false)
    wrapper.vm.generate({ type: 'run' })
    expect(wrapper.vm.drawerVisible).toBe(true)

    wrapper.vm.copy()
    const click = jest.fn()
    const getElementById = jest.spyOn(document, 'getElementById').mockReturnValue({ click })
    wrapper.vm.generate({ type: 'copy' })
    expect(getElementById).toHaveBeenCalledWith('copyNode')
    expect(click).toHaveBeenCalled()
    getElementById.mockRestore()
  })

  test('生成 Vue 代码拼接模板脚本与样式', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.generateConf = { type: 'run' }

    const code = wrapper.vm.generateCode()

    expect(code).toContain('beautified:')
    expect(code).toContain('<template><el-form/></template>')
    expect(code).toContain('export default {}')
    expect(code).toContain('<style>.a{}</style>')
  })

  test('清空画布需确认，取消时保留组件', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.empty()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'clear' }))
    expect(wrapper.vm.drawingList).toEqual([])

    wrapper.vm.drawingList = [{ formId: 1 }]
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.empty()
    await flush()
    expect(wrapper.vm.drawingList).toHaveLength(1)
  })

  test('复制与删除画布组件并重新激活', async () => {
    const { wrapper } = mountPage()
    await flush()
    const parent = [{ formId: 6, layout: 'rowFormItem', children: [{ formId: 7, layout: 'colFormItem' }] }]

    wrapper.vm.drawingItemCopy(parent[0], parent)
    expect(parent).toHaveLength(2)
    expect(parent[1].formId).toBe(101)
    expect(parent[1].children[0].formId).toBe(102)
    expect(wrapper.vm.activeData).toBe(parent[1])

    wrapper.vm.drawingList = [{ formId: 9 }, { formId: 10 }]
    wrapper.vm.drawingItemDelete(0, wrapper.vm.drawingList)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.drawingList).toHaveLength(1)
    expect(wrapper.vm.activeData.formId).toBe(10)
  })

  test('切换组件标签时保留原字段与画布位置', async () => {
    const { wrapper } = mountPage()
    await flush()
    const origin = wrapper.vm.drawingList[0]
    const newTag = inputComponents[1]

    wrapper.vm.tagChange(newTag)

    expect(wrapper.vm.activeData.formId).toBe(origin.formId)
    expect(wrapper.vm.activeData.vModel).toBe(origin.vModel)
    expect(wrapper.vm.activeData.label).toBe(origin.label)
    expect(wrapper.vm.drawingList[0]).toBe(wrapper.vm.activeData)
  })

  test('修改标签文字时同步更新占位符', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.activeData.label = '名称'
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.activeData.placeholder).toBe('请输入名称')
  })
})
