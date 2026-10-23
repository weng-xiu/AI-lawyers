/**
 * P9 前端可观测：错误捕获 + Core Web Vitals 采集 + 批量上报。
 *
 * 设计目标（对应《代码结构盘点与优化规划》P9）：
 * - 线上问题可发现：Vue 组件异常、window.onerror、未处理 Promise、静态资源加载失败
 *   统一采集，带路由/堆栈/组件名，后端按类型与路由计数并打 WARN 日志；
 * - 性能可观测：LCP / FCP / TTFB / INP / CLS 采集并上报，后端转 Prometheus 分布，
 *   与 Grafana 打通；
 * - 对业务零打扰：所有采集逻辑内部吞异常；10s 批量 + 页面隐藏/卸载 sendBeacon 兜底；
 *   同一条错误 60s 内去重；字段截断，队列上限保护内存。
 *
 * 上报协议：POST {VUE_APP_BASE_API}/monitor/frontend/report
 * Content-Type: text/plain;charset=UTF-8（简单请求，页面卸载也能发出）
 * Body: {"app":"ai-ui","sessionId":"...","events":[{kind:'error'|'vital',...}]}
 */
const ENDPOINT_PATH = '/monitor/frontend/report'

export const DEFAULT_FLUSH_INTERVAL = 10000
export const DEFAULT_MAX_BATCH = 30
export const DEFAULT_MAX_QUEUE = 100
export const DEFAULT_DEDUPE_WINDOW = 60000

/** 截断并压平换行，避免超长字段与日志注入 */
export function sanitizeText(value, max) {
  if (value === null || value === undefined) return ''
  let text = String(value)
  text = text.replace(/[\r\n\t]+/g, ' ').trim()
  return text.length > max ? text.slice(0, max) : text
}

/** 归一化路由：去查询串/哈希，数值段折叠为 *，控制后端 Prometheus 标签基数 */
export function normalizeRoute(input) {
  if (!input) return '/'
  let path = String(input).split('?')[0].split('#')[0]
  path = path.replace(/\/\d+(?=\/|$)/g, '/*')
  if (!path) return '/'
  return path.length > 80 ? path.slice(0, 80) : path
}

/** 生成页面会话 ID（优先 crypto，失败回退时间戳+随机数） */
export function createSessionId() {
  try {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      const bytes = new Uint8Array(8)
      crypto.getRandomValues(bytes)
      let out = ''
      for (let i = 0; i < bytes.length; i++) {
        out += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16)
      }
      return out
    }
  } catch (e) {
    // 忽略：回退随机串
  }
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 8)
}

/** 从异常对象中提取可读消息 */
function errorMessage(err) {
  if (err === null || err === undefined) return 'unknown'
  if (typeof err === 'string') return err
  if (err.message) return err.message
  if (err.reason) return errorMessage(err.reason)
  try {
    return String(err)
  } catch (e) {
    return 'unknown'
  }
}

/**
 * 创建监控实例（可测试：不依赖 Vue，仅提供安装函数）。
 */
