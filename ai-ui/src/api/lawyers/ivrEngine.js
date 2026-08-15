import request from '@/utils/request'

// IVR 流程执行引擎（在线调试 / 运行时调用）
export function executeFlow(data) {
  return request({
    url: '/lawyers/ivr/engine/execute',
    method: 'post',
    data: data
  })
}

// 意图识别（测试）
export function recognizeIntention(data) {
  return request({
    url: '/lawyers/ivr/engine/intention',
    method: 'post',
    data: data
  })
}
