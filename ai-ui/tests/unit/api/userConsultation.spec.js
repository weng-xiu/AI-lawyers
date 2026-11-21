'use strict'

/**
 * 用户端咨询 API 单测：锁定提交、结果/详情、历史、评价与分类契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/userConsultation'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['submitConsultation', [{ question: '工伤如何认定' }], {
    url: '/lawyers/userConsultation/submit',
    method: 'post',
    data: { question: '工伤如何认定' }
  }],
  ['getConsultationResult', ['c-1'], {
    url: '/lawyers/userConsultation/result/c-1',
    method: 'get'
  }],
  ['getUserHistory', [{ pageNum: 1 }], {
    url: '/lawyers/userConsultation/history',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getConsultationDetail', ['c-1'], {
    url: '/lawyers/userConsultation/info/c-1',
    method: 'get'
  }],
  ['submitEvaluation', [{ consultationId: 'c-1', score: 5 }], {
    url: '/lawyers/userConsultation/evaluation',
    method: 'post',
    data: { consultationId: 'c-1', score: 5 }
  }],
  ['getQuestionCategories', [], {
    url: '/lawyers/userConsultation/categories',
    method: 'get'
  }]
]

describe('userConsultation 接口契约', () => {
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
