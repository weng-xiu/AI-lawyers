'use strict'

/**
 * 调度日志单测：锁定按任务过滤加载、详情弹窗、多选、删除/清空、导出与返回。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import JobLog from '@/views/monitor/job/log.vue'
import { addDateRange } from '@/utils/ruoyi'
import { getJob } from '@/api/monitor/job'
import { listJobLog, delJobLog, cleanJobLog } from '@/api/monitor/jobLog'

jest.mock('@/api/monitor/job', () => ({ getJob: jest.fn() }))
jest.mock('@/api/monitor/jobLog', () => ({
  listJobLog: jest.fn(),
  delJobLog: jest.fn(),
  cleanJobLog: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.addDateRange = addDateRange
localVue.prototype.parseTime = value => value
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_job_group: [
      { label: '系统', value: 'SYSTEM' },
      { label: '业务', value: 'BUSINESS' }
    ],
    sys_common_status: [
      { label: '成功', value: '0' },
      { label: '失败', value: '1' }
    ]
  }
}

const rows = [
  {
    jobLogId: 1,
    jobName: '同步任务',
    jobGroup: 'SYSTEM',
    invokeTarget: 'ryTask.sync()',
    jobMessage: '执行成功',
    status: '0',
    createTime: '2026-01-01 10:00:00'
  },
  {
    jobLogId: 2,
    jobName: '清理任务',
    jobGroup: 'BUSINESS',
    invokeTarget: 'ryTask.clean()',
    jobMessage: '执行失败',
    status: '1',
    createTime: '2026-01-02 10:00:00',
    exceptionInfo: '数据库连接超时'
  }
]

function mountPage(params = {}) {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const closeOpenPage = jest.fn()
  const wrapper = mount(JobLog, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: {
      $modal: modal,
      download,
      dict,
      $route: { params },
      $tab: { closeOpenPage }
    }
  })
  return { wrapper, modal, download, closeOpenPage }
}

describe('JobLog 调度日志', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listJobLog.mockResolvedValue({ rows, total: rows.length })
    getJob.mockResolvedValue({ data: { jobName: '同步任务', jobGroup: 'SYSTEM' } })
    delJobLog.mockResolvedValue({})
    cleanJobLog.mockResolvedValue({})
  })

  test('无任务编号时直接查询全部日志', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getJob).not.toHaveBeenCalled()
    expect(listJobLog).toHaveBeenCalledTimes(1)
    expect(listJobLog.mock.calls[0][0]).toEqual(expect.objectContaining({
      pageNum: 1,
      pageSize: 10,
      params: { beginTime: undefined, endTime: undefined }
    }))
    expect(wrapper.vm.jobLogList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('同步任务')
  })

  test('带任务编号时先取任务信息再按任务过滤查询', async () => {
    const { wrapper } = mountPage({ jobId: '5' })
    await flush()

    expect(getJob).toHaveBeenCalledWith('5')
    expect(wrapper.vm.queryParams.jobName).toBe('同步任务')
    expect(wrapper.vm.queryParams.jobGroup).toBe('SYSTEM')
    expect(listJobLog.mock.calls[0][0]).toEqual(expect.objectContaining({
      jobName: '同步任务',
      jobGroup: 'SYSTEM'
    }))
  })

  test('查询重置页码，重置清空日期范围', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 3
    wrapper.vm.dateRange = ['2026-01-01', '2026-01-02']

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listJobLog).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(wrapper.vm.dateRange).toEqual([])
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
  })

  test('多选联动 ids 与批量禁用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[0]])
    expect(wrapper.vm.ids).toEqual([1])
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.ids).toEqual([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('查看详情展示调用方法与异常信息', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleView(rows[1])
    await wrapper.vm.$nextTick()

    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.form.exceptionInfo).toBe('数据库连接超时')
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('visible')).toBe(true)
  })

  test('删除选中日志并刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange(rows)
    wrapper.vm.handleDelete()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 2, countNoun: '条日志' }))
    expect(delJobLog).toHaveBeenCalledWith([1, 2])
    expect(listJobLog).toHaveBeenCalledTimes(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
  })

  test('清空日志与导出分别调用对应能力', async () => {
    const { wrapper, modal, download } = mountPage()
    await flush()

    wrapper.vm.handleClean()
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ actionType: 'clear' }))
    expect(cleanJobLog).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('清空成功')

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      '/monitor/jobLog/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^log_\d+\.xlsx$/)
    )
  })

  test('关闭按钮返回定时任务列表页', async () => {
    const { wrapper, closeOpenPage } = mountPage()
    await flush()

    wrapper.vm.handleClose()

    expect(closeOpenPage).toHaveBeenCalledWith({ path: '/monitor/job' })
  })

  test('取消确认时不执行删除与清空', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))

    wrapper.vm.ids = [1]
    wrapper.vm.handleDelete()
    wrapper.vm.handleClean()
    await flush()

    expect(delJobLog).not.toHaveBeenCalled()
    expect(cleanJobLog).not.toHaveBeenCalled()
  })
})
