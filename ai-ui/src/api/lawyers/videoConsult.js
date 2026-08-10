import request from '@/utils/request'

export function listVideoConsult(query) {
  return request({ url: '/lawyers/videoConsult/list', method: 'get', params: query })
}

export function getVideoConsult(consultId) {
  return request({ url: '/lawyers/videoConsult/' + consultId, method: 'get' })
}

export function addVideoConsult(data) {
  return request({ url: '/lawyers/videoConsult', method: 'post', data: data })
}

export function updateVideoConsult(data) {
  return request({ url: '/lawyers/videoConsult', method: 'put', data: data })
}

export function delVideoConsult(consultIds) {
  return request({ url: '/lawyers/videoConsult/' + consultIds, method: 'delete' })
}

export function getVideoConsultLogs(consultId) {
  return request({ url: '/lawyers/videoConsult/log/' + consultId, method: 'get' })
}

export function addVideoConsultLog(data) {
  return request({ url: '/lawyers/videoConsult/log', method: 'post', data: data })
}
