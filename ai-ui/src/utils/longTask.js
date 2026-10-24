/**
 * 统一长任务轮询封装（U8 操作反馈与通知）
 *
 * 背景：数据回填、仿真评测、外呼执行等长任务此前各自裸写 createPoller + 状态判断，
 * 终态分支（SUCCESS/FAILED）散落在各页面，容易漏发通知、重复回调或停止后仍回调。
 *
 * 本工具在 createPoller（不可见暂停 + 在途防重入）之上补齐状态机：
 * - RUNNING 期间每轮回调 onUpdate，页面自行更新进度；
 * - SUCCESS/FAILED 只触发一次 onDone/onFail，并自动停止轮询；
 * - stop() 后不再更新、不再触发任何回调（在途响应返回也会被丢弃）；
 * - 单轮请求失败视为临时异常，不终止任务，下一轮继续轮询。
 *
 * 用法：
 *   this.longTask = createLongTask({
 *     fetchProgress: () => api.progress(this.taskId).then(res => res.data || {}),
 *     interval: 3000,
 *     onUpdate: d => { this.percent = d.percent },
 *     onDone: d => this.$modal.notifyTaskDone('回填完成', `写入 ${d.rowsWritten} 行`),
 *     onFail: d => this.$modal.notifyTaskFail('回填失败', d.errorMsg)
 *   })
 *   this.longTask.start()
 *   // beforeDestroy: this.longTask.stop()
 */
import { createPoller } from './polling'

export function createLongTask(options = {}) {
  const interval = options.interval || 3000
  let poller = null
  let stopped = false
  let settled = false

  const stopPoller = () => {
    if (poller) {
      poller.stop()
      poller = null
    }
  }

  const finish = (status, data) => {
    if (settled || stopped) return
    settled = true
    stopPoller()
    if (status === 'SUCCESS') {
      if (options.onDone) options.onDone(data)
    } else if (options.onFail) {
      options.onFail(data)
    }
  }

  const tick = () => {
    if (stopped || settled) return null
    let result
    try {
      result = options.fetchProgress && options.fetchProgress()
    } catch (e) {
      return null
    }
    return Promise.resolve(result).then(progress => {
      if (stopped || settled) return progress
      const data = progress || {}
      if (options.onUpdate) options.onUpdate(data)
      if (data.status === 'SUCCESS') finish('SUCCESS', data)
      else if (data.status === 'FAILED') finish('FAILED', data)
      return data
    }, () => null)
  }

  return {
    start() {
      if (stopped || settled) return
      stopPoller()
      poller = createPoller(tick, interval, { immediate: options.immediate !== false })
      poller.start()
    },
    stop() {
      stopped = true
      stopPoller()
    }
  }
}
