import request from '@/utils/request'

// 查询系统提示词配置列表
export function listSystemPrompt(query) {
  return request({
    url: '/lawyers/systemPrompt/list',
    method: 'get',
    params: query
  })
}

// 查询系统提示词配置详细
export function getSystemPrompt(promptId) {
  return request({
    url: '/lawyers/systemPrompt/' + promptId,
    method: 'get'
  })
}

// 新增系统提示词配置
export function addSystemPrompt(data) {
  return request({
    url: '/lawyers/systemPrompt',
    method: 'post',
    data: data
  })
}

// 修改系统提示词配置
export function updateSystemPrompt(data) {
  return request({
    url: '/lawyers/systemPrompt',
    method: 'put',
    data: data
  })
}

// 删除系统提示词配置
export function delSystemPrompt(promptId) {
  return request({
    url: '/lawyers/systemPrompt/' + promptId,
    method: 'delete'
  })
}

// 获取默认系统提示词配置
export function getDefaultSystemPrompt(promptType) {
  return request({
    url: '/lawyers/systemPrompt/default/' + promptType,
    method: 'get'
  })
}

// 设置默认系统提示词配置
export function setDefaultSystemPrompt(promptId) {
  return request({
    url: '/lawyers/systemPrompt/setDefault/' + promptId,
    method: 'put'
  })
}

// 预览系统提示词效果
export function previewSystemPrompt(promptId, params) {
  return request({
    url: '/lawyers/systemPrompt/preview/' + promptId,
    method: 'post',
    data: params
  })
}