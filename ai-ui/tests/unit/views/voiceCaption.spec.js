'use strict'

/**
 * 坐席实时字幕组件单测：锁定 WebSocket 建链与帧分发（ASR/答案/VAD/情绪/协办）、
 * 麦克风采集重采样与 20ms 分帧、TTS 排队播放与打断、以及销毁清理。
 */
import { mount, createLocalVue } from '@vue/test-utils'
import ElementUI from 'element-ui'
import VoiceCaption from '@/views/lawyers/callCenter/VoiceCaption.vue'
import { getToken } from '@/utils/auth'

jest.mock('@/utils/auth', () => ({
  getToken: jest.fn(() => 'tk')
}))

const localVue = createLocalVue()
localVue.use(ElementUI)

/** 自定义 WebSocket：记录实例、发送帧，并允许测试主动触发 open/close */
class WebSocketMock {
  constructor(url) {
    this.url = url
    this.readyState = 0
    this.sent = []
    this.closeCode = null
    WebSocketMock.instances.push(this)
  }

  send(data) {
    this.sent.push(data)
  }

  close(code) {
    this.closeCode = code
    this.readyState = 3
  }

  open() {
    this.readyState = WebSocketMock.OPEN
    if (this.onopen) this.onopen()
  }
}
WebSocketMock.OPEN = 1
WebSocketMock.instances = []

/** 自定义 AudioContext：采集与播放共用，暴露节点便于断言 */
class AudioContextMock {
  constructor(opts) {
    const options = opts || {}
    this.sampleRate = options.sampleRate || 48000
    this.currentTime = 0
    this.destination = {}
    this.createMediaStreamSource = jest.fn(() => ({ connect: jest.fn(), disconnect: jest.fn() }))
    this.createScriptProcessor = jest.fn(() => ({
      connect: jest.fn(),
      disconnect: jest.fn(),
      onaudioprocess: null
    }))
    this.createBuffer = jest.fn((channels, length, rate) => ({
      duration: length / rate,
      copyToChannel: jest.fn()
    }))
    this.createBufferSource = jest.fn(() => ({
      buffer: null,
      connect: jest.fn(),
      start: jest.fn(),
      stop: jest.fn(),
      onended: null
    }))
    this.close = jest.fn(() => Promise.resolve())
    AudioContextMock.instances.push(this)
  }
}
AudioContextMock.instances = []

const getUserMedia = jest.fn()

function mountPage() {
  return mount(VoiceCaption, {
    localVue,
    propsData: { sessionId: 12, role: 'agent' }
  })
}

async function connectWs(wrapper) {
  const promise = wrapper.vm.connect()
  const ws = WebSocketMock.instances[WebSocketMock.instances.length - 1]
  ws.open()
  await promise
  return ws
}

