'use strict'

/**
 * Swagger 文档页单测：锁定内嵌地址拼接与 iframe 渲染。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Swagger from '@/views/tool/swagger/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

describe('Swagger', () => {
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

  test('使用接口前缀拼接 swagger 地址并渲染 iframe', () => {
    const wrapper = mount(Swagger, { localVue })

    expect(wrapper.vm.url).toBe('/dev-api/swagger-ui/index.html')
    expect(wrapper.find('iframe').attributes('src')).toBe('/dev-api/swagger-ui/index.html')
  })

  test('组件名为 Swagger', () => {
    const wrapper = mount(Swagger, { localVue })

    expect(wrapper.vm.$options.name).toBe('Swagger')
  })
})
