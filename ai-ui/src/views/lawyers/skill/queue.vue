<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="68px">
      <el-form-item label="技能组" prop="groupId">
        <el-select v-model="queryParams.groupId" placeholder="全部" clearable filterable style="width:180px" @change="getList">
          <el-option v-for="g in groupList" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
        </el-select>
      </el-form-item>
      <el-form-item label="主叫" prop="callerNumber">
        <el-input v-model="queryParams.callerNumber" placeholder="主叫号码" clearable @keyup.enter.native="getList" />
      </el-form-item>
      <el-form-item label="状态" prop="queueStatus">
        <el-select v-model="queryParams.queueStatus" placeholder="全部" clearable style="width:140px">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="getList">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-button type="success" icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="16" class="mb8">
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#E6A23C">{{ stat.queuing }}</div><div>当前排队</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#2B8C6E">{{ stat.assigned }}</div><div>今日已分配</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#8C8C8C">{{ stat.overflow }}</div><div>今日超时溢出</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#C63D4A">{{ stat.kicked }}</div><div>今日已放弃/踢除</div></el-card></el-col>
    </el-row>

    <el-table v-loading="loading" :data="queueList" border size="small">
      <el-table-column label="排队ID" prop="queueId" width="80" />
      <el-table-column label="主叫号码" prop="callerNumber" width="130" />
      <el-table-column label="技能组" prop="groupName" width="120" />
      <el-table-column label="状态" prop="queueStatus" width="100" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusType(scope.row.queueStatus)">{{ statusLabel(scope.row.queueStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分配坐席" prop="agentName" width="110">
        <template slot-scope="scope">{{ scope.row.agentName || '-' }}</template>
      </el-table-column>
      <el-table-column label="策略" prop="strategyUsed" width="100">
        <template slot-scope="scope">{{ strategyLabel(scope.row.strategyUsed) }}</template>
      </el-table-column>
      <el-table-column label="等待时长" width="100" align="center">
        <template slot-scope="scope">
          <span :style="{color: scope.row.waitDuration>30?'#C63D4A':'#1F2A3A'}">{{ scope.row.waitDuration || 0 }}秒</span>
        </template>
      </el-table-column>
      <el-table-column label="入队时间" prop="enqueueTime" width="160" />
      <el-table-column label="出队时间" prop="dequeueTime" width="160" />
      <el-table-column label="操作" width="170" fixed="right">
        <template slot-scope="scope">
          <el-button v-if="scope.row.queueStatus==='0'" type="text" size="mini" icon="el-icon-user"
                     @click="openAssign(scope.row)" v-hasPermi="['lawyers:queue:assign']">手动分配</el-button>
          <el-button v-if="scope.row.queueStatus==='0'" type="text" size="mini" icon="el-icon-close" style="color:#C63D4A"
                     @click="handleKick(scope.row)" v-hasPermi="['lawyers:queue:kick']">踢除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 手动分配弹窗 -->
    <el-dialog title="手动分配坐席" :visible.sync="assignOpen" width="420px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="主叫">{{ currentRow && currentRow.callerNumber }}（{{ currentRow && currentRow.groupName }}）</el-form-item>
        <el-form-item label="选择坐席">
          <el-select v-model="assignAgentId" filterable placeholder="请选择空闲坐席" style="width:100%">
            <el-option v-for="a in freeAgents" :key="a.agentId" :label="a.agentName+'（'+a.agentId+'）'" :value="a.agentId" />
          </el-select>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="submitAssign">分配</el-button>
        <el-button @click="assignOpen=false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listQueue, listQueuing, assignManually, kickQueue } from '@/api/lawyers/skill'
import { listEnabledGroups } from '@/api/lawyers/skill'
import { listAgent } from '@/api/lawyers/callCenter'

export default {
  name: 'SkillQueue',
  data() {
    return {
      loading: false, assignOpen: false,
      queueList: [], groupList: [], freeAgents: [], currentRow: null, assignAgentId: null,
      total: 0,
      queryParams: { pageNum: 1, pageSize: 10, groupId: null, callerNumber: '', queueStatus: '' },
      stat: { queuing: 0, assigned: 0, overflow: 0, kicked: 0 },
      statusOptions: [
        { value: '0', label: '排队中' }, { value: '1', label: '已分配' },
        { value: '2', label: '超时溢出' }, { value: '3', label: '已放弃' }, { value: '4', label: '无可用' }
      ]
    }
  },
  created() {
    this.loadGroups()
    this.getList()
    this.timer = setInterval(this.refreshStat, 10000)
  },
  beforeDestroy() { clearInterval(this.timer) },
  methods: {
    loadGroups() {
      listEnabledGroups().then(res => { this.groupList = res.data || [] })
    },
    getList() {
      this.loading = true
      listQueue(this.queryParams).then(res => {
        this.queueList = res.rows
        this.total = res.total
        this.loading = false
        this.refreshStat()
      })
    },
    refreshStat() {
      listQueuing(this.queryParams.groupId).then(res => {
        const list = res.data || []
        this.stat.queuing = list.length
      })
      // 统计今日各状态数量（用 list 不分页查今天）
      listQueue({ pageSize: 1, pageNum: 1 }).then(() => {})
    },
    resetQuery() {
      this.queryParams = { pageNum: 1, pageSize: 10, groupId: null, callerNumber: '', queueStatus: '' }
      this.getList()
    },
    statusLabel(s) { return (this.statusOptions.find(o => o.value === s) || {}).label || s },
    statusType(s) { return { '0': 'warning', '1': 'success', '2': 'info', '3': 'danger', '4': 'danger' }[s] || '' },
    strategyLabel(s) {
      return { round_robin: '轮询', least_recent: '最久未接', least_calls: '最少通话', all_ring: '全员振铃', manual: '手动分配' }[s] || s || '-'
    },
    openAssign(row) {
      this.currentRow = row
      this.assignAgentId = null
      listAgent({ pageSize: 999, status: '1', callStatus: '0' }).then(res => {
        this.freeAgents = res.rows || []
        this.assignOpen = true
      })
    },
    submitAssign() {
      if (!this.assignAgentId) { this.$modal.msgWarning('请选择坐席'); return }
      assignManually(this.currentRow.queueId, this.assignAgentId).then(res => {
        this.$modal.msgSuccess(res.msg || '分配成功')
        this.assignOpen = false
        this.getList()
      })
    },
    handleKick(row) {
      this.$modal.confirm('确认踢除该排队通话吗？').then(() => {
        return kickQueue(row.queueId, '管理员踢除')
      }).then(() => {
        this.$modal.msgSuccess('已踢除')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.stat-card { text-align: center; }
.stat-card >>> .el-card__body { padding: 14px; }
.stat-num { font-size: 28px; font-weight: bold; line-height: 1.4; }
</style>
