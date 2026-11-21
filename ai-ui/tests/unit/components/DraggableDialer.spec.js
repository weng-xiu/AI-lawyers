'use strict'

/**
 * DraggableDialer 拨号框单测：锁定按键输入、联系人搜索、拨号状态机、静音/保持与拖拽。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import DraggableDialer from '@/components/CallCenter/DraggableDialer.vue'
import { makeCall, hangupCall, holdCall, resumeCall } from '@/api/lawyers/callCenter'

jest.mock('@/api/lawyers/callCenter', () => ({
  makeCall: jest.fn(),
  hangupCall: jest.fn(),
  holdCall: jest.fn(),
  resumeCall: jest.fn()
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

const agent = {
  agentId: 1001,
  sipExtension: '8001',
  status: '1',
  callStatus: '0',
  currentCallPhone: ''
}

const contacts = [
  { id: 1, name: '张三', phone: '13800000000' },
  { id: 2, name: '李四', phone: '13900000000' }
]

const wrappers = []
const flushMicro = () => Promise.resolve().then(() => Promise.resolve())

function mountDialer(propsData = {}) {
  const message = { warning: jest.fn(), success: jest.fn(), info: jest.fn() }
  const wrapper = mount(DraggableDialer, {
    localVue,
    propsData: { visible: true, agent, contacts, ...propsData },
    mocks: { $message: message }
  })
  wrappers.push(wrapper)
  return { wrapper, message }
}

describe('DraggableDialer', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    jest.useFakeTimers()
    localStorage.clear()
    global.requestAnimationFrame = jest.fn(cb => {
      cb()
      return 1
    })
    global.cancelAnimationFrame = jest.fn()
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop().destroy()
    jest.useRealTimers()
  })

  test('展示坐席状态与分机信息', () => {
    const { wrapper } = mountDialer({ agent: { ...agent, status: '2' } })

    expect(wrapper.find('.status-tag').text()).toBe('置忙')
    expect(wrapper.text()).toContain('分机：')
    expect(wrapper.text()).toContain('8001')
  })

  test('未签入时展示未签入状态', () => {
    const { wrapper } = mountDialer({ agent: null })

    expect(wrapper.vm.statusLabel).toBe('未签入')
    expect(wrapper.vm.statusTagType).toBe('info')
    expect(wrapper.text()).toContain('未分配')
  })

  test('数字键盘输入、退格与清除', () => {
    const { wrapper } = mountDialer()

    wrapper.vm.pressKey('1')
    wrapper.vm.pressKey('3')
    expect(wrapper.vm.phoneNumber).toBe('13')

    wrapper.vm.backspace()
    expect(wrapper.vm.phoneNumber).toBe('1')

    wrapper.vm.clearNumber()
    expect(wrapper.vm.phoneNumber).toBe('')
    expect(wrapper.vm.contactName).toBe('')
  })

  test('输入达到 20 位或通话中不再接受按键', () => {
    const { wrapper } = mountDialer()
    wrapper.vm.phoneNumber = '1'.repeat(20)
    wrapper.vm.pressKey('2')
    expect(wrapper.vm.phoneNumber).toHaveLength(20)

    wrapper.vm.callState = 'connected'
    wrapper.vm.pressKey('3')
    expect(wrapper.vm.phoneNumber).toHaveLength(20)
  })

  test('联系人按姓名或号码搜索并选择回填', () => {
    const { wrapper } = mountDialer()

    wrapper.vm.searchKey = '张'
    wrapper.vm.onSearch()
    expect(wrapper.vm.searchResults).toEqual([contacts[0]])

    wrapper.vm.searchKey = '139'
    wrapper.vm.onSearch()
    expect(wrapper.vm.searchResults).toEqual([contacts[1]])

    wrapper.vm.selectContact(contacts[0])
    expect(wrapper.vm.phoneNumber).toBe('13800000000')
    expect(wrapper.vm.contactName).toBe('张三')
    expect(wrapper.vm.searchKey).toBe('')
    expect(wrapper.vm.searchResults).toEqual([])
  })

  test('已拨号码反查联系人', () => {
    const { wrapper } = mountDialer()

    wrapper.vm.lookupContactByPhone('0000')
    expect(wrapper.vm.contactName).toBe('张三')

    wrapper.vm.lookupContactByPhone('1234')
    expect(wrapper.vm.contactName).toBe('')
  })

  test('拨号前校验号码与坐席', () => {
    const { wrapper, message } = mountDialer()

    wrapper.vm.makeCall()
    expect(message.warning).toHaveBeenCalledWith('请输入被叫号码')

    wrapper.vm.phoneNumber = '13800000000'
    const noAgent = mountDialer({ agent: null })
    noAgent.wrapper.vm.phoneNumber = '13800000000'
    noAgent.wrapper.vm.makeCall()
    expect(noAgent.message.warning).toHaveBeenCalledWith('请先签入坐席')
    expect(makeCall).not.toHaveBeenCalled()
  })

  test('拨号成功后进入呼叫中并保留 5 秒回退', async () => {
    makeCall.mockResolvedValue({})
    const { wrapper, message } = mountDialer()
    wrapper.vm.phoneNumber = '13800000000'

    wrapper.vm.makeCall()
    await flushMicro()

    expect(makeCall).toHaveBeenCalledWith({ agentId: 1001, phone: '13800000000' })
    expect(message.success).toHaveBeenCalledWith('外呼请求已提交')
    expect(wrapper.vm.callState).toBe('dialing')
    expect(wrapper.emitted('call-made')).toEqual([[{ agentId: 1001, phone: '13800000000' }]])

    jest.advanceTimersByTime(5000)
    expect(wrapper.vm.callState).toBe('idle')
  })

  test('拨号接口失败时回到空闲', async () => {
    makeCall.mockRejectedValue(new Error('network'))
    const { wrapper } = mountDialer()
    wrapper.vm.phoneNumber = '13800000000'

    wrapper.vm.makeCall()
    await flushMicro()

    expect(wrapper.vm.callState).toBe('idle')
  })

  test('坐席推送驱动通话计时与结束回落', async () => {
    const { wrapper } = mountDialer()

    await wrapper.setProps({ agent: { ...agent, callStatus: '2', currentCallPhone: '13900000000' } })
    expect(wrapper.vm.callState).toBe('connected')
    expect(wrapper.vm.displayText).toBe('13900000000')

    jest.advanceTimersByTime(3000)
    expect(wrapper.vm.duration).toBe(3)
    expect(wrapper.vm.formatDuration).toBe('00:03')

    await wrapper.setProps({ agent: { ...agent, callStatus: '0' } })
    expect(wrapper.vm.callState).toBe('ended')

    jest.advanceTimersByTime(2000)
    expect(wrapper.vm.callState).toBe('idle')
  })

  test('挂断调用接口并结束通话', async () => {
    hangupCall.mockResolvedValue({})
    const { wrapper } = mountDialer()
    wrapper.vm.enterConnected('13900000000')

    wrapper.vm.hangup()
    await flushMicro()

    expect(hangupCall).toHaveBeenCalledWith({ agentId: 1001 })
    expect(wrapper.vm.callState).toBe('ended')
    expect(wrapper.vm.muted).toBe(false)
    expect(wrapper.vm.held).toBe(false)
  })

  test('静音与保持切换', async () => {
    holdCall.mockResolvedValue({})
    resumeCall.mockResolvedValue({})
    const { wrapper, message } = mountDialer()

    wrapper.vm.toggleMute()
    expect(wrapper.vm.muted).toBe(true)
    expect(message.info).toHaveBeenCalledWith('已静音')

    wrapper.vm.toggleMute()
    expect(wrapper.vm.muted).toBe(false)
    expect(message.info).toHaveBeenCalledWith('已取消静音')

    wrapper.vm.toggleHold()
    await flushMicro()
    expect(holdCall).toHaveBeenCalledWith({ agentId: 1001 })
    expect(wrapper.vm.held).toBe(true)

    wrapper.vm.toggleHold()
    await flushMicro()
    expect(resumeCall).toHaveBeenCalledWith({ agentId: 1001 })
    expect(wrapper.vm.held).toBe(false)
  })

  test('关闭拨号框同步内部状态并抛出事件', () => {
    const { wrapper } = mountDialer()

    wrapper.vm.closeDialer()

    expect(wrapper.vm.localVisible).toBe(false)
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:visible')).toEqual([[false]])
  })

  test('visible 属性控制显隐与最小化恢复', async () => {
    const { wrapper } = mountDialer({ visible: false })
    expect(wrapper.find('.draggable-dialer').exists()).toBe(false)

    await wrapper.setProps({ visible: true })
    expect(wrapper.find('.draggable-dialer').exists()).toBe(true)

    wrapper.vm.minimized = true
    wrapper.vm.onKeydown({ key: '1', target: { tagName: 'DIV' }, preventDefault: jest.fn() })
    expect(wrapper.vm.phoneNumber).toBe('')
  })

  test('拖拽更新位置并持久化', () => {
    const { wrapper } = mountDialer()
    wrapper.vm.$el.getBoundingClientRect = () => ({ left: 100, top: 50 })
    const preventDefault = jest.fn()

    wrapper.vm.startDrag({ clientX: 120, clientY: 70 })
    expect(wrapper.vm.dragging).toBe(true)
    expect(document.body.style.userSelect).toBe('none')

    wrapper.vm.onDragMove({ clientX: 160, clientY: 120, cancelable: true, preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(wrapper.vm.posX).toBe(140)
    expect(wrapper.vm.posY).toBe(100)

    wrapper.vm.stopDrag()
    expect(wrapper.vm.dragging).toBe(false)
    expect(JSON.parse(localStorage.getItem('dialer_position_v1'))).toEqual({ x: 140, y: 100 })
  })

  test('优先使用本地记忆的位置', () => {
    localStorage.setItem('dialer_position_v1', JSON.stringify({ x: 120, y: 80 }))

    const { wrapper } = mountDialer()

    expect(wrapper.vm.posX).toBe(120)
    expect(wrapper.vm.posY).toBe(80)
  })
})
