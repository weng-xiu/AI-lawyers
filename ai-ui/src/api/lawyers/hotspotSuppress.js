import request from '@/utils/request'

// 查询高频置底规则列表
export function listHotspot(query) {
  return request({
    url: '/lawyers/hotspot/list',
    method: 'get',
    params: query
  })
}

// 查询高频置底规则详情
export function getHotspot(suppressId) {
  return request({
    url: '/lawyers/hotspot/' + suppressId,
    method: 'get'
  })
}

// 新增高频置底规则
export function addHotspot(data) {
  return request({
    url: '/lawyers/hotspot',
    method: 'post',
    data: data
  })
}

// 修改高频置底规则
export function updateHotspot(data) {
  return request({
    url: '/lawyers/hotspot',
    method: 'put',
    data: data
  })
}

// 删除高频置底规则
export function delHotspot(suppressIds) {
  return request({
    url: '/lawyers/hotspot/' + suppressIds,
    method: 'delete'
  })
}

// 查询高频置底命中日志列表
export function listHotspotLog(query) {
  return request({
    url: '/lawyers/hotspot/log/list',
    method: 'get',
    params: query
  })
}
