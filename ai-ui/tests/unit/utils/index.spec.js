'use strict'

/**
 * 通用工具函数单测：覆盖时间格式化、查询串解析、字节长度、对象合并、DOM class 操作与防抖。
 */
import {
  formatDate,
  formatTime,
  getQueryObject,
  byteLength,
  cleanArray,
  param,
  param2Obj,
  html2Text,
  objectMerge,
  toggleClass,
  getTime,
  debounce,
  deepClone,
  uniqueArr,
  createUniqueString,
  hasClass,
  addClass,
  removeClass,
  makeMap,
  titleCase,
  camelCase,
  isNumberStr,
  exportDefault,
  beautifierConf
} from '@/utils/index'

describe('formatDate', () => {
  test('空值返回空串，正常日期补零', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate('')).toBe('')
    expect(formatDate(new Date(2024, 0, 2, 3, 4, 5))).toBe('2024-01-02 03:04:05')
  })
})

describe('formatTime 相对时间', () => {
  test('按间隔返回刚刚/分钟/小时/1天前', () => {
    expect(formatTime(Date.now() - 10 * 1000)).toBe('刚刚')
    expect(formatTime(Date.now() - 2 * 60 * 1000)).toBe('2分钟前')
    expect(formatTime(Date.now() - 2 * 3600 * 1000)).toBe('2小时前')
    expect(formatTime(Date.now() - 25 * 3600 * 1000)).toBe('1天前')
  })

  test('更早时间支持自定义格式或默认月日时分', () => {
    const time = new Date(2024, 0, 2, 3, 4, 5).getTime()

    expect(formatTime(time, '{y}-{m}-{d}')).toBe('2024-01-02')
    expect(formatTime(time)).toBe('1月2日3时4分')
  })

  test('10 位秒级时间戳自动换算', () => {
    const seconds = Math.floor(new Date(2024, 0, 2).getTime() / 1000)

    expect(formatTime(seconds, '{y}-{m}-{d}')).toBe('2024-01-02')
  })
})

describe('查询串与编码工具', () => {
  test('getQueryObject 解析 URL 参数', () => {
    expect(getQueryObject('http://a.com/list?x=1&y=%E4%B8%AD')).toEqual({ x: '1', y: '中' })
  })

  test('byteLength 计算 UTF-8 字节数', () => {
    expect(byteLength('abc')).toBe(3)
    expect(byteLength('中文')).toBe(6)
    expect(byteLength('a中')).toBe(4)
  })

  test('param 过滤空值并编码，cleanArray 去空', () => {
    expect(cleanArray([1, 0, '', null, 2])).toEqual([1, 2])
    expect(param({ a: 1, b: undefined, c: '中' })).toBe('a=1&c=%E4%B8%AD')
    expect(param(null)).toBe('')
  })

  test('param2Obj 解析查询串，+ 号还原为空格', () => {
    expect(param2Obj('/api?a=1&b=2')).toEqual({ a: '1', b: '2' })
    expect(param2Obj('/api?name=a+b')).toEqual({ name: 'a b' })
    expect(param2Obj('/api')).toEqual({})
  })

  test('html2Text 提取纯文本', () => {
    expect(html2Text('<b>加粗</b>内容')).toBe('加粗内容')
  })
})

describe('对象与数组工具', () => {
  test('objectMerge 递归合并，数组返回副本', () => {
    expect(objectMerge({ a: { x: 1 } }, { a: { y: 2 }, b: 1 })).toEqual({ a: { x: 1, y: 2 }, b: 1 })
    const source = [1, 2]
    expect(objectMerge({}, source)).toEqual(source)
    expect(objectMerge('bad', { a: 1 })).toEqual({ a: 1 })
  })

  test('deepClone 深拷贝对象与数组', () => {
    const source = { a: { b: 1 }, list: [{ c: 2 }] }
    const copy = deepClone(source)

    expect(copy).toEqual(source)
    expect(copy).not.toBe(source)
    expect(copy.a).not.toBe(source.a)
    expect(deepClone([1, 2])).toEqual([1, 2])
  })

  test('uniqueArr 去重，createUniqueString 生成非空唯一串', () => {
    expect(uniqueArr([1, 1, 2])).toEqual([1, 2])
    const value = createUniqueString()
    expect(typeof value).toBe('string')
    expect(value.length).toBeGreaterThan(0)
  })
})

describe('DOM class 操作', () => {
  test('addClass/hasClass/removeClass/toggleClass', () => {
    const element = document.createElement('div')

    addClass(element, 'a')
    expect(hasClass(element, 'a')).toBe(true)
    expect(element.className).toContain('a')

    addClass(element, 'a')
    expect(element.className.match(/a/g)).toHaveLength(1)

    removeClass(element, 'a')
    expect(hasClass(element, 'a')).toBe(false)

    element.className = 'x'
    toggleClass(element, 'x')
    expect(element.className).not.toContain('x')
    toggleClass(element, 'y')
    expect(element.className).toContain('y')

    toggleClass(null, 'y')
  })
})

describe('其它工具', () => {
  test('getTime 返回起始/结束时间', () => {
    expect(typeof getTime('start')).toBe('number')
    expect(getTime('end')).toBeInstanceOf(Date)
  })

  test('debounce 延迟执行且 immediate 立即执行', () => {
    jest.useFakeTimers()
    const fn = jest.fn()
    const delayed = debounce(fn, 100)
    const immediate = debounce(fn, 100, true)

    delayed()
    immediate()
    expect(fn).toHaveBeenCalledTimes(1)
    jest.advanceTimersByTime(100)
    expect(fn).toHaveBeenCalledTimes(2)
    jest.useRealTimers()
  })

  test('makeMap 大小写敏感与不敏感', () => {
    const strict = makeMap('a,b')
    const loose = makeMap('a,b', true)

    expect(strict('a')).toBe(true)
    expect(strict('A')).toBeFalsy()
    expect(loose('A')).toBe(true)
  })

  test('命名转换与数字串校验', () => {
    expect(titleCase('hello world')).toBe('Hello World')
    expect(camelCase('user_name')).toBe('userName')
    expect(isNumberStr('12')).toBe(true)
    expect(isNumberStr('-1.5')).toBe(true)
    expect(isNumberStr('1a')).toBe(false)
  })

  test('导出常量与代码美化配置', () => {
    expect(exportDefault).toBe('export default ')
    expect(beautifierConf.html.indent_size).toBe('2')
    expect(beautifierConf.js.jslint_happy).toBe(true)
  })
})
