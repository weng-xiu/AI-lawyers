'use strict'

/**
 * 未接来电单测：锁定统计卡/回拨率、列表三态、标签页按需加载、回拨标记、
 * 语音留言播放与转工单、漏话通知批量发送，以及未接来电的增删改与导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import MissedCall from '@/views/lawyers/callCenter/missedCall.vue'
import { addDateRange } from '@/utils/ruoyi'
import {
  listMissedCall, getMissedCall, addMissedCall, updateMissedCall, delMissedCall,
  getMissedCallStats, callbackMissedCall, fetchVoiceBlob, transferVoiceToTicket
} from '@/api/lawyers/missedCall'

jest.mock('@/api/lawyers/missedCall', () => ({
  listMissedCall: jest.fn(),
  getMissedCall: jest.fn(),
  addMissedCall: jest.fn(),
  updateMissedCall: jest.fn(),
  delMissedCall: jest.fn(),
  getMissedCallStats: jest.fn(),
  callbackMissedCall: jest.fn(),
  fetchVoiceBlob: jest.fn(),
  transferVoiceToTicket: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.resetForm = jest.fn()
localVue.prototype.download = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { missedCallId: 1, callerNumber: '13800000001', callerName: '张三', callTime: '2026-11-20 09:00:00', status: '0', remark: '' },
  { missedCallId: 2, callerNumber: '13800000002', callerName: '李四', callTime: '2026-11-20 10:00:00', status: '1', callbackTime: '2026-11-20 10:10:00', callbackBy: '王坐席' }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const message = {
    success: jest.fn(),
    warning: jest.fn(),
    info: jest.fn()
  }
  const confirm = jest.fn(() => Promise.resolve())
  const wrapper = mount(MissedCall, {
    localVue,
    mocks: { $modal: modal, $message: message, $confirm: confirm }
  })
  return { wrapper, modal, message, confirm }
}

describe('MissedCall 未接来电', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    localStorage.clear()
    getMissedCallStats.mockResolvedValue({ data: { todayMissed: 3, weekMissed: 9, callbacked: 2, totalCount: 10 } })
    listMissedCall.mockResolvedValue({ rows, total: 2 })
    getMissedCall.mockResolvedValue({ data: { ...rows[0] } })
    addMissedCall.mockResolvedValue({})
    updateMissedCall.mockResolvedValue({})
    delMissedCall.mockResolvedValue({})
    callbackMissedCall.mockResolvedValue({})
    fetchVoiceBlob.mockResolvedValue({ size: 1 })
    transferVoiceToTicket.mockResolvedValue({ data: 'GD20261121' })
    window.URL.createObjectURL = jest.fn(() => 'blob:mock')
    window.URL.revokeObjectURL = jest.fn()
  })

  test('初始化加载统计与未接来电列表，回拨率按百分比格式化', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getMissedCallStats).toHaveBeenCalledTimes(1)
    expect(listMissedCall).toHaveBeenCalledWith(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.missedList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.stats.todayMissed).toBe(3)
    expect(wrapper.vm.callbackRate).toBe('20.0')
    expect(wrapper.text()).toContain('13800000001')

    wrapper.vm.stats = { totalCount: 0 }
    expect(wrapper.vm.callbackRate).toBe(0)
  })

  test('列表加载失败切换错误重试态并结束加载', async () => {
    listMissedCall.mockRejectedValue(new Error('network'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.loadError).toBe(true)
    expect(wrapper.vm.loading).toBe(false)
  })

  test('切换标签页按需加载语音留言与漏话通知', async () => {
    const { wrapper } = mountPage()
    await flush()

    listMissedCall.mockResolvedValue({
      rows: [
        { missedCallId: 3, callerNumber: '13800000003', voiceContent: '请回电' },
        { missedCallId: 4, callerNumber: '13800000004' },
        { missedCallId: 5, callerNumber: '13800000005', voiceFileUrl: '/a.mp3' }
      ],
      total: 3
    })
    wrapper.vm.handleTabClick({ name: 'voice' })
    await flush()

    expect(listMissedCall).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 100 })
    expect(wrapper.vm.voiceList.map(v => v.missedCallId)).toEqual([3, 5])

    wrapper.vm.noticeQuery.pageNum = 3
    listMissedCall.mockResolvedValue({ rows: [], total: 0 })
    wrapper.vm.handleTabClick({ name: 'notice' })
    await flush()

    expect(wrapper.vm.noticeQuery.pageNum).toBe(1)
    expect(listMissedCall).toHaveBeenLastCalledWith({ pageNum: 1, pageSize: 10 })
    expect(wrapper.vm.noticeError).toBe(false)
  })

  test('漏话通知加载失败切换独立错误态', async () => {
    const { wrapper } = mountPage()
    await flush()

    listMissedCall.mockRejectedValue(new Error('network'))
    wrapper.vm.getNoticeList()
    await flush()

    expect(wrapper.vm.noticeError).toBe(true)
    expect(wrapper.vm.loading).toBe(false)
  })

  test('多选映射主键并驱动批量通知', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([{ missedCallId: 1 }, { missedCallId: 2 }])
    expect(wrapper.vm.ids).toEqual([1, 2])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.multiple).toBe(true)

    wrapper.vm.handleSelectionChange([{ missedCallId: 1 }, { missedCallId: 2 }])
    wrapper.vm.handleBatchNotice()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 2, countNoun: '条记录' }))
    expect(updateMissedCall).toHaveBeenCalledTimes(2)
    expect(updateMissedCall).toHaveBeenCalledWith({ missedCallId: 1, noticeStatus: '1', noticeChannel: '1' })
    expect(message.success).toHaveBeenCalledWith('批量通知已发送')

    updateMissedCall.mockClear()
    wrapper.vm.ids = []
    wrapper.vm.handleBatchNotice()
    expect(updateMissedCall).not.toHaveBeenCalled()
  })

  test('回拨标记成功后按当前标签页刷新数据', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    wrapper.vm.activeTab = 'voice'
    const loadVoiceList = jest.spyOn(wrapper.vm, 'loadVoiceList').mockImplementation(() => {})
    const loadStats = jest.spyOn(wrapper.vm, 'loadStats').mockImplementation(() => {})
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleCallback(rows[0])
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: expect.stringContaining('13800000001')
    }))
    expect(callbackMissedCall).toHaveBeenCalledWith(1)
    expect(message.success).toHaveBeenCalledWith('回拨标记成功')
    expect(loadStats).toHaveBeenCalled()
    expect(getList).toHaveBeenCalled()
    expect(loadVoiceList).toHaveBeenCalled()

    wrapper.vm.activeTab = 'notice'
    const getNoticeList = jest.spyOn(wrapper.vm, 'getNoticeList').mockImplementation(() => {})
    wrapper.vm.handleCallback(rows[0])
    await flush()
    expect(getNoticeList).toHaveBeenCalled()
  })

  test('语音留言播放拉取 blob，重复点击停止并释放资源', async () => {
    const { wrapper } = mountPage()
    await flush()
    const row = { missedCallId: 5, voiceFileUrl: '/a.mp3' }

    wrapper.vm.handlePlayVoice(row)
    await flush()

    expect(fetchVoiceBlob).toHaveBeenCalledWith(5)
    expect(window.URL.createObjectURL).toHaveBeenCalled()
    expect(wrapper.vm.playingId).toBe(5)
    expect(wrapper.vm.voiceAudioUrl).toBe('blob:mock')
    expect(wrapper.vm.voiceLoading).toBe(false)

    wrapper.vm.handlePlayVoice(row)
    expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
    expect(wrapper.vm.playingId).toBeNull()
    expect(wrapper.vm.voiceAudioUrl).toBe('')
    expect(fetchVoiceBlob).toHaveBeenCalledTimes(1)
  })

  test('语音文件读取失败给出提示并复位播放状态', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    fetchVoiceBlob.mockRejectedValue(new Error('404'))

    wrapper.vm.handlePlayVoice({ missedCallId: 6 })
    await flush()

    expect(message.warning).toHaveBeenCalledWith('语音文件不存在或读取失败')
    expect(wrapper.vm.playingId).toBeNull()
    expect(wrapper.vm.voiceLoading).toBe(false)

    wrapper.vm.handleVoiceEnded()
    expect(wrapper.vm.playingId).toBeNull()
  })

  test('语音留言转工单：已转提示与确认后提交', async () => {
    const { wrapper, message, confirm } = mountPage()
    await flush()

    wrapper.vm.handleTransfer({ missedCallId: 5, callerNumber: '13800000005', remark: '已转工单#9' })
    expect(message.info).toHaveBeenCalledWith(expect.stringContaining('请勿重复提交'))
    expect(transferVoiceToTicket).not.toHaveBeenCalled()

    wrapper.vm.handleTransfer({ missedCallId: 5, callerNumber: '13800000005', remark: '' })
    await flush()

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('13800000005'), '转工单确认', { type: 'warning' })
    expect(transferVoiceToTicket).toHaveBeenCalledWith(5)
    expect(message.success).toHaveBeenCalledWith('转工单成功，工单号：GD20261121')
  })

  test('发送单条漏话通知并刷新通知列表', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    const getNoticeList = jest.spyOn(wrapper.vm, 'getNoticeList').mockImplementation(() => {})

    wrapper.vm.handleNotice({ missedCallId: 1 })
    await flush()

    expect(updateMissedCall).toHaveBeenCalledWith({ missedCallId: 1, noticeStatus: '1', noticeChannel: '1' })
    expect(message.success).toHaveBeenCalledWith('通知已发送')
    expect(getNoticeList).toHaveBeenCalled()

    expect(wrapper.vm.channelText('1')).toBe('短信')
    expect(wrapper.vm.channelText('2')).toBe('微信')
    expect(wrapper.vm.channelText('9')).toBe('未设置')
  })

  test('查询重置页码，重置清空日期范围后重查', async () => {
    const { wrapper } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.queryParams.pageNum = 4
    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(getList).toHaveBeenCalledTimes(1)

    wrapper.vm.dateRange = ['2026-11-01', '2026-11-02']
    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(getList).toHaveBeenCalledTimes(2)
  })

  test('新增/修改/删除/导出未接来电', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()
    const getList = jest.spyOn(wrapper.vm, 'getList').mockImplementation(() => {})

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加未接来电')
    await wrapper.vm.$nextTick()
    wrapper.vm.form.callerNumber = '13900000000'
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.submitForm()
    await flush()

    expect(addMissedCall).toHaveBeenCalledWith(expect.objectContaining({ callerNumber: '13900000000' }))
    expect(message.success).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)
    expect(getList).toHaveBeenCalled()

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getMissedCall).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改未接来电')
    expect(wrapper.vm.open).toBe(true)
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = cb => cb(true)
    wrapper.vm.form.missedCallId = 1
    wrapper.vm.submitForm()
    await flush()

    expect(updateMissedCall).toHaveBeenCalledWith(expect.objectContaining({ missedCallId: 1 }))
    expect(message.success).toHaveBeenCalledWith('修改成功')

    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '条记录' }))
    expect(delMissedCall).toHaveBeenCalledWith([1])
    expect(message.success).toHaveBeenCalledWith('删除成功')

    wrapper.vm.handleExport()
    expect(wrapper.vm.download).toHaveBeenCalledWith(
      '/lawyers/call/missed/export',
      expect.any(Object),
      expect.stringContaining('未接来电_')
    )

    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
  })
})
