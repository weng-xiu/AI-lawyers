import request from '@/utils/request'

export function listIntentionLog(query) {
  return request({
    url: '/lawyers/ivr/intentionLog/list',
    method: 'get',
    params: query
  })
}

export function getIntentionLog(logId) {
  return request({
    url: '/lawyers/ivr/intentionLog/' + logId,
    method: 'get'
  })
}

export function getIntentionLogByRecordId(recordId) {
  return request({
    url: '/lawyers/ivr/intentionLog/record/' + recordId,
    method: 'get'
  })
}

export function addIntentionLog(data) {
  return request({
    url: '/lawyers/ivr/intentionLog',
    method: 'post',
    data: data
  })
}
