import request from '@/utils/request'

// 查询渠道身份列表
export function listChannelIdentity(query) {
  return request({
    url: '/lawyers/channel/identity/list',
    method: 'get',
    params: query
  })
}

// 发起渠道绑定
export function requestBind(data) {
  return request({
    url: '/lawyers/channel/identity/requestBind',
    method: 'post',
    data: data
  })
}

// 二次确认绑定
export function confirmBind(id, data) {
  return request({
    url: '/lawyers/channel/identity/confirm/' + id,
    method: 'post',
    data: data || {}
  })
}

// 解绑
export function unbindIdentity(id) {
  return request({
    url: '/lawyers/channel/identity/unbind/' + id,
    method: 'post'
  })
}

// 档案下的有效绑定
export function listBoundByProfile(profileId) {
  return request({
    url: '/lawyers/channel/identity/bound/' + profileId,
    method: 'get'
  })
}
