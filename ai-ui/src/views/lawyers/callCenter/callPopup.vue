<template>
  <div class="call-popup">
    <!-- 来电横幅 -->
    <div class="cp-banner">
      <div class="cp-banner-left">
        <div class="cp-banner-icon"><i class="el-icon-phone"></i></div>
        <div class="cp-banner-title">
          <div class="cp-banner-main">来电接入</div>
          <div class="cp-banner-sub"><span class="cp-dot"></span>{{ connected ? '正在通话中' : '等待接听' }}</div>
        </div>
      </div>
      <div class="cp-banner-center">
        <div class="cp-banner-number">{{ profileData.callerNumber || '-' }}</div>
        <div class="cp-banner-info">
          <span><i class="el-icon-location-outline"></i> {{ profileData.address || '归属地未知' }}</span>
        </div>
      </div>
      <div class="cp-banner-right">
        <div class="cp-banner-time">
          <div class="cp-time-label">通话时长</div>
          <div class="cp-time-value">{{ callDuration }}</div>
        </div>
      </div>
    </div>

    <el-row :gutter="16" class="cp-main">
      <!-- 左侧：来电人信息 + 智能分析 -->
      <el-col :span="8">
        <el-card shadow="never" class="cp-card">
          <div slot="header" class="cp-card-header">
            <span>来电人信息</span>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEditProfile">编辑</el-button>
          </div>
          <div class="cp-caller-header">
            <div class="cp-avatar">{{ avatarText }}</div>
            <div class="cp-caller-info">
              <div class="cp-caller-name">
                {{ profileData.callerName || '未知来电人' }}
                <el-tag size="mini" type="warning" effect="light" v-if="profileData.monthCallCount">第{{ profileData.monthCallCount }}次来电</el-tag>
              </div>
              <div class="cp-caller-phone">{{ profileData.callerNumber || '-' }}</div>
            </div>
          </div>
          <div class="cp-caller-detail">
            <div class="cp-detail-item"><span class="cp-detail-label">归属地</span><span class="cp-detail-value">{{ profileData.address || '-' }}</span></div>
            <div class="cp-detail-item"><span class="cp-detail-label">来电次数</span><span class="cp-detail-value">本月{{ profileData.monthCallCount || 0 }}次 / 累计{{ profileData.callCount || 0 }}次</span></div>
            <div class="cp-detail-item"><span class="cp-detail-label">最后来电</span><span class="cp-detail-value">{{ profileData.lastCallTime || '-' }}</span></div>
          </div>
          <div class="cp-caller-tags" v-if="profileData.tags">
            <el-tag v-for="(tag, idx) in tagsArray" :key="idx" size="mini" effect="dark" :type="tagType(idx)">{{ tag }}</el-tag>
          </div>
        </el-card>

        <!-- 智能来电分析 -->
        <el-card shadow="never" class="cp-card cp-ai-card">
          <div slot="header" class="cp-card-header">
            <span class="cp-ai-title"><span class="cp-ai-badge">AI</span> 智能来电分析</span>
          </div>
          <el-row :gutter="8" class="cp-ai-grid">
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">来电意图预测</div>
                <div class="cp-ai-value">{{ profileData.intentPrediction }}</div>
                <div class="cp-ai-confidence">
                  <el-progress :percentage="profileData.intentConfidence || 0" :show-text="false" stroke-width="4" color="#8b5cf6" />
                  <span>置信度 {{ profileData.intentConfidence || 0 }}%</span>
                </div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">风险等级</div>
                <div class="cp-ai-value" :class="'cp-risk-' + profileData.riskLevel">{{ riskLabel(profileData.riskLevel) }}</div>
                <div class="cp-ai-sub">{{ profileData.consultPreference || '暂无偏好数据' }}</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">高频问题</div>
                <div class="cp-ai-value cp-ai-freq">{{ profileData.highFreqProblem || '暂无' }}</div>
                <div class="cp-ai-sub" v-if="profileData.freqMentionCount">提及{{ profileData.freqMentionCount }}次</div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="cp-ai-item">
                <div class="cp-ai-label">情绪状态</div>
                <div class="cp-ai-value">{{ profileData.emotionStatus || '平稳' }}</div>
                <div class="cp-ai-sub">{{ profileData.customerLevel || '普通客户' }}</div>
              </div>
            </el-col>
          </el-row>
          <div class="cp-emotion-warning" v-if="profileData.emotionWarning">
            <i class="el-icon-warning-outline"></i>
            <span>{{ profileData.emotionWarning }}</span>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：5 标签页 -->
      <el-col :span="16">
        <el-card shadow="never" class="cp-card cp-history-card">
          <div slot="header" class="cp-card-header">
            <el-tabs v-model="activeTab" class="cp-history-tabs">
              <el-tab-pane label="基本信息" name="base"></el-tab-pane>
              <el-tab-pane label="历史通话" name="call"></el-tab-pane>
              <el-tab-pane label="工单记录" name="ticket"></el-tab-pane>
              <el-tab-pane label="来电轨迹" name="track"></el-tab-pane>
              <el-tab-pane label="智能分析" name="analysis"></el-tab-pane>
            </el-tabs>
          </div>
          <div class="cp-history-content" v-loading="tabLoading">
            <!-- 基本信息 -->
            <div v-if="activeTab === 'base'">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="来电号码">{{ profileData.callerNumber || '-' }}</el-descriptions-item>
                <el-descriptions-item label="来电人">{{ profileData.callerName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ genderLabel(profileData.callerGender) }}</el-descriptions-item>
                <el-descriptions-item label="年龄">{{ profileData.callerAge || '-' }}</el-descriptions-item>
                <el-descriptions-item label="身份证号">{{ profileData.callerIdCard || '-' }}</el-descriptions-item>
                <el-descriptions-item label="客户等级">{{ profileData.customerLevel || '-' }}</el-descriptions-item>
                <el-descriptions-item label="联系地址" :span="2">{{ profileData.address || '-' }}</el-descriptions-item>
                <el-descriptions-item label="标签" :span="2">{{ profileData.tags || '-' }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <!-- 历史通话 -->
            <div v-else-if="activeTab === 'call'">
              <div v-if="historyList.length === 0" class="cp-empty">暂无历史通话</div>
              <div class="cp-call-item" v-for="item in historyList" :key="item.recordId">
                <div class="cp-call-icon"><i class="el-icon-phone"></i></div>
                <div class="cp-call-main">
                  <div class="cp-call-header">
                    <span class="cp-call-title">{{ item.categoryName || '通话记录' }}</span>
                    <el-tag size="mini" :type="recordStatusTag(item.status)" effect="plain">{{ recordStatusLabel(item.status) }}</el-tag>
                    <span class="cp-call-time">{{ item.callTime }}</span>
                  </div>
                  <div class="cp-call-footer">
                    <span class="cp-call-duration">时长 {{ formatDuration(item.duration) }}</span>
                    <span class="cp-call-content">{{ item.content || '' }}</span>
                  </div>
                </div>
              </div>
            </div>
            <!-- 工单记录 -->
            <div v-else-if="activeTab === 'ticket'">
              <div v-if="ticketList.length === 0" class="cp-empty">暂无工单记录</div>
              <el-table :data="ticketList" size="small" border v-else>
                <el-table-column label="工单号" prop="ticketNo" width="140" />
                <el-table-column label="工单标题" prop="title" />
                <el-table-column label="优先级" align="center" width="80">
                  <template slot-scope="scope">{{ priorityLabel(scope.row.priority) }}</template>
                </el-table-column>
                <el-table-column label="状态" align="center" width="90">
                  <template slot-scope="scope"><el-tag size="mini" :type="ticketStatusTag(scope.row.status)">{{ ticketStatusLabel(scope.row.status) }}</el-tag></template>
                </el-table-column>
                <el-table-column label="创建时间" prop="createTime" width="160" />
              </el-table>
            </div>
            <!-- 来电轨迹 -->
            <div v-else-if="activeTab === 'track'">
              <div v-if="trackList.length === 0" class="cp-empty">暂无来电轨迹</div>
              <el-timeline v-else>
                <el-timeline-item v-for="(node, idx) in trackList" :key="idx" :timestamp="node.time" placement="top" :type="node.type === 'call' ? 'primary' : 'success'">
                  <el-tag size="mini" :type="node.type === 'call' ? 'primary' : 'success'">{{ node.type === 'call' ? '通话' : '工单' }}</el-tag>
                  <span class="cp-track-title">{{ node.title }}</span>
                  <div class="cp-track-content">{{ node.content || '' }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
            <!-- 智能分析 -->
            <div v-else-if="activeTab === 'analysis'">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="意图预测">{{ profileData.intentPrediction }}</el-descriptions-item>
                <el-descriptions-item label="意图置信度">{{ profileData.intentConfidence || 0 }}%</el-descriptions-item>
                <el-descriptions-item label="咨询偏好">{{ profileData.consultPreference || '-' }}</el-descriptions-item>
                <el-descriptions-item label="高频问题">{{ profileData.highFreqProblem || '-' }}</el-descriptions-item>
                <el-descriptions-item label="风险等级">{{ riskLabel(profileData.riskLevel) }}</el-descriptions-item>
                <el-descriptions-item label="情绪状态">{{ profileData.emotionStatus || '-' }}</el-descriptions-item>
                <el-descriptions-item label="情绪预警" :span="2">{{ profileData.emotionWarning || '无' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4 操作按钮 -->
    <div class="cp-action-bar">
      <el-button type="success" size="medium" class="cp-action-btn cp-btn-green" @click="handleAnswer" :disabled="connected">
        <i class="el-icon-microphone"></i><span>接听</span>
      </el-button>
      <el-button type="primary" size="medium" class="cp-action-btn cp-btn-blue" @click="handleLedger">
        <i class="el-icon-document"></i><span>一键登记台账</span>
      </el-button>
      <el-button size="medium" class="cp-action-btn cp-btn-gray" @click="handleTransfer">
        <i class="el-icon-s-promotion"></i><span>转接</span>
      </el-button>
      <el-button size="medium" class="cp-action-btn cp-btn-purple" @click="handleHold">
        <i class="el-icon-video-pause"></i><span>挂起</span>
      </el-button>
    </div>

    <!-- 编辑档案弹窗 -->
    <el-dialog title="编辑来电人档案" :visible.sync="editOpen" width="600px" append-to-body>
      <el-form ref="profileForm" :model="profileForm" label-width="100px" size="small">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="来电人"><el-input v-model="profileForm.callerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来电号码"><el-input v-model="profileForm.callerNumber" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="性别"><el-select v-model="profileForm.callerGender" style="width:100%"><el-option label="男" value="0" /><el-option label="女" value="1" /><el-option label="未知" value="2" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="年龄"><el-input-number v-model="profileForm.callerAge" :min="0" :max="150" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="联系地址"><el-input v-model="profileForm.callerAddress" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户等级"><el-input v-model="profileForm.customerLevel" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="标签"><el-input v-model="profileForm.tags" placeholder="多个用逗号分隔" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="意图预测"><el-input v-model="profileForm.intentPrediction" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="意图置信度"><el-input-number v-model="profileForm.intentConfidence" :min="0" :max="100" controls-position="right" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险等级"><el-select v-model="profileForm.riskLevel" style="width:100%"><el-option label="低" value="0" /><el-option label="中" value="1" /><el-option label="高" value="2" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="情绪状态"><el-input v-model="profileForm.emotionStatus" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer">
        <el-button @click="editOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitProfile">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getCallerProfile, getCallerHistory, getCallerTickets, getCallerTrack, updateCallerProfile } from "@/api/lawyers/callPopup"
import { autoFillLedger, addLedger, transferCall, holdCall } from "@/api/lawyers/callCenter"

export default {
  name: "CallPopup",
  data() {
    return {
      connected: false,
      activeTab: 'base',
      tabLoading: false,
      callDuration: '00:00:00',
      seconds: 0,
      timer: null,
      profileData: {},
      historyList: [],
      ticketList: [],
      trackList: [],
      editOpen: false,
      profileForm: {}
    }
  },
  computed: {
    avatarText() {
      const name = this.profileData.callerName
      if (name && name.length > 0) return name.charAt(0)
      return '?'
    },
    tagsArray() {
      if (!this.profileData.tags) return []
      return this.profileData.tags.split(/[,，]/).filter(t => t)
    }
  },
  watch: {
    activeTab(val) {
      if (val === 'call' && this.historyList.length === 0) this.loadHistory()
      if (val === 'ticket' && this.ticketList.length === 0) this.loadTickets()
      if (val === 'track' && this.trackList.length === 0) this.loadTrack()
    }
  },
  created() {
    const callerNumber = this.$route.query.callerNumber || this.$route.params.callerNumber
    if (callerNumber) {
      this.loadProfile(callerNumber)
    }
  },
  beforeDestroy() { this.clearTimer() },
  methods: {
    loadProfile(callerNumber) {
      getCallerProfile(callerNumber).then(res => {
        const data = res.data || {}
        this.profileData = data
        if (data.profile) {
          const p = data.profile
          this.profileData.callerName = this.profileData.callerName || p.callerName
          this.profileData.callerNumber = this.profileData.callerNumber || p.callerNumber
          this.profileData.callerGender = p.callerGender
          this.profileData.callerAge = p.callerAge
          this.profileData.callerIdCard = p.callerIdCard
          this.profileData.address = this.profileData.address || p.callerAddress
          this.profileData.profileId = p.profileId
        }
      }).catch(() => {})
    },
    loadHistory() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerHistory(this.profileData.callerNumber, 20).then(res => {
        this.historyList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    loadTickets() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerTickets(this.profileData.callerNumber).then(res => {
        this.ticketList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    loadTrack() {
      if (!this.profileData.callerNumber) return
      this.tabLoading = true
      getCallerTrack(this.profileData.callerNumber).then(res => {
        this.trackList = res.data || []
        this.tabLoading = false
      }).catch(() => { this.tabLoading = false })
    },
    handleAnswer() {
      this.connected = true
      this.startTimer()
      this.$message.success('已接听来电')
    },
    startTimer() {
      this.timer = setInterval(() => { this.seconds++; this.callDuration = this.formatTime(this.seconds) }, 1000)
    },
    clearTimer() { if (this.timer) { clearInterval(this.timer); this.timer = null } },
    formatTime(s) {
      const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
      return [h, m, sec].map(n => String(n).padStart(2, '0')).join(':')
    },
    handleLedger() {
      const recordId = this.$route.query.recordId
      if (!recordId) { this.$message.warning('无关联来电记录，请手动登记'); return }
      autoFillLedger(recordId).then(res => {
        const data = res.data || {}
        addLedger(data).then(() => {
          this.$message.success('已一键登记台账')
        }).catch(() => {})
      }).catch(() => {})
    },
    handleTransfer() {
      this.$prompt('请输入转接目标号码', '转接来电', {
        confirmButtonText: '转接', cancelButtonText: '取消'
      }).then(({ value }) => {
        transferCall({ targetNumber: value, callerNumber: this.profileData.callerNumber }).then(() => {
          this.$message.success('转接请求已提交')
        }).catch(() => {})
      }).catch(() => {})
    },
    handleHold() {
      holdCall({ callerNumber: this.profileData.callerNumber }).then(() => {
        this.$message.success('已挂起通话')
      }).catch(() => {})
    },
    handleEditProfile() {
      const p = this.profileData
      this.profileForm = {
        profileId: p.profileId, callerNumber: p.callerNumber, callerName: p.callerName,
        callerGender: p.callerGender, callerAge: p.callerAge, callerIdCard: p.callerIdCard,
        callerAddress: p.address, customerLevel: p.customerLevel, tags: p.tags,
        intentPrediction: p.intentPrediction, intentConfidence: p.intentConfidence,
        riskLevel: p.riskLevel, emotionStatus: p.emotionStatus
      }
      this.editOpen = true
    },
    submitProfile() {
      updateCallerProfile(this.profileForm).then(() => {
        this.$message.success('保存成功')
        this.editOpen = false
        if (this.profileData.callerNumber) this.loadProfile(this.profileData.callerNumber)
      }).catch(() => {})
    },
    genderLabel(g) { return { '0': '男', '1': '女', '2': '未知' }[g] || '未知' },
    riskLabel(r) { return { '0': '低风险', '1': '中风险', '2': '高风险' }[r] || '低风险' },
    recordStatusLabel(s) { return { '0': '接通中', '1': '已完成', '2': '已转接', '3': '未接' }[s] || '未知' },
    recordStatusTag(s) { return { '0': 'primary', '1': 'success', '2': 'warning', '3': 'danger' }[s] || 'info' },
    priorityLabel(p) { return { '1': '紧急', '2': '普通', '3': '低' }[p] || '普通' },
    ticketStatusLabel(s) { return { '0': '待处理', '1': '处理中', '2': '已完成', '3': '已归档' }[s] || '待处理' },
    ticketStatusTag(s) { return { '0': 'info', '1': 'warning', '2': 'success', '3': '' }[s] || 'info' },
    tagType(idx) { return ['warning', '', 'success', 'danger'][idx % 4] },
    formatDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds < 60) return seconds + '秒'
      const m = Math.floor(seconds / 60), s = seconds % 60
      return m + '分' + (s > 0 ? s + '秒' : '')
    }
  }
}
</script>

<style lang="scss" scoped>
.call-popup { background: #f1f5f9; min-height: calc(100vh - 84px); padding: 16px; padding-bottom: 100px; }

.cp-banner {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 10px; padding: 24px 28px; color: #fff;
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; position: relative; overflow: hidden;
  &::before { content: ''; position: absolute; right: -40px; top: -40px; width: 200px; height: 200px; border-radius: 50%; background: rgba(255,255,255,0.08); }
  .cp-banner-left { display: flex; align-items: center; gap: 14px; z-index: 1; }
  .cp-banner-icon { width: 52px; height: 52px; border-radius: 50%; background: rgba(255,255,255,0.2); display: flex; align-items: center; justify-content: center; font-size: 26px; animation: cp-ring 1.5s ease-in-out infinite; }
  .cp-banner-main { font-size: 20px; font-weight: 700; margin-bottom: 4px; }
  .cp-banner-sub { font-size: 13px; opacity: 0.9; display: flex; align-items: center; gap: 6px; }
  .cp-dot { width: 8px; height: 8px; border-radius: 50%; background: #fff; animation: cp-blink 1s ease-in-out infinite; }
  .cp-banner-center { text-align: center; z-index: 1; .cp-banner-number { font-size: 34px; font-weight: 700; letter-spacing: 2px; } .cp-banner-info { font-size: 13px; opacity: 0.9; } }
  .cp-banner-right { z-index: 1; .cp-time-label { font-size: 12px; opacity: 0.8; } .cp-time-value { font-size: 24px; font-weight: 700; font-family: 'Courier New', monospace; } }
}
@keyframes cp-ring { 0%,100% { transform: scale(1); } 50% { transform: scale(1.05); } }
@keyframes cp-blink { 0%,100% { opacity: 1; } 50% { opacity: 0.3; } }

.cp-card { border-radius: 10px; margin-bottom: 16px; border: 1px solid #e2e8f0; ::v-deep .el-card__body { padding: 18px 20px; } }
.cp-card-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; font-size: 14px; color: #1e293b; }
.cp-caller-header { display: flex; align-items: center; gap: 14px; padding-bottom: 14px; border-bottom: 1px solid #f1f5f9; margin-bottom: 14px; }
.cp-avatar { width: 52px; height: 52px; border-radius: 50%; background: linear-gradient(135deg, #3b82f6, #2563eb); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 600; }
.cp-caller-name { font-size: 16px; font-weight: 600; color: #1e293b; display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.cp-caller-phone { font-size: 13px; color: #64748b; }
.cp-detail-item { display: flex; justify-content: space-between; padding: 7px 0; font-size: 13px; .cp-detail-label { color: #64748b; } .cp-detail-value { color: #1e293b; font-weight: 500; } }
.cp-caller-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }

.cp-ai-title { display: flex; align-items: center; gap: 8px; color: #8b5cf6; }
.cp-ai-badge { background: linear-gradient(135deg, #8b5cf6, #7c3aed); color: #fff; font-size: 11px; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.cp-ai-grid { margin-bottom: 12px; }
.cp-ai-item { background: #faf5ff; border-radius: 8px; padding: 12px; text-align: center; margin-bottom: 8px; .cp-ai-label { font-size: 12px; color: #8b5cf6; margin-bottom: 6px; } .cp-ai-value { font-size: 14px; font-weight: 600; color: #1e293b; margin-bottom: 6px; } .cp-ai-sub { font-size: 11px; color: #94a3b8; } .cp-ai-confidence { display: flex; flex-direction: column; align-items: center; gap: 4px; .el-progress { width: 100%; } span { font-size: 11px; color: #8b5cf6; } } }
.cp-ai-freq { color: #f59e0b; }
.cp-risk-0 { color: #16a34a; } .cp-risk-1 { color: #f59e0b; } .cp-risk-2 { color: #ef4444; }
.cp-emotion-warning { background: #fef3c7; border: 1px solid #fde68a; border-radius: 8px; padding: 10px 12px; display: flex; align-items: center; gap: 8px; font-size: 13px; color: #92400e; i { font-size: 16px; color: #f59e0b; } }

.cp-history-card { ::v-deep .el-card__body { padding-top: 0; } }
.cp-history-tabs { ::v-deep .el-tabs__header { margin-bottom: 0; } ::v-deep .el-tabs__nav-wrap::after { display: none; } ::v-deep .el-tabs__item { height: 36px; line-height: 36px; font-size: 13px; color: #64748b; &.is-active { color: #10b981; font-weight: 600; } } ::v-deep .el-tabs__active-bar { background-color: #10b981; } }
.cp-history-content { padding-top: 12px; min-height: 300px; }
.cp-empty { text-align: center; color: #94a3b8; padding: 40px 0; font-size: 13px; }

.cp-call-item { display: flex; gap: 12px; padding: 12px; background: #f8fafc; border-radius: 8px; border: 1px solid #f1f5f9; margin-bottom: 10px; &:hover { background: #f1f5f9; } .cp-call-icon { width: 36px; height: 36px; border-radius: 50%; background: #dcfce7; color: #10b981; display: flex; align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; } .cp-call-main { flex: 1; min-width: 0; } .cp-call-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; .cp-call-title { font-size: 14px; font-weight: 500; color: #1e293b; flex: 1; } .cp-call-time { font-size: 12px; color: #94a3b8; } } .cp-call-footer { display: flex; align-items: center; gap: 12px; .cp-call-duration { font-size: 12px; color: #64748b; } .cp-call-content { font-size: 12px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; } } }

.cp-track-title { margin-left: 8px; font-size: 14px; font-weight: 500; color: #1e293b; }
.cp-track-content { font-size: 12px; color: #64748b; margin-top: 4px; }

.cp-action-bar { position: fixed; bottom: 0; left: 220px; right: 0; background: #fff; border-top: 1px solid #e2e8f0; padding: 14px 24px; display: flex; justify-content: center; gap: 16px; z-index: 100; box-shadow: 0 -4px 12px rgba(0,0,0,0.04); }
.cp-action-btn { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 90px; padding: 10px 20px; border-radius: 8px; font-size: 13px; i { font-size: 20px; }
  &.cp-btn-green { background: linear-gradient(135deg, #10b981, #059669); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #059669, #047857); } }
  &.cp-btn-blue { background: linear-gradient(135deg, #3b82f6, #2563eb); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #2563eb, #1d4ed8); } }
  &.cp-btn-gray { background: #f1f5f9; border: 1px solid #e2e8f0; color: #475569; &:hover { background: #e2e8f0; color: #1e293b; } }
  &.cp-btn-purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); border: none; color: #fff; &:hover { background: linear-gradient(135deg, #7c3aed, #6d28d9); } }
}
</style>
