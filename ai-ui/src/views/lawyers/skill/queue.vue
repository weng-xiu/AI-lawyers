<template>
  <div class="app-container">
    <!-- 今日排队实时统计卡 -->
    <el-row :gutter="16" class="mb8">
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#E6A23C">{{ stat.queuing }}</div><div>当前排队</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#2B8C6E">{{ stat.assigned }}</div><div>今日已分配</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#8C8C8C">{{ stat.overflow }}</div><div>今日超时溢出</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never" class="stat-card"><div class="stat-num" style="color:#C63D4A">{{ stat.kicked }}</div><div>今日已放弃/踢除</div></el-card></el-col>
    </el-row>

    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="queueList"
      :loading="loading"
      :total="total"
      :show-toolbar="false"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="68px"
      empty-text="暂无排队数据"
      empty-hint="来电排队后实时展示，可对排队中的来电手动分配或踢除"
      @query="getList"
      @reset="resetQuery"
      @pagination="getList">
      <!-- 技能组切换即重查（原 @change="getList" 行为），走查询插槽 -->
      <template #query-groupId>
        <el-select v-model="queryParams.groupId" placeholder="全部" clearable filterable style="width:180px"
          @change="getList">
          <el-option v-for="g in groupList" :key="g.groupId" :label="g.groupName" :value="g.groupId" />
        </el-select>
      </template>
      <template #query-extra>
        <el-button type="success" icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </template>
      <template #column-queueStatus="{ row }">
        <el-tag size="mini" :type="statusType(row.queueStatus)">{{ statusLabel(row.queueStatus) }}</el-tag>
      </template>
      <template #column-agentName="{ row }">{{ row.agentName || '-' }}</template>
      <template #column-strategyUsed="{ row }">{{ strategyLabel(row.strategyUsed) }}</template>
      <template #column-waitDuration="{ row }">
        <span :style="{color: row.waitDuration>30?'#C63D4A':'#1F2A3A'}">{{ row.waitDuration || 0 }}秒</span>
      </template>
      <template #column-action="{ row }">
        <el-button v-if="row.queueStatus==='0'" type="text" size="mini" icon="el-icon-user"
          @click="openAssign(row)" v-hasPermi="['lawyers:queue:assign']">手动分配</el-button>
        <el-button v-if="row.queueStatus==='0'" type="text" size="mini" icon="el-icon-close" style="color:#C63D4A"
          @click="handleKick(row)" v-hasPermi="['lawyers:queue:kick']">踢除</el-button>
      </template>
    </ProTable>

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
import { createPoller } from '@/utils/polling'

export default {
  name: 'SkillQueue',
  data() {
    return {
      loading: false, assignOpen: false,
      queueList: [], groupList: [], freeAgents: [], currentRow: null, assignAgentId: null,
      total: 0,
      queryParams: { pageNum: 1, pageSize: 10, groupId: null, callerNumber: '', queueStatus: '' },
      // V3：ProTable 列定义（状态/分配坐席/策略/等待时长/操作走插槽，移除原 fixed="right"）
      columns: [
        { prop: 'queueId', label: '排队ID', width: 80 },
        { prop: 'callerNumber', label: '主叫号码', width: 130 },
        { prop: 'groupName', label: '技能组', width: 120 },
        { prop: 'queueStatus', label: '状态', width: 100, align: 'center', slot: true },
        { prop: 'agentName', label: '分配坐席', width: 110, slot: true },
        { prop: 'strategyUsed', label: '策略', width: 100, slot: true },
        { prop: 'waitDuration', label: '等待时长', width: 100, align: 'center', slot: true },
        { prop: 'enqueueTime', label: '入队时间', width: 160 },
        { prop: 'dequeueTime', label: '出队时间', width: 160 },
        { prop: 'action', label: '操作', width: 170, slot: true }
      ],
      stat: { queuing: 0, assigned: 0, overflow: 0, kicked: 0 },
      statusOptions: [
        { value: '0', label: '排队中' }, { value: '1', label: '已分配' },
        { value: '2', label: '超时溢出' }, { value: '3', label: '已放弃' }, { value: '4', label: '无可用' }
      ]
    }
  },
  computed: {
    queryFormItems() {
      return [
        // 技能组下拉保留 @change 即查，走查询插槽
        { prop: 'groupId', label: '技能组', slot: true },
        { prop: 'callerNumber', label: '主叫', type: 'input', placeholder: '主叫号码' },
        { prop: 'queueStatus', label: '状态', type: 'select', placeholder: '全部', width: 140, options: this.statusOptions }
      ]
    }
  },
  created() {
    this.loadGroups()
    this.getList()
    // P6：统一轮询——页面不可见暂停、恢复可见立即补拉
    this.poller = createPoller(() => this.refreshStat(), 10000)
    this.poller.start()
  },
  beforeDestroy() { if (this.poller) this.poller.stop() },
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
        // 若后端返回了今日统计字段，直接使用
        const s = res.data || res.stat || {}
        if (s.assigned != null || s.overflow != null || s.kicked != null) {
          this.stat.assigned = s.assigned != null ? s.assigned : this.stat.assigned
          this.stat.overflow = s.overflow != null ? s.overflow : this.stat.overflow
          this.stat.kicked = s.kicked != null ? s.kicked : this.stat.kicked
          this._statFromList = true
        } else {
          this._statFromList = false
          this.refreshStat()
        }
      })
    },
    refreshStat() {
      // 当前排队数（实时）
      listQueuing(this.queryParams.groupId).then(res => {
        const list = res.data || []
        this.stat.queuing = list.length
      })
      // 今日各状态统计：优先使用后端列表返回的统计字段，否则按状态分别查询总数
      const baseParams = { pageNum: 1, pageSize: 1, groupId: this.queryParams.groupId }
      if (this.stat.assigned !== undefined && this._statFromList) {
        // 已有列表数据提供的统计字段，直接使用
        return
      }
      listQueue({ ...baseParams, queueStatus: '1' }).then(res => {
        this.stat.assigned = res.total || 0
      }).catch(() => {})
      listQueue({ ...baseParams, queueStatus: '2' }).then(res => {
        this.stat.overflow = res.total || 0
      }).catch(() => {})
      listQueue({ ...baseParams, queueStatus: '3' }).then(res => {
        this.stat.kicked = res.total || 0
      }).catch(() => {})
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        title: '踢除排队确认',
        confirmText: '确认踢除',
        content: '将踢除当前排队中的一通来电，该来电会被引导至溢出/结束流程',
        count: 1,
        countNoun: '通来电',
        tip: '踢除会影响公众来电话务体验，请确认该通话确需清理'
      }).then(() => {
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
