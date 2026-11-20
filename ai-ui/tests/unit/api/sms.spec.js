'use strict'

/**
 * 短信 API 单测：锁定通道配置、模板与发送记录接口的 url、method 与参数透传。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/sms'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listSmsConfig', [{ pageNum: 1 }], { url: '/lawyers/sms/config/list', method: 'get', params: { pageNum: 1 } }],
  ['getSmsConfig', [3], { url: '/lawyers/sms/config/3', method: 'get' }],
  ['listEnabledConfigs', [], { url: '/lawyers/sms/config/enabled', method: 'get' }],
  ['addSmsConfig', [{ configId: 1 }], { url: '/lawyers/sms/config', method: 'post', data: { configId: 1 } }],
  ['updateSmsConfig', [{ configId: 1 }], { url: '/lawyers/sms/config', method: 'put', data: { configId: 1 } }],
  ['delSmsConfig', ['1,2'], { url: '/lawyers/sms/config/1,2', method: 'delete' }],
  ['listSmsTemplate', [{ pageNum: 1 }], { url: '/lawyers/sms/template/list', method: 'get', params: { pageNum: 1 } }],
  ['getSmsTemplate', [3], { url: '/lawyers/sms/template/3', method: 'get' }],
  ['listEnabledTemplates', [], { url: '/lawyers/sms/template/enabled', method: 'get' }],
  ['addSmsTemplate', [{ templateId: 1 }], { url: '/lawyers/sms/template', method: 'post', data: { templateId: 1 } }],
  ['updateSmsTemplate', [{ templateId: 1 }], { url: '/lawyers/sms/template', method: 'put', data: { templateId: 1 } }],
  ['delSmsTemplate', ['1,2'], { url: '/lawyers/sms/template/1,2', method: 'delete' }],
  ['listSmsLog', [{ pageNum: 1 }], { url: '/lawyers/sms/log/list', method: 'get', params: { pageNum: 1 } }],
  ['sendSms', [{ phone: '138' }], { url: '/lawyers/sms/log/send', method: 'post', data: { phone: '138' } }],
  ['delSmsLog', ['1,2'], { url: '/lawyers/sms/log/1,2', method: 'delete' }]
]

describe('sms 接口契约', () => {
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
