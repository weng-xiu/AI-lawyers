import request from '@/utils/request'

// ==================== AI律师辅助会话（独立链路，与人工接听 callCenter.js 完全分离） ====================

// 查询 AI 辅助会话列表
export function listAiAssist(query) {
  return request({
    url: '/lawyers/ai/assist/list',
    method: 'get',
    params: query
  })
}

// 按会话ID查询
export function getAiAssist(sessionId) {
  return request({
    url: '/lawyers/ai/assist/' + sessionId,
    method: 'get'
  })
}

// 按人工通话记录ID查询（人工接听后关联的AI辅助会话）
export function getAiAssistByRecord(recordId) {
  return request({
    url: '/lawyers/ai/assist/byRecord',
    method: 'get',
    params: { recordId }
  })
}

// 独立处理函数：触发 AI 分析 + 法条/话术推荐
export function analyzeAiAssist(data) {
  return request({
    url: '/lawyers/ai/assist/analyze',
    method: 'post',
    data: data
  })
}

// 独立处理函数：生成小结并结束 AI 辅助会话
export function summarizeAiAssist(data) {
  return request({
    url: '/lawyers/ai/assist/summarize',
    method: 'post',
    data: data
  })
}

// 删除 AI 辅助会话
export function delAiAssist(sessionIds) {
  return request({
    url: '/lawyers/ai/assist/' + sessionIds,
    method: 'delete'
  })
}
