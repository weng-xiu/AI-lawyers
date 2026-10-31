import request from '@/utils/request'

// ==================== 话务预测（P1-9 / F9） ====================

// 未来 N 天逐时话务预测
export function previewForecast(days) {
  return request({
    url: '/lawyers/forecast/preview',
    method: 'get',
    params: { days }
  })
}

// F9：未来 N 天 15 分钟粒度话务预测（每天 96 个时段）
export function previewSlotForecast(days) {
  return request({
    url: '/lawyers/forecast/slot-preview',
    method: 'get',
    params: { days }
  })
}

// F9：15 分钟预测 vs 实际对比验收（MAE / MAPE / 准确率）
export function getSlotAccuracy(days) {
  return request({
    url: '/lawyers/forecast/slot-accuracy',
    method: 'get',
    params: { days }
  })
}

// 排班建议（预测 + 逐日峰值坐席）
export function getForecastStaffing(days) {
  return request({
    url: '/lawyers/forecast/staffing',
    method: 'get',
    params: { days }
  })
}

// 班组长确认预测/排班快照
export function confirmForecast(data) {
  return request({
    url: '/lawyers/forecast/confirm',
    method: 'post',
    data
  })
}

// ==================== 节假日/事件日历 ====================
export function listForecastCalendar(query) {
  return request({
    url: '/lawyers/forecast/calendar/list',
    method: 'get',
    params: query
  })
}

export function addForecastCalendar(data) {
  return request({
    url: '/lawyers/forecast/calendar',
    method: 'post',
    data
  })
}

export function updateForecastCalendar(data) {
  return request({
    url: '/lawyers/forecast/calendar',
    method: 'put',
    data
  })
}

export function delForecastCalendar(calendarIds) {
  return request({
    url: '/lawyers/forecast/calendar/' + calendarIds,
    method: 'delete'
  })
}
