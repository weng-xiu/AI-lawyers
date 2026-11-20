'use strict'

/**
 * 呼叫报表 API 单测：锁定四类报表、物化回填与 F6 自助分析接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/report'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getCallReport', [{ startDate: '2026-01-01' }], {
    url: '/lawyers/report/call',
    method: 'get',
    params: { startDate: '2026-01-01' }
  }],
  ['getServiceReport', [{ agentId: 2 }], {
    url: '/lawyers/report/service',
    method: 'get',
    params: { agentId: 2 }
  }],
  ['getQualityReport', [{ pageNum: 1 }], {
    url: '/lawyers/report/quality',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getBusinessReport', [{ ticketType: 'A' }], {
    url: '/lawyers/report/business',
    method: 'get',
    params: { ticketType: 'A' }
  }],
  ['statBackfill', [{ startDate: '2026-01-01' }], {
    url: '/lawyers/report/stat/backfill',
    method: 'post',
    params: { startDate: '2026-01-01' }
  }],
  ['statBackfillProgress', ['task-1'], {
    url: '/lawyers/report/stat/backfill/progress',
    method: 'get',
    params: { taskId: 'task-1' }
  }],
  ['getAdhocCatalog', [], { url: '/lawyers/report/adhoc/catalog', method: 'get' }],
  ['queryAdhoc', [{ metrics: 'calls', dimension: 'day' }], {
    url: '/lawyers/report/adhoc/query',
    method: 'get',
    params: { metrics: 'calls', dimension: 'day' }
  }],
  ['drillAdhoc', [{ pageNum: 1, rows: 20 }], {
    url: '/lawyers/report/adhoc/drill',
    method: 'get',
    params: { pageNum: 1, rows: 20 }
  }]
]

describe('report 接口契约', () => {
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
