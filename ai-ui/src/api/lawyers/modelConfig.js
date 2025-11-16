import request from '@/utils/request'

// 查询AI模型参数配置列表
export function listModelConfig(query) {
  return request({
    url: '/lawyers/modelConfig/list',
    method: 'get',
    params: query
  })
}

// 查询AI模型参数配置详细
export function getModelConfig(configId) {
  return request({
    url: '/lawyers/modelConfig/' + configId,
    method: 'get'
  })
}

// 新增AI模型参数配置
export function addModelConfig(data) {
  return request({
    url: '/lawyers/modelConfig',
    method: 'post',
    data: data
  })
}

// 修改AI模型参数配置
export function updateModelConfig(data) {
  return request({
    url: '/lawyers/modelConfig',
    method: 'put',
    data: data
  })
}

// 删除AI模型参数配置
export function delModelConfig(configId) {
  return request({
    url: '/lawyers/modelConfig/' + configId,
    method: 'delete'
  })
}

// 获取默认AI模型配置
export function getDefaultModelConfig() {
  return request({
    url: '/lawyers/modelConfig/default',
    method: 'get'
  })
}

// 设置默认AI模型配置
export function setDefaultModelConfig(configId) {
  return request({
    url: '/lawyers/modelConfig/setDefault/' + configId,
    method: 'put'
  })
}

// 测试AI模型连接
export function testModelConnection(configId) {
  return request({
    url: '/lawyers/modelConfig/test/' + configId,
    method: 'post'
  })
}