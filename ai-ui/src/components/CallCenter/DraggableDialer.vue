<template>
  <transition name="dialer-fade">
  <div
    v-if="localVisible"
    class="draggable-dialer"
    :class="{ 'is-dragging': dragging, 'is-minimized': minimized }"
    :style="positionStyle"
    role="dialog"
    aria-label="可拖拽拨号框"
  >
    <!-- ============ 标题栏（拖拽手柄） ============ -->
    <div class="dialer-header" @mousedown="startDrag" @touchstart.prevent="startDrag">
      <div class="header-left">
        <i class="el-icon-phone" />
        <span class="title">软电话</span>
        <el-tag v-if="agent" size="mini" :type="statusTagType" effect="dark" class="status-tag">
          {{ statusLabel }}
        </el-tag>
      </div>
      <div class="header-right" @mousedown.stop @touchstart.stop>
        <i class="el-icon-minus" title="最小化" @click="minimized = true" />
        <i class="el-icon-close" title="关闭" @click="closeDialer" />
      </div>
    </div>

    <!-- 最小化浮标 -->
    <div v-if="minimized" class="dialer-fab" @click="minimized = false">
      <i class="el-icon-phone-outline" />
    </div>

    <!-- ============ 主体 ============ -->
    <div v-show="!minimized" class="dialer-body">
      <!-- 线路/分机信息 -->
      <div class="line-info">
        <i class="el-icon-connection" />
        <span class="line-label">分机：</span>
        <span class="line-value">{{ agent && agent.sipExtension ? agent.sipExtension : '未分配' }}</span>
        <el-divider direction="vertical" />
        <span class="line-label">状态：</span>
        <span class="line-value">{{ statusLabel }}</span>
      </div>

      <!-- 通话显示区 -->
      <div class="display-area" :class="callStateClass">
        <div class="display-number">{{ displayText }}</div>
        <div class="display-sub">
          <template v-if="callState === 'idle'">
            <template v-if="contactName">联系人：{{ contactName }}</template>
            <template v-else>请输入号码（可键盘输入）</template>
          </template>
          <template v-else-if="callState === 'dialing'">正在呼叫...</template>
          <template v-else-if="callState === 'ringing'">振铃中...</template>
          <template v-else-if="callState === 'connected'">通话中 {{ formatDuration }}</template>
          <template v-else-if="callState === 'ended'">通话已结束</template>
        </div>
      </div>

      <!-- 联系人搜索 -->
      <div class="search-wrap">
        <el-input
          v-model="searchKey"
          placeholder="搜索联系人姓名/电话"
          prefix-icon="el-icon-search"
          clearable
          size="small"
          @input="onSearch"
        />
        <div v-if="searchResults.length" class="search-results">
          <div
            v-for="item in searchResults"
            :key="item.id"
            class="search-item"
            @click="selectContact(item)"
          >
            <div class="si-name">{{ item.name }}</div>
            <div class="si-phone">{{ item.phone }}</div>
          </div>
        </div>
      </div>

      <!-- 数字键盘 -->
      <div class="keypad" :class="{ disabled: callState === 'connected' || callState === 'ringing' }">
        <div
          v-for="key in keys"
          :key="key.digit"
          class="key"
          @click="pressKey(key.digit)"
        >
          <span class="digit">{{ key.digit }}</span>
          <span v-if="key.letters" class="letters">{{ key.letters }}</span>
        </div>
      </div>

      <!-- 操作按钮区 -->
      <div class="action-bar">
        <!-- 空闲/拨号/振铃：显示拨号+退格 -->
        <template v-if="callState === 'idle' || callState === 'dialing' || callState === 'ended'">
          <button class="act-btn act-clear" title="清除" @click="clearNumber">
            <i class="el-icon-close" />
          </button>
          <button
            class="act-btn act-call"
            :disabled="!phoneNumber"
            :class="{ loading: callState === 'dialing' }"
            title="拨号"
            @click="makeCall"
          >
            <i class="el-icon-phone" />
          </button>
          <button class="act-btn act-back" title="退格" @click="backspace">
            <i class="el-icon-back" />
          </button>
        </template>
        <!-- 通话中：显示挂断+静音+保持 -->
        <template v-else-if="callState === 'connected' || callState === 'ringing'">
          <button class="act-btn act-mute" :class="{ active: muted }" title="静音" @click="toggleMute">
            <i :class="muted ? 'el-icon-turn-off-microphone' : 'el-icon-microphone'" />
          </button>
          <button class="act-btn act-hangup" title="挂断" @click="hangup">
            <i class="el-icon-phone-outline" />
          </button>
          <button class="act-btn act-hold" :class="{ active: held }" title="保持" @click="toggleHold">
            <i :class="held ? 'el-icon-video-play' : 'el-icon-video-pause'" />
          </button>
        </template>
      </div>
    </div>
  </div>
  </transition>
