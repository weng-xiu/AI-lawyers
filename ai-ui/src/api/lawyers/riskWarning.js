import request from '@/utils/request'

// ========== 风险预警记录 API ==========

// 查询风险预警记录列表
export function listRiskWarning(query) {
  return request({
    url: '/lawyers/riskWarning/list',
    method: 'get',
    params: query
  })
}

// 查询风险预警记录详细
export function getRiskWarning(warningId) {
  return request({
    url: '/lawyers/riskWarning/' + warningId,
    method: 'get'
  })
}

// 新增风险预警记录
export function addRiskWarning(data) {
  return request({
    url: '/lawyers/riskWarning',
    method: 'post',
    data: data
  })
}

// 修改风险预警记录
export function updateRiskWarning(data) {
  return request({
    url: '/lawyers/riskWarning',
    method: 'put',
    data: data
  })
}

// 删除风险预警记录
export function delRiskWarning(warningIds) {
  return request({
    url: '/lawyers/riskWarning/' + warningIds,
    method: 'delete'
  })
}

// 导出风险预警记录
export function exportRiskWarning(query) {
  return request({
    url: '/lawyers/riskWarning/export',
    method: 'post',
    params: query
  })
}

// ========== 风险预警规则 API ==========

// 查询风险预警规则列表
export function listRiskWarningRule(query) {
  return request({
    url: '/lawyers/riskWarning/rule/list',
    method: 'get',
    params: query
  })
}

// 查询风险预警规则详细
export function getRiskWarningRule(ruleId) {
  return request({
    url: '/lawyers/riskWarning/rule/' + ruleId,
    method: 'get'
  })
}

// 新增风险预警规则
export function addRiskWarningRule(data) {
  return request({
    url: '/lawyers/riskWarning/rule',
    method: 'post',
    data: data
  })
}

// 修改风险预警规则
export function updateRiskWarningRule(data) {
  return request({
    url: '/lawyers/riskWarning/rule',
    method: 'put',
    data: data
  })
}

// 删除风险预警规则
export function delRiskWarningRule(ruleIds) {
  return request({
    url: '/lawyers/riskWarning/rule/' + ruleIds,
    method: 'delete'
  })
}

// ========== F3 风险联动转办 ==========

// 查询建议条线下可转办的启用机构（仅公开字段）
export function listTransferOrgs(externalType) {
  return request({
    url: '/lawyers/riskWarning/transfer/orgs',
    method: 'get',
    params: { externalType: externalType || undefined }
  })
}

// 高风险预警一键转办（无工单时后端自动建单）
export function transferRiskWarning(data) {
  return request({
    url: '/lawyers/riskWarning/transfer',
    method: 'post',
    data: data
  })
}
