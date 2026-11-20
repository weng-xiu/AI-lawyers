'use strict'

/**
 * 外呼任务 API 单测：锁定任务 CRUD、单号生成与启动/暂停/停止/执行契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/outboundTask'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listTask', [{ pageNum: 1 }], {
    url: '/lawyers/outbound/task/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getTask', [6], { url: '/lawyers/outbound/task/6', method: 'get' }],
  ['addTask', [{ taskName: '回访' }], {
    url: '/lawyers/outbound/task',
    method: 'post',
    data: { taskName: '回访' }
  }],
  ['updateTask', [{ taskId: 6 }], {
    url: '/lawyers/outbound/task',
    method: 'put',
    data: { taskId: 6 }
  }],
  ['delTask', ['6,7'], { url: '/lawyers/outbound/task/6,7', method: 'delete' }],
  ['generateTaskNo', [], { url: '/lawyers/outbound/task/generateNo', method: 'get' }],
  ['startTask', [6], { url: '/lawyers/outbound/task/start/6', method: 'post' }],
  ['pauseTask', [6], { url: '/lawyers/outbound/task/pause/6', method: 'post' }],
  ['stopTask', [6], { url: '/lawyers/outbound/task/stop/6', method: 'post' }],
  ['executeTask', [6], { url: '/lawyers/outbound/task/execute/6', method: 'post' }]
]

describe('outboundTask 接口契约', () => {
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
