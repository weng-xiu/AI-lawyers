'use strict'

/**
 * P8 组件单测：V3 ProDialog（通用弹窗外壳）
 * 覆盖：v-model/:visible.sync 显隐、默认底部按钮与 confirm/cancel 事件、
 *       confirmLoading 提交锁、dirty 脏改动二次确认拦截、footer 插槽自定义、
 *       与内部 ProForm 的自动校验联动（autoValidate）。
 *
 * ProForm 相关模块（store/getDicts）同 ProForm.spec 一并 mock，避免拖入真实依赖。
 */
jest.mock('@/store', () => ({
  getters: { dict: {} },
  state: {},
  dispatch: jest.fn()
}))
jest.mock('@/api/system/dict/data', () => ({
  getDicts: jest.fn(() => Promise.resolve({ data: [] }))
}))

import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProDialog from '@/components/ProDialog/index.vue'
import ProForm from '@/components/ProForm/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

function mountDialog(overrides = {}) {
  return mount(ProDialog, {
    localVue,
    propsData: Object.assign({ visible: true, title: '新增名单' }, overrides),
    slots: overrides.slots
  })
}

describe('ProDialog', () => {
  beforeEach(() => {
    jest.spyOn(console, 'warn').mockImplementation(() => {})
    jest.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => jest.restoreAllMocks())

  it('显隐跟随 visible prop（el-dialog 同步渲染/隐藏）', async () => {
    const wrapper = mountDialog({ visible: false })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-dialog__wrapper').element.style.display).toBe('none')
    await wrapper.setProps({ visible: true })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.el-dialog__wrapper').element.style.display).not.toBe('none')
    expect(wrapper.find('.el-dialog__title').text()).toBe('新增名单')
  })

  it('默认底部「取消/确定」按钮：确定 emit confirm，取消走 finalizeClose 三连 emit', async () => {
    const wrapper = mountDialog()
    const footerBtns = wrapper.findAll('.el-dialog__footer button')
    expect(footerBtns.length).toBe(2)
    await footerBtns.at(1).trigger('click') // 确定
    expect(wrapper.emitted('confirm')).toBeTruthy()
    await footerBtns.at(0).trigger('click') // 取消
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:visible')).toBeTruthy()
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false)
    expect(wrapper.emitted('input')).toBeTruthy()
    expect(wrapper.emitted('input')[0][0]).toBe(false)
  })

  it('confirmLoading 提交锁：确定与取消均被拦截', async () => {
    const wrapper = mountDialog({ confirmLoading: true })
    const footerBtns = wrapper.findAll('.el-dialog__footer button')
    await footerBtns.at(1).trigger('click')
    await footerBtns.at(0).trigger('click')
    expect(wrapper.emitted('confirm')).toBeFalsy()
    expect(wrapper.emitted('cancel')).toBeFalsy()
  })

  it('dirty 脏改动拦截：关闭需二次确认，确认后 emit discard+cancel，取消则保持', async () => {
    const wrapper = mountDialog({ dirty: true })
    // 拦截 $confirm（Element 全局方法）
    const confirmMock = jest.fn()
    wrapper.vm.$confirm = confirmMock

    // 第一次：用户点「继续填写」（reject）
    confirmMock.mockReturnValueOnce(Promise.reject())
    await wrapper.vm.attemptClose()
    await flushPromises()
    expect(confirmMock).toHaveBeenCalled()
    expect(wrapper.emitted('cancel')).toBeFalsy()

    // 第二次：用户点「放弃并关闭」（resolve）
    confirmMock.mockReturnValueOnce(Promise.resolve())
    await wrapper.vm.attemptClose()
    await flushPromises()
    expect(wrapper.emitted('discard')).toBeTruthy()
    expect(wrapper.emitted('cancel')).toBeTruthy()
    expect(wrapper.emitted('update:visible')[0][0]).toBe(false)
  })

  it('footer 插槽自定义后不再渲染默认按钮', async () => {
    const wrapper = mountDialog({
      slots: { footer: '<button class="custom-save">保存并继续</button>' }
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.custom-save').exists()).toBe(true)
    expect(wrapper.findAll('.el-dialog__footer button.el-button').length).toBe(0)
  })

  it('与 ProForm 联动：autoValidate 时校验失败不抛 confirm，通过后才抛', async () => {
    const model = { title: '' }
    const parent = mountDialog({ title: '新增' })
    // 独立挂载 ProForm 并注册到弹窗（等价于 ProForm mounted 沿 $parent 链自动注册）
    const form = mount(ProForm, {
      localVue,
      propsData: { model, fields: [{ prop: 'title', label: '标题', required: true }] }
    })
    parent.vm.registerForm(form.vm)
    // 校验失败：不 emit confirm
    await parent.vm.handleConfirm()
    await flushPromises()
    expect(parent.emitted('confirm')).toBeFalsy()
    // 校验通过：emit confirm
    model.title = '有效标题'
    await flushPromises()
    await parent.vm.handleConfirm()
    await flushPromises()
    expect(parent.emitted('confirm')).toBeTruthy()
  })

  it('onOpened 后自动聚焦内部 ProForm 第一个可输入字段（autoFocus）', async () => {
    const wrapper = mountDialog()
    const focusSpy = jest.fn()
    wrapper.vm.registerForm({ focusFirstField: focusSpy, clearValidate: jest.fn() })
    wrapper.vm.onOpened()
    expect(focusSpy).toHaveBeenCalled()
  })

  it('onClosed 自动清理内部 ProForm 校验状态', () => {
    const wrapper = mountDialog()
    const clearValidateSpy = jest.fn()
    wrapper.vm.registerForm({ focusFirstField: jest.fn(), clearValidate: clearValidateSpy })
    wrapper.vm.onClosed()
    expect(clearValidateSpy).toHaveBeenCalled()
    expect(wrapper.emitted('closed')).toBeTruthy()
  })
})
