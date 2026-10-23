/**
 * V7 状态标签语义色统一映射（配色.md 三、功能语义色）。
 *
 * 后端 `ai_call_ticket.status`：0 待处理 / 1 处理中 / 2 已完成 / 3 已归档。
 * Element 的 type 只承载四个内置语义色（success/warning/danger/info），
 * 「处理中」需要信息蓝 #2B6BB0，与品牌主蓝 #1A3C6E 区分，走
 * `tag-status--processing` 自定义类（见 assets/styles/element-ui.scss）。
 *
 * 本模块收敛此前散落在工单列表 / 来电弹屏 / 工作台 / AI 辅助面板中的
 * 多套不一致映射（曾出现同一状态一处 warning、一处 danger，以及非法的
 * type="primary"），避免后续继续漂移。
 *
 * 用法：组件 methods 中透传一层，例如
 *   ticketStatusType(s) { return statusTag.ticketStatusType(s) }
 *   <el-tag :type="ticketStatusType(row.status)" :class="ticketStatusClass(row.status)">
 */

const UNKNOWN = { label: '未知', type: 'info', className: '' }

/** 工单 4 态语义色：待处理橙 / 处理中信息蓝 / 已完成绿 / 已归档灰 */
export const TICKET_STATUS = {
  '0': { label: '待处理', type: 'warning', className: '' },
  '1': { label: '处理中', type: '', className: 'tag-status--processing' },
  '2': { label: '已完成', type: 'success', className: '' },
  '3': { label: '已归档', type: 'info', className: '' }
}

/** 通话记录状态（来电弹屏 / 工作台最近通话）：接通中信息蓝，其余走内置语义色 */
export const CALL_RECORD_STATUS = {
  '0': { label: '接通中', type: '', className: 'tag-status--processing' },
  '1': { label: '已完成', type: 'success', className: '' },
  '2': { label: '已转接', type: 'warning', className: '' },
  '3': { label: '未接', type: 'danger', className: '' }
}

function pick(map, status) {
  return map[String(status)] || UNKNOWN
}

export function ticketStatus(status) {
  return pick(TICKET_STATUS, status)
}

export function ticketStatusLabel(status) {
  return ticketStatus(status).label
}

export function ticketStatusType(status) {
  return ticketStatus(status).type
}

export function ticketStatusClass(status) {
  return ticketStatus(status).className
}

export function callRecordStatus(status) {
  return pick(CALL_RECORD_STATUS, status)
}

export function callRecordStatusLabel(status) {
  return callRecordStatus(status).label
}

export function callRecordStatusType(status) {
  return callRecordStatus(status).type
}

export function callRecordStatusClass(status) {
  return callRecordStatus(status).className
}
