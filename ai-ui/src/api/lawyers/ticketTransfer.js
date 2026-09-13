import request from '@/utils/request'

// 查询转办流水列表
export function listTransfer(query) {
  return request({
    url: '/lawyers/ticket/transfer/list',
    method: 'get',
    params: query
  })
}

// 查询转办流水详情
export function getTransfer(transferId) {
  return request({
    url: '/lawyers/ticket/transfer/' + transferId,
    method: 'get'
  })
}

// 发起转办
export function createTransfer(data) {
  return request({
    url: '/lawyers/ticket/transfer',
    method: 'post',
    data: data
  })
}

// 手动重试推送
export function retryTransfer(transferId) {
  return request({
    url: '/lawyers/ticket/transfer/retry/' + transferId,
    method: 'post'
  })
}

// 跨渠道咨询时间线（F6）
export function callerTimeline(params) {
  return request({
    url: '/lawyers/channel/timeline',
    method: 'get',
    params: params
  })
}
