import request from '@/utils/request'

export function listCallee(query) {
  return request({
    url: '/lawyers/outbound/callee/list',
    method: 'get',
    params: query
  })
}

export function getCallee(calleeId) {
  return request({
    url: '/lawyers/outbound/callee/' + calleeId,
    method: 'get'
  })
}

export function getCalleesByTaskId(taskId) {
  return request({
    url: '/lawyers/outbound/callee/task/' + taskId,
    method: 'get'
  })
}

export function addCallee(data) {
  return request({
    url: '/lawyers/outbound/callee',
    method: 'post',
    data: data
  })
}

export function updateCallee(data) {
  return request({
    url: '/lawyers/outbound/callee',
    method: 'put',
    data: data
  })
}

export function delCallee(calleeIds) {
  return request({
    url: '/lawyers/outbound/callee/' + calleeIds,
    method: 'delete'
  })
}

export function delCalleeByTaskId(taskId) {
  return request({
    url: '/lawyers/outbound/callee/task/' + taskId,
    method: 'delete'
  })
}

export function batchAddCallee(taskId, callees) {
  return request({
    url: '/lawyers/outbound/callee/batch/' + taskId,
    method: 'post',
    data: callees
  })
}
