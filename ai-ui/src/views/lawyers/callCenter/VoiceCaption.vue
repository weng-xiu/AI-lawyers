<template>
  <el-card shadow="never" class="vc-card" :class="careClass">
    <div slot="header" class="vc-header">
      <span class="vc-title"><i class="el-icon-microphone"></i> 实时字幕</span>
      <div class="vc-header-right">
        <el-tag v-if="peerSpeaking" size="mini" type="success" effect="dark" class="vc-speaking">说话中</el-tag>
        <el-select v-model="engine" size="mini" class="vc-engine" :disabled="listening">
          <el-option label="Mock 联调" value="mock" />
          <el-option label="DashScope" value="dashscope" />
        </el-select>
        <el-tag size="mini" :type="connTagType" effect="dark">{{ connText }}</el-tag>
      </div>
    </div>

    <!-- E4：实时情绪预警横幅（urgent 红 / negative 橙，可关闭） -->
    <el-alert v-if="emotionAlert"
              :class="['vc-emotion', careClass && 'vc-emotion-care']"
              :type="emotionAlert.level === 'urgent' ? 'error' : 'warning'"
              :closable="true" show-icon
              :title="emotionTitle"
              @close="emotionAlert = null" />

    <!-- 字幕流：partial 进行态（灰斜体）→ final 固化；区分坐席/AI/来电者 -->
    <div ref="captionBox" class="vc-captions">
      <div v-if="captions.length === 0 && !partialText" class="vc-empty">
        <i class="el-icon-chat-dot-square"></i>
        <p>开启识别后，坐席语音将实时转写为字幕（当前为链路联调通道）。</p>
      </div>
      <div v-for="(item, idx) in captions" :key="idx" class="vc-item">
        <div class="vc-line" :class="'vc-' + item.speaker">
          <span class="vc-speaker">{{ speakerLabel(item.speaker) }}</span>
          <span class="vc-text">{{ item.text }}</span>
          <span class="vc-time">{{ item.time }}</span>
        </div>
        <div v-if="item.sources && item.sources.length" class="vc-sources">依据：{{ item.sources.join('；') }}</div>
      </div>
      <div v-if="partialText" class="vc-line vc-agent vc-partial">
        <span class="vc-speaker">坐席</span>
        <span class="vc-text">{{ partialText }}</span>
        <span class="vc-time">…</span>
      </div>
    </div>

    <!-- 控制区 -->
    <div class="vc-actions">
      <el-button v-if="!listening" type="primary" size="small" icon="el-icon-video-play"
                 :disabled="!sessionId" @click="startListen">开始识别</el-button>
      <el-button v-else type="danger" size="small" icon="el-icon-video-pause"
                 @click="stopListen">停止识别</el-button>
      <el-button size="small" icon="el-icon-brush" :disabled="captions.length === 0"
                 @click="clearCaptions">清空</el-button>
      <el-button v-if="ttsPlaying" size="small" type="warning" icon="el-icon-close"
                 @click="bargeIn">打断播报</el-button>
    </div>

    <div v-if="lastError" class="vc-error">
      <i class="el-icon-warning-outline"></i> {{ lastError }}
    </div>
  </el-card>
</template>

<script>
/**
 * 坐席实时字幕组件（P3-A6）。
 *
 * 链路：麦克风(getUserMedia) → AudioContext 采集 Float32 → 重采样 16kHz → S16LE PCM
 *   → /ws/voice/{sessionId}/agent 二进制帧 → 服务端流式 ASR（mock/dashscope）
 *   → asr_partial/asr_final 渲染字幕。
 * TTS 试听：tts_audio(base64 PCM) → AudioContext 首包即播；tts_end 收尾；barge-in 打断。
 *
 * 边界（与文档 9.32/9.41 一致）：
 *  - 本组件是链路联调/坐席辅助通道，真实通话音频对接 PBX 媒体流在 B3 之后；
 *  - 来电者(caller)字幕样式预留，待 PBX 媒体链路接入后由后端推送；
 *  - Mock 引擎产物（【Mock】文本/正弦音）禁止用于对公众的正式应答。
 */
import { getToken } from '@/utils/auth'

const TARGET_SAMPLE_RATE = 16000
const FRAME_SAMPLES = 320 // 20ms @16kHz
const CARE_KEY = 'ai-ui-care-mode'

