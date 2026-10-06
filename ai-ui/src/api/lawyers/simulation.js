import request from '@/utils/request'

// ==================== P1-8 坐席辅助/机器人仿真回归（统一异步任务平台） ====================

// 提交 Copilot 评测任务（casesJson 为空串时跑内置默认用例集）
export function submitCopilotSimulation(data) {
  return request({
    url: '/lawyers/copilot/simulation/submit',
    method: 'post',
    headers: { 'Content-Type': 'text/plain;charset=utf-8' },
    data: data || ''
  })
}

// 查询 Copilot 评测任务进度（完成后 data.report 为完整报告）
export function getCopilotSimulationProgress(taskId) {
  return request({
    url: '/lawyers/copilot/simulation/progress',
    method: 'get',
    params: { taskId: taskId }
  })
}

// 提交机器人评测任务
export function submitRobotSimulation(data) {
  return request({
    url: '/lawyers/robot/simulation/submit',
    method: 'post',
    headers: { 'Content-Type': 'text/plain;charset=utf-8' },
    data: data || ''
  })
}

// 查询机器人评测任务进度
export function getRobotSimulationProgress(taskId) {
  return request({
    url: '/lawyers/robot/simulation/progress',
    method: 'get',
    params: { taskId: taskId }
  })
}
