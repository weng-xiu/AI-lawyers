import request from '@/utils/request'

// 呼叫报表
export function getCallReport(query) {
  return request({
    url: '/lawyers/report/call',
    method: 'get',
    params: query
  })
}

// 坐席服务报表
export function getServiceReport(query) {
  return request({
    url: '/lawyers/report/service',
    method: 'get',
    params: query
  })
}

// 质检报表
export function getQualityReport(query) {
  return request({
    url: '/lawyers/report/quality',
    method: 'get',
    params: query
  })
}

// 业务工单报表
export function getBusinessReport(query) {
  return request({
    url: '/lawyers/report/business',
    method: 'get',
    params: query
  })
}