export default {
  name: 'VoiceCaption',
  props: {
    // 关联通话记录ID，作为 /ws/voice 的 sessionId
    sessionId: {
      type: [Number, String],
      default: null
    },
    role: {
      type: String,
      default: 'agent'
    }
  },
  data() {
    return {
      engine: 'mock',
      ws: null,
      connected: false,
      listening: false,
      captions: [],
      partialText: '',
      lastError: '',
      // 音频采集
      audioCtx: null,
      mediaStream: null,
      pcmBuffer: [],
      // TTS 播放
      playCtx: null,
      playQueue: [],
      ttsPlaying: false,
      playSampleRate: TARGET_SAMPLE_RATE,
      // A4：服务端 VAD 说话状态（vad_speech_start/end 驱动，用于"说话中"指示）
      peerSpeaking: false,
      // E4：实时情绪预警（null 不展示横幅；urgent 覆盖 negative）
      emotionAlert: null
    }
  },
  computed: {
    connText() {
      if (this.listening) return '识别中'
      if (this.connected) return '已连接'
      return '未连接'
    },
    connTagType() {
      if (this.listening) return 'success'
      if (this.connected) return 'primary'
      return 'info'
    },
    // E4：预警横幅文案
    emotionTitle() {
      if (!this.emotionAlert) return ''
      const a = this.emotionAlert
      const levelText = a.level === 'urgent' ? '紧急情绪' : '负面情绪'
      const intentText = a.intent ? `，意图：${a.intent}` : ''
      const kw = a.keywords && a.keywords.length ? `，命中：${a.keywords.join('、')}` : ''
      return `来电者${levelText}预警${intentText}${kw}（已通知班长/风险预警页）`
    },
    // 关怀模式（坐席端 localStorage，V2.11：fontSize standard/large/xlarge）
    careClass() {
      try {
        const raw = localStorage.getItem(CARE_KEY)
        if (!raw) return ''
        const cfg = JSON.parse(raw)
        if (cfg && (cfg.fontSize === 'large' || cfg.fontSize === 'xlarge')) {
          return 'vc-care-' + cfg.fontSize
        }
      } catch (e) { /* ignore */ }
      return ''
    }
  },
  beforeDestroy() {
    this.teardown()
  },
  methods: {
    speakerLabel(speaker) {
      return { agent: '坐席', caller: '来电', ai: 'AI' }[speaker] || speaker
    },

    /* ================= WebSocket ================= */

    buildWsUrl() {
      const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
      const token = getToken()
      // N5：浏览器 WebSocket 无法自定义头，JWT 走 query（日志严禁打印完整 URL）
      return `${proto}://${window.location.host}/ws/voice/${this.sessionId}/${this.role}` +
        (token ? `?token=${encodeURIComponent(token)}` : '')
    },

    connect() {
      return new Promise((resolve, reject) => {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
          resolve()
          return
        }
        const ws = new WebSocket(this.buildWsUrl())
        this.ws = ws
        ws.onopen = () => {
          this.connected = true
          resolve()
        }
        ws.onmessage = (evt) => this.handleFrame(evt.data)
        ws.onclose = (evt) => {
          this.connected = false
          this.listening = false
          if (evt.code === 1008) {
            this.lastError = '鉴权失败（1008），请重新登录'
          }
        }
        ws.onerror = () => {
          this.lastError = '语音通道连接失败'
          reject(new Error('ws error'))
        }
      })
    },

    handleFrame(data) {
      let msg
      try {
        msg = JSON.parse(data)
      } catch (e) {
        return
      }
      switch (msg.type) {
        case 'asr_partial':
          this.partialText = msg.text || ''
          this.scrollBottom()
          break
        case 'asr_final': {
          const text = (msg.text || '').trim()
          this.partialText = ''
          if (text) {
            this.pushCaption('agent', text)
            this.$emit('final', text)
          }
          break
        }
        case 'tts_audio':
          this.playPcmChunk(msg)
          break
        case 'tts_end':
          this.ttsPlaying = false
          if (msg.interrupted) this.clearPlayQueue()
          break
        case 'vad_speech_start':
          // A4：服务端 VAD 检测到语音起始——播报中立即清本地播放队列（不等 tts_end，抢 ≤300ms 打断）
          this.peerSpeaking = true
          if (this.ttsPlaying) this.clearPlayQueue()
          break
        case 'vad_speech_end':
          this.peerSpeaking = false
          break
        case 'answer_delta': {
          // E3：机器人应答文本（当前 LLM 非流式单帧全量；turnId 变化即新回合另起一行）
          const t = msg.text || ''
          if (!t) break
          const last = this.captions[this.captions.length - 1]
          if (this._aiTurnId === msg.turnId && last && last.speaker === 'ai') {
            last.text = t
          } else {
            this.pushCaption('ai', t)
            this._aiTurnId = msg.turnId
          }
          this.scrollBottom()
          break
        }
        case 'answer_done': {
          // E3：应答完成——附法条溯源；degraded 为兜底话术（文本本身已提示转人工）
          const last = this.captions[this.captions.length - 1]
          if (this._aiTurnId === msg.turnId && last && last.speaker === 'ai' &&
              msg.sources && msg.sources.length) {
            this.$set(last, 'sources', msg.sources)
          }
          break
        }
        case 'error':
          this.lastError = `${msg.code}: ${msg.message}`
          break
        case 'emotion': {
          // E4：情绪命中——urgent 覆盖 negative；横幅已显示 urgent 时不再降级覆盖
          if (this.emotionAlert && this.emotionAlert.level === 'urgent') break
          this.emotionAlert = {
            level: msg.level,
            intent: msg.intent || null,
            keywords: msg.keywords || [],
            warningId: msg.warningId || null
          }
          break
        }
        default:
          break
      }
    },

    pushCaption(speaker, text) {
      const now = new Date()
      const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
      this.captions.push({ speaker, text, time })
      if (this.captions.length > 200) this.captions.splice(0, this.captions.length - 200)
      this.scrollBottom()
    },

    scrollBottom() {
      this.$nextTick(() => {
        const box = this.$refs.captionBox
        if (box) box.scrollTop = box.scrollHeight
      })
    },

    /* ================= 识别控制 ================= */

    async startListen() {
      this.lastError = ''
      try {
        await this.connect()
      } catch (e) {
        return
      }
      // start 帧：声明引擎与音频格式
      this.ws.send(JSON.stringify({
        type: 'start',
        engine: this.engine,
        format: 'pcm',
        sampleRate: TARGET_SAMPLE_RATE
      }))
      try {
        await this.startCapture()
      } catch (e) {
        this.lastError = '麦克风不可用：' + (e && e.message ? e.message : '未授权或设备缺失')
        return
      }
      this.listening = true
    },

    stopListen() {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'stop' }))
      }
      this.stopCapture()
      this.listening = false
      this.partialText = ''
    },

    clearCaptions() {
      this.captions = []
      this.partialText = ''
      this._aiTurnId = null
    },

    /* ================= 麦克风采集（Float32 → 16kHz S16LE） ================= */

    async startCapture() {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
      const Ctx = window.AudioContext || window.webkitAudioContext
      this.audioCtx = new Ctx()
      const source = this.audioCtx.createMediaStreamSource(this.mediaStream)
      const processor = this.audioCtx.createScriptProcessor(4096, 1, 1)
      const srcRate = this.audioCtx.sampleRate
      this.pcmBuffer = []
      processor.onaudioprocess = (e) => {
        if (!this.listening && !this.ws) return
        const input = e.inputBuffer.getChannelData(0)
        const pcm16 = this.resampleToPcm16(input, srcRate, TARGET_SAMPLE_RATE)
        if (pcm16.length === 0) return
        for (let i = 0; i < pcm16.length; i++) this.pcmBuffer.push(pcm16[i])
        // 按 20ms（320 样本）整帧直发二进制
        while (this.pcmBuffer.length >= FRAME_SAMPLES) {
          const frame = new Int16Array(this.pcmBuffer.splice(0, FRAME_SAMPLES))
          if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(frame.buffer)
          }
        }
      }
      source.connect(processor)
      processor.connect(this.audioCtx.destination)
      this._captureNodes = { source, processor }
    },

    stopCapture() {
      if (this._captureNodes) {
        try {
          this._captureNodes.processor.disconnect()
          this._captureNodes.source.disconnect()
        } catch (e) { /* ignore */ }
        this._captureNodes = null
      }
      if (this.mediaStream) {
        this.mediaStream.getTracks().forEach(t => t.stop())
        this.mediaStream = null
      }
      if (this.audioCtx) {
        this.audioCtx.close().catch(() => {})
        this.audioCtx = null
      }
      this.pcmBuffer = []
    },

    /** 线性插值重采样 + Float32 → S16LE */
    resampleToPcm16(input, srcRate, dstRate) {
      if (srcRate === dstRate) {
        return this.floatTo16(input)
      }
      const ratio = srcRate / dstRate
      const outLen = Math.floor(input.length / ratio)
      const out = new Float32Array(outLen)
      for (let i = 0; i < outLen; i++) {
        const pos = i * ratio
        const idx = Math.floor(pos)
        const frac = pos - idx
        const a = input[idx] || 0
        const b = input[idx + 1] || 0
        out[i] = a + (b - a) * frac
      }
      return this.floatTo16(out)
    },

    floatTo16(float32) {
      const out = new Int16Array(float32.length)
      for (let i = 0; i < float32.length; i++) {
        const v = Math.max(-1, Math.min(1, float32[i]))
        out[i] = v < 0 ? v * 0x8000 : v * 0x7FFF
      }
      return out
    },

    /* ================= TTS 播放（首包即播） ================= */

    playPcmChunk(msg) {
      if (!msg.data) return
      this.playSampleRate = msg.sampleRate || TARGET_SAMPLE_RATE
      const binary = atob(msg.data)
      const bytes = new Uint8Array(binary.length)
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
      const pcm16 = new Int16Array(bytes.buffer)
      const float32 = new Float32Array(pcm16.length)
      for (let i = 0; i < pcm16.length; i++) float32[i] = pcm16[i] / 0x8000
      if (!this.playCtx) {
        const Ctx = window.AudioContext || window.webkitAudioContext
        this.playCtx = new Ctx({ sampleRate: this.playSampleRate })
      }
      const buf = this.playCtx.createBuffer(1, float32.length, this.playSampleRate)
      buf.copyToChannel(float32, 0)
      const src = this.playCtx.createBufferSource()
      src.buffer = buf
      src.connect(this.playCtx.destination)
      // 排队保序播放（首包立即播）
      const startAt = Math.max(this.playCtx.currentTime, this._nextPlayAt || 0)
      src.start(startAt)
      this._nextPlayAt = startAt + buf.duration
      this.playQueue.push(src)
      this.ttsPlaying = true
      src.onended = () => {
        const idx = this.playQueue.indexOf(src)
        if (idx >= 0) this.playQueue.splice(idx, 1)
        if (this.playQueue.length === 0) this.ttsPlaying = false
      }
    },

    clearPlayQueue() {
      this.playQueue.forEach(src => {
        try { src.stop() } catch (e) { /* ignore */ }
      })
      this.playQueue = []
      this._nextPlayAt = 0
      this.ttsPlaying = false
    },

    bargeIn() {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'bargein' }))
      }
      this.clearPlayQueue()
    },

    /* ================= 清理 ================= */

    teardown() {
      this.stopCapture()
      this.clearPlayQueue()
      if (this.playCtx) {
        this.playCtx.close().catch(() => {})
        this.playCtx = null
      }
      if (this.ws) {
        try { this.ws.close(1000, 'component destroy') } catch (e) { /* ignore */ }
        this.ws = null
      }
      this.connected = false
      this.listening = false
      this.peerSpeaking = false
      this.emotionAlert = null
    }
  }
}
</script>

