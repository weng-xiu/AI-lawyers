<template>
  <div class="app-container rp-page">
    <!-- 工具栏：日期范围 + 粒度 + 查询/导出 -->
    <el-form :inline="true" size="small" class="rp-toolbar">
      <el-form-item label="统计区间">
        <el-date-picker v-model="dateRange" type="daterange" value-format="yyyy-MM-dd"
                        range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期"
                        style="width: 260px" :clearable="false" />
      </el-form-item>
      <el-form-item label="统计粒度" v-if="activeTab !== 'service'">
        <el-radio-group v-model="granularity">
          <el-radio-button label="day">日</el-radio-button>
          <el-radio-button label="week">周</el-radio-button>
          <el-radio-button label="month">月</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="loadData">查询</el-button>
        <el-button type="warning" plain icon="el-icon-download" @click="handleExport"
                   v-hasPermi="['lawyers:report:export']">导出</el-button>
        <el-button type="info" plain icon="el-icon-refresh" @click="openBackfill"
                   v-hasPermi="['lawyers:report:export']">数据回填</el-button>
      </el-form-item>
    </el-form>

    <el-tabs v-model="activeTab" class="rp-tabs" @tab-click="loadData">
      <!-- 呼叫报表 -->
      <el-tab-pane label="呼叫报表" name="call">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="周期" prop="period" width="110" />
          <el-table-column label="呼叫总量" prop="totalCalls" align="right" />
          <el-table-column label="接通量" prop="answeredCalls" align="right" />
          <el-table-column label="未接量" prop="missedCalls" align="right" />
          <el-table-column label="转接量" prop="transferredCalls" align="right" />
          <el-table-column label="接通率" align="right">
            <template slot-scope="scope">{{ scope.row.answerRate }}%</template>
          </el-table-column>
          <el-table-column label="平均通话时长(秒)" prop="avgDurationSec" align="right" />
        </el-table>
      </el-tab-pane>

      <!-- 坐席服务报表 -->
      <el-tab-pane label="坐席服务报表" name="service">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="坐席" prop="agentName" width="140" />
          <el-table-column label="接线量" prop="totalCalls" align="right" />
          <el-table-column label="接通量" prop="answeredCalls" align="right" />
          <el-table-column label="接通率" align="right">
            <template slot-scope="scope">{{ scope.row.answerRate }}%</template>
          </el-table-column>
          <el-table-column label="平均通话时长(秒)" prop="avgDurationSec" align="right" />
          <el-table-column label="总通话时长(秒)" prop="totalTalkSec" align="right" />
        </el-table>
      </el-tab-pane>

      <!-- 质检报表 -->
      <el-tab-pane label="质检报表" name="quality">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="周期" prop="period" width="110" />
          <el-table-column label="质检量" prop="inspections" align="right" />
          <el-table-column label="平均分" prop="avgScore" align="right" />
          <el-table-column label="已复核" prop="reviewedCount" align="right" />
          <el-table-column label="待复核" prop="pendingCount" align="right" />
          <el-table-column label="关联风险数" prop="riskCount" align="right" />
        </el-table>
      </el-tab-pane>

      <!-- 业务工单报表 -->
      <el-tab-pane label="业务工单报表" name="business">
        <el-table v-loading="loading" :data="rows" border stripe>
          <el-table-column label="周期" prop="period" width="110" />
          <el-table-column label="工单量" prop="totalTickets" align="right" />
          <el-table-column label="办结量" prop="closedTickets" align="right" />
          <el-table-column label="办结率" align="right">
            <template slot-scope="scope">{{ scope.row.closeRate }}%</template>
          </el-table-column>
          <el-table-column label="超时量" prop="overdueTickets" align="right" />
          <el-table-column label="平均办结时长(分钟)" prop="avgCloseMinutes" align="right" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 分钟物化数据回填弹窗 -->
    <el-dialog title="分钟物化数据回填" :visible.sync="backfillOpen" width="520px" append-to-body
               :close-on-click-modal="false" @closed="onBackfillClosed">
      <!-- 选区间 -->
      <el-form v-if="!backfillTaskId" label-width="92px" size="small">
        <el-form-item label="回填区间">
          <el-date-picker v-model="backfillRange" type="datetimerange"
                          value-format="yyyy-MM-dd HH:mm" range-separator="-"
                          start-placeholder="开始时间" end-placeholder="结束时间"
                          style="width: 360px" :clearable="false" />
        </el-form-item>
        <el-form-item>
          <span class="bf-tip">按分钟聚合并幂等覆盖写入 ai_stat_minute，可重复执行；单次区间不超过 7 天，区间为半开 [开始, 结束)。提交后后台执行，可关闭弹窗，任务将继续运行。</span>
        </el-form-item>
      </el-form>

      <!-- 执行进度 -->
      <div v-else class="bf-prog">
        <el-progress :percentage="backfillPercent"
                     :status="backfillStatus === 'FAILED' ? 'exception' : (backfillStatus === 'SUCCESS' ? 'success' : undefined)" />
        <div class="bf-meta">
          <div>状态：<b>{{ statusText }}</b></div>
          <div>进度：{{ backfillProcessed }} / {{ backfillTotal }} 分钟</div>
          <div>已写入/更新指标：{{ backfillRows }} 条</div>
          <div v-if="backfillStatus === 'FAILED'" class="bf-err">{{ backfillError }}</div>
          <div v-if="backfillStatus === 'RUNNING'" class="bf-tip">后台执行中，可安全关闭弹窗。</div>
        </div>
      </div>

      <div slot="footer" class="dialog-footer">
        <template v-if="!backfillTaskId">
          <el-button @click="backfillOpen = false">取 消</el-button>
          <el-button type="primary" :loading="backfillLoading" @click="handleBackfill">开始回填</el-button>
        </template>
        <template v-else>
          <el-button v-if="backfillStatus === 'RUNNING'" @click="backfillOpen = false">后台运行</el-button>
          <el-button v-if="backfillStatus !== 'RUNNING'" type="primary" @click="resetBackfill">知道了</el-button>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getCallReport, getServiceReport, getQualityReport, getBusinessReport, statBackfill, statBackfillProgress } from '@/api/lawyers/report'

