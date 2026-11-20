'use strict'

/**
 * 定时任务 API 单测：锁定任务 CRUD、状态变更与立即执行契约。
 */
import request from '@/utils/request'
import * as api from '@/api/monitor/job'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listJob', [{ pageNum: 1 }], { url: '/monitor/job/list', method: 'get', params: { pageNum: 1 } }],
  ['getJob', [4], { url: '/monitor/job/4', method: 'get' }],
  ['addJob', [{ jobName: '清理' }], {
    url: '/monitor/job',
    method: 'post',
    data: { jobName: '清理' }
  }],
  ['updateJob', [{ jobId: 4 }], {
    url: '/monitor/job',
    method: 'put',
    data: { jobId: 4 }
  }],
  ['delJob', [4], { url: '/monitor/job/4', method: 'delete' }],
  ['changeJobStatus', [4, '1'], {
    url: '/monitor/job/changeStatus',
    method: 'put',
    data: { jobId: 4, status: '1' }
  }],
  ['runJob', [4, 'DEFAULT'], {
    url: '/monitor/job/run',
    method: 'put',
    data: { jobId: 4, jobGroup: 'DEFAULT' }
  }]
]

describe('job 接口契约', () => {
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