<style scoped>
.vc-card { margin-bottom: 12px; }
.vc-emotion { margin: 8px 0; }
/* E4：关怀模式下预警标题加大（scoped 穿透 element 内部类） */
.vc-emotion-care ::v-deep .el-alert__title { font-size: 18px; }
.vc-header { display: flex; align-items: center; justify-content: space-between; }
.vc-header-right { display: flex; align-items: center; gap: 8px; }
.vc-title { font-weight: 600; }
.vc-engine { width: 110px; }
.vc-captions {
  max-height: 220px; overflow-y: auto; padding: 4px 2px;
  background: #f8fafc; border-radius: 4px;
}
.vc-empty { text-align: center; color: #8C8C8C; padding: 18px 0; font-size: 13px; }
.vc-empty i { font-size: 24px; margin-bottom: 6px; display: block; }
.vc-line { display: flex; align-items: baseline; gap: 8px; padding: 4px 6px; font-size: 14px; line-height: 1.6; }
.vc-speaker {
  flex-shrink: 0; font-size: 12px; padding: 0 6px; border-radius: 3px; color: #fff;
}
.vc-agent .vc-speaker { background: #1A3C6E; }
.vc-caller .vc-speaker { background: #2B8C6E; }
.vc-ai .vc-speaker { background: #764ba2; }
.vc-text { flex: 1; color: #1F2A3A; word-break: break-all; }
.vc-partial .vc-text { color: #8C8C8C; font-style: italic; }
.vc-sources { padding: 0 6px 4px 52px; font-size: 12px; color: #5B7DB8; line-height: 1.5; }
.vc-time { flex-shrink: 0; font-size: 11px; color: #B0BCCA; }
.vc-actions { display: flex; gap: 10px; margin-top: 10px; }
.vc-error {
  margin-top: 8px; padding: 6px 10px; font-size: 12px; color: #b91c1c;
  background: #fef2f2; border: 1px solid #fecaca; border-radius: 4px;
}
/* 关怀模式：大字适配（坐席端 V2.11） */
.vc-care-large .vc-line { font-size: 18px; }
.vc-care-large .vc-text { font-size: 18px; }
.vc-care-xlarge .vc-line { font-size: 22px; }
.vc-care-xlarge .vc-text { font-size: 22px; }
.vc-care-large .vc-captions, .vc-care-xlarge .vc-captions { max-height: 280px; }
</style>
