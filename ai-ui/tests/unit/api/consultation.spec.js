'use strict'

/**
 * 法律咨询 API 单测：锁定咨询记录 CRUD、导出与多维度统计接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/consultation'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listConsultation', [{ pageNum: 1 }], {
    url: '/lawyers/consultation/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getConsultation', [5], { url: '/lawyers/consultation/5', method: 'get' }],
  ['getUserConsultations', [12], { url: '/lawyers/consultation/user/12', method: 'get' }],
  ['addConsultation', [{ question: '咨询' }], {
    url: '/lawyers/consultation',
    method: 'post',
    data: { question: '咨询' }
  }],
  ['updateConsultation', [{ consultationId: 5 }], {
    url: '/lawyers/consultation',
    method: 'put',
    data: { consultationId: 5 }
  }],
  ['delConsultation', [5], { url: '/lawyers/consultation/5', method: 'delete' }],
  ['exportConsultation', [{ channel: 'voice' }], {
    url: '/lawyers/consultation/export',
    method: 'post',
    params: { channel: 'voice' }
  }],
  ['getConsultationStatistics', [], { url: '/lawyers/consultation/statistics', method: 'get' }],
  ['getConsultationByCategory', [], {
    url: '/lawyers/consultation/statistics/category',
    method: 'get'
  }],
  ['getConsultationByDate', [7], {
    url: '/lawyers/consultation/statistics/date',
    method: 'get',
    params: { days: 7 }
  }],
  ['getConsultationChannelStats', [], { url: '/lawyers/consultation/stats', method: 'get' }]
]

describe('consultation 接口契约', () => {
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
