import request from '@/utils/request'

// 查询咨询分类列表
export function listConsultationCategory(query) {
  return request({
    url: '/lawyers/category/list',
    method: 'get',
    params: query
  })
}

// 查询咨询分类树形结构
export function listConsultationCategoryTree() {
  return request({
    url: '/lawyers/category/tree',
    method: 'get'
  })
}

// 查询咨询分类详细
export function getConsultationCategory(categoryId) {
  return request({
    url: '/lawyers/category/' + categoryId,
    method: 'get'
  })
}

// 新增咨询分类
export function addConsultationCategory(data) {
  return request({
    url: '/lawyers/category',
    method: 'post',
    data: data
  })
}

// 修改咨询分类
export function updateConsultationCategory(data) {
  return request({
    url: '/lawyers/category',
    method: 'put',
    data: data
  })
}

// 删除咨询分类
export function delConsultationCategory(categoryId) {
  return request({
    url: '/lawyers/category/' + categoryId,
    method: 'delete'
  })
}

// 导出咨询分类
export function exportConsultationCategory(query) {
  return request({
    url: '/lawyers/category/export',
    method: 'get',
    params: query
  })
}