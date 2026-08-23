import request from '@/utils/request'

// 查询黑白名单列表
export function listBlacklist(query) {
  return request({
    url: '/lawyers/blacklist/list',
    method: 'get',
    params: query
  })
}

// 查询黑白名单详情
export function getBlacklist(id) {
  return request({
    url: '/lawyers/blacklist/' + id,
    method: 'get'
  })
}

// 新增黑白名单
export function addBlacklist(data) {
  return request({
    url: '/lawyers/blacklist',
    method: 'post',
    data: data
  })
}

// 修改黑白名单
export function updateBlacklist(data) {
  return request({
    url: '/lawyers/blacklist',
    method: 'put',
    data: data
  })
}

// 删除黑白名单
export function delBlacklist(ids) {
  return request({
    url: '/lawyers/blacklist/' + ids,
    method: 'delete'
  })
}

// 号码检查
export function checkPhone(phone) {
  return request({
    url: '/lawyers/blacklist/check/' + phone,
    method: 'get'
  })
}
