'use strict'

/**
 * U1 快捷键单测：默认映射、本地覆盖合并、组合键捕获与严格匹配。
 */
import {
  DEFAULT_SHORTCUTS,
  SHORTCUTS_STORAGE_KEY,
  formatKeys,
  loadShortcuts,
  matchShortcut,
  resetShortcuts,
  saveShortcuts
} from '@/utils/shortcuts'

function event(overrides) {
  return {
    key: '',
    ctrlKey: false,
    altKey: false,
    shiftKey: false,
    metaKey: false,
    ...overrides
  }
}

describe('U1 快捷键配置', () => {
  beforeEach(() => localStorage.clear())

  it('默认覆盖接听/保持/转接/台账/话后整理/挂机/建单/查知识/转办', () => {
    expect(DEFAULT_SHORTCUTS.map(s => s.id)).toEqual([
      'answer', 'hold', 'transfer', 'ledger', 'afterWork', 'hangup', 'createTicket', 'knowledge', 'transferOut'
    ])
    expect(loadShortcuts().map(s => s.keys)).toEqual([
      'Ctrl+1', 'Ctrl+2', 'Ctrl+3', 'Ctrl+4', 'Ctrl+5', 'Ctrl+6', 'Ctrl+7', 'Ctrl+8', 'Ctrl+9'
    ])
  })

  it('建单/查知识/转办携带权限标识，与按钮权限一致', () => {
    const byId = id => DEFAULT_SHORTCUTS.find(s => s.id === id)
    expect(byId('createTicket').perm).toBe('lawyers:call:ticket:add')
    expect(byId('knowledge').perm).toBe('lawyers:knowledge:query')
    expect(byId('transferOut').perm).toBe('lawyers:ticketTransfer:add')
    // 通话动作由坐席状态机约束，不额外挂菜单权限
    expect(byId('answer').perm).toBeUndefined()
    expect(byId('hangup').perm).toBeUndefined()
  })

  it('保存后按 id 覆盖默认值并持久化', () => {
    const saved = saveShortcuts([{ id: 'answer', keys: 'Alt+A' }])
    expect(saved.find(s => s.id === 'answer').keys).toBe('Alt+A')
    expect(saved.find(s => s.id === 'hangup').keys).toBe('Ctrl+6')
    expect(JSON.parse(localStorage.getItem(SHORTCUTS_STORAGE_KEY))).toEqual(
      expect.arrayContaining([{ id: 'answer', keys: 'Alt+A' }])
    )
    expect(loadShortcuts().find(s => s.id === 'answer').keys).toBe('Alt+A')
  })

  it('重复/未知/非法覆盖项被忽略，回退默认', () => {
    localStorage.setItem(SHORTCUTS_STORAGE_KEY, JSON.stringify([
      { id: 'answer', keys: 'Ctrl+2' },
      { id: 'notExist', keys: 'Ctrl+0' },
      { id: 'hold', keys: '' }
    ]))
    const list = loadShortcuts()
    expect(list.find(s => s.id === 'answer').keys).toBe('Ctrl+1')
    expect(list.find(s => s.id === 'hold').keys).toBe('Ctrl+2')
    expect(list).toHaveLength(DEFAULT_SHORTCUTS.length)
  })

  it('resetShortcuts 清除本地覆盖', () => {
    saveShortcuts([{ id: 'answer', keys: 'Alt+A' }])
    expect(resetShortcuts().find(s => s.id === 'answer').keys).toBe('Ctrl+1')
    expect(localStorage.getItem(SHORTCUTS_STORAGE_KEY)).toBeNull()
  })
})

describe('U1 组合键捕获与匹配', () => {
  it('formatKeys 生成展示用组合键', () => {
    expect(formatKeys(event({ key: '1', ctrlKey: true }))).toBe('Ctrl+1')
    expect(formatKeys(event({ key: 'k', ctrlKey: true, shiftKey: true }))).toBe('Ctrl+Shift+K')
    expect(formatKeys(event({ key: 'F2' }))).toBe('F2')
    expect(formatKeys(event({ key: ' ' , altKey: true }))).toBe('Alt+Space')
    expect(formatKeys(event({ key: 'Escape', ctrlKey: true }))).toBe('Ctrl+Esc')
  })

  it('formatKeys 拒绝纯修饰键与无修饰普通键', () => {
    expect(formatKeys(event({ key: 'Control', ctrlKey: true }))).toBe('')
    expect(formatKeys(event({ key: 'a' }))).toBe('')
  })

  it('matchShortcut 严格匹配修饰键组合', () => {
    expect(matchShortcut(event({ key: '1', ctrlKey: true }), 'Ctrl+1')).toBe(true)
    expect(matchShortcut(event({ key: '1', ctrlKey: true, shiftKey: true }), 'Ctrl+1')).toBe(false)
    expect(matchShortcut(event({ key: 'k', ctrlKey: true }), 'Alt+K')).toBe(false)
    expect(matchShortcut(event({ key: 'k', ctrlKey: true, altKey: true }), 'Alt+Ctrl+K')).toBe(true)
    expect(matchShortcut(event({ key: 'Escape' }), 'Esc')).toBe(true)
  })
})
