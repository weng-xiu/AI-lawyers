import request from '@/utils/request'

// ========== 智能质检 API（T4-1） ==========

// 查询质检记录列表
export function listQuality(query) {
  return request({
    url: '/lawyers/quality/list',
    method: 'get',
    params: query
  })
}

// 查询质检记录详细（含转写文本/维度评分/违禁明细）
export function getQuality(inspectionId) {
  return request({
    url: '/lawyers/quality/' + inspectionId,
    method: 'get'
  })
}

// 人工复核（通过/驳回整改 + 复核评语）
export function reviewQuality(data) {
  return request({
    url: '/lawyers/quality/review',
    method: 'put',
    data: data
  })
}

// 手动触发/重检指定话单
export function inspectRecord(recordId) {
  return request({
    url: '/lawyers/quality/inspect/' + recordId,
    method: 'post'
  })
}

// 导出质检结果
export function exportQuality(query) {
  return request({
    url: '/lawyers/quality/export',
    method: 'post',
    params: query
  })
}
