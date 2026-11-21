'use strict'

/**
 * UserAvatar 头像裁剪单测：锁定头像来源、上传校验、裁剪上传与关闭恢复。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import store from '@/store'
import UserAvatar from '@/views/system/user/profile/userAvatar.vue'
import { uploadAvatar } from '@/api/system/user'

jest.mock('@/store', () => {
  const state = { avatar: 'avatar.png' }
  return {
    __esModule: true,
    default: {
      getters: state,
      commit: jest.fn((type, value) => {
        if (type === 'SET_AVATAR') state.avatar = value
      })
    }
  }
})
jest.mock('@/api/system/user', () => ({ uploadAvatar: jest.fn() }))
jest.mock('vue-cropper', () => ({
  VueCropper: {
    name: 'VueCropper',
    render: h => h('div', { class: 'cropper-stub' }),
    methods: {
      refresh: jest.fn(),
      rotateLeft: jest.fn(),
      rotateRight: jest.fn(),
      changeScale: jest.fn(),
      getCropBlob(callback) {
        callback(new Blob(['blob-data'], { type: 'image/png' }))
      }
    }
  }
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 10))

function mountAvatar() {
  const msgError = jest.fn()
  const msgSuccess = jest.fn()
  const wrapper = mount(UserAvatar, {
    localVue,
    mocks: { $modal: { msgError, msgSuccess } }
  })
  return { wrapper, msgError, msgSuccess }
}

describe('UserAvatar', () => {
  const baseApi = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    uploadAvatar.mockReset()
    store.commit.mockClear()
    store.getters.avatar = 'avatar.png'
    process.env.VUE_APP_BASE_API = '/dev-api'
  })

  afterAll(() => {
    process.env.VUE_APP_BASE_API = baseApi
  })

  test('头像取 store 并点击打开裁剪弹窗', () => {
    const { wrapper } = mountAvatar()

    expect(wrapper.vm.options.img).toBe('avatar.png')
    wrapper.find('.user-info-head').trigger('click')
    expect(wrapper.vm.open).toBe(true)
  })

  test('打开弹窗后显示裁剪器并监听窗口缩放', () => {
    const addEventListener = jest.spyOn(window, 'addEventListener')
    const { wrapper } = mountAvatar()

    wrapper.vm.modalOpened()

    expect(wrapper.vm.visible).toBe(true)
    expect(addEventListener).toHaveBeenCalledWith('resize', expect.any(Function))
    addEventListener.mockRestore()
  })

  test('非图片文件提示格式错误', () => {
    const { wrapper, msgError } = mountAvatar()

    wrapper.vm.beforeUpload({ type: 'text/plain' })

    expect(msgError).toHaveBeenCalledWith('文件格式错误，请上传图片类型,如：JPG，PNG后缀的文件。')
  })

  test('图片文件读取为 DataURL 并记录文件名', async () => {
    const { wrapper } = mountAvatar()
    const file = new File(['fake-image'], 'avatar.png', { type: 'image/png' })

    wrapper.vm.beforeUpload(file)
    await flush()

    expect(wrapper.vm.options.img).toMatch(/^data:image\/png/)
    expect(wrapper.vm.options.filename).toBe('avatar.png')
  })

  test('裁剪上传成功后更新头像并提示', async () => {
    uploadAvatar.mockResolvedValue({ imgUrl: '/profile/avatar.png' })
    const { wrapper, msgSuccess } = mountAvatar()
    wrapper.vm.open = true
    wrapper.vm.visible = true
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()

    wrapper.vm.uploadImg()
    await flush()

    expect(uploadAvatar).toHaveBeenCalledWith(expect.any(FormData))
    const formData = uploadAvatar.mock.calls[0][0]
    expect(formData.get('avatarfile')).toBeInstanceOf(Blob)
    expect(store.commit).toHaveBeenCalledWith('SET_AVATAR', '/dev-api/profile/avatar.png')
    expect(wrapper.vm.options.img).toBe('/dev-api/profile/avatar.png')
    expect(msgSuccess).toHaveBeenCalledWith('修改成功')
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.visible).toBe(false)
  })

  test('关闭弹窗恢复 store 头像', () => {
    const { wrapper } = mountAvatar()
    wrapper.vm.options.img = 'temp.png'
    wrapper.vm.visible = true

    wrapper.vm.closeDialog()

    expect(wrapper.vm.options.img).toBe('avatar.png')
    expect(wrapper.vm.visible).toBe(false)
  })
})
