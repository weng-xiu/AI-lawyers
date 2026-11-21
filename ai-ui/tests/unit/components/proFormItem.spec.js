'use strict'

/**
 * ProFormItem 单测：锁定栅格表单项的渲染、属性透传，以及与 el-form 的校验联动。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI, { Form, Input } from 'element-ui'
import ProFormItem from '@/components/ProForm/ProFormItem.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)
const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

// el-form-item 依赖父级 el-form 注入，因此统一挂在真实 Form 内渲染
function mountInForm(propsData = {}, withRules = false) {
  const Host = {
    name: 'ProFormItemHost',
    components: { ProFormItem, Form, Input },
    data() {
      return {
        model: { name: '' },
        rules: withRules
          ? { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }
          : {}
      }
    },
    render(h) {
      return h(Form, { ref: 'form', props: { model: this.model, rules: this.rules } }, [
        h(ProFormItem, { props: propsData }, [
          h(Input, {
            props: { value: this.model.name },
            on: { input: value => { this.model.name = value } }
          })
        ])
      ])
    }
  }
  return mount(Host, { localVue })
}

async function validate(wrapper) {
  try {
    await wrapper.vm.$refs.form.validate()
    return true
  } catch (e) {
    return false
  }
}

describe('ProFormItem', () => {
  test('渲染栅格列、标签与提示图标', () => {
    const wrapper = mountInForm({ label: '通道', labelTip: '选择接入通道' })

    expect(wrapper.findComponent(ProFormItem).classes()).toContain('el-col-24')
    expect(wrapper.find('.el-form-item__label').text()).toContain('通道')
    expect(wrapper.find('.pro-form-item__tip').exists()).toBe(true)
  })

  test('span/offset 透传为 el-col 栅格类名', () => {
    const wrapper = mountInForm({ span: 12, offset: 4 })
    const classes = wrapper.findComponent(ProFormItem).classes()

    expect(classes).toContain('el-col-12')
    expect(classes).toContain('el-col-offset-4')
  })

  test('默认栅格值为整行，响应式断点仅 xs 有默认值', () => {
    const props = wrapperProps()

    expect(props.span).toBe(24)
    expect(props.xs).toBe(24)
    expect(props.sm).toBeUndefined()
    expect(props.md).toBeUndefined()
    expect(props.lg).toBeUndefined()
    expect(props.xl).toBeUndefined()
  })

  test('label 为空时不渲染标签区域', () => {
    const wrapper = mountInForm()

    expect(wrapper.find('.el-form-item__label').exists()).toBe(false)
  })

  test('required 为 true 时标记必填星号', () => {
    const wrapper = mountInForm({ required: true })

    expect(wrapper.find('.el-form-item').classes()).toContain('is-required')
  })

  test('error 文案直接展示，showMessage 关闭后隐藏', () => {
    const wrapper = mountInForm({ error: '名称已存在' })
    expect(wrapper.text()).toContain('名称已存在')

    const silent = mountInForm({ error: '名称已存在', showMessage: false })
    expect(silent.text()).not.toContain('名称已存在')
  })

  test('prop + rules 接入 el-form 校验，填值后通过', async () => {
    const wrapper = mountInForm({ label: '名称', prop: 'name' }, true)
    await wrapper.vm.$nextTick()

    await expect(validate(wrapper)).resolves.toBe(false)
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('请输入名称')

    wrapper.vm.model.name = '劳动仲裁'
    await flushPromises()
    await expect(validate(wrapper)).resolves.toBe(true)
  })

  test('插槽内容正常渲染在表单项内', () => {
    const wrapper = mountInForm()

    expect(wrapper.find('.el-form-item .el-input input').exists()).toBe(true)
  })
})

function wrapperProps() {
  const wrapper = mountInForm()
  return wrapper.findComponent(ProFormItem).props()
}
