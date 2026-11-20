'use strict'

/**
 * IVR 流程 API 单测：锁定流程 CRUD、发布、设计保存与默认流程查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrFlow'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listFlow', [{ pageNum: 1 }], { url: '/lawyers/ivr/flow/list', method: 'get', params: { pageNum: 1 } }],
  ['getFlow', [5], { url: '/lawyers/ivr/flow/5', method: 'get' }],
  ['addFlow', [{ flowName: '主流程' }], {
    url: '/lawyers/ivr/flow',
    method: 'post',
    data: { flowName: '主流程' }
  }],
  ['updateFlow', [{ flowId: 5 }], {
    url: '/lawyers/ivr/flow',
    method: 'put',
    data: { flowId: 5 }
  }],
  ['delFlow', ['5,6'], { url: '/lawyers/ivr/flow/5,6', method: 'delete' }],
  ['publishFlow', [5], { url: '/lawyers/ivr/flow/publish/5', method: 'post' }],
  ['saveDesign', [{ flowId: 5, nodes: [] }], {
    url: '/lawyers/ivr/flow/design',
    method: 'post',
    data: { flowId: 5, nodes: [] }
  }],
  ['getPublishedFlows', [], { url: '/lawyers/ivr/flow/published', method: 'get' }],
  ['getDefaultFlow', [], { url: '/lawyers/ivr/flow/default', method: 'get' }]
]

describe('ivrFlow 接口契约', () => {
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
