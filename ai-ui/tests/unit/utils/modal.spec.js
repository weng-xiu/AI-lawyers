'use strict'

/**
 * U2 危险操作确认单测：dangerConfirm 语义预设 / HTML 转义 / 影响条数 / 撤销提示。
 */
jest.mock('element-ui', () => {
  const Message = jest.fn()
  Message.success = jest.fn()
  Message.error = jest.fn()
  const MessageBox = { confirm: jest.fn(), alert: jest.fn(), prompt: jest.fn() }
  const Notification = jest.fn()
  Notification.success = jest.fn()
  Notification.info = jest.fn()
  Notification.warning = jest.fn()
  Notification.error = jest.fn()
  const Loading = { service: jest.fn() }
  return { Message, MessageBox, Notification, Loading }
})

import { Message, MessageBox } from 'element-ui'
import modal from '@/plugins/modal'

describe('modal.dangerConfirm', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    MessageBox.confirm.mockResolvedValue('confirm')
  })

  it('删除类默认使用破坏性语义并把默认焦点放在取消', () => {
    modal.dangerConfirm('将删除该条目')

    const [html, title, options] = MessageBox.confirm.mock.calls[0]
    expect(title).toBe('删除确认')
    expect(html).toContain('将删除该条目')
    expect(html).toContain('danger-confirm-tip')
    expect(options).toEqual(expect.objectContaining({
      confirmButtonText: '确认删除',
      type: 'error',
      focusCancel: true,
      focusConfirm: false,
      dangerouslyUseHTMLString: true
    }))
    expect(options.customClass).toContain('is-destructive')
    expect(options.confirmButtonClass).toBe('el-button--danger')
  })

  it('影响条数会以结构化行展示，且用户输入内容被转义', () => {
    modal.dangerConfirm({
      content: '删除用户 <img src=x onerror=alert(1)>',
      count: 3,
      countNoun: '个用户'
    })

    const [html] = MessageBox.confirm.mock.calls[0]
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(html).not.toContain('<img')
    expect(html).toContain('将影响 <b>3</b> 个用户')
  })

  it('发布类使用警示语义，可覆盖 tip 或关闭 tip', () => {
    modal.dangerConfirm({ actionType: 'publish', content: '发布公告', tip: null })

    const [html, title, options] = MessageBox.confirm.mock.calls[0]
    expect(title).toBe('发布确认')
    expect(html).not.toContain('danger-confirm-tip')
    expect(options).toEqual(expect.objectContaining({
      confirmButtonText: '确认发布',
      type: 'warning',
      focusCancel: false,
      focusConfirm: true
    }))
    expect(options.customClass).toContain('is-caution')
    expect(options.confirmButtonClass).toBe('el-button--primary')
  })

  it('清空/重置预设各自给出明确按钮文案', () => {
    modal.dangerConfirm({ actionType: 'clear', content: '清空日志' })
    modal.dangerConfirm({ actionType: 'reset', content: '重置配置' })

    expect(MessageBox.confirm.mock.calls[0][1]).toBe('清空确认')
    expect(MessageBox.confirm.mock.calls[0][2].confirmButtonText).toBe('确认清空')
    expect(MessageBox.confirm.mock.calls[1][1]).toBe('重置确认')
    expect(MessageBox.confirm.mock.calls[1][2].confirmButtonText).toBe('确认重置')
  })

  it('未知 actionType 回退为删除语义，避免误用弱确认', () => {
    modal.dangerConfirm({ actionType: 'unknown', content: 'x' })

    expect(MessageBox.confirm.mock.calls[0][1]).toBe('删除确认')
    expect(MessageBox.confirm.mock.calls[0][2].focusCancel).toBe(true)
  })

  it('取消/关闭时 reject，与既有 .catch 回滚语义一致', async () => {
    MessageBox.confirm.mockRejectedValueOnce('cancel')
    await expect(modal.dangerConfirm('x')).rejects.toBe('cancel')
  })
})

describe('modal.undo', () => {
  let el
  let close

  beforeEach(() => {
    jest.useFakeTimers()
    jest.clearAllMocks()
    el = document.createElement('div')
    el.innerHTML = '<div class="el-message__content"></div>'
    close = jest.fn()
    Message.mockReturnValue({ $el: el, close })
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  it('渲染倒计时按钮，点击后关闭并执行撤销动作', async () => {
    const onUndo = jest.fn(() => Promise.resolve())
    modal.undo({ message: '知识已发布', onUndo, duration: 30000 })

    jest.advanceTimersByTime(0)
    const btn = el.querySelector('.el-message-undo__btn')
    expect(btn).not.toBeNull()
    expect(btn.textContent).toBe('撤销（30s）')

    jest.advanceTimersByTime(1000)
    expect(btn.textContent).toBe('撤销（29s）')

    btn.click()
    expect(onUndo).toHaveBeenCalledTimes(1)
    expect(close).toHaveBeenCalledTimes(1)

    await Promise.resolve()
    expect(Message.success).toHaveBeenCalledWith('已撤销')
  })

  it('撤销失败给出可感知的错误提示', async () => {
    const onUndo = jest.fn(() => Promise.reject(new Error('网络异常')))
    modal.undo({ message: '已下线', onUndo, errorText: '撤销失败' })

    jest.advanceTimersByTime(0)
    el.querySelector('.el-message-undo__btn').click()

    await Promise.resolve()
    await Promise.resolve()
    expect(Message.error).toHaveBeenCalledWith('撤销失败：网络异常')
  })

  it('超过撤销窗口自动关闭，不再提供撤销入口', () => {
    modal.undo({ message: '已保存', duration: 3000 })

    jest.advanceTimersByTime(0)
    expect(el.querySelector('.el-message-undo__btn')).not.toBeNull()

    jest.advanceTimersByTime(3500)
    expect(close).toHaveBeenCalledTimes(1)
  })

  it('重复点击撤销只执行一次，防止重复回滚', () => {
    const onUndo = jest.fn(() => Promise.resolve())
    modal.undo({ message: '已处理', onUndo })

    jest.advanceTimersByTime(0)
    const btn = el.querySelector('.el-message-undo__btn')
    btn.click()
    btn.click()

    expect(onUndo).toHaveBeenCalledTimes(1)
  })
})
