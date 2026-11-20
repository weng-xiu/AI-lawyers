'use strict'

/**
 * 技能组 API 单测：锁定技能组 CRUD、成员增减、排队监控与派单接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/skill'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listSkillGroup', [{ pageNum: 1 }], { url: '/lawyers/skill/group/list', method: 'get', params: { pageNum: 1 } }],
  ['listEnabledGroups', [], { url: '/lawyers/skill/group/listEnabled', method: 'get' }],
  ['getSkillGroup', [3], { url: '/lawyers/skill/group/3', method: 'get' }],
  ['addSkillGroup', [{ groupId: 1 }], { url: '/lawyers/skill/group', method: 'post', data: { groupId: 1 } }],
  ['updateSkillGroup', [{ groupId: 1 }], { url: '/lawyers/skill/group', method: 'put', data: { groupId: 1 } }],
  ['delSkillGroup', ['1,2'], { url: '/lawyers/skill/group/1,2', method: 'delete' }],
  ['listMembers', [3], { url: '/lawyers/skill/group/members/3', method: 'get' }],
  ['addMembers', [3, [1, 2], 2], {
    url: '/lawyers/skill/group/members/add',
    method: 'post',
    data: { groupId: 3, agentIds: [1, 2], skillLevel: 2 }
  }],
  ['addMembers', [3, [1]], {
    url: '/lawyers/skill/group/members/add',
    method: 'post',
    data: { groupId: 3, agentIds: [1], skillLevel: 3 }
  }],
  ['removeMembers', [3, [1, 2]], {
    url: '/lawyers/skill/group/members/remove',
    method: 'post',
    data: { groupId: 3, agentIds: [1, 2] }
  }],
  ['listQueue', [{ pageNum: 1 }], { url: '/lawyers/skill/queue/list', method: 'get', params: { pageNum: 1 } }],
  ['listQueuing', [3], { url: '/lawyers/skill/queue/queuing', method: 'get', params: { groupId: 3 } }],
  ['assignManually', [10, 8], { url: '/lawyers/skill/queue/assign/10/8', method: 'post' }],
  ['kickQueue', [10, '超时'], { url: '/lawyers/skill/queue/kick/10', method: 'post', params: { reason: '超时' } }],
  ['dispatch', [3, { callId: 1 }], {
    url: '/lawyers/skill/queue/dispatch',
    method: 'post',
    params: { groupId: 3 },
    data: { callId: 1 }
  }],
  ['dispatch', [3, null], {
    url: '/lawyers/skill/queue/dispatch',
    method: 'post',
    params: { groupId: 3 },
    data: {}
  }]
]

describe('skill 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test.each(cases)('%s 按约定调用 request', async (name, args, expected) => {
    const result = await api[name](...args)

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith(expected)
    expect(result).toEqual({ code: 200 })
  })

  test('导出函数与用例表一一对应', () => {
    const covered = [...new Set(cases.map(item => item[0]))].sort()
    expect(Object.keys(api).sort()).toEqual(covered)
  })
})
