'use strict'

/**
 * 代码生成器资源单测：覆盖 makeUpCss 的样式收集与 drawingDefault 默认模板结构。
 */
import { makeUpCss } from '@/utils/generator/css'
import drawingDefault from '@/utils/generator/drawingDefault'

describe('generator/css makeUpCss', () => {
  test('无匹配组件时返回空字符串', () => {
    expect(makeUpCss({ fields: [{ tag: 'el-input' }, { tag: 'el-select' }] })).toBe('')
  })

  test('收集 el-rate 与 el-upload 的补充样式', () => {
    const css = makeUpCss({ fields: [{ tag: 'el-rate' }, { tag: 'el-upload' }] })

    expect(css).toContain('.el-rate')
    expect(css).toContain('.el-upload__tip')
  })

  test('递归子组件且重复标签只保留一次', () => {
    const css = makeUpCss({
      fields: [
        { tag: 'el-rate' },
        { tag: 'el-row', children: [{ tag: 'el-rate' }, { tag: 'el-upload' }] }
      ]
    })

    expect(css).toBe([
      '.el-rate{display: inline-block; vertical-align: text-top;}',
      '.el-upload__tip{line-height: 1.2;}'
    ].join('\n'))
  })

  test('字段为空时安全返回空串', () => {
    expect(makeUpCss({ fields: [] })).toBe('')
  })
})

describe('generator/drawingDefault', () => {
  test('默认模板为手机号输入项', () => {
    expect(Array.isArray(drawingDefault)).toBe(true)
    expect(drawingDefault).toHaveLength(1)

    expect(drawingDefault[0]).toMatchObject({
      layout: 'colFormItem',
      tag: 'el-input',
      vModel: 'mobile',
      span: 24,
      required: true
    })
    expect(drawingDefault[0].style).toEqual({ width: '100%' })
    expect(drawingDefault[0].maxlength).toBe(11)
  })

  test('内置手机号正则可用于格式校验', () => {
    const reg = drawingDefault[0].regList[0]
    const pattern = new RegExp(reg.pattern.slice(1, -1))

    expect(reg.message).toBe('手机号格式错误')
    expect(pattern.test('13800138000')).toBe(true)
    expect(pattern.test('12345')).toBe(false)
  })
})
