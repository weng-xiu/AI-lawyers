'use strict'

/**
 * ImagePreview 图片预览单测：锁定相对路径拼接、外链透传与宽高单位处理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ImagePreview from '@/components/ImagePreview/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountPreview(propsData) {
  return mount(ImagePreview, { localVue, propsData })
}

describe('ImagePreview', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('相对路径拼接接口前缀，多图拆分为预览列表', () => {
    const wrapper = mountPreview({ src: '/profile/a.png,/profile/b.png' })

    expect(wrapper.vm.realSrc).toBe('/dev-api/profile/a.png')
    expect(wrapper.vm.realSrcList).toEqual(['/dev-api/profile/a.png', '/dev-api/profile/b.png'])
    expect(wrapper.findComponent({ name: 'ElImage' }).props('previewSrcList')).toEqual([
      '/dev-api/profile/a.png',
      '/dev-api/profile/b.png'
    ])
  })

  test('外链图片不拼接接口前缀', () => {
    const wrapper = mountPreview({ src: 'https://example.com/a.png' })

    expect(wrapper.vm.realSrc).toBe('https://example.com/a.png')
    expect(wrapper.vm.realSrcList).toEqual(['https://example.com/a.png'])
  })

  test('数字宽高自动补 px，字符串宽高原样保留', () => {
    const numeric = mountPreview({ src: '/a.png', width: 120, height: 80 })
    expect(numeric.vm.realWidth).toBe('120px')
    expect(numeric.vm.realHeight).toBe('80px')

    const string = mountPreview({ src: '/a.png', width: '50%', height: '10rem' })
    expect(string.vm.realWidth).toBe('50%')
    expect(string.vm.realHeight).toBe('10rem')
  })

  test('未传 src 时预览地址为空', () => {
    const wrapper = mountPreview({})

    expect(wrapper.vm.realSrc).toBeUndefined()
    expect(wrapper.vm.realSrcList).toBeUndefined()
  })
})
