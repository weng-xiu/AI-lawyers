'use strict'

/**
 * AI 律师辅助会话 API 单测：锁定会话查询、分析/小结与删除契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/aiAssist'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listAiAssist', [{ pageNum: 1 }], {
    url: '/lawyers/ai/assist/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getAiAssist', ['s-1'], { url: '/lawyers/ai/assist/s-1', method: 'get' }],
  ['getAiAssistByRecord', [88], {
    url: '/lawyers/ai/assist/byRecord',
    method: 'get',
    params: { recordId: 88 }
  }],
  ['analyzeAiAssist', [{ sessionId: 's-1' }], {
    url: '/lawyers/ai/assist/analyze',
    method: 'post',
    data: { sessionId: 's-1' }
  }],
  ['summarizeAiAssist', [{ sessionId: 's-1' }], {
    url: '/lawyers/ai/assist/summarize',
    method: 'post',
    data: { sessionId: 's-1' }
  }],
  ['delAiAssist', ['s-1,s-2'], { url: '/lawyers/ai/assist/s-1,s-2', method: 'delete' }]
]

describe('aiAssist 接口契约', () => {
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
