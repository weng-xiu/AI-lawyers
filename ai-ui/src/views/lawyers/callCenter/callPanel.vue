<template>
  <div class="call-panel">
    <!-- 状态条 -->
    <div class="status-bar">
      <div class="status-left">
        <span class="status-dot"></span>
        <span class="status-text">空闲 - 自动应答模式</span>
      </div>
      <div class="status-center">
        <button
          v-for="(item, index) in statusButtons"
          :key="index"
          :class="['status-btn', 'status-btn-' + item.type, { active: currentStatus === item.key }]"
          @click="currentStatus = item.key"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="status-right">
        <span class="signin-time">已签入 08:30</span>
        <button class="signout-btn">签出</button>
      </div>
    </div>

    <!-- 等待来电横幅 -->
    <div class="waiting-banner">
      <div class="banner-content">
        <h2 class="banner-title">空闲等待来电中...</h2>
        <p class="banner-subtitle">等待时长 {{ waitingTime }}</p>
      </div>
      <div class="banner-actions">
        <button
          v-for="(action, index) in actionButtons"
          :key="index"
          class="action-btn"
          :class="'action-btn-' + action.type"
        >
          <i :class="action.icon"></i>
          <span>{{ action.label }}</span>
        </button>
      </div>
    </div>

    <!-- 排队信息 + 今日话务统计 -->
    <div class="info-section">
      <!-- 左侧排队信息 -->
      <div class="queue-info">
        <div class="card-header">
          <span class="card-title">排队信息</span>
          <span class="queue-badge">{{ queueList.length }}人排队等待</span>
        </div>
        <div class="queue-list">
          <div
            v-for="(item, index) in queueList"
            :key="index"
            class="queue-item"
          >
            <div class="queue-num" :class="'queue-num-' + (index + 1)">
              {{ index + 1 }}
            </div>
            <div class="queue-info-main">
              <div class="queue-phone">{{ item.phone }}</div>
              <div class="queue-type">{{ item.type }}</div>
            </div>
            <div class="queue-wait">
              <span class="wait-label">等待</span>
              <span class="wait-time">{{ item.waitTime }}</span>
            </div>
          </div>
        </div>
        <div class="queue-footer">
          <span class="avg-wait-label">平均等待时长</span>
          <span class="avg-wait-time">{{ avgWaitTime }}</span>
        </div>
      </div>

      <!-- 右侧今日话务统计 -->
      <div class="today-stats">
        <div class="card-header">
          <span class="card-title">今日话务统计</span>
        </div>
        <div class="stats-cards">
          <div
            v-for="(stat, index) in todayStats"
            :key="index"
            class="stat-card"
            :class="'stat-card-' + stat.type"
          >
            <div class="stat-value">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
        <div class="hourly-chart">
          <div class="chart-title">小时话务分布</div>
          <div class="chart-bars">
            <div
              v-for="(bar, index) in hourlyData"
              :key="index"
              class="bar-wrapper"
            >
              <div
                class="bar"
                :class="{ highlight: bar.highlight }"
                :style="{ height: bar.height + '%' }"
              ></div>
              <span class="bar-label">{{ bar.hour }}</span>
            </div>
          </div>
        </div>
        <div class="satisfaction">
          <div class="satisfaction-header">
            <span class="sat-title">客户满意度</span>
            <span class="sat-value">{{ satisfaction }}%</span>
          </div>
          <div class="sat-progress">
            <div
              class="sat-progress-bar"
              :style="{ width: satisfaction + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 技能组信息 -->
    <div class="skill-section">
      <div class="card-header">
        <span class="card-title">技能组信息</span>
      </div>
      <div class="skill-list">
        <div
          v-for="(skill, index) in skillGroups"
          :key="index"
          class="skill-item"
        >
          <div class="skill-header">
            <div class="skill-name">
              <span
                v-if="skill.isMain"
                class="main-skill-tag"
              >主技能</span>
              {{ skill.name }}
            </div>
            <div class="skill-count">
              <span class="online-count">{{ skill.online }}</span>
              <span class="total-count">/{{ skill.total }}坐席在线</span>
            </div>
          </div>
          <div class="skill-progress">
            <div
              class="skill-progress-bar"
              :class="'skill-bar-' + skill.type"
              :style="{ width: (skill.online / skill.total * 100) + '%' }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { getOnlineAgents, getCallStatistics, listRecord, getCallStatisticsByDate } from "@/api/lawyers/callCenter"

