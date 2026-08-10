import request from '@/utils/request'

// ==================== 图文会话 ====================
export function listChatSession(query) {
  return request({
    url: '/lawyers/chatSession/list',
    method: 'get',
    params: query
  })
}

export function getChatSession(sessionId) {
  return request({
    url: '/lawyers/chatSession/' + sessionId,
    method: 'get'
  })
}

export function addChatSession(data) {
  return request({
    url: '/lawyers/chatSession',
    method: 'post',
    data: data
  })
}

export function getChatSessionStats() {
  return request({
    url: '/lawyers/chatSession/stats',
    method: 'get'
  })
}

// ==================== 聊天消息 ====================
export function listChatMessage(sessionId) {
  return request({
    url: '/lawyers/chatMessage/list/' + sessionId,
    method: 'get'
  })
}

export function sendChatMessage(data) {
  return request({
    url: '/lawyers/chatMessage/send',
    method: 'post',
    data: data
  })
}

export function markRead(sessionId) {
  return request({
    url: '/lawyers/chatMessage/read/' + sessionId,
    method: 'put'
  })
}

export function closeChatSession(sessionId) {
  return request({
    url: '/lawyers/chatMessage/close/' + sessionId,
    method: 'put'
  })
}

export function transferChatSession(data) {
  return request({
    url: '/lawyers/chatMessage/transfer',
    method: 'put',
    data: data
  })
}
