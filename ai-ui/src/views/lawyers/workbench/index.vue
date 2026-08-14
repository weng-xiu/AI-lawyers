<template>
  <div class="workbench">
    <!-- 欢迎横幅 -->
    <div class="wb-banner">
      <div class="wb-banner-left">
        <h2>欢迎回来，{{ userName || '律师' }}</h2>
        <p>{{ currentDate }} · {{ agentStatusText }}</p>
      </div>
      <div class="wb-banner-right">
        <div class="wb-banner-stat">
          <span class="wb-banner-num">{{ stats.todayCalls }}</span>
          <span class="wb-banner-label">今日服务群众</span>
        </div>
        <div class="wb-banner-stat">
          <span class="wb-banner-num">{{ stats.satisfaction }}%</span>
          <span class="wb-banner-label">平均满意度</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="wb-stat-row">
      <el-col :span="6" v-for="(card, idx) in statCards" :key="idx">
        <div class="wb-stat-card">
          <div class="wb-stat-icon" :class="'wb-sicon-' + card.type">
            <i :class="card.icon"></i>
          </div>
          <div class="wb-stat-body">
            <div class="wb-stat-value">{{ card.value }}</div>
            <div class="wb-stat-title">{{ card.title }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-card shadow="never" class="wb-card wb-quick-card">
      <div slot="header" class="wb-card-header">
        <span><i class="el-icon-menu"></i> 快捷入口</span>
      </div>
      <div class="wb-quick-grid">
        <div class="wb-quick-item" v-for="(item, idx) in quickList" :key="idx" @click="handleQuickClick(item)">
          <div class="wb-quick-icon" :class="'wb-qicon-' + item.type">
            <i :class="item.icon"></i>
          </div>
          <span>{{ item.name }}</span>
        </div>
      </div>
    </el-card>

    <!-- 统一工作台标签页 -->
    <el-tabs v-model="activeTab" class="wb-tabs">
      <el-tab-pane label="概览" name="overview">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-card shadow="never" class="wb-card">
              <div slot="header" class="wb-card-header">
                <span><i class="el-icon-date"></i> 月度日历</span>
              </div>
              <el-calendar v-model="calendarValue">
                <template slot="dateCell" slot-scope="{ date, data }">
                  <div class="wb-cal-cell" :class="{ 'wb-cal-today': data.isSelected }">
                    <span>{{ data.day.split('-').slice(2).join() }}</span>
                    <span class="wb-cal-dot" v-if="hasTodoOnDate(data.day)"></span>
                  </div>
                </template>
              </el-calendar>
            </el-card>
          </el-col>
          <el-col :span="10">
            <el-card shadow="never" class="wb-card">
              <div slot="header" class="wb-card-header">
                <span><i class="el-icon-phone"></i> 最近通话</span>
              </div>
              <div v-loading="recordLoading">
                <div v-if="recentRecords.length === 0" class="wb-empty">暂无通话记录</div>
                <div class="wb-record-item" v-for="item in recentRecords" :key="item.recordId">
                  <div class="wb-record-top">
                    <span class="wb-record-phone">{{ item.callerNumber || '-' }}</span>
                    <el-tag size="mini" :type="recordStatusTag(item.status)" effect="plain">{{ recordStatusLabel(item.status) }}</el-tag>
                  </div>
                  <div class="wb-record-name">{{ item.callerName || '未知' }} · {{ item.categoryName || '未分类' }}</div>
                  <div class="wb-record-time">{{ item.callTime }} · {{ formatDuration(item.duration) }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="待办" name="todo">
        <todo-panel />
      </el-tab-pane>

      <el-tab-pane label="公告" name="notice">
        <notice-panel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { getWorkbenchStats, getWorkbenchTodos, getWorkbenchRecentCalls } from "@/api/lawyers/workbench"
import TodoPanel from './todo'
import NoticePanel from './notice'

export default {
  name: "Workbench",
  components: { TodoPanel, NoticePanel },
  data() {
    return {
      activeTab: 'overview',
      currentDate: '',
      userName: '',
      todoLoading: false,
      recordLoading: false,
      stats: {
        todayCalls: 0,
        serviceDuration: 0,
        satisfaction: 0,
        onlineDuration: 0,
        agentStatus: '0'
      },
      todoList: [],
      recentRecords: [],
      calendarValue: new Date(),
      quickList: [
        { name: '话务面板', icon: 'el-icon-phone', type: 'blue', path: '/workbench/callPanel' },
        { name: '图文服务', icon: 'el-icon-chat-dot-square', type: 'teal', path: '/workbench/chat' },
        { name: '咨询台账', icon: 'el-icon-document', type: 'blue2', path: '/business/callLedger' },
        { name: '工单管理', icon: 'el-icon-edit', type: 'green', path: '/business/workOrder' }
      ]
    }
  },
  computed: {
    statCards() {
      return [
        { title: '今日通话', value: this.stats.todayCalls + ' 次', icon: 'el-icon-phone', type: 'blue' },
        { title: '服务时长', value: this.formatDuration(this.stats.serviceDuration * 60), icon: 'el-icon-time', type: 'green' },
        { title: '满意度', value: this.stats.satisfaction + '%', icon: 'el-icon-star-on', type: 'orange' },
        { title: '在线时长', value: this.formatDuration(this.stats.onlineDuration * 60), icon: 'el-icon-user', type: 'purple' }
      ]
    },
    agentStatusText() {
      const map = { '0': '离线', '1': '在线', '2': '忙碌', '3': '休息' }
      return map[this.stats.agentStatus] || '离线'
    }
  },
  created() {
    this.initDate()
    this.loadAll()
  },
  methods: {
    initDate() {
      const now = new Date()
      const weekDays = ['日', '一', '二', '三', '四', '五', '六']
      this.currentDate = `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${weekDays[now.getDay()]}`
      try { this.userName = this.$store.getters.name || '' } catch (e) { this.userName = '' }
    },
    loadAll() {
      this.loadStats()
      this.loadTodos()
      this.loadRecentRecords()
    },
    loadStats() {
      getWorkbenchStats().then(res => {
        this.stats = Object.assign({ todayCalls: 0, serviceDuration: 0, satisfaction: 0, onlineDuration: 0, agentStatus: '0' }, res.data || {})
      }).catch(() => {})
    },
    loadTodos() {
      this.todoLoading = true
      getWorkbenchTodos().then(res => {
        this.todoList = res.data || []
        this.todoLoading = false
      }).catch(() => { this.todoLoading = false })
    },
    loadRecentRecords() {
      this.recordLoading = true
      getWorkbenchRecentCalls(5).then(res => {
        this.recentRecords = res.data || []
        this.recordLoading = false
      }).catch(() => { this.recordLoading = false })
    },
    hasTodoOnDate(day) {
      return this.todoList.some(t => t.dueDate && t.dueDate.indexOf(day) === 0 && t.status === '0')
    },
    handleQuickClick(item) {
      if (item.path) {
        this.$router.push({ path: item.path })
      } else {
        this.$message.info(item.name + ' 敬请期待')
      }
    },
    recordStatusLabel(s) {
      return { '0': '接通中', '1': '已完成', '2': '已转接', '3': '未接' }[s] || '未知'
    },
    recordStatusTag(s) {
      return { '0': 'primary', '1': 'success', '2': 'warning', '3': 'danger' }[s] || 'info'
    },
    formatDuration(seconds) {
      seconds = parseInt(seconds) || 0
      if (seconds <= 0) return '0分'
      if (seconds < 60) return seconds + '秒'
      const h = Math.floor(seconds / 3600)
      const m = Math.floor((seconds % 3600) / 60)
      if (h > 0) return h + '小时' + m + '分'
      return m + '分'
    }
  }
}
</script>

<style lang="scss" scoped>
.workbench {
  padding: 24px;
  background: #f1f5f9;
  min-height: calc(100vh - 84px);
}

.wb-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border-radius: 12px;
  padding: 28px 32px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    right: -40px;
    top: -40px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);
  }
  .wb-banner-left h2 { margin: 0 0 8px 0; font-size: 22px; font-weight: 700; }
  .wb-banner-left p { margin: 0; font-size: 13px; opacity: 0.85; }
  .wb-banner-right { display: flex; gap: 48px; z-index: 1; }
  .wb-banner-stat { text-align: right; }
  .wb-banner-num { display: block; font-size: 24px; font-weight: 700; }
  .wb-banner-label { display: block; font-size: 12px; opacity: 0.8; margin-top: 6px; }
}

