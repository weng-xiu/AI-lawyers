<template>
  <div class="app-container cb-page">
    <!-- 4 统计卡 -->
    <el-row :gutter="16" class="cb-stats">
      <el-col :xs="12" :sm="6">
        <div class="cb-stat-card cb-stat-total">
          <div class="cb-stat-icon"><i class="el-icon-document"></i></div>
          <div class="cb-stat-body">
            <div class="cb-stat-label">回访总数</div>
            <div class="cb-stat-value">{{ stats.totalCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cb-stat-card cb-stat-done">
          <div class="cb-stat-icon"><i class="el-icon-success"></i></div>
          <div class="cb-stat-body">
            <div class="cb-stat-label">已完成回访</div>
            <div class="cb-stat-value">{{ stats.completedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cb-stat-card cb-stat-pending">
          <div class="cb-stat-icon"><i class="el-icon-time"></i></div>
          <div class="cb-stat-body">
            <div class="cb-stat-label">待回访</div>
            <div class="cb-stat-value">{{ stats.pendingCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cb-stat-card cb-stat-score">
          <div class="cb-stat-icon"><i class="el-icon-star-off"></i></div>
          <div class="cb-stat-body">
            <div class="cb-stat-label">平均满意度</div>
            <div class="cb-stat-value">{{ stats.avgScore || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 满意度趋势图 -->
    <el-card shadow="never" class="cb-trend-card">
      <div slot="header" class="cb-card-header">
        <span>满意度趋势（最近7天）</span>
        <el-tag size="mini" type="info">折线图</el-tag>
      </div>
      <div ref="trendChart" class="cb-trend-chart"></div>
    </el-card>

    <!-- 查询 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="来电号码" prop="callerNumber">
        <el-input v-model="queryParams.callerNumber" placeholder="请输入来电号码" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="来电人" prop="callerName">
        <el-input v-model="queryParams.callerName" placeholder="请输入来电人" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="满意度" prop="satisfaction">
        <el-select v-model="queryParams.satisfaction" placeholder="全部" clearable style="width: 140px">
          <el-option label="非常满意" value="1" />
          <el-option label="满意" value="2" />
          <el-option label="一般" value="3" />
          <el-option label="不满意" value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待回访" value="0" />
          <el-option label="已完成" value="1" />
          <el-option label="无法联系" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="回访时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['lawyers:callback:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:callback:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['lawyers:callback:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="callbackList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="来电号码" prop="callerNumber" width="130" />
      <el-table-column label="来电人" prop="callerName" width="100" />
      <el-table-column label="回访时间" prop="visitTime" width="160">
        <template slot-scope="scope">{{ scope.row.visitTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="回访人" prop="visitBy" width="100">
        <template slot-scope="scope">{{ scope.row.visitBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="满意度" prop="satisfaction" width="100" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="satTagType(scope.row.satisfaction)">{{ satText(scope.row.satisfaction) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回访意见" prop="visitOpinion" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['lawyers:callback:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['lawyers:callback:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['lawyers:callback:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="来电号码" prop="callerNumber">
              <el-input v-model="form.callerNumber" placeholder="请输入来电号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来电人" prop="callerName">
              <el-input v-model="form.callerName" placeholder="请输入来电人姓名" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="回访时间" prop="visitTime">
              <el-date-picker v-model="form.visitTime" style="width: 100%" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" placeholder="选择回访时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="回访人" prop="visitBy">
              <el-input v-model="form.visitBy" placeholder="请输入回访人" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="满意度" prop="satisfaction">
              <el-select v-model="form.satisfaction" placeholder="请选择满意度" style="width: 100%">
                <el-option label="非常满意" value="1" />
                <el-option label="满意" value="2" />
                <el-option label="一般" value="3" />
                <el-option label="不满意" value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option label="待回访" value="0" />
                <el-option label="已完成" value="1" />
                <el-option label="无法联系" value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="回访意见" prop="visitOpinion">
          <el-input v-model="form.visitOpinion" type="textarea" placeholder="请输入回访意见" />
        </el-form-item>
        <el-form-item label="回访结果" prop="visitResult">
          <el-input v-model="form.visitResult" type="textarea" placeholder="请输入回访结果" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog title="回访详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="来电号码">{{ detailForm.callerNumber }}</el-descriptions-item>
        <el-descriptions-item label="来电人">{{ detailForm.callerName }}</el-descriptions-item>
        <el-descriptions-item label="回访时间">{{ detailForm.visitTime }}</el-descriptions-item>
        <el-descriptions-item label="回访人">{{ detailForm.visitBy }}</el-descriptions-item>
        <el-descriptions-item label="满意度">
          <el-tag size="mini" :type="satTagType(detailForm.satisfaction)">{{ satText(detailForm.satisfaction) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="mini" :type="statusTagType(detailForm.status)">{{ statusText(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="回访意见">{{ detailForm.visitOpinion }}</el-descriptions-item>
        <el-descriptions-item label="回访结果">{{ detailForm.visitResult }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listCallback, getCallback, addCallback, updateCallback, delCallback, getCallbackStats, getSatisfactionTrend } from '@/api/lawyers/callback'
import * as echarts from 'echarts'

export default {
  name: 'Callback',
  data() {
    return {
      loading: false,
      showSearch: true,
      stats: {},
      callbackList: [],
      total: 0,
      open: false,
      detailOpen: false,
      title: '',
      ids: [],
      multiple: true,
      dateRange: [],
      trendChart: null,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        callerName: undefined,
        satisfaction: undefined,
        status: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        callerNumber: [{ required: true, message: '来电号码不能为空', trigger: 'blur' }]
      }
    }
  },
  mounted() {
    window.addEventListener('resize', this.resizeChart)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeChart)
    if (this.trendChart) this.trendChart.dispose()
  },
  created() {
    this.loadStats()
    this.getList()
    this.loadTrend()
  },
  methods: {
    loadStats() {
      getCallbackStats().then(res => { this.stats = res.data || {} })
    },
    getList() {
      this.loading = true
      listCallback(this.addDateRange(this.queryParams, this.dateRange)).then(res => {
        this.callbackList = res.rows
        this.total = res.total
      }).finally(() => { this.loading = false })
    },
    loadTrend() {
      getSatisfactionTrend(7).then(res => { this.drawTrend(res.data || []) })
    },
    drawTrend(data) {
      this.$nextTick(() => {
        if (!this.$refs.trendChart) return
        if (!this.trendChart) this.trendChart = echarts.init(this.$refs.trendChart)
        const option = {
          tooltip: { trigger: 'axis' },
          legend: { data: ['非常满意', '满意', '一般', '不满意'] },
          grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
          xAxis: { type: 'category', boundaryGap: false, data: data.map(i => i.date) },
          yAxis: { type: 'value' },
          series: [
            { name: '非常满意', type: 'line', smooth: true, data: data.map(i => i.verySatisfied || 0), itemStyle: { color: '#67c23a' } },
            { name: '满意', type: 'line', smooth: true, data: data.map(i => i.satisfied || 0), itemStyle: { color: '#409eff' } },
            { name: '一般', type: 'line', smooth: true, data: data.map(i => i.normal || 0), itemStyle: { color: '#e6a23c' } },
            { name: '不满意', type: 'line', smooth: true, data: data.map(i => i.unsatisfied || 0), itemStyle: { color: '#f56c6c' } }
          ]
        }
        this.trendChart.setOption(option, true)
      })
    },
    resizeChart() {
      if (this.trendChart) this.trendChart.resize()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm('queryForm')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(i => i.callbackId)
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增客户回访'
    },
    handleUpdate(row) {
      this.reset()
      getCallback(row.callbackId).then(res => {
        this.form = res.data
        this.open = true
        this.title = '修改客户回访'
      })
    },
    handleDetail(row) {
      getCallback(row.callbackId).then(res => {
        this.detailForm = res.data
        this.detailOpen = true
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.callbackId) {
          updateCallback(this.form).then(() => {
            this.$message.success('修改成功')
            this.open = false
            this.getList()
            this.loadStats()
            this.loadTrend()
          })
        } else {
          addCallback(this.form).then(() => {
            this.$message.success('新增成功')
            this.open = false
            this.getList()
            this.loadStats()
            this.loadTrend()
          })
        }
      })
    },
    handleDelete(row) {
      const ids = row.callbackId ? [row.callbackId] : this.ids
      this.$confirm('是否确认删除选中的回访记录？', '删除确认', { type: 'warning' }).then(() => {
        return delCallback(ids)
      }).then(() => {
        this.$message.success('删除成功')
        this.loadStats()
        this.loadTrend()
        this.getList()
      }).catch(() => {})
    },
    handleExport() {
      this.download('/lawyers/callback/export', { ...this.queryParams }, `客户回访_${new Date().getTime()}.xlsx`)
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        callbackId: undefined,
        ledgerId: undefined,
        callerNumber: undefined,
        callerName: undefined,
        visitTime: undefined,
        visitBy: undefined,
        satisfaction: undefined,
        visitOpinion: undefined,
        visitResult: undefined,
        status: '0',
        remark: undefined
      }
      this.resetForm('form')
    },
    satText(s) {
      const map = { '1': '非常满意', '2': '满意', '3': '一般', '4': '不满意' }
      return map[s] || '未评价'
    },
    satTagType(s) {
      const map = { '1': 'success', '2': 'primary', '3': 'warning', '4': 'danger' }
      return map[s] || 'info'
    },
    statusText(s) {
      const map = { '0': '待回访', '1': '已完成', '2': '无法联系' }
      return map[s] || '-'
    },
    statusTagType(s) {
      const map = { '0': 'info', '1': 'success', '2': 'danger' }
      return map[s] || 'info'
    }
  }
}
</script>

<style lang="scss" scoped>
.cb-page { padding: 16px; }
.cb-stats { margin-bottom: 16px; }
.cb-stat-card {
  display: flex; align-items: center;
  padding: 18px 20px; border-radius: 8px; color: #fff;
  min-height: 84px; box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  .cb-stat-icon { font-size: 32px; margin-right: 14px; opacity: 0.9; }
  .cb-stat-label { font-size: 13px; opacity: 0.9; }
  .cb-stat-value { font-size: 26px; font-weight: 600; line-height: 1.2; margin-top: 4px; }
}
.cb-stat-total { background: linear-gradient(135deg, #409eff, #66b1ff); }
.cb-stat-done { background: linear-gradient(135deg, #67c23a, #95d475); }
.cb-stat-pending { background: linear-gradient(135deg, #e6a23c, #f3b55c); }
.cb-stat-score { background: linear-gradient(135deg, #f56c6c, #f89898); }
.cb-trend-card { margin-bottom: 16px; }
.cb-trend-chart { height: 280px; }
.cb-card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
