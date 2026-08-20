<template>
  <div class="sip-incoming-mask" v-if="visible">
    <div class="sip-incoming-card">
      <div class="sip-incoming-icon">
        <i class="el-icon-phone" />
      </div>
      <div class="sip-incoming-info">
        <div class="sip-incoming-title">浏览器分机来电</div>
        <div class="sip-incoming-from">{{ callerDisplay }}</div>
        <div class="sip-incoming-sub">点击接听将使用本设备麦克风进行通话</div>
      </div>
      <div class="sip-incoming-actions">
        <el-button type="success" icon="el-icon-phone" round :loading="answering" @click="handleAnswer">
          接听
        </el-button>
        <el-button type="danger" icon="el-icon-phone-outline" round @click="handleReject">
          拒接
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'SipIncomingAlert',
  data() {
    return {
      answering: false
    }
  },
  computed: {
    ...mapGetters(['sipCall', 'agent']),
    visible() {
      return this.sipCall && this.sipCall.direction === 'incoming' && this.sipCall.state === 'ringing'
    },
    callerDisplay() {
      if (!this.sipCall) return ''
      return this.sipCall.from || this.sipCall.callId || '未知来电'
    }
  },
  methods: {
    handleAnswer() {
      this.answering = true
      const ok = this.$store.dispatch('agent/sipAnswer')
      if (!ok) {
        this.$message.warning('当前没有可接听的来电')
      }
      setTimeout(() => { this.answering = false }, 800)
    },
    handleReject() {
      this.$store.dispatch('agent/sipHangup')
    }
  }
}
</script>

<style lang="scss" scoped>
.sip-incoming-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sip-incoming-card {
  width: 420px;
  padding: 28px 32px 24px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
  text-align: center;
}
.sip-incoming-icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 50%;
  background: linear-gradient(135deg, #67c23a, #36a663);
  color: #fff;
  font-size: 32px;
  line-height: 64px;
  animation: sip-pulse 1.2s ease-in-out infinite;
}
@keyframes sip-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(103, 194, 58, 0.6); }
  50% { box-shadow: 0 0 0 14px rgba(103, 194, 58, 0); }
}
.sip-incoming-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.sip-incoming-from {
  font-size: 22px;
  color: #409eff;
  margin: 6px 0;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.sip-incoming-sub {
  font-size: 12px;
  color: #909399;
  margin-bottom: 18px;
}
.sip-incoming-actions {
  display: flex;
  justify-content: center;
  gap: 18px;
}
</style>
