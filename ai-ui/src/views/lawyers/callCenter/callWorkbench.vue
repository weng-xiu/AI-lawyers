<template>
  <div class="app-container">
    <el-row :gutter="20">
      <el-col :span="16">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>今日来电统计</span>
            <el-button size="mini" style="float:right" @click="refreshData">刷新</el-button>
          </div>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="stat-card">
                <div class="stat-icon incoming">
                  <i class="el-icon-phone-incoming"></i>
                </div>
                <div class="stat-info">
                  <div class="stat-value">{{ todayStats.totalCount }}</div>
                  <div class="stat-label">总来电</div>
                </div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card">
                <div class="stat-icon answered">
                  <i class="el-icon-phone"></i>
                </div>
                <div class="stat-info">
                  <div class="stat-value">{{ todayStats.answeredCount }}</div>
                  <div class="stat-label">已接来电</div>
                </div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card">
                <div class="stat-icon missed">
                  <i class="el-icon-phone-outgoing"></i>
                </div>
                <div class="stat-info">
                  <div class="stat-value">{{ todayStats.unansweredCount }}</div>
                  <div class="stat-label">未接来电</div>
                </div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card">
                <div class="stat-icon duration">
                  <i class="el-icon-clock"></i>
                </div>
                <div class="stat-info">
                  <div class="stat-value">{{ formatDuration(todayStats.totalDuration) }}</div>
                  <div class="stat-label">总通话时长</div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <el-card class="box-card" style="margin-top: 20px">
          <div slot="header" class="clearfix">
            <span>最近来电记录</span>
            <el-button size="mini" style="float:right" @click="goToRecords">查看全部</el-button>
          </div>
          <el-table :data="recentRecords" stripe>
            <el-table-column label="来电号码" align="center" prop="callerNumber" />
            <el-table-column label="来电姓名" align="center" prop="callerName" />
            <el-table-column label="咨询分类" align="center" prop="consultationCategory" />
            <el-table-column label="状态" align="center" prop="status">
              <template slot-scope="scope">
                <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="通话时长" align="center" prop="callDuration">
              <template slot-scope="scope">
                {{ scope.row.callDuration }}秒
              </template>
            </el-table-column>
            <el-table-column label="来电时间" align="center" prop="callTime" width="180">
              <template slot-scope="scope">
                {{ parseTime(scope.row.callTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="box-card" style="margin-top: 20px">
          <div slot="header" class="clearfix">
            <span>待处理工单</span>
            <el-button size="mini" style="float:right" @click="goToTickets">查看全部</el-button>
          </div>
          <el-table :data="pendingTickets" stripe>
            <el-table-column label="工单号" align="center" prop="ticketNo" />
            <el-table-column label="工单标题" align="center" prop="title" />
            <el-table-column label="优先级" align="center" prop="priority">
              <template slot-scope="scope">
                <el-tag :type="getPriorityType(scope.row.priority)">{{ getPriorityLabel(scope.row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" align="center" prop="createTime" width="180">
              <template slot-scope="scope">
                {{ parseTime(scope.row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="box-card">
          <div slot="header" class="clearfix">
            <span>坐席状态</span>
          </div>
          <div class="agent-status-card">
            <div class="agent-avatar">
              <i class="el-icon-user" :class="{ 'online': agentStatus.status == '1', 'offline': agentStatus.status != '1' }"></i>
            </div>
            <div class="agent-info">
              <div class="agent-name">{{ agentStatus.agentName }}</div>
              <div class="agent-department">{{ agentStatus.deptName }}</div>
            </div>
            <div class="agent-status-badge">
              <el-tag :type="getAgentStatusType(agentStatus.status)" size="large">
                {{ getAgentStatusLabel(agentStatus.status) }}
              </el-tag>
            </div>
          </div>
          <div class="status-actions" style="margin-top: 20px">
            <el-button
              v-if="agentStatus.status != '1'"
              type="success"
              icon="el-icon-power"
              @click="handleLogin"
              style="width: 100%"
            >登录</el-button>
            <el-button
              v-if="agentStatus.status == '1'"
              type="danger"
              icon="el-icon-power-off"
              @click="handleLogout"
              style="width: 100%; margin-bottom: 10px"
            >退出登录</el-button>
            <el-select
              v-if="agentStatus.status == '1'"
              v-model="statusSelect"
              placeholder="切换状态"
              style="width: 100%"
              @change="handleStatusChange"
            >
              <el-option label="在线" value="1" />
              <el-option label="忙碌" value="2" />
              <el-option label="休息" value="3" />
            </el-select>
          </div>
        </el-card>

        <el-card class="box-card" style="margin-top: 20px">
          <div slot="header" class="clearfix">
            <span>在线坐席</span>
          </div>
          <div class="online-agents-list">
            <div
              v-for="agent in onlineAgents"
              :key="agent.agentId"
              class="online-agent-item"
            >
              <div class="agent-dot online"></div>
              <div class="agent-name">{{ agent.agentName }}</div>
              <div class="agent-status">{{ getAgentStatusLabel(agent.status) }}</div>
            </div>
          </div>
        </el-card>

        <el-card class="box-card" style="margin-top: 20px">
          <div slot="header" class="clearfix">
            <span>快捷操作</span>
          </div>
          <div class="quick-actions">
            <el-button type="primary" icon="el-icon-phone" @click="goToRecords">来电记录</el-button>
            <el-button type="success" icon="el-icon-tickets" @click="goToTickets">工单管理</el-button>
            <el-button type="warning" icon="el-icon-user" @click="goToAgents">坐席管理</el-button>
            <el-button type="info" icon="el-icon-data-line" @click="goToStatistics">统计分析</el-button>
          </div>
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
      agentStatus: {
        agentId: undefined,
        agentName: '当前用户',
        deptName: '法务部',
        status: '0'
      },
      statusSelect: '1',
      todayStats: {
        totalCount: 0,
        answeredCount: 0,
        unansweredCount: 0,
        totalDuration: 0
      },
      recentRecords: [],
      pendingTickets: [],
      onlineAgents: []
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
      this.getOnlineAgentList()
    },
    refreshData() {
      this.loadData()
      this.$modal.msgSuccess("数据已刷新")
    },
    getTodayStats() {
      getCallStatistics().then(response => {
        const stats = response.data
        this.todayStats = {
          totalCount: stats.todayCount || 0,
          answeredCount: stats.answeredCount || 0,
          unansweredCount: stats.unansweredCount || 0,
          totalDuration: stats.totalDuration || 0
        }
      })
    },
    getRecentRecords() {
      listRecord({ pageNum: 1, pageSize: 10 }).then(response => {
        this.recentRecords = response.rows || []
      })
    },
    getPendingTickets() {
      listTicket({ pageNum: 1, pageSize: 10, status: '0' }).then(response => {
        this.pendingTickets = response.rows || []
      })
    },
    getOnlineAgentList() {
      getOnlineAgents().then(response => {
        this.onlineAgents = response.data || []
      })
    },
    handleLogin() {
      agentLogin({ agentId: this.agentStatus.agentId || 1 }).then(response => {
        this.$modal.msgSuccess("登录成功")
        this.agentStatus.status = '1'
        this.statusSelect = '1'
        this.loadData()
      })
    },
    handleLogout() {
      agentLogout({ agentId: this.agentStatus.agentId || 1 }).then(response => {
        this.$modal.msgSuccess("退出成功")
        this.agentStatus.status = '0'
        this.statusSelect = '1'
        this.loadData()
      })
    },
    handleStatusChange() {
      updateAgentStatus({
        agentId: this.agentStatus.agentId || 1,
        status: this.statusSelect
      }).then(response => {
        this.$modal.msgSuccess("状态更新成功")
        this.agentStatus.status = this.statusSelect
      })
    },
    getStatusType(status) {
      const types = { '0': 'danger', '1': 'success', '2': 'warning', '3': 'info' }
      return types[status] || 'info'
    },
    getStatusLabel(status) {
      const labels = { '0': '未接', '1': '已接', '2': '已转接', '3': '已结束' }
      return labels[status] || '未知'
    },
    getAgentStatusType(status) {
      const types = { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }
      return types[status] || 'info'
    },
    getAgentStatusLabel(status) {
      const labels = { '0': '离线', '1': '在线', '2': '忙碌', '3': '休息' }
      return labels[status] || '未知'
    },
    getPriorityType(priority) {
      const types = { '0': 'info', '1': 'warning', '2': 'danger' }
      return types[priority] || 'info'
    },
    getPriorityLabel(priority) {
      const labels = { '0': '低', '1': '中', '2': '高' }
      return labels[priority] || '未知'
    },
    formatDuration(seconds) {
      const h = Math.floor(seconds / 3600)
      const m = Math.floor((seconds % 3600) / 60)
      const s = seconds % 60
      if (h > 0) {
        return `${h}时${m}分${s}秒`
      } else if (m > 0) {
        return `${m}分${s}秒`
      } else {
        return `${s}秒`
      }
    },
    goToRecords() {
      this.$router.push({ path: '/lawyers/callCenter/callRecord' })
    },
    goToTickets() {
      this.$router.push({ path: '/lawyers/callCenter/callTicket' })
    },
    goToAgents() {
      this.$router.push({ path: '/lawyers/callCenter/callAgent' })
    },
    goToStatistics() {
      this.$router.push({ path: '/lawyers/callCenter/callRecord' })
    }
  }
}
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  font-size: 24px;
}

.stat-icon.incoming {
  background: #e6f7ff;
  color: #1890ff;
}

.stat-icon.answered {
  background: #f6ffed;
  color: #52c41a;
}

.stat-icon.missed {
  background: #fff2f0;
  color: #ff4d4f;
}

.stat-icon.duration {
  background: #fffbe6;
  color: #faad14;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

.agent-status-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
}

.agent-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  color: #999;
  margin-bottom: 15px;
}

.agent-avatar .el-icon-user.online {
  color: #52c41a;
}

.agent-avatar .el-icon-user.offline {
  color: #999;
}

.agent-info {
  text-align: center;
  margin-bottom: 15px;
}

.agent-name {
  font-size: 18px;
  font-weight: bold;
  color: #333;
}

.agent-department {
  font-size: 14px;
  color: #999;
}

.status-actions {
  padding: 10px;
}

.online-agents-list {
  max-height: 300px;
  overflow-y: auto;
}

.online-agent-item {
  display: flex;
  align-items: center;
  padding: 10px;
  border-bottom: 1px solid #f0f0f0;
}

.online-agent-item:last-child {
  border-bottom: none;
}

.agent-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 10px;
}

.agent-dot.online {
  background: #52c41a;
}

.agent-name {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.agent-status {
  font-size: 12px;
  color: #999;
}

.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-actions .el-button {
  width: 100%;
}
</style>
