'use strict'

/**
 * 工作台 API 单测：锁定统计/待办/公告接口的 url、method 与参数透传。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/workbench'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['getWorkbenchStats', [], { url: '/lawyers/workbench/stats', method: 'get' }],
  ['getWorkbenchTodos', [], { url: '/lawyers/workbench/todos', method: 'get' }],
  ['getWorkbenchRecentCalls', [10], { url: '/lawyers/workbench/recentCalls', method: 'get', params: { limit: 10 } }],
  ['getWorkbenchNotices', [5], { url: '/lawyers/workbench/notices', method: 'get', params: { limit: 5 } }],
  ['listTodo', [{ pageNum: 1 }], { url: '/lawyers/workbench/todo/list', method: 'get', params: { pageNum: 1 } }],
  ['getTodo', [3], { url: '/lawyers/workbench/todo/3', method: 'get' }],
  ['getTodoTicket', [3], { url: '/lawyers/workbench/todo/3/ticket', method: 'get' }],
  ['addTodo', [{ todoId: 1 }], { url: '/lawyers/workbench/todo', method: 'post', data: { todoId: 1 } }],
  ['updateTodo', [{ todoId: 1 }], { url: '/lawyers/workbench/todo', method: 'put', data: { todoId: 1 } }],
  ['delTodo', ['1,2'], { url: '/lawyers/workbench/todo/1,2', method: 'delete' }],
  ['processTodo', [3], { url: '/lawyers/workbench/todo/process/3', method: 'put' }],
  ['deferTodo', [3], { url: '/lawyers/workbench/todo/defer/3', method: 'put' }],
  ['ignoreTodo', [3], { url: '/lawyers/workbench/todo/ignore/3', method: 'put' }],
  ['listNotice', [{ pageNum: 1 }], { url: '/lawyers/workbench/notice/list', method: 'get', params: { pageNum: 1 } }],
  ['getNotice', [3], { url: '/lawyers/workbench/notice/3', method: 'get' }],
  ['addNotice', [{ noticeId: 1 }], { url: '/lawyers/workbench/notice', method: 'post', data: { noticeId: 1 } }],
  ['updateNotice', [{ noticeId: 1 }], { url: '/lawyers/workbench/notice', method: 'put', data: { noticeId: 1 } }],
  ['delNotice', ['1,2'], { url: '/lawyers/workbench/notice/1,2', method: 'delete' }],
  ['publishNotice', [3], { url: '/lawyers/workbench/notice/publish/3', method: 'put' }]
]

describe('workbench 接口契约', () => {
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
    expect(Object.keys(api).sort()).toEqual(cases.map(item => item[0]).sort())
  })
})
