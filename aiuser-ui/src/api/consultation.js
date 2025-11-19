import http from '@/utils/http'

// 提交法律咨询
export function submitConsultation(data) {
  return http({
    url: '/aiuser/consultation/submit',
    method: 'post',
    data: data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 获取咨询结果
export function getConsultationResult(id) {
  return http({
    url: `/aiuser/consultation/result/${id}`,
    method: 'get'
  })
}

// 获取咨询信息
export function getConsultationInfo(id) {
  return http({
    url: `/aiuser/consultation/info/${id}`,
    method: 'get'
  })
}

// 获取咨询历史记录
export function getConsultationHistory(params) {
  return http({
    url: '/aiuser/consultation/history',
    method: 'get',
    params: params
  })
}

// 提交咨询评价
export function submitEvaluation(data) {
  return http({
    url: '/aiuser/consultation/evaluation',
    method: 'post',
    data: data
  })
}

// 获取问题分类
export function getQuestionCategories() {
  return http({
    url: '/aiuser/consultation/categories',
    method: 'get'
  })
}

// 删除咨询记录
export function deleteConsultation(id) {
  return http({
    url: `/aiuser/consultation/${id}`,
    method: 'delete'
  })
}

// 获取用户评价列表
export function getUserEvaluations(params) {
  return http({
    url: '/aiuser/evaluation/list',
    method: 'get',
    params: params
  })
}