<template>
  <div v-if="canAccess" class="agent-status-bar">
    <div class="agent-identity">
      <template v-if="agent">
        <el-tag size="mini" effect="plain" type="primary">工号 {{ agent.agentId }}</el-tag>
        <span class="agent-name">{{ agent.agentName || nickName }}</span>
        <span class="agent-account">账号 {{ nickName }}</span>
      </template>
      <template v-else>
        <el-tag size="mini" type="danger" effect="dark">未签入</el-tag>
        <el-button v-hasPermi="['lawyers:call:agent:login']" type="primary" size="mini" plain @click="openLogin">签入</el-button>
      </template>
    </div>

    <div class="agent-status" v-if="agent">
      <span class="status-dot" :class="'dot-' + (agent.status || '0')"></span>
      <el-select v-hasPermi="['lawyers:call:agent:status']" v-model="statusValue" size="mini" class="status-select" @change="handleStatusChange">
        <el-option label="置闲" value="1" />
        <el-option label="置忙" value="2" />
        <el-option label="小休" value="3" />
      </el-select>
      <span class="signin-duration">已签入 {{ signinDuration }}</span>
      <span v-if="agent.callStatus !== '0'" class="ringing-chip" @click="goCallPage">
        <i class="el-icon-phone-outline"></i>
        {{ callStatusText }} {{ agent.currentCallPhone || '' }}
        <i class="el-icon-top-right"></i>
      </span>
    </div>

    <div class="agent-actions" v-if="agent">
      <span v-hasPermi="['lawyers:call:agent:status']" class="mode-label">应答</span>
      <el-radio-group v-hasPermi="['lawyers:call:agent:status']" v-model="modeValue" size="mini" @change="handleModeChange">
        <el-radio-button label="0">自动</el-radio-button>
        <el-radio-button label="1">手动</el-radio-button>
      </el-radio-group>
      <el-button v-hasPermi="['lawyers:call:agent:status']" size="mini" icon="el-icon-phone-outline" @click="openDial">外呼</el-button>
      <el-button v-hasPermi="['lawyers:call:agent:logout']" size="mini" type="warning" plain icon="el-icon-switch-button" @click="handleLogout">签出</el-button>
    </div>

    <el-dialog title="坐席签入" :visible.sync="loginOpen" width="380px" append-to-body :close-on-click-modal="false">
      <el-form label-width="80px" size="small">
        <el-form-item label="工号">
          <el-input v-model="loginForm.agentId" placeholder="请输入坐席工号（数字）" :disabled="!!loginForm.bound" @keyup.enter.native="confirmLogin" />
        </el-form-item>
        <el-form-item v-if="loginForm.agentName" label="坐席">
          <el-input :value="loginForm.agentName" disabled />
        </el-form-item>
        <el-form-item label="账号">
          <el-input :value="nickName" disabled />
        </el-form-item>
        <div class="login-hint">
          {{ loginForm.bound ? '该账号已绑定此工号，确认后签入。' : '首次签入的工号将自动与当前账号绑定；已绑定其他账号的工号无法签入。' }}
        </div>
      </el-form>
      <div slot="footer">
        <el-button @click="loginOpen = false">取 消</el-button>
        <el-button type="primary" :loading="loginLoading" @click="confirmLogin">签 入</el-button>
      </div>
    </el-dialog>

    <el-dialog title="外呼" :visible.sync="dialOpen" width="360px" append-to-body :close-on-click-modal="false">
      <el-form label-width="80px" size="small">
        <el-form-item label="被叫号码">
          <el-input v-model="dialForm.phone" placeholder="请输入号码" @keyup.enter.native="confirmDial" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialOpen = false">取 消</el-button>
        <el-button type="primary" :loading="dialLoading" @click="confirmDial">呼 出</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { getMyAgent, makeCall } from '@/api/lawyers/callCenter'

