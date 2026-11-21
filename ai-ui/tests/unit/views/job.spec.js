'use strict'

/**
 * 定时任务单测：锁定列表查询、任务详细、cron 生成、启停切换、立即执行、提交与删除导出。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Job from '@/views/monitor/job/index.vue'
import { selectDictLabel } from '@/utils/ruoyi'
import { listJob, getJob, delJob, addJob, updateJob, runJob, changeJobStatus } from '@/api/monitor/job'

jest.mock('@/api/monitor/job', () => ({
  listJob: jest.fn(),
  getJob: jest.fn(),
  delJob: jest.fn(),
  addJob: jest.fn(),
  updateJob: jest.fn(),
  runJob: jest.fn(),
  changeJobStatus: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.directive('hasPermi', {})
localVue.prototype.selectDictLabel = selectDictLabel
localVue.prototype.parseTime = value => (value ? '格式化时间' : null)
localVue.prototype.resetForm = jest.fn()

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const dict = {
  type: {
    sys_job_group: [
      { label: '默认', value: 'DEFAULT' },
      { label: '系统', value: 'SYSTEM' }
    ],
    sys_job_status: [
      { label: '正常', value: '0' },
      { label: '暂停', value: '1' }
    ]
  }
}

const rows = [
  {
    jobId: 1,
    jobName: '系统默认任务',
    jobGroup: 'DEFAULT',
    invokeTarget: 'ryTask.ryNoParams',
    cronExpression: '0 0 2 * * ?',
    status: '0',
    createTime: '2026-01-01'
  },
  {
    jobId: 2,
    jobName: '清理任务',
    jobGroup: 'SYSTEM',
    invokeTarget: 'ryTask.ryParams',
    cronExpression: '0 0 3 * * ?',
    status: '1',
    createTime: '2026-01-02'
  }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn()
  }
  const download = jest.fn()
  const push = jest.fn()
  const wrapper = mount(Job, {
    localVue,
    stubs: {
      pagination: true,
      'right-toolbar': true,
      crontab: true,
      'dict-tag': { props: ['options', 'value'], template: '<span class="dict-tag-stub">{{ value }}</span>' }
    },
    mocks: { $modal: modal, download, $router: { push }, dict }
  })
  return { wrapper, modal, download, push }
}

describe('Job 定时任务', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listJob.mockResolvedValue({ rows, total: rows.length })
    getJob.mockResolvedValue({ data: { ...rows[0], nextValidTime: '2026-01-03' } })
    delJob.mockResolvedValue({})
    addJob.mockResolvedValue({})
    updateJob.mockResolvedValue({})
    runJob.mockResolvedValue({})
    changeJobStatus.mockResolvedValue({})
  })

  test('创建时查询任务列表并翻译任务组名', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listJob).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(wrapper.vm.jobList).toHaveLength(2)
    expect(wrapper.vm.total).toBe(2)
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('系统默认任务')
    expect(wrapper.text()).toContain('清理任务')
    expect(wrapper.vm.jobGroupFormat(rows[0])).toBe('默认')
    expect(wrapper.vm.jobGroupFormat({ jobGroup: 'UNKNOWN' })).toBe('UNKNOWN')
  })

  test('查询重置页码，重置表单后重新查询', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.queryParams.pageNum = 4

    wrapper.vm.handleQuery()
    expect(wrapper.vm.queryParams.pageNum).toBe(1)
    expect(listJob).toHaveBeenCalledTimes(2)

    wrapper.vm.resetQuery()
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('queryForm')
    expect(listJob).toHaveBeenCalledTimes(3)
  })

  test('多选联动 ids 与按钮禁用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleSelectionChange([rows[1]])
    expect(wrapper.vm.ids).toEqual([2])
    expect(wrapper.vm.single).toBe(false)
    expect(wrapper.vm.multiple).toBe(false)

    wrapper.vm.handleSelectionChange([rows[0], rows[1]])
    expect(wrapper.vm.single).toBe(true)

    wrapper.vm.handleSelectionChange([])
    expect(wrapper.vm.multiple).toBe(true)
  })

  test('新增初始化表单，修改读取任务详情', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('添加任务')
    expect(wrapper.vm.form).toEqual(expect.objectContaining({
      misfirePolicy: 1,
      concurrent: 1,
      status: '0'
    }))

    wrapper.vm.handleUpdate(rows[0])
    await flush()
    expect(getJob).toHaveBeenCalledWith(1)
    expect(wrapper.vm.title).toBe('修改任务')
    expect(wrapper.vm.form.jobName).toBe('系统默认任务')
    expect(wrapper.vm.open).toBe(true)
  })

  test('任务详细与 cron 表达式回填', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleView(rows[0])
    await flush()
    expect(getJob).toHaveBeenCalledWith(1)
    expect(wrapper.vm.openView).toBe(true)
    expect(wrapper.vm.form.nextValidTime).toBe('2026-01-03')

    wrapper.vm.form.cronExpression = '0 0 2 * * ?'
    wrapper.vm.handleShowCron()
    expect(wrapper.vm.openCron).toBe(true)
    expect(wrapper.vm.expression).toBe('0 0 2 * * ?')

    wrapper.vm.crontabFill('0 0/5 * * * ?')
    expect(wrapper.vm.form.cronExpression).toBe('0 0/5 * * * ?')
  })

  test('状态开关确认后调用接口，取消时回滚开关值', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const row = { ...rows[0], status: '0' }

    wrapper.vm.handleStatusChange(row)
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      actionType: 'caution',
      content: '确认要"启用"任务「系统默认任务」吗？'
    }))
    expect(changeJobStatus).toHaveBeenCalledWith(1, '0')
    expect(modal.msgSuccess).toHaveBeenCalledWith('启用成功')

    const row2 = { ...rows[0], status: '0' }
    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleStatusChange(row2)
    await flush()
    expect(changeJobStatus).toHaveBeenCalledTimes(1)
    expect(row2.status).toBe('1')
  })

  test('立即执行确认后触发，取消不执行', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleRun(rows[1])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '确认要立即执行一次「清理任务」任务吗？'
    }))
    expect(runJob).toHaveBeenCalledWith(2, 'SYSTEM')
    expect(modal.msgSuccess).toHaveBeenCalledWith('执行成功')

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleRun(rows[1])
    await flush()
    expect(runJob).toHaveBeenCalledTimes(1)
  })

  test('更多操作按指令分发到执行、详细与日志', async () => {
    const { wrapper, push } = mountPage()
    await flush()

    wrapper.vm.handleCommand('handleView', rows[0])
    await flush()
    expect(wrapper.vm.openView).toBe(true)

    wrapper.vm.handleCommand('handleRun', rows[0])
    await flush()
    expect(runJob).toHaveBeenCalledWith(1, 'DEFAULT')

    wrapper.vm.handleCommand('handleJobLog', rows[1])
    expect(push).toHaveBeenCalledWith('/monitor/job-log/index/2')

    wrapper.vm.handleJobLog({})
    expect(push).toHaveBeenLastCalledWith('/monitor/job-log/index/0')

    wrapper.vm.handleCommand('unknown', rows[0])
    expect(push).toHaveBeenCalledTimes(2)
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = {
      jobName: '对账任务',
      jobGroup: 'SYSTEM',
      invokeTarget: 'ryTask.ryNoParams',
      cronExpression: '0 0 1 * * ?',
      misfirePolicy: 1,
      concurrent: 1,
      status: '0'
    }
    wrapper.vm.submitForm()
    await flush()
    expect(addJob).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('新增成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { ...rows[0] }
    wrapper.vm.submitForm()
    await flush()
    expect(updateJob).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('修改成功')
  })

  test('删除单行与批量删除按数量提示，取消确认不删除', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[1])
    await flush()
    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({ count: 1, countNoun: '个任务' }))
    expect(delJob).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
    expect(listJob).toHaveBeenCalledTimes(2)

    wrapper.vm.ids = [1, 2]
    wrapper.vm.handleDelete({})
    await flush()
    expect(delJob).toHaveBeenLastCalledWith([1, 2])
    expect(modal.dangerConfirm).toHaveBeenLastCalledWith(expect.objectContaining({ count: 2 }))

    modal.dangerConfirm.mockImplementation(() => Promise.reject(new Error('cancel')))
    wrapper.vm.handleDelete(rows[0])
    await flush()
    expect(delJob).toHaveBeenCalledTimes(2)
  })

  test('取消关闭弹窗重置表单并支持导出', async () => {
    const { wrapper, download } = mountPage()
    await flush()
    wrapper.vm.open = true
    wrapper.vm.form = { jobName: '临时任务' }

    wrapper.vm.cancel()
    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.status).toBe('0')
    expect(localVue.prototype.resetForm).toHaveBeenCalledWith('form')

    wrapper.vm.handleExport()
    expect(download).toHaveBeenCalledWith(
      'monitor/job/export',
      expect.objectContaining({ pageNum: 1, pageSize: 10 }),
      expect.stringMatching(/^job_\d+\.xlsx$/)
    )
  })
})
