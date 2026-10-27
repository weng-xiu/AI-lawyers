/**
 * U1 坐席工作台快捷键：默认映射 / 本地持久化 / 组合键解析与匹配。
 *
 * 设计目标：
 *  - 默认 Ctrl+1~9 覆盖接听、保持、转接、台账、话后整理、挂机、快速建单、查知识、转办；
 *  - 允许坐席在界面上自定义（本地保存，换设备/清缓存后回到默认）；
 *  - 统一组合键捕获与匹配，避免各组件各写一套 e.key 判断。
 */

export const SHORTCUTS_STORAGE_KEY = 'ai_lawyers_shortcuts_v1'

export const DEFAULT_SHORTCUTS = [
  { id: 'answer', label: '接听', keys: 'Ctrl+1', method: 'handleAnswer' },
  { id: 'hold', label: '保持/恢复', keys: 'Ctrl+2', method: 'handleHold' },
  { id: 'transfer', label: '转接', keys: 'Ctrl+3', method: 'handleTransfer' },
  { id: 'ledger', label: '一键登记台账', keys: 'Ctrl+4', method: 'handleLedger' },
  { id: 'afterWork', label: '话后整理', keys: 'Ctrl+5', method: 'handleAfterWork' },
  { id: 'hangup', label: '挂机', keys: 'Ctrl+6', method: 'handleHangup' },
  { id: 'createTicket', label: '快速建单', keys: 'Ctrl+7', method: 'openQuickTicket' },
  { id: 'knowledge', label: '查知识', keys: 'Ctrl+8', method: 'openKnowledge' },
  { id: 'transferOut', label: '转办（协同机构）', keys: 'Ctrl+9', method: 'openTransferOut' }
]

function normalizeKey(key) {
  if (!key) return ''
  if (key === ' ' || key === 'Spacebar') return 'Space'
  if (key === 'Escape') return 'Esc'
  if (key === 'ArrowUp') return '↑'
  if (key === 'ArrowDown') return '↓'
  if (key === 'ArrowLeft') return '←'
  if (key === 'ArrowRight') return '→'
  if (key.length === 1) return key.toUpperCase()
  return key
}

/** 由键盘事件生成展示用组合键；无修饰键且非 F1-F12 时返回空串 */
export function formatKeys(e) {
  if (!e || !e.key) return ''
  const parts = []
  if (e.ctrlKey) parts.push('Ctrl')
  if (e.altKey) parts.push('Alt')
  if (e.shiftKey) parts.push('Shift')
  if (e.metaKey) parts.push('Meta')
  const key = normalizeKey(e.key)
  if (!key || ['Control', 'Alt', 'Shift', 'Meta'].includes(key)) return ''
  const isFunctionKey = /^F\d{1,2}$/.test(key)
  if (parts.length === 0 && !isFunctionKey) return ''
  parts.push(key)
  return parts.join('+')
}

/** 判断键盘事件是否命中某个组合键（严格比较修饰键，避免 Ctrl+K 命中 Ctrl+Shift+K） */
export function matchShortcut(e, keys) {
  if (!e || !keys) return false
  const parts = String(keys).split('+').map(s => s.trim()).filter(Boolean)
  if (parts.length === 0) return false
  const expectedKey = normalizeKey(parts.pop())
  if (normalizeKey(e.key) !== expectedKey) return false
  const has = p => parts.includes(p)
  if (!!e.ctrlKey !== has('Ctrl')) return false
  if (!!e.altKey !== has('Alt')) return false
  if (!!e.shiftKey !== has('Shift')) return false
  if (!!e.metaKey !== has('Meta')) return false
  return true
}

/**
 * 读取快捷键配置：默认值 + 本地覆盖（按 id 合并）。
 * 非法/重复/空值配置直接忽略并回退默认，避免出现"两个动作同一个键"。
 */
export function loadShortcuts() {
  const merged = DEFAULT_SHORTCUTS.map(s => ({ ...s }))
  let saved = null
  try {
    saved = JSON.parse(localStorage.getItem(SHORTCUTS_STORAGE_KEY))
  } catch (e) {
    saved = null
  }
  if (!Array.isArray(saved)) return merged
  const used = new Set()
  saved.forEach(item => {
    if (!item || !item.id || !item.keys || used.has(item.keys)) return
    const target = merged.find(s => s.id === item.id)
    if (!target) return
    // 与任何已确定键冲突时忽略该覆盖项
    if (merged.some(s => s.id !== item.id && s.keys === item.keys)) return
    target.keys = item.keys
    used.add(item.keys)
  })
  return merged
}

/** 保存快捷键配置（仅持久化 id + keys），返回合并后的完整配置 */
export function saveShortcuts(list) {
  const merged = DEFAULT_SHORTCUTS.map(s => {
    const hit = (list || []).find(x => x && x.id === s.id)
    return hit && hit.keys ? { ...s, keys: hit.keys } : { ...s }
  })
  try {
    localStorage.setItem(SHORTCUTS_STORAGE_KEY, JSON.stringify(merged.map(s => ({ id: s.id, keys: s.keys }))))
  } catch (e) {
    // 存储不可用：仅当前会话生效
  }
  return merged
}

/** 恢复默认（清除本地覆盖） */
export function resetShortcuts() {
  try {
    localStorage.removeItem(SHORTCUTS_STORAGE_KEY)
  } catch (e) {
    // ignore
  }
  return DEFAULT_SHORTCUTS.map(s => ({ ...s }))
}
