'use strict'

/**
 * V7 状态标签语义色单测：锁定工单/通话状态 → el-tag type + 自定义 class 的映射，
 * 防止工单列表、来电弹屏、工作台、AI 辅助面板再次出现同一状态不同颜色。
 */
import {
  TICKET_STATUS,
  CALL_RECORD_STATUS,
  ticketStatus,
  ticketStatusLabel,
  ticketStatusType,
  ticketStatusClass,
  callRecordStatus,
  callRecordStatusLabel,
  callRecordStatusType,
  callRecordStatusClass
} from '@/utils/statusTag'

describe('statusTag 工单状态', () => {
  test('四态语义映射符合配色.md：待处理橙 / 处理中信息蓝 / 已完成绿 / 已归档灰', () => {
    expect(TICKET_STATUS['0']).toEqual({ label: '待处理', type: 'warning', className: '' })
    expect(TICKET_STATUS['1']).toEqual({ label: '处理中', type: '', className: 'tag-status--processing' })
    expect(TICKET_STATUS['2']).toEqual({ label: '已完成', type: 'success', className: '' })
    expect(TICKET_STATUS['3']).toEqual({ label: '已归档', type: 'info', className: '' })
  })

  test('字符串与数字状态值等价', () => {
    expect(ticketStatusLabel(1)).toBe('处理中')
    expect(ticketStatusType('1')).toBe('')
    expect(ticketStatusClass(1)).toBe('tag-status--processing')
  })

  test('未知状态回退为中性 info，不再出现非法 type=primary', () => {
    expect(ticketStatus('9')).toEqual({ label: '未知', type: 'info', className: '' })
    expect(ticketStatus(null).type).toBe('info')
  })
})

describe('statusTag 通话记录状态', () => {
  test('接通中为信息蓝，其余走内置语义色', () => {
    expect(CALL_RECORD_STATUS['0']).toEqual({ label: '接通中', type: '', className: 'tag-status--processing' })
    expect(CALL_RECORD_STATUS['1']).toEqual({ label: '已完成', type: 'success', className: '' })
    expect(CALL_RECORD_STATUS['2']).toEqual({ label: '已转接', type: 'warning', className: '' })
    expect(CALL_RECORD_STATUS['3']).toEqual({ label: '未接', type: 'danger', className: '' })
  })

  test('辅助函数与映射保持一致', () => {
    expect(callRecordStatusLabel(0)).toBe('接通中')
    expect(callRecordStatusType('0')).toBe('')
    expect(callRecordStatusClass('0')).toBe('tag-status--processing')
    expect(callRecordStatus(9).label).toBe('未知')
  })
})
