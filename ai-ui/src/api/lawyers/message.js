import request from '@/utils/request'

// 查询站内消息列表（仅本人）
export function listMessage(query) {
  return request({
    url: '/lawyers/message/list',
    method: 'get',
    params: query
  })
}

// 未读消息数（铃铛角标）
export function unreadCount() {
  return request({
    url: '/lawyers/message/unreadCount',
    method: 'get'
  })
}

// 查询消息详情（同时标记为已读）
export function getMessage(messageId) {
  return request({
    url: '/lawyers/message/' + messageId,
    method: 'get'
  })
}

// 标记单条为已读
export function readMessage(messageId) {
  return request({
    url: '/lawyers/message/read/' + messageId,
    method: 'put'
  })
}

// 全部标记为已读
export function readAllMessage() {
  return request({
    url: '/lawyers/message/readAll',
    method: 'put'
  })
}

// 删除消息（仅本人）
export function delMessage(messageIds) {
  return request({
    url: '/lawyers/message/' + messageIds,
    method: 'delete'
  })
}
