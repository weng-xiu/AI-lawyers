import request from '@/utils/request'

// 查询AI智能体配置列表
export function listAgentConfig(query) {
  return request({
    url: '/lawyers/agent/config/list',
    method: 'get',
    params: query
  })
}

// 查询AI智能体配置详细
export function getAgentConfig(agentId) {
  return request({
    url: '/lawyers/agent/config/' + agentId,
    method: 'get'
  })
}

// 启用状态的智能体列表（设计器下拉用）
export function listActiveAgent() {
  return request({
    url: '/lawyers/agent/config/active',
    method: 'get'
  })
}

// 新增AI智能体配置
export function addAgentConfig(data) {
  return request({
    url: '/lawyers/agent/config',
    method: 'post',
    data: data
  })
}

// 修改AI智能体配置
export function updateAgentConfig(data) {
  return request({
    url: '/lawyers/agent/config',
    method: 'put',
    data: data
  })
}

// 删除AI智能体配置
export function delAgentConfig(agentId) {
  return request({
    url: '/lawyers/agent/config/' + agentId,
    method: 'delete'
  })
}

// 智能体连接测试
export function testAgentConfig(agentId) {
  return request({
    url: '/lawyers/agent/config/test/' + agentId,
    method: 'get'
  })
}
