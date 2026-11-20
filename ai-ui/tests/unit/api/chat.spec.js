'use strict'

/**
 * 图文会话 API 单测：锁定会话查询/统计与消息收发、已读、关闭、转接契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/chat'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listChatSession', [{ pageNum: 1 }], {
    url: '/lawyers/chatSession/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getChatSession', [10], { url: '/lawyers/chatSession/10', method: 'get' }],
  ['addChatSession', [{ userId: 2 }], {
    url: '/lawyers/chatSession',
    method: 'post',
    data: { userId: 2 }
  }],
  ['getChatSessionStats', [], { url: '/lawyers/chatSession/stats', method: 'get' }],
  ['listChatMessage', [10], { url: '/lawyers/chatMessage/list/10', method: 'get' }],
  ['sendChatMessage', [{ sessionId: 10, content: '您好' }], {
    url: '/lawyers/chatMessage/send',
    method: 'post',
    data: { sessionId: 10, content: '您好' }
  }],
  ['markRead', [10], { url: '/lawyers/chatMessage/read/10', method: 'put' }],
  ['closeChatSession', [10], { url: '/lawyers/chatMessage/close/10', method: 'put' }],
  ['transferChatSession', [{ sessionId: 10, agentId: 3 }], {
    url: '/lawyers/chatMessage/transfer',
    method: 'put',
    data: { sessionId: 10, agentId: 3 }
  }]
]

describe('chat 接口契约', () => {
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
