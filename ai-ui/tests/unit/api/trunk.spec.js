'use strict'

/**
 * 中继线路 API 单测：锁定线路/号段/监控大屏/外呼调度接口的 url、method 与参数透传。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/trunk'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  // 线路管理
  ['listTrunk', [{ pageNum: 1 }], { url: '/lawyers/trunk/list', method: 'get', params: { pageNum: 1 } }],
  ['getTrunk', [3], { url: '/lawyers/trunk/3', method: 'get' }],
  ['addTrunk', [{ trunkId: 1 }], { url: '/lawyers/trunk', method: 'post', data: { trunkId: 1 } }],
  ['updateTrunk', [{ trunkId: 1 }], { url: '/lawyers/trunk', method: 'put', data: { trunkId: 1 } }],
  ['changeTrunkStatus', [{ trunkId: 1 }], { url: '/lawyers/trunk/changeStatus', method: 'put', data: { trunkId: 1 } }],
  ['delTrunk', ['1,2'], { url: '/lawyers/trunk/1,2', method: 'delete' }],
  ['testTrunk', [3], { url: '/lawyers/trunk/test/3', method: 'post' }],
  ['testCallTrunk', [3, '138'], { url: '/lawyers/trunk/testCall/3', method: 'post', params: { calleeNumber: '138' } }],
  // 号段路由
  ['listSegment', [{ pageNum: 1 }], { url: '/lawyers/trunk/segment/list', method: 'get', params: { pageNum: 1 } }],
  ['addSegment', [{ segmentId: 1 }], { url: '/lawyers/trunk/segment', method: 'post', data: { segmentId: 1 } }],
  ['updateSegment', [{ segmentId: 1 }], { url: '/lawyers/trunk/segment', method: 'put', data: { segmentId: 1 } }],
  ['delSegment', ['1,2'], { url: '/lawyers/trunk/segment/1,2', method: 'delete' }],
  ['refreshSegment', [], { url: '/lawyers/trunk/segment/refresh', method: 'post' }],
  ['recognizeNumber', ['13800000000'], { url: '/lawyers/trunk/recognize', method: 'get', params: { number: '13800000000' } }],
  // 监控大屏
  ['trunkOverview', [], { url: '/lawyers/trunk/monitor/overview', method: 'get' }],
  ['trunkStatusList', [], { url: '/lawyers/trunk/monitor/trunkStatus', method: 'get' }],
  ['carrierStat', [], { url: '/lawyers/trunk/monitor/carrierStat', method: 'get' }],
  ['trunkMetricList', [{ pageNum: 1 }], { url: '/lawyers/trunk/monitor/metricList', method: 'get', params: { pageNum: 1 } }],
  ['trunkTrend', [{ hours: 24 }], { url: '/lawyers/trunk/monitor/trend', method: 'get', params: { hours: 24 } }],
  ['trunkAlarmList', [{ pageNum: 1 }], { url: '/lawyers/trunk/monitor/alarm/list', method: 'get', params: { pageNum: 1 } }],
  ['trunkActiveAlarms', [], { url: '/lawyers/trunk/monitor/alarm/active', method: 'get' }],
  ['handleTrunkAlarm', [{ alarmId: 1 }], { url: '/lawyers/trunk/monitor/alarm/handle', method: 'post', data: { alarmId: 1 } }],
  ['trunkHealthCheck', [], { url: '/lawyers/trunk/monitor/healthCheck', method: 'post' }],
  // PJSIP 只读
  ['pjsipEndpoints', [], { url: '/lawyers/trunk/monitor/pjsip/endpoints', method: 'get' }],
  ['pjsipQueues', [], { url: '/lawyers/trunk/monitor/pjsip/queues', method: 'get' }],
  ['pjsipSummary', [], { url: '/lawyers/trunk/monitor/pjsip/summary', method: 'get' }],
  ['pjsipRegistrations', [], { url: '/lawyers/trunk/monitor/pjsip/registrations', method: 'get' }],
  // 外呼调度
  ['dispatchStatus', [], { url: '/lawyers/call/dispatchStatus', method: 'get' }],
  ['dialLogList', [{ pageNum: 1 }], { url: '/lawyers/call/dialLog/list', method: 'get', params: { pageNum: 1 } }]
]

describe('trunk 接口契约', () => {
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