export default {
  name: 'AgentStatusBar',
  data() {
    return {
      loginOpen: false,
      loginLoading: false,
      loginForm: { agentId: '', agentName: '', bound: false },
      dialOpen: false,
      dialLoading: false,
      dialForm: { phone: '' },
      now: Date.now(),
      timer: null,
      lastCallStatus: '0',
      lastPhone: '',
      pendingOutbound: '',
      currentDirection: 'in'
    }
  },
  computed: {
    ...mapGetters(['nickName', 'userId', 'permissions']),
    canAccess() {
      const perms = this.permissions || []
      return perms.some(p =>
        p === '*:*:*' ||
        p === 'lawyers:call:agent:status' ||
        p === 'lawyers:call:agent:login' ||
        p === 'lawyers:call:agent:logout')
    },
    agent() {
      return this.$store.state.agent.agent
    },
    statusValue: {
      get() { return this.agent ? (this.agent.status || '1') : '1' },
      set() {}
    },
    modeValue: {
      get() { return this.agent ? (this.agent.callMode || '0') : '0' },
      set() {}
    },
    signinDuration() {
      const agent = this.agent
      if (!agent || !agent.loginTime) return '00:00:00'
      const start = Date.parse(String(agent.loginTime).replace(' ', 'T'))
      if (!start) return '00:00:00'
      const sec = Math.max(0, Math.floor((this.now - start) / 1000))
      return this.formatHMS(sec)
    },
    callStatusText() {
      const map = { '0': '空闲', '1': '通话中', '2': '保持', '3': '咨询中', '4': '三方通话', '5': '话后整理' }
      return map[this.agent ? this.agent.callStatus : '0'] || '空闲'
    }
  },
  created() {
    this.$store.dispatch('agent/restore').then(() => {
      this.syncCallState()
      this.startPolling()
    })
    this.timer = setInterval(() => {
      this.now = Date.now()
    }, 1000)
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    startPolling() {
      if (this.pollTimer) clearInterval(this.pollTimer)
      this.pollTimer = setInterval(() => this.refreshAgent(), 5000)
    },
    refreshAgent() {
      if (this.$store.state.agent.agentId == null) return
      this.$store.dispatch('agent/refresh').then(agent => {
        this.syncCallState(agent)
      })
    },
    syncCallState(agent) {
      const current = agent || this.agent
      const callStatus = current ? (current.callStatus || '0') : '0'
      const phone = current ? (current.currentCallPhone || '') : ''

      if (callStatus !== '0') {
        let direction = 'in'
        if (this.pendingOutbound && this.pendingOutbound === phone) {
          direction = 'out'
        }
        this.currentDirection = direction
        const isNewCall = this.lastCallStatus === '0'
        const phoneChanged = phone && phone !== this.lastPhone
        // 呼入/呼出自动进入来电弹屏页面，避免在通话中反复打断用户浏览
        if (isNewCall || phoneChanged) {
          const target = {
            path: '/inbound/callPopup',
            query: {
              callerNumber: phone,
              direction: direction,
              callStatus: callStatus,
              recordId: current ? current.currentCallId : null
            }
          }
          if (this.$route.path === '/inbound/callPopup') {
            this.$router.replace(target).catch(() => {})
          } else {
            this.$router.push(target).catch(() => {})
          }
        }
      } else {
        this.pendingOutbound = ''
      }
      this.lastCallStatus = callStatus
      this.lastPhone = phone || ''
    },
    handleStatusChange(status) {
      this.$store.dispatch('agent/setStatus', status).then(() => {
        this.$message.success('状态已更新')
        this.syncCallState()
      }).catch(() => {})
    },
    handleModeChange(callMode) {
      this.$store.dispatch('agent/setCallMode', callMode).then(() => {
        this.$message.success('应答模式已切换')
      }).catch(() => {})
    },
    openLogin() {
      this.loginForm = { agentId: '', agentName: '', bound: false }
      this.loginOpen = true
      getMyAgent().then(res => {
        const agent = res.data || null
        if (agent && agent.agentId != null) {
          this.loginForm.agentId = String(agent.agentId)
          this.loginForm.agentName = agent.agentName || ''
          this.loginForm.bound = true
        }
      }).catch(() => {})
    },
    confirmLogin() {
      const value = String(this.loginForm.agentId || '').trim()
      if (!/^\d+$/.test(value)) {
        this.$message.warning('请输入数字工号')
        return
      }
      this.loginLoading = true
      this.$store.dispatch('agent/login', {
        agentId: parseInt(value),
        userId: this.userId,
        ip: ''
      }).then(() => {
        this.loginLoading = false
        this.loginOpen = false
        this.loginForm = { agentId: '', agentName: '', bound: false }
        this.$message.success('签入成功')
        this.lastCallStatus = '0'
        return this.$store.dispatch('agent/refresh')
      }).then(agent => {
        this.syncCallState(agent)
        this.startPolling()
      }).catch(() => {
        this.loginLoading = false
      })
    },
    handleLogout() {
      this.$confirm('确定签出当前坐席吗？', '提示', { type: 'warning' }).then(() => {
        this.$store.dispatch('agent/logout').then(() => {
          this.pendingOutbound = ''
          this.$message.success('已签出')
        }).catch(() => {})
      }).catch(() => {})
    },
    openDial() {
      if (this.agent && this.agent.callStatus !== '0') {
        this.$message.warning('当前通话中，请先挂机')
        return
      }
      this.dialForm.phone = ''
      this.dialOpen = true
    },
    confirmDial() {
      const phone = String(this.dialForm.phone || '').trim()
      if (!phone) {
        this.$message.warning('请输入被叫号码')
        return
      }
      this.dialLoading = true
      makeCall({ agentId: this.agent.agentId, phone }).then(() => {
        this.dialLoading = false
        this.dialOpen = false
        this.pendingOutbound = phone
        this.currentDirection = 'out'
        this.lastCallStatus = this.agent.callStatus
        // 呼出成功后直接进入来电弹屏页面
        this.$router.push({
          path: '/inbound/callPopup',
          query: { callerNumber: phone, direction: 'out', callStatus: '1' }
        }).catch(() => {})
        this.$message.success('外呼成功')
        this.refreshAgent()
      }).catch(() => {
        this.dialLoading = false
      })
    },
    goCallPage() {
      this.$router.push({
        path: '/inbound/callPopup',
        query: {
          callerNumber: this.agent.currentCallPhone,
          direction: this.currentDirection,
          callStatus: this.agent.callStatus,
          recordId: this.agent.currentCallId
        }
      })
    },
    formatHMS(sec) {
      const h = Math.floor(sec / 3600)
      const m = Math.floor((sec % 3600) / 60)
      const s = sec % 60
      const pad = n => String(n).padStart(2, '0')
      return `${pad(h)}:${pad(m)}:${pad(s)}`
    }
  }
}
</script>

