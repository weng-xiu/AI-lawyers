import request from '@/utils/request'

export function listNode(query) {
  return request({
    url: '/lawyers/ivr/node/list',
    method: 'get',
    params: query
  })
}

export function getNode(nodeId) {
  return request({
    url: '/lawyers/ivr/node/' + nodeId,
    method: 'get'
  })
}

export function getNodesByFlowId(flowId) {
  return request({
    url: '/lawyers/ivr/node/flow/' + flowId,
    method: 'get'
  })
}

export function addNode(data) {
  return request({
    url: '/lawyers/ivr/node',
    method: 'post',
    data: data
  })
}

export function updateNode(data) {
  return request({
    url: '/lawyers/ivr/node',
    method: 'put',
    data: data
  })
}

export function delNode(nodeIds) {
  return request({
    url: '/lawyers/ivr/node/' + nodeIds,
    method: 'delete'
  })
}

export function delNodeByFlowId(flowId) {
  return request({
    url: '/lawyers/ivr/node/flow/' + flowId,
    method: 'delete'
  })
}

export function batchSaveNode(flowId, nodes) {
  return request({
    url: '/lawyers/ivr/node/batch/' + flowId,
    method: 'post',
    data: nodes
  })
}
