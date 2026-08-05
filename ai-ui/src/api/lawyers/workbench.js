import request from '@/utils/request'

// ==================== 工作台聚合 ====================
export function getWorkbenchStats() {
  return request({
    url: '/lawyers/workbench/stats',
    method: 'get'
  })
}

export function getWorkbenchTodos() {
  return request({
    url: '/lawyers/workbench/todos',
    method: 'get'
  })
}

export function getWorkbenchRecentCalls(limit) {
  return request({
    url: '/lawyers/workbench/recentCalls',
    method: 'get',
    params: { limit }
  })
}

export function getWorkbenchNotices(limit) {
  return request({
    url: '/lawyers/workbench/notices',
    method: 'get',
    params: { limit }
  })
}

// ==================== 待办管理 ====================
export function listTodo(query) {
  return request({
    url: '/lawyers/workbench/todo/list',
    method: 'get',
    params: query
  })
}

export function getTodo(todoId) {
  return request({
    url: '/lawyers/workbench/todo/' + todoId,
    method: 'get'
  })
}

export function addTodo(data) {
  return request({
    url: '/lawyers/workbench/todo',
    method: 'post',
    data: data
  })
}

export function updateTodo(data) {
  return request({
    url: '/lawyers/workbench/todo',
    method: 'put',
    data: data
  })
}

export function delTodo(todoIds) {
  return request({
    url: '/lawyers/workbench/todo/' + todoIds,
    method: 'delete'
  })
}

export function processTodo(todoId) {
  return request({
    url: '/lawyers/workbench/todo/process/' + todoId,
    method: 'put'
  })
}

export function deferTodo(todoId) {
  return request({
    url: '/lawyers/workbench/todo/defer/' + todoId,
    method: 'put'
  })
}

export function ignoreTodo(todoId) {
  return request({
    url: '/lawyers/workbench/todo/ignore/' + todoId,
    method: 'put'
  })
}

// ==================== 公告管理 ====================
export function listNotice(query) {
  return request({
    url: '/lawyers/workbench/notice/list',
    method: 'get',
    params: query
  })
}

export function getNotice(noticeId) {
  return request({
    url: '/lawyers/workbench/notice/' + noticeId,
    method: 'get'
  })
}

export function addNotice(data) {
  return request({
    url: '/lawyers/workbench/notice',
    method: 'post',
    data: data
  })
}

export function updateNotice(data) {
  return request({
    url: '/lawyers/workbench/notice',
    method: 'put',
    data: data
  })
}

export function delNotice(noticeIds) {
  return request({
    url: '/lawyers/workbench/notice/' + noticeIds,
    method: 'delete'
  })
}

export function publishNotice(noticeId) {
  return request({
    url: '/lawyers/workbench/notice/publish/' + noticeId,
    method: 'put'
  })
}
