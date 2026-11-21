'use strict'

/**
 * 表单草稿 API 单测：锁定按 formKey 读取、保存与删除契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/formDraft'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getFormDraft', ['ticket:create'], {
    url: '/lawyers/formDraft',
    method: 'get',
    params: { formKey: 'ticket:create' }
  }],
  ['saveFormDraft', ['ticket:create', { title: '草稿' }], {
    url: '/lawyers/formDraft',
    method: 'post',
    data: { formKey: 'ticket:create', content: { title: '草稿' } }
  }],
  ['deleteFormDraft', ['ticket:create'], {
    url: '/lawyers/formDraft',
    method: 'delete',
    params: { formKey: 'ticket:create' }
  }]
]

describe('formDraft 接口契约', () => {
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
