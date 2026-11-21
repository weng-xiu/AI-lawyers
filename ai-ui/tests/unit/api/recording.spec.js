'use strict'

/**
 * 通话录音 API 单测：锁定录音查询、播放/下载地址与 blob 请求契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/recording'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

describe('recording 接口契约', () => {
  const base = process.env.VUE_APP_BASE_API

  beforeEach(() => {
    request.mockClear()
  })

  test('listRecord 查询通话记录列表', async () => {
    const result = await api.listRecord({ pageNum: 1 })

    expect(request).toHaveBeenCalledWith({
      url: '/lawyers/call/record/list',
      method: 'get',
      params: { pageNum: 1 }
    })
    expect(result).toEqual({ code: 200 })
  })

  test('getRecord 查询录音详情', async () => {
    const result = await api.getRecord(88)

    expect(request).toHaveBeenCalledWith({ url: '/lawyers/call/record/88', method: 'get' })
    expect(result).toEqual({ code: 200 })
  })

  test('playUrl / downloadUrl 拼接完整地址', () => {
    expect(api.playUrl(88)).toBe(base + '/lawyers/call/record/88/play')
    expect(api.downloadUrl(88)).toBe(base + '/lawyers/call/record/88/download')
  })

  test('fetchAudioBlob 以 blob 方式请求播放地址', async () => {
    const result = await api.fetchAudioBlob(88)

    expect(request).toHaveBeenCalledWith({
      url: '/lawyers/call/record/88/play',
      method: 'get',
      responseType: 'blob'
    })
    expect(result).toEqual({ code: 200 })
  })
})
