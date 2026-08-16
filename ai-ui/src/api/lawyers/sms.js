import request from '@/utils/request'

// ==================== 短信通道配置 ====================

// 查询短信通道配置列表
export function listSmsConfig(query) {
  return request({
    url: '/lawyers/sms/config/list',
    method: 'get',
    params: query
  })
}

// 查询短信通道配置详细
export function getSmsConfig(configId) {
  return request({
    url: '/lawyers/sms/config/' + configId,
    method: 'get'
  })
}

// 所有启用通道（设计器/模板下拉）
export function listEnabledConfigs() {
  return request({
    url: '/lawyers/sms/config/enabled',
    method: 'get'
  })
}

// 新增短信通道配置
export function addSmsConfig(data) {
  return request({
    url: '/lawyers/sms/config',
    method: 'post',
    data: data
  })
}

// 修改短信通道配置
export function updateSmsConfig(data) {
  return request({
    url: '/lawyers/sms/config',
    method: 'put',
    data: data
  })
}

// 删除短信通道配置
export function delSmsConfig(configIds) {
  return request({
    url: '/lawyers/sms/config/' + configIds,
    method: 'delete'
  })
}

// ==================== 短信模板 ====================

// 查询短信模板列表
export function listSmsTemplate(query) {
  return request({
    url: '/lawyers/sms/template/list',
    method: 'get',
    params: query
  })
}

// 查询短信模板详细
export function getSmsTemplate(templateId) {
  return request({
    url: '/lawyers/sms/template/' + templateId,
    method: 'get'
  })
}

// 所有启用模板（设计器下拉）
export function listEnabledTemplates() {
  return request({
    url: '/lawyers/sms/template/enabled',
    method: 'get'
  })
}

// 新增短信模板
export function addSmsTemplate(data) {
  return request({
    url: '/lawyers/sms/template',
    method: 'post',
    data: data
  })
}

// 修改短信模板
export function updateSmsTemplate(data) {
  return request({
    url: '/lawyers/sms/template',
    method: 'put',
    data: data
  })
}

// 删除短信模板
export function delSmsTemplate(templateIds) {
  return request({
    url: '/lawyers/sms/template/' + templateIds,
    method: 'delete'
  })
}

// ==================== 短信发送记录 ====================

// 查询短信发送记录列表
export function listSmsLog(query) {
  return request({
    url: '/lawyers/sms/log/list',
    method: 'get',
    params: query
  })
}

// 调试发送短信（不经过 IVR）
export function sendSms(data) {
  return request({
    url: '/lawyers/sms/log/send',
    method: 'post',
    data: data
  })
}

// 删除短信发送记录
export function delSmsLog(logIds) {
  return request({
    url: '/lawyers/sms/log/' + logIds,
    method: 'delete'
  })
}
