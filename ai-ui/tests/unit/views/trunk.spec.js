'use strict'

/**
 * 中继线路管理单测：锁定列表、新增编辑提交、状态开关撤销回滚、连通性/拨测与号码识别。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import ProTable from '@/components/ProTable/index.vue'
import Trunk from '@/views/lawyers/callCenter/trunk.vue'
import {
  listTrunk, addTrunk, updateTrunk, changeTrunkStatus, delTrunk, testTrunk, testCallTrunk,
  recognizeNumber, refreshSegment
} from '@/api/lawyers/trunk'

jest.mock('@/api/lawyers/trunk', () => ({
  listTrunk: jest.fn(),
  addTrunk: jest.fn(),
  updateTrunk: jest.fn(),
  changeTrunkStatus: jest.fn(),
  delTrunk: jest.fn(),
  testTrunk: jest.fn(),
  testCallTrunk: jest.fn(),
  recognizeNumber: jest.fn(),
  refreshSegment: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
localVue.component('ProTable', ProTable)
localVue.directive('hasPermi', {})

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const rows = [
  { trunkId: 1, trunkCode: 'CM-SIP-01', trunkName: '移动主线', carrier: 'CM', trunkType: 'SIP', gatewayHost: '10.0.0.1', isPrimary: '1', currentConcurrent: 3, maxConcurrent: 30, healthStatus: 1, successRate: 0.855, status: '0' },
  { trunkId: 2, trunkCode: 'CU-SIP-01', trunkName: '联通备线', carrier: 'CU', trunkType: 'PSTN', gatewayHost: '10.0.0.2', isPrimary: '0', currentConcurrent: 0, maxConcurrent: 10, healthStatus: 3, successRate: null, status: null }
]

function mountPage() {
  const modal = {
    dangerConfirm: jest.fn(() => Promise.resolve()),
    msgSuccess: jest.fn(),
    msgWarning: jest.fn(),
    msgError: jest.fn(),
    undo: jest.fn()
  }
  const wrapper = mount(Trunk, {
    localVue,
    mocks: { $modal: modal }
  })
  return { wrapper, modal }
}

describe('Trunk 中继线路管理', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    listTrunk.mockResolvedValue({ rows })
    addTrunk.mockResolvedValue({})
    updateTrunk.mockResolvedValue({})
    changeTrunkStatus.mockResolvedValue({})
    delTrunk.mockResolvedValue({})
    testTrunk.mockResolvedValue({ reachable: true, latencyMs: 20, registerState: 'Registered', message: '正常' })
    testCallTrunk.mockResolvedValue({ success: true, callUuid: 'uuid-1', dialStatus: 'RINGING' })
    recognizeNumber.mockResolvedValue({ data: { carrier: 'CM', trunkName: '移动主线' } })
    refreshSegment.mockResolvedValue({})
  })

  test('创建时查询线路并补齐默认启用状态', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(listTrunk).toHaveBeenCalledWith(wrapper.vm.queryParams)
    expect(wrapper.vm.trunkList).toHaveLength(2)
    expect(wrapper.vm.trunkList[1].status).toBe('0')
    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.text()).toContain('移动主线')
    expect(wrapper.text()).toContain('中国移动')
  })

  test('列 formatter 输出并发与接通率，空接通率显示占位', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.columns[3].formatter({ trunkType: 'SIP' })).toBe('SIP')
    expect(wrapper.vm.columns[6].formatter(rows[0])).toBe('3/30')
    expect(wrapper.vm.columns[8].formatter(rows[0])).toBe('85.5%')
    expect(wrapper.vm.columns[8].formatter(rows[1])).toBe('-')
  })

  test('查询字段与运营商/健康状态映射', () => {
    const { wrapper } = mountPage()

    const items = wrapper.vm.queryFormItems
    expect(items).toHaveLength(3)
    expect(items[1].options).toBe(wrapper.vm.carrierOptions)
    expect(items[2].options).toHaveLength(4)

    expect(wrapper.vm.carrierLabel('CM')).toBe('中国移动')
    expect(wrapper.vm.carrierLabel('XX')).toBe('XX')
    expect(wrapper.vm.carrierTagType('CT')).toBe('danger')
    expect(wrapper.vm.carrierTagType('XX')).toBe('')
    expect(wrapper.vm.healthLabel(2)).toBe('亚健康')
    expect(wrapper.vm.healthLabel(9)).toBe('未知')
    expect(wrapper.vm.healthTagType(4)).toBe('info')
    expect(wrapper.vm.healthTagType(9)).toBe('')
  })

  test('新增重置默认值，编辑回填线路数据', async () => {
    const { wrapper } = mountPage()
    await flush()

    wrapper.vm.handleAdd()
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('新增线路')
    expect(wrapper.vm.form).toEqual(expect.objectContaining({
      carrier: 'CM',
      trunkType: 'SIP',
      maxConcurrent: 30,
      priority: 100,
      weight: 100,
      isPrimary: '1',
      gatewayVendor: 'FREESWITCH'
    }))

    wrapper.vm.handleUpdate({ ...rows[0] })
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.title).toBe('编辑线路')
    expect(wrapper.vm.form.trunkCode).toBe('CM-SIP-01')
    expect(wrapper.vm.form.maxConcurrent).toBe(30)
  })

  test('提交时按是否带主键区分新增与修改', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    wrapper.vm.open = true
    await wrapper.vm.$nextTick()
    wrapper.vm.$refs.form.validate = callback => callback(true)

    wrapper.vm.form = { trunkCode: 'CT-SIP-01', trunkName: '电信主线', carrier: 'CT', trunkType: 'SIP', gatewayHost: '1.1.1.1' }
    wrapper.vm.submitForm()
    await flush()
    expect(addTrunk).toHaveBeenCalledWith(wrapper.vm.form)
    expect(modal.msgSuccess).toHaveBeenCalledWith('保存成功')
    expect(wrapper.vm.open).toBe(false)

    wrapper.vm.form = { trunkId: 1, trunkCode: 'CM-SIP-01', trunkName: '移动主线' }
    wrapper.vm.submitForm()
    await flush()
    expect(updateTrunk).toHaveBeenCalledWith(wrapper.vm.form)
  })

  test('状态切换成功后开启撤销，撤销时回写原状态', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const row = { ...rows[0], status: '0' }

    wrapper.vm.handleStatusChange(row)
    await flush()

    expect(changeTrunkStatus).toHaveBeenCalledWith({ trunkId: 1, status: '0' })
    expect(modal.undo).toHaveBeenCalledWith(expect.objectContaining({
      message: '线路已启用',
      undoSuccessText: '已撤销，线路已停用'
    }))

    const conf = modal.undo.mock.calls[0][0]
    await conf.onUndo()
    expect(changeTrunkStatus).toHaveBeenLastCalledWith({ trunkId: 1, status: '1' })
    expect(row.status).toBe('1')
  })

  test('状态切换失败回退开关并提示', async () => {
    const { wrapper, modal } = mountPage()
    await flush()
    const row = { ...rows[0], status: '1' }
    changeTrunkStatus.mockRejectedValueOnce(new Error('fail'))

    wrapper.vm.handleStatusChange(row)
    await flush()

    expect(row.status).toBe('0')
    expect(modal.msgError).toHaveBeenCalledWith('状态更新失败，已恢复原状态')
    expect(modal.undo).not.toHaveBeenCalled()
  })

  test('连通性测试与拨测号码校验', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleTest(rows[0])
    await flush()
    expect(testTrunk).toHaveBeenCalledWith(1)
    expect(wrapper.vm.healthResult.reachable).toBe(true)
    expect(wrapper.vm.healthOpen).toBe(true)

    wrapper.vm.handleCallTest(rows[0])
    expect(wrapper.vm.callTestOpen).toBe(true)
    expect(wrapper.vm.callTestForm.trunkName).toBe('移动主线')
    expect(wrapper.vm.callTestResult).toBeNull()

    wrapper.vm.submitCallTest()
    expect(modal.msgWarning).toHaveBeenCalledWith('请输入拨测号码')
    expect(testCallTrunk).not.toHaveBeenCalled()

    wrapper.vm.callTestForm.calleeNumber = '13800138000'
    wrapper.vm.submitCallTest()
    await flush()
    expect(testCallTrunk).toHaveBeenCalledWith(1, '13800138000')
    expect(wrapper.vm.callTestResult.success).toBe(true)
    expect(wrapper.vm.callTestLoading).toBe(false)
  })

  test('拨测失败恢复加载状态', async () => {
    const { wrapper } = mountPage()
    wrapper.vm.callTestForm = { trunkId: 1, calleeNumber: '13800138000' }
    testCallTrunk.mockRejectedValueOnce(new Error('fail'))

    wrapper.vm.submitCallTest()
    await flush()

    expect(wrapper.vm.callTestLoading).toBe(false)
  })

  test('号码识别校验与结果回填', async () => {
    const { wrapper, modal } = mountPage()

    wrapper.vm.handleRecognize()
    expect(modal.msgWarning).toHaveBeenCalledWith('请输入号码')

    wrapper.vm.testNumber = '13800138000'
    wrapper.vm.handleRecognize()
    await flush()
    expect(recognizeNumber).toHaveBeenCalledWith('13800138000')
    expect(wrapper.vm.recognizeResult.trunkName).toBe('移动主线')
  })

  test('刷新号段缓存提示成功', async () => {
    const { wrapper, modal } = mountPage()

    wrapper.vm.refreshSegment()
    await flush()

    expect(refreshSegment).toHaveBeenCalledTimes(1)
    expect(modal.msgSuccess).toHaveBeenCalledWith('号段缓存已刷新')
  })

  test('删除线路确认后执行并刷新列表', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.handleDelete(rows[1])
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: '将删除中继线路「联通备线」',
      count: 1,
      countNoun: '条线路'
    }))
    expect(delTrunk).toHaveBeenCalledWith(2)
    expect(modal.msgSuccess).toHaveBeenCalledWith('删除成功')
    expect(listTrunk).toHaveBeenCalledTimes(2)
  })

  test('取消关闭弹窗并重置表单', () => {
    const { wrapper } = mountPage()
    wrapper.vm.open = true
    wrapper.vm.form = { trunkCode: 'TEMP' }

    wrapper.vm.cancel()

    expect(wrapper.vm.open).toBe(false)
    expect(wrapper.vm.form.trunkCode).toBeUndefined()
    expect(wrapper.vm.form.carrier).toBe('CM')
  })
})