.wb-stat-row {
  margin-bottom: 24px;
  .wb-stat-card {
    display: flex;
    align-items: center;
    padding: 24px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
    .wb-stat-icon {
      width: 54px;
      height: 54px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 18px;
      font-size: 26px;
      color: #fff;
    }
    .wb-stat-body { flex: 1; }
    .wb-stat-value { font-size: 24px; font-weight: 700; color: #1e293b; line-height: 1.2; }
    .wb-stat-title { font-size: 13px; color: #64748b; margin-top: 6px; }
  }
  .wb-sicon-blue { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
  .wb-sicon-green { background: linear-gradient(135deg, #10b981, #059669); }
  .wb-sicon-orange { background: linear-gradient(135deg, #f59e0b, #d97706); }
  .wb-sicon-purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
}

.wb-card {
  border-radius: 12px;
  margin-bottom: 24px;
  border: 1px solid #e2e8f0;
  ::v-deep .el-card__body { padding: 20px 24px; }
  .wb-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
    font-size: 15px;
    color: #1e293b;
    i { color: #3b82f6; margin-right: 6px; }
  }
}

.wb-empty {
  text-align: center;
  color: #94a3b8;
  padding: 32px 0;
  font-size: 13px;
}

.wb-cal-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  .wb-cal-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #ef4444;
    margin-top: 2px;
  }
}
.wb-cal-today {
  background: #eff6ff;
  border-radius: 6px;
  color: #3b82f6;
  font-weight: 700;
}

.wb-quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.wb-quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 8px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  &:hover { background: #f8fafc; }
  .wb-quick-icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    margin-bottom: 10px;
  }
  span { font-size: 13px; color: #475569; }
}
.wb-qicon-blue { background: #eff6ff; color: #3b82f6; }
.wb-qicon-green { background: #f0fdf4; color: #16a34a; }
.wb-qicon-blue2 { background: #e0e7ff; color: #6366f1; }
.wb-qicon-teal { background: #ccfbf1; color: #0d9488; }

.wb-record-item {
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: none; }
  .wb-record-top { display: flex; justify-content: space-between; align-items: center; }
  .wb-record-phone { font-size: 14px; font-weight: 600; color: #1e293b; }
  .wb-record-name { font-size: 12px; color: #475569; margin-top: 6px; }
  .wb-record-time { font-size: 12px; color: #94a3b8; margin-top: 4px; }
}

.wb-tabs {
  ::v-deep .el-tabs__header { margin-bottom: 16px; }
  ::v-deep .app-container { padding: 0; }
}
</style>
