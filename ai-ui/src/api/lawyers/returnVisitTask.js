import request from '@/utils/request'

// 查询回访任务列表
export function listReturnVisitTask(query) {
  return request({
    url: '/lawyers/returnVisitTask/list',
    method: 'get',
    params: query
  })
}

// 查询回访任务详细
export function getReturnVisitTask(taskId) {
  return request({
    url: '/lawyers/returnVisitTask/' + taskId,
    method: 'get'
  })
}

// 新增回访任务
export function addReturnVisitTask(data) {
  return request({
    url: '/lawyers/returnVisitTask',
    method: 'post',
    data: data
  })
}

// 修改回访任务
export function updateReturnVisitTask(data) {
  return request({
    url: '/lawyers/returnVisitTask',
    method: 'put',
    data: data
  })
}

// 删除回访任务
export function delReturnVisitTask(taskIds) {
  return request({
    url: '/lawyers/returnVisitTask/' + taskIds,
    method: 'delete'
  })
}

// 回访任务统计
export function getReturnVisitTaskStats() {
  return request({
    url: '/lawyers/returnVisitTask/stats',
    method: 'get'
  })
}
