'use strict'

/**
 * CodeTypeDialog 代码生成类型弹窗单测：锁定文件名生成、校验与确认事件。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import CodeTypeDialog from '@/views/tool/build/CodeTypeDialog.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

function mountDialog(propsData = {}) {
  return mount(CodeTypeDialog, {
    localVue,
    propsData,
    attrs: { visible: true }
  })
}

describe('CodeTypeDialog', () => {
  test('打开弹窗且需要文件名时自动生成 vue 文件名', () => {
    const wrapper = mountDialog({ showFileName: true })

    wrapper.vm.onOpen()

    expect(wrapper.vm.formData.fileName).toMatch(/^\d+\.vue$/)
  })

  test('无需文件名时打开弹窗不填充', () => {
    const wrapper = mountDialog({ showFileName: false })

    wrapper.vm.onOpen()

    expect(wrapper.vm.formData.fileName).toBeUndefined()
  })

  test('校验通过后抛出 confirm 并关闭', async () => {
    const wrapper = mountDialog({ showFileName: true })
    await wrapper.vm.$nextTick()
    wrapper.vm.formData.fileName = 'demo.vue'

    wrapper.vm.handleConfirm()
    await flush()

    expect(wrapper.emitted('confirm').pop()).toEqual([{ fileName: 'demo.vue', type: 'file' }])
    expect(wrapper.emitted('update:visible').pop()).toEqual([false])
  })

  test('文件名为空时校验失败不抛出 confirm', async () => {
    const wrapper = mountDialog({ showFileName: true })
    await wrapper.vm.$nextTick()

    wrapper.vm.handleConfirm()
    await flush()

    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  test('切换生成类型后随确认事件透传', async () => {
    const wrapper = mountDialog({ showFileName: true })
    await wrapper.vm.$nextTick()
    wrapper.vm.formData.fileName = 'demo.vue'
    wrapper.vm.formData.type = 'dialog'

    wrapper.vm.handleConfirm()
    await flush()

    expect(wrapper.emitted('confirm').pop()).toEqual([{ fileName: 'demo.vue', type: 'dialog' }])
  })
})
