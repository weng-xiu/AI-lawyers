'use strict'

/**
 * DictTag 字典标签单测：锁定默认样式文本、el-tag 类型、数组/逗号值归一与未匹配回显。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import DictTag from '@/components/DictTag/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const options = [
  { label: '正常', value: '0', raw: { listClass: 'success', cssClass: '' } },
  { label: '停用', value: '1', raw: { listClass: 'danger', cssClass: 'custom-tag' } },
  { label: '默认', value: '2', raw: { listClass: 'default', cssClass: '' } }
]

function mountTag(propsData) {
  return mount(DictTag, { localVue, propsData })
}

describe('DictTag', () => {
  test('命中的选项渲染为对应类型的 el-tag', () => {
    const wrapper = mountTag({ options, value: '0' })

    const tag = wrapper.find('.el-tag')
    expect(tag.exists()).toBe(true)
    expect(tag.text()).toBe('正常')
    expect(tag.classes()).toContain('el-tag--success')
  })

  test('自定义 cssClass 透传到标签', () => {
    const wrapper = mountTag({ options, value: '1' })

    expect(wrapper.find('.el-tag').classes()).toContain('custom-tag')
  })

  test('default 样式渲染为普通文本而非标签', () => {
    const wrapper = mountTag({ options, value: '2' })

    expect(wrapper.find('.el-tag').exists()).toBe(false)
    expect(wrapper.find('span').text()).toBe('默认')
  })

  test('数组值按字符串匹配多个标签', () => {
    const wrapper = mountTag({ options, value: ['0', 1] })

    expect(wrapper.findAll('.el-tag')).toHaveLength(2)
    expect(wrapper.text()).toContain('正常')
    expect(wrapper.text()).toContain('停用')
  })

  test('逗号分隔字符串值可拆分匹配', () => {
    const wrapper = mountTag({ options, value: '0,2' })

    expect(wrapper.findAll('.el-tag')).toHaveLength(1)
    expect(wrapper.text()).toContain('正常')
    expect(wrapper.text()).toContain('默认')
  })

  test('未匹配的值按 showValue 决定是否回显', () => {
    const shown = mountTag({ options, value: '9' })
    expect(shown.text()).toContain('9')

    const hidden = mountTag({ options, value: '9', showValue: false })
    expect(hidden.text()).toBe('')
  })

  test('空值与空字典均不渲染任何内容', () => {
    expect(mountTag({ options, value: null }).text()).toBe('')
    expect(mountTag({ options: [], value: '0' }).text()).toBe('')
  })
})
