'use strict'

/**
 * 字典模型单测：覆盖初始化、取值映射、懒加载重载与异常响应处理。
 */
import Dict from '@/utils/dict/Dict'
import DictData from '@/utils/dict/DictData'

describe('Dict 初始化', () => {
  test('未传类型时初始化空元数据', async () => {
    const dict = new Dict()

    await dict.init()

    expect(dict._dictMetas).toEqual([])
    expect(dict.label).toEqual({})
    expect(dict.type).toEqual({})
  })

  test('传入类型数组时创建空容器并解析元数据', async () => {
    const dict = new Dict()

    await dict.init(['sys_yes_no'])

    expect(dict._dictMetas).toHaveLength(1)
    expect(dict._dictMetas[0].type).toBe('sys_yes_no')
    expect(dict.type.sys_yes_no).toEqual([])
    expect(dict.label.sys_yes_no).toEqual({})
  })

  test('对象配置支持自定义 request 并写入 DictData 与标签映射', async () => {
    const dict = new Dict()
    const request = jest.fn(() => Promise.resolve([
      { label: '启用', value: 1 },
      { label: '停用', value: 0 }
    ]))

    await dict.init({ types: [{ type: 'status', request }] })

    expect(request).toHaveBeenCalledTimes(1)
    expect(dict.type.status).toHaveLength(2)
    expect(dict.type.status[0]).toBeInstanceOf(DictData)
    expect(dict.type.status[0].raw).toEqual({ label: '启用', value: 1 })
    expect(dict.label.status[1]).toBe('启用')
    expect(dict.label.status[0]).toBe('停用')
  })
})

describe('Dict 懒加载与重载', () => {
  test('lazy 类型初始化不请求，reloadDict 时才加载', async () => {
    const request = jest.fn(() => Promise.resolve([{ label: '是', value: 'Y' }]))
    const dict = new Dict()

    await dict.init([{ type: 'lazyYes', lazy: true, request }])

    expect(request).not.toHaveBeenCalled()
    expect(dict.type.lazyYes).toEqual([])

    await dict.reloadDict('lazyYes')

    expect(request).toHaveBeenCalledTimes(1)
    expect(dict.label.lazyYes.Y).toBe('是')
  })

  test('重载不存在的类型时返回 rejected promise', async () => {
    const dict = new Dict()
    await dict.init([])

    await expect(dict.reloadDict('missing')).rejects.toBe('the dict meta of missing was not found')
  })
})

describe('Dict 异常响应', () => {
  let consoleSpy

  beforeEach(() => {
    consoleSpy = jest.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleSpy.mockRestore()
  })

  test('responseConverter 返回非数组时提示并保持空列表', async () => {
    const dict = new Dict()

    await dict.init([{
      type: 'badShape',
      lazy: false,
      request: () => Promise.resolve([]),
      responseConverter: () => 'not-array'
    }])

    expect(consoleSpy).toHaveBeenCalled()
    expect(dict.type.badShape).toEqual([])
  })

  test('responseConverter 返回非 DictData 元素时提示并保持空列表', async () => {
    const dict = new Dict()

    await dict.init([{
      type: 'badItem',
      lazy: false,
      request: () => Promise.resolve([]),
      responseConverter: () => [{ label: '伪数据', value: 1 }]
    }])

    expect(consoleSpy).toHaveBeenCalled()
    expect(dict.type.badItem).toEqual([])
  })
})

describe('Dict 参数校验', () => {
  test('types 为 undefined 时抛出缺少类型错误', () => {
    const dict = new Dict()

    expect(() => dict.init({ types: undefined })).toThrow('need dict types')
  })
})
