'use strict'

/**
 * 表单生成器右侧属性面板单测：锁定字段编辑器、默认值转换、时间/开关联动与选项维护。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import RightPanel from '@/views/tool/build/RightPanel.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function makeActive(overrides = {}) {
  return Object.assign({
    changeTag: false,
    vModel: 'userName',
    componentName: '输入框',
    label: '姓名',
    placeholder: '请输入姓名',
    span: 12,
    layout: 'colFormItem',
    tag: 'el-input',
    tagIcon: 'input',
    renderKey: 1,
    regList: [],
    options: [],
    defaultValue: ''
  }, overrides)
}

function mountPanel(activeData = makeActive(), formConf = { span: 12, formRef: 'elForm' }, showField = true) {
  return mount(RightPanel, {
    localVue,
    propsData: { showField, activeData, formConf },
    stubs: {
      draggable: true,
      'svg-icon': true,
      'tree-node-dialog': true,
      'icons-dialog': true
    }
  })
}

describe('RightPanel 表单属性面板', () => {
  test('默认展示组件属性并回显字段配置', async () => {
    const wrapper = mountPanel()
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.currentTab).toBe('field')
    expect(wrapper.vm.documentLink).toBe('https://element.eleme.cn/#/zh-CN/component/installation')
    expect(wrapper.text()).toContain('组件属性')
    expect(wrapper.text()).toContain('表单属性')

    const forms = wrapper.findAllComponents({ name: 'ElForm' })
    expect(forms.at(0).element.style.display).toBe('')
    expect(forms.at(1).element.style.display).toBe('none')
  })

  test('切换页签控制两块表单显隐，自定义文档地址生效', async () => {
    const activeData = makeActive({ document: 'https://example.com/doc' })
    const wrapper = mountPanel(activeData)

    expect(wrapper.vm.documentLink).toBe('https://example.com/doc')

    wrapper.vm.currentTab = 'form'
    await wrapper.vm.$nextTick()
    const forms = wrapper.findAllComponents({ name: 'ElForm' })
    expect(forms.at(0).element.style.display).toBe('none')
    expect(forms.at(1).element.style.display).toBe('')
  })

  test('默认值按数组、布尔、数字、字符串分别转换', () => {
    const wrapper = mountPanel()

    expect(wrapper.vm.setDefaultValue(['a', 'b'])).toBe('a,b')
    expect(wrapper.vm.setDefaultValue(true)).toBe('true')
    expect(wrapper.vm.setDefaultValue('string')).toBe('string')
    expect(wrapper.vm.setDefaultValue(10)).toBe(10)

    wrapper.vm.activeData.defaultValue = ['a', 'b']
    wrapper.vm.onDefaultValueInput('1,2')
    expect(wrapper.vm.activeData.defaultValue).toEqual([1, 2])

    wrapper.vm.activeData.defaultValue = ''
    wrapper.vm.onDefaultValueInput('true')
    expect(wrapper.vm.activeData.defaultValue).toBe(true)

    wrapper.vm.onDefaultValueInput('3.5')
    expect(wrapper.vm.activeData.defaultValue).toBe(3.5)

    wrapper.vm.onDefaultValueInput('abc')
    expect(wrapper.vm.activeData.defaultValue).toBe('abc')
  })

  test('开关值与选项值按数字字符串规则转换', () => {
    const wrapper = mountPanel()

    wrapper.vm.onSwitchValueInput('false', 'active-value')
    expect(wrapper.vm.activeData['active-value']).toBe(false)

    wrapper.vm.onSwitchValueInput('1', 'inactive-value')
    expect(wrapper.vm.activeData['inactive-value']).toBe(1)

    const item = { label: '', value: '' }
    wrapper.vm.setOptionValue(item, '2')
    expect(item.value).toBe(2)
    wrapper.vm.setOptionValue(item, 'text')
    expect(item.value).toBe('text')
  })

  test('时间类型切换同步格式、周类型使用日期格式', () => {
    const wrapper = mountPanel()

    wrapper.vm.dateTypeChange('week')
    expect(wrapper.vm.activeData['value-format']).toBe('yyyy-MM-dd')
    expect(wrapper.vm.activeData.format).toBe('yyyy 第 WW 周')
    expect(wrapper.vm.activeData.defaultValue).toBeNull()

    wrapper.vm.setTimeValue('yyyy-MM', 'month')
    expect(wrapper.vm.activeData['value-format']).toBe('yyyy-MM')
  })

  test('多选、范围、评分与颜色格式联动默认值', () => {
    const wrapper = mountPanel()
    const data = wrapper.vm.activeData

    wrapper.vm.multipleChange(true)
    expect(data.defaultValue).toEqual([])
    wrapper.vm.multipleChange(false)
    expect(data.defaultValue).toBe('')

    data.min = 1
    data.max = 5
    wrapper.vm.rangeChange(true)
    expect(data.defaultValue).toEqual([1, 5])
    wrapper.vm.rangeChange(false)
    expect(data.defaultValue).toBe(1)

    data['show-score'] = true
    wrapper.vm.rateTextChange(true)
    expect(data['show-score']).toBe(false)
    data['show-text'] = true
    wrapper.vm.rateScoreChange(true)
    expect(data['show-text']).toBe(false)

    wrapper.vm.colorFormatChange('rgba')
    expect(data.defaultValue).toBeNull()
    expect(data['show-alpha']).toBe(true)
    expect(typeof data.renderKey).toBe('number')
  })

  test('栅格变化同步表单配置，日期选项按是否范围区分', () => {
    const wrapper = mountPanel()

    wrapper.vm.spanChange(8)
    expect(wrapper.vm.formConf.span).toBe(8)

    const plain = mountPanel(makeActive({ tag: 'el-date-picker', type: 'date' }))
    expect(plain.vm.dateOptions).toHaveLength(5)

    const ranged = mountPanel(makeActive({ tag: 'el-date-picker', type: 'daterange', 'start-placeholder': '开始' }))
    expect(ranged.vm.dateOptions).toHaveLength(3)
  })

  test('维护选项、正则与树节点，图标与组件类型可回写', () => {
    const wrapper = mountPanel()
    const data = wrapper.vm.activeData

    wrapper.vm.addSelectItem()
    expect(data.options).toEqual([{ label: '', value: '' }])

    wrapper.vm.addReg()
    expect(data.regList).toEqual([{ pattern: '', message: '' }])

    wrapper.vm.addTreeItem()
    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.currentNode).toBe(data.options)

    const node = { parent: { data: { children: [{ id: 1 }, { id: 2 }] } } }
    wrapper.vm.remove(node, { id: 1 })
    expect(node.parent.data.children).toEqual([{ id: 2 }])

    wrapper.vm.addNode({ id: 3 })
    expect(data.options).toContainEqual({ id: 3 })

    wrapper.vm.openIconsDialog('prefix-icon')
    expect(wrapper.vm.iconsVisible).toBe(true)
    expect(wrapper.vm.currentIconModel).toBe('prefix-icon')
    wrapper.vm.setIcon('el-icon-user')
    expect(data['prefix-icon']).toBe('el-icon-user')

    wrapper.vm.tagChange('select')
    expect(wrapper.emitted('tag-change')[0][0].tagIcon).toBe('select')
  })
})
