import request from '@/utils/request'

// 查询客户回访列表
export function listCallback(query) {
  return request({
    url: '/lawyers/callback/list',
    method: 'get',
    params: query
  })
}

// 查询客户回访详细
export function getCallback(callbackId) {
  return request({
    url: '/lawyers/callback/' + callbackId,
    method: 'get'
  })
}

// 新增客户回访
export function addCallback(data) {
  return request({
    url: '/lawyers/callback',
    method: 'post',
    data: data
  })
}

// 修改客户回访
export function updateCallback(data) {
  return request({
    url: '/lawyers/callback',
    method: 'put',
    data: data
  })
}

// 删除客户回访
export function delCallback(callbackIds) {
  return request({
    url: '/lawyers/callback/' + callbackIds,
    method: 'delete'
  })
}

// 回访统计
export function getCallbackStats() {
  return request({
    url: '/lawyers/callback/stats',
    method: 'get'
  })
}

// 满意度趋势
export function getSatisfactionTrend(days) {
  return request({
    url: '/lawyers/callback/satisfactionTrend',
    method: 'get',
    params: { days }
  })
}
