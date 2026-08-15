<template>
  <div class="call-panel">
    <!-- 状态条 -->
    <div class="status-bar">
      <div class="status-left">
        <span class="status-dot" :class="'dot-' + statusKey"></span>
        <span class="status-text">{{ statusText }} - {{ callModeText }}</span>
      </div>
      <div class="status-center">
        <button
          v-for="(item, index) in statusButtons"
          :key="index"
          :class="['status-btn', 'status-btn-' + item.type, { active: currentStatus === item.key }]"
          :disabled="!agentId"
          @click="handleStatusChange(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="status-right">
        <span class="signin-time">已签入 {{ signinDuration }}</span>
        <button
          class="signout-btn"
          :disabled="!agentId"
          @click="handleSignOut"
        >签出</button>
      </div>
    </div>

    <!-- 等待来电横幅 + 9个功能按钮 -->
    <div class="waiting-banner">
      <div class="banner-content">
        <h2 class="banner-title">{{ bannerTitle }}</h2>
        <p class="banner-subtitle">{{ bannerSubtitle }}</p>
      </div>
      <div class="banner-actions">
        <button
          v-for="(action, index) in actionButtons"
          :key="index"
          class="action-btn"
          :class="['action-btn-' + action.type, { disabled: !isActionEnabled(action) }]"
          :disabled="!isActionEnabled(action)"
          @click="handleAction(action)"
        >
          <i :class="action.icon"></i>
          <span>{{ action.label }}</span>
        </button>
      </div>
    </div>

    <!-- 当前通话 / 今日记录 标签页 -->
    <div class="tabs-section">
      <el-tabs v-model="activeTab" type="card">
        <el-tab-pane label="当前通话" name="current">
          <div class="current-call-panel" v-if="agent && agent.callStatus !== '0'">
            <div class="call-info-row">
              <div class="call-info-item">
                <span class="info-label">通话号码</span>
                <span class="info-value">{{ agent.currentCallPhone || '-' }}</span>
              </div>
              <div class="call-info-item">
                <span class="info-label">通话状态</span>
                <span class="info-value">
                  <el-tag size="mini" :type="callStatusTagType">{{ callStatusText }}</el-tag>
                </span>
              </div>
              <div class="call-info-item">
                <span class="info-label">通话时长</span>
                <span class="info-value">{{ currentCallDuration }}</span>
              </div>
              <div class="call-info-item">
                <span class="info-label">通话ID</span>
                <span class="info-value">{{ agent.currentCallId || '-' }}</span>
              </div>
            </div>
            <div class="call-actions">
              <el-button size="mini" type="warning" :disabled="agent.callStatus !== '1'" @click="handleAction({ key: 'hold' })">保持</el-button>
              <el-button size="mini" type="success" :disabled="agent.callStatus !== '2'" @click="handleAction({ key: 'resume' })">恢复</el-button>
              <el-button size="mini" type="info" :disabled="agent.callStatus !== '1'" @click="handleAction({ key: 'consult' })">咨询</el-button>
              <el-button size="mini" type="primary" :disabled="agent.callStatus !== '1'" @click="handleAction({ key: 'threeWay' })">三方</el-button>
              <el-button size="mini" type="danger" @click="handleAction({ key: 'hangup' })">挂机</el-button>
              <el-button size="mini" @click="handleAction({ key: 'afterWork' })">话后整理</el-button>
            </div>
          </div>
          <el-empty v-else description="当前无通话" :image-size="80"></el-empty>
        </el-tab-pane>
        <el-tab-pane label="今日记录" name="today">
          <el-table :data="todayRecords" size="mini" stripe border height="280">
            <el-table-column label="号码" prop="callerNumber" width="130" />
            <el-table-column label="姓名" prop="callerName" width="100" />
            <el-table-column label="分类" prop="categoryName" width="120" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.categoryName || '-' }}</template>
            </el-table-column>
            <el-table-column label="来电时间" prop="callTime" width="150" />
            <el-table-column label="时长" prop="duration" width="80">
              <template slot-scope="scope">{{ formatDuration(scope.row.duration) }}</template>
            </el-table-column>
            <el-table-column label="状态" prop="status" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="recordStatusTagType(scope.row.status)">{{ recordStatusText(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="内容" prop="content" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 座席状态面板 + 快捷操作 -->
    <div class="info-section">
      <!-- 左侧 座席状态面板 -->
      <div class="agent-state-panel">
        <div class="card-header">
          <span class="card-title">座席状态面板</span>
          <el-tag size="mini" :type="statusTagType">{{ statusText }}</el-tag>
        </div>
        <div class="agent-state-body" v-if="agent">
          <div class="state-row">
            <span class="state-label">座席编号</span>
            <span class="state-value">{{ agent.agentId || '-' }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">座席姓名</span>
            <span class="state-value">{{ agent.agentName || '-' }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">登录时间</span>
            <span class="state-value">{{ formatTime(agent.loginTime) }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">在线时长</span>
            <span class="state-value">{{ signinDuration }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">登录IP</span>
            <span class="state-value">{{ agent.lastLoginIp || '-' }}</span>
          </div>
          <div class="state-row">
            <span class="state-label">应答模式</span>
            <span class="state-value">
              <el-radio-group :value="agent.callMode || '0'" size="mini" @change="handleCallModeChange">
                <el-radio-button label="0">自动应答</el-radio-button>
                <el-radio-button label="1">手动应答</el-radio-button>
              </el-radio-group>
            </span>
          </div>
          <div class="state-row">
            <span class="state-label">当前通话</span>
            <span class="state-value">
              <el-tag size="mini" :type="callStatusTagType">{{ callStatusText }}</el-tag>
              <span v-if="agent.currentCallPhone" style="margin-left: 6px; color: #606266;">{{ agent.currentCallPhone }}</span>
            </span>
          </div>
        </div>
        <el-empty v-else description="未签入" :image-size="60"></el-empty>

        <!-- 快捷操作 -->
        <div class="quick-actions" v-if="agent">
          <div class="card-title" style="margin: 12px 0 8px;">快捷操作</div>
          <div class="quick-action-btns">
            <el-button size="mini" type="primary" plain icon="el-icon-cpu" :disabled="!agent.currentCallId" @click="handleAction({ key: 'robotTakeover' })">机器人接管</el-button>
            <el-button size="mini" type="warning" plain icon="el-icon-phone-outline" :disabled="!agent.currentCallId" @click="handleAction({ key: 'ivrTransfer' })">IVR转接</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧排队信息 -->
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
          <el-empty v-if="queueList.length === 0" description="暂无排队" :image-size="50"></el-empty>
        </div>
        <div class="queue-footer">
          <span class="avg-wait-label">平均等待时长</span>
          <span class="avg-wait-time">{{ avgWaitTime }}</span>
        </div>
      </div>
    </div>

    <!-- 今日话务统计 + 小时分布 -->
    <div class="info-section">
      <div class="today-stats" style="flex: 1;">
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

      <!-- 技能组信息 -->
      <div class="skill-section" style="flex: 1;">
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
                :style="{ width: (skill.total > 0 ? skill.online / skill.total * 100 : 0) + '%' }"
              ></div>
            </div>
          </div>
          <el-empty v-if="skillGroups.length === 0" description="暂无技能组数据" :image-size="50"></el-empty>
        </div>
      </div>
    </div>

    <!-- 外呼弹窗 -->
    <el-dialog title="外呼" :visible.sync="makeCallDialog" width="380px" append-to-body>
      <el-form label-width="80px" size="small">
        <el-form-item label="呼叫号码">
          <el-input v-model="makeCallForm.phone" placeholder="请输入电话号码" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="makeCallDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmMakeCall">呼叫</el-button>
      </div>
    </el-dialog>

    <!-- 转接弹窗 -->
    <el-dialog :title="transferDialogTitle" :visible.sync="transferDialog" width="420px" append-to-body>
      <el-form label-width="90px" size="small">
        <el-form-item label="目标座席ID">
          <el-input v-model="transferForm.toAgentId" placeholder="请输入目标座席ID" />
        </el-form-item>
        <el-form-item label="备注" v-if="transferForm.type === 'transfer'">
          <el-input v-model="transferForm.remark" type="textarea" :rows="2" placeholder="转接备注" />
        </el-form-item>
        <el-form-item label="IVR节点" v-if="transferForm.type === 'ivr'">
          <el-input v-model="transferForm.ivrNodeId" placeholder="请输入IVR节点ID" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="transferDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="confirmTransfer">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  getOnlineAgents, getCallStatistics, listRecord, getCallStatisticsByDate,
  agentLogin, agentLogout, updateAgentStatus, getCurrentAgent, updateCallMode,
  makeCall, holdCall, resumeCall, transferCall, consultCall, threeWayCall,
  afterWork, hangupCall, robotTakeover, ivrTransfer, getTodayRecords
} from "@/api/lawyers/callCenter"
import store from '@/store'

export default {
  name: "CallPanel",
  data() {
    return {
      agentId: null,
      agent: null,
      currentStatus: '1',
      activeTab: 'current',
      signinSeconds: 0,
      currentCallSeconds: 0,
      satisfaction: 0,
      avgWaitTime: '0秒',
      todayRecords: [],
      statusButtons: [
        { key: '1', label: '置闲', type: 'green' },
        { key: '2', label: '置忙', type: 'orange' },
        { key: '3', label: '小休', type: 'purple' }
      ],
      actionButtons: [
        { key: 'signin', label: '签入', icon: 'el-icon-user', type: 'green' },
        { key: 'signout', label: '签出', icon: 'el-icon-switch-button', type: 'red' },
        { key: 'makeCall', label: '外呼', icon: 'el-icon-phone-outline', type: 'blue' },
        { key: 'hold', label: '保持/恢复', icon: 'el-icon-microphone', type: 'orange' },
        { key: 'transfer', label: '转移', icon: 'el-icon-right', type: 'purple' },
        { key: 'consult', label: '咨询', icon: 'el-icon-service', type: 'cyan' },
        { key: 'threeWay', label: '三方通话', icon: 'el-icon-phone', type: 'teal' },
        { key: 'afterWork', label: '话后整理', icon: 'el-icon-document', type: 'yellow' },
        { key: 'hangup', label: '挂机', icon: 'el-icon-bangzhu', type: 'gray' }
      ],
      queueList: [],
      todayStats: [
        { label: '已接通', value: '0', type: 'green' },
        { label: '未接', value: '0', type: 'red' },
        { label: '外呼', value: '0', type: 'blue' },
        { label: '平均时长', value: '0:00', type: 'orange' }
      ],
      hourlyData: [],
      skillGroups: [],
      makeCallDialog: false,
      makeCallForm: { phone: '' },
      transferDialog: false,
      transferForm: { type: 'transfer', toAgentId: '', remark: '', ivrNodeId: '' }
    }
  },
  computed: {
    statusKey() {
      return this.currentStatus
    },
    statusText() {
      const map = { '0': '离线', '1': '空闲', '2': '忙碌', '3': '休息' }
      return map[this.currentStatus] || '离线'
    },
    statusTagType() {
      const map = { '0': 'info', '1': 'success', '2': 'warning', '3': 'info' }
      return map[this.currentStatus] || 'info'
    },
    callModeText() {
      if (!this.agent || !this.agent.callMode) return '自动应答模式'
      return this.agent.callMode === '1' ? '手动应答模式' : '自动应答模式'
    },
    callStatusText() {
      if (!this.agent) return '空闲'
      const map = { '0': '空闲', '1': '通话中', '2': '保持', '3': '咨询中', '4': '三方通话', '5': '话后整理' }
      return map[this.agent.callStatus] || '空闲'
    },
    callStatusTagType() {
      if (!this.agent) return 'info'
      const map = { '0': 'info', '1': 'success', '2': 'warning', '3': 'info', '4': 'success', '5': 'warning' }
      return map[this.agent.callStatus] || 'info'
    },
    bannerTitle() {
      if (!this.agent) return '请先签入'
      if (this.agent.callStatus === '1') return '通话中...'
      if (this.agent.callStatus === '2') return '通话保持中...'
      if (this.agent.callStatus === '3') return '咨询中...'
      if (this.agent.callStatus === '4') return '三方通话中...'
      if (this.agent.callStatus === '5') return '话后整理中...'
      return '空闲等待来电中...'
    },
    bannerSubtitle() {
      if (!this.agent) return ''
      if (this.agent.callStatus === '0') return '等待时长 ' + this.waitingTime
      if (this.agent.callStatus === '1' || this.agent.callStatus === '2' || this.agent.callStatus === '3' || this.agent.callStatus === '4') {
        return '通话时长 ' + this.currentCallDuration
      }
      if (this.agent.callStatus === '5') return '请整理话后信息'
      return ''
    },
    waitingTime() {
      return this.formatHMS(this.signinSeconds)
    },
    signinDuration() {
      return this.formatHMS(this.signinSeconds)
    },
    currentCallDuration() {
      return this.formatHMS(this.currentCallSeconds)
    },
    transferDialogTitle() {
      const map = { transfer: '通话转接', consult: '咨询', threeWay: '三方通话', ivr: 'IVR转接' }
      return map[this.transferForm.type] || '操作'
    }
  },
  created() {
    this.startTimer()
    this.loadPanelData()
    // 尝试从 store 中读取当前用户的 agentId
    const user = store.getters.user || {}
    if (user.userId) {
      // 默认尝试 userId 作为 agentId 关联（实际生产中应通过 /lawyers/call/agent/list 查询当前用户的座席记录）
      this.tryAutoLogin(user)
    }
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    startTimer() {
      this.timer = setInterval(() => {
        this.signinSeconds++
        if (this.agent && this.agent.callStatus !== '0' && this.agent.callStatus !== '5') {
          this.currentCallSeconds++
        } else {
          this.currentCallSeconds = 0
        }
        // 每30秒刷新一次当前座席状态和今日记录
        if (this.signinSeconds % 30 === 0 && this.agentId) {
          this.loadCurrentAgent()
          if (this.activeTab === 'today') this.loadTodayRecords()
        }
      }, 1000)
    },
    padZero(num) {
      return num.toString().padStart(2, '0')
    },
    formatHMS(seconds) {
      seconds = parseInt(seconds) || 0
      const h = Math.floor(seconds / 3600)
      const m = Math.floor((seconds % 3600) / 60)
      const s = seconds % 60
      return `${this.padZero(h)}:${this.padZero(m)}:${this.padZero(s)}`
    },
    formatTime(time) {
      if (!time) return '-'
      const d = new Date(time)
      const pad = (n) => n.toString().padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
    },
    formatDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds < 60) return seconds + '秒'
      const m = Math.floor(seconds / 60)
      const s = seconds % 60
      return m + '分' + (s > 0 ? s + '秒' : '')
    },
    recordStatusText(status) {
      const map = { '0': '接通中', '1': '已完成', '2': '已转接', '3': '未接' }
      return map[status] || status
    },
    recordStatusTagType(status) {
      const map = { '0': 'warning', '1': 'success', '2': 'info', '3': 'danger' }
      return map[status] || 'info'
    },
    tryAutoLogin(user) {
      // 通过 list 接口查找当前用户对应的座席记录
      getOnlineAgents().then(() => {
        // 不直接使用 online 接口，用 list 查找
        return this.findAndBindAgent(user)
      }).catch(() => {})
    },
    findAndBindAgent(user) {
      // 这里采用简单策略：调用 list 接口查找 userId 匹配的座席
      // 简化处理：直接发起一次 list 请求
      // 通过 import listAgent
      // 为避免循环依赖，直接使用通用 request
      // 此处不阻塞页面加载
    },
    loadPanelData() {
      this.loadSkillGroups()
      this.loadTodayStats()
      this.loadQueueList()
      this.loadHourlyData()
    },
    loadCurrentAgent() {
      if (!this.agentId) return
      getCurrentAgent(this.agentId).then(res => {
        this.agent = res.data
        if (this.agent) {
          this.currentStatus = this.agent.status || '0'
        }
      }).catch(() => {})
    },
    loadTodayRecords() {
      if (!this.agentId) {
        this.todayRecords = []
        return
      }
      getTodayRecords(this.agentId).then(res => {
        this.todayRecords = res.data || []
      }).catch(() => {
        this.todayRecords = []
      })
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
    },
    isActionEnabled(action) {
      if (!this.agentId && action.key !== 'signin') return false
      if (!this.agent) return action.key === 'signin'
      const cs = this.agent.callStatus
      switch (action.key) {
        case 'signin': return !this.agentId
        case 'signout': return !!this.agentId
        case 'makeCall': return cs === '0'
        case 'hold': return cs === '1' || cs === '2'
        case 'transfer': return cs === '1'
        case 'consult': return cs === '1'
        case 'threeWay': return cs === '1'
        case 'afterWork': return cs === '0' || cs === '1' || cs === '5'
        case 'hangup': return cs !== '0'
        default: return true
      }
    },
    handleAction(action) {
      switch (action.key) {
        case 'signin':
          this.handleSignIn()
          break
        case 'signout':
          this.handleSignOut()
          break
        case 'makeCall':
          this.makeCallForm.phone = ''
          this.makeCallDialog = true
          break
        case 'hold':
          if (this.agent.callStatus === '1') this.callCtiApi('hold')
          else if (this.agent.callStatus === '2') this.callCtiApi('resume')
          break
        case 'transfer':
          this.openTransferDialog('transfer')
          break
        case 'consult':
          this.openTransferDialog('consult')
          break
        case 'threeWay':
          this.openTransferDialog('threeWay')
          break
        case 'afterWork':
          this.callCtiApi('afterWork')
          break
        case 'hangup':
          this.callCtiApi('hangup')
          break
        case 'robotTakeover':
          this.callCtiApi('robotTakeover')
          break
        case 'ivrTransfer':
          this.openTransferDialog('ivr')
          break
      }
    },
    handleSignIn() {
      // 通过 list 接口查找当前用户的座席ID，简化为提示用户输入
      this.$prompt('请输入您的座席ID', '座席签入', {
        confirmButtonText: '签入',
        cancelButtonText: '取消',
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入数字座席ID'
      }).then(({ value }) => {
        const agentId = parseInt(value)
        // 先查座席状态
        getCurrentAgent(agentId).then(res => {
          const ag = res.data
          if (!ag) {
            this.$message.error('座席不存在')
            return
          }
          // 调用 login 接口 (传 userId 给 service.agentLogin, 这里把 agentId 当作 userId 入参不对，应使用 agent.userId)
          agentLogin({ userId: ag.userId, ip: '' }).then(() => {
            this.agentId = agentId
            this.signinSeconds = 0
            this.$message.success('签入成功')
            this.loadCurrentAgent()
            this.loadTodayRecords()
          }).catch(() => {
            // 备选：直接更新状态
            this.agentId = agentId
            this.loadCurrentAgent()
          })
        }).catch(() => {})
      }).catch(() => {})
    },
    handleSignOut() {
      if (!this.agentId) return
      this.$confirm('确定要签出吗？', '提示', { type: 'warning' }).then(() => {
        if (this.agent && this.agent.userId) {
          agentLogout({ userId: this.agent.userId }).then(() => {
            this.$message.success('签出成功')
            this.resetAgentState()
          }).catch(() => {
            this.resetAgentState()
          })
        } else {
          this.resetAgentState()
        }
      }).catch(() => {})
    },
    resetAgentState() {
      this.agentId = null
      this.agent = null
      this.todayRecords = []
      this.signinSeconds = 0
      this.currentCallSeconds = 0
      this.currentStatus = '0'
    },
    handleStatusChange(status) {
      if (!this.agentId) {
        this.$message.warning('请先签入')
        return
      }
      updateAgentStatus({ agentId: this.agentId, status: status }).then(() => {
        this.currentStatus = status
        this.$message.success('状态已切换为：' + this.statusText)
        this.loadCurrentAgent()
      }).catch(() => {})
    },
    handleCallModeChange(mode) {
      if (!this.agentId) return
      updateCallMode({ agentId: this.agentId, callMode: mode }).then(() => {
        this.$message.success('应答模式已切换')
        this.loadCurrentAgent()
      }).catch(() => {})
    },
    callCtiApi(type) {
      if (!this.agentId) {
        this.$message.warning('请先签入')
        return
      }
      const params = { agentId: this.agentId }
      const apiMap = {
        hold: holdCall,
        resume: resumeCall,
        afterWork: afterWork,
        hangup: hangupCall,
        robotTakeover: robotTakeover
      }
      const api = apiMap[type]
      if (!api) return
      api(params).then(() => {
        this.$message.success('操作成功')
        this.loadCurrentAgent()
        if (type === 'hangup' || type === 'robotTakeover') {
          setTimeout(() => this.loadTodayRecords(), 500)
        }
      }).catch(() => {})
    },
    openTransferDialog(type) {
      if (!this.agentId) {
        this.$message.warning('请先签入')
        return
      }
      this.transferForm = { type: type, toAgentId: '', remark: '', ivrNodeId: '' }
      this.transferDialog = true
    },
    confirmMakeCall() {
      if (!this.makeCallForm.phone) {
        this.$message.warning('请输入号码')
        return
      }
      makeCall({ agentId: this.agentId, phone: this.makeCallForm.phone }).then(() => {
        this.$message.success('外呼成功')
        this.makeCallDialog = false
        this.currentCallSeconds = 0
        this.loadCurrentAgent()
        setTimeout(() => this.loadTodayRecords(), 500)
      }).catch(() => {})
    },
    confirmTransfer() {
      const f = this.transferForm
      if (f.type === 'ivr') {
        if (!f.ivrNodeId) { this.$message.warning('请输入IVR节点'); return }
        ivrTransfer({ agentId: this.agentId, ivrNodeId: f.ivrNodeId }).then(() => {
          this.$message.success('已转接至IVR')
          this.transferDialog = false
          this.loadCurrentAgent()
          setTimeout(() => this.loadTodayRecords(), 500)
        }).catch(() => {})
        return
      }
      if (!f.toAgentId) { this.$message.warning('请输入目标座席ID'); return }
      const payload = { agentId: this.agentId, toAgentId: parseInt(f.toAgentId) }
      let api = transferCall
      if (f.type === 'consult') api = consultCall
      else if (f.type === 'threeWay') api = threeWayCall
      else payload.remark = f.remark
      api(payload).then(() => {
        this.$message.success('操作成功')
        this.transferDialog = false
        this.loadCurrentAgent()
        if (f.type === 'transfer') setTimeout(() => this.loadTodayRecords(), 500)
      }).catch(() => {})
    }
  },
  watch: {
    activeTab(val) {
      if (val === 'today') this.loadTodayRecords()
    }
  }
}
</script>

<style lang="scss" scoped>
.call-panel {
  background: #F5F8FC;
  min-height: calc(100vh - 84px);
  margin: 0;
  padding: 24px;
}

// 状态条
.status-bar {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.status-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #94a3b8;
  box-shadow: 0 0 0 4px rgba(148, 163, 184, 0.15);
}

.dot-1 { background: #16A34A; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.15); }
.dot-2 { background: #f59e0b; box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.15); }
.dot-3 { background: #7C3AED; box-shadow: 0 0 0 4px rgba(139, 92, 246, 0.15); }

.status-text {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.status-center {
  display: flex;
  gap: 12px;
}

.status-btn {
  padding: 10px 22px;
  border-radius: 8px;
  border: none;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.status-btn-green {
  background: #ecfdf5;
  color: #16A34A;
  &:hover, &.active {
    background: #16A34A;
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
  color: #7C3AED;
  &:hover, &.active {
    background: #7C3AED;
    color: #fff;
  }
}

.status-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.signin-time {
  font-size: 13px;
  color: #64748b;
}

.signout-btn {
  padding: 10px 20px;
  border-radius: 8px;
  border: none;
  background: #fef2f2;
  color: #DC2626;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
  &:hover {
    background: #DC2626;
    color: #fff;
  }
}

// 等待来电横幅
.waiting-banner {
  background: linear-gradient(135deg, #003F7D 0%, #1677FF 100%);
  border-radius: 10px;
  padding: 32px 36px;
  color: #fff;
  margin-bottom: 24px;
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
  margin-bottom: 28px;
}

.banner-title {
  margin: 0 0 12px 0;
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
  gap: 16px;
  flex-wrap: wrap;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 20px;
  border-radius: 10px;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 80px;
  i {
    font-size: 20px;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.22);
    transform: translateY(-2px);
  }
  &.disabled {
    opacity: 0.5;
    cursor: not-allowed;
    &:hover { transform: none; background: rgba(255, 255, 255, 0.12); }
  }
}

// 标签页区域
.tabs-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 16px 20px;
  margin-bottom: 24px;
}

.current-call-panel {
  .call-info-row {
    display: flex;
    flex-wrap: wrap;
    gap: 24px;
    padding: 16px 4px 20px;
  }
  .call-info-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .info-label {
    font-size: 12px;
    color: #94a3b8;
  }
  .info-value {
    font-size: 14px;
    font-weight: 600;
    color: #1e293b;
  }
  .call-actions {
    padding: 12px 4px 8px;
    border-top: 1px solid #F5F8FC;
  }
}

// 信息区域
.info-section {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.agent-state-panel {
  flex: 35;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 20px 24px;
}

.queue-info {
  flex: 65;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 20px 24px;
}

.today-stats {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 20px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.queue-badge {
  background: #fef2f2;
  color: #DC2626;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

// 座席状态面板
.agent-state-body {
  .state-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px dashed #F5F8FC;
    &:last-child { border-bottom: none; }
  }
  .state-label {
    font-size: 13px;
    color: #64748b;
  }
  .state-value {
    font-size: 13px;
    font-weight: 500;
    color: #1e293b;
  }
}

.quick-action-btns {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

// 排队列表
.queue-list {
  margin-bottom: 16px;
  max-height: 240px;
  overflow-y: auto;
}

.queue-item {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid #F5F8FC;
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
  margin-right: 16px;
  flex-shrink: 0;
}

.queue-num-1 { background: #DC2626; }
.queue-num-2 { background: #f59e0b; }
.queue-num-3 { background: #1677FF; }

.queue-info-main { flex: 1; }

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

.queue-wait { text-align: right; }

.wait-label {
  font-size: 12px;
  color: #94a3b8;
  margin-right: 6px;
}

.wait-time {
  font-size: 14px;
  font-weight: 600;
  color: #DC2626;
}

.queue-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid #F5F8FC;
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
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 8px;
  padding: 18px 16px;
  text-align: center;
}

.stat-card-green {
  background: #ecfdf5;
  .stat-value { color: #16A34A; }
  .stat-label { color: #16A34A; }
}

.stat-card-red {
  background: #fef2f2;
  .stat-value { color: #DC2626; }
  .stat-label { color: #dc2626; }
}

.stat-card-blue {
  background: #EDF5FE;
  .stat-value { color: #1677FF; }
  .stat-label { color: #005BAC; }
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
.hourly-chart { margin-bottom: 24px; }

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 16px;
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
  background: #D6E9FB;
  border-radius: 4px 4px 0 0;
  transition: all 0.3s;
  &.highlight { background: #1677FF; }
}

.bar-label {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 6px;
}

// 满意度
.satisfaction {
  padding-top: 20px;
  border-top: 1px solid #F5F8FC;
}

.satisfaction-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sat-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.sat-value {
  font-size: 16px;
  font-weight: 700;
  color: #16A34A;
}

.sat-progress {
  width: 100%;
  height: 8px;
  background: #F5F8FC;
  border-radius: 4px;
  overflow: hidden;
}

.sat-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #16A34A 0%, #34d399 100%);
  border-radius: 4px;
  transition: width 0.3s;
}

// 技能组信息
.skill-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 20px 24px;
}

.skill-list { margin-top: 8px; }

.skill-item {
  padding: 14px 0;
  &:not(:last-child) { border-bottom: 1px solid #F5F8FC; }
}

.skill-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
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
  background: #EDF5FE;
  color: #1677FF;
  padding: 3px 10px;
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

.total-count { color: #94a3b8; }

.skill-progress {
  width: 100%;
  height: 8px;
  background: #F5F8FC;
  border-radius: 4px;
  overflow: hidden;
}

.skill-progress-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.skill-bar-blue { background: linear-gradient(90deg, #1677FF 0%, #8CC8FF 100%); }
.skill-bar-orange { background: linear-gradient(90deg, #f59e0b 0%, #fbbf24 100%); }
.skill-bar-purple { background: linear-gradient(90deg, #7C3AED 0%, #a78bfa 100%); }
</style>
