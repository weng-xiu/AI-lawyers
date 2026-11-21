'use strict'

/**
 * FileUpload 文件上传单测：锁定值回显、上传校验、聚合回传与删除逻辑。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import FileUpload from '@/components/FileUpload/index.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function createModal() {
  return { msgError: jest.fn(), loading: jest.fn(), closeLoading: jest.fn() }
}

function mountUpload(propsData = {}) {
  const modal = createModal()
  const wrapper = mount(FileUpload, {
    localVue,
    propsData: { drag: false, ...propsData },
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('FileUpload', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('字符串值拆分为文件列表并渲染下载链接', () => {
    const { wrapper } = mountUpload({ value: '/profile/a.pdf,/profile/b.docx' })

    expect(wrapper.vm.fileList).toHaveLength(2)
    expect(wrapper.vm.fileList[0].name).toBe('/profile/a.pdf')
    expect(wrapper.vm.fileList[0].uid).toBeTruthy()

    const items = wrapper.findAll('li.ele-upload-list__item-content')
    expect(items.at(0).find('a').attributes('href')).toBe('/dev-api/profile/a.pdf')
    expect(items.at(1).text()).toContain('b.docx')
  })

  test('上传前校验格式、逗号与大小', () => {
    const { wrapper, modal } = mountUpload()

    expect(wrapper.vm.handleBeforeUpload({ name: 'a.exe', size: 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('文件格式不正确，请上传doc/docx/xls/xlsx/ppt/pptx/txt/pdf格式文件!')

    expect(wrapper.vm.handleBeforeUpload({ name: 'a,b.pdf', size: 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('文件名不正确，不能包含英文逗号!')

    expect(wrapper.vm.handleBeforeUpload({ name: 'a.pdf', size: 6 * 1024 * 1024 })).toBe(false)
    expect(modal.msgError).toHaveBeenCalledWith('上传文件大小不能超过 5 MB!')

    expect(wrapper.vm.handleBeforeUpload({ name: 'a.pdf', size: 1024 })).toBe(true)
    expect(modal.loading).toHaveBeenCalledWith('正在上传文件，请稍候...')
    expect(wrapper.vm.number).toBe(1)
  })

  test('全部上传成功后合并列表并回传逗号串', () => {
    const { wrapper, modal } = mountUpload()
    wrapper.vm.number = 1

    wrapper.vm.handleUploadSuccess({ code: 200, fileName: '/profile/c.pdf' }, {})

    expect(wrapper.vm.fileList).toHaveLength(1)
    expect(wrapper.emitted('input').pop()).toEqual(['/profile/c.pdf'])
    expect(modal.closeLoading).toHaveBeenCalled()
  })

  test('上传失败时提示后端信息并移除临时文件', () => {
    const { wrapper, modal } = mountUpload()
    wrapper.vm.number = 2

    wrapper.vm.handleUploadSuccess({ code: 500, msg: '磁盘已满' }, { uid: 1 })

    expect(modal.msgError).toHaveBeenCalledWith('磁盘已满')
    expect(modal.closeLoading).toHaveBeenCalled()
    expect(wrapper.vm.number).toBe(1)
    expect(wrapper.emitted('input')).toBeUndefined()
  })

  test('删除文件后回传剩余地址', () => {
    const { wrapper } = mountUpload({ value: '/profile/a.pdf,/profile/b.pdf' })

    wrapper.vm.handleDelete(0)

    expect(wrapper.vm.fileList).toHaveLength(1)
    expect(wrapper.emitted('input').pop()).toEqual(['/profile/b.pdf'])
  })

  test('文件名截取与列表转字符串', () => {
    const { wrapper } = mountUpload()

    expect(wrapper.vm.getFileName('/profile/dir/a.pdf')).toBe('a.pdf')
    expect(wrapper.vm.getFileName('a.pdf')).toBe('a.pdf')
    expect(wrapper.vm.listToString([{ url: '/a.pdf' }, { url: '/b.pdf' }])).toBe('/a.pdf,/b.pdf')
    expect(wrapper.vm.listToString([])).toBe('')
  })

  test('禁用时隐藏上传入口但保留文件列表', () => {
    const { wrapper } = mountUpload({ disabled: true, value: '/profile/a.pdf' })

    expect(wrapper.find('.upload-file-uploader').exists()).toBe(false)
    expect(wrapper.findAll('.ele-upload-list__item-content')).toHaveLength(1)
  })
})
