import request from '@/utils/request'

export function listFlow(query) {
  return request({
    url: '/lawyers/ivr/flow/list',
    method: 'get',
    params: query
  })
}

export function getFlow(flowId) {
  return request({
    url: '/lawyers/ivr/flow/' + flowId,
    method: 'get'
  })
}

export function addFlow(data) {
  return request({
    url: '/lawyers/ivr/flow',
    method: 'post',
    data: data
  })
}

export function updateFlow(data) {
  return request({
    url: '/lawyers/ivr/flow',
    method: 'put',
    data: data
  })
}

export function delFlow(flowIds) {
  return request({
    url: '/lawyers/ivr/flow/' + flowIds,
    method: 'delete'
  })
}

export function publishFlow(flowId) {
  return request({
    url: '/lawyers/ivr/flow/publish/' + flowId,
    method: 'post'
  })
}

export function saveDesign(data) {
  return request({
    url: '/lawyers/ivr/flow/design',
    method: 'post',
    data: data
  })
}

export function getPublishedFlows() {
  return request({
    url: '/lawyers/ivr/flow/published',
    method: 'get'
  })
}

export function getDefaultFlow() {
  return request({
    url: '/lawyers/ivr/flow/default',
    method: 'get'
  })
}
