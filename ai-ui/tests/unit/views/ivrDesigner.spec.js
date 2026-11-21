'use strict'

/**
 * IVR 流程图设计器单测：锁定流程/节点/连线加载映射、节点拖拽与连线绘制、
 * 删除确认、缩放、配置校验与保存载荷，以及未保存离开拦截。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import Designer from '@/views/lawyers/ivr/flow/designer.vue'
import { getFlow, saveDesign } from '@/api/lawyers/ivrFlow'
import { getNodesByFlowId } from '@/api/lawyers/ivrNode'
import { getEdgesByFlowId } from '@/api/lawyers/ivrEdge'
import { listActiveAgent } from '@/api/lawyers/agent'
import { listEnabledGroups } from '@/api/lawyers/skill'
import { listEnabledTemplates } from '@/api/lawyers/sms'

jest.mock('@/api/lawyers/ivrFlow', () => ({ getFlow: jest.fn(), saveDesign: jest.fn() }))
jest.mock('@/api/lawyers/ivrNode', () => ({ getNodesByFlowId: jest.fn() }))
jest.mock('@/api/lawyers/ivrEdge', () => ({ getEdgesByFlowId: jest.fn() }))
jest.mock('@/api/lawyers/agent', () => ({ listActiveAgent: jest.fn() }))
jest.mock('@/api/lawyers/skill', () => ({ listEnabledGroups: jest.fn() }))
jest.mock('@/api/lawyers/sms', () => ({ listEnabledTemplates: jest.fn() }))

const localVue = createLocalVue()
localVue.use(ElementUI)

const flush = () => new Promise(resolve => setTimeout(resolve, 0))

const nodeRows = [
  { nodeId: 1, nodeType: 'start', nodeName: '开始', positionX: 400, positionY: 250, nodeConfig: null },
  { nodeId: 2, nodeType: 'say', nodeName: '欢迎语', positionX: 600, positionY: 250, nodeConfig: '{"text":"你好","format":"mp3"}' },
  { nodeId: 3, nodeType: 'agent', nodeName: '转人工', positionX: 800, positionY: 250, nodeConfig: 'bad-json' }
]

const edgeRows = [
  { edgeId: 9, sourceNodeId: 1, targetNodeId: 2, edgeLabel: '是', conditionExpr: 'x>1' },
  { edgeId: 10, sourceNodeId: 2, targetNodeId: 3, edgeLabel: '', conditionExpr: 'y>0' }
]

const wrappers = []

function mountPage(query = { flowId: '5' }) {
  const modal = { dangerConfirm: jest.fn(() => Promise.resolve()), msgSuccess: jest.fn() }
  const message = { warning: jest.fn(), error: jest.fn() }
  const routerPush = jest.fn()
  const confirm = jest.fn(() => Promise.resolve())
  const wrapper = mount(Designer, {
    localVue,
    mocks: {
      $route: { query },
      $router: { push: routerPush },
      $modal: modal,
      $message: message,
      $confirm: confirm
    }
  })
  wrappers.push(wrapper)
  return { wrapper, modal, message, routerPush, confirm }
}

describe('IvrFlowDesigner IVR 流程图设计器', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    getFlow.mockResolvedValue({ data: { flowId: 5, flowName: '主流程', flowCode: 'F001', status: '1' } })
    getNodesByFlowId.mockResolvedValue({ data: nodeRows })
    getEdgesByFlowId.mockResolvedValue({ data: edgeRows })
    saveDesign.mockResolvedValue({})
    listActiveAgent.mockResolvedValue({ data: [{ agentId: 1, agentName: '智能体A' }] })
    listEnabledGroups.mockResolvedValue({ data: [{ groupId: 1, groupName: '一组' }] })
    listEnabledTemplates.mockResolvedValue({ data: [{ templateId: 1, templateName: '模板A' }] })
  })

  afterEach(() => {
    wrappers.splice(0).forEach(w => w.destroy())
  })

  test('缺少流程ID时提示并返回列表', async () => {
    const { wrapper, message, routerPush } = mountPage({ flowId: '' })
    await flush()

    expect(message.warning).toHaveBeenCalledWith('缺少流程ID参数')
    expect(routerPush).toHaveBeenCalledWith('/ivrFlow/flow')
    expect(getFlow).not.toHaveBeenCalled()
    expect(wrapper.vm.loading).toBe(false)
  })

  test('加载流程/节点/连线并合并默认配置，非法配置兜底为空', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(getFlow).toHaveBeenCalledWith(5)
    expect(getNodesByFlowId).toHaveBeenCalledWith(5)
    expect(getEdgesByFlowId).toHaveBeenCalledWith(5)
    expect(wrapper.vm.flow.flowName).toBe('主流程')
    expect(wrapper.vm.nodes).toHaveLength(3)
    expect(wrapper.vm.nodes[1].config).toEqual(expect.objectContaining({
      text: '你好',
      format: 'mp3',
      voiceEngine: '',
      sampleRate: 8000
    }))
    expect(wrapper.vm.nodes[2].config).toEqual({ dispatchMode: 'static', groupId: null, categoryVar: 'agentCategoryId', enqueueIfNoAgent: true, queue: '' })
    expect(wrapper.vm.edges[0]).toEqual({ id: 9, source: 1, target: 2, label: '是', condition: 'x>1' })
    expect(wrapper.vm.dirty).toBe(false)
    expect(wrapper.vm.loading).toBe(false)

    expect(wrapper.vm.agentOptions).toHaveLength(1)
    expect(wrapper.vm.skillGroups).toHaveLength(1)
    expect(wrapper.vm.smsTemplates).toHaveLength(1)
  })

  test('流程无节点时自动播种开始节点', async () => {
    getNodesByFlowId.mockResolvedValue({ data: [] })
    getEdgesByFlowId.mockResolvedValue({ data: [] })
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.nodes).toHaveLength(1)
    expect(wrapper.vm.nodes[0].type).toBe('start')
    expect(wrapper.vm.nodes[0].x).toBe(400)
    expect(wrapper.vm.dirty).toBe(false)
  })

  test('渲染数据加载失败提示且结束加载', async () => {
    getFlow.mockRejectedValue(new Error('network'))
    const { wrapper, message } = mountPage()
    await flush()

    expect(message.error).toHaveBeenCalledWith('加载流程设计失败')
    expect(wrapper.vm.loading).toBe(false)
  })

  test('下拉资源加载失败时降级为空数组', async () => {
    listActiveAgent.mockRejectedValue(new Error('a'))
    listEnabledGroups.mockRejectedValue(new Error('b'))
    listEnabledTemplates.mockRejectedValue(new Error('c'))
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.agentOptions).toEqual([])
    expect(wrapper.vm.skillGroups).toEqual([])
    expect(wrapper.vm.smsTemplates).toEqual([])
  })

  test('节点面板类型与待放置提示', () => {
    const { wrapper } = mountPage()

    expect(wrapper.vm.paletteTypes).toHaveLength(18)
    expect(wrapper.vm.paletteTypes[0]).toEqual(expect.objectContaining({ type: 'start', label: '开始' }))
    expect(wrapper.vm.pendingLabel).toBe('')

    wrapper.vm.pendingType = 'say'
    expect(wrapper.vm.pendingLabel).toBe('语音播报')
  })

  test('新增节点限制唯一开始节点，自动命名并按画布边界收敛坐标', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    wrapper.vm.nodes = []

    expect(wrapper.vm.addNode('start', 400, 250)).toBe(true)
    expect(wrapper.vm.addNode('start', 500, 300)).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('一个流程只能有一个开始节点')

    wrapper.vm.addNode('say', 5000, -10)
    wrapper.vm.addNode('say', 100, 100)
    expect(wrapper.vm.nodes.map(n => n.name)).toEqual(['开始', '语音播报', '语音播报2'])
    expect(wrapper.vm.nodes[1].x).toBe(2452)
    expect(wrapper.vm.nodes[1].y).toBe(0)
    expect(wrapper.vm.selectedKind).toBe('node')
    expect(wrapper.vm.dirty).toBe(true)
  })

  test('面板点击切换待放置类型，拖拽落点与画布点击均可新增节点', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.nodes = []
    wrapper.vm.edges = []

    wrapper.vm.onPaletteClick('say')
    expect(wrapper.vm.pendingType).toBe('say')
    wrapper.vm.onPaletteClick('say')
    expect(wrapper.vm.pendingType).toBeNull()

    const setData = jest.fn()
    wrapper.vm.onPaletteDragStart({ dataTransfer: { setData } }, 'say')
    expect(setData).toHaveBeenCalledWith('text/ivr-node-type', 'say')
    expect(wrapper.vm.pendingType).toBeNull()

    wrapper.vm.onCanvasDrop({ preventDefault: jest.fn(), dataTransfer: { getData: () => 'unknown' }, clientX: 10, clientY: 10 })
    expect(wrapper.vm.nodes).toHaveLength(0)

    wrapper.vm.onCanvasDrop({ preventDefault: jest.fn(), dataTransfer: { getData: () => 'say' }, clientX: 500, clientY: 300 })
    expect(wrapper.vm.nodes).toHaveLength(1)
    expect(wrapper.vm.nodes[0].x).toBe(426)
    expect(wrapper.vm.nodes[0].y).toBe(271)

    wrapper.vm.pendingType = 'sms'
    wrapper.vm.onCanvasClick({ clientX: 200, clientY: 200 })
    expect(wrapper.vm.nodes).toHaveLength(2)
    expect(wrapper.vm.nodes[1].type).toBe('sms')
    expect(wrapper.vm.pendingType).toBeNull()

    wrapper.vm.selectNode(wrapper.vm.nodes[0])
    wrapper.vm.onCanvasClick({ clientX: 900, clientY: 900 })
    expect(wrapper.vm.selectedKind).toBeNull()
  })

  test('拖动节点按缩放换算并标记未保存，端口拖出进入连线态', async () => {
    const { wrapper } = mountPage()
    await flush()

    const node = wrapper.vm.nodes[1]
    const originX = node.x
    wrapper.vm.onNodeMouseDown({ button: 0, clientX: 10, clientY: 10, target: { getAttribute: () => null } }, node)
    expect(wrapper.vm.selectedId).toBe(node.id)
    expect(wrapper.vm.dragState).not.toBeNull()

    wrapper.vm.onWindowMouseMove({ clientX: 310, clientY: 10 })
    expect(node.x).toBe(originX + 300)
    expect(wrapper.vm.dragState.moved).toBe(true)

    wrapper.vm.onWindowMouseUp()
    expect(wrapper.vm.dragState).toBeNull()
    expect(wrapper.vm.dirty).toBe(true)

    wrapper.vm.zoom = 0.5
    wrapper.vm.onNodeMouseDown({ button: 0, clientX: 0, clientY: 0, target: { getAttribute: () => null } }, node)
    const startX = node.x
    wrapper.vm.onWindowMouseMove({ clientX: 100, clientY: 0 })
    expect(node.x).toBe(Math.min(2452, startX + 200))
    wrapper.vm.onWindowMouseUp()

    const event = { button: 0, clientX: 1, clientY: 1, target: { getAttribute: () => 'out' } }
    wrapper.vm.onNodeMouseDown(event, node)
    expect(wrapper.vm.edgeDraft).toEqual(expect.objectContaining({ source: node.id }))

    wrapper.vm.edgeDraft = null
    wrapper.vm.onNodeMouseDown({ ...event, button: 2 }, node)
    expect(wrapper.vm.dragState).toBeNull()

    const hangup = wrapper.vm.nodes.find(n => n.type === 'agent')
    wrapper.vm.startEdge(hangup)
    expect(wrapper.vm.edgeDraft).not.toBeNull()
    const terminal = { id: 99, type: 'hangup', x: 0, y: 0 }
    wrapper.vm.edgeDraft = null
    wrapper.vm.startEdge(terminal)
    expect(wrapper.vm.edgeDraft).toBeNull()
  })

  test('连线落点命中节点时创建连线，重复连线提示，挂断节点禁止出线', async () => {
    const { wrapper, message } = mountPage()
    await flush()
    const source = wrapper.vm.nodes[0]
    const target = wrapper.vm.nodes[2]

    wrapper.vm.startEdge(source)
    wrapper.vm.edgeDraft.mx = target.x + 10
    wrapper.vm.edgeDraft.my = target.y + 10
    wrapper.vm.onWindowMouseUp()

    expect(wrapper.vm.edges).toHaveLength(3)
    expect(wrapper.vm.selectedKind).toBe('edge')
    expect(wrapper.vm.dirty).toBe(true)

    wrapper.vm.createEdge(source.id, target.id)
    expect(message.warning).toHaveBeenCalledWith('这两个节点之间已存在连线')
    expect(wrapper.vm.edges).toHaveLength(3)

    wrapper.vm.edgeDraft = { source: source.id, x: 0, y: 0, mx: 9999, my: 9999 }
    wrapper.vm.onWindowMouseUp()
    expect(wrapper.vm.edgeDraft).toBeNull()

    wrapper.vm.startEdge({ id: 100, type: 'hangup', x: 0, y: 0 })
    expect(message.warning).toHaveBeenCalledWith('挂断节点是终止节点，不能继续连线')
  })

  test('节点命中检测含容差，连线路径与标签渲染辅助正确', async () => {
    const { wrapper } = mountPage()
    await flush()
    const [start, say] = wrapper.vm.nodes

    expect(wrapper.vm.findNodeAt({ x: start.x - 10, y: start.y - 10 }).id).toBe(start.id)
    expect(wrapper.vm.findNodeAt({ x: 9999, y: 9999 })).toBeNull()
    expect(wrapper.vm.nodeById(2).name).toBe('欢迎语')
    expect(wrapper.vm.nodeById(999)).toBeNull()

    expect(wrapper.vm.nodeColor(say)).toBe('#1A3C6E')
    expect(wrapper.vm.nodeColor({ type: 'unknown' })).toBe('#1A3C6E')
    expect(wrapper.vm.nodeIcon({ type: 'unknown' })).toBe('?')
    expect(wrapper.vm.nodeTypeLabel('unknown')).toBe('unknown')
    expect(wrapper.vm.nodeName({ type: 'say', name: '' })).toBe('语音播报')
    expect(wrapper.vm.nodeBodyStyle(say).strokeWidth).toBe(1.5)
    wrapper.vm.selectNode(say)
    expect(wrapper.vm.nodeBodyStyle(say).strokeWidth).toBe(2.5)
    expect(wrapper.vm.portStyle(say).stroke).toBe('#1A3C6E')

    expect(wrapper.vm.edgePath({ source: 1, target: 2 })).toMatch(/^M /)
    expect(wrapper.vm.edgePath({ source: 1, target: 999 })).toBe('')
    expect(wrapper.vm.edgeLabelPos({ source: 1, target: 2 })).toMatch(/^translate\(/)
    expect(wrapper.vm.edgeLabelPos({ source: 1, target: 999 })).toBe('translate(0,0)')
    expect(wrapper.vm.edgeLabel({ label: '是', condition: '' })).toBe('是')
    expect(wrapper.vm.edgeLabel({ label: '', condition: 'x>1' })).toBe('x>1')
    expect(wrapper.vm.labelW({ label: '', condition: '' })).toBe(24)
    expect(wrapper.vm.labelW({ label: '是', condition: '' })).toBe(26)

    wrapper.vm.edgeDraft = { x: 0, y: 0, mx: 100, my: 0 }
    expect(wrapper.vm.draftPath).toMatch(/^M 0 0 C /)
  })

  test('删除节点走确认并级联删除连线，删除连线直接生效', async () => {
    const { wrapper, modal } = mountPage()
    await flush()

    wrapper.vm.selectNode(wrapper.vm.nodes[0])
    wrapper.vm.deleteSelected()
    await flush()

    expect(modal.dangerConfirm).toHaveBeenCalledWith(expect.objectContaining({
      content: expect.stringContaining('开始'),
      count: 1,
      countNoun: '个节点'
    }))
    expect(wrapper.vm.nodes).toHaveLength(2)
    expect(wrapper.vm.edges.some(e => e.source === 1 || e.target === 1)).toBe(false)

    const edge = wrapper.vm.edges[0]
    wrapper.vm.selectEdge(edge)
    wrapper.vm.deleteSelected()
    expect(wrapper.vm.edges.some(e => e.id === edge.id)).toBe(false)
    expect(wrapper.vm.selectedKind).toBeNull()
  })

  test('键盘 Delete 删除选中连线，Escape 取消连线与选中', async () => {
    const { wrapper } = mountPage()
    await flush()

    const edge = wrapper.vm.edges[0]
    wrapper.vm.selectEdge(edge)
    const preventDefault = jest.fn()
    wrapper.vm.onKeyDown({ key: 'Delete', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(wrapper.vm.edges.some(e => e.id === edge.id)).toBe(false)

    wrapper.vm.edgeDraft = { source: 1 }
    wrapper.vm.dragState = { node: wrapper.vm.nodes[0] }
    wrapper.vm.selectNode(wrapper.vm.nodes[0])
    wrapper.vm.onKeyDown({ key: 'Escape' })
    expect(wrapper.vm.edgeDraft).toBeNull()
    expect(wrapper.vm.dragState).toBeNull()
    expect(wrapper.vm.selectedKind).toBeNull()

    const input = document.createElement('input')
    document.body.appendChild(input)
    input.focus()
    wrapper.vm.selectNode(wrapper.vm.nodes[1])
    wrapper.vm.onKeyDown({ key: 'Delete', preventDefault: jest.fn() })
    expect(wrapper.vm.selectedKind).toBe('node')
    document.body.removeChild(input)
  })

  test('菜单选项/抽取字段/变量项增删受节点类型约束', async () => {
    const { wrapper } = mountPage()
    await flush()
    const menu = { id: 11, type: 'menu', name: '菜单', x: 0, y: 0, config: { options: [{ key: '1', label: '选项一' }] } }
    const extract = { id: 12, type: 'extract', name: '抽取', x: 0, y: 0, config: { fields: [] } }
    const variable = { id: 13, type: 'variable', name: '变量', x: 0, y: 0, config: { variables: [] } }
    wrapper.vm.nodes.push(menu, extract, variable)

    wrapper.vm.selectNode(menu)
    wrapper.vm.addMenuOption()
    expect(wrapper.vm.selectedNode.config.options).toHaveLength(2)
    expect(wrapper.vm.selectedNode.config.options[1]).toEqual({ key: '2', label: '选项2' })
    wrapper.vm.removeMenuOption(0)
    expect(wrapper.vm.selectedNode.config.options).toHaveLength(1)

    wrapper.vm.addExtractField()
    expect(extract.config.fields).toHaveLength(0)
    wrapper.vm.selectNode(extract)
    wrapper.vm.addExtractField()
    expect(extract.config.fields).toHaveLength(1)
    wrapper.vm.removeExtractField(0)
    expect(extract.config.fields).toHaveLength(0)

    wrapper.vm.selectNode(variable)
    wrapper.vm.addVariableItem()
    expect(variable.config.variables).toHaveLength(1)
    wrapper.vm.removeVariableItem(0)
    expect(variable.config.variables).toHaveLength(0)

    wrapper.vm.clearSelection()
    const dirtyBefore = wrapper.vm.dirty
    wrapper.vm.addMenuOption()
    wrapper.vm.addExtractField()
    wrapper.vm.addVariableItem()
    expect(wrapper.vm.dirty).toBe(dirtyBefore)
  })

  test('缩放边界与视图复位', async () => {
    const { wrapper } = mountPage()
    await flush()
    wrapper.vm.zoom = 0.9

    wrapper.vm.zoomIn()
    expect(wrapper.vm.zoom).toBe(1)
    wrapper.vm.zoom = 2
    wrapper.vm.zoomIn()
    expect(wrapper.vm.zoom).toBe(2)
    wrapper.vm.zoomOut()
    expect(wrapper.vm.zoom).toBe(1.9)
    wrapper.vm.zoom = 0.5
    wrapper.vm.zoomOut()
    expect(wrapper.vm.zoom).toBe(0.5)

    wrapper.vm.$refs.canvasWrap.scrollLeft = 100
    wrapper.vm.$refs.canvasWrap.scrollTop = 100
    wrapper.vm.zoom = 1.5
    wrapper.vm.resetView()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.zoom).toBe(1)
    expect(wrapper.vm.$refs.canvasWrap.scrollLeft).toBe(0)
    expect(wrapper.vm.$refs.canvasWrap.scrollTop).toBe(0)
  })

  test('保存前校验：空流程、缺少开始节点、无效连线', async () => {
    const { wrapper, message } = mountPage()
    await flush()

    wrapper.vm.nodes = []
    expect(wrapper.vm.validateDesign()).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('流程图中至少需要一个节点')

    wrapper.vm.nodes = [{ id: 1, type: 'say', name: '播报', x: 0, y: 0, config: {} }]
    expect(wrapper.vm.validateDesign()).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('流程图中必须包含一个开始节点')

    wrapper.vm.nodes = [{ id: 1, type: 'start', name: '开始', x: 0, y: 0, config: {} }]
    wrapper.vm.edges = [{ id: 2, source: 1, target: 99, label: '', condition: '' }]
    expect(wrapper.vm.validateDesign()).toBe(false)
    expect(message.warning).toHaveBeenCalledWith('存在无效连线，请检查后再保存')

    wrapper.vm.edges = []
    expect(wrapper.vm.validateDesign()).toBe(true)
  })

  test('保存载荷按节点顺序生成配置 JSON 与连线元数据', async () => {
    const { wrapper } = mountPage()
    await flush()

    const payload = wrapper.vm.buildPayload()
    expect(payload.flowId).toBe(5)
    expect(payload.nodes[0]).toEqual(expect.objectContaining({
      nodeId: 1,
      nodeType: 'start',
      nodeName: '开始',
      positionX: 400,
      positionY: 250,
      sortOrder: 0
    }))
    expect(JSON.parse(payload.nodes[1].nodeConfig).text).toBe('你好')
    expect(payload.edges[0]).toEqual(expect.objectContaining({
      sourceNodeId: 1,
      targetNodeId: 2,
      edgeLabel: '是',
      conditionExpr: 'x>1',
      sortOrder: 0
    }))

    const flowData = JSON.parse(payload.flowData)
    expect(flowData.nodes).toHaveLength(3)
    expect(flowData.nodes[0]).toEqual(expect.objectContaining({ id: '1', type: 'start', x: 400, y: 250 }))
    expect(flowData.edges[0]).toEqual(expect.objectContaining({
      id: '1-2',
      type: 'polyline',
      sourceNodeId: '1',
      targetNodeId: '2'
    }))
    expect(flowData.edges[0].properties.conditionExpr).toBe('x>1')
  })

  test('保存成功提示并重新加载，失败提示错误，校验不通过不提交', async () => {
    const { wrapper, modal, message } = mountPage()
    await flush()

    wrapper.vm.handleSave()
    await flush()

    expect(saveDesign).toHaveBeenCalled()
    expect(modal.msgSuccess).toHaveBeenCalledWith('保存成功')
    expect(wrapper.vm.dirty).toBe(false)
    expect(wrapper.vm.saving).toBe(false)
    expect(getFlow).toHaveBeenCalledTimes(2)

    saveDesign.mockRejectedValue(new Error('fail'))
    wrapper.vm.handleSave()
    await flush()
    expect(message.error).toHaveBeenCalledWith('保存失败，请稍后重试')
    expect(wrapper.vm.saving).toBe(false)

    saveDesign.mockClear()
    wrapper.vm.nodes = []
    wrapper.vm.handleSave()
    await flush()
    expect(saveDesign).not.toHaveBeenCalled()
  })

  test('未保存离开需确认，取消则停留在当前页', async () => {
    const { wrapper, routerPush, confirm } = mountPage()
    await flush()

    wrapper.vm.dirty = false
    wrapper.vm.goBack()
    expect(confirm).not.toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith('/ivrFlow/flow')

    routerPush.mockClear()
    wrapper.vm.dirty = true
    wrapper.vm.goBack()
    await flush()
    expect(confirm).toHaveBeenCalledWith('当前有未保存的修改，确定离开吗？', '提示', expect.any(Object))
    expect(routerPush).toHaveBeenCalledWith('/ivrFlow/flow')

    routerPush.mockClear()
    confirm.mockRejectedValue(new Error('cancel'))
    wrapper.vm.goBack()
    await flush()
    expect(routerPush).not.toHaveBeenCalled()
  })

  test('短信模板按选中节点模板ID匹配', async () => {
    const { wrapper } = mountPage()
    await flush()

    expect(wrapper.vm.currentSmsTemplate).toBeNull()

    const smsNode = { id: 20, type: 'sms', name: '短信', x: 0, y: 0, config: { templateId: 1 } }
    wrapper.vm.nodes.push(smsNode)
    wrapper.vm.selectNode(smsNode)
    expect(wrapper.vm.currentSmsTemplate).toEqual(expect.objectContaining({ templateId: 1 }))

    wrapper.vm.selectedNode.config.templateId = 99
    expect(wrapper.vm.currentSmsTemplate).toBeNull()
  })
})
