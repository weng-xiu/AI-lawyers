import request from '@/utils/request'

// 查询审核发布流水
export function listAuditLog(query) {
  return request({
    url: '/lawyers/knowledge/audit/list',
    method: 'get',
    params: query
  })
}

// 提交审核
export function submitAudit(knowledgeId) {
  return request({
    url: '/lawyers/knowledge/audit/submit/' + knowledgeId,
    method: 'post'
  })
}

// 审核通过
export function approveKnowledge(knowledgeId, opinion) {
  return request({
    url: '/lawyers/knowledge/audit/approve/' + knowledgeId,
    method: 'post',
    data: { opinion: opinion }
  })
}

// 审核驳回
export function rejectKnowledge(knowledgeId, opinion) {
  return request({
    url: '/lawyers/knowledge/audit/reject/' + knowledgeId,
    method: 'post',
    data: { opinion: opinion }
  })
}

// 发布上架
export function publishKnowledge(knowledgeId, opinion) {
  return request({
    url: '/lawyers/knowledge/audit/publish/' + knowledgeId,
    method: 'post',
    data: { opinion: opinion }
  })
}

// 下线
export function offlineKnowledge(knowledgeId, opinion) {
  return request({
    url: '/lawyers/knowledge/audit/offline/' + knowledgeId,
    method: 'post',
    data: { opinion: opinion }
  })
}
