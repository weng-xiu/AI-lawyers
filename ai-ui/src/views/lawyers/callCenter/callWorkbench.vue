<template>
  <div class="call-workbench cc-page">
    <!-- 统计卡片区 -->
    <el-row :gutter="16" class="wb-stats-row">
      <el-col :span="4" :xs="12" v-for="(stat, idx) in statsList" :key="idx">
        <div class="wb-stat-card" :class="'wb-stat-' + stat.type">
          <div class="wb-stat-icon">
            <i :class="stat.icon"></i>
          </div>
          <div class="wb-stat-body">
            <div class="wb-stat-value">{{ stat.value }}</div>
            <div class="wb-stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="16" class="wb-section">
      <el-col :span="24">
        <el-card shadow="never" class="wb-quick-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-menu"></i> 快捷入口</span>
          </div>
          <div class="wb-quick-grid">
            <div class="wb-quick-item" @click="goToRecords">
              <div class="wb-quick-icon wb-icon-blue"><i class="el-icon-phone"></i></div>
              <span>未接来电</span>
            </div>
            <div class="wb-quick-item" @click="goToTickets">
              <div class="wb-quick-icon wb-icon-green"><i class="el-icon-tickets"></i></div>
              <span>工单处理</span>
            </div>
            <div class="wb-quick-item" @click="goToAgents">
              <div class="wb-quick-icon wb-icon-orange"><i class="el-icon-user"></i></div>
              <span>坐席管理</span>
            </div>
            <div class="wb-quick-item" @click="goToStatistics">
              <div class="wb-quick-icon wb-icon-purple"><i class="el-icon-data-line"></i></div>
              <span>统计分析</span>
            </div>
            <div class="wb-quick-item" @click="handleLogin" v-if="agentStatus.status != '1'">
              <div class="wb-quick-icon wb-icon-blue"><i class="el-icon-switch-button"></i></div>
              <span>坐席登录</span>
            </div>
            <div class="wb-quick-item" @click="handleLogout" v-else>
              <div class="wb-quick-icon wb-icon-red"><i class="el-icon-switch-button"></i></div>
              <span>退出坐席</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 两列内容 -->
    <el-row :gutter="16" class="wb-section">
      <!-- 最近来电 -->
      <el-col :span="14" :xs="24">
        <el-card shadow="never" class="wb-list-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-phone"></i> 最近来电</span>
            <el-button type="text" size="mini" @click="goToRecords">查看更多 ></el-button>
          </div>
          <el-table :data="recentRecords" size="small" :stripe="true">
            <el-table-column label="来电号码" align="center" prop="callerNumber" />
            <el-table-column label="来电姓名" align="center" prop="callerName" />
            <el-table-column label="咨询分类" align="center" prop="consultationCategory" />
            <el-table-column label="状态" align="center" prop="status" width="80">
              <template slot-scope="scope">
                <span :class="'wb-status-dot wb-status-' + scope.row.status"></span>
                {{ getStatusLabel(scope.row.status) }}
              </template>
            </el-table-column>
            <el-table-column label="来电时间" align="center" prop="callTime" width="150">
              <template slot-scope="scope">{{ parseTime(scope.row.callTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：坐席状态 + 待处理工单 -->
      <el-col :span="10" :xs="24">
        <!-- 我的坐席 -->
        <el-card shadow="never" class="wb-agent-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-user"></i> 我的坐席</span>
          </div>
          <div class="wb-agent-info">
            <div class="wb-agent-avatar" :class="{ 'wb-agent-online': agentStatus.status == '1' }">
              <i class="el-icon-user-solid"></i>
            </div>
            <div class="wb-agent-meta">
              <div class="wb-agent-name">{{ agentStatus.agentName }}</div>
              <div class="wb-agent-dept">{{ agentStatus.deptName }}</div>
              <div class="wb-agent-status" :class="'wb-agent-status-' + agentStatus.status">
                {{ getAgentStatusLabel(agentStatus.status) }}
              </div>
            </div>
            <div class="wb-agent-actions">
              <el-button v-if="agentStatus.status != '1'" type="primary" size="small" @click="handleLogin" style="width:100%">登录坐席</el-button>
              <template v-else>
                <el-button type="danger" size="small" @click="handleLogout" style="width:100%;margin-bottom:8px">退出坐席</el-button>
                <el-select v-model="statusSelect" @change="handleStatusChange" size="small" style="width:100%">
                  <el-option label="在线" value="1" />
                  <el-option label="忙碌" value="2" />
                  <el-option label="休息" value="3" />
                </el-select>
              </template>
            </div>
          </div>
        </el-card>

        <!-- 待处理工单 -->
        <el-card shadow="never" class="wb-ticket-card">
          <div slot="header" class="wb-card-header">
            <span><i class="el-icon-tickets"></i> 待处理工单</span>
            <el-button type="text" size="mini" @click="goToTickets">查看更多 ></el-button>
          </div>
          <div v-for="ticket in pendingTickets" :key="ticket.ticketId" class="wb-ticket-item">
            <div class="wb-ticket-title">
              <span class="wb-ticket-no">{{ ticket.ticketNo }}</span>
              <span class="wb-ticket-priority" :class="'wb-priority-' + ticket.priority">{{ getPriorityLabel(ticket.priority) }}</span>
            </div>
            <div class="wb-ticket-desc">{{ ticket.title }}</div>
            <div class="wb-ticket-time">{{ parseTime(ticket.createTime) }}</div>
          </div>
          <div v-if="pendingTickets.length === 0" class="wb-empty">暂无待处理工单</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { agentLogin, agentLogout, updateAgentStatus, getOnlineAgents, listRecord, listTicket, getCallStatistics } from "@/api/lawyers/callCenter"

export default {
  name: "CallWorkbench",
  data() {
    return {
      agentStatus: { agentId: undefined, agentName: '当前用户', deptName: '法务部', status: '0' },
      statusSelect: '1',
      todayStats: { totalCount: 0, answeredCount: 0, unansweredCount: 0, totalDuration: 0 },
      recentRecords: [],
      pendingTickets: []
    }
  },
  computed: {
    statsList() {
      return [
        { icon: 'el-icon-phone-incoming', value: this.todayStats.totalCount, label: '今日总来电', type: 'blue' },
        { icon: 'el-icon-phone', value: this.todayStats.answeredCount, label: '已接来电', type: 'green' },
        { icon: 'el-icon-phone-outline', value: this.todayStats.unansweredCount, label: '未接来电', type: 'red' },
        { icon: 'el-icon-time', value: this.formatDuration(this.todayStats.totalDuration), label: '通话时长', type: 'orange' },
        { icon: 'el-icon-user', value: this.todayStats.onlineCount || 0, label: '在线坐席', type: 'purple' },
        { icon: 'el-icon-tickets', value: this.pendingTickets.length, label: '待处理工单', type: 'cyan' }
      ]
    }
  },
  created() {
    this.loadData()
  },
  methods: {
    loadData() {
      this.getTodayStats()
      this.getRecentRecords()
      this.getPendingTickets()
    },
    getTodayStats() {
      getCallStatistics().then(response => {
        const stats = response.data || {}
        this.todayStats = {
          totalCount: stats.todayCount || 0,
          answeredCount: stats.answeredCount || 0,
          unansweredCount: stats.unansweredCount || 0,
          totalDuration: stats.totalDuration || 0,
          onlineCount: stats.onlineCount || 0
        }
      })
    },
    getRecentRecords() {
      listRecord({ pageNum: 1, pageSize: 8 }).then(response => { this.recentRecords = response.rows || [] })
    },
    getPendingTickets() {
      listTicket({ pageNum: 1, pageSize: 8, status: '0' }).then(response => { this.pendingTickets = response.rows || [] })
    },
    handleLogin() {
      agentLogin({ agentId: this.agentStatus.agentId || 1 }).then(() => {
        this.$modal.msgSuccess("登录成功")
        this.agentStatus.status = '1'
        this.statusSelect = '1'
        this.loadData()
      })
    },
    handleLogout() {
      agentLogout({ agentId: this.agentStatus.agentId || 1 }).then(() => {
        this.$modal.msgSuccess("退出成功")
        this.agentStatus.status = '0'
        this.statusSelect = '1'
        this.loadData()
      })
    },
    handleStatusChange() {
      updateAgentStatus({ agentId: this.agentStatus.agentId || 1, status: this.statusSelect }).then(() => {
        this.$modal.msgSuccess("状态更新成功")
        this.agentStatus.status = this.statusSelect
      })
    },
    getStatusLabel(s) { return { '0':'未接','1':'已接','2':'已转接','3':'已结束' }[s] || '未知' },
    getAgentStatusLabel(s) { return { '0':'离线','1':'在线','2':'忙碌','3':'休息' }[s] || '未知' },
    getPriorityLabel(p) { return { '0':'低','1':'中','2':'高' }[p] || '未知' },
    formatDuration(seconds) {
      const h = Math.floor(seconds / 3600), m = Math.floor((seconds % 3600) / 60), s = seconds % 60
      if (h > 0) return `${h}时${m}分`
      if (m > 0) return `${m}分${s}秒`
      return `${s}秒`
    },
    goToRecords() { this.$router.push({ path: '/lawyers/callCenter/callRecord' }) },
    goToTickets() { this.$router.push({ path: '/lawyers/callCenter/callTicket' }) },
    goToAgents() { this.$router.push({ path: '/lawyers/callCenter/callAgent' }) },
    goToStatistics() { this.$router.push({ path: '/lawyers/callCenter/callRecord' }) }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';

.call-workbench {
  background: #f0f2f5;
}

// 统计卡片
.wb-stats-row {
  margin-bottom: 16px;
}
.wb-stat-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  transition: all 0.3s;
}
.wb-stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(58, 92, 184, 0.12);
}
.wb-stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-right: 12px;
}
.wb-stat-blue .wb-stat-icon { background: rgba(58, 92, 184, 0.1); color: #3a5cb8; }
.wb-stat-green .wb-stat-icon { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.wb-stat-red .wb-stat-icon { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.wb-stat-orange .wb-stat-icon { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.wb-stat-purple .wb-stat-icon { background: rgba(140, 100, 220, 0.1); color: #8c64dc; }
.wb-stat-cyan .wb-stat-icon { background: rgba(58, 170, 220, 0.1); color: #3aaadc; }
.wb-stat-value { font-size: 22px; font-weight: 700; color: #303133; line-height: 1.2; }
.wb-stat-label { font-size: 12px; color: #909399; margin-top: 4px; }

// 卡片头部
.wb-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 15px;
}
.wb-card-header i {
  margin-right: 6px;
  color: #3a5cb8;
}

// 快捷入口
.wb-section { margin-bottom: 16px; }
.wb-quick-card {
  .el-card__body { padding: 24px; }
}
.wb-quick-grid {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.wb-quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  min-width: 80px;
  transition: all 0.3s;
}
.wb-quick-item:hover {
  transform: translateY(-3px);
}
.wb-quick-item:hover .wb-quick-icon {
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
.wb-quick-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  margin-bottom: 10px;
  transition: all 0.3s;
}
.wb-icon-blue { background: rgba(58, 92, 184, 0.1); color: #3a5cb8; }
.wb-icon-green { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.wb-icon-orange { background: rgba(230, 162, 60, 0.1); color: #e6a23c; }
.wb-icon-purple { background: rgba(140, 100, 220, 0.1); color: #8c64dc; }
.wb-icon-red { background: rgba(245, 108, 108, 0.1); color: #f56c6c; }
.wb-quick-item span { font-size: 13px; color: #606266; }

// 列表卡片
.wb-list-card {
  min-height: 480px;
}

// 坐席卡片
.wb-agent-card {
  margin-bottom: 16px;
}
.wb-agent-info {
  display: flex;
  gap: 16px;
}
.wb-agent-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #f0f2f5;
  border: 2px solid #e4e7ed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: #909399;
  flex-shrink: 0;
}
.wb-agent-avatar.wb-agent-online {
  border-color: #52c41a;
  color: #52c41a;
  background: rgba(82, 196, 26, 0.08);
}
.wb-agent-meta {
  flex: 1;
}
.wb-agent-name { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 4px; }
.wb-agent-dept { font-size: 12px; color: #909399; margin-bottom: 8px; }
.wb-agent-status {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
}
.wb-agent-status-0 { background: #f4f4f5; color: #909399; }
.wb-agent-status-1 { background: #f0f9eb; color: #52c41a; }
.wb-agent-status-2 { background: #fdf6ec; color: #e6a23c; }
.wb-agent-status-3 { background: #fef0f0; color: #f56c6c; }
.wb-agent-actions { margin-top: 16px; }

// 工单卡片
.wb-ticket-card { min-height: 320px; }
.wb-ticket-item {
  padding: 12px 0;
  border-bottom: 1px solid #f0f2f5;
}
.wb-ticket-item:last-child { border-bottom: none; }
.wb-ticket-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.wb-ticket-no { font-size: 13px; font-weight: 600; color: #3a5cb8; }
.wb-ticket-priority {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.wb-priority-0 { background: #f4f4f5; color: #909399; }
.wb-priority-1 { background: #fdf6ec; color: #e6a23c; }
.wb-priority-2 { background: #fef0f0; color: #f56c6c; }
.wb-ticket-desc { font-size: 13px; color: #303133; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.wb-ticket-time { font-size: 11px; color: #909399; }
.wb-empty { text-align: center; color: #909399; padding: 32px 0; font-size: 13px; }

// 状态圆点
.wb-status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.wb-status-0 { background: #f56c6c; }
.wb-status-1 { background: #52c41a; }
.wb-status-2 { background: #e6a23c; }
.wb-status-3 { background: #909399; }
</style>
