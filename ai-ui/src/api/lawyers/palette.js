import request from '@/utils/request'

/**
 * U3 命令面板聚合搜索：工单号 / 知识标题 / 来电号码（PII 盲 token）。
 * 菜单搜索为前端本地路由树匹配，不走本接口。
 */
export function searchPalette(keyword, limit = 5) {
  return request({
    url: '/lawyers/search/palette',
    method: 'get',
    params: { keyword, limit }
  })
}
