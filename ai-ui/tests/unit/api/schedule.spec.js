'use strict'

/**
 * 排班 API 单测：锁定班次/排班/签到签退接口的 url、method 与参数透传。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/schedule'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listShift', [{ pageNum: 1 }], { url: '/lawyers/schedule/shift/list', method: 'get', params: { pageNum: 1 } }],
  ['getShift', [3], { url: '/lawyers/schedule/shift/3', method: 'get' }],
  ['addShift', [{ shiftId: 1 }], { url: '/lawyers/schedule/shift', method: 'post', data: { shiftId: 1 } }],
  ['updateShift', [{ shiftId: 1 }], { url: '/lawyers/schedule/shift', method: 'put', data: { shiftId: 1 } }],
  ['delShift', ['1,2'], { url: '/lawyers/schedule/shift/1,2', method: 'delete' }],
  ['listEnabledShifts', [], { url: '/lawyers/schedule/shift/enabled', method: 'get' }],
  ['listSchedule', [{ pageNum: 1 }], { url: '/lawyers/schedule/list', method: 'get', params: { pageNum: 1 } }],
  ['getSchedule', [3], { url: '/lawyers/schedule/3', method: 'get' }],
  ['addSchedule', [{ scheduleId: 1 }], { url: '/lawyers/schedule', method: 'post', data: { scheduleId: 1 } }],
  ['updateSchedule', [{ scheduleId: 1 }], { url: '/lawyers/schedule', method: 'put', data: { scheduleId: 1 } }],
  ['delSchedule', ['1,2'], { url: '/lawyers/schedule/1,2', method: 'delete' }],
  ['batchSchedule', [{ agentIds: [1] }], { url: '/lawyers/schedule/batch', method: 'post', data: { agentIds: [1] } }],
  ['scheduleByRange', [{ beginDate: '2024-01-01' }], { url: '/lawyers/schedule/range', method: 'get', params: { beginDate: '2024-01-01' } }],
  ['checkIn', [3], { url: '/lawyers/schedule/checkIn/3', method: 'post' }],
  ['checkOut', [3], { url: '/lawyers/schedule/checkOut/3', method: 'post' }],
  ['todaySchedule', [], { url: '/lawyers/schedule/today', method: 'get' }]
]

describe('schedule 接口契约', () => {
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
