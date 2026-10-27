'use strict'

/**
 * U4 表单防丢失单测：本地草稿保存/恢复、服务端草稿择优、清空与提交防重。
 */
jest.mock('@/api/lawyers/formDraft', () => ({
  getFormDraft: jest.fn(() => Promise.resolve({ data: null })),
  saveFormDraft: jest.fn(() => Promise.resolve({ code: 200 })),
  deleteFormDraft: jest.fn(() => Promise.resolve({ code: 200 }))
}))

import Vue from 'vue'
import { createDraftMixin } from '@/utils/draft'
import { getFormDraft, saveFormDraft, deleteFormDraft } from '@/api/lawyers/formDraft'

const KEY = 'draft:test'

function makeVm({ key = KEY, form, excludes = [], when = null } = {}) {
  return new Vue({
    mixins: [createDraftMixin({ key, formKey: 'form', excludes, when })],
    data() {
      return {
        form: form || { title: '', content: '', tags: [] }
      }
    }
  })
}

describe('U4 草稿自动保存', () => {
  let nowSpy

  beforeEach(() => {
    jest.useFakeTimers()
    nowSpy = jest.spyOn(Date, 'now').mockReturnValue(1000)
    localStorage.clear()
    jest.clearAllMocks()
  })

  afterEach(() => {
    jest.useRealTimers()
    nowSpy.mockRestore()
    localStorage.clear()
  })

  it('有内容时写入本地草稿并携带时间戳', () => {
    const vm = makeVm()
    vm.form.title = '劳动仲裁咨询'
    vm.draftSave()

    expect(JSON.parse(localStorage.getItem(KEY))).toEqual({
      ts: 1000,
      data: { title: '劳动仲裁咨询', content: '', tags: [] }
    })
  })

  it('表单全空时移除草稿，避免空草稿反复提示', () => {
    localStorage.setItem(KEY, JSON.stringify({ ts: 1, data: { title: '旧草稿' } }))
    const vm = makeVm()
    vm.draftSave()

    expect(localStorage.getItem(KEY)).toBeNull()
  })

  it('excludes 字段不写入草稿，避免主键/系统字段污染恢复', () => {
    const vm = makeVm({ excludes: ['tags'], form: { title: 'a', tags: ['x'] } })
    vm.draftSave()

    expect(JSON.parse(localStorage.getItem(KEY)).data).toEqual({ title: 'a' })
  })

  it('watch 防抖后自动保存，再节流同步服务端', async () => {
    const vm = makeVm()
    vm.form.title = '自动保存'
    await vm.$nextTick()

    jest.advanceTimersByTime(799)
    expect(localStorage.getItem(KEY)).toBeNull()

    jest.advanceTimersByTime(1)
    expect(localStorage.getItem(KEY)).not.toBeNull()

    jest.advanceTimersByTime(2000)
    expect(saveFormDraft).toHaveBeenCalledWith(KEY, localStorage.getItem(KEY))
  })

  it('when 条件不满足时不保存', async () => {
    const vm = makeVm({ when: () => false })
    vm.form.title = '编辑态不存草稿'
    await vm.$nextTick()

    jest.advanceTimersByTime(1000)
    expect(localStorage.getItem(KEY)).toBeNull()
  })
})

describe('U4 草稿恢复', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    localStorage.clear()
    getFormDraft.mockResolvedValue({ data: null })
  })

  it('无草稿时不打扰用户', async () => {
    const vm = makeVm()
    vm.$confirm = jest.fn()

    await expect(vm.draftRestorePrompt()).resolves.toBe(false)
    expect(vm.$confirm).not.toHaveBeenCalled()
  })

  it('按本地草稿恢复表单字段，忽略未知字段', async () => {
    localStorage.setItem(KEY, JSON.stringify({
      ts: 1000,
      data: { title: '本地草稿', unknownField: '旧版本字段' }
    }))
    const vm = makeVm()
    vm.$confirm = jest.fn(() => Promise.resolve())

    await expect(vm.draftRestorePrompt()).resolves.toBe(true)
    expect(vm.form.title).toBe('本地草稿')
    expect('unknownField' in vm.form).toBe(false)
    expect(deleteFormDraft).not.toHaveBeenCalled()
  })

  it('服务端草稿更新时优先恢复服务端版本', async () => {
    localStorage.setItem(KEY, JSON.stringify({ ts: 1000, data: { title: '本地旧草稿' } }))
    getFormDraft.mockResolvedValue({
      data: { content: JSON.stringify({ ts: 2000, data: { title: '服务端新草稿' } }) }
    })
    const vm = makeVm()
    vm.$confirm = jest.fn(() => Promise.resolve())

    await expect(vm.draftRestorePrompt()).resolves.toBe(true)
    expect(vm.form.title).toBe('服务端新草稿')
  })

  it('服务端不可用时降级为本地草稿，不阻断填写', async () => {
    localStorage.setItem(KEY, JSON.stringify({ ts: 1000, data: { title: '离线草稿' } }))
    getFormDraft.mockRejectedValue(new Error('network down'))
    const vm = makeVm()
    vm.$confirm = jest.fn(() => Promise.resolve())

    await expect(vm.draftRestorePrompt()).resolves.toBe(true)
    expect(vm.form.title).toBe('离线草稿')
  })

  it('用户选择丢弃时清理本地与服务端草稿', async () => {
    localStorage.setItem(KEY, JSON.stringify({ ts: 1000, data: { title: '待丢弃' } }))
    const vm = makeVm()
    vm.$confirm = jest.fn(() => Promise.reject('cancel'))

    await expect(vm.draftRestorePrompt()).resolves.toBe(false)
    expect(localStorage.getItem(KEY)).toBeNull()
    expect(deleteFormDraft).toHaveBeenCalledWith(KEY)
  })
})

describe('U4 提交防重与清理', () => {
  beforeEach(() => {
    localStorage.clear()
    jest.clearAllMocks()
  })

  it('lockSubmit/unlockSubmit 控制提交中状态', () => {
    const vm = makeVm()
    expect(vm.draftSubmitting).toBe(false)
    vm.lockSubmit()
    expect(vm.draftSubmitting).toBe(true)
    vm.unlockSubmit()
    expect(vm.draftSubmitting).toBe(false)
  })

  it('draftClear 同时删除本地与服务端草稿', () => {
    localStorage.setItem(KEY, JSON.stringify({ ts: 1, data: { title: 'x' } }))
    const vm = makeVm()
    vm.draftClear()

    expect(localStorage.getItem(KEY)).toBeNull()
    expect(deleteFormDraft).toHaveBeenCalledWith(KEY)
  })
})
