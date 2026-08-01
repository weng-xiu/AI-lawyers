import request from '@/utils/request'

export function listResult(query) {
  return request({
    url: '/lawyers/outbound/result/list',
    method: 'get',
    params: query
  })
}

export function getResult(resultId) {
  return request({
    url: '/lawyers/outbound/result/' + resultId,
    method: 'get'
  })
}

export function getResultsByTaskId(taskId) {
  return request({
    url: '/lawyers/outbound/result/task/' + taskId,
    method: 'get'
  })
}

export function getResultByCalleeId(calleeId) {
  return request({
    url: '/lawyers/outbound/result/callee/' + calleeId,
    method: 'get'
  })
}

export function getTaskResultStatistics(taskId) {
  return request({
    url: '/lawyers/outbound/result/statistics/' + taskId,
    method: 'get'
  })
}

export function addResult(data) {
  return request({
    url: '/lawyers/outbound/result',
    method: 'post',
    data: data
  })
}
