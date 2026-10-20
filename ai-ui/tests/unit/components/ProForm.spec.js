'use strict'

/**
 * P8 组件单测：V3 ProForm（schema 驱动录入表单）
 * 覆盖：内置控件渲染、必填/正则校验、getDefaultModel 默认值、
 *       visible/disabled 联动函数、field-change 事件、字典选项（Vuex）、
 *       optionsLoader 远程选项、未知类型兜底提示、底部提交。
 *
 * 注意：ProForm 依赖全局 store（字典缓存）与 getDicts API，此处 mock 掉，
 *      避免拖入 axios/jssip 等真实依赖链。
 */
jest.mock('@/store', () => {
  // 可变字典缓存：测试内通过 require('@/store').getters.dict 直接注入
  const dictStore = {}
  return {
    getters: { dict: dictStore },
    state: {},
    dispatch: jest.fn()
  }
})
jest.mock('@/api/system/dict/data', () => ({
  getDicts: jest.fn(() => Promise.resolve({ data: [{ dictLabel: '语音', dictValue: '1' }, { dictLabel: '图文', dictValue: '2' }] }))
}))

import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProForm from '@/components/ProForm/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const baseFields = [
  { prop: 'title', label: '标题', required: true, max: 50 },
  { prop: 'phone', label: '手机号', pattern: /^1\d{10}$/, patternMessage: '手机号格式不正确' },
  { prop: 'channel', label: '渠道', type: 'select', options: [{ label: '热线', value: 'hotline' }] },
  { prop: 'enabled', label: '启用', type: 'switch' },
  { prop: 'remark', label: '备注', type: 'textarea', rows: 2 },
  { prop: 'divider1', type: 'divider', label: '其他' }
]

function mountForm(overrides = {}) {
  const model = overrides.model || { title: '', phone: '', channel: null, enabled: false, remark: '' }
  const props = Object.assign({ model, fields: baseFields }, overrides)
  delete props.model
  props.model = model
  return { wrapper: mount(ProForm, { localVue, propsData: props }), model }
}

describe('ProForm', () => {
  beforeEach(() => {
    jest.spyOn(console, 'warn').mockImplementation(() => {})
    jest.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => jest.restoreAllMocks())

  it('按 schema 渲染内置控件（input/select/switch/textarea/divider）', () => {
    const { wrapper } = mountForm()
    expect(wrapper.find('.el-input input').exists()).toBe(true)
    expect(wrapper.find('textarea').exists()).toBe(true)
    expect(wrapper.find('.el-switch').exists()).toBe(true)
    expect(wrapper.find('.el-select').exists()).toBe(true)
    expect(wrapper.find('.pro-form__divider').text()).toContain('其他')
  })

  it('必填校验：空值 validate 拒绝并展示错误提示，填值后通过', async () => {
    const { wrapper, model } = mountForm()
    await expect(wrapper.vm.validate()).rejects.toThrow('validation failed')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('请输入标题')
    model.title = '劳动仲裁咨询'
    await flushPromises()
    await expect(wrapper.vm.validate()).resolves.toBe(true)
  })

  it('正则校验：pattern 不匹配时提示 patternMessage', async () => {
    const { wrapper, model } = mountForm()
    model.phone = 'abc'
    await flushPromises()
    await expect(wrapper.vm.validate()).rejects.toThrow()
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('手机号格式不正确')
  })

  it('getDefaultModel 按控件类型生成默认值', () => {
    const { wrapper } = mountForm()
    const model = wrapper.vm.getDefaultModel()
    expect(model.title).toBe('')
    expect(model.channel).toBeNull()
    expect(model.enabled).toBe(false)
    expect(model.remark).toBe('')
    // 显式 default 优先生效；多选/checkbox 默认空数组
    const { wrapper: wrapper2 } = mountForm({
      fields: [
        { prop: 'level', type: 'select', default: 'A' },
        { prop: 'tags', type: 'checkbox', options: [] }
      ]
    })
    const model2 = wrapper2.vm.getDefaultModel()
    expect(model2.level).toBe('A')
    expect(model2.tags).toEqual([])
  })

  it('visible/disabled 联动函数随 model 变化生效', async () => {
    const model = { type: 'A', title: '', channel: null }
    const wrapper = mount(ProForm, {
      localVue,
      propsData: {
        model,
        fields: [
          { prop: 'title', label: '标题' },
          { prop: 'extra', label: '附加', visible: m => m.type === 'B' },
          { prop: 'channel', label: '渠道', type: 'select', options: [], disabled: m => m.type === 'A' }
        ]
      }
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).not.toContain('附加') // visible=false 隐藏
    const selectInput = wrapper.find('.el-select input')
    expect(selectInput.attributes('disabled')).toBeDefined()
    // 联动切换
    model.type = 'B'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('附加')
  })

  it('输入变化 emit field-change(prop, value, item)，并触发依赖字段的级联重载', async () => {
    const loader = jest.fn(() => Promise.resolve([{ id: 9, name: '机构九' }]))
    const { wrapper } = mountForm({
      fields: [
        { prop: 'province', label: '省份' },
        { prop: 'org', label: '机构', type: 'select', optionsLoader: loader, reloadOn: ['province'] }
      ]
    })
    await flushPromises()
    loader.mockClear()
    const item = { prop: 'province', label: '省份' }
    wrapper.vm.onFieldChange(item, '广东')
    await flushPromises()
    expect(wrapper.emitted('field-change')[0][0]).toBe('province')
    expect(wrapper.emitted('field-change')[0][1]).toBe('广东')
    expect(loader).toHaveBeenCalled() // reloadOn 级联触发
    // loader 结果归一化（name/id → label/value）
    expect(wrapper.vm.loaderOptions.org[0]).toMatchObject({ label: '机构九', value: 9 })
  })

  it('字典选项：声明 dict 的字段自动拉取，Vuex 缓存命中后映射为 {label,value}', async () => {
    const { getDicts } = require('@/api/system/dict/data')
    const store = require('@/store')
    const { wrapper } = mountForm({ fields: [{ prop: 'callType', label: '通话类型', type: 'select', dict: 'ai_call_type' }] })
    await flushPromises()
    // 未命中缓存时自动拉取字典
    expect(getDicts).toHaveBeenCalledWith('ai_call_type')
    // 模拟字典缓存回填（真实链路为 store.dispatch('dict/setDict')）
    store.getters.dict['ai_call_type'] = [{ dictLabel: '语音', dictValue: '1' }, { dictLabel: '图文', dictValue: '2' }]
    const options = wrapper.vm.getFieldOptions({ dict: 'ai_call_type' })
    expect(options[0]).toMatchObject({ label: '语音', value: '1' })
    expect(options[1]).toMatchObject({ label: '图文', value: '2' })
  })

  it('未知字段类型且未提供插槽时渲染兜底告警', () => {
    const { wrapper } = mountForm({ fields: [{ prop: 'magic', label: '魔法', type: 'unknown-type' }] })
    expect(wrapper.find('.pro-form__unsupported').text()).toContain('不支持的字段类型')
  })

  it('showFooter 开启后提交按钮校验通过才 emit submit(model)', async () => {
    const { wrapper, model } = mountForm({
      fields: [{ prop: 'title', label: '标题', required: true }],
      showFooter: true
    })
    const submitBtn = wrapper.find('.pro-form__footer button')
    await submitBtn.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('submit')).toBeFalsy() // 校验失败不提交
    model.title = '有效标题'
    await flushPromises()
    await submitBtn.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')[0][0].title).toBe('有效标题')
  })
})
