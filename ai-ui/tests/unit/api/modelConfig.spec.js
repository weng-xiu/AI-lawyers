'use strict'

/**
 * AI 模型配置 API 单测：锁定配置 CRUD、默认配置与连通性测试契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/modelConfig'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listModelConfig', [{ pageNum: 1 }], {
    url: '/lawyers/modelConfig/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getModelConfig', [2], { url: '/lawyers/modelConfig/2', method: 'get' }],
  ['addModelConfig', [{ modelName: 'doubao' }], {
    url: '/lawyers/modelConfig',
    method: 'post',
    data: { modelName: 'doubao' }
  }],
  ['updateModelConfig', [{ configId: 2 }], {
    url: '/lawyers/modelConfig',
    method: 'put',
    data: { configId: 2 }
  }],
  ['delModelConfig', [2], { url: '/lawyers/modelConfig/2', method: 'delete' }],
  ['getDefaultModelConfig', [], { url: '/lawyers/modelConfig/default', method: 'get' }],
  ['setDefaultModelConfig', [2], {
    url: '/lawyers/modelConfig/setDefault/2',
    method: 'put'
  }],
  ['testModelConnection', [2], { url: '/lawyers/modelConfig/test/2', method: 'post' }]
]

describe('modelConfig 接口契约', () => {
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