<style lang="scss" scoped>
.agent-status-bar {
  height: 38px;
  line-height: 38px;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
  font-size: 12px;
  color: #334155;

  .agent-identity {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;

    .agent-name { font-weight: 600; color: #0f172a; }
    .agent-account { color: #94a3b8; }
  }

  .agent-status {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    overflow: hidden;

    .status-dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      flex-shrink: 0;
      background: #94a3b8;

      &.dot-1 { background: #2B8C6E; box-shadow: 0 0 0 3px rgba(43, 140, 110, 0.15); }
      &.dot-2 { background: #E8923A; box-shadow: 0 0 0 3px rgba(232, 146, 58, 0.15); }
      &.dot-3 { background: #7C3AED; box-shadow: 0 0 0 3px rgba(124, 58, 237, 0.15); }
    }

    .status-select { width: 82px; }
    .signin-duration { color: #94a3b8; white-space: nowrap; }

    .ringing-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      height: 24px;
      line-height: 22px;
      padding: 0 10px;
      border-radius: 12px;
      background: #FBECEE;
      color: #C63D4A;
      border: 1px solid #fecaca;
      cursor: pointer;
      white-space: nowrap;
      animation: agent-pulse 1.4s ease-in-out infinite;
    }
  }

  .agent-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;

    .mode-label { color: #94a3b8; }
  }

  .login-hint {
    font-size: 12px;
    color: #94a3b8;
    line-height: 1.6;
    margin-top: 4px;
  }
}

@keyframes agent-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.65; }
}
</style>
