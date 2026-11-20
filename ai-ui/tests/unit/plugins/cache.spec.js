'use strict'

/**
 * 浏览器缓存插件单测：验证 session/local 两套命名空间对字符串与 JSON 的读写移除，
 * 以及空键值时的安全返回。
 */
import cache from '@/plugins/cache'

beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('session 会话级缓存', () => {
  test('字符串读写与移除', () => {
    cache.session.set('k', 'v')
    expect(cache.session.get('k')).toBe('v')

    cache.session.remove('k')
    expect(cache.session.get('k')).toBeNull()
  })

  test('JSON 读写', () => {
    cache.session.setJSON('obj', { a: 1 })

    expect(cache.session.getJSON('obj')).toEqual({ a: 1 })
    expect(cache.session.getJSON('missing')).toBeNull()
  })

  test('空键值不写入且 get 返回 null', () => {
    cache.session.set(null, 'v')
    cache.session.set('k', null)

    expect(cache.session.get(null)).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })
})

describe('local 持久缓存', () => {
  test('字符串与 JSON 读写', () => {
    cache.local.set('token', 'tk')
    cache.local.setJSON('profile', { name: 'a' })

    expect(cache.local.get('token')).toBe('tk')
    expect(cache.local.getJSON('profile')).toEqual({ name: 'a' })
  })

  test('移除与空键处理', () => {
    cache.local.set('k', 'v')
    cache.local.remove('k')
    expect(cache.local.get('k')).toBeNull()

    cache.local.setJSON('k2', null)
    expect(localStorage.getItem('k2')).toBeNull()
  })

  test('session 与 local 命名空间互不影响', () => {
    cache.session.set('same', 's')
    cache.local.set('same', 'l')

    expect(cache.session.get('same')).toBe('s')
    expect(cache.local.get('same')).toBe('l')
  })
})
