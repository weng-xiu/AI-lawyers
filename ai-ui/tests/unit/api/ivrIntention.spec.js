'use strict'

/**
 * IVR 意图配置 API 单测：锁定意图 CRUD、启用列表与文本匹配契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrIntention'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listIntention', [{ pageNum: 1 }], {
    url: '/lawyers/ivr/intention/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getIntention', [7], { url: '/lawyers/ivr/intention/7', method: 'get' }],
  ['addIntention', [{ intentionName: '转人工' }], {
    url: '/lawyers/ivr/intention',
    method: 'post',
    data: { intentionName: '转人工' }
  }],
  ['updateIntention', [{ intentionId: 7 }], {
    url: '/lawyers/ivr/intention',
    method: 'put',
    data: { intentionId: 7 }
  }],
  ['delIntention', ['7,8'], { url: '/lawyers/ivr/intention/7,8', method: 'delete' }],
  ['getActiveIntentions', [], { url: '/lawyers/ivr/intention/active', method: 'get' }],
  ['matchIntention', ['我要转人工'], {
    url: '/lawyers/ivr/intention/match',
    method: 'post',
    data: { inputText: '我要转人工' }
  }]
]

describe('ivrIntention 接口契约', () => {
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
