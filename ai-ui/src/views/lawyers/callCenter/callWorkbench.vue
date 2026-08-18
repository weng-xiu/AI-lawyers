<template>
  <div class="wb-home">
    <!-- 欢迎横幅 -->
    <div class="wb-banner">
      <div class="wb-banner-left">
        <div class="wb-avatar">
          <i class="el-icon-user-solid"></i>
        </div>
        <div class="wb-banner-text">
          <h2>{{ greeting }}，{{ userName || '律师' }}</h2>
          <p>{{ currentDate }} <span class="wb-dot"></span> 今天也要加油哦</p>
        </div>
      </div>
      <div class="wb-banner-right">
        <div class="wb-bn-item" v-for="(s, i) in bannerStats" :key="i">
          <span class="wb-bn-val">{{ s.value }}</span>
          <span class="wb-bn-lbl">{{ s.label }}</span>
        </div>
      </div>
    </div>

    <!-- 核心统计卡 -->
    <el-row :gutter="16" class="wb-row">
      <el-col :span="6" v-for="(card, idx) in statCards" :key="idx">
        <div class="wb-kpi" :style="{ '--accent': card.color }">
          <div class="wb-kpi-top">
            <span class="wb-kpi-title">{{ card.title }}</span>
            <span class="wb-kpi-icon" :style="{ background: card.bg, color: card.color }">
              <i :class="card.icon"></i>
            </span>
          </div>
          <div class="wb-kpi-value">{{ card.value }}</div>
          <div class="wb-kpi-foot">
            <i class="el-icon-top" v-if="card.trendUp"></i>
            <i class="el-icon-bottom" v-else></i>
            <span>{{ card.trend }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 三栏主体 -->
    <el-row :gutter="16" class="wb-row wb-main-row">
      <!-- 左栏：待办 + 日历 -->
      <el-col :span="6">
        <!-- 我的待办 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-finished"></i> 我的待办</span>
            <span class="wb-badge" v-if="pendingTodoCount">{{ pendingTodoCount }}</span>
          </div>
          <div class="wb-todo-list" v-loading="todoLoading">
            <div v-if="todoList.length === 0" class="wb-empty">
              <i class="el-icon-circle-check"></i>
              <p>暂无待办，一切就绪</p>
            </div>
            <div class="wb-todo-item" v-for="item in todoList" :key="item.todoId" @click="handleTodoClick(item)">
              <div class="wb-todo-bar" :class="'pri-' + item.priority"></div>
              <div class="wb-todo-body">
                <div class="wb-todo-title">{{ item.todoTitle }}</div>
                <div class="wb-todo-meta">
                  <span class="wb-todo-due" v-if="item.dueDate"><i class="el-icon-date"></i>{{ item.dueDate }}</span>
                  <el-tag size="mini" :type="priorityTag(item.priority)" effect="plain">{{ priorityLabel(item.priority) }}</el-tag>
                </div>
              </div>
              <el-button type="text" size="mini" class="wb-todo-done" @click.stop="markTodoDone(item)"><i class="el-icon-check"></i></el-button>
            </div>
          </div>
        </div>

        <!-- 月度日历 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-date"></i> 月度日历</span>
          </div>
          <el-calendar v-model="calendarValue" class="wb-calendar">
            <template slot="dateCell" slot-scope="{ date, data }">
              <div class="wb-cal-cell" :class="{ 'is-today': data.isSelected, 'has-todo': hasTodoOnDate(data.day) }">
                <span>{{ data.day.split('-').slice(2).join() }}</span>
                <em v-if="hasTodoOnDate(data.day)"></em>
              </div>
            </template>
          </el-calendar>
        </div>
      </el-col>

      <!-- 中栏：快捷入口 + 最近通话 -->
      <el-col :span="12">
        <!-- 快捷入口 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-menu"></i> 快捷入口</span>
          </div>
          <div class="wb-quick-grid">
            <div class="wb-quick-item" v-for="(q, i) in quickEntries" :key="i" @click="handleQuick(q.path)">
              <div class="wb-qk-icon" :style="{ background: q.bg, color: q.color }">
                <i :class="q.icon"></i>
              </div>
              <span>{{ q.name }}</span>
            </div>
          </div>
        </div>

        <!-- 最近通话 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-phone"></i> 最近通话记录</span>
            <div class="wb-hd-tools">
              <el-radio-group v-model="callFilter" size="mini">
                <el-radio-button label="today">今天</el-radio-button>
                <el-radio-button label="week">本周</el-radio-button>
              </el-radio-group>
              <el-button type="text" size="mini" @click="$router.push('/inbound/callRecord')">全部</el-button>
            </div>
          </div>
          <el-table :data="pagedCalls" size="small" class="wb-table" :header-cell-style="{background:'#F5F7FA',color:'#5A6A7E',fontWeight:600}">
            <el-table-column label="来电号码" prop="callerNumber" min-width="120" align="left">
              <template slot-scope="scope">
                <span class="wb-phone">{{ scope.row.callerNumber }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="serviceTag(scope.row)" effect="light">{{ serviceLabel(scope.row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时长" prop="duration" width="90" align="center">
              <template slot-scope="scope">{{ fmtDuration(scope.row.duration) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template slot-scope="scope">
                <el-tag size="mini" :type="statusTag(scope.row)">{{ statusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间" prop="callTime" width="150" align="left" />
          </el-table>
          <div v-if="!filteredCalls.length" class="wb-table-empty">
            <i class="el-icon-phone-outline"></i><span>暂无通话记录</span>
          </div>
          <el-pagination
            v-if="filteredCalls.length > pageSize"
            layout="prev, pager, next"
            :total="filteredCalls.length"
            :page-size="pageSize"
            :current-page.sync="currentPage"
            small
            style="text-align:right;margin-top:12px;" />
        </div>
      </el-col>

      <!-- 右栏：公告 + 团队概览 -->
      <el-col :span="6">
        <!-- 公告通知 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-bell"></i> 公告通知</span>
            <el-button type="text" size="mini" @click="$router.push('/system/notice')">更多</el-button>
          </div>
          <div class="wb-notice-list" v-loading="noticeLoading">
            <div v-if="noticeList.length === 0" class="wb-empty">
              <i class="el-icon-document"></i>
              <p>暂无公告</p>
            </div>
            <div class="wb-notice-item" v-for="(n, i) in noticeList" :key="i" @click="showNotice(n)">
              <div class="wb-nc-bar"></div>
              <div class="wb-nc-body">
                <div class="wb-nc-title">{{ n.title }}</div>
                <div class="wb-nc-date"><i class="el-icon-time"></i> {{ n.date }}</div>
              </div>
              <el-tag size="mini" type="danger" effect="plain" v-if="i === 0">新</el-tag>
            </div>
          </div>
        </div>

        <!-- 团队概览 -->
        <div class="wb-panel">
          <div class="wb-panel-hd">
            <span class="wb-panel-title"><i class="el-icon-s-custom"></i> 团队概览</span>
          </div>
          <div class="wb-team-grid">
            <div class="wb-team-item" v-for="(t, i) in teamItems" :key="i">
              <div class="wb-tm-val" :style="{color: t.color}">{{ t.value }}</div>
              <div class="wb-tm-lbl">{{ t.label }}</div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 公告详情弹窗 -->
    <el-dialog title="公告详情" :visible.sync="noticeOpen" width="520px" append-to-body custom-class="wb-notice-dialog">
      <div class="wb-notice-detail" v-if="curNotice.title">
        <h3>{{ curNotice.title }}</h3>
        <div class="wb-notice-meta"><i class="el-icon-time"></i> {{ curNotice.date }}</div>
        <div class="wb-notice-content">{{ curNotice.content || '暂无详细内容' }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getWorkbenchSummary } from "@/api/lawyers/callCenter"
import { listNotice } from "@/api/system/notice"
import { getWorkbenchTodos, processTodo } from "@/api/lawyers/workbench"

export default {
  name: "CallWorkbench",
  data() {
    return {
      callFilter: 'today',
      currentPage: 1,
      pageSize: 6,
      currentDate: '',
      userName: '',
      calendarValue: new Date(),
      todoLoading: false,
      noticeLoading: false,
      todoList: [],
      noticeOpen: false,
      curNotice: {},
      bannerStats: [
        { value: '-', label: '今日通话' },
        { value: '-', label: '在线坐席' },
        { value: '-', label: '满意度' }
      ],
      statCards: [
        { title: '总通话数', value: '-', trend: '全部', trendUp: true, icon: 'el-icon-phone', color: '#255A99', bg: '#E9EFF7' },
        { title: '今日通话', value: '-', trend: '-', trendUp: true, icon: 'el-icon-phone-outline', color: '#2B8C6E', bg: '#EDF5EF' },
        { title: '今日服务时长', value: '-', trend: '-', trendUp: true, icon: 'el-icon-time', color: '#E8923A', bg: '#fffbeb' },
        { title: '平均满意度', value: '-', trend: '-', trendUp: true, icon: 'el-icon-star-on', color: '#7C3AED', bg: '#f5f3ff' }
      ],
      teamItems: [
        { value: '-', label: '团队总通话', color: '#255A99' },
        { value: '-', label: '团队成员', color: '#2B8C6E' },
        { value: '-', label: '团队满意度', color: '#E8923A' },
        { value: '-', label: '平均时长', color: '#7C3AED' }
      ],
      noticeList: [],
      quickEntries: [
        { name: '来电弹屏', icon: 'el-icon-phone', color: '#255A99', bg: '#E9EFF7', path: '/inbound/callPopup' },
        { name: '图文服务', icon: 'el-icon-chat-dot-square', color: '#06b6d4', bg: '#ecfeff', path: '/workbench/chat' },
        { name: '视频咨询', icon: 'el-icon-video-camera', color: '#E8923A', bg: '#fffbeb', path: '/workbench/video' },
        { name: '来电弹屏', icon: 'el-icon-monitor', color: '#7C3AED', bg: '#f5f3ff', path: '/inbound/callPopup' },
        { name: '台账填写', icon: 'el-icon-document', color: '#0d9488', bg: '#ccfbf1', path: '/business/callLedger' },
        { name: '工单登记', icon: 'el-icon-edit', color: '#6366f1', bg: '#e0e7ff', path: '/business/workOrder' },
        { name: '回访任务', icon: 'el-icon-back', color: '#db2777', bg: '#fce7f3', path: '/business/callback/task' },
        { name: '风险预警', icon: 'el-icon-warning', color: '#C63D4A', bg: '#FBECEE', path: '/resource/riskWarning' }
      ],
      allCalls: []
    }
  },
  computed: {
    greeting() {
      const h = new Date().getHours()
      if (h < 6) return '夜深了'
      if (h < 12) return '上午好'
      if (h < 14) return '中午好'
      if (h < 18) return '下午好'
      return '晚上好'
    },
    pendingTodoCount() {
      return this.todoList.filter(t => t.status === '0').length
    },
    filteredCalls() {
      if (!this.allCalls.length) return []
      if (this.callFilter === 'today') {
        const today = new Date().toISOString().substring(0, 10)
        return this.allCalls.filter(c => (c.callTime || '').startsWith(today))
      }
      return this.allCalls
    },
    pagedCalls() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.filteredCalls.slice(start, start + this.pageSize)
    }
  },
  watch: {
    callFilter() { this.currentPage = 1 }
  },
  created() {
    this.initDate()
    this.loadData()
    this.loadTodos()
    this.loadNotices()
  },
  methods: {
    initDate() {
      const d = new Date()
      const w = ['日','一','二','三','四','五','六']
      this.currentDate = `${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 星期${w[d.getDay()]}`
      try { this.userName = this.$store.getters.name || '' } catch(e) {}
    },
    loadData() {
      getWorkbenchSummary().then(res => {
        const d = res.data || {}
        const today = d.todayCallStats || {}
        const total = d.totalStats || {}
        const teams = d.teamStats || []

        this.bannerStats = [
          { value: today.todayCalls != null ? today.todayCalls : 0, label: '今日通话' },
          { value: d.onlineAgentCount != null ? d.onlineAgentCount : 0, label: '在线坐席' },
          { value: total.avgRating != null ? Number(total.avgRating).toFixed(1) + '%' : '0%', label: '满意度' }
        ]

        const totalCount = total.totalCount || 0
        this.statCards = [
          { title: '总通话数', value: totalCount.toLocaleString(), trend: '全部记录', trendUp: true, icon: 'el-icon-phone', color: '#255A99', bg: '#E9EFF7' },
          { title: '今日通话', value: today.todayCalls || 0, trend: today.todayCalls > 0 ? '+' + today.todayCalls : '今日暂无', trendUp: true, icon: 'el-icon-phone-outline', color: '#2B8C6E', bg: '#EDF5EF' },
          { title: '今日服务时长', value: this.fmtDuration(today.todayServiceDuration || 0), trend: '今日累计', trendUp: true, icon: 'el-icon-time', color: '#E8923A', bg: '#fffbeb' },
          { title: '平均满意度', value: total.avgRating ? Number(total.avgRating).toFixed(1) + '分' : '暂无', trend: '综合评分', trendUp: true, icon: 'el-icon-star-on', color: '#7C3AED', bg: '#f5f3ff' }
        ]

        const teamTotal = teams.reduce((s, t) => s + (t.callCount || 0), 0)
        this.teamItems = [
          { value: teamTotal.toLocaleString(), label: '团队总通话', color: '#255A99' },
          { value: teams.length, label: '团队成员', color: '#2B8C6E' },
          { value: total.avgRating ? Number(total.avgRating).toFixed(1) + '%' : '暂无', label: '团队满意度', color: '#E8923A' },
          { value: this.fmtDuration(total.avgDuration || 0), label: '平均时长', color: '#7C3AED' }
        ]

        this.allCalls = (d.recentCalls || []).map(c => ({
          ...c,
          callerNumber: c.callerNumber || '-',
          callerName: c.callerName || '-',
          duration: c.duration || 0,
          status: c.status,
          content: c.content || '-',
          callTime: c.callTime || '-',
          callType: c.callType
        }))
      }).catch(() => {})
    },
    loadTodos() {
      this.todoLoading = true
      getWorkbenchTodos().then(res => {
        this.todoList = (res.data || []).filter(t => t.status === '0').slice(0, 6)
        this.todoLoading = false
      }).catch(() => { this.todoLoading = false })
    },
    loadNotices() {
      this.noticeLoading = true
      listNotice({ pageNum: 1, pageSize: 6, status: '1' }).then(res => {
        this.noticeList = (res.rows || []).map(n => ({
          noticeId: n.noticeId,
          title: n.noticeTitle || '',
          content: n.noticeContent || '',
          date: n.createTime ? n.createTime.substring(0, 10) : '-'
        }))
        this.noticeLoading = false
      }).catch(() => { this.noticeLoading = false })
    },
    showNotice(n) {
      this.curNotice = n
      this.noticeOpen = true
    },
    hasTodoOnDate(day) {
      return this.todoList.some(t => t.dueDate && t.dueDate.indexOf(day) === 0)
    },
    handleTodoClick(item) {
      this.$router.push('/workbench/todo')
    },
    markTodoDone(item) {
      processTodo(item.todoId).then(() => {
        this.$modal.msgSuccess('已标记完成')
        this.todoList = this.todoList.filter(t => t.todoId !== item.todoId)
      })
    },
    serviceLabel(row) {
      const m = { '1': '语音', '2': '图文', '3': '视频' }
      return m[row.callType] || m[row.category] || '语音'
    },
    serviceTag(row) {
      const m = { '1': 'primary', '2': 'success', '3': 'warning' }
      return m[row.callType] || m[row.category] || 'primary'
    },
    statusLabel(s) {
      const m = { '0': '未接听', '1': '已接听', '2': '已转接', '3': '漏接' }
      return m[s] || '-'
    },
    statusTag(s) {
      const m = { '0': 'warning', '1': 'success', '2': 'info', '3': 'danger' }
      return m[s] || 'info'
    },
    priorityLabel(p) { return { '1': '紧急', '2': '普通', '3': '低' }[p] || '普通' },
    priorityTag(p) { return { '1': 'danger', '2': 'warning', '3': 'info' }[p] || 'info' },
    fmtDuration(sec) {
      if (!sec) return '0秒'
      sec = parseInt(sec)
      if (sec < 60) return sec + '秒'
      const h = Math.floor(sec / 3600)
      const m = Math.floor((sec % 3600) / 60)
      const s = sec % 60
      if (h > 0) return `${h}时${m}分`
      return `${m}分${s > 0 ? s + '秒' : ''}`
    },
    handleQuick(path) {
      if (path) this.$router.push(path)
    }
  }
}
</script>

<style lang="scss" scoped>
.wb-home {
  background: #F5F7FA;
  min-height: calc(100vh - 84px);
  padding: 24px;
  margin: 0;
}
.wb-row { margin-bottom: 20px; }

/* 横幅 */
.wb-banner {
  background: linear-gradient(135deg, #16335C 0%, #1A3C6E 55%, #255A99 100%);
  border-radius: 16px;
  padding: 28px 32px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(37, 99, 235, 0.18);
  &::before {
    content: '';
    position: absolute;
    right: -60px; top: -80px;
    width: 260px; height: 260px;
    border-radius: 50%;
    background: rgba(255,255,255,0.07);
  }
  &::after {
    content: '';
    position: absolute;
    right: 120px; bottom: -100px;
    width: 200px; height: 200px;
    border-radius: 50%;
    background: rgba(255,255,255,0.04);
  }
}
.wb-banner-left {
  display: flex; align-items: center; gap: 20px; z-index: 1;
  .wb-avatar {
    width: 56px; height: 56px; border-radius: 50%;
    background: rgba(255,255,255,0.18);
    display: flex; align-items: center; justify-content: center;
    font-size: 30px; backdrop-filter: blur(4px);
  }
  .wb-banner-text h2 { margin: 0 0 8px 0; font-size: 22px; font-weight: 700; }
  .wb-banner-text p { margin: 0; font-size: 13px; opacity: .88; display: flex; align-items: center; }
  .wb-dot { width: 4px; height: 4px; border-radius: 50%; background: #B8D7F5; margin: 0 8px; display: inline-block; }
}
.wb-banner-right { display: flex; gap: 48px; z-index: 1; }
.wb-bn-item { text-align: center; }
.wb-bn-val { display: block; font-size: 28px; font-weight: 700; line-height: 1.1; text-shadow: 0 2px 8px rgba(0,0,0,.15); }
.wb-bn-lbl { display: block; font-size: 12px; opacity: .82; margin-top: 8px; }

/* KPI 卡 */
.wb-kpi {
  background: #fff; border-radius: 14px; padding: 20px 24px;
  position: relative; border: 1px solid #eef2f7;
  transition: all .3s cubic-bezier(.4,0,.2,1);
  overflow: hidden;
  &::before {
    content: ''; position: absolute; left: 0; top: 0; bottom: 0;
    width: 4px; background: var(--accent); opacity: .8;
  }
  &:hover { box-shadow: 0 10px 24px rgba(15,23,42,.08); transform: translateY(-3px); }
}
.wb-kpi-top { display: flex; justify-content: space-between; align-items: center; }
.wb-kpi-title { font-size: 13px; color: #5A6A7E; font-weight: 500; }
.wb-kpi-icon {
  width: 40px; height: 40px; border-radius: 11px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.wb-kpi-value { font-size: 28px; font-weight: 700; color: #0f172a; line-height: 1.3; margin: 10px 0 8px; }
.wb-kpi-foot {
  font-size: 12px; color: #94a3b8; display: flex; align-items: center; gap: 6px;
  i { color: #2B8C6E; }
}

/* 面板通用 */
.wb-panel {
  background: #fff; border-radius: 14px; padding: 20px 24px;
  margin-bottom: 20px; border: 1px solid #eef2f7;
  box-shadow: 0 1px 3px rgba(15,23,42,.03);
}
.wb-panel-hd {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px;
  .wb-panel-title {
    font-size: 15px; font-weight: 600; color: #0f172a;
    display: flex; align-items: center; gap: 8px;
    i { color: #1A3C6E; font-size: 16px; }
  }
  .wb-badge {
    background: #C63D4A; color: #fff; font-size: 11px; font-weight: 600;
    min-width: 20px; height: 20px; line-height: 20px; padding: 0 6px;
    border-radius: 10px; text-align: center;
  }
  .wb-hd-tools { display: flex; align-items: center; gap: 12px; }
}

/* 待办 */
.wb-todo-list { min-height: 120px; }
.wb-empty {
  text-align: center; color: #B0BCCA; padding: 24px 0;
  i { font-size: 32px; display: block; margin-bottom: 8px; opacity: .7; }
  p { font-size: 13px; margin: 0; }
}
.wb-todo-item {
  display: flex; align-items: stretch;
  padding: 12px 0; border-bottom: 1px dashed #F5F7FA;
  &:last-child { border-bottom: none; }
}
.wb-todo-bar { width: 3px; border-radius: 2px; margin-right: 14px; }
.pri-1 { background: #C63D4A; }
.pri-2 { background: #E8923A; }
.pri-3 { background: #94a3b8; }
.wb-todo-body { flex: 1; min-width: 0; }
.wb-todo-title { font-size: 13px; color: #1F2A3A; font-weight: 500; margin-bottom: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wb-todo-meta { display: flex; align-items: center; justify-content: space-between; }
.wb-todo-due { font-size: 11px; color: #94a3b8; display: flex; align-items: center; gap: 3px; }

/* 日历 */
::v-deep .wb-calendar {
  .el-calendar__header { padding: 0 0 10px; }
  .el-calendar__title { font-size: 13px; color: #334155; font-weight: 600; }
  .el-calendar__button-group { display: none; }
  table { border-collapse: separate; border-spacing: 3px; }
  th { font-size: 11px; color: #94a3b8; font-weight: 500; padding: 4px 0; }
  td { border: none !important; padding: 0 !important; }
  .el-calendar-day {
    height: 32px; padding: 0; border-radius: 7px;
    display: flex; align-items: center; justify-content: center;
    transition: all .15s;
    &:hover { background: #E9EFF7; }
  }
}
.wb-cal-cell {
  width: 100%; height: 32px; display: flex; flex-direction: column;
  align-items: center; justify-content: center; position: relative;
  font-size: 12px; color: #5A6A7E; border-radius: 7px; cursor: pointer;
  &.is-today { background: #1A3C6E; color: #fff; font-weight: 600; }
  &.has-todo em {
    width: 4px; height: 4px; border-radius: 50%;
    background: #E8923A; margin-top: 1px; font-style: normal;
  }
  &.is-today.has-todo em { background: #fde68a; }
}

/* 快捷入口 */
.wb-quick-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.wb-quick-item {
  display: flex; flex-direction: column; align-items: center;
  padding: 18px 8px; cursor: pointer; border-radius: 12px;
  transition: all .2s;
  &:hover { background: #F5F7FA; transform: translateY(-2px); }
  span { font-size: 12px; color: #334155; margin-top: 10px; }
}
.wb-qk-icon {
  width: 46px; height: 46px; border-radius: 13px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; transition: transform .2s;
}
.wb-quick-item:hover .wb-qk-icon { transform: scale(1.08); }

/* 表格 */
.wb-table {
  ::v-deep th { background: #F5F7FA !important; }
  ::v-deep td { border-color: #F5F7FA !important; }
  .wb-phone { font-weight: 600; color: #1F2A3A; font-family: 'Consolas', monospace; }
}
.wb-table-empty {
  text-align: center; color: #B0BCCA; padding: 28px 0;
  i { font-size: 28px; display: block; margin-bottom: 6px; }
  span { font-size: 13px; }
}

/* 公告 */
.wb-notice-list { min-height: 120px; }
.wb-notice-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 6px; border-bottom: 1px dashed #F5F7FA;
  cursor: pointer; transition: background .15s; border-radius: 6px;
  &:last-child { border-bottom: none; }
  &:hover { background: #F5F7FA; .wb-nc-title { color: #1A3C6E; } }
  .wb-nc-bar { width: 3px; align-self: stretch; background: #255A99; border-radius: 2px; }
  .wb-nc-body { flex: 1; min-width: 0; }
  .wb-nc-title { font-size: 13px; color: #1F2A3A; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: color .15s; }
  .wb-nc-date { font-size: 11px; color: #94a3b8; margin-top: 4px; display: flex; align-items: center; gap: 3px; }
}

/* 团队 */
.wb-team-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.wb-team-item {
  text-align: center; padding: 18px 8px;
  background: #F5F7FA; border-radius: 10px;
  transition: all .2s;
  &:hover { background: #F5F7FA; transform: translateY(-2px); }
}
.wb-tm-val { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
.wb-tm-lbl { font-size: 12px; color: #5A6A7E; }

/* 公告详情 */
.wb-notice-detail {
  h3 { margin: 0 0 12px 0; color: #0f172a; font-size: 17px; }
  .wb-notice-meta { font-size: 12px; color: #94a3b8; margin-bottom: 20px; display: flex; align-items: center; gap: 6px; }
  .wb-notice-content { line-height: 1.8; color: #334155; white-space: pre-wrap; font-size: 14px; }
}
</style>
