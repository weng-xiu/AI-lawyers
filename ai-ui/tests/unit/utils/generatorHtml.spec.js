'use strict'

/**
 * 表单生成器 HTML 模板单测：锁定标签包装、布局包裹与各 Element 组件属性拼装逻辑。
 */
import {
  dialogWrapper,
  vueTemplate,
  vueScript,
  cssStyle,
  makeUpHtml
} from '@/utils/generator/html'

function baseConf(overrides) {
  return Object.assign({
    formRef: 'elForm',
    formModel: 'formData',
    formRules: 'rules',
    size: 'medium',
    labelPosition: 'right',
    labelWidth: 100,
    gutter: 15,
    disabled: false,
    formBtns: true,
    fields: []
  }, overrides)
}

function field(overrides) {
  return Object.assign({
    layout: 'colFormItem',
    tag: 'el-input',
    label: '名称',
    vModel: 'name',
    span: 24,
    required: true,
    placeholder: '请输入'
  }, overrides)
}

describe('html 标签包装', () => {
  test('dialogWrapper 生成带默认按钮的弹窗', () => {
    const str = dialogWrapper('<div class="inner" />')

    expect(str).toContain('<el-dialog')
    expect(str).toContain('@open="onOpen"')
    expect(str).toContain('@close="onClose"')
    expect(str).toContain('<div class="inner" />')
    expect(str).toContain('取消')
    expect(str).toContain('确定')
  })

  test('vueTemplate / vueScript / cssStyle 分别输出模板、脚本、样式块', () => {
    expect(vueTemplate('<el-form />')).toContain('<template>')
    expect(vueTemplate('<el-form />')).toContain('<el-form />')
    expect(vueScript('export default {}')).toContain('<script>')
    expect(vueScript('export default {}')).toContain('export default {}')
    expect(cssStyle('.a{}')).toContain('<style>')
    expect(cssStyle('.a{}')).toContain('.a{}')
  })
})

