import request from '@/utils/request'

export function listIntention(query) {
  return request({
    url: '/lawyers/ivr/intention/list',
    method: 'get',
    params: query
  })
}

export function getIntention(intentionId) {
  return request({
    url: '/lawyers/ivr/intention/' + intentionId,
    method: 'get'
  })
}

export function addIntention(data) {
  return request({
    url: '/lawyers/ivr/intention',
    method: 'post',
    data: data
  })
}

export function updateIntention(data) {
  return request({
    url: '/lawyers/ivr/intention',
    method: 'put',
    data: data
  })
}

export function delIntention(intentionIds) {
  return request({
    url: '/lawyers/ivr/intention/' + intentionIds,
    method: 'delete'
  })
}

export function getActiveIntentions() {
  return request({
    url: '/lawyers/ivr/intention/active',
    method: 'get'
  })
}

export function matchIntention(inputText) {
  return request({
    url: '/lawyers/ivr/intention/match',
    method: 'post',
    data: { inputText: inputText }
  })
}
