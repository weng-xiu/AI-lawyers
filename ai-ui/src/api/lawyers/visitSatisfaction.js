import request from '@/utils/request'

// F5 回访满意度归因分析看板聚合数据
export function visitSatisfactionBoard(query) {
  return request({
    url: '/lawyers/visit/satisfaction/board',
    method: 'get',
    params: query
  })
}
