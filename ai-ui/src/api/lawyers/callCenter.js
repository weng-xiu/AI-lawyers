import request from '@/utils/request'

// ==================== 坐席管理 ====================
export function listAgent(query) {
  return request({
    url: '/lawyers/call/agent/list',
    method: 'get',
    params: query
  })
}

export function getAgent(agentId) {
  return request({
    url: '/lawyers/call/agent/' + agentId,
    method: 'get'
  })
}

export function addAgent(data) {
  return request({
    url: '/lawyers/call/agent',
    method: 'post',
    data: data
  })
}

export function updateAgent(data) {
  return request({
    url: '/lawyers/call/agent',
    method: 'put',
    data: data
  })
}

export function delAgent(agentIds) {
  return request({
    url: '/lawyers/call/agent/' + agentIds,
    method: 'delete'
  })
}

export function agentLogin(data) {
  return request({
    url: '/lawyers/call/agent/login',
    method: 'post',
    data: data
  })
}

export function agentLogout(data) {
  return request({
    url: '/lawyers/call/agent/logout',
    method: 'post',
    data: data
  })
}

export function updateAgentStatus(data) {
  return request({
    url: '/lawyers/call/agent/status',
    method: 'post',
    data: data
  })
}

export function getOnlineAgents() {
  return request({
    url: '/lawyers/call/agent/online',
    method: 'get'
  })
}

export function getCurrentAgent(agentId) {
  return request({
    url: '/lawyers/call/agent/current/' + agentId,
    method: 'get'
  })
}

export function updateCallMode(data) {
  return request({
    url: '/lawyers/call/agent/callMode',
    method: 'post',
    data: data
  })
}

// ==================== CTI 通话控制 ====================
export function makeCall(data) {
  return request({
    url: '/lawyers/call/agent/makeCall',
    method: 'post',
    data: data
  })
}

export function holdCall(data) {
  return request({
    url: '/lawyers/call/agent/hold',
    method: 'post',
    data: data
  })
}

export function resumeCall(data) {
  return request({
    url: '/lawyers/call/agent/resume',
    method: 'post',
    data: data
  })
}

export function transferCall(data) {
  return request({
    url: '/lawyers/call/agent/transfer',
    method: 'post',
    data: data
  })
}

export function consultCall(data) {
  return request({
    url: '/lawyers/call/agent/consult',
    method: 'post',
    data: data
  })
}

export function threeWayCall(data) {
  return request({
    url: '/lawyers/call/agent/threeWay',
    method: 'post',
    data: data
  })
}

export function afterWork(data) {
  return request({
    url: '/lawyers/call/agent/afterWork',
    method: 'post',
    data: data
  })
}

export function hangupCall(data) {
  return request({
    url: '/lawyers/call/agent/hangup',
    method: 'post',
    data: data
  })
}

export function robotTakeover(data) {
  return request({
    url: '/lawyers/call/agent/robotTakeover',
    method: 'post',
    data: data
  })
}

export function ivrTransfer(data) {
  return request({
    url: '/lawyers/call/agent/ivrTransfer',
    method: 'post',
    data: data
  })
}

export function getTodayRecords(agentId) {
  return request({
    url: '/lawyers/call/agent/todayRecords/' + agentId,
    method: 'get'
  })
}

// ==================== 来电记录 ====================
export function listRecord(query) {
  return request({
    url: '/lawyers/call/record/list',
    method: 'get',
    params: query
  })
}

export function getRecord(recordId) {
  return request({
    url: '/lawyers/call/record/' + recordId,
    method: 'get'
  })
}

export function addRecord(data) {
  return request({
    url: '/lawyers/call/record',
    method: 'post',
    data: data
  })
}

export function updateRecord(data) {
  return request({
    url: '/lawyers/call/record',
    method: 'put',
    data: data
  })
}

export function delRecord(recordIds) {
  return request({
    url: '/lawyers/call/record/' + recordIds,
    method: 'delete'
  })
}

export function getRecordsByAgentId(agentId) {
  return request({
    url: '/lawyers/call/record/agent/' + agentId,
    method: 'get'
  })
}

export function getCallStatistics() {
  return request({
    url: '/lawyers/call/record/statistics',
    method: 'get'
  })
}

