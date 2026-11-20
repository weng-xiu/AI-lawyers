'use strict'

/**
 * 智能质检 API 单测：锁定质检记录、申诉复核与质检模板接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/quality'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listQuality', [{ pageNum: 1 }], {
    url: '/lawyers/quality/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getQuality', [9], { url: '/lawyers/quality/9', method: 'get' }],
  ['reviewQuality', [{ inspectionId: 9 }], {
    url: '/lawyers/quality/review',
    method: 'put',
    data: { inspectionId: 9 }
  }],
  ['inspectRecord', [100], { url: '/lawyers/quality/inspect/100', method: 'post' }],
  ['exportQuality', [{ score: 80 }], {
    url: '/lawyers/quality/export',
    method: 'post',
    params: { score: 80 }
  }],
  ['appealQuality', [9, '评分有误'], {
    url: '/lawyers/quality/appeal/9',
    method: 'post',
    params: { appealReason: '评分有误' }
  }],
  ['reviewAppeal', [{ appealId: 1 }], {
    url: '/lawyers/quality/appeal/review',
    method: 'put',
    data: { appealId: 1 }
  }],
  ['listQualityTemplate', [{ pageNum: 1 }], {
    url: '/lawyers/quality/template/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['addQualityTemplate', [{ templateName: '默认' }], {
    url: '/lawyers/quality/template',
    method: 'post',
    data: { templateName: '默认' }
  }],
  ['updateQualityTemplate', [{ templateId: 2 }], {
    url: '/lawyers/quality/template',
    method: 'put',
    data: { templateId: 2 }
  }],
  ['setDefaultQualityTemplate', [2], {
    url: '/lawyers/quality/template/default/2',
    method: 'put'
  }]
]

describe('quality 接口契约', () => {
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
