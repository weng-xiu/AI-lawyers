import request from '@/utils/request'

// ==================== 来电弹屏 ====================
export function getCallerProfile(callerNumber) {
  return request({
    url: '/lawyers/call/popup/profile/' + callerNumber,
    method: 'get'
  })
}

export function getCallerHistory(callerNumber, limit) {
  return request({
    url: '/lawyers/call/popup/history/' + callerNumber,
    method: 'get',
    params: { limit }
  })
}

export function getCallerTickets(callerNumber) {
  return request({
    url: '/lawyers/call/popup/tickets/' + callerNumber,
    method: 'get'
  })
}

export function getCallerTrack(callerNumber) {
  return request({
    url: '/lawyers/call/popup/track/' + callerNumber,
    method: 'get'
  })
}

export function updateCallerProfile(data) {
  return request({
    url: '/lawyers/call/popup/profile',
    method: 'put',
    data: data
  })
}
