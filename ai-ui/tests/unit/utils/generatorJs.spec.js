'use strict'

/**
 * 表单生成器 JS 脚本单测：锁定 data/rules/options/props 生成与 file/dialog 方法模板。
 */
import { makeUpJs } from '@/utils/generator/js'

function baseConf(overrides) {
  return Object.assign({
    formRef: 'elForm',
    formModel: 'formData',
    formRules: 'rules',
    formBtns: true,
    fields: []
  }, overrides)
}

describe('makeUpJs 基础结构', () => {
  test('file 类型生成导出默认对象与提交/重置方法', () => {
    const script = makeUpJs(baseConf(), 'file')

    expect(script.startsWith('export default {')).toBe(true)
    expect(script).not.toContain('inheritAttrs')
    expect(script).toContain('formData: {')
    expect(script).toContain('rules: {')
    expect(script).toContain('components: {},')
    expect(script).toContain('props: [],')
    expect(script).toContain('submitForm() {')
    expect(script).toContain("this.$refs['elForm'].validate")
    expect(script).toContain('resetForm() {')
    expect(script).toContain("this.$refs['elForm'].resetFields()")
  })

  test('file 类型未开启 formBtns 时不生成提交/重置方法', () => {
    const script = makeUpJs(baseConf({ formBtns: false }), 'file')

    expect(script).not.toContain('submitForm() {')
    expect(script).not.toContain('resetForm() {')
  })

  test('dialog 类型生成 inheritAttrs 与弹窗相关方法', () => {
    const script = makeUpJs(baseConf(), 'dialog')

    expect(script).toContain('inheritAttrs: false,')
    expect(script).toContain('onOpen() {},')
    expect(script).toContain('onClose() {')
    expect(script).toContain("this.$emit('update:visible', false)")
    expect(script).toContain('handleConfirm() {')
    expect(script).not.toContain('submitForm() {')
  })
})

describe('makeUpJs 字段生成', () => {
  test('字符串默认值加引号，必填规则使用 placeholder 与 blur 触发', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        tag: 'el-input',
        vModel: 'name',
        label: '名称',
        defaultValue: '张三',
        placeholder: '请输入名称',
        required: true
      }]
    }), 'file')

    expect(script).toContain("name: '张三',")
    expect(script).toContain("{ required: true,  message: '请输入名称', trigger: 'blur' }")
  })

  test('数组默认值生成 array 类型规则，缺省 placeholder 时回退为字段提示', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        tag: 'el-checkbox-group',
        vModel: 'hobbies',
        label: '爱好',
        defaultValue: [],
        required: true
      }]
    }), 'file')

    expect(script).toContain('hobbies: [],')
    expect(script).toContain("{ required: true, type: 'array', message: '请至少选择一个hobbies', trigger: 'change' }")
  })

  test('regList 中的正则规则按 pattern 输出', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        tag: 'el-input',
        vModel: 'code',
        label: '编码',
        defaultValue: '',
        required: false,
        regList: [{ pattern: '/^[A-Z]+$/', message: '仅支持大写字母' }]
      }]
    }), 'file')

    expect(script).toContain("code: '',")
    expect(script).toContain("{ pattern: /^[A-Z]+$/, message: '仅支持大写字母', trigger: 'blur' }")
  })

  test('静态 options 直接序列化，动态 options 置空并生成获取方法', () => {
    const staticScript = makeUpJs(baseConf({
      fields: [{
        tag: 'el-select',
        vModel: 'status',
        defaultValue: null,
        options: [{ label: '启用', value: 1 }]
      }]
    }), 'file')
    expect(staticScript).toContain('statusOptions: [{"label":"启用","value":1}],')

    const dynamicScript = makeUpJs(baseConf({
      fields: [{
        tag: 'el-select',
        vModel: 'status',
        defaultValue: null,
        dataType: 'dynamic',
        options: [{ label: '启用', value: 1 }]
      }]
    }), 'file')
    expect(dynamicScript).toContain('statusOptions: [],')
    expect(dynamicScript).toContain('getStatusOptions() {')
    expect(dynamicScript).toContain('this.statusOptions')
  })

  test('动态 props 按 valueKey/labelKey/childrenKey 覆盖默认键名', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        tag: 'el-cascader',
        vModel: 'region',
        defaultValue: [],
        dataType: 'dynamic',
        valueKey: 'id',
        labelKey: 'name',
        childrenKey: 'sub',
        props: { props: { multiple: false } }
      }]
    }), 'file')

    expect(script).toContain('regionProps: {')
    expect(script).toContain('"value":"id"')
    expect(script).toContain('"label":"name"')
    expect(script).toContain('"children":"sub"')
  })

  test('el-upload 生成 action/fileList 变量、大小与类型校验及手动上传方法', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        tag: 'el-upload',
        vModel: 'file',
        defaultValue: null,
        action: '/upload',
        accept: 'image/*',
        fileSize: 2,
        sizeUnit: 'MB',
        'auto-upload': false
      }]
    }), 'file')

    expect(script).toContain("fileAction: '/upload',")
    expect(script).toContain('filefileList: [],')
    expect(script).toContain('fileBeforeUpload(file) {')
    expect(script).toContain('let isRightSize = file.size / 1024 / 1024 < 2')
    expect(script).toContain('return isRightSize&&isAccept')
    expect(script).toContain('submitUpload() {')
    expect(script).toContain("this.$refs['file'].submit()")
  })

  test('rowFormItem 的 children 递归生成 data 与规则', () => {
    const script = makeUpJs(baseConf({
      fields: [{
        layout: 'rowFormItem',
        children: [{
          tag: 'el-input',
          vModel: 'nested',
          label: '子项',
          defaultValue: 'x',
          required: true,
          placeholder: '请输入'
        }]
      }]
    }), 'file')

    expect(script).toContain("nested: 'x',")
    expect(script).toContain("{ required: true,  message: '请输入', trigger: 'blur' }")
  })

  test('生成结果不改动原始配置对象', () => {
    const conf = baseConf({
      fields: [{
        tag: 'el-select',
        vModel: 'status',
        defaultValue: null,
        dataType: 'dynamic',
        options: [{ label: '启用', value: 1 }]
      }]
    })

    makeUpJs(conf, 'file')

    expect(conf.fields[0].options).toEqual([{ label: '启用', value: 1 }])
  })
})
