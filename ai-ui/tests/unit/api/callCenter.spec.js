'use strict'

/**
 * 呼叫中心 API 层单测：锁定每个接口的 url/method/参数透传，
 * 防止前端调用路径与后端 @RequestMapping 漂移导致 404。
 */
import request from '@/utils/request'
import * as api from '@/api/lawyers/callCenter'

jest.mock('@/utils/request', () => jest.fn(() => Promise.resolve({ code: 200 })))

const cases = [
  // ==================== 坐席管理 ====================
  ['listAgent', [{ pageNum: 1 }], { url: '/lawyers/call/agent/list', method: 'get', params: { pageNum: 1 } }],
  ['getAgent', [5], { url: '/lawyers/call/agent/5', method: 'get' }],
  ['getMyAgent', [], { url: '/lawyers/call/agent/my', method: 'get' }],
  ['addAgent', [{ agentId: 1 }], { url: '/lawyers/call/agent', method: 'post', data: { agentId: 1 } }],
  ['updateAgent', [{ agentId: 1 }], { url: '/lawyers/call/agent', method: 'put', data: { agentId: 1 } }],
  ['delAgent', ['1,2'], { url: '/lawyers/call/agent/1,2', method: 'delete' }],
  ['agentLogin', [{ agentId: 1 }], { url: '/lawyers/call/agent/login', method: 'post', data: { agentId: 1 } }],
  ['agentLogout', [{ agentId: 1 }], { url: '/lawyers/call/agent/logout', method: 'post', data: { agentId: 1 } }],
  ['updateAgentStatus', [{ status: '1' }], { url: '/lawyers/call/agent/status', method: 'post', data: { status: '1' } }],
  ['getOnlineAgents', [], { url: '/lawyers/call/agent/online', method: 'get' }],
  ['getCurrentAgent', [5], { url: '/lawyers/call/agent/current/5', method: 'get' }],
  ['updateCallMode', [{ callMode: 'manual' }], { url: '/lawyers/call/agent/callMode', method: 'post', data: { callMode: 'manual' } }],
  // ==================== CTI 通话控制 ====================
  ['makeCall', [{ phone: '138' }], { url: '/lawyers/call/agent/makeCall', method: 'post', data: { phone: '138' } }],
  ['holdCall', [{ callId: 1 }], { url: '/lawyers/call/agent/hold', method: 'post', data: { callId: 1 } }],
  ['resumeCall', [{ callId: 1 }], { url: '/lawyers/call/agent/resume', method: 'post', data: { callId: 1 } }],
  ['transferCall', [{ callId: 1 }], { url: '/lawyers/call/agent/transfer', method: 'post', data: { callId: 1 } }],
  ['consultCall', [{ callId: 1 }], { url: '/lawyers/call/agent/consult', method: 'post', data: { callId: 1 } }],
  ['threeWayCall', [{ callId: 1 }], { url: '/lawyers/call/agent/threeWay', method: 'post', data: { callId: 1 } }],
  ['afterWork', [{ callId: 1 }], { url: '/lawyers/call/agent/afterWork', method: 'post', data: { callId: 1 } }],
  ['hangupCall', [{ callId: 1 }], { url: '/lawyers/call/agent/hangup', method: 'post', data: { callId: 1 } }],
  ['robotTakeover', [{ callId: 1 }], { url: '/lawyers/call/agent/robotTakeover', method: 'post', data: { callId: 1 } }],
  ['ivrTransfer', [{ callId: 1 }], { url: '/lawyers/call/agent/ivrTransfer', method: 'post', data: { callId: 1 } }],
  ['getTodayRecords', [5], { url: '/lawyers/call/agent/todayRecords/5', method: 'get' }],
  // ==================== 来电记录 ====================
  ['listRecord', [{ pageNum: 1 }], { url: '/lawyers/call/record/list', method: 'get', params: { pageNum: 1 } }],
  ['getRecord', [5], { url: '/lawyers/call/record/5', method: 'get' }],
  ['addRecord', [{ recordId: 1 }], { url: '/lawyers/call/record', method: 'post', data: { recordId: 1 } }],
  ['updateRecord', [{ recordId: 1 }], { url: '/lawyers/call/record', method: 'put', data: { recordId: 1 } }],
  ['delRecord', ['1,2'], { url: '/lawyers/call/record/1,2', method: 'delete' }],
  ['generateAiSummary', [5, true], { url: '/lawyers/call/record/5/ai-summary', method: 'post', params: { force: true } }],
  ['generateAiSummary', [5], { url: '/lawyers/call/record/5/ai-summary', method: 'post', params: { force: false } }],
  ['getRecordsByAgentId', [5], { url: '/lawyers/call/record/agent/5', method: 'get' }],
  ['getCallStatistics', [], { url: '/lawyers/call/record/statistics', method: 'get' }],
  ['getCallStatisticsByAgent', [], { url: '/lawyers/call/record/statistics/agent', method: 'get' }],
  ['getCallStatisticsByCategory', [], { url: '/lawyers/call/record/statistics/category', method: 'get' }],
  ['getCallStatisticsByDate', [7], { url: '/lawyers/call/record/statistics/date', method: 'get', params: { days: 7 } }],
  ['getWorkbenchSummary', [], { url: '/lawyers/call/record/workbench', method: 'get' }],
  // ==================== 工单管理 ====================
  ['listTicket', [{ pageNum: 1 }], { url: '/lawyers/call/ticket/list', method: 'get', params: { pageNum: 1 } }],
  ['getTicket', [5], { url: '/lawyers/call/ticket/5', method: 'get' }],
  ['addTicket', [{ ticketId: 1 }], { url: '/lawyers/call/ticket', method: 'post', data: { ticketId: 1 } }],
  ['updateTicket', [{ ticketId: 1 }], { url: '/lawyers/call/ticket', method: 'put', data: { ticketId: 1 } }],
  ['delTicket', ['1,2'], { url: '/lawyers/call/ticket/1,2', method: 'delete' }],
  ['getTicketsByAssignUserId', [5], { url: '/lawyers/call/ticket/user/5', method: 'get' }],
  ['processTicket', [{ ticketId: 1 }], { url: '/lawyers/call/ticket/process', method: 'post', data: { ticketId: 1 } }],
  ['completeTicket', [{ ticketId: 1 }], { url: '/lawyers/call/ticket/complete', method: 'post', data: { ticketId: 1 } }],
  ['archiveTicket', [{ ticketId: 1 }], { url: '/lawyers/call/ticket/archive', method: 'post', data: { ticketId: 1 } }],
  ['transferOutTicket', [{ ticketId: 1 }], { url: '/lawyers/ticket/transfer', method: 'post', data: { ticketId: 1 } }],
  ['generateTicketNo', [], { url: '/lawyers/call/ticket/generateNo', method: 'get' }],
  ['listTicketActions', ['flow', '1'], { url: '/lawyers/ticket-flow/actions/flow/1', method: 'get' }],
  // ==================== 转接管理 ====================
  ['listTransfer', [{ pageNum: 1 }], { url: '/lawyers/call/transfer/list', method: 'get', params: { pageNum: 1 } }],
  ['getTransfer', [5], { url: '/lawyers/call/transfer/5', method: 'get' }],
  ['addTransfer', [{ transferId: 1 }], { url: '/lawyers/call/transfer', method: 'post', data: { transferId: 1 } }],
  ['delTransfer', ['1,2'], { url: '/lawyers/call/transfer/1,2', method: 'delete' }],
  ['doTransfer', [{ transferId: 1 }], { url: '/lawyers/call/transfer/doTransfer', method: 'post', data: { transferId: 1 } }],
  ['getTransfersByRecordId', [5], { url: '/lawyers/call/transfer/record/5', method: 'get' }],
  // ==================== 台账记录 ====================
  ['listLedger', [{ pageNum: 1 }], { url: '/lawyers/call/ledger/list', method: 'get', params: { pageNum: 1 } }],
  ['getLedger', [5], { url: '/lawyers/call/ledger/5', method: 'get' }],
  ['addLedger', [{ ledgerId: 1 }], { url: '/lawyers/call/ledger', method: 'post', data: { ledgerId: 1 } }],
  ['updateLedger', [{ ledgerId: 1 }], { url: '/lawyers/call/ledger', method: 'put', data: { ledgerId: 1 } }],
  ['delLedger', ['1,2'], { url: '/lawyers/call/ledger/1,2', method: 'delete' }],
  ['generateLedgerNo', [], { url: '/lawyers/call/ledger/generateNo', method: 'get' }],
  ['getLedgerTemplates', [], { url: '/lawyers/call/ledger/templates', method: 'get' }],
  ['autoFillLedger', [5], { url: '/lawyers/call/ledger/autoFill/5', method: 'get' }],
  ['transferLedgerToTicket', [5], { url: '/lawyers/call/ledger/transferTicket/5', method: 'post' }]
]

describe('callCenter 接口契约', () => {
  beforeEach(() => {
    request.mockClear()
  })

  test.each(cases)('%s 按约定调用 request', async (name, args, expected) => {
    const result = await api[name](...args)

    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith(expected)
    expect(result).toEqual({ code: 200 })
  })

  test('导出函数与用例表一一对应，新增接口需同步补测', () => {
    const exported = Object.keys(api).sort()
    const covered = [...new Set(cases.map(item => item[0]))].sort()
    expect(exported).toEqual(covered)
  })
})
