'use strict'

/**
 * 法律知识库 API 单测：锁定 CRUD、导入导出、审核/效力标记、AI 沉淀与 RAG 运维接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/knowledge'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listKnowledge', [{ pageNum: 1 }], { url: '/lawyers/knowledge/list', method: 'get', params: { pageNum: 1 } }],
  ['getKnowledge', [3], { url: '/lawyers/knowledge/3', method: 'get' }],
  ['getKnowledgeByCategory', [2], { url: '/lawyers/knowledge/category/2', method: 'get' }],
  ['searchKnowledge', ['劳动合同 纠纷'], {
    url: '/lawyers/knowledge/search/%E5%8A%B3%E5%8A%A8%E5%90%88%E5%90%8C%20%E7%BA%A0%E7%BA%B7',
    method: 'get'
  }],
  ['searchKnowledge', [null], { url: '/lawyers/knowledge/search/', method: 'get' }],
  ['addKnowledge', [{ knowledgeId: 1 }], { url: '/lawyers/knowledge', method: 'post', data: { knowledgeId: 1 } }],
  ['updateKnowledge', [{ knowledgeId: 1 }], { url: '/lawyers/knowledge', method: 'put', data: { knowledgeId: 1 } }],
  ['delKnowledge', [3], { url: '/lawyers/knowledge/3', method: 'delete' }],
  ['exportKnowledge', [{ categoryId: 2 }], { url: '/lawyers/knowledge/export', method: 'post', params: { categoryId: 2 } }],
  ['importTemplate', [], { url: '/lawyers/knowledge/importTemplate', method: 'get' }],
  ['importKnowledge', [{ file: 'f' }], { url: '/lawyers/knowledge/importData', method: 'post', data: { file: 'f' } }],
  ['auditKnowledge', [{ knowledgeId: 1 }], { url: '/lawyers/knowledge/audit', method: 'put', data: { knowledgeId: 1 } }],
  ['markValidStatus', [3, 2, '已修订'], {
    url: '/lawyers/knowledge/valid-status',
    method: 'put',
    params: { knowledgeId: 3, validStatus: 2, reason: '已修订' }
  }],
  ['draftFromCall', [9], { url: '/lawyers/knowledge/draft/call/9', method: 'post' }],
  ['draftFromTicket', [9], { url: '/lawyers/knowledge/draft/ticket/9', method: 'post' }],
  ['scanDraft', [{ minScore: 80 }], { url: '/lawyers/knowledge/draft/scan', method: 'post', params: { minScore: 80 } }],
  ['getRagIndexInfo', [], { url: '/lawyers/knowledge/rag/indexInfo', method: 'get' }],
  ['rebuildRagIndex', [], { url: '/lawyers/knowledge/rag/rebuild', method: 'post' }],
  ['testRagRerank', [], { url: '/lawyers/knowledge/rag/rerankTest', method: 'post' }],
  ['getKnowledgeOpsOverview', [{ beginDate: '2024-01-01' }], {
    url: '/lawyers/knowledge/ops/overview',
    method: 'get',
    params: { beginDate: '2024-01-01' }
  }],
  ['listUnmatchedQuestions', [{ pageNum: 1 }], { url: '/lawyers/knowledge/ops/unmatched', method: 'get', params: { pageNum: 1 } }],
  ['listTopKnowledge', [{ limit: 10 }], { url: '/lawyers/knowledge/ops/topKnowledge', method: 'get', params: { limit: 10 } }],
  ['draftFromQuestion', [{ question: '?' }], { url: '/lawyers/knowledge/ops/draft', method: 'post', data: { question: '?' } }]
]

describe('knowledge 接口契约', () => {
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
