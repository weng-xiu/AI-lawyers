import request from '@/utils/request'

// 队列水位概览
export function listQueue() {
  return request({
    url: '/lawyers/queueMonitor/list',
    method: 'get'
  })
}

// 死信列表（倒序分页）
export function listDeadLetter(query) {
  return request({
    url: '/lawyers/queueMonitor/dead/list',
    method: 'get',
    params: query
  })
}

// 死信重投
export function replayDeadLetter(queue, id) {
  return request({
    url: '/lawyers/queueMonitor/dead/replay',
    method: 'post',
    params: { queue, id }
  })
}

// 死信删除
export function removeDeadLetter(queue, id) {
  return request({
    url: '/lawyers/queueMonitor/dead/' + queue + '/' + id,
    method: 'delete'
  })
}
