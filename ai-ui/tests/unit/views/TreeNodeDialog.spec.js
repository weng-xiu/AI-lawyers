'use strict'

/**
 * TreeNodeDialog 选项弹窗单测：锁定表单重置、类型推断与提交数值转换。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TreeNodeDialog from '@/views/tool/build/TreeNodeDialog.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountDialog() {
  return mount(TreeNodeDialog, { localVue, attrs: { visible: true } })
}

describe('TreeNodeDialog', () => {
  test('打开时重置表单字段', () => {
    const wrapper = mountDialog()
    wrapper.vm.formData.label = '旧值'
    wrapper.vm.formData.value = 'old'

    wrapper.vm.onOpen()

    expect(wrapper.vm.formData).toEqual({ label: undefined, value: undefined })
  })

  test('根据输入值自动切换字符串/数字类型', async () => {
    const wrapper = mountDialog()

    wrapper.vm.formData.value = '12'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.dataType).toBe('number')

    wrapper.vm.formData.value = 'abc'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.dataType).toBe('string')
  })

  test('校验通过后数字值转换并抛出 commit', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()
    wrapper.vm.formData.label = '选项一'
    wrapper.vm.formData.value = '3.5'
    wrapper.vm.dataType = 'number'

    wrapper.vm.handleConfirm()
    await flush()

    const payload = wrapper.emitted('commit').pop()[0]
    expect(payload).toMatchObject({ label: '选项一', value: 3.5, id: 100 })
    expect(wrapper.emitted('update:visible').pop()).toEqual([false])
  })

  test('必填项为空时校验失败不提交', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()

    wrapper.vm.handleConfirm()
    await flush()

    expect(wrapper.emitted('commit')).toBeUndefined()
  })

  test('取消仅请求关闭弹窗', () => {
    const wrapper = mountDialog()

    wrapper.vm.close()

    expect(wrapper.emitted('update:visible').pop()).toEqual([false])
    expect(wrapper.emitted('commit')).toBeUndefined()
  })
})