describe('VoiceCaption 坐席实时字幕', () => {
  beforeEach(() => {
    jest.clearAllMocks()
    WebSocketMock.instances = []
    AudioContextMock.instances = []
    getToken.mockReturnValue('tk')
    global.WebSocket = WebSocketMock
    global.AudioContext = AudioContextMock
    delete global.webkitAudioContext
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: { getUserMedia },
      configurable: true,
      writable: true
    })
    getUserMedia.mockResolvedValue({ getTracks: () => [] })
    localStorage.clear()
  })

  test('WS 地址携带会话、角色与 token，无 token 时不追加参数', () => {
    const wrapper = mountPage()

    const url = wrapper.vm.buildWsUrl()
    expect(url).toMatch(/^ws:\/\//)
    expect(url).toContain('/ws/voice/12/agent?token=tk')

    getToken.mockReturnValue(null)
    expect(wrapper.vm.buildWsUrl()).not.toContain('token=')
  })

  test('建链成功后连接状态文案与颜色切换', async () => {
    const wrapper = mountPage()

    expect(wrapper.vm.connText).toBe('未连接')
    expect(wrapper.vm.connTagType).toBe('info')

    await connectWs(wrapper)
    expect(wrapper.vm.connected).toBe(true)
    expect(wrapper.vm.connText).toBe('已连接')
    expect(wrapper.vm.connTagType).toBe('primary')

    wrapper.setData({ listening: true })
    expect(wrapper.vm.connText).toBe('识别中')
    expect(wrapper.vm.connTagType).toBe('success')
  })

  test('ASR partial/final 帧驱动字幕固化并向外抛出 final', async () => {
    const wrapper = mountPage()
    const ws = await connectWs(wrapper)

    wrapper.vm.handleFrame('not-json')
    expect(wrapper.vm.captions).toHaveLength(0)

    ws.onmessage({ data: JSON.stringify({ type: 'asr_partial', text: '你好' }) })
    expect(wrapper.vm.partialText).toBe('你好')

    ws.onmessage({ data: JSON.stringify({ type: 'asr_final', text: ' 您好 ' }) })
    expect(wrapper.vm.partialText).toBe('')
    expect(wrapper.vm.captions).toHaveLength(1)
    expect(wrapper.vm.captions[0]).toMatchObject({ speaker: 'agent', text: '您好' })
    expect(wrapper.emitted('final')[0]).toEqual(['您好'])

    ws.onmessage({ data: JSON.stringify({ type: 'asr_final', text: '   ' }) })
    expect(wrapper.vm.captions).toHaveLength(1)
  })

  test('AI 应答按 turnId 续写同回合并附法条溯源', async () => {
    const wrapper = mountPage()

    wrapper.vm.handleFrame(JSON.stringify({ type: 'answer_delta', turnId: 't1', text: '第一版' }))
    wrapper.vm.handleFrame(JSON.stringify({ type: 'answer_delta', turnId: 't1', text: '修订版' }))
    expect(wrapper.vm.captions).toHaveLength(1)
    expect(wrapper.vm.captions[0]).toMatchObject({ speaker: 'ai', text: '修订版' })

    wrapper.vm.handleFrame(JSON.stringify({ type: 'answer_done', turnId: 't1', sources: ['民法典第1条'] }))
    expect(wrapper.vm.captions[0].sources).toEqual(['民法典第1条'])

    wrapper.vm.handleFrame(JSON.stringify({ type: 'answer_delta', turnId: 't2', text: '新回合' }))
    expect(wrapper.vm.captions).toHaveLength(2)
    expect(wrapper.vm.captions[1]).toMatchObject({ speaker: 'ai', text: '新回合' })

    wrapper.vm.handleFrame(JSON.stringify({ type: 'answer_delta', turnId: 't2', text: '' }))
    expect(wrapper.vm.captions).toHaveLength(2)
  })

  test('情绪预警 urgent 覆盖 negative 且不再降级，标题含意图与关键词', () => {
    const wrapper = mountPage()

    expect(wrapper.vm.emotionTitle).toBe('')

    wrapper.vm.handleFrame(JSON.stringify({
      type: 'emotion',
      level: 'negative',
      intent: '退款纠纷',
      keywords: ['投诉', '退款']
    }))
    expect(wrapper.vm.emotionAlert.level).toBe('negative')
    expect(wrapper.vm.emotionTitle).toContain('负面情绪预警')
    expect(wrapper.vm.emotionTitle).toContain('意图：退款纠纷')
    expect(wrapper.vm.emotionTitle).toContain('命中：投诉、退款')

    wrapper.vm.handleFrame(JSON.stringify({ type: 'emotion', level: 'urgent' }))
    expect(wrapper.vm.emotionAlert.level).toBe('urgent')
    expect(wrapper.vm.emotionTitle).toContain('紧急情绪预警')

    wrapper.vm.handleFrame(JSON.stringify({ type: 'emotion', level: 'negative', intent: '其他' }))
    expect(wrapper.vm.emotionAlert.level).toBe('urgent')

    wrapper.vm.handleFrame(JSON.stringify({ type: 'emotion', level: 'negative', keywords: ['退款'] }))
    expect(wrapper.vm.emotionTitle).toContain('紧急情绪预警')
    expect(wrapper.vm.emotionTitle).not.toContain('意图')
  })

  test('VAD 说话状态切换，播报中检测到语音立即清空本地播放队列', () => {
    const wrapper = mountPage()
    const stop = jest.fn()
    wrapper.vm.playQueue = [{ stop }]
    wrapper.vm.ttsPlaying = true

    wrapper.vm.handleFrame(JSON.stringify({ type: 'vad_speech_start' }))
    expect(wrapper.vm.peerSpeaking).toBe(true)
    expect(stop).toHaveBeenCalled()
    expect(wrapper.vm.playQueue).toEqual([])
    expect(wrapper.vm.ttsPlaying).toBe(false)

    wrapper.vm.handleFrame(JSON.stringify({ type: 'vad_speech_end' }))
    expect(wrapper.vm.peerSpeaking).toBe(false)
  })

  test('协办事件透传为组件事件，错误帧写入错误提示', () => {
    const wrapper = mountPage()

    wrapper.vm.handleFrame(JSON.stringify({ type: 'copilot_element', elements: [1] }))
    wrapper.vm.handleFrame(JSON.stringify({ type: 'copilot_laws', laws: ['民法典'] }))
    wrapper.vm.handleFrame(JSON.stringify({ type: 'copilot_tickets', tickets: [] }))
    wrapper.vm.handleFrame(JSON.stringify({ type: 'copilot_actions', actions: [] }))

    expect(wrapper.emitted('copilot-element')[0]).toEqual([{ type: 'copilot_element', elements: [1] }])
    expect(wrapper.emitted('copilot-laws')[0]).toEqual([{ type: 'copilot_laws', laws: ['民法典'] }])
    expect(wrapper.emitted('copilot-tickets')[0]).toEqual([{ type: 'copilot_tickets', tickets: [] }])
    expect(wrapper.emitted('copilot-actions')[0]).toEqual([{ type: 'copilot_actions', actions: [] }])

    wrapper.vm.handleFrame(JSON.stringify({ type: 'error', code: '500', message: 'ASR 异常' }))
    expect(wrapper.vm.lastError).toBe('500: ASR 异常')
  })

  test('WS 关闭（1008）提示重新登录；连接失败提示通道错误', async () => {
    const wrapper = mountPage()
    const ws = await connectWs(wrapper)

    ws.onclose({ code: 1008 })
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.listening).toBe(false)
    expect(wrapper.vm.lastError).toContain('1008')

    const failing = mountPage()
    const promise = failing.vm.connect()
    const failingWs = WebSocketMock.instances[WebSocketMock.instances.length - 1]
    failingWs.onerror()
    await expect(promise).rejects.toThrow('ws error')
    expect(failing.vm.lastError).toBe('语音通道连接失败')
  })

  test('开始识别发送 start 帧并启动采集；停止识别发送 stop 并释放设备', async () => {
    const wrapper = mountPage()

    const startPromise = wrapper.vm.startListen()
    const ws = WebSocketMock.instances[0]
    ws.open()
    await startPromise

    const startFrame = JSON.parse(ws.sent[0])
    expect(startFrame).toEqual({ type: 'start', engine: 'mock', format: 'pcm', sampleRate: 16000 })
    expect(wrapper.vm.listening).toBe(true)
    expect(wrapper.vm.audioCtx).toBeTruthy()
    expect(getUserMedia).toHaveBeenCalledWith({ audio: true })

    wrapper.vm.partialText = '临时'
    wrapper.vm.stopListen()
    expect(JSON.parse(ws.sent[1])).toEqual({ type: 'stop' })
    expect(wrapper.vm.listening).toBe(false)
    expect(wrapper.vm.partialText).toBe('')
    expect(wrapper.vm.audioCtx).toBeNull()
  })

  test('麦克风不可用时记录错误并保持未识别状态', async () => {
    getUserMedia.mockRejectedValueOnce(new Error('denied'))
    const wrapper = mountPage()

    const startPromise = wrapper.vm.startListen()
    WebSocketMock.instances[0].open()
    await startPromise

    expect(wrapper.vm.lastError).toContain('麦克风不可用')
    expect(wrapper.vm.lastError).toContain('denied')
    expect(wrapper.vm.listening).toBe(false)
  })

  test('采集回调按 16kHz、20ms 整帧发送二进制', async () => {
    const wrapper = mountPage()
    await connectWs(wrapper)
    await wrapper.vm.startCapture()
    wrapper.vm.listening = true

    const processor = wrapper.vm._captureNodes.processor
    const ws = WebSocketMock.instances[0]
    // 48kHz 源、960 样本 → 重采样后 320 样本，正好一帧
    processor.onaudioprocess({ inputBuffer: { getChannelData: () => new Float32Array(960) } })

    expect(ws.sent).toHaveLength(1)
    expect(ws.sent[0]).toBeInstanceOf(ArrayBuffer)
    expect(ws.sent[0].byteLength).toBe(320 * 2)
    expect(wrapper.vm.pcmBuffer).toEqual([])
  })

  test('停止采集释放音轨与音频上下文', async () => {
    const trackStop = jest.fn()
    getUserMedia.mockResolvedValueOnce({ getTracks: () => [{ stop: trackStop }] })
    const wrapper = mountPage()
    await wrapper.vm.startCapture()
    const ctx = wrapper.vm.audioCtx

    wrapper.vm.stopCapture()

    expect(ctx.close).toHaveBeenCalled()
    expect(trackStop).toHaveBeenCalled()
    expect(wrapper.vm.mediaStream).toBeNull()
    expect(wrapper.vm.audioCtx).toBeNull()
  })

  test('重采样与 Float32 转 S16LE 数值边界', () => {
    const wrapper = mountPage()

    expect(Array.from(wrapper.vm.floatTo16(new Float32Array([0, 1, -1])))).toEqual([0, 32767, -32768])
    expect(Array.from(wrapper.vm.resampleToPcm16(new Float32Array([0.5]), 16000, 16000))).toEqual([16383])
    expect(wrapper.vm.resampleToPcm16(new Float32Array(4), 32000, 16000)).toHaveLength(2)
  })

  test('TTS 首包即播并按包时长顺序排队，播放结束回落状态', () => {
    const wrapper = mountPage()
    const chunk = Buffer.from(new Int16Array([0, 16384, -16384]).buffer).toString('base64')

    wrapper.vm.playPcmChunk({ data: chunk, sampleRate: 16000 })
    expect(wrapper.vm.ttsPlaying).toBe(true)
    expect(wrapper.vm.playQueue).toHaveLength(1)

    const playCtx = AudioContextMock.instances.find(i => i.sampleRate === 16000)
    expect(playCtx.createBuffer).toHaveBeenCalledWith(1, 3, 16000)
    const first = wrapper.vm.playQueue[0]
    expect(first.start).toHaveBeenCalledWith(0)

    wrapper.vm.playPcmChunk({ data: chunk, sampleRate: 16000 })
    const second = wrapper.vm.playQueue[1]
    expect(second.start).toHaveBeenCalledWith(3 / 16000)

    first.onended()
    expect(wrapper.vm.playQueue).toHaveLength(1)
    second.onended()
    expect(wrapper.vm.ttsPlaying).toBe(false)

    wrapper.vm.playPcmChunk({})
    expect(wrapper.vm.playQueue).toHaveLength(0)
  })

  test('打断播报发送 bargein 帧并停止所有播放源', async () => {
    const wrapper = mountPage()
    const ws = await connectWs(wrapper)
    const chunk = Buffer.from(new Int16Array([0, 1]).buffer).toString('base64')
    wrapper.vm.playPcmChunk({ data: chunk })
    const source = wrapper.vm.playQueue[0]

    wrapper.vm.bargeIn()

    expect(JSON.parse(ws.sent[0])).toEqual({ type: 'bargein' })
    expect(source.stop).toHaveBeenCalled()
    expect(wrapper.vm.playQueue).toEqual([])
    expect(wrapper.vm.ttsPlaying).toBe(false)
    expect(wrapper.vm._nextPlayAt).toBe(0)
  })

  test('销毁时关闭 WS 与播放上下文并复位状态', async () => {
    const wrapper = mountPage()
    const ws = await connectWs(wrapper)
    wrapper.vm.playPcmChunk({ data: Buffer.from(new Int16Array([0]).buffer).toString('base64') })
    const playCtx = wrapper.vm.playCtx

    wrapper.destroy()

    expect(ws.closeCode).toBe(1000)
    expect(playCtx.close).toHaveBeenCalled()
    expect(wrapper.vm.ws).toBeNull()
    expect(wrapper.vm.connected).toBe(false)
    expect(wrapper.vm.listening).toBe(false)
  })

  test('关怀模式按坐席端 localStorage 配置返回字号类', () => {
    localStorage.setItem('ai-ui-care-mode', JSON.stringify({ fontSize: 'large' }))
    const large = mountPage()
    expect(large.vm.careClass).toBe('vc-care-large')
    large.destroy()

    localStorage.setItem('ai-ui-care-mode', JSON.stringify({ fontSize: 'xlarge' }))
    const xlarge = mountPage()
    expect(xlarge.vm.careClass).toBe('vc-care-xlarge')
    xlarge.destroy()

    localStorage.setItem('ai-ui-care-mode', 'bad-json')
    const bad = mountPage()
    expect(bad.vm.careClass).toBe('')
    bad.destroy()

    localStorage.setItem('ai-ui-care-mode', JSON.stringify({ fontSize: 'standard' }))
    const standard = mountPage()
    expect(standard.vm.careClass).toBe('')
  })
})
