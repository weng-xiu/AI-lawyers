'use strict'

/**
 * 高频置底规则 API 单测：锁定规则 CRUD 与命中日志查询契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/hotspotSuppress'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listHotspot', [{ enabled: '0' }], {
    url: '/lawyers/hotspot/list',
    method: 'get',
    params: { enabled: '0' }
  }],
  ['getHotspot', [3], { url: '/lawyers/hotspot/3', method: 'get' }],
  ['addHotspot', [{ keyword: '劳动仲裁' }], {
    url: '/lawyers/hotspot',
    method: 'post',
    data: { keyword: '劳动仲裁' }
  }],
  ['updateHotspot', [{ suppressId: 3 }], {
    url: '/lawyers/hotspot',
    method: 'put',
    data: { suppressId: 3 }
  }],
  ['delHotspot', ['3,4'], { url: '/lawyers/hotspot/3,4', method: 'delete' }],
  ['listHotspotLog', [{ pageNum: 1 }], {
    url: '/lawyers/hotspot/log/list',
    method: 'get',
    params: { pageNum: 1 }
  }]
]

describe('hotspotSuppress 接口契约', () => {
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
