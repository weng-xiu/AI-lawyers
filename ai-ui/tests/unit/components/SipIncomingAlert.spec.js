'use strict'

/**
 * SipIncomingAlert 来电弹窗单测：锁定振铃条件、号码展示、接听与拒接。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import SipIncomingAlert from '@/components/CallCenter/SipIncomingAlert.vue'

const localVue = createLocalVue()
localVue.use(ElementUI)

function mountAlert(sipCall, dispatchResult = true) {
  const dispatch = jest.fn(() => dispatchResult)
  const message = { warning: jest.fn() }
  const wrapper = mount(SipIncomingAlert, {
    localVue,
    mocks: {
      $store: { getters: { sipCall, agent: null }, dispatch },
      $message: message
    }
  })
  return { wrapper, dispatch, message }
}

describe('SipIncomingAlert', () => {
  beforeEach(() => {
    jest.useFakeTimers()
  })

  afterEach(() => {
    jest.useRealTimers()
  })

  test('非来电振铃状态不显示弹窗', () => {
    const { wrapper } = mountAlert({ direction: 'outgoing', state: 'ringing' })

    expect(wrapper.vm.visible).toBe(false)
    expect(wrapper.find('.sip-incoming-mask').exists()).toBe(false)
  })

  test('来电振铃时展示号码', () => {
    const { wrapper } = mountAlert({ direction: 'incoming', state: 'ringing', from: '13800000000' })

    expect(wrapper.vm.visible).toBe(true)
    expect(wrapper.find('.sip-incoming-mask').exists()).toBe(true)
    expect(wrapper.find('.sip-incoming-from').text()).toBe('13800000000')
  })

  test('无主叫号码时依次降级为 callId 与未知来电', () => {
    const withCallId = mountAlert({ direction: 'incoming', state: 'ringing', callId: 'call-1' }).wrapper
    expect(withCallId.vm.callerDisplay).toBe('call-1')

    const unknown = mountAlert({ direction: 'incoming', state: 'ringing' }).wrapper
    expect(unknown.vm.callerDisplay).toBe('未知来电')
  })

  test('接听调用 sipAnswer，失败时给出警告并复位按钮', () => {
    const { wrapper, dispatch, message } = mountAlert({ direction: 'incoming', state: 'ringing' }, false)

    wrapper.vm.handleAnswer()

    expect(dispatch).toHaveBeenCalledWith('agent/sipAnswer')
    expect(message.warning).toHaveBeenCalledWith('当前没有可接听的来电')
    expect(wrapper.vm.answering).toBe(true)

    jest.advanceTimersByTime(800)
    expect(wrapper.vm.answering).toBe(false)
  })

  test('拒接调用 sipHangup', () => {
    const { wrapper, dispatch } = mountAlert({ direction: 'incoming', state: 'ringing' })

    wrapper.vm.handleReject()

    expect(dispatch).toHaveBeenCalledWith('agent/sipHangup')
  })
})
