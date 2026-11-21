'use strict'

/**
 * Editor 富文本单测：锁定 Quill 初始化、上传校验、图片插入与粘贴处理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Quill from 'quill'
import Editor from '@/components/Editor/index.vue'

jest.mock('quill', () => jest.fn(() => ({
  getModule: jest.fn(() => ({ addHandler: jest.fn() })),
  root: { addEventListener: jest.fn() },
  clipboard: { dangerouslyPasteHTML: jest.fn() },
  on: jest.fn(),
  getText: jest.fn(() => '纯文本'),
  getSelection: jest.fn(() => ({ index: 3 })),
  insertEmbed: jest.fn(),
  setSelection: jest.fn(),
  format: jest.fn()
})))

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountEditor(propsData = {}) {
  const message = { error: jest.fn() }
  const wrapper = mount(Editor, {
    localVue,
    propsData,
    mocks: { $message: message }
  })
  const instance = Quill.mock.results[Quill.mock.results.length - 1].value
  return { wrapper, message, instance }
}

describe('Editor', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('挂载时初始化 Quill 并注册内容变更事件', () => {
    const { wrapper, instance } = mountEditor()

    expect(Quill).toHaveBeenCalledWith(wrapper.vm.$refs.editor, expect.objectContaining({ theme: 'snow' }))
    expect(instance.clipboard.dangerouslyPasteHTML).toHaveBeenCalled()
    expect(instance.on).toHaveBeenCalledWith('text-change', expect.any(Function))
    expect(instance.on).toHaveBeenCalledWith('selection-change', expect.any(Function))
    expect(instance.on).toHaveBeenCalledWith('editor-change', expect.any(Function))
    expect(instance.root.addEventListener).toHaveBeenCalledWith('paste', expect.any(Function), true)
  })

  test('高度与最小高度转换为样式对象', () => {
    const { wrapper } = mountEditor({ height: 400, minHeight: 200 })

    expect(wrapper.vm.styles).toEqual({ minHeight: '200px', height: '400px' })
  })

  test('上传前校验图片类型与大小', () => {
    const { wrapper, message } = mountEditor()

    expect(wrapper.vm.handleBeforeUpload({ type: 'text/plain', size: 1024 })).toBe(false)
    expect(message.error).toHaveBeenCalledWith('图片格式错误!')

    expect(wrapper.vm.handleBeforeUpload({ type: 'image/png', size: 6 * 1024 * 1024 })).toBe(false)
    expect(message.error).toHaveBeenCalledWith('上传文件大小不能超过 5 MB!')

    expect(wrapper.vm.handleBeforeUpload({ type: 'image/png', size: 1024 })).toBe(true)
  })

  test('上传成功后在光标处插入图片', () => {
    const { wrapper, instance } = mountEditor()

    wrapper.vm.handleUploadSuccess({ code: 200, fileName: '/profile/a.png' })

    expect(instance.insertEmbed).toHaveBeenCalledWith(3, 'image', '/dev-api/profile/a.png')
    expect(instance.setSelection).toHaveBeenCalledWith(4)
  })

  test('上传失败给出错误提示', () => {
    const { wrapper, message } = mountEditor()

    wrapper.vm.handleUploadSuccess({ code: 500 })
    expect(message.error).toHaveBeenCalledWith('图片插入失败')

    wrapper.vm.handleUploadError()
    expect(message.error).toHaveBeenCalledTimes(2)
  })

  test('粘贴图片时拦截默认行为并上传', () => {
    const { wrapper } = mountEditor()
    const insertImage = jest.spyOn(wrapper.vm, 'insertImage')
    const file = { name: 'a.png' }
    const preventDefault = jest.fn()

    wrapper.vm.handlePasteCapture({
      clipboardData: { items: [{ type: 'image/png', getAsFile: () => file }] },
      preventDefault
    })

    expect(preventDefault).toHaveBeenCalled()
    expect(insertImage).toHaveBeenCalledWith(file)

    insertImage.mockClear()
    const plainPrevent = jest.fn()
    wrapper.vm.handlePasteCapture({
      clipboardData: { items: [{ type: 'text/plain' }] },
      preventDefault: plainPrevent
    })

    expect(plainPrevent).not.toHaveBeenCalled()
    expect(insertImage).not.toHaveBeenCalled()
  })

  test('外部 value 变化时同步到编辑器', async () => {
    const { wrapper, instance } = mountEditor()

    await wrapper.setProps({ value: '<p>新内容</p>' })

    expect(wrapper.vm.currentValue).toBe('<p>新内容</p>')
    expect(instance.clipboard.dangerouslyPasteHTML).toHaveBeenCalledWith('<p>新内容</p>')
  })
})