export default {
  name: "CallPanel",
  data() {
    return {
      currentStatus: 'idle',
      waitingTime: '00:00:00',
      satisfaction: 0,
      avgWaitTime: '0秒',
      statusButtons: [
        { key: 'idle', label: '置闲', type: 'green' },
        { key: 'busy', label: '置忙', type: 'orange' },
        { key: 'rest', label: '小休', type: 'purple' },
        { key: 'meeting', label: '会议', type: 'light-purple' },
        { key: 'training', label: '培训', type: 'light-blue' }
      ],
      actionButtons: [
        { label: '签入', icon: 'el-icon-user', type: 'green' },
        { label: '签出', icon: 'el-icon-switch-button', type: 'red' },
        { label: '外呼', icon: 'el-icon-phone-outline', type: 'blue' },
        { label: '保持/恢复', icon: 'el-icon-microphone', type: 'orange' },
        { label: '转移', icon: 'el-icon-right', type: 'purple' },
        { label: '咨询', icon: 'el-icon-service', type: 'cyan' },
        { label: '三方通话', icon: 'el-icon-phone', type: 'teal' },
        { label: '话后整理', icon: 'el-icon-document', type: 'yellow' },
        { label: '挂机', icon: 'el-icon-bangzhu', type: 'gray' }
      ],
      queueList: [],
      todayStats: [
        { label: '已接通', value: '0', type: 'green' },
        { label: '未接', value: '0', type: 'red' },
        { label: '外呼', value: '0', type: 'blue' },
        { label: '平均时长', value: '0:00', type: 'orange' }
      ],
      hourlyData: [],
      skillGroups: []
    }
  },
  created() {
    this.startTimer()
    this.loadPanelData()
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
  },
  methods: {
    startTimer() {
      let seconds = 0
      this.timer = setInterval(() => {
        seconds++
        const h = Math.floor(seconds / 3600)
        const m = Math.floor((seconds % 3600) / 60)
        const s = seconds % 60
        this.waitingTime = `${this.padZero(h)}:${this.padZero(m)}:${this.padZero(s)}`
      }, 1000)
    },
    padZero(num) {
      return num.toString().padStart(2, '0')
    },
    loadPanelData() {
      this.loadSkillGroups()
      this.loadTodayStats()
      this.loadQueueList()
      this.loadHourlyData()
    },
    loadSkillGroups() {
      getOnlineAgents().then(res => {
        const agents = res.data || res.rows || []
        if (!Array.isArray(agents)) {
          this.skillGroups = []
          return
        }
        const groupMap = {}
        agents.forEach(agent => {
          const group = agent.skillGroup || agent.groupName || '默认技能组'
          if (!groupMap[group]) {
            groupMap[group] = { online: 0, total: 0 }
          }
          groupMap[group].total++
          if (agent.status === '1' || agent.status === 'online' || agent.online) {
            groupMap[group].online++
          }
        })
        const types = ['blue', 'orange', 'purple']
        this.skillGroups = Object.keys(groupMap).map((name, idx) => ({
          name,
          online: groupMap[name].online,
          total: groupMap[name].total,
          type: types[idx % types.length],
          isMain: idx === 0
        }))
      }).catch(() => {
        this.skillGroups = []
      })
    },
    loadTodayStats() {
      getCallStatistics().then(res => {
        const data = res.data || {}
        const completed = data.completedCalls || data.totalCalls || 0
        const missed = data.missedCalls || 0
        const outbound = data.outboundCalls || 0
        const avgDuration = data.avgDuration || 0
        this.satisfaction = data.satisfaction || 0
        this.todayStats = [
          { label: '已接通', value: String(completed), type: 'green' },
          { label: '未接', value: String(missed), type: 'red' },
          { label: '外呼', value: String(outbound), type: 'blue' },
          { label: '平均时长', value: this.formatAvgDuration(avgDuration), type: 'orange' }
        ]
      }).catch(() => {})
    },
    loadQueueList() {
      listRecord({ status: '0', pageNum: 1, pageSize: 10 }).then(res => {
        const rows = res.rows || []
        this.queueList = rows.map(item => ({
          phone: item.callerNumber || '',
          type: item.categoryName || item.category || '',
          waitTime: this.formatWaitTime(item.waitDuration || item.duration || 0)
        }))
      }).catch(() => {
        this.queueList = []
      })
    },
    loadHourlyData() {
      getCallStatisticsByDate(1).then(res => {
        const data = res.data || []
        if (Array.isArray(data) && data.length > 0) {
          const maxVal = Math.max(...data.map(d => d.count || d.callCount || 0), 1)
          this.hourlyData = data.map(d => ({
            hour: (d.hour || d.hourOfDay || '') + '点',
            height: Math.round(((d.count || d.callCount || 0) / maxVal) * 100),
            highlight: (d.count || d.callCount || 0) === maxVal
          }))
        } else {
          this.hourlyData = this.getDefaultHourlyData()
        }
      }).catch(() => {
        this.hourlyData = this.getDefaultHourlyData()
      })
    },
    getDefaultHourlyData() {
      const hours = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17]
      return hours.map(h => ({ hour: h + '点', height: 0, highlight: false }))
    },
    formatAvgDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds <= 0) return '0:00'
      const m = Math.floor(seconds / 60)
      const s = seconds % 60
      return m + ':' + this.padZero(s)
    },
    formatWaitTime(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds < 60) return seconds + '秒'
      const m = Math.floor(seconds / 60)
      const s = seconds % 60
      return m + '分' + (s > 0 ? s + '秒' : '')
    }
  }
}
</script>

