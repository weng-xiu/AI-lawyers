import request from '@/utils/request'

// ==================== 班次管理 ====================
export function listShift(query) {
  return request({
    url: '/lawyers/schedule/shift/list',
    method: 'get',
    params: query
  })
}

export function getShift(shiftId) {
  return request({
    url: '/lawyers/schedule/shift/' + shiftId,
    method: 'get'
  })
}

export function addShift(data) {
  return request({
    url: '/lawyers/schedule/shift',
    method: 'post',
    data: data
  })
}

export function updateShift(data) {
  return request({
    url: '/lawyers/schedule/shift',
    method: 'put',
    data: data
  })
}

export function delShift(shiftIds) {
  return request({
    url: '/lawyers/schedule/shift/' + shiftIds,
    method: 'delete'
  })
}

export function listEnabledShifts() {
  return request({
    url: '/lawyers/schedule/shift/listEnabled',
    method: 'get'
  })
}

// ==================== 排班管理 ====================
export function listSchedule(query) {
  return request({
    url: '/lawyers/schedule/list',
    method: 'get',
    params: query
  })
}

export function getSchedule(scheduleId) {
  return request({
    url: '/lawyers/schedule/' + scheduleId,
    method: 'get'
  })
}

export function addSchedule(data) {
  return request({
    url: '/lawyers/schedule',
    method: 'post',
    data: data
  })
}

export function updateSchedule(data) {
  return request({
    url: '/lawyers/schedule',
    method: 'put',
    data: data
  })
}

export function delSchedule(scheduleIds) {
  return request({
    url: '/lawyers/schedule/' + scheduleIds,
    method: 'delete'
  })
}

// 批量排班：为多个坐席在一个日期范围内每天创建排班
export function batchSchedule(data) {
  return request({
    url: '/lawyers/schedule/batch',
    method: 'post',
    data: data
  })
}

// 按日期范围查询排班
export function scheduleByRange(query) {
  return request({
    url: '/lawyers/schedule/range',
    method: 'get',
    params: query
  })
}

// 签到
export function checkIn(scheduleId) {
  return request({
    url: '/lawyers/schedule/checkIn/' + scheduleId,
    method: 'post'
  })
}

// 签退
export function checkOut(scheduleId) {
  return request({
    url: '/lawyers/schedule/checkOut/' + scheduleId,
    method: 'post'
  })
}

// 今日排班
export function todaySchedule() {
  return request({
    url: '/lawyers/schedule/today',
    method: 'get'
  })
}
