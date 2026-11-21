'use strict'

/**
 * 知识库审核发布 API 单测：锁定审核流水与提交/通过/驳回/发布/下线契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/knowledgeAudit'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listAuditLog', [{ pageNum: 1 }], {
    url: '/lawyers/knowledge/audit/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['submitAudit', [15], {
    url: '/lawyers/knowledge/audit/submit/15',
    method: 'post'
  }],
  ['approveKnowledge', [15, '内容合规'], {
    url: '/lawyers/knowledge/audit/approve/15',
    method: 'post',
    data: { opinion: '内容合规' }
  }],
  ['rejectKnowledge', [15, '引用失效'], {
    url: '/lawyers/knowledge/audit/reject/15',
    method: 'post',
    data: { opinion: '引用失效' }
  }],
  ['publishKnowledge', [15, '发布'], {
    url: '/lawyers/knowledge/audit/publish/15',
    method: 'post',
    data: { opinion: '发布' }
  }],
  ['offlineKnowledge', [15, '过期'], {
    url: '/lawyers/knowledge/audit/offline/15',
    method: 'post',
    data: { opinion: '过期' }
  }]
]

describe('knowledgeAudit 接口契约', () => {
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
