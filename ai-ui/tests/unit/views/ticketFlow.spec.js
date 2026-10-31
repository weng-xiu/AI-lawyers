'use strict'

/**
 * F2 工单流程可视化编排单测：
 * 边几何（顺向/自环/逆向/停用虚线）、未知状态提示、拖拽连线、表格过滤分页、发布前校验。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TicketFlow from '@/views/lawyers/callCenter/ticketFlow.vue'
import { listTicketFlow, validateTicketFlow } from '@/api/lawyers/ticketFlow'

jest.mock('@/api/lawyers/ticketFlow', () => ({
  listTicketFlow: jest.fn(),
  getTicketFlow: jest.fn(),
  addTicketFlow: jest.fn(),
  updateTicketFlow: jest.fn(),
  validateTicketFlow: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)
// 页面使用 v-hasPermi 权限指令，单测只关心渲染逻辑，注册空指令
localVue.directive('hasPermi', {})

const flushPromises = () => new Promise(resolve => setTimeout(resolve, 0))

const modal = {
  msgSuccess: jest.fn(),
  msgError: jest.fn(),
  msgWarning: jest.fn()
}

function rule(overrides = {}) {
  return Object.assign({
    flowId: 1,
    flowCode: 'HOTLINE',
    statusFrom: '0',
    actionCode: 'start',
    actionName: '受理',
    targetStatus: '1',
    roleKey: 'agent',
    status: '0'
  }, overrides)
}

function mountPage(rules = []) {
  listTicketFlow.mockResolvedValue({ data: rules })
  return mount(TicketFlow, {
    localVue,
    stubs: { ProTable: { template: '<div class="pro-table-stub" />' } },
    mocks: { $modal: modal }
  })
}

describe('TicketFlow F2 画布', () => {
  let wrapper

  beforeEach(() => {
    jest.clearAllMocks()
    jest.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    if (wrapper) wrapper.destroy()
    jest.restoreAllMocks()
  })

  it('顺向/自环/逆向生成不同几何，停用规则标记为虚线', async () => {
    wrapper = mountPage([
      rule({ flowId: 1, statusFrom: '0', targetStatus: '1', actionName: '受理' }),
      rule({ flowId: 2, statusFrom: '1', targetStatus: '1', actionName: '处理记录' }),
      rule({ flowId: 3, statusFrom: '3', targetStatus: '2', actionName: '重新打开', status: '1' })
    ])
    await flushPromises()

    const edges = wrapper.vm.canvasEdges
    expect(edges).toHaveLength(3)
    expect(edges.map(e => e.label)).toEqual(['受理', '处理记录', '重新打开'])
    expect(edges[0].path.startsWith('M ')).toBe(true)

    const node = wrapper.vm.nodeByStatus('1')
    // 自环标签绘制在节点上方
    expect(edges[1].labelY).toBeLessThan(node.y)
    // 逆向边绕到节点下方，标签在节点底边之下
    expect(edges[2].labelY).toBeGreaterThan(node.y + wrapper.vm.nodeHeight)
    expect(edges[2].disabled).toBe(true)
    expect(edges[0].disabled).toBe(false)
    // key 唯一
    expect(new Set(edges.map(e => e.key)).size).toBe(3)
  })

  it('未知状态规则不绘制连线，并给出提示数据', async () => {
    wrapper = mountPage([
      rule({ flowId: 1, statusFrom: '0', targetStatus: '1' }),
      rule({ flowId: 2, statusFrom: '9', targetStatus: '2', actionCode: 'resume' })
    ])
    await flushPromises()

    expect(wrapper.vm.canvasEdges).toHaveLength(1)
    expect(wrapper.vm.unknownRules).toHaveLength(1)
    expect(wrapper.vm.unknownStatuses).toEqual(['9'])
  })

  it('命中检测覆盖节点矩形与留白，矩形外返回 null', async () => {
    wrapper = mountPage([])
    await flushPromises()

    expect(wrapper.vm.hitStatus({ x: 45, y: 145 })).toBe('0')
    expect(wrapper.vm.hitStatus({ x: 320, y: 180 })).toBe('1')
    expect(wrapper.vm.hitStatus({ x: 100, y: 40 })).toBe(null)
    expect(wrapper.vm.hitStatus({ x: 500, y: 300 })).toBe(null)
  })

  it('从源节点拖到目标节点后打开新增弹窗并预填状态', async () => {
    wrapper = mountPage([])
    await flushPromises()

    const source = wrapper.vm.nodeByStatus('0')
    wrapper.vm.startLink(source)
    expect(wrapper.vm.linkingFrom).toBe('0')

    wrapper.vm.linkTarget = '2'
    wrapper.vm.onLinkEnd()

    expect(wrapper.vm.linkingFrom).toBe(null)
    expect(wrapper.vm.open).toBe(true)
    expect(wrapper.vm.isEdit).toBe(false)
    expect(wrapper.vm.form.statusFrom).toBe('0')
    expect(wrapper.vm.form.targetStatus).toBe('2')
    expect(wrapper.vm.form.flowCode).toBe('HOTLINE')
    expect(wrapper.vm.title).toContain('新增动作')
  })

  it('释放到空白处不打开弹窗', async () => {
    wrapper = mountPage([])
    await flushPromises()

    wrapper.vm.startLink(wrapper.vm.nodeByStatus('0'))
    wrapper.vm.linkTarget = null
    wrapper.vm.onLinkEnd()

    expect(wrapper.vm.open).toBe(false)
  })

  it('表格视图按状态/动作/启用状态过滤并本地分页', async () => {
    wrapper = mountPage([
      rule({ flowId: 1, statusFrom: '0', actionCode: 'start' }),
      rule({ flowId: 2, statusFrom: '1', targetStatus: '2', actionCode: 'complete', status: '1' }),
      rule({ flowId: 3, statusFrom: '1', targetStatus: '2', actionCode: 'close' })
    ])
    await flushPromises()

    wrapper.vm.queryParams.statusFrom = '1'
    expect(wrapper.vm.tableList).toHaveLength(2)

    wrapper.vm.queryParams.actionCode = 'complete'
    expect(wrapper.vm.tableList).toHaveLength(1)

    wrapper.vm.queryParams.actionCode = ''
    wrapper.vm.queryParams.status = '0'
    expect(wrapper.vm.tableList).toHaveLength(1)

    wrapper.vm.queryParams.statusFrom = ''
    wrapper.vm.queryParams.status = ''
    wrapper.vm.queryParams.pageSize = 2
    expect(wrapper.vm.pagedList).toHaveLength(2)
    wrapper.vm.queryParams.pageNum = 2
    expect(wrapper.vm.pagedList).toHaveLength(1)
  })

  it('发布前校验：展示后端返回的错误与提示', async () => {
    wrapper = mountPage([])
    await flushPromises()

    validateTicketFlow.mockResolvedValue({
      data: {
        flowCode: 'HOTLINE',
        valid: false,
        checked: 2,
        errors: ['状态「1」没有出向动作，工单将卡死在该状态'],
        warnings: ['仅 admin 可执行']
      }
    })

    await wrapper.vm.runValidate()
    await flushPromises()

    expect(validateTicketFlow).toHaveBeenCalledWith('HOTLINE')
    expect(wrapper.vm.validateOpen).toBe(true)
    expect(wrapper.vm.validation.valid).toBe(false)
    expect(wrapper.vm.validation.errors).toHaveLength(1)
    expect(wrapper.vm.validation.warnings).toHaveLength(1)
  })

  it('校验接口异常时提示失败且不打开结果弹窗', async () => {
    wrapper = mountPage([])
    await flushPromises()

    validateTicketFlow.mockRejectedValue(new Error('network'))
    await wrapper.vm.runValidate()
    await flushPromises()

    expect(wrapper.vm.validateOpen).toBe(false)
    expect(modal.msgError).toHaveBeenCalled()
  })
})
