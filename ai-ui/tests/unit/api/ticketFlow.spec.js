'use strict'

/**
 * 工单流程规则 API 单测：锁定规则查询、校验与画布布局读写契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ticketFlow'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const layout = [{ id: 'draft', x: 10, y: 20 }]

const cases = [
  ['listTicketFlow', [{ flowCode: 'default' }], {
    url: '/lawyers/ticket-flow/list',
    method: 'get',
    params: { flowCode: 'default' }
  }],
  ['getTicketFlow', [3], { url: '/lawyers/ticket-flow/3', method: 'get' }],
  ['validateTicketFlow', ['main flow'], {
    url: '/lawyers/ticket-flow/validate/main%20flow',
    method: 'get'
  }],
  ['getTicketFlowLayout', ['main'], {
    url: '/lawyers/ticket-flow/layout/main',
    method: 'get'
  }],
  ['saveTicketFlowLayout', ['main', layout], {
    url: '/lawyers/ticket-flow/layout/main',
    method: 'put',
    data: layout
  }],
  ['addTicketFlow', [{ flowCode: 'main' }], {
    url: '/lawyers/ticket-flow',
    method: 'post',
    data: { flowCode: 'main' }
  }],
  ['updateTicketFlow', [{ flowId: 3 }], {
    url: '/lawyers/ticket-flow',
    method: 'put',
    data: { flowId: 3 }
  }]
]

describe('ticketFlow 接口契约', () => {
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
