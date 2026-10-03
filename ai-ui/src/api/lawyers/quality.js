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

// ========== P1-7：质检申诉 ==========

// 被检坐席本人发起申诉
export function appealQuality(inspectionId, appealReason) {
  return request({
    url: '/lawyers/quality/appeal/' + inspectionId,
    method: 'post',
    params: { appealReason: appealReason }
  })
}

// 班组长复核申诉（2维持 / 3改分）
export function reviewAppeal(data) {
  return request({
    url: '/lawyers/quality/appeal/review',
    method: 'put',
    data: data
  })
}

// ========== P1-7：质检模板 ==========

// 查询质检模板列表
export function listQualityTemplate(query) {
  return request({
    url: '/lawyers/quality/template/list',
    method: 'get',
    params: query
  })
}

// 新增质检模板
export function addQualityTemplate(data) {
  return request({
    url: '/lawyers/quality/template',
    method: 'post',
    data: data
  })
}

// 修改质检模板
export function updateQualityTemplate(data) {
  return request({
    url: '/lawyers/quality/template',
    method: 'put',
    data: data
  })
}

// 设为唯一生效默认模板
export function setDefaultQualityTemplate(templateId) {
  return request({
    url: '/lawyers/quality/template/default/' + templateId,
    method: 'put'
  })
}
