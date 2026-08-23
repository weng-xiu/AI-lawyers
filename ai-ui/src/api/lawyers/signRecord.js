import request from '@/utils/request'

// 查询签署记录列表
export function listSignRecord(query) {
  return request({
    url: '/lawyers/signRecord/list',
    method: 'get',
    params: query
  })
}

// 查询签署记录详细
export function getSignRecord(signId) {
  return request({
    url: '/lawyers/signRecord/' + signId,
    method: 'get'
  })
}

// 新增签署记录
export function addSignRecord(data) {
  return request({
    url: '/lawyers/signRecord',
    method: 'post',
    data: data
  })
}

// 修改签署记录
export function updateSignRecord(data) {
  return request({
    url: '/lawyers/signRecord',
    method: 'put',
    data: data
  })
}

// 删除签署记录
export function delSignRecord(signIds) {
  return request({
    url: '/lawyers/signRecord/' + signIds,
    method: 'delete'
  })
}

// 导出签署记录
export function exportSignRecord(query) {
  return request({
    url: '/lawyers/signRecord/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  })
}
