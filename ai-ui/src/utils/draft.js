/**
 * U4 表单防丢失：localStorage 草稿自动保存 / 恢复 / 清除 + 服务端草稿 + 提交防重锁。
 *
 * 设计（对应规划文档 U4）：
 *  - 长表单填写中自动保存草稿：先写 localStorage（零延迟），再节流同步到服务端（默认 2s）；
 *  - 意外关闭 / 刷新 / 换浏览器后重新打开弹窗，取本地与服务端较新的草稿询问恢复；
 *  - 提交成功清除草稿（本地 + 服务端）；表单清空时自动移除草稿；
 *  - 附带提交防重锁（draftSubmitting + lockSubmit/unlockSubmit），
 *    避免"提交中禁用重复提交"各页重复实现。
 *
 * 用法（页面接入）：
 *   import { createDraftMixin } from '@/utils/draft'
 *   mixins: [createDraftMixin({
 *     key: vm => 'workOrder:' + (vm.$store.state.user.name || 'anon'),  // 支持函数动态 key（按用户隔离）
 *     formKey: 'form',                    // 表单对象在 data 中的字段名
 *     excludes: ['ticketId', 'ticketNo'], // 不参与草稿的字段（主键 / 系统生成值）
 *     when: vm => !vm.isEdit              // 可选：仅特定状态下存/恢复（如新增态）
 *   })],
 *
 * 页面钩子：
 *   - 打开新增弹窗后调 this.draftRestorePrompt()：有草稿则弹"恢复/丢弃"确认；
 *   - 提交方法入口：if (this.draftSubmitting) return; this.lockSubmit();
 *     提交完成后（成功/失败）finally this.unlockSubmit();
 *   - 提交成功后 this.draftClear();
 *   - 提交按钮 :loading="draftSubmitting"。
 */
import { getFormDraft, saveFormDraft, deleteFormDraft } from '@/api/lawyers/formDraft'

export function createDraftMixin({ key, formKey = 'form', excludes = [], when = null, debounceMs = 800, serverSyncMs = 2000 }) {
  const kOf = vm => (typeof key === 'function' ? key(vm) : key)

  /** 判断草稿数据是否含有效填写值（全空视为无草稿） */
  function hasValue(data) {
    return Object.keys(data).some(f => {
      const v = data[f]
      return v !== null && v !== undefined && v !== '' && !(Array.isArray(v) && v.length === 0)
    })
  }

  /** 剔除 excludes 拷贝表单字段 */
  function pickFields(vm) {
    const src = vm[formKey] || {}
    const data = {}
    Object.keys(src).forEach(f => {
      if (excludes.indexOf(f) === -1) data[f] = src[f]
    })
    return data
  }

  /** 解析草稿 JSON（本地/服务端同一结构 {ts, data}），脏数据返回 null */
  function parseDraft(raw) {
    if (!raw) return null
    try {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
      if (!parsed || !parsed.data || !hasValue(parsed.data)) return null
      return { ts: Number(parsed.ts) || 0, data: parsed.data }
    } catch (e) {
      return null
    }
  }

  return {
    data() {
      return { draftSubmitting: false }
    },
    watch: {
      [formKey]: {
        deep: true,
        handler: function(val) {
          if (when && !when(this)) return
          clearTimeout(this._draftTimer)
          clearTimeout(this._draftServerTimer)
          this._draftTimer = setTimeout(() => this.draftSave(), debounceMs)
        }
      }
    },
    methods: {
      /** 保存草稿（全空自动移除）。watch 自动调用，一般无需手动调 */
      draftSave() {
        const k = kOf(this)
        if (!k) return
        const data = pickFields(this)
        if (!hasValue(data)) {
          localStorage.removeItem(k)
        } else {
          try {
            localStorage.setItem(k, JSON.stringify({ ts: Date.now(), data }))
          } catch (e) {
            // 存储满 / 隐私模式：草稿不可用不影响主流程
          }
        }
        // 本地已落盘，服务端同步再节流一次，避免每次按键都发请求
        clearTimeout(this._draftServerTimer)
        this._draftServerTimer = setTimeout(() => this.draftSaveServer(), serverSyncMs)
      },
      /** 把本地草稿同步到服务端（失败静默：本地草稿仍可用） */
      draftSaveServer() {
        const k = kOf(this)
        if (!k) return
        let raw = null
        try {
          raw = localStorage.getItem(k)
        } catch (e) {
          raw = null
        }
        const req = raw ? saveFormDraft(k, raw) : deleteFormDraft(k)
        if (req && typeof req.catch === 'function') req.catch(() => {})
      },
      /**
       * 有草稿则弹"恢复/丢弃"确认。本地与服务端都取，按 ts 取较新的。
       * @returns {Promise<boolean>} true=已恢复 / false=无草稿或用户丢弃
       */
      async draftRestorePrompt() {
        const k = kOf(this)
        if (!k) return false
        let best = null
        try {
          best = parseDraft(localStorage.getItem(k))
        } catch (e) {
          best = null
        }
        try {
          const res = await getFormDraft(k)
          const server = parseDraft(res && res.data ? res.data.content : null)
          if (server && (!best || server.ts > best.ts)) best = server
        } catch (e) {
          // 服务端不可用（离线/接口未部署）：降级为仅本地草稿
        }
        if (!best) {
          localStorage.removeItem(k)
          return false
        }
        const time = new Date(best.ts).toLocaleString()
        return this.$confirm(`检测到 ${time} 保存的未提交草稿，是否恢复？`, '草稿恢复', {
          confirmButtonText: '恢复草稿',
          cancelButtonText: '丢弃',
          type: 'info'
        }).then(() => {
          const form = this[formKey] || {}
          Object.keys(best.data).forEach(f => {
            // 仅恢复表单已有字段（避免残留旧版本字段），excludes 不恢复
            if (excludes.indexOf(f) === -1 && f in form) {
              this.$set(form, f, best.data[f])
            }
          })
          return true
        }).catch(() => {
          this.draftClear()
          return false
        })
      },
      /** 清除草稿（提交成功后调用）：本地立即删，服务端异步删 */
      draftClear() {
        const k = kOf(this)
        if (!k) return
        localStorage.removeItem(k)
        clearTimeout(this._draftServerTimer)
        const req = deleteFormDraft(k)
        if (req && typeof req.catch === 'function') req.catch(() => {})
      },
      /** 提交防重：上锁（提交按钮转圈） */
      lockSubmit() {
        this.draftSubmitting = true
      },
      /** 提交防重：解锁（成功/失败均需在 finally 调用） */
      unlockSubmit() {
        this.draftSubmitting = false
      }
    },
    beforeDestroy() {
      clearTimeout(this._draftTimer)
      clearTimeout(this._draftServerTimer)
    }
  }
}
