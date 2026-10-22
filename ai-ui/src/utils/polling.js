/**
 * 统一页面级轮询工具（P6 接口层性能细节）
 *
 * 背景：各页面原先各自裸写 setInterval 轮询，存在两类浪费：
 * 1. 页面切到后台（浏览器其他 Tab）时仍按频率发请求，多开页面时请求成倍放大；
 * 2. 接口响应慢于轮询周期时，在途请求叠加堆积，进一步拖慢后端。
 *
 * 本工具统一提供：
 * - 不可见暂停：document.hidden 时跳过本轮 tick，页面恢复可见立即补拉一次；
 * - 在途防重入：回调返回 Promise 且尚未结束时，跳过本轮（相当于按实际耗时自动节流）；
 * - 显式生命周期：start/stop 幂等，支持 pause/resume（如弹窗打开时暂停）与手动 refresh。
 *
 * 用法：
 *   this.poller = createPoller(this.loadData, 30000, { immediate: true })
 *   this.poller.start()
 *   // beforeDestroy:
 *   this.poller.stop()
 */
export function createPoller(fn, interval, options = {}) {
  const immediate = options.immediate === true
  const refreshOnVisible = options.refreshOnVisible !== false
  let timer = null
  let running = false
  let inFlight = false

  const invoke = () => {
    if (!running || inFlight) return
    if (typeof document !== 'undefined' && document.hidden) return
    let result
    try {
      result = fn()
    } catch (e) {
      return
    }
    if (result && typeof result.catch === 'function') {
      inFlight = true
      result.catch(() => {}).then(() => { inFlight = false })
    }
  }

  const onVisibilityChange = () => {
    if (!running) return
    if (!document.hidden && refreshOnVisible) invoke()
  }

  return {
    start() {
      if (running) return
      running = true
      document.addEventListener('visibilitychange', onVisibilityChange)
      if (immediate) invoke()
      timer = setInterval(invoke, interval)
    },
    stop() {
      if (!running) return
      running = false
      document.removeEventListener('visibilitychange', onVisibilityChange)
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    },
    /** 手动触发一次（同样受不可见暂停与在途防重入约束） */
    refresh() {
      invoke()
    },
    /** 暂停定时器（保留可见性监听），如弹窗打开期间 */
    pause() {
      if (timer) {
        clearInterval(timer)
        timer = null
      }
    },
    /** 恢复定时器 */
    resume() {
      if (running && !timer) {
        timer = setInterval(invoke, interval)
      }
    }
  }
}
