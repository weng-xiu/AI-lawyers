'use strict'

/**
 * F2 工单流程可视化编排单测：
 * 边几何（顺向/自环/逆向/停用虚线）、未知状态提示、拖拽连线、表格过滤分页、发布前校验，
 * 以及节点自由拖拽布局的边界收敛、回显合并、松手落库与重置。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import TicketFlow from '@/views/lawyers/callCenter/ticketFlow.vue'
import { listTicketFlow, validateTicketFlow, getTicketFlowLayout, saveTicketFlowLayout } from '@/api/lawyers/ticketFlow'

jest.mock('@/api/lawyers/ticketFlow', () => ({
  listTicketFlow: jest.fn(),
  getTicketFlow: jest.fn(),
  addTicketFlow: jest.fn(),
  updateTicketFlow: jest.fn(),
  validateTicketFlow: jest.fn(),
  getTicketFlowLayout: jest.fn(),
  saveTicketFlowLayout: jest.fn()
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
    // created 会拉取布局；默认无布局，节点使用默认排布
    getTicketFlowLayout.mockResolvedValue({ data: [] })
    saveTicketFlowLayout.mockResolvedValue({})
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

  // ===== F2：节点自由拖拽布局持久化 =====

  it('created 按状态回显已保存布局，未保存节点保持默认坐标', async () => {
    getTicketFlowLayout.mockResolvedValue({
      data: [
        { statusCode: '0', posX: 60, posY: 180 },
        { statusCode: '2', posX: 620, posY: 120 }
      ]
    })
    wrapper = mountPage([])
    await flushPromises()

    expect(getTicketFlowLayout).toHaveBeenCalledWith('HOTLINE')
    const n0 = wrapper.vm.nodeByStatus('0')
    expect(n0.x).toBe(60)
    expect(n0.y).toBe(180)
    const n2 = wrapper.vm.nodeByStatus('2')
    expect(n2.x).toBe(620)
    expect(n2.y).toBe(120)
    // 服务端未保存的状态保持默认排布
    expect(wrapper.vm.nodeByStatus('1').x).toBe(300)
    expect(wrapper.vm.nodeByStatus('1').y).toBe(140)
  })

  it('拖拽移动被画布边界收敛，松手后一次性保存全部节点', async () => {
    wrapper = mountPage([])
    await flushPromises()

    const node = wrapper.vm.nodeByStatus('0')
    wrapper.vm.startNodeDrag(node, { clientX: 100, clientY: 100 })
    wrapper.vm.onNodeDragMove({ clientX: -900, clientY: 900 })
    expect(node.x).toBe(20)
    expect(node.y).toBe(250)

    wrapper.vm.onNodeDragMove({ clientX: 1200, clientY: -500 })
    expect(node.x).toBe(wrapper.vm.canvasWidth - wrapper.vm.nodeWidth - 20)
    expect(node.y).toBe(100)

    wrapper.vm.onNodeDragEnd()
    await flushPromises()

    expect(saveTicketFlowLayout).toHaveBeenCalledTimes(1)
    const [flow, payload] = saveTicketFlowLayout.mock.calls[0]
    expect(flow).toBe('HOTLINE')
    expect(payload).toHaveLength(4)
    expect(payload.find(p => p.statusCode === '0')).toEqual({
      statusCode: '0',
      posX: wrapper.vm.canvasWidth - wrapper.vm.nodeWidth - 20,
      posY: 100
    })
    expect(modal.msgSuccess).toHaveBeenCalled()
  })

  it('单击节点未产生位移时不写库', async () => {
    wrapper = mountPage([])
    await flushPromises()

    wrapper.vm.startNodeDrag(wrapper.vm.nodeByStatus('1'), { clientX: 300, clientY: 140 })
    wrapper.vm.onNodeDragEnd()
    await flushPromises()

    expect(saveTicketFlowLayout).not.toHaveBeenCalled()
  })

  it('重置布局恢复默认坐标并落库', async () => {
    getTicketFlowLayout.mockResolvedValue({
      data: [{ statusCode: '0', posX: 10, posY: 200 }]
    })
    wrapper = mountPage([])
    await flushPromises()

    // 越界坐标回显时被收敛到最小边界
    expect(wrapper.vm.nodeByStatus('0').x).toBe(20)

    wrapper.vm.resetLayout()
    await flushPromises()

    expect(wrapper.vm.nodeByStatus('0').x).toBe(40)
    expect(wrapper.vm.nodeByStatus('0').y).toBe(140)
    expect(saveTicketFlowLayout).toHaveBeenCalledTimes(1)
    expect(saveTicketFlowLayout.mock.calls[0][1][0]).toEqual({ statusCode: '0', posX: 40, posY: 140 })
  })
})