<style lang="scss" scoped>
.call-panel {
  background: #f1f5f9;
  min-height: 100vh;
  margin: -20px;
  padding: 20px;
}

// 状态条
.status-bar {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 14px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.15);
}

.status-text {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.status-center {
  display: flex;
  gap: 10px;
}

.status-btn {
  padding: 8px 20px;
  border-radius: 8px;
  border: none;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.status-btn-green {
  background: #ecfdf5;
  color: #10b981;
  &:hover, &.active {
    background: #10b981;
    color: #fff;
  }
}

.status-btn-orange {
  background: #fff7ed;
  color: #f59e0b;
  &:hover, &.active {
    background: #f59e0b;
    color: #fff;
  }
}

.status-btn-purple {
  background: #faf5ff;
  color: #8b5cf6;
  &:hover, &.active {
    background: #8b5cf6;
    color: #fff;
  }
}

.status-btn-light-purple {
  background: #f3e8ff;
  color: #a855f7;
  &:hover, &.active {
    background: #a855f7;
    color: #fff;
  }
}

.status-btn-light-blue {
  background: #eff6ff;
  color: #3b82f6;
  &:hover, &.active {
    background: #3b82f6;
    color: #fff;
  }
}

.status-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.signin-time {
  font-size: 13px;
  color: #64748b;
}

.signout-btn {
  padding: 8px 18px;
  border-radius: 8px;
  border: none;
  background: #fef2f2;
  color: #ef4444;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  &:hover {
    background: #ef4444;
    color: #fff;
  }
}

// 等待来电横幅
.waiting-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border-radius: 10px;
  padding: 28px 32px;
  color: #fff;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
}

.waiting-banner::before {
  content: '';
  position: absolute;
  right: -60px;
  top: -60px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}

.waiting-banner::after {
  content: '';
  position: absolute;
  right: 80px;
  bottom: -80px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
}

.banner-content {
  position: relative;
  z-index: 1;
  text-align: center;
  margin-bottom: 24px;
}

