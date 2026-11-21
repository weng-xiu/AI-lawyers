'use strict'

/**
 * RSA 加解密工具单测：锁定登录密码前端加密与解密还原行为。
 */
import { encrypt, decrypt } from '@/utils/jsencrypt'

describe('RSA 加解密', () => {
  test('加密结果非空且不同于明文', () => {
    const cipher = encrypt('admin@123')

    expect(typeof cipher).toBe('string')
    expect(cipher.length).toBeGreaterThan(0)
    expect(cipher).not.toBe('admin@123')
  })

  test('同一明文可被私钥解密还原', () => {
    const text = 'lawyers#2024'

    expect(decrypt(encrypt(text))).toBe(text)
  })

  test('非法密文解密返回空值', () => {
    expect(decrypt('not-a-valid-cipher')).toBeFalsy()
  })
})
