import request from '@/utils/request'

// 查询 SLA 策略列表
export function listSlaPolicy(query) {
  return request({
    url: '/lawyers/sla/policy/list',
    method: 'get',
    params: query
  })
}

// 查询 SLA 策略详情
export function getSlaPolicy(policyId) {
  return request({
    url: '/lawyers/sla/policy/' + policyId,
    method: 'get'
  })
}

// 新增 SLA 策略
export function addSlaPolicy(data) {
  return request({
    url: '/lawyers/sla/policy',
    method: 'post',
    data: data
  })
}

// 修改 SLA 策略
export function updateSlaPolicy(data) {
  return request({
    url: '/lawyers/sla/policy',
    method: 'put',
    data: data
  })
}

// 删除 SLA 策略
export function delSlaPolicy(policyIds) {
  return request({
    url: '/lawyers/sla/policy/' + policyIds,
    method: 'delete'
  })
}
