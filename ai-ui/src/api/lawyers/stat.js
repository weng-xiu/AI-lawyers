import request from '@/utils/request'

// ==================== 运营大屏 ====================

// 大屏聚合数据（一次拉取全部模块）
export function dashboardData(query) {
  return request({
    url: '/lawyers/dashboard/data',
    method: 'get',
    params: query
  })
}

// ==================== 坐席效能报表 ====================

// 坐席效能聚合列表
export function listAgentPerformance(query) {
  return request({
    url: '/lawyers/performance/agent/list',
    method: 'get',
    params: query
  })
}
