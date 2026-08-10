import request from '@/utils/request'

// 查询转接记录列表
export function listTransfer(query) {
  return request({
    url: '/lawyers/call/transfer/list',
    method: 'get',
    params: query
  })
}

// 查询转接记录详细
export function getTransfer(transferId) {
  return request({
    url: '/lawyers/call/transfer/' + transferId,
    method: 'get'
  })
}

// 新增转接记录
export function addTransfer(data) {
  return request({
    url: '/lawyers/call/transfer',
    method: 'post',
    data: data
  })
}

// 删除转接记录
export function delTransfer(transferIds) {
  return request({
    url: '/lawyers/call/transfer/' + transferIds,
    method: 'delete'
  })
}

// 按通话记录查询转接记录
export function getTransfersByRecordId(recordId) {
  return request({
    url: '/lawyers/call/transfer/record/' + recordId,
    method: 'get'
  })
}

// 导出转接记录
export function exportTransfer(query) {
  return request({
    url: '/lawyers/call/transfer/export',
    method: 'post',
    params: query
  })
}
