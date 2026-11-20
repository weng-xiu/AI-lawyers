'use strict'

/**
 * AI 智能体配置 API 单测：锁定配置 CRUD、启用列表与连接测试契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/agent'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listAgentConfig', [{ pageNum: 1 }], {
    url: '/lawyers/agent/config/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getAgentConfig', [8], { url: '/lawyers/agent/config/8', method: 'get' }],
  ['listActiveAgent', [], { url: '/lawyers/agent/config/active', method: 'get' }],
  ['addAgentConfig', [{ agentName: '接待助手' }], {
    url: '/lawyers/agent/config',
    method: 'post',
    data: { agentName: '接待助手' }
  }],
  ['updateAgentConfig', [{ agentId: 8 }], {
    url: '/lawyers/agent/config',
    method: 'put',
    data: { agentId: 8 }
  }],
  ['delAgentConfig', [8], { url: '/lawyers/agent/config/8', method: 'delete' }],
  ['testAgentConfig', [8], { url: '/lawyers/agent/config/test/8', method: 'get' }]
]

describe('agent 接口契约', () => {
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