.banner-title {
  margin: 0 0 10px 0;
  font-size: 26px;
  font-weight: 700;
}

.banner-subtitle {
  margin: 0;
  font-size: 15px;
  opacity: 0.85;
}

.banner-actions {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  border-radius: 10px;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 70px;
  i {
    font-size: 20px;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.22);
    transform: translateY(-2px);
  }
}

// 信息区域
.info-section {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.queue-info {
  flex: 55;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 18px 20px;
}

.today-stats {
  flex: 45;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 18px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.queue-badge {
  background: #fef2f2;
  color: #ef4444;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

// 排队列表
.queue-list {
  margin-bottom: 14px;
}

.queue-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child {
    border-bottom: none;
  }
}

.queue-num {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  margin-right: 14px;
  flex-shrink: 0;
}

.queue-num-1 {
  background: #ef4444;
}

.queue-num-2 {
  background: #f59e0b;
}

.queue-num-3 {
  background: #3b82f6;
}

.queue-info-main {
  flex: 1;
}

.queue-phone {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.queue-type {
  font-size: 12px;
  color: #64748b;
}

.queue-wait {
  text-align: right;
}

.wait-label {
  font-size: 12px;
  color: #94a3b8;
  margin-right: 6px;
}

.wait-time {
  font-size: 14px;
  font-weight: 600;
  color: #ef4444;
}

.queue-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.avg-wait-label {
  font-size: 13px;
  color: #64748b;
}

.avg-wait-time {
  font-size: 18px;
  font-weight: 700;
  color: #f59e0b;
}

// 今日话务统计
.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  padding: 14px 10px;
  text-align: center;
}

.stat-card-green {
  background: #ecfdf5;
  .stat-value { color: #10b981; }
  .stat-label { color: #059669; }
}

.stat-card-red {
  background: #fef2f2;
  .stat-value { color: #ef4444; }
  .stat-label { color: #dc2626; }
}

.stat-card-blue {
  background: #eff6ff;
  .stat-value { color: #3b82f6; }
  .stat-label { color: #2563eb; }
}

.stat-card-orange {
  background: #fff7ed;
  .stat-value { color: #f59e0b; }
  .stat-label { color: #d97706; }
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  font-weight: 500;
}

// 小时话务分布图
.hourly-chart {
  margin-bottom: 20px;
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 12px;
}

.chart-bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 100px;
  padding: 0 4px;
}

.bar-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.bar {
  width: 18px;
  background: #dbeafe;
  border-radius: 4px 4px 0 0;
  transition: all 0.3s;
  &.highlight {
    background: #3b82f6;
  }
}

.bar-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
}

// 满意度
.satisfaction {
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.satisfaction-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sat-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.sat-value {
  font-size: 16px;
  font-weight: 700;
  color: #10b981;
}

.sat-progress {
  width: 100%;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
}

.sat-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #10b981 0%, #34d399 100%);
  border-radius: 4px;
  transition: width 0.3s;
}

// 技能组信息
.skill-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 18px 20px;
}

.skill-list {
  margin-top: 4px;
}

.skill-item {
  padding: 12px 0;
  &:not(:last-child) {
    border-bottom: 1px solid #f1f5f9;
  }
}

.skill-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.skill-name {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-skill-tag {
  background: #eff6ff;
  color: #3b82f6;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.skill-count {
  font-size: 13px;
  color: #64748b;
}

.online-count {
  font-weight: 700;
  color: #1e293b;
}

.total-count {
  color: #94a3b8;
}

.skill-progress {
  width: 100%;
  height: 8px;
  background: #f1f5f9;
  border-radius: 4px;
  overflow: hidden;
}

.skill-progress-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.skill-bar-blue {
  background: linear-gradient(90deg, #3b82f6 0%, #60a5fa 100%);
}

.skill-bar-orange {
  background: linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%);
}

.skill-bar-purple {
  background: linear-gradient(90deg, #8b5cf6 0%, #a78bfa 100%);
}
</style>
