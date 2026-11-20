'use strict'

/**
 * 平滑滚动工具单测：覆盖缓动函数两段公式与动画结束回调。
 */
import { scrollTo } from '@/utils/scroll-to'

const wait = ms => new Promise(resolve => setTimeout(resolve, ms))

describe('Math.easeInOutQuad', () => {
  test('起点、加速段、中点与终点取值', () => {
    expect(Math.easeInOutQuad(0, 0, 100, 40)).toBe(0)
    expect(Math.easeInOutQuad(10, 0, 100, 40)).toBe(12.5)
    expect(Math.easeInOutQuad(20, 0, 100, 40)).toBe(50)
    expect(Math.easeInOutQuad(40, 0, 100, 40)).toBe(100)
  })

  test('带起始偏移时结果整体平移', () => {
    expect(Math.easeInOutQuad(40, 50, 100, 40)).toBe(150)
  })
})

describe('scrollTo', () => {
  beforeEach(() => {
    document.documentElement.scrollTop = 0
    document.body.scrollTop = 0
  })

  test('按 duration 逐帧滚动并在结束时执行回调', async () => {
    const done = new Promise(resolve => scrollTo(100, 40, resolve))

    // 首帧（同步执行）位于中点
    expect(document.documentElement.scrollTop).toBe(50)

    await done

    expect(document.documentElement.scrollTop).toBe(100)
  })

  test('未传 duration 时使用默认时长并滚动到目标位置', async () => {
    await new Promise(resolve => scrollTo(100, undefined, resolve))

    expect(document.documentElement.scrollTop).toBe(100)
  })

  test('不传回调时动画正常结束且不报错', async () => {
    expect(() => scrollTo(60, 40)).not.toThrow()

    await wait(200)

    expect(document.documentElement.scrollTop).toBe(60)
  })
})
