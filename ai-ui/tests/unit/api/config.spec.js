'use strict'

/**
 * 参数配置 API 单测：锁定参数 CRUD、键名查询与刷新缓存契约。
 */
import request from '@/utils/request'
import * as api from '@/api/system/config'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listConfig', [{ pageNum: 1 }], {
    url: '/system/config/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getConfig', [3], { url: '/system/config/3', method: 'get' }],
  ['getConfigKey', ['sys.index.skinName'], {
    url: '/system/config/configKey/sys.index.skinName',
    method: 'get'
  }],
  ['addConfig', [{ configKey: 'k' }], {
    url: '/system/config',
    method: 'post',
    data: { configKey: 'k' }
  }],
  ['updateConfig', [{ configId: 3 }], {
    url: '/system/config',
    method: 'put',
    data: { configId: 3 }
  }],
  ['delConfig', [3], { url: '/system/config/3', method: 'delete' }],
  ['refreshCache', [], { url: '/system/config/refreshCache', method: 'delete' }]
]

describe('config 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test.each(cases)('%s 按约定调用 request', async (name, args, expected) => {
    const result = await api[name](...args)

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith(expected)
    expect(result).toEqual({ code: 200 })
  })

  test('导出函数与用例表一一对应', () => {
    const covered = [...new Set(cases.map(item => item[0]))].sort()
    expect(Object.keys(api).sort()).toEqual(covered)
  })
})