const REPORT_API = {
  call: getCallReport,
  service: getServiceReport,
  quality: getQualityReport,
  business: getBusinessReport
}
const REPORT_NAME = {
  call: '呼叫报表',
  service: '坐席服务报表',
  quality: '质检报表',
  business: '业务工单报表'
}

export default {
  name: 'StatisticsReport',
  data() {
    return {
      loading: false,
      activeTab: 'call',
      dateRange: [],
      granularity: 'day',
      rows: [],
      backfillOpen: false,
      backfillLoading: false,
      backfillRange: [],
      backfillTaskId: null,
      backfillStatus: '',
      backfillPercent: 0,
      backfillProcessed: 0,
      backfillTotal: 0,
      backfillRows: 0,
      backfillError: '',
      backfillTimer: null
    }
  },
  computed: {
    statusText() {
      if (this.backfillStatus === 'SUCCESS') return '已完成'
      if (this.backfillStatus === 'FAILED') return '失败'
      return '执行中'
    }
  },
  created() {
    // 默认近 7 天
    const end = new Date()
    const begin = new Date()
    begin.setDate(begin.getDate() - 6)
    this.dateRange = [this.formatDate(begin), this.formatDate(end)]
    this.loadData()
  },
  methods: {
    formatDate(d) {
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${d.getFullYear()}-${m}-${day}`
    },
    loadData() {
      if (!this.dateRange || this.dateRange.length !== 2) return
      this.loading = true
      const params = {
        beginTime: this.dateRange[0],
        endTime: this.dateRange[1],
        granularity: this.granularity
      }
      REPORT_API[this.activeTab](params).then(res => {
        this.rows = res.data || []
      }).finally(() => {
        this.loading = false
      })
    },
    handleExport() {
      const params = {
        beginTime: this.dateRange[0],
        endTime: this.dateRange[1],
        granularity: this.granularity
      }
      this.download('/lawyers/report/' + this.activeTab + '/export', params,
        `${REPORT_NAME[this.activeTab]}_${new Date().getTime()}.xlsx`)
    },
    // 打开回填弹窗：若有未完成任务直接展示进度，否则默认近 1 天（分钟归零）
    openBackfill() {
      if (this.backfillTaskId && this.backfillStatus === 'RUNNING') {
        this.backfillOpen = true
        return
      }
      const end = new Date()
      end.setSeconds(0, 0)
      const begin = new Date(end.getTime() - 24 * 60 * 60 * 1000)
      this.backfillRange = [this.formatMinute(begin), this.formatMinute(end)]
      this.backfillOpen = true
    },
    formatMinute(d) {
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      return `${d.getFullYear()}-${m}-${day} ${hh}:${mm}`
    },
    handleBackfill() {
      if (!this.backfillRange || this.backfillRange.length !== 2) {
        this.$message.warning('请选择回填区间')
        return
      }
      const begin = new Date(this.backfillRange[0].replace(' ', 'T'))
      const end = new Date(this.backfillRange[1].replace(' ', 'T'))
      if (!(begin < end)) {
        this.$message.warning('开始时间必须早于结束时间')
        return
      }
      if (begin < new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000)) {
        this.$message.warning('回填区间不能超过 7 天')
        return
      }
      this.$confirm(`确认回填 ${this.backfillRange[0]} 至 ${this.backfillRange[1]} 的分钟指标？`,
        '回填确认', { type: 'warning' }).then(() => {
        this.backfillLoading = true
        statBackfill({ beginTime: this.backfillRange[0], endTime: this.backfillRange[1] }).then(res => {
          this.backfillTaskId = res.taskId
          this.backfillStatus = 'RUNNING'
          this.startPolling()
        }).finally(() => {
          this.backfillLoading = false
        })
      }).catch(() => {})
    },
    startPolling() {
      this.stopPolling()
      this.pollProgress()
      this.backfillTimer = setInterval(this.pollProgress, 3000)
    },
    stopPolling() {
      if (this.backfillTimer) {
        clearInterval(this.backfillTimer)
        this.backfillTimer = null
      }
    },
    pollProgress() {
      statBackfillProgress(this.backfillTaskId).then(res => {
        const d = res.data || {}
        this.backfillStatus = d.status
        this.backfillPercent = d.percent || 0
        this.backfillProcessed = d.processedMinutes || 0
        this.backfillTotal = d.totalMinutes || 0
        this.backfillRows = d.rowsWritten || 0
        this.backfillError = d.errorMsg || ''
        if (d.status !== 'RUNNING') {
          this.stopPolling()
          if (d.status === 'SUCCESS') {
            this.loadData()
          }
        }
      })
    },
    resetBackfill() {
      this.backfillOpen = false
      this.backfillTaskId = null
      this.backfillStatus = ''
    },
    onBackfillClosed() {
      // 关弹窗不取消后台任务，仅停本地轮询
      this.stopPolling()
      if (this.backfillStatus !== 'RUNNING') {
        this.backfillTaskId = null
      }
    },
    beforeDestroy() {
      this.stopPolling()
    }
  }
}
</script>

<style lang="scss" scoped>
.rp-page {
  padding: 24px;
}
.rp-toolbar {
  background: #fff;
  padding: 16px 16px 0;
  border-radius: 4px 4px 0 0;
  margin-bottom: 0;
}
.rp-tabs {
  background: #fff;
  padding: 0 16px 16px;
  border-radius: 0 0 4px 4px;
}
.bf-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
.bf-prog {
  padding: 8px 8px 0;
}
.bf-meta {
  margin-top: 16px;
  font-size: 13px;
  line-height: 2;
  color: #606266;
}
.bf-err {
  color: #f56c6c;
  word-break: break-all;
}
</style>
