import request from '@/utils/request'

// 查询未接来电列表
export function listMissedCall(query) {
  return request({
    url: '/lawyers/call/missed/list',
    method: 'get',
    params: query
  })
}

// 查询未接来电详细
export function getMissedCall(missedCallId) {
  return request({
    url: '/lawyers/call/missed/' + missedCallId,
    method: 'get'
  })
}

// 新增未接来电
export function addMissedCall(data) {
  return request({
    url: '/lawyers/call/missed',
    method: 'post',
    data: data
  })
}

// 修改未接来电
export function updateMissedCall(data) {
  return request({
    url: '/lawyers/call/missed',
    method: 'put',
    data: data
  })
}

// 删除未接来电
export function delMissedCall(missedCallIds) {
  return request({
    url: '/lawyers/call/missed/' + missedCallIds,
    method: 'delete'
  })
}

// 未接来电统计
export function getMissedCallStats() {
  return request({
    url: '/lawyers/call/missed/stats',
    method: 'get'
  })
}

// 回拨标记
export function callbackMissedCall(missedCallId) {
  return request({
    url: '/lawyers/call/missed/callback/' + missedCallId,
    method: 'put'
  })
}
