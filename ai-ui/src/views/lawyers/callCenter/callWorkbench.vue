<template>
  <div class="workbench-home">
    <!-- 欢迎横幅 -->
    <div class="wb-banner">
      <div class="wb-banner-left">
        <h2>工作台</h2>
        <p>{{ greeting }}，{{ userName || '律师' }} | {{ currentDate }}</p>
      </div>
      <div class="wb-banner-right">
        <div class="wb-banner-stat" v-for="(s, i) in bannerStats" :key="i">
          <span class="wb-bn-val">{{ s.value }}</span>
          <span class="wb-bn-lbl">{{ s.label }}</span>
        </div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="wb-card-row">
      <el-col :span="6" v-for="(card, idx) in statCards" :key="idx">
        <div class="wb-stat-card">
          <div class="wb-sc-top">
            <span class="wb-sc-title">{{ card.title }}</span>
            <span class="wb-sc-trend" :class="card.trendUp ? 'up' : 'down'">
              <i :class="card.trendUp ? 'el-icon-top' : 'el-icon-bottom'"></i>{{ card.trend }}
            </span>
          </div>
          <div class="wb-sc-value">{{ card.value }}</div>
          <div class="wb-sc-icon" :style="{ background: card.bg, color: card.color }">
            <i :class="card.icon"></i>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 第二行：团队概览 + 快捷入口 + 公告 -->
    <el-row :gutter="16" class="wb-card-row">
      <el-col :span="8">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-hd">
            <span><i class="el-icon-s-custom"></i> 团队概览</span>
          </div>
          <div class="wb-team-grid">
            <div class="wb-team-item" v-for="(t, i) in teamItems" :key="i">
              <div class="wb-tm-val">{{ t.value }}</div>
              <div class="wb-tm-lbl">{{ t.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-hd">
            <span><i class="el-icon-menu"></i> 快捷入口</span>
          </div>
          <div class="wb-quick-grid">
            <div class="wb-quick-item" v-for="(q, i) in quickEntries" :key="i" @click="handleQuick(q.path)">
              <div class="wb-qk-icon" :style="{ background: q.bg, color: q.color }">
                <i :class="q.icon"></i>
              </div>
              <span>{{ q.name }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-hd">
            <span><i class="el-icon-bell"></i> 公告通知</span>
            <el-button type="text" size="mini" @click="$router.push('/lawyers/notice')">更多</el-button>
          </div>
          <div class="wb-notice-list">
            <div class="wb-notice-item" v-for="(n, i) in noticeList" :key="i">
              <span class="wb-n-date">{{ n.date }}</span>
              <span class="wb-n-title">{{ n.title }}</span>
            </div>
            <div v-if="!noticeList.length" class="wb-empty">暂无公告</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近通话 -->
    <el-card shadow="never" class="wb-card wb-mt">
      <div slot="header" class="wb-card-hd">
        <span><i class="el-icon-phone"></i> 最近通话记录</span>
        <div style="display:flex;align-items:center;gap:10px;">
          <el-radio-group v-model="callFilter" size="mini">
            <el-radio-button label="today">今天</el-radio-button>
            <el-radio-button label="week">本周</el-radio-button>
          </el-radio-group>
          <el-button type="text" size="mini" @click="$router.push('/lawyers/callCenter/callRecord')">全部</el-button>
        </div>
      </div>
      <el-table :data="pagedCalls" size="small">
        <el-table-column label="来电号码" prop="callerNumber" width="140" align="left" />
        <el-table-column label="来电人" prop="callerName" width="100" align="left" />
        <el-table-column label="服务类型" width="100" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="serviceTag(scope.row)" effect="light">{{ serviceLabel(scope.row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通话时长" prop="duration" width="90" align="center">
          <template slot-scope="scope">{{ fmtDuration(scope.row.duration) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template slot-scope="scope">
            <el-tag size="mini" :type="statusTag(scope.row)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通话内容" prop="content" align="left" show-overflow-tooltip />
        <el-table-column label="时间" prop="callTime" width="160" align="left" />
      </el-table>
      <el-pagination
        v-if="filteredCalls.length > pageSize"
        layout="prev, pager, next"
        :total="filteredCalls.length"
        :page-size="pageSize"
        :current-page.sync="currentPage"
        @current-change="handlePageChange"
        style="text-align:right;margin-top:12px;" />
    </el-card>
  </div>
</template>

<script>
import { getWorkbenchSummary } from "@/api/lawyers/callCenter"
import { listNotice } from "@/api/system/notice"

export default {
  name: "CallWorkbench",
  data() {
    return {
      callFilter: 'today',
      currentPage: 1,
      pageSize: 8,
      currentDate: '',
      userName: '',
      bannerStats: [
        { value: '0', label: '今日通话' },
        { value: '0', label: '在线坐席' },
        { value: '0%', label: '满意度' }
      ],
      statCards: [
        { title: '总通话数', value: '0', trend: '0%', trendUp: true, icon: 'el-icon-phone', color: '#3b82f6', bg: '#eff6ff' },
        { title: '今日通话', value: '0', trend: '-', trendUp: false, icon: 'el-icon-phone-outline', color: '#16a34a', bg: '#f0fdf4' },
        { title: '通话总时长', value: '0分', trend: '-', trendUp: false, icon: 'el-icon-time', color: '#f97316', bg: '#fff7ed' },
        { title: '平均满意度', value: '0分', trend: '-', trendUp: false, icon: 'el-icon-star-on', color: '#a855f7', bg: '#faf5ff' }
      ],
      teamItems: [
        { value: '0', label: '团队总通话' },
        { value: '0', label: '团队成员' },
        { value: '0%', label: '团队满意度' },
        { value: '0分', label: '平均通话时长' }
      ],
      noticeList: [],
      quickEntries: [
        { name: '语音咨询', icon: 'el-icon-phone', color: '#3b82f6', bg: '#eff6ff', path: '/lawyers/callCenter/callPanel' },
        { name: '来电弹屏', icon: 'el-icon-monitor', color: '#06b6d4', bg: '#ecfeff', path: '/lawyers/callCenter/callPopup' },
        { name: '外呼任务', icon: 'el-icon-phone-outline', color: '#ef4444', bg: '#fee2e2', path: '/lawyers/outbound/task' },
        { name: '工单登记', icon: 'el-icon-edit', color: '#6366f1', bg: '#e0e7ff', path: '/lawyers/callCenter/callTicket' },
        { name: '台账填写', icon: 'el-icon-document', color: '#0d9488', bg: '#ccfbf1', path: '/lawyers/callCenter/callLedger' },
        { name: '知识检索', icon: 'el-icon-search', color: '#db2777', bg: '#fce7f3', path: '/lawyers/legal/knowledge' },
        { name: '回访任务', icon: 'el-icon-back', color: '#7c3aed', bg: '#ede9fe', path: '/lawyers/callback/task' },
        { name: '法律服务', icon: 'el-icon-s-claim', color: '#d97706', bg: '#fef3c7', path: '/lawyers/consultation' }
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
  },
  methods: {
    initDate() {
      const d = new Date()
      const w = ['日','一','二','三','四','五','六']
      this.currentDate = `${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日 星期${w[d.getDay()]}`
      try { this.userName = this.$store.getters.name || '' } catch(e) {}
    },
    loadData() {
      // 工作台汇总 API（单次请求）
      getWorkbenchSummary().then(res => {
        const d = res.data || {}
        const today = d.todayCallStats || {}
        const total = d.totalStats || {}
        const teams = d.teamStats || []

        // 横幅
        this.bannerStats = [
          { value: today.todayCalls || 0, label: '今日通话' },
          { value: d.onlineAgentCount || 0, label: '在线坐席' },
          { value: (total.avgRating || 0).toFixed(1) + '%', label: '满意度' }
        ]

        // 统计卡
        const totalCount = total.totalCount || 0
        this.statCards = [
          { title: '总通话数', value: totalCount.toLocaleString(), trend: '全部', trendUp: true, icon: 'el-icon-phone', color: '#3b82f6', bg: '#eff6ff' },
          { title: '今日通话', value: today.todayCalls || 0, trend: today.todayCalls > 0 ? '+' + today.todayCalls : '-', trendUp: true, icon: 'el-icon-phone-outline', color: '#16a34a', bg: '#f0fdf4' },
          { title: '今日服务时长', value: this.fmtDuration(today.todayServiceDuration || 0), trend: '-', trendUp: true, icon: 'el-icon-time', color: '#f97316', bg: '#fff7ed' },
          { title: '平均满意度', value: total.avgRating ? parseFloat(total.avgRating).toFixed(1) + '分' : '-', trend: '-', trendUp: true, icon: 'el-icon-star-on', color: '#a855f7', bg: '#faf5ff' }
        ]

        // 团队
        const teamTotal = teams.reduce((s, t) => s + (t.callCount || 0), 0)
        this.teamItems = [
          { value: teamTotal.toLocaleString(), label: '团队总通话' },
          { value: teams.length, label: '团队成员' },
          { value: total.avgRating ? parseFloat(total.avgRating).toFixed(1) + '%' : '-', label: '团队满意度' },
          { value: this.fmtDuration(total.avgDuration || 0), label: '平均通话时长' }
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

      // 公告单独加载
      listNotice({ pageNum: 1, pageSize: 5 }).then(res => {
        this.noticeList = (res.rows || []).map(n => ({
          date: (n.createTime || '').substring(0, 10),
          title: n.noticeTitle || ''
        }))
      }).catch(() => {})
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
    fmtDuration(sec) {
      if (!sec) return '-'
      sec = parseInt(sec)
      if (sec < 60) return sec + '秒'
      const h = Math.floor(sec / 3600)
      const m = Math.floor((sec % 3600) / 60)
      const s = sec % 60
      if (h > 0) return `${h}时${m}分${s > 0 ? s + '秒' : ''}`
      return `${m}分${s > 0 ? s + '秒' : ''}`
    },
    handleQuick(path) {
      if (path) this.$router.push(path)
    },
    handlePageChange(page) {
      this.currentPage = page
    }
  }
}
</script>

<style lang="scss" scoped>
.workbench-home {
  background: #f0f2f5;
  min-height: calc(100vh - 84px);
  padding: 20px;
  margin: -20px;
}

// 横幅
.wb-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border-radius: 12px;
  padding: 24px 32px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    right: -50px; top: -50px;
    width: 200px; height: 200px;
    border-radius: 50%;
    background: rgba(255,255,255,.05);
  }
  &::after {
    content: '';
    position: absolute;
    right: 80px; bottom: -70px;
    width: 160px; height: 160px;
    border-radius: 50%;
    background: rgba(255,255,255,.03);
  }
}
.wb-banner-left {
  z-index: 1;
  h2 { margin: 0 0 4px 0; font-size: 22px; font-weight: 700; }
  p { margin: 0; font-size: 13px; opacity: .85; }
}
.wb-banner-right {
  display: flex; gap: 36px; z-index: 1;
}
.wb-banner-stat { text-align: right; }
.wb-bn-val { display: block; font-size: 24px; font-weight: 700; line-height: 1.2; }
.wb-bn-lbl { display: block; font-size: 12px; opacity: .8; margin-top: 4px; }

// 卡片行
.wb-card-row { margin-bottom: 16px; }

// 统计卡片
.wb-stat-card {
  background: #fff; border-radius: 10px; padding: 18px 20px;
  position: relative; border: 1px solid #e5e7eb;
  transition: all .25s;
  &:hover { box-shadow: 0 4px 12px rgba(0,0,0,.06); transform: translateY(-2px); }
}
.wb-sc-top { display: flex; justify-content: space-between; margin-bottom: 10px; }
.wb-sc-title { font-size: 13px; color: #6b7280; }
.wb-sc-trend { font-size: 12px; font-weight: 600; }
.wb-sc-trend.up { color: #16a34a; }
.wb-sc-trend.down { color: #ef4444; }
.wb-sc-value { font-size: 24px; font-weight: 700; color: #111827; line-height: 1.2; }
.wb-sc-icon {
  position: absolute; right: 16px; bottom: 16px;
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; opacity: .9;
}

// 通用卡片
.wb-card { border-radius: 10px; border: 1px solid #e5e7eb; }
.wb-card-hd {
  display: flex; align-items: center; justify-content: space-between;
  font-weight: 600; font-size: 14px; color: #111827;
  i { color: #3b82f6; margin-right: 6px; }
}
.wb-mt { margin-bottom: 0; }

// 团队
.wb-team-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; padding: 8px 0; }
.wb-team-item { text-align: center; padding: 8px 0; }
.wb-tm-val { font-size: 22px; font-weight: 700; color: #111827; margin-bottom: 4px; }
.wb-tm-lbl { font-size: 12px; color: #6b7280; }

// 快捷入口
.wb-quick-grid { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 4px; }
.wb-quick-item {
  display: flex; flex-direction: column; align-items: center;
  padding: 10px 4px; cursor: pointer; border-radius: 8px;
  transition: all .2s;
  &:hover { background: #f9fafb; }
  span { font-size: 12px; color: #374151; margin-top: 4px; }
}
.wb-qk-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}

// 公告
.wb-notice-list { padding: 4px 0; min-height: 120px; }
.wb-notice-item { display: flex; align-items: center; padding: 8px 0; border-bottom: 1px solid #f3f4f6; }
.wb-notice-item:last-child { border-bottom: none; }
.wb-n-date { color: #9ca3af; font-size: 12px; margin-right: 12px; width: 80px; flex-shrink: 0; }
.wb-n-title { color: #2563eb; font-size: 13px; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wb-empty { text-align: center; color: #9ca3af; font-size: 13px; padding: 20px 0; }

// 表格
::v-deep .el-table {
  th { background: #f9fafb !important; color: #6b7280 !important; font-weight: 600 !important; border-color: #e5e7eb !important; }
  td { border-color: #f3f4f6 !important; }
  tr:hover > td { background: #f9fafb !important; }
}
</style>
