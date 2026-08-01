import request from '@/utils/request'

export function listExecutionLog(query) {
  return request({
    url: '/lawyers/ivr/executionLog/list',
    method: 'get',
    params: query
  })
}

export function getExecutionLog(execId) {
  return request({
    url: '/lawyers/ivr/executionLog/' + execId,
    method: 'get'
  })
}

export function getExecutionLogBySessionId(sessionId) {
  return request({
    url: '/lawyers/ivr/executionLog/session/' + sessionId,
    method: 'get'
  })
}

export function addExecutionLog(data) {
  return request({
    url: '/lawyers/ivr/executionLog',
    method: 'post',
    data: data
  })
}

export function updateExecutionLog(data) {
  return request({
    url: '/lawyers/ivr/executionLog',
    method: 'put',
    data: data
  })
}
