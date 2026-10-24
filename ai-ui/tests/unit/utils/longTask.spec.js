'use strict'

/**
 * U8 长任务封装单测：createLongTask
 * 覆盖：进度回调、成功/失败终态只回调一次、停止后不再回调、单轮失败不终止任务。
 */
import { createLongTask } from '@/utils/longTask'

const flushMicrotasks = async () => {
  for (let i = 0; i < 5; i++) await Promise.resolve()
}

function mockSlow() {
  let resolveFn
  const fetchProgress = jest.fn(() => new Promise(resolve => { resolveFn = resolve }))
  return { fetchProgress, resolve: data => resolveFn(data) }
}

describe('createLongTask', () => {
  beforeEach(() => {
    jest.useFakeTimers()
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  test('RUNNING 期间每轮回调 onUpdate，SUCCESS 只触发一次 onDone', async () => {
    const statuses = ['RUNNING', 'RUNNING', 'SUCCESS']
    let i = 0
    const fetchProgress = jest.fn(() => Promise.resolve({ status: statuses[Math.min(i++, 2)] }))
    const onUpdate = jest.fn()
    const onDone = jest.fn()
    const onFail = jest.fn()
    const task = createLongTask({ fetchProgress, interval: 1000, onUpdate, onDone, onFail })

    task.start()
    await flushMicrotasks()
    expect(onUpdate).toHaveBeenCalledTimes(1)
    expect(onDone).not.toHaveBeenCalled()

    jest.advanceTimersByTime(1000)
    await flushMicrotasks()
    expect(onUpdate).toHaveBeenCalledTimes(2)

    jest.advanceTimersByTime(1000)
    await flushMicrotasks()
    expect(onUpdate).toHaveBeenCalledTimes(3)
    expect(onDone).toHaveBeenCalledTimes(1)
    expect(onDone).toHaveBeenCalledWith({ status: 'SUCCESS' })
    expect(onFail).not.toHaveBeenCalled()

    // 终态后轮询已停止，不再有回调
    jest.advanceTimersByTime(5000)
    await flushMicrotasks()
    expect(fetchProgress).toHaveBeenCalledTimes(3)
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  test('FAILED 触发 onFail，且只触发一次', async () => {
    const fetchProgress = jest.fn(() => Promise.resolve({ status: 'FAILED', errorMsg: 'boom' }))
    const onDone = jest.fn()
    const onFail = jest.fn()
    const task = createLongTask({ fetchProgress, interval: 1000, onDone, onFail })

    task.start()
    await flushMicrotasks()
    expect(onFail).toHaveBeenCalledTimes(1)
    expect(onFail).toHaveBeenCalledWith({ status: 'FAILED', errorMsg: 'boom' })
    expect(onDone).not.toHaveBeenCalled()

    jest.advanceTimersByTime(5000)
    await flushMicrotasks()
    expect(onFail).toHaveBeenCalledTimes(1)
    expect(fetchProgress).toHaveBeenCalledTimes(1)
  })

  test('stop 后在途响应返回也不再回调', async () => {
    const { fetchProgress, resolve } = mockSlow()
    const onUpdate = jest.fn()
    const onDone = jest.fn()
    const task = createLongTask({ fetchProgress, interval: 1000, onUpdate, onDone })

    task.start()
    task.stop()
    resolve({ status: 'SUCCESS' })
    await flushMicrotasks()

    expect(onUpdate).not.toHaveBeenCalled()
    expect(onDone).not.toHaveBeenCalled()
  })

  test('单轮请求失败视为临时异常，继续下一轮且不触发终态回调', async () => {
    const fetchProgress = jest.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValue({ status: 'RUNNING' })
    const onUpdate = jest.fn()
    const onFail = jest.fn()
    const task = createLongTask({ fetchProgress, interval: 1000, onUpdate, onFail })

    task.start()
    await flushMicrotasks()
    expect(onFail).not.toHaveBeenCalled()

    jest.advanceTimersByTime(1000)
    await flushMicrotasks()
    expect(fetchProgress).toHaveBeenCalledTimes(2)
    expect(onUpdate).toHaveBeenCalledTimes(1)
    task.stop()
  })

  test('fetchProgress 同步抛错不中断轮询', async () => {
    const fetchProgress = jest.fn(() => { throw new Error('boom') })
    const onFail = jest.fn()
    const task = createLongTask({ fetchProgress, interval: 1000, onFail })

    expect(() => task.start()).not.toThrow()
    jest.advanceTimersByTime(3000)
    expect(onFail).not.toHaveBeenCalled()
    // 立即首查 1 次 + 后续 3 个周期
    expect(fetchProgress).toHaveBeenCalledTimes(4)
    task.stop()
  })

  test('stop 幂等且可在 start 前调用', () => {
    const fetchProgress = jest.fn(() => Promise.resolve({ status: 'RUNNING' }))
    const task = createLongTask({ fetchProgress, interval: 1000 })
    task.stop()
    task.start()
    jest.advanceTimersByTime(2000)
    expect(fetchProgress).not.toHaveBeenCalled()
  })
})