describe('makeUpHtml 表单拼装', () => {
  test('file 类型生成表单与提交/重置按钮', () => {
    const html = makeUpHtml(baseConf({ fields: [field()] }), 'file')

    expect(html).toContain('<el-form')
    expect(html).toContain('ref="elForm"')
    expect(html).toContain(':model="formData"')
    expect(html).toContain(':rules="rules"')
    expect(html).toContain('size="medium"')
    expect(html).toContain('label-width="100px"')
    expect(html).toContain('v-model="formData.name"')
    expect(html).toContain('placeholder="请输入"')
    expect(html).toContain('submitForm')
    expect(html).toContain('resetForm')
    // 右侧标签时省略 label-position
    expect(html).not.toContain('label-position=')
    // 全部 span 为 24 时不加 el-row
    expect(html).not.toContain('<el-row :gutter')
  })

  test('dialog 类型外层包裹弹窗，且非 file 类型无表单按钮', () => {
    const html = makeUpHtml(baseConf({ fields: [field()], formBtns: false }), 'dialog')

    expect(html.trim().startsWith('<el-dialog')).toBe(true)
    expect(html).toContain('Dialog Title')
    expect(html).not.toContain('submitForm')
  })

  test('labelPosition 非 right 与 disabled 时输出对应属性', () => {
    const html = makeUpHtml(baseConf({
      labelPosition: 'left',
      disabled: true,
      fields: [field()]
    }), 'file')

    expect(html).toContain('label-position="left"')
    expect(html).toContain(':disabled="true"')
  })

  test('存在非 24 栅格字段时用 el-row/el-col 包裹，按钮也包 el-col', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({ span: 12 })]
    }), 'file')

    expect(html).toContain('<el-row :gutter="15">')
    expect(html).toContain('<el-col :span="12">')
    expect(html).toContain('<el-col :span="24">')
  })

  test('字段独立 labelWidth 与默认值不同时输出 label-width', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({ labelWidth: 200 })]
    }), 'file')

    expect(html).toContain('label-width="200px"')
  })

  test('无 trigger 的标签且有 required 时输出 required 属性', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({ tag: 'el-button', vModel: 'btn', default: '查询', type: 'primary' })]
    }), 'file')

    expect(html).toContain('label="名称"')
    expect(html).toContain('prop="btn"')
    expect(html).toContain(' required>')
    expect(html).toContain('type="primary"')
    expect(html).toContain('查询')
  })

  test('rowFormItem 递归渲染子字段并输出 gutter', () => {
    const html = makeUpHtml(baseConf({
      fields: [{
        layout: 'rowFormItem',
        tag: 'el-row',
        type: 'default',
        justify: 'start',
        align: 'top',
        span: 24,
        gutter: 10,
        children: [field({ label: '子项', vModel: 'nested' })]
      }]
    }), 'file')

    expect(html).toContain('<el-row')
    expect(html).toContain('gutter="10"')
    expect(html).toContain('prop="nested"')
    expect(html).toContain('v-model="formData.nested"')
  })

  test('el-input 输出 maxlength/字数限制/只读/图标/前置后置等属性', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({
        maxlength: 20,
        'show-word-limit': true,
        readonly: true,
        'prefix-icon': 'el-icon-user',
        'suffix-icon': 'el-icon-search',
        'show-password': true,
        autosize: { minRows: 2, maxRows: 4 },
        prepend: 'Http://',
        append: '.com'
      })]
    }), 'file')

    expect(html).toContain(':maxlength="20"')
    expect(html).toContain('show-word-limit')
    expect(html).toContain('readonly')
    expect(html).toContain("prefix-icon='el-icon-user'")
    expect(html).toContain("suffix-icon='el-icon-search'")
    expect(html).toContain('show-password')
    expect(html).toContain(':autosize="{minRows: 2, maxRows: 4}"')
    expect(html).toContain('<template slot="prepend">Http://</template>')
    expect(html).toContain('<template slot="append">.com</template>')
  })

  test('el-select 有 options 时生成 el-option 循环', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-select',
        vModel: 'status',
        filterable: true,
        multiple: true,
        options: [{ label: '启用', value: 1 }]
      })]
    }), 'file')

    expect(html).toContain('filterable')
    expect(html).toContain('multiple')
    expect(html).toContain('v-for="(item, index) in statusOptions"')
  })

  test('单选/多选组按 optionType 与 border 选择按钮或普通样式', () => {
    const radioHtml = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-radio-group',
        vModel: 'radio',
        optionType: 'button',
        border: true,
        options: [{ label: 'A', value: 1 }]
      })]
    }), 'file')
    expect(radioHtml).toContain('<el-radio-button')
    expect(radioHtml).toContain('v-for="(item, index) in radioOptions"')

    const checkboxHtml = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-checkbox-group',
        vModel: 'checks',
        border: true,
        options: [{ label: 'A', value: 1 }]
      })]
    }), 'file')
    expect(checkboxHtml).toContain('<el-checkbox ')
    expect(checkboxHtml).toContain('v-for="(item, index) in checksOptions"')
  })

  test('el-switch 输出自定义文案与激活值', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-switch',
        vModel: 'enabled',
        'active-text': '是',
        'inactive-text': '否',
        'active-value': '1',
        'inactive-value': '0'
      })]
    }), 'file')

    expect(html).toContain('active-text="是"')
    expect(html).toContain('inactive-text="否"')
    expect(html).toContain(`:active-value='"1"'`)
    expect(html).toContain(`:inactive-value='"0"'`)
  })

  test('el-date-picker 非 date 类型输出 type，date 类型省略', () => {
    const rangeHtml = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-date-picker',
        vModel: 'range',
        type: 'daterange',
        'range-separator': '至'
      })]
    }), 'file')
    expect(rangeHtml).toContain('type="daterange"')
    expect(rangeHtml).toContain('range-separator="至"')

    const dateHtml = makeUpHtml(baseConf({
      fields: [field({ tag: 'el-date-picker', vModel: 'day', type: 'date' })]
    }), 'file')
    expect(dateHtml).not.toContain('type="date"')
  })

  test('el-upload 图片卡片模式输出加号图标与提示文案', () => {
    const html = makeUpHtml(baseConf({
      fields: [field({
        tag: 'el-upload',
        vModel: 'file',
        action: '/upload',
        'list-type': 'picture-card',
        showTip: true,
        fileSize: 2,
        sizeUnit: 'MB',
        accept: 'image/*'
      })]
    }), 'file')

    expect(html).toContain('el-icon-plus')
    expect(html).toContain('只能上传不超过 2MB 的image/*文件')
    expect(html).toContain(':action="fileAction"')
    expect(html).toContain(':file-list="filefileList"')
  })
})
