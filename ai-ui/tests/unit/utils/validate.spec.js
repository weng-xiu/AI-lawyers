'use strict'

/**
 * 校验工具单测：覆盖路由路径匹配与各类格式校验，避免登录/菜单外链判断回归。
 */
import {
  isPathMatch,
  isEmpty,
  isHttp,
  isExternal,
  validUsername,
  validURL,
  validLowerCase,
  validUpperCase,
  validAlphabets,
  validEmail,
  isString,
  isArray
} from '@/utils/validate'

describe('isPathMatch 路径匹配', () => {
  test('精确匹配与单星号不跨层级匹配', () => {
    expect(isPathMatch('/system/user', '/system/user')).toBe(true)
    expect(isPathMatch('/system/*', '/system/user')).toBe(true)
    expect(isPathMatch('/system/*', '/system/user/list')).toBe(false)
  })
})

describe('空值与链接判断', () => {
  test('isEmpty 识别 null/空串/undefined 字面量', () => {
    expect(isEmpty(null)).toBe(true)
    expect(isEmpty('')).toBe(true)
    expect(isEmpty(undefined)).toBe(true)
    expect(isEmpty('undefined')).toBe(true)
    expect(isEmpty('值')).toBe(false)
    expect(isEmpty('0')).toBe(false)
  })

  test('isHttp 判断字符串是否包含 http(s) 前缀', () => {
    expect(isHttp('https://example.com')).toBe(true)
    expect(isHttp('http://example.com')).toBe(true)
    expect(isHttp('/system/user')).toBe(false)
  })

  test('isExternal 仅识别外链协议', () => {
    expect(isExternal('https://example.com')).toBe(true)
    expect(isExternal('http://example.com')).toBe(true)
    expect(isExternal('mailto:a@b.com')).toBe(true)
    expect(isExternal('tel:10086')).toBe(true)
    expect(isExternal('/system/user')).toBe(false)
  })
})

describe('账号与 URL 校验', () => {
  test('validUsername 仅允许内置账号并忽略首尾空格', () => {
    expect(validUsername('admin')).toBe(true)
    expect(validUsername(' editor ')).toBe(true)
    expect(validUsername('guest')).toBe(false)
  })

  test('validURL 支持 http/ftp 与端口路径', () => {
    expect(validURL('https://example.com')).toBe(true)
    expect(validURL('ftp://example.com/file')).toBe(true)
    expect(validURL('https://example.com:8080/a?b=1')).toBe(true)
    expect(validURL('example.com')).toBe(false)
    expect(validURL('http://')).toBe(false)
  })
})

describe('字符形态校验', () => {
  test('大小写与字母校验', () => {
    expect(validLowerCase('abc')).toBe(true)
    expect(validLowerCase('Abc')).toBe(false)
    expect(validUpperCase('ABC')).toBe(true)
    expect(validUpperCase('ABC1')).toBe(false)
    expect(validAlphabets('AbC')).toBe(true)
    expect(validAlphabets('Ab1')).toBe(false)
  })

  test('validEmail 常规邮箱判定', () => {
    expect(validEmail('user@example.com')).toBe(true)
    expect(validEmail('user.name@sub.example.cn')).toBe(true)
    expect(validEmail('user@')).toBe(false)
    expect(validEmail('not-an-email')).toBe(false)
  })

  test('isString/isArray 类型判定', () => {
    expect(isString('a')).toBe(true)
    expect(isString(new String('a'))).toBe(true)
    expect(isString(1)).toBe(false)
    expect(isArray([])).toBe(true)
    expect(isArray({ length: 0 })).toBe(false)
  })
})
