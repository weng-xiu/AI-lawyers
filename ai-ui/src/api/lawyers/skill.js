import request from '@/utils/request'

// 查询技能组列表
export function listSkillGroup(query) {
  return request({
    url: '/lawyers/skill/group/list',
    method: 'get',
    params: query
  })
}

// 所有启用技能组（下拉）
export function listEnabledGroups() {
  return request({
    url: '/lawyers/skill/group/listEnabled',
    method: 'get'
  })
}

// 查询技能组详情
export function getSkillGroup(groupId) {
  return request({
    url: '/lawyers/skill/group/' + groupId,
    method: 'get'
  })
}

// 新增技能组
export function addSkillGroup(data) {
  return request({
    url: '/lawyers/skill/group',
    method: 'post',
    data: data
  })
}

// 修改技能组
export function updateSkillGroup(data) {
  return request({
    url: '/lawyers/skill/group',
    method: 'put',
    data: data
  })
}

// 删除技能组
export function delSkillGroup(groupIds) {
  return request({
    url: '/lawyers/skill/group/' + groupIds,
    method: 'delete'
  })
}

// 技能组成员列表
export function listMembers(groupId) {
  return request({
    url: '/lawyers/skill/group/members/' + groupId,
    method: 'get'
  })
}

// 添加成员
export function addMembers(groupId, agentIds, skillLevel) {
  return request({
    url: '/lawyers/skill/group/members/add',
    method: 'post',
    data: { groupId: groupId, agentIds: agentIds, skillLevel: skillLevel || 3 }
  })
}

// 移除成员
export function removeMembers(groupId, agentIds) {
  return request({
    url: '/lawyers/skill/group/members/remove',
    method: 'post',
    data: { groupId: groupId, agentIds: agentIds }
  })
}

// ---- 排队监控 ----
export function listQueue(query) {
  return request({
    url: '/lawyers/skill/queue/list',
    method: 'get',
    params: query
  })
}

export function listQueuing(groupId) {
  return request({
    url: '/lawyers/skill/queue/queuing',
    method: 'get',
    params: { groupId: groupId }
  })
}

export function assignManually(queueId, agentId) {
  return request({
    url: '/lawyers/skill/queue/assign/' + queueId + '/' + agentId,
    method: 'post'
  })
}

export function kickQueue(queueId, reason) {
  return request({
    url: '/lawyers/skill/queue/kick/' + queueId,
    method: 'post',
    params: { reason: reason }
  })
}

export function dispatch(groupId, ctx) {
  return request({
    url: '/lawyers/skill/queue/dispatch',
    method: 'post',
    params: { groupId: groupId },
    data: ctx || {}
  })
}
