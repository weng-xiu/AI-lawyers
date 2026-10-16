import { Message, MessageBox, Notification, Loading } from 'element-ui'

let loadingInstance

/**
 * U2：HTML 转义，危险确认框以 HTML 拼装影响条数等结构，
 * 内容文本可能携带用户输入（如任务名/机构名），必须先转义防注入。
 */
function escapeHtml(text) {
  return String(text == null ? '' : text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * U2：危险操作语义预设。
 * destructive=true 使用错误图标 + 红色按钮 + 取消按钮默认聚焦（回车不会误触删除）；
 * 其余为高关注操作（发布等），警示图标 + 主色按钮。
 */
const DANGER_PRESETS = {
  delete: {
    title: '删除确认',
    confirmText: '确认删除',
    type: 'error',
    destructive: true,
    tip: '删除后数据不可恢复，请谨慎操作'
  },
  clear: {
    title: '清空确认',
    confirmText: '确认清空',
    type: 'error',
    destructive: true,
    tip: '清空后数据不可恢复，请谨慎操作'
  },
  reset: {
    title: '重置确认',
    confirmText: '确认重置',
    type: 'error',
    destructive: true,
    tip: '重置后原配置不可恢复，请谨慎操作'
  },
  publish: {
    title: '发布确认',
    confirmText: '确认发布',
    type: 'warning',
    destructive: false,
    tip: '发布后即时生效，相关用户可见'
  },
  caution: {
    title: '操作确认',
    confirmText: '确认执行',
    type: 'warning',
    destructive: false,
    tip: ''
  }
}

export default {
  // 消息提示
  msg(content) {
    Message.info(content)
  },
  // 错误消息
  msgError(content) {
    Message.error(content)
  },
  // 成功消息
  msgSuccess(content) {
    Message.success(content)
  },
  // 警告消息
  msgWarning(content) {
    Message.warning(content)
  },
  // 弹出提示
  alert(content) {
    MessageBox.alert(content, "系统提示")
  },
  // 错误提示
  alertError(content) {
    MessageBox.alert(content, "系统提示", { type: 'error' })
  },
  // 成功提示
  alertSuccess(content) {
    MessageBox.alert(content, "系统提示", { type: 'success' })
  },
  // 警告提示
  alertWarning(content) {
    MessageBox.alert(content, "系统提示", { type: 'warning' })
  },
  // 通知提示
  notify(content) {
    Notification.info(content)
  },
  // 错误通知
  notifyError(content) {
    Notification.error(content)
  },
  // 成功通知
  notifySuccess(content) {
    Notification.success(content)
  },
  // 警告通知
  notifyWarning(content) {
    Notification.warning(content)
  },
  // 确认窗体
  confirm(content) {
    return MessageBox.confirm(content, "系统提示", {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: "warning",
    })
  },
  /**
   * U2：危险操作统一二次确认。
   *
   * @param {string|object} options 传字符串时等价于 { content: options }（默认 delete 语义）；
   *   对象字段：
   *   - actionType: delete(默认) | clear | reset | publish | caution
   *   - content: 主体说明（会做 HTML 转义）
   *   - count: 影响条数（数字，>0 时展示"将影响 N 条数据"）
   *   - countNoun: 量词，默认"条数据"
   *   - title / confirmText / tip: 覆盖预设文案；tip 传 null 可关闭提示行
   * @returns Promise（取消/关闭均 reject，与 Element MessageBox 及既有 .catch 回滚语义一致）
   */
  dangerConfirm(options) {
    const o = typeof options === 'string' ? { content: options } : (options || {})
    const preset = DANGER_PRESETS[o.actionType] || DANGER_PRESETS.delete
    const title = o.title || preset.title
    const confirmText = o.confirmText || preset.confirmText
    const tip = o.tip === undefined ? preset.tip : o.tip
    const lines = []
    if (o.content) {
      lines.push('<div class="danger-confirm-content">' + escapeHtml(o.content) + '</div>')
    }
    if (o.count) {
      const noun = escapeHtml(o.countNoun || '条数据')
      lines.push('<div class="danger-confirm-count">将影响 <b>' + o.count + '</b> ' + noun + '</div>')
    }
    if (tip) {
      lines.push('<div class="danger-confirm-tip">' + escapeHtml(tip) + '</div>')
    }
    return MessageBox.confirm(lines.join(''), title, {
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
      type: preset.type,
      dangerouslyUseHTMLString: true,
      customClass: 'danger-confirm-box' + (preset.destructive ? ' is-destructive' : ' is-caution'),
      confirmButtonClass: preset.destructive ? 'el-button--danger' : 'el-button--primary',
      distinguishCancelAndClose: true,
      showClose: false,
      closeOnClickModal: false,
      // 删除/清空/重置类：焦点默认在"取消"，回车不会直接触发危险动作
      focusConfirm: !preset.destructive,
      focusCancel: preset.destructive
    })
  },
  /**
   * U2：可撤销轻提示（默认 30 秒倒计时）。
   *
   * @param {object} options
   *   - message: 成功提示文本
   *   - actionText: 撤销按钮文案，默认"撤销"
   *   - duration: 撤销窗口毫秒，默认 30000
   *   - onUndo: 点击撤销时执行（可返回 Promise，失败自动弹错误提示）
   *   - errorText: 撤销失败提示前缀
   * @returns Message 实例
   */
  undo(options) {
    const o = options || {}
    const totalMs = o.duration || 30000
    let remain = Math.max(1, Math.round(totalMs / 1000))
    let settled = false
    let timer = null
    const instance = Message({
      message: o.message || '操作成功',
      type: o.type || 'success',
      duration: 0,
      showClose: false,
      customClass: 'el-message-undo'
    })
    const finish = () => {
      if (settled) return
      settled = true
      if (timer) clearInterval(timer)
      instance.close()
    }
    // Message 实例挂载后向内容区追加撤销按钮（DOM 方式，兼容运行时版 Vue，无需模板编译）
    setTimeout(() => {
      const el = instance.$el
      const slot = el && el.querySelector ? el.querySelector('.el-message__content') : null
      if (!slot) return
      const btn = document.createElement('button')
      btn.type = 'button'
      btn.className = 'el-message-undo__btn'
      const paint = () => {
        btn.textContent = (o.actionText || '撤销') + '（' + remain + 's）'
      }
      paint()
      btn.addEventListener('click', () => {
        if (settled) return
        finish()
        try {
          const ret = o.onUndo && o.onUndo()
          if (ret && typeof ret.then === 'function') {
            ret.then(() => Message.success(o.undoSuccessText || '已撤销')).catch(err => {
              Message.error((o.errorText || '撤销失败') + (err && err.message ? '：' + err.message : ''))
            })
          }
        } catch (err) {
          Message.error((o.errorText || '撤销失败') + (err && err.message ? '：' + err.message : ''))
        }
      })
      slot.appendChild(btn)
      timer = setInterval(() => {
        remain -= 1
        if (remain <= 0) {
          finish()
        } else {
          paint()
        }
      }, 1000)
    }, 0)
    return instance
  },
  // 提交内容
  prompt(content) {
    return MessageBox.prompt(content, "系统提示", {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: "warning",
    })
  },
  // 打开遮罩层
  loading(content) {
    loadingInstance = Loading.service({
      lock: true,
      text: content,
      spinner: "el-icon-loading",
      background: "rgba(0, 0, 0, 0.7)",
    })
  },
  // 关闭遮罩层
  closeLoading() {
    loadingInstance.close()
  }
}
