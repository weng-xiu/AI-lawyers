<template>
  <div class="mc-page" v-loading="loading">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="mc-filter">
      <el-form :inline="true" size="small" @submit.native.prevent>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="yyyy-MM-dd"
            :picker-options="pickerOptions"
          />
        </el-form-item>
        <el-form-item label="调用类型">
          <el-select v-model="logQuery.kind" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="o in kindOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="场景">
          <el-select v-model="logQuery.scene" placeholder="全部" clearable filterable style="width: 140px">
            <el-option v-for="o in sceneOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="logQuery.result" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
            <el-option label="舱壁拒绝" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button>
          <el-button
            type="warning"
            plain
            icon="el-icon-download"
            @click="handleExport"
            v-hasPermi="['lawyers:modelCost:export']"
          >导出明细</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 指标卡 -->
    <el-row :gutter="12" class="mc-cards">
      <el-col :xs="12" :sm="8" :md="4" v-for="c in statCards" :key="c.label">
        <div class="mc-card" :style="{ borderTopColor: c.color }">
          <div class="mc-card-num" :style="{ color: c.color }">{{ c.value }}</div>
          <div class="mc-card-label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势 + 模型分布 -->
    <el-row :gutter="12">
      <el-col :xs="24" :md="15">
        <el-card shadow="never" class="mc-panel">
          <div slot="header"><span>调用量 / 费用趋势（按日）</span></div>
          <div ref="trendChart" class="mc-chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="9">
        <el-card shadow="never" class="mc-panel">
          <div slot="header"><span>模型调用量分布</span></div>
          <div ref="modelChart" class="mc-chart"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 场景分布 -->
    <el-card shadow="never" class="mc-panel">
      <div slot="header"><span>调用类型 / 业务场景分布</span></div>
      <el-table :data="sceneRows" size="small" border>
        <el-table-column label="调用类型" prop="kind" width="100" align="center">
          <template slot-scope="s">{{ kindLabel(s.row.kind) }}</template>
        </el-table-column>
        <el-table-column label="业务场景" prop="scene" min-width="120">
          <template slot-scope="s">{{ sceneLabel(s.row.scene) }}</template>
        </el-table-column>
        <el-table-column label="调用量" prop="totalCalls" width="90" align="right" />
        <el-table-column label="成功" prop="successCalls" width="80" align="right" />
        <el-table-column label="失败" prop="failCalls" width="80" align="right" />
        <el-table-column label="拒绝" prop="rejectCalls" width="80" align="right" />
        <el-table-column label="成功率" width="100" align="right">
          <template slot-scope="s">{{ rate(s.row.successCalls, s.row.totalCalls) }}%</template>
        </el-table-column>
        <el-table-column label="总Token" prop="totalTokens" width="110" align="right" />
        <el-table-column label="估算费用(元)" prop="costAmount" width="120" align="right">
          <template slot-scope="s">{{ money(s.row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="平均耗时(ms)" prop="avgElapsedMs" width="110" align="right" />
      </el-table>
    </el-card>

    <!-- 模型明细分布 -->
    <el-card shadow="never" class="mc-panel">
      <div slot="header"><span>模型维度明细</span></div>
      <el-table :data="modelRows" size="small" border>
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" prop="modelType" width="100" align="center" />
        <el-table-column label="调用量" prop="totalCalls" width="90" align="right" />
        <el-table-column label="成功" prop="successCalls" width="80" align="right" />
        <el-table-column label="失败" prop="failCalls" width="80" align="right" />
        <el-table-column label="拒绝" prop="rejectCalls" width="80" align="right" />
        <el-table-column label="成功率" width="100" align="right">
          <template slot-scope="s">{{ rate(s.row.successCalls, s.row.totalCalls) }}%</template>
        </el-table-column>
        <el-table-column label="总Token" prop="totalTokens" width="120" align="right" />
        <el-table-column label="估算费用(元)" prop="costAmount" width="130" align="right">
          <template slot-scope="s">{{ money(s.row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="平均耗时(ms)" prop="avgElapsedMs" width="120" align="right" />
      </el-table>
    </el-card>

    <!-- 调用明细 -->
    <el-card shadow="never" class="mc-panel">
      <div slot="header"><span>大模型调用明细</span></div>
      <el-table :data="logList" size="small" border>
        <el-table-column label="调用时间" prop="callTime" width="160" />
        <el-table-column label="类型" prop="kind" width="70" align="center">
          <template slot-scope="s">{{ kindLabel(s.row.kind) }}</template>
        </el-table-column>
        <el-table-column label="场景" prop="scene" width="90" align="center">
          <template slot-scope="s">{{ sceneLabel(s.row.scene) }}</template>
        </el-table-column>
        <el-table-column label="模型配置" prop="configName" min-width="130" show-overflow-tooltip />
        <el-table-column label="模型名称" prop="modelName" min-width="150" show-overflow-tooltip />
        <el-table-column label="输入Tok" prop="promptTokens" width="85" align="right" />
        <el-table-column label="输出Tok" prop="completionTokens" width="85" align="right" />
        <el-table-column label="费用(元)" prop="costAmount" width="100" align="right">
          <template slot-scope="s">{{ money(s.row.costAmount) }}</template>
        </el-table-column>
        <el-table-column label="耗时(ms)" prop="elapsedMs" width="90" align="right" />
        <el-table-column label="尝试" prop="attempts" width="60" align="center" />
        <el-table-column label="结果" prop="result" width="90" align="center">
          <template slot-scope="s">
            <el-tag size="mini" :type="resultTag(s.row.result)">{{ resultLabel(s.row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" prop="failReason" min-width="180" show-overflow-tooltip />
      </el-table>
      <pagination
        v-show="logTotal > 0"
        :total="logTotal"
        :page.sync="logQuery.pageNum"
        :limit.sync="logQuery.pageSize"
        @pagination="loadLogList"
      />
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import {
  getModelCostOverview,
  getModelCostTrend,
  getModelCostByModel,
  getModelCostByScene,
  listModelCallLog
} from '@/api/lawyers/modelCost'

const PRIMARY = '#3a5cb8'

export default {
  name: 'ModelCost',
  data() {
    return {
      loading: false,
      dateRange: [],
      pickerOptions: {
        shortcuts: [
          { text: '最近7天', onClick(picker) {
            const end = new Date(); const start = new Date()
            start.setTime(start.getTime() - 6 * 86400000)
            picker.$emit('pick', [start, end])
          } },
          { text: '最近14天', onClick(picker) {
            const end = new Date(); const start = new Date()
            start.setTime(start.getTime() - 13 * 86400000)
            picker.$emit('pick', [start, end])
          } },
          { text: '最近30天', onClick(picker) {
            const end = new Date(); const start = new Date()
            start.setTime(start.getTime() - 29 * 86400000)
            picker.$emit('pick', [start, end])
          } }
        ],
        disabledDate(time) {
          return time.getTime() > Date.now()
        }
      },
      kindOptions: [
        { value: 'chat', label: '对话' },
        { value: 'embed', label: '向量' },
        { value: 'rerank', label: '精排' }
      ],
      sceneOptions: [
        { value: 'intention', label: '意图识别' },
        { value: 'emotion', label: '情绪分析' },
        { value: 'extract', label: '信息抽取' },
        { value: 'quality', label: '智能质检' },
        { value: 'summary', label: '通话小结' },
        { value: 'agent', label: '智能体' },
        { value: 'consultation', label: '法律咨询' },
        { value: 'rag', label: 'RAG检索' },
        { value: 'rag_index', label: '向量入库' },
        { value: 'test', label: '连接测试' },
        { value: 'other', label: '其他' }
      ],
      overview: {},
      trendRows: [],
      modelRows: [],
      sceneRows: [],
      logList: [],
      logTotal: 0,
      logQuery: {
        pageNum: 1,
        pageSize: 10,
        kind: null,
        scene: null,
        modelName: null,
        result: null
      },
      trendChart: null,
      modelChart: null
    }
  },
  computed: {
    statCards() {
      const o = this.overview || {}
      return [
        { label: '调用总量', value: this.num(o.totalCalls), color: PRIMARY },
        { label: '成功率(%)', value: this.num(o.successRate, 2), color: '#2B8C6E' },
        { label: '失败 / 拒绝', value: `${this.num(o.failCalls)} / ${this.num(o.rejectCalls)}`, color: '#C63D4A' },
        { label: '总Token', value: this.num(o.totalTokens), color: '#3B73B3' },
        { label: '估算费用(元)', value: this.money(o.costAmount), color: '#E8923A' },
        { label: '平均耗时(ms)', value: this.num(o.avgElapsedMs), color: '#9B6EAA' },
        { label: '重试次数', value: this.num(o.retryCalls), color: '#8C8C8C' },
        { label: '单位通话成本(元/通)', value: this.money(o.costPerCall, 6), color: '#1A3C6E' }
      ]
    }
  },
  created() {
    this.dateRange = [this.fmtDay(-6), this.fmtDay(0)]
    this.loadBoard()
    this.loadLogList()
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
    if (this.trendChart) this.trendChart.dispose()
    if (this.modelChart) this.modelChart.dispose()
  },
  methods: {
    day(offset) {
      const d = new Date()
      d.setTime(d.getTime() + offset * 3600 * 1000 * 24)
      return d
    },
    fmtDay(offset) {
      const d = this.day(offset)
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${d.getFullYear()}-${m}-${day}`
    },
    rangeParams() {
      const p = {}
      if (this.dateRange && this.dateRange.length === 2) {
        p.beginTime = this.dateRange[0]
        p.endTime = this.dateRange[1]
      }
      return p
    },
    handleQuery() {
      this.logQuery.pageNum = 1
      this.loadBoard()
      this.loadLogList()
    },
    resetQuery() {
      this.dateRange = [this.fmtDay(-6), this.fmtDay(0)]
      this.logQuery.kind = null
      this.logQuery.scene = null
      this.logQuery.result = null
      this.logQuery.modelName = null
      this.logQuery.pageNum = 1
      this.loadBoard()
      this.loadLogList()
    },
    loadBoard() {
      this.loading = true
      const range = this.rangeParams()
      Promise.all([
        getModelCostOverview(range),
        getModelCostTrend(range),
        getModelCostByModel(range),
        getModelCostByScene(range)
      ]).then(([ov, tr, mm, sc]) => {
        this.overview = ov.data || {}
        this.trendRows = tr.data || []
        this.modelRows = mm.data || []
        this.sceneRows = sc.data || []
        this.$nextTick(() => {
          this.renderTrend()
          this.renderModelPie()
        })
      }).finally(() => {
        this.loading = false
      })
    },
    loadLogList() {
      const params = {
        pageNum: this.logQuery.pageNum,
        pageSize: this.logQuery.pageSize,
        kind: this.logQuery.kind,
        scene: this.logQuery.scene,
        result: this.logQuery.result,
        'params[beginTime]': this.dateRange && this.dateRange.length === 2 ? this.dateRange[0] + ' 00:00:00' : undefined,
        'params[endTime]': this.dateRange && this.dateRange.length === 2 ? this.dateRange[1] + ' 23:59:59' : undefined
      }
      listModelCallLog(params).then(res => {
        this.logList = res.rows || []
        this.logTotal = res.total || 0
      })
    },
    handleExport() {
      const params = {
        kind: this.logQuery.kind,
        scene: this.logQuery.scene,
        result: this.logQuery.result,
        'params[beginTime]': this.dateRange && this.dateRange.length === 2 ? this.dateRange[0] + ' 00:00:00' : undefined,
        'params[endTime]': this.dateRange && this.dateRange.length === 2 ? this.dateRange[1] + ' 23:59:59' : undefined
      }
      this.download('/lawyers/modelCost/export', params,
        `大模型调用明细_${new Date().getTime()}.xlsx`)
    },
    renderTrend() {
      if (!this.$refs.trendChart) return
      if (!this.trendChart) this.trendChart = echarts.init(this.$refs.trendChart)
      const rows = this.trendRows
      this.trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['调用量', '成功', '费用(元)'] },
        grid: { left: 50, right: 60, top: 40, bottom: 40 },
        xAxis: { type: 'category', data: rows.map(r => r.period), axisLabel: { rotate: 35, fontSize: 10 } },
        yAxis: [
          { type: 'value', name: '调用量', minInterval: 1 },
          { type: 'value', name: '费用(元)', position: 'right' }
        ],
        series: [
          { name: '调用量', type: 'bar', data: rows.map(r => this.num(r.totalCalls)), itemStyle: { color: PRIMARY }, barMaxWidth: 22 },
          { name: '成功', type: 'bar', data: rows.map(r => this.num(r.successCalls)), itemStyle: { color: '#7fa2e0' }, barMaxWidth: 22 },
          { name: '费用(元)', type: 'line', smooth: true, yAxisIndex: 1,
            data: rows.map(r => this.num(r.costAmount, 4)), itemStyle: { color: '#E8923A' } }
        ]
      }, true)
    },
    renderModelPie() {
      if (!this.$refs.modelChart) return
      if (!this.modelChart) this.modelChart = echarts.init(this.$refs.modelChart)
      const data = this.modelRows.map(r => ({
        name: r.modelName || '未知模型',
        value: this.num(r.totalCalls)
      }))
      this.modelChart.setOption({
        tooltip: {
          trigger: 'item',
          formatter: (p) => {
            const row = this.modelRows[p.dataIndex] || {}
            return `${p.name}<br/>调用量：${p.value}（${p.percent}%）`
              + `<br/>费用(元)：${this.money(row.costAmount)}`
              + `<br/>平均耗时(ms)：${this.num(row.avgElapsedMs)}`
          }
        },
        legend: { type: 'scroll', bottom: 0, fontSize: 10 },
        color: ['#3a5cb8', '#54A68B', '#E8923A', '#9B6EAA', '#C63D4A', '#3B73B3', '#8C8C8C', '#7fa2e0'],
        series: [{
          type: 'pie',
          radius: ['38%', '62%'],
          center: ['50%', '45%'],
          label: { fontSize: 10 },
          data
        }]
      }, true)
    },
    handleResize() {
      if (this.trendChart) this.trendChart.resize()
      if (this.modelChart) this.modelChart.resize()
    },
    num(v, fixed) {
      const n = Number(v)
      if (isNaN(n)) return fixed != null ? Number(0).toFixed(fixed) : 0
      return fixed != null ? n.toFixed(fixed) : n
    },
    money(v, fixed) {
      const n = Number(v)
      return (isNaN(n) ? 0 : n).toFixed(fixed != null ? fixed : 4)
    },
    rate(success, total) {
      const s = Number(success) || 0
      const t = Number(total) || 0
      return t <= 0 ? '0.00' : ((s * 100) / t).toFixed(2)
    },
    kindLabel(k) {
      const o = this.kindOptions.find(i => i.value === k)
      return o ? o.label : (k || '')
    },
    sceneLabel(s) {
      const o = this.sceneOptions.find(i => i.value === s)
      return o ? o.label : (s || '')
    },
    resultLabel(r) {
      if (r === '1') return '成功'
      if (r === '0') return '失败'
      if (r === '2') return '舱壁拒绝'
      return r || ''
    },
    resultTag(r) {
      if (r === '1') return 'success'
      if (r === '0') return 'danger'
      if (r === '2') return 'warning'
      return 'info'
    }
  }
}
</script>

<style lang="scss" scoped>
.mc-page {
  padding: 12px;
}
.mc-filter {
  margin-bottom: 12px;
::v-deep .el-card__body {
    padding-bottom: 2px;
  }
}
.mc-cards {
  margin-bottom: 12px;
}
.mc-card {
  background: #fff;
  border-radius: 4px;
  border-top: 3px solid #3a5cb8;
  padding: 16px 12px;
  margin-bottom: 12px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.mc-card-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
}
.mc-card-label {
  color: #666;
  font-size: 12px;
  margin-top: 4px;
}
.mc-panel {
  margin-bottom: 12px;
}
.mc-chart {
  width: 100%;
  height: 320px;
}
</style>
