'use strict'

/**
 * IVR 执行引擎 API 单测：锁定流程执行与意图识别调试契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/ivrEngine'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['executeFlow', [{ flowId: 5, input: '1' }], {
    url: '/lawyers/ivr/engine/execute',
    method: 'post',
    data: { flowId: 5, input: '1' }
  }],
  ['recognizeIntention', [{ text: '我要咨询工伤' }], {
    url: '/lawyers/ivr/engine/intention',
    method: 'post',
    data: { text: '我要咨询工伤' }
  }]
]

describe('ivrEngine 接口契约', () => {
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
