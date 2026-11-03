<template>
  <div class="app-container outbound-result-page">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="4" :offset="2">
        <el-card shadow="hover" class="stat-card stat-total">
          <div class="stat-num">{{ statistics.totalCount || 0 }}</div>
          <div class="stat-label">总数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-answered">
          <div class="stat-num">{{ statistics.answeredCount || 0 }}</div>
          <div class="stat-label">接通</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-missed">
          <div class="stat-num">{{ statistics.noAnswerCount || 0 }}</div>
          <div class="stat-label">未接</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-failed">
          <div class="stat-num">{{ statistics.failedCount || 0 }}</div>
          <div class="stat-label">失败</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card stat-avg">
          <div class="stat-num">{{ formatDuration(statistics.avgDuration) }}</div>
          <div class="stat-label">平均通话时长</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- V3/U：查询区/表格/分页骨架收敛到 ProTable；顶部结果统计卡保留 -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="resultList"
      :loading="loading"
      :total="total"
      :show-toolbar="false"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="80px"
      empty-text="暂无外呼结果"
      empty-hint="任务执行后，这里会展示每个号码的呼叫结果"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <!-- 任务下拉需在切换后即时刷新统计，走自定义查询插槽 -->
      <template #query-taskId>
        <el-select v-model="queryParams.taskId" placeholder="全部任务" clearable filterable style="width:220px" @change="handleTaskChange">
          <el-option v-for="t in taskList" :key="t.taskId" :label="t.taskName || ('任务#' + t.taskId)" :value="t.taskId" />
        </el-select>
      </template>
      <template #column-calleeName="{ row }">{{ row.calleeName || '-' }}</template>
      <template #column-callStatus="{ row }">
        <el-tag size="mini" :type="callStatusType(row.callStatus)">{{ callStatusLabel(row.callStatus) }}</el-tag>
      </template>
      <template #column-callDuration="{ row }">{{ formatDuration(row.callDuration) }}</template>
      <template #column-hangupCause="{ row }">{{ row.hangupCause || '-' }}</template>
      <template #column-retryCount="{ row }">{{ row.retryCount || 0 }}</template>
      <template #column-callTime="{ row }">{{ parseTime(row.callTime) }}</template>
    </ProTable>
  </div>
</template>

<script>
import { listResult, getTaskResultStatistics } from '@/api/lawyers/outboundResult'
import { listTask } from '@/api/lawyers/outboundTask'

export default {
  name: 'OutboundResult',
  data() {
    return {
      loading: false,
      total: 0,
      resultList: [],
      taskList: [],
      // V3：ProTable 列定义（姓名/状态/时长/挂断原因/重试/时间走插槽）
      columns: [
        { prop: 'resultId', label: '结果ID', width: 90, align: 'center' },
        { prop: 'calleeNumber', label: '被叫号码', width: 140, align: 'center' },
        { prop: 'calleeName', label: '被叫姓名', width: 120, align: 'center', slot: true },
        { prop: 'taskId', label: '任务ID', width: 100, align: 'center' },
        { prop: 'callStatus', label: '呼叫状态', width: 100, align: 'center', slot: true },
        { prop: 'callDuration', label: '通话时长', width: 110, align: 'center', slot: true },
        { prop: 'hangupCause', label: '挂断原因', minWidth: 140, align: 'center', slot: true },
        { prop: 'retryCount', label: '重试次数', width: 90, align: 'center', slot: true },
        { prop: 'callTime', label: '呼叫时间', width: 170, align: 'center', slot: true }
      ],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        taskId: undefined,
        callStatus: undefined,
        calleeNumber: undefined
      },
      statistics: {
        totalCount: 0,
        answeredCount: 0,
        noAnswerCount: 0,
        failedCount: 0,
        avgDuration: 0
      }
    }
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'taskId', label: '任务', slot: true },
        {
          prop: 'callStatus',
          label: '呼叫状态',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '待呼叫', value: '0' },
            { label: '呼叫中', value: '1' },
            { label: '已接通', value: '2' },
            { label: '未接听', value: '3' },
            { label: '呼叫失败', value: '4' }
          ]
        },
        { prop: 'calleeNumber', label: '被叫号码', placeholder: '被叫号码', width: 180 }
      ]
    }
  },
  created() {
    this.loadTasks()
    this.getList()
  },
  methods: {
    loadTasks() {
      listTask({ pageNum: 1, pageSize: 200 }).then(res => {
        this.taskList = res.rows || []
      })
    },
    getList() {
      this.loading = true
      listResult(this.queryParams).then(res => {
        this.resultList = res.rows || []
        this.total = res.total || 0
        this.loading = false
        this.loadStatistics()
      }).catch(() => { this.loading = false })
    },
    loadStatistics() {
      if (this.queryParams.taskId) {
        getTaskResultStatistics(this.queryParams.taskId).then(res => {
          const d = res.data || {}
          this.statistics = {
            totalCount: d.totalCount != null ? d.totalCount : (d.total || 0),
            answeredCount: d.answeredCount != null ? d.answeredCount : (d.answered || 0),
            noAnswerCount: d.noAnswerCount != null ? d.noAnswerCount : (d.noAnswer || 0),
            failedCount: d.failedCount != null ? d.failedCount : (d.failed || 0),
            avgDuration: d.avgDuration != null ? d.avgDuration : (d.avgCallDuration || 0)
          }
        }).catch(() => {})
      } else {
        // 未选择任务时，使用当前页结果粗略统计
        const list = this.resultList
        const total = this.total
        const answered = list.filter(r => String(r.callStatus) === '2').length
        const missed = list.filter(r => String(r.callStatus) === '3').length
        const failed = list.filter(r => String(r.callStatus) === '4').length
        const durations = list.filter(r => r.callDuration > 0).map(r => Number(r.callDuration) || 0)
        const avg = durations.length ? Math.round(durations.reduce((a, b) => a + b, 0) / durations.length) : 0
        this.statistics = {
          totalCount: total,
          answeredCount: answered,
          noAnswerCount: missed,
          failedCount: failed,
          avgDuration: avg
        }
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, taskId: undefined, callStatus: undefined, calleeNumber: undefined }
      this.getList()
    },
    handleTaskChange() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    callStatusLabel(s) {
      return { '0': '待呼叫', '1': '呼叫中', '2': '已接通', '3': '未接听', '4': '呼叫失败' }[s] || '未知'
    },
    callStatusType(s) {
      return { '0': 'info', '1': 'warning', '2': 'success', '3': 'warning', '4': 'danger' }[s] || 'info'
    },
    formatDuration(sec) {
      if (sec == null || sec === '' || isNaN(sec)) return '0秒'
      const s = Number(sec)
      if (s < 60) return s + '秒'
      const m = Math.floor(s / 60)
      const r = s % 60
      return m + '分' + (r > 0 ? r + '秒' : '')
    }
  }
}
</script>

<style scoped>
.outbound-result-page { padding: 16px; }
.stat-row { margin-bottom: 16px; }
.stat-card { text-align: center; border-radius: 8px; }
.stat-card >>> .el-card__body { padding: 18px 10px; }
.stat-num { font-size: 28px; font-weight: 700; line-height: 1.2; }
.stat-label { color: #5A6A7E; font-size: 13px; margin-top: 6px; }
.stat-total .stat-num { color: #255A99; }
.stat-answered .stat-num { color: #2B8C6E; }
.stat-missed .stat-num { color: #E6A23C; }
.stat-failed .stat-num { color: #C63D4A; }
.stat-avg .stat-num { color: #7C3AED; font-size: 22px; }
</style>
