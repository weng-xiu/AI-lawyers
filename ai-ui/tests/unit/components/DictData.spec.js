'use strict'

/**
 * DictData 字典插件单测：锁定安装配置、缓存优先与接口回填后的 store 写入。
 */
import Vue from 'vue'
import DataDict from '@/utils/dict'
import store from '@/store'
import { getDicts } from '@/api/system/dict/data'
import DictData from '@/components/DictData/index.js'

jest.mock('@/utils/dict', () => ({ __esModule: true, default: jest.fn() }))
jest.mock('@/store', () => ({
  __esModule: true,
  default: { getters: { dict: [] }, dispatch: jest.fn() }
}))
jest.mock('@/api/system/dict/data', () => ({ getDicts: jest.fn() }))

describe('DictData', () => {
  let meta

  beforeAll(() => {
    DictData.install()
    const config = DataDict.mock.calls[0][1]
    meta = config.metas['*']
  })

  beforeEach(() => {
    store.dispatch.mockClear()
    getDicts.mockReset()
    store.getters.dict = []
  })

  test('通过 Vue.use 安装并声明字典字段', () => {
    expect(DataDict).toHaveBeenCalledWith(Vue, expect.objectContaining({ metas: expect.any(Object) }))
    expect(meta.labelField).toBe('dictLabel')
    expect(meta.valueField).toBe('dictValue')
  })

  test('命中 store 缓存时直接返回且不请求接口', async () => {
    const cached = [{ dictLabel: '男', dictValue: '0' }]
    store.getters.dict = [{ key: 'sys_user_sex', value: cached }]

    await expect(meta.request({ type: 'sys_user_sex' })).resolves.toEqual(cached)
    expect(getDicts).not.toHaveBeenCalled()
  })

  test('未命中缓存时请求接口并写入 store', async () => {
    const rows = [{ dictLabel: '启用', dictValue: '0' }]
    getDicts.mockResolvedValue({ data: rows })

    await expect(meta.request({ type: 'sys_normal_disable' })).resolves.toEqual(rows)
    expect(getDicts).toHaveBeenCalledWith('sys_normal_disable')
    expect(store.dispatch).toHaveBeenCalledWith('dict/setDict', {
      key: 'sys_normal_disable',
      value: rows
    })
  })

  test('接口失败时向上抛出错误', async () => {
    const error = new Error('network')
    getDicts.mockRejectedValue(error)

    await expect(meta.request({ type: 'unknown' })).rejects.toThrow('network')
    expect(store.dispatch).not.toHaveBeenCalled()
  })
})
