import request from '@/utils/request'

// 查询协同机构台账列表
export function listExternalOrg(query) {
  return request({
    url: '/lawyers/external/org/list',
    method: 'get',
    params: query
  })
}

// 查询协同机构详情
export function getExternalOrg(orgId) {
  return request({
    url: '/lawyers/external/org/' + orgId,
    method: 'get'
  })
}

// 新增协同机构
export function addExternalOrg(data) {
  return request({
    url: '/lawyers/external/org',
    method: 'post',
    data: data
  })
}

// 修改协同机构
export function updateExternalOrg(data) {
  return request({
    url: '/lawyers/external/org',
    method: 'put',
    data: data
  })
}

// 删除协同机构
export function delExternalOrg(orgIds) {
  return request({
    url: '/lawyers/external/org/' + orgIds,
    method: 'delete'
  })
}
