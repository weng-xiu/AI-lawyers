'use strict'

/**
 * 话务预测 API 单测：锁定逐时/时段预测、准确率、排班确认与事件日历接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/forecast'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['previewForecast', [7], { url: '/lawyers/forecast/preview', method: 'get', params: { days: 7 } }],
  ['previewSlotForecast', [3], {
    url: '/lawyers/forecast/slot-preview',
    method: 'get',
    params: { days: 3 }
  }],
  ['getSlotAccuracy', [7], {
    url: '/lawyers/forecast/slot-accuracy',
    method: 'get',
    params: { days: 7 }
  }],
  ['getForecastStaffing', [7], {
    url: '/lawyers/forecast/staffing',
    method: 'get',
    params: { days: 7 }
  }],
  ['confirmForecast', [{ snapshotId: 1 }], {
    url: '/lawyers/forecast/confirm',
    method: 'post',
    data: { snapshotId: 1 }
  }],
  ['listForecastCalendar', [{ pageNum: 1 }], {
    url: '/lawyers/forecast/calendar/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['addForecastCalendar', [{ eventName: '国庆' }], {
    url: '/lawyers/forecast/calendar',
    method: 'post',
    data: { eventName: '国庆' }
  }],
  ['updateForecastCalendar', [{ calendarId: 1 }], {
    url: '/lawyers/forecast/calendar',
    method: 'put',
    data: { calendarId: 1 }
  }],
  ['delForecastCalendar', ['1,2'], {
    url: '/lawyers/forecast/calendar/1,2',
    method: 'delete'
  }]
]

describe('forecast 接口契约', () => {
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
