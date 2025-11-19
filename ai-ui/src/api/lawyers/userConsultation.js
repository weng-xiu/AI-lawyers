import request from '@/utils/request'

// 提交用户咨询
export function submitConsultation(data) {
  return request({
    url: '/lawyers/userConsultation/submit',
    method: 'post',
    data: data
  })
}

// 获取咨询结果
export function getConsultationResult(consultationId) {
  return request({
    url: '/lawyers/userConsultation/result/' + consultationId,
    method: 'get'
  })
}

// 获取用户历史咨询记录
export function getUserHistory(query) {
  return request({
    url: '/lawyers/userConsultation/history',
    method: 'get',
    params: query
  })
}

// 获取咨询详情
export function getConsultationDetail(consultationId) {
  return request({
    url: '/lawyers/userConsultation/detail/' + consultationId,
    method: 'get'
  })
}

// 提交咨询评价
export function submitEvaluation(data) {
  return request({
    url: '/lawyers/userConsultation/evaluation',
    method: 'post',
    data: data
  })
}

// 获取问题分类
export function getQuestionCategories() {
  return request({
    url: '/lawyers/userConsultation/categories',
    method: 'get'
  })
}

// 获取热门问题
export function getHotQuestions() {
  return request({
    url: '/lawyers/userConsultation/hotQuestions',
    method: 'get'
  })
}

// 获取常见问题
export function getFaq() {
  return request({
    url: '/lawyers/userConsultation/faq',
    method: 'get'
  })
}