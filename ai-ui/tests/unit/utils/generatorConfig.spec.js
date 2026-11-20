'use strict'

/**
 * 表单生成器配置单测：锁定表单项清单与组件属性约定，
 * 防止新增/调整组件时遗漏 trigger 规则、tagIcon 或示例选项，导致生成器回显异常。
 */
import { formConf, inputComponents, selectComponents, layoutComponents, trigger } from '@/utils/generator/config'

describe('generator 表单全局配置', () => {
  test('表单配置包含渲染所需的核心字段', () => {
    expect(formConf).toMatchObject({
      formRef: 'elForm',
      formModel: 'formData',
      size: 'medium',
      labelPosition: 'right',
      labelWidth: 100,
      formRules: 'rules',
      gutter: 15,
      span: 24,
      formBtns: true
    })
  })
})

describe('generator 输入型组件', () => {
  test('四类输入组件齐全且字段完整', () => {
    expect(inputComponents.map(item => item.tagIcon)).toEqual(['input', 'textarea', 'password', 'number'])
    inputComponents.forEach(item => {
      expect(item.tag).toMatch(/^el-/)
      expect(item.label).toBeTruthy()
      expect(item.span).toBe(24)
      expect(item.regList).toEqual([])
      expect(item.changeTag).toBe(true)
      expect(item.document).toContain('element.eleme.cn')
    })
  })

  test('多行文本自带 4 行自适应高度', () => {
    const textarea = inputComponents.find(item => item.tagIcon === 'textarea')
    expect(textarea).toMatchObject({ tag: 'el-input', type: 'textarea' })
    expect(textarea.autosize).toEqual({ minRows: 4, maxRows: 4 })
  })
})

describe('generator 选择型组件', () => {
  test('组件清单与图标保持稳定顺序', () => {
    expect(selectComponents.map(item => item.tagIcon)).toEqual([
      'select',
      'cascader',
      'radio',
      'checkbox',
      'switch',
      'slider',
      'time',
      'time-range',
      'date',
      'date-range',
      'rate',
      'color',
      'upload'
    ])
  })

  test('下拉/单选/多选默认提供两项示例选项', () => {
    const withOptions = selectComponents.filter(item =>
      ['el-select', 'el-radio-group', 'el-checkbox-group'].includes(item.tag)
    )
    expect(withOptions).toHaveLength(3)
    withOptions.forEach(item => {
      expect(item.options).toEqual([
        { label: '选项一', value: 1 },
        { label: '选项二', value: 2 }
      ])
    })
  })

  test('级联选择使用嵌套 props 且默认动态数据源', () => {
    const cascader = selectComponents.find(item => item.tag === 'el-cascader')
    expect(cascader.props).toEqual({ props: { multiple: false } })
    expect(cascader).toMatchObject({
      dataType: 'dynamic',
      labelKey: 'label',
      valueKey: 'value',
      childrenKey: 'children',
      separator: '/'
    })
    expect(cascader.options[0].children).toHaveLength(1)
  })

  test('滑块与开关默认值符合语义', () => {
    const slider = selectComponents.find(item => item.tag === 'el-slider')
    expect(slider).toMatchObject({ min: 0, max: 100, step: 1, range: false, defaultValue: null })

    const toggle = selectComponents.find(item => item.tag === 'el-switch')
    expect(toggle).toMatchObject({ 'active-value': true, 'inactive-value': false, defaultValue: false })
  })

  test('上传组件带大小限制与默认上传地址', () => {
    const upload = selectComponents.find(item => item.tag === 'el-upload')
    expect(upload).toMatchObject({
      name: 'file',
      'auto-upload': true,
      fileSize: 2,
      sizeUnit: 'MB',
      'list-type': 'text',
      multiple: false
    })
    expect(upload.action).toContain('jsonplaceholder')
  })
})

describe('generator 布局组件与校验触发', () => {
  test('行容器与按钮布局配置', () => {
    expect(layoutComponents.map(item => item.layout)).toEqual(['rowFormItem', 'colFormItem'])
    const row = layoutComponents[0]
    expect(row).toMatchObject({ layoutTree: true, justify: 'start', align: 'top', children: [] })
    const button = layoutComponents[1]
    expect(button).toMatchObject({ tag: 'el-button', type: 'primary', icon: 'el-icon-search' })
  })

  test('trigger 映射只包含存在的组件标签，且无输入类走 blur', () => {
    const allTags = [...inputComponents, ...selectComponents, ...layoutComponents].map(item => item.tag)
    Object.keys(trigger).forEach(tag => {
      expect(allTags).toContain(tag)
    })
    expect(trigger['el-input']).toBe('blur')
    expect(trigger['el-input-number']).toBe('blur')
    expect(trigger['el-select']).toBe('change')
  })
})
