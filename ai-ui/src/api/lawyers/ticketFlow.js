import request from '@/utils/request'

// 查询流程规则列表（后端返回全量 List，不分页）
export function listTicketFlow(query) {
  return request({
    url: '/lawyers/ticket-flow/list',
    method: 'get',
    params: query
  })
}

// 查询单条流程规则
export function getTicketFlow(flowId) {
  return request({
    url: '/lawyers/ticket-flow/' + flowId,
    method: 'get'
  })
}

// F2：发布前强校验（必填/重复动作/不可达/死路/终态出向）
export function validateTicketFlow(flowCode) {
  return request({
    url: '/lawyers/ticket-flow/validate/' + encodeURIComponent(flowCode),
    method: 'get'
  })
}

// F2：查询画布节点布局（未保存过返回空数组，前端回退默认排布）
export function getTicketFlowLayout(flowCode) {
  return request({
    url: '/lawyers/ticket-flow/layout/' + encodeURIComponent(flowCode),
    method: 'get'
  })
}

// F2：保存画布节点布局（按 流程+状态 覆盖写入）
export function saveTicketFlowLayout(flowCode, data) {
  return request({
    url: '/lawyers/ticket-flow/layout/' + encodeURIComponent(flowCode),
    method: 'put',
    data: data
  })
}

// 新增流程规则
export function addTicketFlow(data) {
  return request({
    url: '/lawyers/ticket-flow',
    method: 'post',
    data: data
  })
}

// 修改流程规则
export function updateTicketFlow(data) {
  return request({
    url: '/lawyers/ticket-flow',
    method: 'put',
    data: data
  })
}
