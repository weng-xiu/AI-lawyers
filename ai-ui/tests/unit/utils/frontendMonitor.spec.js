'use strict'

/**
 * P9 前端可观测单测：frontendMonitor
 * 覆盖：字段清洗/路由归一化、错误去重、批量与队列上限、Web Vitals 数值处理、
 * 关闭开关、Vue errorHandler 链式保留、定时 flush 与 pagehide beacon 兜底。
 */
import { createFrontendMonitor, sanitizeText, normalizeRoute } from '@/utils/frontendMonitor'

const beacon = jest.fn()
let sent = []

beforeEach(() => {
  sent = []
  beacon.mockReset()
  beacon.mockImplementation((url, body) => {
    sent.push({ url, body })
    return true
  })
  Object.defineProperty(navigator, 'sendBeacon', {
    configurable: true,
    writable: true,
    value: beacon
  })
  global.fetch = jest.fn(() => Promise.resolve({ ok: true }))
})

afterEach(() => {
  delete navigator.sendBeacon
  delete global.fetch
  jest.useRealTimers()
})

function lastPayload() {
  return JSON.parse(sent[sent.length - 1].body)
}

describe('frontendMonitor 工具函数', () => {
  test('sanitizeText 压平换行并按上限截断', () => {
    expect(sanitizeText('a\nb\tc', 100)).toBe('a b c')
    expect(sanitizeText(null, 10)).toBe('')
    expect(sanitizeText('0123456789', 5)).toBe('01234')
  })

  test('normalizeRoute 去查询串并把数值段折叠为 *', () => {
    expect(normalizeRoute('/lawyers/ticket/42?x=1')).toBe('/lawyers/ticket/*')
    expect(normalizeRoute('/lawyers/ticket/42/detail#log')).toBe('/lawyers/ticket/*/detail')
    expect(normalizeRoute('')).toBe('/')
    expect(normalizeRoute('/lawyers/abc123')).toBe('/lawyers/abc123')
  })
})

describe('createFrontendMonitor', () => {
  test('同一条错误在去重窗口内只入队一次，flush 使用 sendBeacon', () => {
    const monitor = createFrontendMonitor()
    monitor.captureError('vue', 'boom', 'stack')
    monitor.captureError('vue', 'boom', 'stack')
    expect(monitor.pendingCount()).toBe(1)

    expect(monitor.flush(true)).toBe(true)
    expect(beacon).toHaveBeenCalledTimes(1)
    const payload = lastPayload()
    expect(payload.app).toBe('ai-ui')
    expect(payload.sessionId).toHaveLength(16)
    expect(payload.events).toHaveLength(1)
    expect(payload.events[0]).toMatchObject({ kind: 'error', type: 'vue', message: 'boom', route: '/' })
  })

  test('不同错误分别入队，字段超长被截断', () => {
    const monitor = createFrontendMonitor()
    monitor.captureError('window', 'x'.repeat(600))
    monitor.captureError('promise', 'other')
    expect(monitor.pendingCount()).toBe(2)

    monitor.flush(true)
    const events = lastPayload().events
    expect(events[0].message).toHaveLength(500)
    expect(events[1].type).toBe('promise')
  })

  test('队列超上限时丢弃最旧事件', () => {
    const monitor = createFrontendMonitor({ maxQueue: 3, maxBatch: 100 })
    for (let i = 0; i < 5; i++) monitor.captureError('window', 'e' + i)
    expect(monitor.pendingCount()).toBe(3)

    monitor.flush(true)
    expect(lastPayload().events.map((e) => e.message)).toEqual(['e2', 'e3', 'e4'])
  })

  test('达到 maxBatch 立即发送，flush 空队列返回 false', () => {
    const monitor = createFrontendMonitor({ maxBatch: 2 })
    monitor.captureError('window', 'a')
    expect(global.fetch).not.toHaveBeenCalled()
    monitor.captureError('window', 'b')
    // 自动 flush 走 fetch（非页面卸载场景不占用 sendBeacon）
    expect(global.fetch).toHaveBeenCalledTimes(1)

    expect(monitor.flush(true)).toBe(false)
  })

  test('Web Vitals 丢弃非法值并按指标格式化', () => {
    const monitor = createFrontendMonitor()
    monitor.captureVital('lcp', 2500.6)
    monitor.captureVital('cls', 0.12345)
    monitor.captureVital('lcp', NaN)
    monitor.captureVital('inp', -1)

    expect(monitor.pendingCount()).toBe(2)
    monitor.flush(true)
    const events = lastPayload().events
    expect(events[0]).toMatchObject({ kind: 'vital', metric: 'lcp', value: 2501 })
    expect(events[1]).toMatchObject({ kind: 'vital', metric: 'cls', value: 0.123 })
  })

  test('enabled=false 时不采集、不发送', () => {
    const monitor = createFrontendMonitor({ enabled: false })
    monitor.captureError('window', 'boom')
    monitor.captureVital('lcp', 100)
    expect(monitor.pendingCount()).toBe(0)
    expect(monitor.flush(true)).toBe(false)
  })

  test('install 链式保留原 errorHandler 并上报组件名', () => {
    const original = jest.fn()
    const Vue = { config: { errorHandler: original } }
    const monitor = createFrontendMonitor()
    monitor.install(Vue)

    Vue.config.errorHandler(new Error('render boom'), { $options: { name: 'TicketList' } }, 'render')

    expect(original).toHaveBeenCalledTimes(1)
    expect(monitor.pendingCount()).toBe(1)
    monitor.flush(true)
    const event = lastPayload().events[0]
    expect(event).toMatchObject({ type: 'vue', message: 'render boom', source: 'TicketList#render' })
    monitor.stop()
  })

  test('定时器按 flushInterval 自动发送', () => {
    jest.useFakeTimers()
    const monitor = createFrontendMonitor({ flushInterval: 1000 })
    monitor.start()
    monitor.captureError('window', 'tick')

    expect(global.fetch).not.toHaveBeenCalled()
    jest.advanceTimersByTime(1000)
    expect(global.fetch).toHaveBeenCalledTimes(1)

    monitor.stop()
  })

  test('pagehide 时优先 sendBeacon 兜底并上报已采集 Vitals', () => {
    const monitor = createFrontendMonitor({ flushInterval: 60000 })
    monitor.start()
    monitor.captureVital('fcp', 1200)

    window.dispatchEvent(new Event('pagehide'))

    expect(beacon).toHaveBeenCalledTimes(1)
    expect(lastPayload().events[0]).toMatchObject({ kind: 'vital', metric: 'fcp', value: 1200 })
    monitor.stop()
  })
})
