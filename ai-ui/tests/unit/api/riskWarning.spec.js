'use strict'

/**
 * 风险预警 API 单测：锁定预警记录 CRUD、规则 CRUD 与 F3 转办接口契约。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/riskWarning'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  ['listRiskWarning', [{ pageNum: 1 }], {
    url: '/lawyers/riskWarning/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getRiskWarning', [7], { url: '/lawyers/riskWarning/7', method: 'get' }],
  ['addRiskWarning', [{ warningType: 'A' }], {
    url: '/lawyers/riskWarning',
    method: 'post',
    data: { warningType: 'A' }
  }],
  ['updateRiskWarning', [{ warningId: 7 }], {
    url: '/lawyers/riskWarning',
    method: 'put',
    data: { warningId: 7 }
  }],
  ['delRiskWarning', ['7,8'], { url: '/lawyers/riskWarning/7,8', method: 'delete' }],
  ['exportRiskWarning', [{ warningType: 'A' }], {
    url: '/lawyers/riskWarning/export',
    method: 'post',
    params: { warningType: 'A' }
  }],
  ['listRiskWarningRule', [{ pageNum: 1 }], {
    url: '/lawyers/riskWarning/rule/list',
    method: 'get',
    params: { pageNum: 1 }
  }],
  ['getRiskWarningRule', [3], { url: '/lawyers/riskWarning/rule/3', method: 'get' }],
  ['addRiskWarningRule', [{ ruleName: '规则' }], {
    url: '/lawyers/riskWarning/rule',
    method: 'post',
    data: { ruleName: '规则' }
  }],
  ['updateRiskWarningRule', [{ ruleId: 3 }], {
    url: '/lawyers/riskWarning/rule',
    method: 'put',
    data: { ruleId: 3 }
  }],
  ['delRiskWarningRule', ['3,4'], { url: '/lawyers/riskWarning/rule/3,4', method: 'delete' }],
  ['listTransferOrgs', ['HOSPITAL'], {
    url: '/lawyers/riskWarning/transfer/orgs',
    method: 'get',
    params: { externalType: 'HOSPITAL' }
  }],
  ['listTransferOrgs', [], {
    url: '/lawyers/riskWarning/transfer/orgs',
    method: 'get',
    params: { externalType: undefined }
  }],
  ['transferRiskWarning', [{ warningId: 7 }], {
    url: '/lawyers/riskWarning/transfer',
    method: 'post',
    data: { warningId: 7 }
  }]
]

describe('riskWarning 接口契约', () => {
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
