'use strict'

/**
 * ImageUpload 图片上传单测：锁定地址补全、图片校验、回传去前缀与预览。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ImageUpload from '@/components/ImageUpload/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function createModal() {
  return { msgError: jest.fn(), loading: jest.fn(), closeLoading: jest.fn() }
}

function mountUpload(propsData = {}) {
  const modal = createModal()
  const wrapper = mount(ImageUpload, {
    localVue,
    propsData: { drag: false, ...propsData },
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('ImageUpload', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('相对地址补全接口前缀，外链保持原样', () => {
    const relative = mountUpload({ value: '/profile/a.png' }).wrapper
    expect(relative.vm.fileList[0]).toMatchObject({
      name: '/dev-api/profile/a.png',
      url: '/dev-api/profile/a.png'
    })

    const external = mountUpload({ value: 'https://example.com/a.png' }).wrapper
    expect(external.vm.fileList[0].url).toBe('https://example.com/a.png')
  })

  test('上传前仅允许配置的图片格式且限制大小', () => {
    const { wrapper, modal } = mountUpload()

    expect(wrapper.vm.handleBeforeUpload({ name: 'a.txt', type: 'text/plain', size: 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('文件格式不正确，请上传png/jpg/jpeg图片格式文件!')

    expect(wrapper.vm.handleBeforeUpload({ name: 'a,png.png', type: 'image/png', size: 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('文件名不正确，不能包含英文逗号!')

    expect(wrapper.vm.handleBeforeUpload({ name: 'a.png', type: 'image/png', size: 6 * 1024 * 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('上传头像图片大小不能超过 5 MB!')

    wrapper.vm.handleBeforeUpload({ name: 'a.png', type: 'image/png', size: 1024 })
    expect(modal.loading).toHaveBeenCalledWith('正在上传图片，请稍候...')
    expect(wrapper.vm.number).toBe(1)
  })

  test('上传成功后合并列表并回传去除前缀的地址', () => {
    const { wrapper, modal } = mountUpload()
    wrapper.vm.number = 1

    wrapper.vm.handleUploadSuccess({ code: 200, fileName: '/dev-api/profile/a.png' }, {})

    expect(wrapper.vm.fileList).toHaveLength(1)
    expect(wrapper.emitted('input').pop()).toEqual(['/profile/a.png'])
    expect(modal.closeLoading).toHaveBeenCalled()
  })

  test('按文件名删除图片并回传剩余地址', () => {
    const { wrapper } = mountUpload({ value: '/profile/a.png,/profile/b.png' })

    wrapper.vm.handleDelete({ name: '/dev-api/profile/a.png' })

    expect(wrapper.vm.fileList).toHaveLength(1)
    expect(wrapper.emitted('input').pop()).toEqual(['/profile/b.png'])
  })

  test('点击图片卡片打开预览弹窗', () => {
    const { wrapper } = mountUpload()

    wrapper.vm.handlePictureCardPreview({ url: '/dev-api/profile/a.png' })

    expect(wrapper.vm.dialogImageUrl).toBe('/dev-api/profile/a.png')
    expect(wrapper.vm.dialogVisible).toBe(true)
  })

  test('超出数量与上传失败均给出提示', () => {
    const { wrapper, modal } = mountUpload({ limit: 1 })

    wrapper.vm.handleExceed()
    expect(modal.msgError).toHaveBeenCalledWith('上传文件数量不能超过 1 个!')

    wrapper.vm.handleUploadError()
    expect(modal.msgError).toHaveBeenCalledWith('上传图片失败，请重试')
    expect(modal.closeLoading).toHaveBeenCalled()
  })
})
