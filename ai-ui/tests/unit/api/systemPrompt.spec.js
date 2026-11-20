'use strict'

/**
 * 系统提示词 API 单测：锁定提示词 CRUD、默认配置与预览接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/systemPrompt'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listSystemPrompt', [{ pageNum: 1 }], {
    url: '/lawyers/systemPrompt/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getSystemPrompt', [3], { url: '/lawyers/systemPrompt/3', method: 'get' }],
  ['addSystemPrompt', [{ promptName: '接待' }], {
    url: '/lawyers/systemPrompt',
    method: 'post',
    data: { promptName: '接待' }
  }],
  ['updateSystemPrompt', [{ promptId: 3 }], {
    url: '/lawyers/systemPrompt',
    method: 'put',
    data: { promptId: 3 }
  }],
  ['delSystemPrompt', [3], { url: '/lawyers/systemPrompt/3', method: 'delete' }],
  ['getDefaultSystemPrompt', ['chat'], {
    url: '/lawyers/systemPrompt/default/chat',
    method: 'get'
  }],
  ['setDefaultSystemPrompt', [3], {
    url: '/lawyers/systemPrompt/setDefault/3',
    method: 'put'
  }],
  ['previewSystemPrompt', [3, { question: '你好' }], {
    url: '/lawyers/systemPrompt/preview/3',
    method: 'post',
    data: { question: '你好' }
  }]
]

describe('systemPrompt 接口契约', () => {
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