export function createFrontendMonitor(options = {}) {
  const cfg = {
    endpoint: (process.env.VUE_APP_BASE_API || '') + ENDPOINT_PATH,
    app: 'ai-ui',
    enabled: true,
    flushInterval: DEFAULT_FLUSH_INTERVAL,
    maxBatch: DEFAULT_MAX_BATCH,
    maxQueue: DEFAULT_MAX_QUEUE,
    dedupeWindow: DEFAULT_DEDUPE_WINDOW,
    // Web Vitals 采样率（错误始终全量采集）；1 表示 100%
    sampleRate: 1,
    ...options
  }

  const sessionId = createSessionId()
  let queue = []
  let router = null
  let timer = null
  let started = false
  let installed = false
  const dedupe = new Map()
  const vitals = {}
  const observers = []

  const currentRoute = () => {
    try {
      const route = router && router.currentRoute
      if (route && (route.path || route.fullPath)) return normalizeRoute(route.path || route.fullPath)
    } catch (e) {
      // 忽略：回退 location
    }
    try {
      if (typeof window !== 'undefined' && window.location) return normalizeRoute(window.location.pathname)
    } catch (e) {
      // 忽略
    }
    return '/'
  }

  const currentUrl = () => {
    try {
      return sanitizeText(window.location.href.split('?')[0].split('#')[0], 300)
    } catch (e) {
      return ''
    }
  }

  /** 同一条错误在去重窗口内只入队一次 */
  const isDuplicate = (type, message) => {
    if (!cfg.dedupeWindow) return false
    const now = Date.now()
    const key = type + '|' + message
    const last = dedupe.get(key)
    if (last && now - last < cfg.dedupeWindow) return true
    dedupe.set(key, now)
    if (dedupe.size > 500) {
      dedupe.forEach((ts, k) => {
        if (now - ts >= cfg.dedupeWindow) dedupe.delete(k)
      })
      if (dedupe.size > 500) dedupe.clear()
    }
    return false
  }

  const enqueue = (event) => {
    if (queue.length >= cfg.maxQueue) queue.shift()
    queue.push(event)
    if (queue.length >= cfg.maxBatch) flush(false)
  }

  /** 发送一批数据；失败时回填队列，下轮重试 */
  const send = (body, useBeacon) => {
    if (!cfg.endpoint) return false
    if (useBeacon && typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      try {
        // DOMString 会以 text/plain;charset=UTF-8 发送，与后端 consumes 对齐
        if (navigator.sendBeacon(cfg.endpoint, body)) return true
      } catch (e) {
        // 忽略：回退 fetch
      }
    }
    if (typeof fetch === 'function') {
      try {
        fetch(cfg.endpoint, {
          method: 'POST',
          headers: { 'Content-Type': 'text/plain;charset=UTF-8' },
          body,
          keepalive: useBeacon === true
        }).catch(() => {})
        return true
      } catch (e) {
        return false
      }
    }
    return false
  }

  /**
   * 立即发送一批（最多 maxBatch 条）。
   * @param {boolean} useBeacon 页面卸载/隐藏时传 true，优先 sendBeacon
   * @returns {boolean} 是否已发出
   */
  const flush = (useBeacon) => {
    if (!queue.length || !cfg.enabled) return false
    const events = queue.slice(0, cfg.maxBatch)
    const body = JSON.stringify({ app: cfg.app, sessionId, events })
    if (!send(body, useBeacon)) return false
    queue = queue.slice(events.length)
    return true
  }

  /** 记录错误（对业务完全无感，内部吞异常） */
  const captureError = (type, message, stack, source) => {
    if (!cfg.enabled) return
    try {
      const text = sanitizeText(message, 500) || 'unknown'
      if (isDuplicate(type, text)) return
      enqueue({
        kind: 'error',
        type,
        message: text,
        stack: sanitizeText(stack, 2000),
        source: sanitizeText(source, 200),
        route: currentRoute(),
        url: currentUrl(),
        ts: Date.now()
      })
    } catch (e) {
      // 忽略：采集失败不影响业务
    }
  }

  /** 记录 Web Vitals（无效值丢弃；按 sampleRate 采样） */
  const captureVital = (metric, value) => {
    if (!cfg.enabled) return
    if (typeof value !== 'number' || !isFinite(value) || value < 0) return
    if (cfg.sampleRate < 1 && Math.random() > cfg.sampleRate) return
    try {
      enqueue({
        kind: 'vital',
        metric,
        // CLS 无量纲保留 3 位小数，其余毫秒取整
        value: metric === 'cls' ? Math.round(value * 1000) / 1000 : Math.round(value),
        route: currentRoute(),
        ts: Date.now()
      })
    } catch (e) {
      // 忽略
    }
  }

  /** 注册 PerformanceObserver，浏览器不支持或类型不支持时静默跳过 */
  const observe = (type, handler, extra) => {
    if (typeof PerformanceObserver === 'undefined') return
    try {
      const observer = new PerformanceObserver((list) => {
        try {
          handler(list.getEntries())
        } catch (e) {
          // 忽略
        }
      })
      observer.observe({ type, buffered: true, ...(extra || {}) })
      observers.push(observer)
    } catch (e) {
      // 忽略：该指标不可用
    }
  }

  const readNavigationTiming = () => {
    try {
      if (typeof performance === 'undefined' || !performance.getEntriesByType) return
      const nav = performance.getEntriesByType('navigation')[0]
      if (nav && typeof nav.responseStart === 'number' && nav.responseStart > 0) {
        vitals.ttfb = nav.responseStart
      }
    } catch (e) {
      // 忽略
    }
  }

  const setupVitals = () => {
    readNavigationTiming()
    // LCP：取最后一条（页面持续渲染，最终值在隐藏/卸载时上报）
    observe('largest-contentful-paint', (entries) => {
      const last = entries[entries.length - 1]
      if (last) vitals.lcp = last.renderTime || last.loadTime || last.startTime
    })
    observe('paint', (entries) => {
      entries.forEach((entry) => {
        if (entry.name === 'first-contentful-paint') vitals.fcp = entry.startTime
      })
    })
    observe('layout-shift', (entries) => {
      let cls = vitals.cls || 0
      entries.forEach((entry) => {
        if (!entry.hadRecentInput) cls += entry.value
      })
      vitals.cls = cls
    })
    // INP 近似：采集事件耗时最大值（durationThreshold 200ms 过滤低价值事件）
    observe('event', (entries) => {
      entries.forEach((entry) => {
        if (entry.duration > (vitals.inp || 0)) vitals.inp = entry.duration
      })
    }, { durationThreshold: 200 })
  }

  /** 把已采集的 Vitals 入队（只上报一次），随后由 flush 发出 */
  const reportVitals = () => {
    Object.keys(vitals).forEach((key) => {
      const value = vitals[key]
      delete vitals[key]
      captureVital(key, value)
    })
  }

  const onWindowError = (event) => {
    try {
      const target = event && event.target
      if (target && target !== window && (target.src || target.href)) {
        // 静态资源加载失败（img/script/link 等，捕获阶段监听）
        captureError('resource', sanitizeText(target.tagName, 40) + ' ' + (target.src || target.href), '', target.tagName)
        return
      }
      captureError('window', event && event.message, event && event.error && event.error.stack, event && event.filename)
    } catch (e) {
      // 忽略
    }
  }

  const onUnhandledRejection = (event) => {
    try {
      const reason = event && event.reason
      captureError('promise', errorMessage(reason), reason && reason.stack, '')
    } catch (e) {
      // 忽略
    }
  }

  const onVisibilityChange = () => {
    try {
      if (document.hidden) {
        reportVitals()
        flush(true)
      }
    } catch (e) {
      // 忽略
    }
  }

  const onPageHide = () => {
    try {
      reportVitals()
      flush(true)
    } catch (e) {
      // 忽略
    }
  }

  /** 安装全局捕获（Vue errorHandler 会链式保留原处理器） */
  const install = (Vue) => {
    if (!cfg.enabled || !Vue || installed) return
    installed = true
    const original = Vue.config.errorHandler
    Vue.config.errorHandler = function (err, vm, info) {
      try {
        const name = vm && vm.$options ? (vm.$options.name || vm.$options.__file || '') : ''
        captureError('vue', errorMessage(err), err && err.stack, sanitizeText(name, 120) + (info ? '#' + info : ''))
      } catch (e) {
        // 忽略
      }
      if (typeof original === 'function') original.call(Vue, err, vm, info)
    }
    if (typeof window !== 'undefined' && window.addEventListener) {
      window.addEventListener('error', onWindowError, true)
      window.addEventListener('unhandledrejection', onUnhandledRejection)
    }
  }

  const start = () => {
    if (!cfg.enabled || started) return
    started = true
    setupVitals()
    timer = setInterval(() => flush(false), cfg.flushInterval)
    if (typeof document !== 'undefined' && document.addEventListener) {
      document.addEventListener('visibilitychange', onVisibilityChange)
    }
    if (typeof window !== 'undefined' && window.addEventListener) {
      window.addEventListener('pagehide', onPageHide)
    }
  }

  const stop = () => {
    started = false
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    if (typeof document !== 'undefined' && document.removeEventListener) {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
    if (typeof window !== 'undefined' && window.removeEventListener) {
      window.removeEventListener('pagehide', onPageHide)
      window.removeEventListener('error', onWindowError, true)
      window.removeEventListener('unhandledrejection', onUnhandledRejection)
    }
    observers.splice(0).forEach((observer) => {
      try {
        observer.disconnect()
      } catch (e) {
        // 忽略
      }
    })
    reportVitals()
    flush(true)
  }

  return {
    config: cfg,
    install,
    start,
    stop,
    flush,
    captureError,
    captureVital,
    reportVitals,
    bindRouter(r) {
      router = r
    },
    pendingCount() {
      return queue.length
    }
  }
}

/**
 * 应用入口：按环境决定是否启用，并绑定路由。
 *
 * 生产环境默认开启；开发环境需显式设置 VUE_APP_MONITOR_ENABLED=true，
 * 避免本地调试把噪声打到后端。
 */
export function installFrontendMonitor(Vue, router) {
  const enabled = process.env.NODE_ENV === 'production' || process.env.VUE_APP_MONITOR_ENABLED === 'true'
  if (!enabled) return null
  const monitor = createFrontendMonitor({ enabled: true })
  monitor.bindRouter(router)
  monitor.install(Vue)
  monitor.start()
  return monitor
}
