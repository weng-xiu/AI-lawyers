import request from '@/utils/request'

// ==================== P3-F4 坐席 Copilot 实时辅助（法条溯源/采纳埋点/采纳率） ====================

// 法条溯源详情（标题/条号/出处/原文）
export function getCopilotChunk(chunkId) {
  return request({
    url: '/lawyers/copilot/chunk/' + chunkId,
    method: 'get'
  })
}

// 建议行为埋点（ADOPT/MODIFY/IGNORE；坐席信息服务端补全）
export function copilotFeedback(data) {
  return request({
    url: '/lawyers/copilot/feedback',
    method: 'post',
    data: data
  })
}

// 区间采纳率统计（dimension=total 默认 / agent 按坐席）
export function getCopilotAdoption(query) {
  return request({
    url: '/lawyers/copilot/adoption',
    method: 'get',
    params: query
  })
}

// ==================== P1-8 代执行白名单 ====================

// 确认建单（Copilot 草稿经坐席修改后提交，白名单字段 title/content/priority/recordId）
export function createCopilotTicket(data) {
  return request({
    url: '/lawyers/copilot/actions/createTicket',
    method: 'post',
    data: data
  })
}

// 查询工单办理进度（返回脱敏进度信息 + 口语化播报话术）
export function queryCopilotTicket(ticketId) {
  return request({
    url: '/lawyers/copilot/actions/queryTicket/' + ticketId,
    method: 'get'
  })
}
