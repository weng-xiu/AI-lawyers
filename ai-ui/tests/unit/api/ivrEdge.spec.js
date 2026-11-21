'use strict'

/**
 * IVR 流程连线 API 单测：锁定连线 CRUD、按流程查询/删除与批量保存契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrEdge'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listEdge', [{ flowId: 5 }], {
    url: '/lawyers/ivr/edge/list',
    method: 'get',
    params: { flowId: 5 }
  }],
  ['getEdge', [21], { url: '/lawyers/ivr/edge/21', method: 'get' }],
  ['getEdgesByFlowId', [5], { url: '/lawyers/ivr/edge/flow/5', method: 'get' }],
  ['addEdge', [{ source: 11 }], {
    url: '/lawyers/ivr/edge',
    method: 'post',
    data: { source: 11 }
  }],
  ['updateEdge', [{ edgeId: 21 }], {
    url: '/lawyers/ivr/edge',
    method: 'put',
    data: { edgeId: 21 }
  }],
  ['delEdge', ['21,22'], { url: '/lawyers/ivr/edge/21,22', method: 'delete' }],
  ['delEdgeByFlowId', [5], { url: '/lawyers/ivr/edge/flow/5', method: 'delete' }],
  ['batchSaveEdge', [5, [{ edgeId: 21 }]], {
    url: '/lawyers/ivr/edge/batch/5',
    method: 'post',
    data: [{ edgeId: 21 }]
  }]
]

describe('ivrEdge 接口契约', () => {
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
