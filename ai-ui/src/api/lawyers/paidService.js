import request from '@/utils/request'

// 查询有偿法律服务列表
export function listPaidService(query) {
  return request({
    url: '/lawyers/paidLegalService/list',
    method: 'get',
    params: query
  })
}

// 查询有偿法律服务详细
export function getPaidService(paidId) {
  return request({
    url: '/lawyers/paidLegalService/' + paidId,
    method: 'get'
  })
}

// 新增有偿法律服务
export function addPaidService(data) {
  return request({
    url: '/lawyers/paidLegalService',
    method: 'post',
    data: data
  })
}

// 修改有偿法律服务
export function updatePaidService(data) {
  return request({
    url: '/lawyers/paidLegalService',
    method: 'put',
    data: data
  })
}

// 删除有偿法律服务
export function delPaidService(paidId) {
  return request({
    url: '/lawyers/paidLegalService/' + paidId,
    method: 'delete'
  })
}

// 导出有偿法律服务
export function exportPaidService(query) {
  return request({
    url: '/lawyers/paidLegalService/export',
    method: 'post',
    params: query
  })
}
