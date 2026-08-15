import request from '@/utils/request'

// ==================== 运营商线路管理 ====================
export function listTrunk(query) {
  return request({
    url: '/lawyers/trunk/list',
    method: 'get',
    params: query
  })
}

export function getTrunk(trunkId) {
  return request({
    url: '/lawyers/trunk/' + trunkId,
    method: 'get'
  })
}

export function addTrunk(data) {
  return request({
    url: '/lawyers/trunk',
    method: 'post',
    data: data
  })
}

export function updateTrunk(data) {
  return request({
    url: '/lawyers/trunk',
    method: 'put',
    data: data
  })
}

export function changeTrunkStatus(data) {
  return request({
    url: '/lawyers/trunk/changeStatus',
    method: 'put',
    data: data
  })
}

export function delTrunk(trunkIds) {
  return request({
    url: '/lawyers/trunk/' + trunkIds,
    method: 'delete'
  })
}

export function testTrunk(trunkId) {
  return request({
    url: '/lawyers/trunk/test/' + trunkId,
    method: 'post'
  })
}

export function testCallTrunk(trunkId, calleeNumber) {
  return request({
    url: '/lawyers/trunk/testCall/' + trunkId,
    method: 'post',
    params: { calleeNumber }
  })
}

// ==================== 号段路由管理 ====================
export function listSegment(query) {
  return request({
    url: '/lawyers/trunk/segment/list',
    method: 'get',
    params: query
  })
}

export function addSegment(data) {
  return request({
    url: '/lawyers/trunk/segment',
    method: 'post',
    data: data
  })
}

export function updateSegment(data) {
  return request({
    url: '/lawyers/trunk/segment',
    method: 'put',
    data: data
  })
}

export function delSegment(segmentIds) {
  return request({
    url: '/lawyers/trunk/segment/' + segmentIds,
    method: 'delete'
  })
}

export function refreshSegment() {
  return request({
    url: '/lawyers/trunk/segment/refresh',
    method: 'post'
  })
}

// 号码归属识别（根据被叫号码前缀识别运营商）
export function recognizeNumber(number) {
  return request({
    url: '/lawyers/trunk/recognize',
    method: 'get',
    params: { number }
  })
}

// ==================== 线路监控大屏 ====================
export function trunkOverview() {
  return request({
    url: '/lawyers/trunk/monitor/overview',
    method: 'get'
  })
}

export function trunkStatusList() {
  return request({
    url: '/lawyers/trunk/monitor/trunkStatus',
    method: 'get'
  })
}

export function carrierStat() {
  return request({
    url: '/lawyers/trunk/monitor/carrierStat',
    method: 'get'
  })
}

export function trunkMetricList(query) {
  return request({
    url: '/lawyers/trunk/monitor/metricList',
    method: 'get',
    params: query
  })
}

export function trunkTrend(params) {
  return request({
    url: '/lawyers/trunk/monitor/trend',
    method: 'get',
    params: params
  })
}

export function trunkAlarmList(query) {
  return request({
    url: '/lawyers/trunk/monitor/alarm/list',
    method: 'get',
    params: query
  })
}

export function trunkActiveAlarms() {
  return request({
    url: '/lawyers/trunk/monitor/alarm/active',
    method: 'get'
  })
}

export function handleTrunkAlarm(data) {
  return request({
    url: '/lawyers/trunk/monitor/alarm/handle',
    method: 'post',
    data: data
  })
}

export function trunkHealthCheck() {
  return request({
    url: '/lawyers/trunk/monitor/healthCheck',
    method: 'post'
  })
}

// ==================== 外呼调度 & 拨号日志 ====================
export function dispatchStatus() {
  return request({
    url: '/lawyers/call/dispatchStatus',
    method: 'get'
  })
}

export function dialLogList(query) {
  return request({
    url: '/lawyers/call/dialLog/list',
    method: 'get',
    params: query
  })
}
