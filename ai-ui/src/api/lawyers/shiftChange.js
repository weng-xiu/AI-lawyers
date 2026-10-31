import request from '@/utils/request'

// ==================== 坐席换班申请与审批（F9） ====================

// 换班申请列表（班组长视角，可按状态/类型/申请人过滤）
export function listShiftChange(query) {
  return request({
    url: '/lawyers/shift-change/list',
    method: 'get',
    params: query
  })
}

// 我的换班申请（服务端按当前登录账号绑定坐席过滤）
export function listMyShiftChange(query) {
  return request({
    url: '/lawyers/shift-change/mine',
    method: 'get',
    params: query
  })
}

export function getShiftChange(requestId) {
  return request({
    url: '/lawyers/shift-change/' + requestId,
    method: 'get'
  })
}

// 本人近期排班（申请弹窗选择待变更排班/对方排班）
export function listMySchedules(params) {
  return request({
    url: '/lawyers/shift-change/my-schedules',
    method: 'get',
    params
  })
}

// 互换可选的对方排班（申请人排班同一天、他人名下、状态有效）
export function listSwapTargets(applicantScheduleId) {
  return request({
    url: '/lawyers/shift-change/swap-targets',
    method: 'get',
    params: { applicantScheduleId }
  })
}

// 提交换班申请：applicantAgentId 由服务端按登录账号覆盖，前端无需传
export function applyShiftChange(data) {
  return request({
    url: '/lawyers/shift-change',
    method: 'post',
    data
  })
}

// 审批通过（服务端自动改写排班）
export function approveShiftChange(requestId, approveRemark) {
  return request({
    url: '/lawyers/shift-change/approve/' + requestId,
    method: 'put',
    data: { approveRemark }
  })
}

// 审批驳回
export function rejectShiftChange(requestId, approveRemark) {
  return request({
    url: '/lawyers/shift-change/reject/' + requestId,
    method: 'put',
    data: { approveRemark }
  })
}

// 撤销本人的待审批申请
export function cancelShiftChange(requestId) {
  return request({
    url: '/lawyers/shift-change/cancel/' + requestId,
    method: 'put'
  })
}
