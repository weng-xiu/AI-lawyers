import request from '@/utils/request'

// 概览指标
export function getModelCostOverview(query) {
  return request({
    url: '/lawyers/modelCost/overview',
    method: 'get',
    params: query
  })
}

// 按日趋势
export function getModelCostTrend(query) {
  return request({
    url: '/lawyers/modelCost/trend',
    method: 'get',
    params: query
  })
}

// 模型分布
export function getModelCostByModel(query) {
  return request({
    url: '/lawyers/modelCost/byModel',
    method: 'get',
    params: query
  })
}

// 类型+场景分布
export function getModelCostByScene(query) {
  return request({
    url: '/lawyers/modelCost/byScene',
    method: 'get',
    params: query
  })
}

// 调用明细分页
export function listModelCallLog(query) {
  return request({
    url: '/lawyers/modelCost/logList',
    method: 'get',
    params: query
  })
}