</template>

<script>
import { makeCall as apiMakeCall, hangupCall, holdCall, resumeCall } from '@/api/lawyers/callCenter'

/**
 * 可拖拽拨号框组件
 *
 * 功能：
 *  - 鼠标/触摸按住标题栏拖拽，requestAnimationFrame 保证流畅
 *  - 边界约束：拨号框不超出可视区域
 *  - 数字键盘 0-9 * #，退格/清除
 *  - 联系人搜索（内置模拟数据，可通过 contacts prop 传入）
 *  - 拨号 / 通话中 / 挂断 状态切换与通话计时
 *  - 最小化浮标、ESC 关闭
 *  - 位置记忆（localStorage）
 *
 * Props:
 *  - agent: 当前签入的坐席对象（含 agentId / sipExtension / status / callStatus）
 *  - contacts: Array<{ id, name, phone }> 联系人列表
 *  - storageKey: localStorage 位置存储键名
 */
export default {
  name: 'DraggableDialer',
  props: {
    agent: { type: Object, default: null },
    contacts: { type: Array, default: () => [] },
    storageKey: { type: String, default: 'dialer_position_v1' },
    // 外部控制显隐（来自 Vuex agent.dialerVisible）
    visible: { type: Boolean, default: true }
  },
  data() {
    return {
      // 内部显隐，由 visible prop 同步
      localVisible: this.visible,
      minimized: false,
      // 位置
      posX: 0,
      posY: 0,
      // 拖拽状态
      dragging: false,
      dragOffsetX: 0,
      dragOffsetY: 0,
      rafId: null,
      pendingX: 0,
      pendingY: 0,
      // 号码与拨号
      phoneNumber: '',
      searchKey: '',
      searchResults: [],
      // 通话状态：idle / dialing / ringing / connected / ended
      callState: 'idle',
      contactName: '',
      muted: false,
      held: false,
      duration: 0,
      durationTimer: null,
      // 键盘布局
      keys: [
        { digit: '1', letters: '' },
        { digit: '2', letters: 'ABC' },
        { digit: '3', letters: 'DEF' },
        { digit: '4', letters: 'GHI' },
        { digit: '5', letters: 'JKL' },
        { digit: '6', letters: 'MNO' },
        { digit: '7', letters: 'PQRS' },
        { digit: '8', letters: 'TUV' },
        { digit: '9', letters: 'WXYZ' },
        { digit: '*', letters: '' },
        { digit: '0', letters: '+' },
        { digit: '#', letters: '' }
      ]
    }
  },
  computed: {
    // 坐席状态文字
    statusLabel() {
      if (!this.agent) return '未签入'
      const map = { '0': '离线', '1': '空闲', '2': '置忙', '3': '通话中', '4': '话后处理' }
      return map[this.agent.status] || '未知'
    },
    statusTagType() {
      if (!this.agent) return 'info'
      const map = { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger', '4': '' }
      return map[this.agent.status] || 'info'
    },
    // 通话状态样式
    callStateClass() {
      return 'state-' + this.callState
    },
    // 显示号码（通话中显示对方号码，空闲时显示已输入号码）
    displayText() {
      if (this.phoneNumber) return this.phoneNumber
      if (this.callState === 'connected' || this.callState === 'ringing' || this.callState === 'dialing') {
        return this.currentPeerNumber || ''
      }
      return ''
    },
    currentPeerNumber() {
      return this._peerNumber || ''
    },
    // 通话计时 mm:ss
    formatDuration() {
      const m = Math.floor(this.duration / 60)
      const s = this.duration % 60
      return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    },
    // 位置样式（transform 性能优于 left/top）
    positionStyle() {
      return {
        transform: `translate3d(${this.posX}px, ${this.posY}px, 0)`
      }
    }
  },
  watch: {
    // 外部 prop 控制显隐（坐席状态栏"外呼"按钮打开）
    visible(val) {
      this.localVisible = val
      if (val) {
        this.minimized = false
        this.$nextTick(() => {
          this.constrainPosition()
          // 打开时自动聚焦号码输入（物理键盘可直接输入）
          const input = this.$el && this.$el.querySelector('.search-wrap input')
          if (input) input.focus()
        })
      }
    },
    // 坐席对象变化时，同步通话状态（由 CTI 推送驱动）
    agent: {
      immediate: true,
      handler(val) {
        if (!val) {
          this.resetCall()
          return
        }
        // 坐席 callStatus: 0空闲 1振铃 2通话 3保持
        if (val.callStatus === '2' && this.callState !== 'connected') {
          this.enterConnected(val.currentCallPhone)
        } else if (val.callStatus === '1' && this.callState === 'idle') {
          this.callState = 'ringing'
        } else if (val.callStatus === '0' && (this.callState === 'connected' || this.callState === 'ringing')) {
          this.endCall()
        }
      }
    }
  },
  mounted() {
    this.initPosition()
    window.addEventListener('resize', this.constrainPosition)
    window.addEventListener('keydown', this.onKeydown)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.constrainPosition)
    window.removeEventListener('keydown', this.onKeydown)
    this.removeDragListeners()
    this.clearDurationTimer()
  },
  methods: {
    // ===================== 位置初始化 & 边界约束 =====================
    initPosition() {
      // 默认右下角，距离右边 32px，底部 32px
      const defaultX = () => Math.max(16, window.innerWidth - this.$el.offsetWidth - 32)
      const defaultY = () => Math.max(16, window.innerHeight - this.$el.offsetHeight - 32)
      let saved = null
      try {
        saved = JSON.parse(localStorage.getItem(this.storageKey))
      } catch (e) { saved = null }
      this.posX = saved && typeof saved.x === 'number' ? saved.x : defaultX()
      this.posY = saved && typeof saved.y === 'number' ? saved.y : defaultY()
      this.$nextTick(this.constrainPosition)
    },
    /** 将拨号框约束在可视区域内 */
    constrainPosition() {
      if (!this.$el) return
      const w = this.$el.offsetWidth
      const h = this.minimized ? 56 : this.$el.offsetHeight
      const maxX = window.innerWidth - w
      const maxY = window.innerHeight - h
      this.posX = Math.min(Math.max(0, this.posX), Math.max(0, maxX))
      this.posY = Math.min(Math.max(0, this.posY), Math.max(0, maxY))
    },
    savePosition() {
      try {
        localStorage.setItem(this.storageKey, JSON.stringify({ x: this.posX, y: this.posY }))
      } catch (e) { /* ignore */ }
    },

    // ===================== 拖拽（鼠标 + 触摸 + RAF） =====================
    startDrag(e) {
      // 最小化状态不拖拽（浮标点击还原）
      if (this.minimized) return
      const point = e.touches ? e.touches[0] : e
      const rect = this.$el.getBoundingClientRect()
      this.dragOffsetX = point.clientX - rect.left
      this.dragOffsetY = point.clientY - rect.top
      this.dragging = true
      // 拖拽期间禁用文本选择和过渡动画
      document.body.style.userSelect = 'none'
      window.addEventListener('mousemove', this.onDragMove)
      window.addEventListener('mouseup', this.stopDrag)
      window.addEventListener('touchmove', this.onDragMove, { passive: false })
      window.addEventListener('touchend', this.stopDrag)
    },
    onDragMove(e) {
      if (!this.dragging) return
      if (e.cancelable) e.preventDefault()
      const point = e.touches ? e.touches[0] : e
      this.pendingX = point.clientX - this.dragOffsetX
      this.pendingY = point.clientY - this.dragOffsetY
      // 用 rAF 合批位置更新，避免高频事件导致重排卡顿
      if (this.rafId == null) {
        this.rafId = requestAnimationFrame(this.applyDragPosition)
      }
    },
    applyDragPosition() {
      this.rafId = null
      const w = this.$el.offsetWidth
      const h = this.minimized ? 56 : this.$el.offsetHeight
      const maxX = window.innerWidth - w
      const maxY = window.innerHeight - h
      this.posX = Math.min(Math.max(0, this.pendingX), Math.max(0, maxX))
      this.posY = Math.min(Math.max(0, this.pendingY), Math.max(0, maxY))
    },
    stopDrag() {
      if (!this.dragging) return
      this.dragging = false
      document.body.style.userSelect = ''
      this.removeDragListeners()
      if (this.rafId != null) {
        cancelAnimationFrame(this.rafId)
        this.rafId = null
        this.applyDragPosition()
      }
      this.savePosition()
    },
    removeDragListeners() {
      window.removeEventListener('mousemove', this.onDragMove)
      window.removeEventListener('mouseup', this.stopDrag)
      window.removeEventListener('touchmove', this.onDragMove)
      window.removeEventListener('touchend', this.stopDrag)
    },

    // ===================== 键盘输入 =====================
    pressKey(d) {
      if (this.callState === 'connected' || this.callState === 'ringing') return
      if (this.phoneNumber.length >= 20) return
      this.phoneNumber += d
      this.lookupContactByPhone(this.phoneNumber)
      // 输入新号码后重置为空闲态
      if (this.callState === 'ended') this.callState = 'idle'
    },
    backspace() {
      if (this.callState === 'connected' || this.callState === 'ringing') return
      this.phoneNumber = this.phoneNumber.slice(0, -1)
      if (!this.phoneNumber) this.contactName = ''
      this.lookupContactByPhone(this.phoneNumber)
    },
    clearNumber() {
      this.phoneNumber = ''
      this.contactName = ''
      this.searchKey = ''
      this.searchResults = []
      if (this.callState === 'ended') this.callState = 'idle'
    },
    /** 物理键盘监听：0-9、*、# 输入，Backspace 退格，Enter 拨号，ESC 关闭 */
    onKeydown(e) {
      if (!this.localVisible || this.minimized) return
      // 输入框聚焦时不拦截
      const tag = (e.target.tagName || '').toLowerCase()
      if (tag === 'input' || tag === 'textarea') return
      if (e.key >= '0' && e.key <= '9') { this.pressKey(e.key); e.preventDefault() }
      else if (e.key === '*' || e.key === '#') { this.pressKey(e.key); e.preventDefault() }
      else if (e.key === 'Backspace') { this.backspace(); e.preventDefault() }
      else if (e.key === 'Enter' && this.phoneNumber) { this.makeCall(); e.preventDefault() }
      else if (e.key === 'Escape') { this.closeDialer() }
    },

    // ===================== 联系人搜索 =====================
    onSearch() {
      const key = this.searchKey.trim().toLowerCase()
      if (!key) { this.searchResults = []; return }
      this.searchResults = this.contacts
        .filter(c =>
          (c.name && c.name.toLowerCase().includes(key)) ||
          (c.phone && c.phone.includes(key))
        )
        .slice(0, 8)
    },
    selectContact(item) {
      this.phoneNumber = item.phone
      this.contactName = item.name
      this.searchKey = ''
      this.searchResults = []
    },
    /** 根据已拨号码模糊匹配联系人（DTMF 反查） */
    lookupContactByPhone(phone) {
      if (!phone) { this.contactName = ''; return }
      const hit = this.contacts.find(c => c.phone && c.phone.replace(/\D/g, '').endsWith(phone.replace(/\D/g, '')))
      this.contactName = hit ? hit.name : ''
    },

    // ===================== 拨号 / 挂断 / 通话状态 =====================
    makeCall() {
      const phone = String(this.phoneNumber || '').trim()
      if (!phone) { this.$message.warning('请输入被叫号码'); return }
      if (!this.agent || !this.agent.agentId) { this.$message.warning('请先签入坐席'); return }
      if (this.callState === 'connected' || this.callState === 'ringing') {
        this.$message.warning('当前通话中，请先挂机'); return
      }
      this.callState = 'dialing'
      this._peerNumber = phone
      apiMakeCall({ agentId: this.agent.agentId, phone }).then(() => {
        this.$message.success('外呼请求已提交')
        // 通知父组件（布局层可据此跳转到通话弹屏页）
        this.$emit('call-made', { agentId: this.agent.agentId, phone })
        // 由 agent.callStatus 推送驱动进入振铃/通话；5 秒未进入则回退
        setTimeout(() => {
          if (this.callState === 'dialing') this.callState = 'idle'
        }, 5000)
      }).catch(() => {
        this.callState = 'idle'
      })
    },
    hangup() {
      if (!this.agent || !this.agent.agentId) { this.endCall(); return }
      hangupCall({ agentId: this.agent.agentId }).then(() => {
        this.endCall()
      }).catch(() => {
        this.endCall()
      })
    },
    /** 进入通话中状态，启动计时 */
    enterConnected(peer) {
      this.callState = 'connected'
      if (peer) this._peerNumber = peer
      this.duration = 0
      this.clearDurationTimer()
      this.durationTimer = setInterval(() => { this.duration += 1 }, 1000)
    },
    /** 通话结束 */
    endCall() {
      this.callState = 'ended'
      this.clearDurationTimer()
      this.muted = false
      this.held = false
      setTimeout(() => {
        if (this.callState === 'ended') this.callState = 'idle'
      }, 2000)
    },
    resetCall() {
      this.callState = 'idle'
      this.clearDurationTimer()
      this.phoneNumber = ''
      this.contactName = ''
      this._peerNumber = ''
    },
    clearDurationTimer() {
      if (this.durationTimer) { clearInterval(this.durationTimer); this.durationTimer = null }
    },
    toggleMute() {
      this.muted = !this.muted
      this.$message.info(this.muted ? '已静音' : '已取消静音')
      // 浏览器侧静音由 webrtcSipPhone 控制；此处仅 UI 状态，CTI 侧静音可通过后端扩展
    },
    toggleHold() {
      if (!this.agent || !this.agent.agentId) return
      const action = this.held ? resumeCall : holdCall
      action({ agentId: this.agent.agentId }).then(() => {
        this.held = !this.held
        this.$message.info(this.held ? '通话已保持' : '已恢复通话')
      }).catch(() => {})
    },
    closeDialer() {
      this.localVisible = false
      this.$emit('close')
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style lang="scss" scoped>
$w: 320px;
$header-h: 44px;
$radius: 10px;
$primary: #1890ff;
$success: #52c41a;
$danger: #ff4d4f;

.draggable-dialer {
  position: fixed;
  top: 0;
  left: 0;
  width: $w;
  z-index: 2000;
  background: #fff;
  border-radius: $radius;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  user-select: none;
  will-change: transform;
  transition: box-shadow 0.2s ease, opacity 0.2s ease;

  // 拖拽时的视觉反馈
  &.is-dragging {
    box-shadow: 0 16px 48px rgba(24, 144, 255, 0.35);
    opacity: 0.96;
    cursor: grabbing;
    .dialer-header { cursor: grabbing; }
  }

  &.is-minimized {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    overflow: visible;
  }
}

// ===================== 标题栏 =====================
.dialer-header {
  height: $header-h;
  padding: 0 12px;
  background: linear-gradient(135deg, $primary, #096dd9);
  color: #fff;
  border-radius: $radius $radius 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: grab;
  touch-action: none;

  .header-left {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    font-weight: 600;
    .el-icon-phone { font-size: 16px; }
    .status-tag { margin-left: 4px; }
  }
  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    i {
      cursor: pointer;
      font-size: 15px;
      opacity: 0.85;
      transition: opacity 0.15s;
      &:hover { opacity: 1; }
    }
  }
}

// 最小化浮标
.dialer-fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, $primary, #096dd9);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.4);
}

// ===================== 主体 =====================
.dialer-body {
  padding: 10px 12px 12px;
}

.line-info {
  font-size: 12px;
  color: #666;
  padding: 4px 2px 8px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 8px;
  .el-icon-connection { color: $primary; margin-right: 4px; }
  .line-label { color: #999; }
  .line-value { color: #333; font-weight: 500; }
}

// 通话显示区
.display-area {
  text-align: center;
  padding: 12px 8px;
  border-radius: 8px;
  background: #f7f9fc;
  margin-bottom: 10px;
  transition: background 0.3s;

  .display-number {
    font-size: 22px;
    font-weight: 600;
    color: #222;
    letter-spacing: 1px;
    min-height: 30px;
    word-break: break-all;
  }
  .display-sub {
    font-size: 12px;
    color: #999;
    margin-top: 4px;
    min-height: 16px;
  }
  &.state-dialing { background: #e6f7ff; .display-sub { color: $primary; } }
  &.state-ringing { background: #fff7e6; .display-sub { color: #fa8c16; animation: blink 1s infinite; } }
  &.state-connected { background: #f6ffed; .display-sub { color: $success; font-weight: 600; } }
  &.state-ended { background: #f5f5f5; .display-sub { color: #999; } }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

// 搜索
.search-wrap {
  position: relative;
  margin-bottom: 10px;
  .search-results {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    max-height: 180px;
    overflow-y: auto;
    background: #fff;
    border: 1px solid #e8e8e8;
    border-radius: 4px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    z-index: 10;
    .search-item {
      padding: 6px 10px;
      cursor: pointer;
      font-size: 13px;
      &:hover { background: #f0f7ff; }
      .si-name { color: #333; }
      .si-phone { color: #999; font-size: 12px; }
    }
  }
}

// 数字键盘
.keypad {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-bottom: 10px;

  &.disabled .key { opacity: 0.5; pointer-events: none; }

  .key {
    height: 48px;
    border-radius: 8px;
    background: #f7f9fc;
    border: 1px solid #eef0f3;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.12s;
    -webkit-tap-highlight-color: transparent;

    .digit { font-size: 20px; font-weight: 600; color: #222; line-height: 1; }
    .letters { font-size: 9px; color: #999; letter-spacing: 1px; margin-top: 2px; min-height: 10px; }

    &:hover { background: #e6f7ff; border-color: #91d5ff; }
    &:active { background: #bae7ff; transform: scale(0.95); }
  }
}

// 操作栏
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 4px 0;

  .act-btn {
    width: 52px;
    height: 52px;
    border-radius: 50%;
    border: none;
    cursor: pointer;
    font-size: 20px;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s;
    outline: none;
    &:disabled { opacity: 0.45; cursor: not-allowed; }
    &:not(:disabled):active { transform: scale(0.92); }
  }
  .act-call {
    width: 60px;
    height: 60px;
    background: $success;
    box-shadow: 0 4px 12px rgba(82, 196, 26, 0.4);
    &.loading { background: #faad14; animation: pulse 1s infinite; }
  }
  .act-hangup {
    width: 60px;
    height: 60px;
    background: $danger;
    box-shadow: 0 4px 12px rgba(255, 77, 79, 0.4);
    transform: rotate(135deg);
  }
  .act-clear, .act-back {
    background: #f0f0f0;
    color: #666;
    &:hover { background: #e0e0e0; }
  }
  .act-mute, .act-hold {
    background: #f0f0f0;
    color: #666;
    &.active { background: $primary; color: #fff; }
  }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 4px 12px rgba(250, 173, 20, 0.4); }
  50% { box-shadow: 0 4px 24px rgba(250, 173, 20, 0.7); }
}

// ===================== 响应式 =====================
@media (max-width: 480px) {
  .draggable-dialer {
    width: calc(100vw - 24px) !important;
    max-width: 340px;
  }
  .keypad .key { height: 52px; }
}

@media (max-height: 600px) {
  .keypad .key { height: 40px; }
  .display-area { padding: 8px; }
}

// 高对比度 / 深色模式适配
@media (prefers-color-scheme: dark) {
  .draggable-dialer {
    background: #1f1f1f;
    .line-info { border-bottom-color: #333; .line-value { color: #ddd; } }
    .display-area { background: #2a2a2a; .display-number { color: #f0f0f0; } }
    .keypad .key {
      background: #2a2a2a; border-color: #3a3a3a;
      .digit { color: #f0f0f0; }
      &:hover { background: #1f3a5f; }
    }
    .search-results .search-item { background: #1f1f1f; &:hover { background: #2a2a2a; } }
  }
}

// 显隐过渡（仅用 opacity，避免与定位用的 transform: translate3d 冲突）
.dialer-fade-enter-active,
.dialer-fade-leave-active {
  transition: opacity 0.18s ease;
}
.dialer-fade-enter,
.dialer-fade-leave-to {
  opacity: 0;
}
</style>
