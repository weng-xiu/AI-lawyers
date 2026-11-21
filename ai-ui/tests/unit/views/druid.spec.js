'use strict'

/**
 * Druid 监控页单测：锁定内嵌地址拼接与 iframe 渲染。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Druid from '@/views/monitor/druid/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('Druid', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterEach(() => {
    window.onresize = null
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('使用接口前缀拼接 druid 地址并渲染 iframe', () => {
    const wrapper = mount(Druid, { localVue })

    expect(wrapper.vm.url).toBe('/dev-api/druid/login.html')
    expect(wrapper.find('iframe').attributes('src')).toBe('/dev-api/druid/login.html')
  })

  test('组件名为 Druid', () => {
    const wrapper = mount(Druid, { localVue })

    expect(wrapper.vm.$options.name).toBe('Druid')
  })
})
