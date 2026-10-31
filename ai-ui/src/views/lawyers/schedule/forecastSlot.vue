<template>
  <div class="forecast-slot">
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" size="small" label-width="86px" @submit.native.prevent>
        <el-form-item label="预测天数">
          <el-select v-model="days" style="width:140px" aria-label="预测天数" @change="loadPreview">
            <el-option label="未来 7 天" :value="7" />
            <el-option label="未来 14 天" :value="14" />
          </el-select>
        </el-form-item>
        <el-form-item label="查看日期">
          <el-select v-model="selectedDate" style="width:190px" aria-label="查看日期" @change="renderChart">
            <el-option v-for="item in dateOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" :loading="loading" @click="loadPreview">刷新预测</el-button>
          <el-button icon="el-icon-data-line" size="mini" :loading="accuracyLoading" @click="loadAccuracy">对比验收</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="12" class="fs-stats">
      <el-col :xs="12" :sm="6">
        <div class="fs-stat-card">
          <div class="fs-stat-label">预测总话量</div>
          <div class="fs-stat-value">{{ totalVolume }}</div>
          <div class="fs-stat-unit">通 / 未来 {{ days }} 天</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="fs-stat-card">
          <div class="fs-stat-label">所选日话量</div>
          <div class="fs-stat-value">{{ selectedVolume }}</div>
          <div class="fs-stat-unit">{{ selectedDate || '暂无数据' }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="fs-stat-card">
          <div class="fs-stat-label">峰值时段</div>
          <div class="fs-stat-value fs-stat-value-sm">{{ peakSlot ? peakSlot.timeRange : '-' }}</div>
          <div class="fs-stat-unit">{{ peakSlot && peakSlot.volume ? peakSlot.volume + ' 通' : '暂无数据' }}</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="fs-stat-card">
          <div class="fs-stat-label">峰值坐席需求</div>
          <div class="fs-stat-value">{{ peakSlot ? peakSlot.agents : 0 }}</div>
          <div class="fs-stat-unit">按 AHT 与利用率推算</div>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never" class="fs-chart-card">
      <div slot="header" class="fs-card-header">
        <span>15 分钟粒度预测曲线（{{ selectedDate || '暂无数据' }}）</span>
        <el-tag size="mini" type="info">96 时段 / 天</el-tag>
      </div>
      <div ref="slotChart" class="fs-chart" role="img" aria-label="15 分钟粒度话务预测量折线图"></div>
      <div v-if="!currentRows.length" class="fs-empty">暂无预测数据（请先回填 ai_stat_minute 基线话务）</div>
    </el-card>

    <el-row :gutter="12">
      <el-col :xs="24" :sm="12">
        <el-card shadow="never" class="fs-table-card">
          <div slot="header">峰值时段 Top 5</div>
          <el-table :data="peakSlots" border size="mini">
            <el-table-column label="排名" align="center" width="60">
              <template slot-scope="scope">{{ scope.$index + 1 }}</template>
            </el-table-column>
            <el-table-column label="时段" align="center" prop="timeRange" />
            <el-table-column label="预测进线量" align="center" prop="volume" />
            <el-table-column label="坐席需求" align="center" prop="agents" />
          </el-table>
          <div v-if="!peakSlots.length" class="fs-empty">所选日期暂无可排班的话务量</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12">
        <el-card shadow="never" class="fs-table-card">
          <div slot="header" class="fs-card-header">
            <span>对比验收（近 {{ accuracy ? accuracy.days : compareDays }} 天）</span>
            <el-tag size="mini" :type="accuracyTag">{{ accuracyTagText }}</el-tag>
          </div>
          <div v-if="accuracy && accuracy.available">
            <div class="fs-acc-tip">
              剖面窗口已剔除最近 {{ accuracy.days }} 天，避免用同一批数据自评；共 {{ accuracy.slotCount }} 个 15 分钟时段参与对比。
            </div>
            <el-row :gutter="8" class="fs-acc-metrics">
              <el-col :span="8">
                <div class="fs-acc-label">MAE</div>
                <div class="fs-acc-value">{{ formatNumber(accuracy.mae) }} 通</div>
              </el-col>
              <el-col :span="8">
                <div class="fs-acc-label">MAPE</div>
                <div class="fs-acc-value">{{ formatPercent(accuracy.mape) }}</div>
              </el-col>
              <el-col :span="8">
                <div class="fs-acc-label">准确率</div>
                <div class="fs-acc-value">{{ formatPercent(accuracy.accuracy) }}</div>
              </el-col>
            </el-row>
            <el-table :data="accuracy.perDate" border size="mini" max-height="220">
              <el-table-column label="日期" align="center" prop="date" width="120" />
              <el-table-column label="预测话量" align="center" prop="forecastTotal" />
              <el-table-column label="实际话量" align="center" prop="actualTotal" />
              <el-table-column label="准确率" align="center" width="100">
                <template slot-scope="scope">{{ formatPercent(scope.row.accuracy) }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else class="fs-empty">
            近 {{ accuracy ? accuracy.days : compareDays }} 天暂无可对比的实际话务量（ai_stat_minute 尚未回填该窗口）
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { previewSlotForecast, getSlotAccuracy } from '@/api/lawyers/forecast'

const WEEKDAY_TEXT = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

export default {
  name: 'ForecastSlot',
  data() {
    return {
      days: 7,
      compareDays: 7,
      loading: false,
      accuracyLoading: false,
      slotRows: [],
      selectedDate: null,
      accuracy: null,
      chart: null
    }
  },
  computed: {
    dateOptions() {
      const seen = {}
      const options = []
      this.slotRows.forEach(row => {
        if (!row.forecastDate || seen[row.forecastDate]) return
        seen[row.forecastDate] = true
        options.push({
          value: row.forecastDate,
          label: row.forecastDate + '（' + (WEEKDAY_TEXT[row.weekday] || '') + '）'
        })
      })
      return options
    },
    currentRows() {
      if (!this.selectedDate) return []
      return this.slotRows.filter(row => row.forecastDate === this.selectedDate).sort((a, b) => (a.slot || 0) - (b.slot || 0))
    },
    totalVolume() {
      return this.slotRows.reduce((sum, row) => sum + (row.volume || 0), 0)
    },
    selectedVolume() {
      return this.currentRows.reduce((sum, row) => sum + (row.volume || 0), 0)
    },
    peakSlot() {
      let peak = null
      this.currentRows.forEach(row => {
        if (!peak || (row.volume || 0) > (peak.volume || 0)) peak = row
      })
      return peak
    },
    peakSlots() {
      return this.currentRows
        .filter(row => (row.volume || 0) > 0)
        .sort((a, b) => (b.volume || 0) - (a.volume || 0))
        .slice(0, 5)
    },
    accuracyTag() {
      if (!this.accuracy || !this.accuracy.available || this.accuracy.accuracy == null) return 'info'
      if (this.accuracy.accuracy >= 0.85) return 'success'
      if (this.accuracy.accuracy >= 0.7) return 'warning'
      return 'danger'
    },
    accuracyTagText() {
      if (!this.accuracy || !this.accuracy.available || this.accuracy.accuracy == null) return '无数据'
      if (this.accuracy.accuracy >= 0.85) return '达标'
      if (this.accuracy.accuracy >= 0.7) return '接近阈值'
      return '待优化'
    }
  },
  created() {
    this.loadPreview()
    this.loadAccuracy()
  },
  mounted() {
    this.handleResize = () => {
      if (this.chart) this.chart.resize()
    }
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    if (this.chart) {
      this.chart.dispose()
      this.chart = null
    }
  },
  methods: {
    loadPreview() {
      this.loading = true
      previewSlotForecast(this.days).then(res => {
        const rows = (res && res.data) || []
        this.slotRows = Array.isArray(rows) ? rows : []
        this.loading = false
        const hasSelected = this.dateOptions.some(item => item.value === this.selectedDate)
        if (!hasSelected) {
          this.selectedDate = this.dateOptions.length ? this.dateOptions[0].value : null
        }
        this.$nextTick(() => this.renderChart())
      }).catch(() => {
        this.loading = false
      })
    },
    loadAccuracy() {
      this.accuracyLoading = true
      getSlotAccuracy(this.compareDays).then(res => {
        this.accuracy = (res && res.data) || null
        this.accuracyLoading = false
      }).catch(() => {
        this.accuracyLoading = false
      })
    },
    renderChart() {
      if (!this.$refs.slotChart) return
      if (!this.chart) {
        this.chart = echarts.init(this.$refs.slotChart, 'judicial')
      }
      this.chart.setOption(this.buildChartOption(this.currentRows), true)
    },
    buildChartOption(rows) {
      const times = rows.map(row => (row.beginTime || '').substring(0, 5))
      const volumes = rows.map(row => row.volume || 0)
      const agents = rows.map(row => row.agents || 0)
      return {
        tooltip: { trigger: 'axis' },
        legend: { data: ['预测进线量', '坐席需求'], top: 4 },
        grid: { left: 52, right: 28, top: 44, bottom: 44 },
        xAxis: { type: 'category', boundaryGap: false, data: times, name: '时段' },
        yAxis: [
          { type: 'value', name: '进线量(通)', minInterval: 1 },
          { type: 'value', name: '坐席', minInterval: 1 }
        ],
        series: [
          { name: '预测进线量', type: 'line', smooth: true, showSymbol: false, data: volumes, areaStyle: { opacity: 0.15 } },
          { name: '坐席需求', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, data: agents, lineStyle: { type: 'dashed' } }
        ]
      }
    },
    formatNumber(value) {
      if (value == null) return '-'
      return Number(value).toFixed(3)
    },
    formatPercent(value) {
      if (value == null) return '-'
      return (Number(value) * 100).toFixed(1) + '%'
    }
  }
}
</script>

<style scoped>
.forecast-slot { padding: 4px 0; }
.search-card { margin-bottom: 12px; }
.fs-stats { margin-bottom: 12px; }
.fs-stat-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 14px 16px;
  margin-bottom: 8px;
}
.fs-stat-label { color: #606266; font-size: 13px; }
.fs-stat-value { color: #1f3f7a; font-size: 24px; font-weight: 600; line-height: 34px; }
.fs-stat-value-sm { font-size: 18px; }
.fs-stat-unit { color: #909399; font-size: 12px; }
.fs-chart-card { margin-bottom: 12px; }
.fs-card-header { display: flex; align-items: center; justify-content: space-between; }
.fs-chart { width: 100%; height: 320px; }
.fs-empty { color: #909399; font-size: 13px; text-align: center; padding: 24px 0; }
.fs-table-card { margin-bottom: 12px; }
.fs-acc-tip { color: #909399; font-size: 12px; line-height: 20px; margin-bottom: 8px; }
.fs-acc-metrics { text-align: center; margin-bottom: 8px; }
.fs-acc-label { color: #909399; font-size: 12px; }
.fs-acc-value { color: #1f3f7a; font-size: 18px; font-weight: 600; }
</style>
