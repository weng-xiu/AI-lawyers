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

    <!-- 4 统计卡 -->
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

    <el-row :gutter="16" class="wb-main">
      <!-- 左侧：待办 + 日历 + 快捷入口 -->
      <el-col :span="16">
        <!-- 我的待办 -->
        <el-card shadow="never" class="wb-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-finished"></i> 我的待办</span>
            <el-button type="text" size="mini" icon="el-icon-plus" @click="handleAddTodo">新建待办</el-button>
          </div>
          <div v-loading="todoLoading">
            <div v-if="todoList.length === 0" class="wb-empty">暂无待办事项</div>
            <div class="wb-todo-item" v-for="item in todoList" :key="item.todoId">
              <div class="wb-todo-main">
                <el-tag size="mini" :type="priorityTag(item.priority)" effect="dark">{{ priorityLabel(item.priority) }}</el-tag>
                <span class="wb-todo-title" :class="{ 'wb-todo-done': item.status === '1' }">{{ item.todoTitle }}</span>
                <span class="wb-todo-content">{{ item.todoContent }}</span>
                <span class="wb-todo-due" v-if="item.dueDate">到期：{{ item.dueDate }}</span>
              </div>
              <div class="wb-todo-actions" v-if="item.status === '0'">
                <el-button type="text" size="mini" style="color:#16a34a" @click="handleProcessTodo(item)">处理</el-button>
                <el-button type="text" size="mini" style="color:#f59e0b" @click="handleDeferTodo(item)">延后</el-button>
                <el-button type="text" size="mini" style="color:#94a3b8" @click="handleIgnoreTodo(item)">忽略</el-button>
              </div>
              <div class="wb-todo-actions" v-else>
                <el-tag size="mini" :type="statusTag(item.status)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 月度日历 + 快捷入口 -->
        <el-row :gutter="16" class="wb-section">
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
          </el-col>
        </el-row>
      </el-col>

      <!-- 右侧：最近通话 + 公告 -->
      <el-col :span="8">
        <!-- 最近通话记录 -->
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

        <!-- 公告栏 -->
        <el-card shadow="never" class="wb-card wb-notice-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-bell"></i> 公告通知</span>
          </div>
          <div v-loading="noticeLoading">
            <div v-if="noticeList.length === 0" class="wb-empty">暂无公告</div>
            <div class="wb-notice-item" v-for="item in noticeList" :key="item.noticeId" @click="handleNotice(item)">
              <div class="wb-notice-top">
                <el-tag v-if="item.isTop === '1'" size="mini" type="danger" effect="dark">置顶</el-tag>
                <span class="wb-notice-title">{{ item.noticeTitle }}</span>
              </div>
              <div class="wb-notice-time">{{ item.publishTime }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建待办弹窗 -->
    <el-dialog title="新建待办" :visible.sync="todoOpen" width="520px" append-to-body>
      <el-form ref="todoForm" :model="todoForm" :rules="todoRules" label-width="80px" size="small">
        <el-form-item label="标题" prop="todoTitle">
          <el-input v-model="todoForm.todoTitle" placeholder="请输入待办标题" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="todoForm.priority" style="width: 100%">
            <el-option label="紧急" value="1" />
            <el-option label="普通" value="2" />
            <el-option label="低" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="todoForm.dueDate" type="date" value-format="yyyy-MM-dd" placeholder="选择到期日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="todoForm.todoContent" type="textarea" :rows="3" placeholder="请输入待办内容" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="todoOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitTodo">确 定</el-button>
      </div>
    </el-dialog>

    <!-- 公告详情弹窗 -->
    <el-dialog title="公告详情" :visible.sync="noticeOpen" width="600px" append-to-body>
      <div v-if="noticeDetail.noticeTitle" class="wb-notice-detail">
        <h3>{{ noticeDetail.noticeTitle }}</h3>
        <div class="wb-notice-meta">发布时间：{{ noticeDetail.publishTime }}</div>
        <div class="wb-notice-content">{{ noticeDetail.noticeContent }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  getWorkbenchStats, getWorkbenchTodos, getWorkbenchRecentCalls, getWorkbenchNotices,
  addTodo, processTodo, deferTodo, ignoreTodo
} from "@/api/lawyers/workbench"

export default {
  name: "Workbench",
  data() {
    return {
      currentDate: '',
      userName: '',
      todoLoading: false,
      recordLoading: false,
      noticeLoading: false,
      stats: {
        todayCalls: 0,
        serviceDuration: 0,
        satisfaction: 0,
        onlineDuration: 0,
        agentStatus: '0'
      },
      todoList: [],
      recentRecords: [],
      noticeList: [],
      calendarValue: new Date(),
      todoOpen: false,
      noticeOpen: false,
      todoForm: { todoTitle: '', priority: '2', dueDate: '', todoContent: '' },
      todoRules: {
        todoTitle: [{ required: true, message: '请输入待办标题', trigger: 'blur' }],
        priority: [{ required: true, message: '请选择优先级', trigger: 'change' }]
      },
      noticeDetail: {},
      quickList: [
        { name: '话务面板', icon: 'el-icon-phone', type: 'blue', path: '/lawyers/callCenter/callPanel' },
        { name: '图文服务', icon: 'el-icon-chat-dot-square', type: 'teal', path: '/lawyers/chat' },
        { name: '咨询台账', icon: 'el-icon-document', type: 'blue2', path: '/lawyers/callCenter/callLedger' },
        { name: '工单管理', icon: 'el-icon-edit', type: 'green', path: '/lawyers/callCenter/callTicket' }
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
      this.loadNotices()
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
    loadNotices() {
      this.noticeLoading = true
      getWorkbenchNotices(5).then(res => {
        this.noticeList = res.data || []
        this.noticeLoading = false
      }).catch(() => { this.noticeLoading = false })
    },
    hasTodoOnDate(day) {
      return this.todoList.some(t => t.dueDate && t.dueDate.indexOf(day) === 0 && t.status === '0')
    },
    handleAddTodo() {
      this.todoForm = { todoTitle: '', priority: '2', dueDate: '', todoContent: '' }
      this.todoOpen = true
    },
    submitTodo() {
      this.$refs.todoForm.validate(valid => {
        if (!valid) return
        addTodo(this.todoForm).then(() => {
          this.$message.success('新建待办成功')
          this.todoOpen = false
          this.loadTodos()
        }).catch(() => {})
      })
    },
    handleProcessTodo(item) {
      this.$confirm('确认处理待办"' + item.todoTitle + '"？', '处理待办', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'success'
      }).then(() => {
        processTodo(item.todoId).then(() => {
          this.$message.success('已标记为已处理')
          this.loadTodos()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleDeferTodo(item) {
      deferTodo(item.todoId).then(() => {
        this.$message.success('已延后')
        this.loadTodos()
      }).catch(() => {})
    },
    handleIgnoreTodo(item) {
      ignoreTodo(item.todoId).then(() => {
        this.$message.success('已忽略')
        this.loadTodos()
      }).catch(() => {})
    },
    handleNotice(item) {
      this.noticeDetail = item
      this.noticeOpen = true
    },
    handleQuickClick(item) {
      if (item.path) {
        this.$router.push({ path: item.path })
      } else {
        this.$message.info(item.name + ' 敬请期待')
      }
    },
    priorityLabel(p) {
      return { '1': '紧急', '2': '普通', '3': '低' }[p] || '普通'
    },
    priorityTag(p) {
      return { '1': 'danger', '2': 'warning', '3': 'info' }[p] || 'warning'
    },
    statusLabel(s) {
      return { '0': '待办', '1': '已完成', '2': '已延后', '3': '已忽略' }[s] || '待办'
    },
    statusTag(s) {
      return { '1': 'success', '2': 'warning', '3': 'info' }[s] || 'info'
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
  padding: 16px;
  background: #f1f5f9;
  min-height: calc(100vh - 84px);
}

.wb-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
  border-radius: 10px;
  padding: 24px 28px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    right: -40px; top: -40px;
    width: 200px; height: 200px;
    border-radius: 50%;
    background: rgba(255,255,255,0.06);
  }
  .wb-banner-left h2 { margin: 0 0 6px 0; font-size: 22px; font-weight: 700; }
  .wb-banner-left p { margin: 0; font-size: 13px; opacity: 0.85; }
  .wb-banner-right { display: flex; gap: 40px; z-index: 1; }
  .wb-banner-stat { text-align: right; }
  .wb-banner-num { display: block; font-size: 24px; font-weight: 700; }
  .wb-banner-label { display: block; font-size: 12px; opacity: 0.8; margin-top: 4px; }
}

.wb-stat-row {
  margin-bottom: 16px;
  .wb-stat-card {
    display: flex;
    align-items: center;
    padding: 20px;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
    .wb-stat-icon {
      width: 52px; height: 52px; border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      margin-right: 16px; font-size: 26px; color: #fff;
    }
    .wb-stat-body { flex: 1; }
    .wb-stat-value { font-size: 24px; font-weight: 700; color: #1e293b; line-height: 1.2; }
    .wb-stat-title { font-size: 13px; color: #64748b; margin-top: 4px; }
  }
  .wb-sicon-blue { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
  .wb-sicon-green { background: linear-gradient(135deg, #10b981, #059669); }
  .wb-sicon-orange { background: linear-gradient(135deg, #f59e0b, #d97706); }
  .wb-sicon-purple { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
}

.wb-main { margin-bottom: 0; }

.wb-card {
  border-radius: 10px;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  ::v-deep .el-card__body { padding: 16px 20px; }
  .wb-card-header {
    display: flex; align-items: center; justify-content: space-between;
    font-weight: 600; font-size: 14px; color: #1e293b;
    i { color: #3b82f6; margin-right: 6px; }
  }
}

.wb-empty {
  text-align: center;
  color: #94a3b8;
  padding: 24px 0;
  font-size: 13px;
}

.wb-todo-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: none; }
  .wb-todo-main {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    .wb-todo-title { font-size: 14px; color: #1e293b; font-weight: 500; }
    .wb-todo-done { text-decoration: line-through; color: #94a3b8; }
    .wb-todo-content { font-size: 12px; color: #64748b; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .wb-todo-due { font-size: 12px; color: #f59e0b; }
  }
}

.wb-cal-cell {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  .wb-cal-dot {
    width: 6px; height: 6px; border-radius: 50%;
    background: #ef4444; margin-top: 2px;
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
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.wb-quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 4px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  &:hover { background: #f8fafc; }
  .wb-quick-icon {
    width: 46px; height: 46px; border-radius: 12px;
    display: flex; align-items: center; justify-content: center;
    font-size: 22px; margin-bottom: 8px;
  }
  span { font-size: 13px; color: #475569; }
}
.wb-qicon-blue { background: #eff6ff; color: #3b82f6; }
.wb-qicon-green { background: #f0fdf4; color: #16a34a; }
.wb-qicon-blue2 { background: #e0e7ff; color: #6366f1; }
.wb-qicon-teal { background: #ccfbf1; color: #0d9488; }

.wb-record-item {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  &:last-child { border-bottom: none; }
  .wb-record-top { display: flex; justify-content: space-between; align-items: center; }
  .wb-record-phone { font-size: 14px; font-weight: 600; color: #1e293b; }
  .wb-record-name { font-size: 12px; color: #475569; margin-top: 4px; }
  .wb-record-time { font-size: 12px; color: #94a3b8; margin-top: 2px; }
}

.wb-notice-item {
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  &:last-child { border-bottom: none; }
  &:hover .wb-notice-title { color: #1d4ed8; }
  .wb-notice-top { display: flex; align-items: center; gap: 6px; }
  .wb-notice-title { font-size: 13px; color: #3b82f6; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .wb-notice-time { font-size: 12px; color: #94a3b8; margin-top: 4px; }
}

.wb-notice-detail {
  h3 { margin: 0 0 12px 0; color: #1e293b; }
  .wb-notice-meta { font-size: 13px; color: #94a3b8; margin-bottom: 16px; }
  .wb-notice-content { line-height: 1.8; color: #334155; white-space: pre-wrap; }
}

.wb-section { margin-bottom: 0; }
</style>
