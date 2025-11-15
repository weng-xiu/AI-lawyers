import request from '@/utils/request'

// 查询咨询分类列表
export function listCategory(query) {
  return request({
    url: '/lawyers/category/list',
    method: 'get',
    params: query
  })
}

// 查询咨询分类详细
export function getCategory(categoryId) {
  return request({
    url: '/lawyers/category/' + categoryId,
    method: 'get'
  })
}

// 获取所有有效的咨询分类
export function getValidCategories() {
  return request({
    url: '/lawyers/category/valid',
    method: 'get'
  })
}

// 新增咨询分类
export function addCategory(data) {
  return request({
    url: '/lawyers/category',
    method: 'post',
    data: data
  })
}

// 修改咨询分类
export function updateCategory(data) {
  return request({
    url: '/lawyers/category',
    method: 'put',
    data: data
  })
}

// 删除咨询分类
export function delCategory(categoryId) {
  return request({
    url: '/lawyers/category/' + categoryId,
    method: 'delete'
  })
}

// 导出咨询分类
export function exportCategory(query) {
  return request({
    url: '/lawyers/category/export',
    method: 'post',
    params: query
  })
}