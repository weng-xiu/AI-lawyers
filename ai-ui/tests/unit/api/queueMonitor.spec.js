'use strict'

/**
 * 队列监控 API 单测：锁定队列水位、死信查询/重投/删除契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/queueMonitor'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listQueue', [], { url: '/lawyers/queueMonitor/list', method: 'get' }],
  ['listDeadLetter', [{ pageNum: 1 }], {
    url: '/lawyers/queueMonitor/dead/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['replayDeadLetter', ['call.queue', 'msg-1'], {
    url: '/lawyers/queueMonitor/dead/replay',
    method: 'post',
    params: { queue: 'call.queue', id: 'msg-1' }
  }],
  ['removeDeadLetter', ['call.queue', 'msg-1'], {
    url: '/lawyers/queueMonitor/dead/call.queue/msg-1',
    method: 'delete'
  }]
]

describe('queueMonitor 接口契约', () => {
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
