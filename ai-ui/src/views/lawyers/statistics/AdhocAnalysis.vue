<template>
  <div class="adhoc-wrap">
    <!-- 选择器：区间 + 粒度 + 维度 + 指标 -->
    <el-form :inline="true" size="small" class="adhoc-form">
      <el-form-item label="统计区间">
        <el-date-picker v-model="dateRange" type="datetimerange" value-format="yyyy-MM-dd HH:mm:ss"
                        range-separator="-" start-placeholder="开始时间" end-placeholder="结束时间"
                        style="width: 340px" :clearable="false" />
      </el-form-item>
      <el-form-item label="聚合粒度">
        <el-radio-group v-model="granularity" @change="onGranularityChange">
          <el-radio-button label="minute">分钟</el-radio-button>
          <el-radio-button label="hour">小时</el-radio-button>
          <el-radio-button label="day">天</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="分析维度">
        <el-select v-model="selectedDims" multiple collapse-tags clearable placeholder="不选=仅全局"
                   style="width: 240px" @change="onDimsChange">
          <el-option v-for="d in dimOptions" :key="d.key" :value="d.key"
                     :label="d.label + (d.crossDim ? '' : '（不支持组合）')">
            <span>{{ d.label }}</span>
            <span class="dim-hint">{{ d.crossDim ? '可跨维度组合' : '仅单维度' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="指标">
        <el-select v-model="selectedMetrics" multiple collapse-tags style="width: 320px"
                   placeholder="请选择指标">
          <el-option-group v-for="g in metricGroups" :key="g.group" :label="g.group">
            <el-option v-for="m in g.items" :key="m.key" :value="m.key" :label="m.label" />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" :loading="loading" @click="loadData">查询</el-button>
      </el-form-item>
      <el-form-item>
        <span class="adhoc-tip">{{ dimHint }}</span>
      </el-form-item>
    </el-form>

    <!-- 结果：动态列 -->
    <el-alert v-if="capped" type="warning" show-icon :closable="false" class="adhoc-alert"
              :title="`结果超过上限 ${maxRows} 行已截断，请缩小统计区间或减少维度`" />
    <el-table v-loading="loading" :data="rows" border stripe max-height="520" size="small">
      <el-table-column v-for="col in columns" :key="col.key" :label="col.label"
                       :align="isMetricColumn(col) ? 'right' : 'left'" min-width="120">
        <template slot-scope="scope">{{ renderCell(scope.row, col) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template slot-scope="scope">
          <el-button type="text" size="mini" @click="openDrill(scope.row)">下钻</el-button>
        </template>
      </el-table-column>
      <div slot="empty">
        <TableEmpty />
      </div>
    </el-table>
    <div v-if="rows.length" class="adhoc-meta">数据来源：{{ source === 'realtime' ? '实时聚合（跨维度组合）' : '分钟预聚合' }}</div>

    <!-- 下钻话单明细 -->
    <el-dialog :title="drillTitle" :visible.sync="drillOpen" width="960px" append-to-body top="6vh">
      <el-table v-loading="drillLoading" :data="drillRows" border stripe size="small" max-height="420">
        <el-table-column label="主叫号码" prop="callerNumber" width="130" />
        <el-table-column label="主叫姓名" prop="callerName" width="110" show-overflow-tooltip />
        <el-table-column label="呼叫时间" prop="callTime" width="160" />
        <el-table-column label="时长(秒)" prop="duration" width="90" align="right" />
        <el-table-column label="状态" width="80" align="center">
          <template slot-scope="scope">{{ statusText(scope.row.status) }}</template>
        </el-table-column>
        <el-table-column label="坐席" prop="agentName" width="110" />
        <el-table-column label="咨询分类" prop="categoryName" min-width="110" show-overflow-tooltip />
      </el-table>
      <pagination v-show="drillTotal > 0" :total="drillTotal" :page.sync="drillPageNum"
                  :limit.sync="drillPageSize" layout="total, prev, pager, next" @pagination="loadDrill" />
    </el-dialog>
  </div>
</template>

<script>
import { getAdhocCatalog, queryAdhoc, drillAdhoc } from '@/api/lawyers/report'

const STATUS_TEXT = { '1': '接通', '2': '转接', '3': '未接' }

export default {
  name: 'AdhocAnalysis',
  data() {
    return {
      loading: false,
      catalog: null,
      dateRange: [],
      granularity: 'day',
      selectedDims: [],
      selectedMetrics: [],
      columns: [],
      rows: [],
      labels: {},
      capped: false,
      maxRows: 0,
      source: '',
      // 下钻弹窗
      drillOpen: false,
      drillLoading: false,
      drillTitle: '',
      drillRows: [],
      drillTotal: 0,
      drillPageNum: 1,
      drillPageSize: 10,
      drillQuery: null
    }
  },
  computed: {
    dimOptions() {
      return this.catalog ? this.catalog.dimensions : []
    },
    // 指标选项随维度联动：无维度=全局指标；1 个维度=该维度可用指标；跨维度组合=呼叫类指标
    metricGroups() {
      if (!this.catalog) return []
      let keys = []
      if (this.selectedDims.length === 0) {
        keys = this.catalog.metrics.filter(m => m.group !== '维度专属').map(m => m.key)
      } else if (this.selectedDims.length === 1) {
        const dim = this.catalog.dimensions.find(d => d.key === this.selectedDims[0])
        keys = dim ? dim.metrics : []
      } else {
        keys = this.catalog.crossMetrics
      }
      const keySet = new Set(keys)
      const groups = []
      let lastGroup = null
      this.catalog.metrics.forEach(m => {
        if (!keySet.has(m.key)) return
        if (!lastGroup || lastGroup.group !== m.group) {
          lastGroup = { group: m.group, items: [] }
          groups.push(lastGroup)
        }
        lastGroup.items.push(m)
      })
      return groups
    },
    dimHint() {
      if (this.selectedDims.length >= 2) return '跨维度组合走实时查询，结果受上限保护'
      if (this.selectedDims.length === 1) {
        const dim = this.catalog && this.catalog.dimensions.find(d => d.key === this.selectedDims[0])
        return dim && !dim.crossDim ? '该维度仅支持单维度分析' : ''
      }
      return '不选维度=全局趋势分析'
    }
  },
  created() {
    const end = new Date()
    const begin = new Date(end.getTime() - 6 * 24 * 3600 * 1000)
    begin.setHours(0, 0, 0, 0)
    this.dateRange = [this.formatTs(begin), this.formatTs(end)]
    this.loadCatalog().then(() => {
      if (!this.selectedMetrics.length && this.metricGroups.length && this.metricGroups[0].items.length) {
        this.selectedMetrics = [this.metricGroups[0].items[0].key]
      }
      this.loadData()
    })
  },
  methods: {
    formatTs(d) {
      const p = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
    },
    loadCatalog() {
      return getAdhocCatalog().then(res => {
        this.catalog = res.data || { metrics: [], dimensions: [], crossMetrics: [] }
      })
    },
    onGranularityChange() {
      if (this.granularity === 'minute' && this.dateRange.length === 2) {
        const begin = new Date(this.dateRange[0].replace(' ', 'T'))
        const end = new Date(this.dateRange[1].replace(' ', 'T'))
        if (end - begin > 3 * 24 * 3600 * 1000) {
          this.$message.warning('分钟粒度最长支持 3 天，请缩小统计区间')
        }
      }
    },
    onDimsChange() {
      // 维度变化后剔除不可用指标，并补第一个可用项，保证查询始终有指标
      const avail = []
      this.metricGroups.forEach(g => g.items.forEach(m => avail.push(m.key)))
      this.selectedMetrics = this.selectedMetrics.filter(k => avail.includes(k))
      if (!this.selectedMetrics.length && avail.length) {
        this.selectedMetrics = [avail[0]]
      }
    },
    isMetricColumn(col) {
      return !['period', 'agent', 'category', 'lang', 'line', 'channel'].includes(col.key)
    },
    renderCell(row, col) {
      const v = row[col.key]
      if (v === null || v === undefined) return ''
      if (col.key === 'period') return v
      if (!this.isMetricColumn(col)) {
        const dimLabels = this.labels[col.key]
        if (dimLabels && dimLabels[v] !== undefined) return dimLabels[v]
        if (col.key === 'lang') return v
      }
      return v
    },
    validate() {
      if (!this.dateRange || this.dateRange.length !== 2) {
        this.$message.warning('请选择统计区间')
        return false
      }
      if (!this.selectedMetrics.length) {
        this.$message.warning('请至少选择一个指标')
        return false
      }
      if (this.granularity === 'minute') {
        const begin = new Date(this.dateRange[0].replace(' ', 'T'))
        const end = new Date(this.dateRange[1].replace(' ', 'T'))
        if (end - begin > 3 * 24 * 3600 * 1000) {
          this.$message.warning('分钟粒度最长支持 3 天，请缩小统计区间')
          return false
        }
      }
      return true
    },
    loadData() {
      if (!this.validate()) return
      this.loading = true
      queryAdhoc({
        beginTime: this.dateRange[0],
        endTime: this.dateRange[1],
        granularity: this.granularity,
        metrics: this.selectedMetrics.join(','),
        dimensions: this.selectedDims.join(',')
      }).then(res => {
        const d = res.data || {}
        this.columns = d.columns || []
        this.rows = d.rows || []
        this.labels = d.labels || {}
        this.capped = !!d.capped
        this.maxRows = d.maxRows || 0
        this.source = d.source || 'preagg'
      }).finally(() => {
        this.loading = false
      })
    },
    openDrill(row) {
      const drill = row._drill || {}
      this.drillQuery = {
        beginTime: drill.begin,
        endTime: drill.end,
        agentId: drill.agent,
        categoryId: drill.category,
        lang: drill.lang
      }
      this.drillTitle = `话单明细 ${drill.begin || ''} ~ ${drill.end || ''}`
      this.drillPageNum = 1
      this.drillOpen = true
      this.loadDrill()
    },
    loadDrill() {
      if (!this.drillQuery) return
      this.drillLoading = true
      drillAdhoc({ ...this.drillQuery, pageNum: this.drillPageNum, pageSize: this.drillPageSize }).then(res => {
        this.drillRows = res.rows || []
        this.drillTotal = res.total || 0
      }).finally(() => {
        this.drillLoading = false
      })
    },
    statusText(s) {
      return STATUS_TEXT[s] || s
    }
  }
}
</script>

<style lang="scss" scoped>
.adhoc-wrap {
  padding-top: 8px;
}
.adhoc-form {
  padding-bottom: 4px;
}
.adhoc-tip {
  font-size: 12px;
  color: #909399;
}
.dim-hint {
  float: right;
  font-size: 12px;
  color: #909399;
}
.adhoc-alert {
  margin-bottom: 8px;
}
.adhoc-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
