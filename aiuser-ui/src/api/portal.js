import request from '@/utils/http'

// ========== F8 我的工单 ==========

// 我的工单（登录态，后端强制按本人手机号过滤；仅允许 status 透传）
export function myTickets(params) {
  return request({
    url: '/lawyers/portal/ticket/list',
    method: 'get',
    params
  })
}

// ========== F8 服务导航 ==========

// 服务机构目录（匿名只读，已脱敏，无对接密钥）
export function serviceOrgs(params) {
  return request({
    url: '/lawyers/portal/services/orgs',
    method: 'get',
    params
  })
}

// ========== F2 适老/语种偏好 ==========

export function savePreference(data) {
  return request({
    url: '/lawyers/portal/preference',
    method: 'post',
    data
  })
}

// ========== F6 渠道身份绑定（二期入口） ==========

export function myChannels() {
  return request({ url: '/lawyers/portal/channel/list', method: 'get' })
}

export function requestChannelBind(data) {
  return request({ url: '/lawyers/portal/channel/requestBind', method: 'post', data })
}

export function confirmChannelBind(data) {
  return request({ url: '/lawyers/portal/channel/confirm', method: 'post', data })
}

export function unbindChannel(data) {
  return request({ url: '/lawyers/portal/channel/unbind', method: 'post', data })
}
