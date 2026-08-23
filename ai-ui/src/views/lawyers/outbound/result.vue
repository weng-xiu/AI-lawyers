<template>
  <div class="app-container outbound-result-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="任务" prop="taskId">
          <el-select v-model="queryParams.taskId" placeholder="全部任务" clearable filterable style="width:220px" @change="handleTaskChange">
            <el-option v-for="t in taskList" :key="t.taskId" :label="t.taskName || ('任务#' + t.taskId)" :value="t.taskId" />
          </el-select>
        </el-form-item>
        <el-form-item label="呼叫状态" prop="callStatus">
          <el-select v-model="queryParams.callStatus" placeholder="全部" clearable style="width:140px">
            <el-option label="待呼叫" value="0" />
            <el-option label="呼叫中" value="1" />
            <el-option label="已接通" value="2" />
            <el-option label="未接听" value="3" />
            <el-option label="呼叫失败" value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="被叫号码" prop="calleeNumber">
          <el-input v-model="queryParams.calleeNumber" placeholder="被叫号码" clearable style="width:180px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

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

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="resultList" border size="small">
        <el-table-column label="结果ID" align="center" prop="resultId" width="90" />
        <el-table-column label="被叫号码" align="center" prop="calleeNumber" width="140" />
        <el-table-column label="被叫姓名" align="center" prop="calleeName" width="120">
          <template slot-scope="scope">{{ scope.row.calleeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="任务ID" align="center" prop="taskId" width="100" />
        <el-table-column label="呼叫状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="callStatusType(scope.row.callStatus)">{{ callStatusLabel(scope.row.callStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通话时长" align="center" width="110">
          <template slot-scope="scope">{{ formatDuration(scope.row.callDuration) }}</template>
        </el-table-column>
        <el-table-column label="挂断原因" align="center" prop="hangupCause" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.hangupCause || '-' }}</template>
        </el-table-column>
        <el-table-column label="重试次数" align="center" prop="retryCount" width="90">
          <template slot-scope="scope">{{ scope.row.retryCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="呼叫时间" align="center" prop="callTime" width="170">
          <template slot-scope="scope">{{ parseTime(scope.row.callTime) }}</template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
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
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, taskId: undefined, callStatus: undefined, calleeNumber: undefined }
      this.handleQuery()
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
