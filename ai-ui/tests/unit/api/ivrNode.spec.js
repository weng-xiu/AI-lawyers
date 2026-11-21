'use strict'

/**
 * IVR 流程节点 API 单测：锁定节点 CRUD、按流程查询/删除与批量保存契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrNode'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listNode', [{ flowId: 5 }], {
    url: '/lawyers/ivr/node/list',
    method: 'get',
    params: { flowId: 5 }
  }],
  ['getNode', [11], { url: '/lawyers/ivr/node/11', method: 'get' }],
  ['getNodesByFlowId', [5], { url: '/lawyers/ivr/node/flow/5', method: 'get' }],
  ['addNode', [{ nodeType: 'start' }], {
    url: '/lawyers/ivr/node',
    method: 'post',
    data: { nodeType: 'start' }
  }],
  ['updateNode', [{ nodeId: 11 }], {
    url: '/lawyers/ivr/node',
    method: 'put',
    data: { nodeId: 11 }
  }],
  ['delNode', ['11,12'], { url: '/lawyers/ivr/node/11,12', method: 'delete' }],
  ['delNodeByFlowId', [5], { url: '/lawyers/ivr/node/flow/5', method: 'delete' }],
  ['batchSaveNode', [5, [{ nodeId: 11 }]], {
    url: '/lawyers/ivr/node/batch/5',
    method: 'post',
    data: [{ nodeId: 11 }]
  }]
]

describe('ivrNode 接口契约', () => {
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
