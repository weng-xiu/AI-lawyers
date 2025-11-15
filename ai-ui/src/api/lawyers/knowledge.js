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
    url: '/lawyers/knowledge/search/' + keyword,
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