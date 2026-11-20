'use strict'

/**
 * 表单生成器渲染组件单测：验证 conf 到 vnode 的属性/子节点映射与 v-model 透传。
 */
import renderComponent from '@/utils/generator/render'

const render = renderComponent.render
const h = jest.fn((tag, data, children) => ({ tag, data, children }))

// 兼容 JSX 编译后 attribute 落在 attrs 或 props 的差异
function attrsOf(node) {
  return node.data.attrs || node.data.props
}

function renderConf(conf) {
  const self = { conf, $emit: jest.fn() }
  const vnode = render.call(self, h)
  return { vnode, self }
}

describe('render 组件定义', () => {
  test('声明 conf 属性并挂载 render 方法', () => {
    expect(renderComponent.props).toEqual(['conf'])
    expect(typeof renderComponent.render).toBe('function')
  })
})

describe('render 属性映射', () => {
  beforeEach(() => {
    h.mockClear()
  })

  test('vModel 写入 props.value 并透传 input 事件', () => {
    const { vnode, self } = renderConf({
      tag: 'el-input',
      vModel: 'name',
      defaultValue: '张三',
      placeholder: '请输入',
      style: { width: '100%' }
    })

    expect(vnode.tag).toBe('el-input')
    expect(vnode.data.props.value).toBe('张三')
    expect(vnode.data.attrs.placeholder).toBe('请输入')
    expect(vnode.data.style).toEqual({ width: '100%' })

    vnode.data.on.input('新值')
    expect(self.$emit).toHaveBeenCalledWith('input', '新值')
  })

  test('isAttr 登记的属性放入 attrs', () => {
    const { vnode } = renderConf({
      tag: 'el-input',
      vModel: 'code',
      defaultValue: '',
      maxlength: 10
    })

    expect(vnode.data.attrs.maxlength).toBe(10)
    expect(vnode.data.props.value).toBe('')
  })
})

describe('render 子节点生成', () => {
  beforeEach(() => {
    h.mockClear()
  })

  test('el-button 的 default 文本作为子节点', () => {
    const { vnode } = renderConf({
      tag: 'el-button',
      vModel: 'btn',
      default: '提交'
    })

    expect(vnode.children).toEqual(['提交'])
  })

  test('el-select 按 options 生成 el-option 列表', () => {
    const { vnode } = renderConf({
      tag: 'el-select',
      vModel: 'status',
      options: [
        { label: '启用', value: 1, disabled: true },
        { label: '停用', value: 2, disabled: false }
      ]
    })

    expect(Array.isArray(vnode.children[0])).toBe(true)
    expect(vnode.children[0]).toHaveLength(2)
    expect(vnode.children[0][0].tag).toBe('el-option')
    expect(attrsOf(vnode.children[0][0]).label).toBe('启用')
    expect(attrsOf(vnode.children[0][0]).value).toBe(1)
  })

  test('el-radio-group 按 optionType 渲染按钮或普通单选', () => {
    const { vnode: buttonVnode } = renderConf({
      tag: 'el-radio-group',
      vModel: 'radio',
      optionType: 'button',
      options: [{ label: 'A', value: 1 }, { label: 'B', value: 2 }]
    })
    expect(buttonVnode.children[0].map(node => node.tag)).toEqual(['el-radio-button', 'el-radio-button'])

    const { vnode: normalVnode } = renderConf({
      tag: 'el-radio-group',
      vModel: 'radio',
      border: true,
      options: [{ label: 'A', value: 1 }]
    })
    expect(normalVnode.children[0][0].tag).toBe('el-radio')
    expect(attrsOf(normalVnode.children[0][0]).border).toBe(true)
  })

  test('el-checkbox-group 渲染 el-checkbox 并带 border', () => {
    const { vnode } = renderConf({
      tag: 'el-checkbox-group',
      vModel: 'checks',
      border: true,
      options: [{ label: 'A', value: 1 }]
    })

    expect(vnode.children[0][0].tag).toBe('el-checkbox')
    expect(attrsOf(vnode.children[0][0]).border).toBe(true)
    expect(attrsOf(vnode.children[0][0]).label).toBe(1)
  })

  test('el-upload 图片卡片模式生成加号图标，showTip 生成提示', () => {
    const { vnode } = renderConf({
      tag: 'el-upload',
      vModel: 'file',
      'list-type': 'picture-card',
      showTip: true,
      fileSize: 2,
      sizeUnit: 'MB',
      accept: 'image/*'
    })

    expect(vnode.children[0]).toHaveLength(2)
    expect(vnode.children[0][0].tag).toBe('i')
    expect(vnode.children[0][1].tag).toBe('div')
    expect(JSON.stringify(vnode.children[0][1].children)).toContain('只能上传不超过')
  })

  test('el-upload 非图片卡片模式生成按钮，无提示时仅一个子节点', () => {
    const { vnode } = renderConf({
      tag: 'el-upload',
      vModel: 'file',
      'list-type': 'text',
      buttonText: '点击上传'
    })

    expect(vnode.children[0]).toHaveLength(1)
    expect(vnode.children[0][0].tag).toBe('el-button')
  })
})
