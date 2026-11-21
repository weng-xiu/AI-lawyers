'use strict'

/**
 * 坐席换班 API 单测：锁定申请列表、可选排班、提交与审批流转契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/shiftChange'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listShiftChange', [{ status: '0' }], {
    url: '/lawyers/shift-change/list',
    method: 'get',
    params: { status: '0' }
  }],
  ['listMyShiftChange', [{ pageNum: 1 }], {
    url: '/lawyers/shift-change/mine',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getShiftChange', [21], { url: '/lawyers/shift-change/21', method: 'get' }],
  ['listMySchedules', [{ days: 7 }], {
    url: '/lawyers/shift-change/my-schedules',
    method: 'get',
    params: { days: 7 }
  }],
  ['listSwapTargets', [31], {
    url: '/lawyers/shift-change/swap-targets',
    method: 'get',
    params: { applicantScheduleId: 31 }
  }],
  ['applyShiftChange', [{ changeType: 'SWAP' }], {
    url: '/lawyers/shift-change',
    method: 'post',
    data: { changeType: 'SWAP' }
  }],
  ['approveShiftChange', [21, '同意'], {
    url: '/lawyers/shift-change/approve/21',
    method: 'put',
    data: { approveRemark: '同意' }
  }],
  ['rejectShiftChange', [21, '排班冲突'], {
    url: '/lawyers/shift-change/reject/21',
    method: 'put',
    data: { approveRemark: '排班冲突' }
  }],
  ['cancelShiftChange', [21], {
    url: '/lawyers/shift-change/cancel/21',
    method: 'put'
  }]
]

describe('shiftChange 接口契约', () => {
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
