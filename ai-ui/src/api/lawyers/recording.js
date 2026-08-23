import request from '@/utils/request'

// 查询录音列表（复用通话记录列表，仅展示有录音的记录）
export function listRecord(query) {
  return request({
    url: '/lawyers/call/record/list',
    method: 'get',
    params: query
  })
}

// 查询通话录音详情
export function getRecord(recordId) {
  return request({
    url: '/lawyers/call/record/' + recordId,
    method: 'get'
  })
}

// 录音在线播放完整路径
export function playUrl(recordId) {
  return process.env.VUE_APP_BASE_API + '/lawyers/call/record/' + recordId + '/play'
}

// 录音下载完整路径
export function downloadUrl(recordId) {
  return process.env.VUE_APP_BASE_API + '/lawyers/call/record/' + recordId + '/download'
}

// 通过 blob 方式请求带 token 的录音文件
export function fetchAudioBlob(recordId) {
  return request({
    url: '/lawyers/call/record/' + recordId + '/play',
    method: 'get',
    responseType: 'blob'
  })
}