export function getCallStatisticsByAgent() {
  return request({
    url: '/lawyers/call/record/statistics/agent',
    method: 'get'
  })
}

export function getCallStatisticsByCategory() {
  return request({
    url: '/lawyers/call/record/statistics/category',
    method: 'get'
  })
}

export function getCallStatisticsByDate(days) {
  return request({
    url: '/lawyers/call/record/statistics/date',
    method: 'get',
    params: { days }
  })
}

// ==================== 工作台首页 ====================
export function getWorkbenchSummary() {
  return request({
    url: '/lawyers/call/record/workbench',
    method: 'get'
  })
}

// ==================== 工单管理 ====================
export function listTicket(query) {
  return request({
    url: '/lawyers/call/ticket/list',
    method: 'get',
    params: query
  })
}

export function getTicket(ticketId) {
  return request({
    url: '/lawyers/call/ticket/' + ticketId,
    method: 'get'
  })
}

export function addTicket(data) {
  return request({
    url: '/lawyers/call/ticket',
    method: 'post',
    data: data
  })
}

export function updateTicket(data) {
  return request({
    url: '/lawyers/call/ticket',
    method: 'put',
    data: data
  })
}

export function delTicket(ticketIds) {
  return request({
    url: '/lawyers/call/ticket/' + ticketIds,
    method: 'delete'
  })
}

export function getTicketsByAssignUserId(assignUserId) {
  return request({
    url: '/lawyers/call/ticket/user/' + assignUserId,
    method: 'get'
  })
}

export function processTicket(data) {
  return request({
    url: '/lawyers/call/ticket/process',
    method: 'post',
    data: data
  })
}

export function completeTicket(data) {
  return request({
    url: '/lawyers/call/ticket/complete',
    method: 'post',
    data: data
  })
}

export function archiveTicket(data) {
  return request({
    url: '/lawyers/call/ticket/archive',
    method: 'post',
    data: data
  })
}

export function generateTicketNo() {
  return request({
    url: '/lawyers/call/ticket/generateNo',
    method: 'get'
  })
}

// ==================== 转接管理 ====================
export function listTransfer(query) {
  return request({
    url: '/lawyers/call/transfer/list',
    method: 'get',
    params: query
  })
}

export function getTransfer(transferId) {
  return request({
    url: '/lawyers/call/transfer/' + transferId,
    method: 'get'
  })
}

export function addTransfer(data) {
  return request({
    url: '/lawyers/call/transfer',
    method: 'post',
    data: data
  })
}

export function delTransfer(transferIds) {
  return request({
    url: '/lawyers/call/transfer/' + transferIds,
    method: 'delete'
  })
}

export function doTransfer(data) {
  return request({
    url: '/lawyers/call/transfer/doTransfer',
    method: 'post',
    data: data
  })
}

export function getTransfersByRecordId(recordId) {
  return request({
    url: '/lawyers/call/transfer/record/' + recordId,
    method: 'get'
  })
}

// ==================== 台账记录 ====================
export function listLedger(query) {
  return request({
    url: '/lawyers/call/ledger/list',
    method: 'get',
    params: query
  })
}

export function getLedger(ledgerId) {
  return request({
    url: '/lawyers/call/ledger/' + ledgerId,
    method: 'get'
  })
}

export function addLedger(data) {
  return request({
    url: '/lawyers/call/ledger',
    method: 'post',
    data: data
  })
}

export function updateLedger(data) {
  return request({
    url: '/lawyers/call/ledger',
    method: 'put',
    data: data
  })
}

export function delLedger(ledgerIds) {
  return request({
    url: '/lawyers/call/ledger/' + ledgerIds,
    method: 'delete'
  })
}

export function generateLedgerNo() {
  return request({
    url: '/lawyers/call/ledger/generateNo',
    method: 'get'
  })
}

export function getLedgerTemplates() {
  return request({
    url: '/lawyers/call/ledger/templates',
    method: 'get'
  })
}

export function autoFillLedger(recordId) {
  return request({
    url: '/lawyers/call/ledger/autoFill/' + recordId,
    method: 'get'
  })
}

export function transferLedgerToTicket(ledgerId) {
  return request({
    url: '/lawyers/call/ledger/transferTicket/' + ledgerId,
    method: 'post'
  })
}
