'use strict'

/**
 * P6 统一轮询工具单测：createPoller
 * 覆盖：立即首查、页面不可见暂停、恢复可见立即补拉、在途防重入、start/stop 生命周期。
 */
import { createPoller } from '@/utils/polling'

function setHidden(hidden) {
  Object.defineProperty(document, 'hidden', {
    configurable: true,
    value: hidden
  })
}

function fireVisibilityChange() {
  document.dispatchEvent(new Event('visibilitychange'))
}

// fake timers 下 promise 链（inFlight 复位）属于 microtask，需显式冲刷
const flushMicrotasks = async () => {
  for (let i = 0; i < 5; i++) await Promise.resolve()
}

describe('createPoller', () => {
  beforeEach(() => {
    jest.useFakeTimers()
    setHidden(false)
  })

  afterEach(() => {
    jest.useRealTimers()
    setHidden(false)
  })

  test('immediate: start 时立即执行一次，后续按周期执行', async () => {
    const fn = jest.fn(() => Promise.resolve())
    const poller = createPoller(fn, 5000, { immediate: true })
    poller.start()
    expect(fn).toHaveBeenCalledTimes(1)
    await flushMicrotasks()
    jest.advanceTimersByTime(5000)
    expect(fn).toHaveBeenCalledTimes(2)
    await flushMicrotasks()
    jest.advanceTimersByTime(5000)
    expect(fn).toHaveBeenCalledTimes(3)
    poller.stop()
  })

  test('页面不可见时跳过 tick，恢复可见立即补拉一次', () => {
    const fn = jest.fn(() => Promise.resolve())
    const poller = createPoller(fn, 5000)
    poller.start()
    expect(fn).not.toHaveBeenCalled()

    setHidden(true)
    jest.advanceTimersByTime(20000)
    expect(fn).not.toHaveBeenCalled()

    setHidden(false)
    fireVisibilityChange()
    expect(fn).toHaveBeenCalledTimes(1)
    poller.stop()
  })

  test('在途防重入：上一次 Promise 未结束时跳过本轮', async () => {
    let resolveFn
    const fn = jest.fn(() => new Promise(resolve => { resolveFn = resolve }))
    const poller = createPoller(fn, 1000)
    poller.start()

    jest.advanceTimersByTime(1000)
    expect(fn).toHaveBeenCalledTimes(1)

    // 请求仍在途，后续 3 个周期全部跳过
    jest.advanceTimersByTime(3000)
    expect(fn).toHaveBeenCalledTimes(1)

    // 结束在途请求后（microtask 冲刷复位 inFlight），下一个周期恢复调用
    resolveFn()
    await flushMicrotasks()
    jest.advanceTimersByTime(1000)
    expect(fn).toHaveBeenCalledTimes(2)
    poller.stop()
  })

  test('stop 后停止轮询且不再响应可见性变化，start 幂等', () => {
    const fn = jest.fn(() => Promise.resolve())
    const poller = createPoller(fn, 1000)
    poller.start()
    poller.start()
    jest.advanceTimersByTime(2000)
    const called = fn.mock.calls.length
    expect(called).toBeGreaterThan(0)

    poller.stop()
    jest.advanceTimersByTime(5000)
    setHidden(false)
    fireVisibilityChange()
    expect(fn).toHaveBeenCalledTimes(called)
  })

  test('fn 同步抛错不影响后续轮询周期', () => {
    const fn = jest.fn(() => { throw new Error('boom') })
    const poller = createPoller(fn, 1000)
    poller.start()
    expect(() => jest.advanceTimersByTime(3000)).not.toThrow()
    expect(fn).toHaveBeenCalledTimes(3)
    poller.stop()
  })
})
