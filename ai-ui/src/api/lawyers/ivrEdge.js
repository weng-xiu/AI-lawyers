import request from '@/utils/request'

export function listEdge(query) {
  return request({
    url: '/lawyers/ivr/edge/list',
    method: 'get',
    params: query
  })
}

export function getEdge(edgeId) {
  return request({
    url: '/lawyers/ivr/edge/' + edgeId,
    method: 'get'
  })
}

export function getEdgesByFlowId(flowId) {
  return request({
    url: '/lawyers/ivr/edge/flow/' + flowId,
    method: 'get'
  })
}

export function addEdge(data) {
  return request({
    url: '/lawyers/ivr/edge',
    method: 'post',
    data: data
  })
}

export function updateEdge(data) {
  return request({
    url: '/lawyers/ivr/edge',
    method: 'put',
    data: data
  })
}

export function delEdge(edgeIds) {
  return request({
    url: '/lawyers/ivr/edge/' + edgeIds,
    method: 'delete'
  })
}

export function delEdgeByFlowId(flowId) {
  return request({
    url: '/lawyers/ivr/edge/flow/' + flowId,
    method: 'delete'
  })
}

export function batchSaveEdge(flowId, edges) {
  return request({
    url: '/lawyers/ivr/edge/batch/' + flowId,
    method: 'post',
    data: edges
  })
}
