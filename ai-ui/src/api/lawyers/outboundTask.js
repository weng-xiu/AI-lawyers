import request from '@/utils/request'

export function listTask(query) {
  return request({
    url: '/lawyers/outbound/task/list',
    method: 'get',
    params: query
  })
}

export function getTask(taskId) {
  return request({
    url: '/lawyers/outbound/task/' + taskId,
    method: 'get'
  })
}

export function addTask(data) {
  return request({
    url: '/lawyers/outbound/task',
    method: 'post',
    data: data
  })
}

export function updateTask(data) {
  return request({
    url: '/lawyers/outbound/task',
    method: 'put',
    data: data
  })
}

export function delTask(taskIds) {
  return request({
    url: '/lawyers/outbound/task/' + taskIds,
    method: 'delete'
  })
}

export function generateTaskNo() {
  return request({
    url: '/lawyers/outbound/task/generateNo',
    method: 'get'
  })
}

export function startTask(taskId) {
  return request({
    url: '/lawyers/outbound/task/start/' + taskId,
    method: 'post'
  })
}

export function pauseTask(taskId) {
  return request({
    url: '/lawyers/outbound/task/pause/' + taskId,
    method: 'post'
  })
}

export function stopTask(taskId) {
  return request({
    url: '/lawyers/outbound/task/stop/' + taskId,
    method: 'post'
  })
}
