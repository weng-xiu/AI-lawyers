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
  </div>
</template>

<script>
import { getCallReport, getServiceReport, getQualityReport, getBusinessReport } from '@/api/lawyers/report'

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
      rows: []
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
</style>
