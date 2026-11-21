'use strict'

/**
 * 动态标题工具单测：锁定开关开启时拼接业务标题、关闭时回落默认标题。
 */
import store from '@/store'
import defaultSettings from '@/settings'
import { useDynamicTitle } from '@/utils/dynamicTitle'

jest.mock('@/store', () => ({
  __esModule: true,
  default: {
    state: {
      settings: {
        dynamicTitle: false,
        title: '呼叫中心'
      }
    }
  }
}))

jest.mock('@/settings', () => ({
  __esModule: true,
  default: { title: 'AI 法务中台' }
}))

beforeEach(() => {
  document.title = ''
  store.state.settings.dynamicTitle = false
})

describe('动态标题', () => {
  test('关闭动态标题时使用默认标题', () => {
    useDynamicTitle()

    expect(document.title).toBe(defaultSettings.title)
    expect(document.title).toBe('AI 法务中台')
  })

  test('开启动态标题时拼接业务标题', () => {
    store.state.settings.dynamicTitle = true

    useDynamicTitle()

    expect(document.title).toBe('呼叫中心 - AI 法务中台')
  })
})
