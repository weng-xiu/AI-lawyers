import request from '@/utils/request'

// 查询法律知识库列表
export function listKnowledge(query) {
  return request({
    url: '/lawyers/knowledge/list',
    method: 'get',
    params: query
  })
}

// 查询法律知识库详细
export function getKnowledge(knowledgeId) {
  return request({
    url: '/lawyers/knowledge/' + knowledgeId,
    method: 'get'
  })
}

// 根据分类ID查询法律知识库列表
export function getKnowledgeByCategory(categoryId) {
  return request({
    url: '/lawyers/knowledge/category/' + categoryId,
    method: 'get'
  })
}

// 根据关键词搜索法律知识库
export function searchKnowledge(keyword) {
  return request({
    // 关键词可能含中文/空格，统一编码，避免路径非法或截断
    url: '/lawyers/knowledge/search/' + encodeURIComponent(keyword || ''),
    method: 'get'
  })
}

// 新增法律知识库
export function addKnowledge(data) {
  return request({
    url: '/lawyers/knowledge',
    method: 'post',
    data: data
  })
}

// 修改法律知识库
export function updateKnowledge(data) {
  return request({
    url: '/lawyers/knowledge',
    method: 'put',
    data: data
  })
}

// 删除法律知识库
export function delKnowledge(knowledgeId) {
  return request({
    url: '/lawyers/knowledge/' + knowledgeId,
    method: 'delete'
  })
}

// 导出法律知识库
export function exportKnowledge(query) {
  return request({
    url: '/lawyers/knowledge/export',
    method: 'post',
    params: query
  })
}

// 查询法律知识库导入模板
export function importTemplate() {
  return request({
    url: '/lawyers/knowledge/importTemplate',
    method: 'get'
  })
}

// 批量导入法律知识库
export function importKnowledge(data) {
  return request({
    url: '/lawyers/knowledge/importData',
    method: 'post',
    data: data
  })
}

// 审核法律知识库
export function auditKnowledge(data) {
  return request({
    url: '/lawyers/knowledge/audit',
    method: 'put',
    data: data
  })
}

// 标记知识效力（1现行有效 2已修订 3已失效）
export function markValidStatus(knowledgeId, validStatus, reason) {
  return request({
    url: '/lawyers/knowledge/valid-status',
    method: 'put',
    params: { knowledgeId, validStatus, reason }
  })
}

// AI 沉淀：从通话记录生成知识草稿
export function draftFromCall(recordId) {
  return request({
    url: '/lawyers/knowledge/draft/call/' + recordId,
    method: 'post'
  })
}

// AI 沉淀：从工单生成知识草稿
export function draftFromTicket(ticketId) {
  return request({
    url: '/lawyers/knowledge/draft/ticket/' + ticketId,
    method: 'post'
  })
}

// AI 沉淀：扫描高分通话批量生成知识草稿，返回新生成条数
export function scanDraft(query) {
  return request({
    url: '/lawyers/knowledge/draft/scan',
    method: 'post',
    params: query
  })
}

// RAG 索引状态（向量条目数/索引后端/rerank 开关）
export function getRagIndexInfo() {
  return request({
    url: '/lawyers/knowledge/rag/indexInfo',
    method: 'get'
  })
}

// RAG 全量重建索引（异步）
export function rebuildRagIndex() {
  return request({
    url: '/lawyers/knowledge/rag/rebuild',
    method: 'post'
  })
}

// RAG rerank 服务连通性测试（固定样例）
export function testRagRerank() {
  return request({
    url: '/lawyers/knowledge/rag/rerankTest',
    method: 'post'
  })
}