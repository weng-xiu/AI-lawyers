import request from '@/utils/request'

// 查询法律咨询记录列表
export function listConsultation(query) {
  return request({
    url: '/lawyers/consultation/list',
    method: 'get',
    params: query
  })
}

// 查询法律咨询记录详细
export function getConsultation(consultationId) {
  return request({
    url: '/lawyers/consultation/' + consultationId,
    method: 'get'
  })
}

// 查询用户的历史咨询记录
export function getUserConsultations(userId) {
  return request({
    url: '/lawyers/consultation/user/' + userId,
    method: 'get'
  })
}

// 新增法律咨询记录
export function addConsultation(data) {
  return request({
    url: '/lawyers/consultation',
    method: 'post',
    data: data
  })
}

// 修改法律咨询记录
export function updateConsultation(data) {
  return request({
    url: '/lawyers/consultation',
    method: 'put',
    data: data
  })
}

// 删除法律咨询记录
export function delConsultation(consultationId) {
  return request({
    url: '/lawyers/consultation/' + consultationId,
    method: 'delete'
  })
}

// 导出法律咨询记录
export function exportConsultation(query) {
  return request({
    url: '/lawyers/consultation/export',
    method: 'post',
    params: query
  })
}

// 获取咨询记录统计数据
export function getConsultationStatistics() {
  return request({
    url: '/lawyers/consultation/statistics',
    method: 'get'
  })
}

// 获取按分类统计的咨询数据
export function getConsultationByCategory() {
  return request({
    url: '/lawyers/consultation/statistics/category',
    method: 'get'
  })
}

// 获取按日期统计的咨询数据
export function getConsultationByDate(days) {
  return request({
    url: '/lawyers/consultation/statistics/date',
    method: 'get',
    params: { days }
  })
}