<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="来电号码" prop="callerNumber">
        <el-input
          v-model="queryParams.callerNumber"
          placeholder="请输入来电号码"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="坐席名称" prop="agentName">
        <el-input
          v-model="queryParams.agentName"
          placeholder="请输入坐席名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来电状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="未接" value="0" />
          <el-option label="已接" value="1" />
          <el-option label="已转接" value="2" />
          <el-option label="已结束" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="咨询分类" prop="consultationCategory">
        <el-input
          v-model="queryParams.consultationCategory"
          placeholder="请输入咨询分类"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="来电时间">
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-button type="warning" icon="el-icon-data-line" size="mini" @click="handleStatistics">统计分析</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:call:record:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['lawyers:call:record:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:call:record:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="recordList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="记录ID" align="center" prop="recordId" />
      <el-table-column label="来电号码" align="center" prop="callerNumber" />
      <el-table-column label="来电姓名" align="center" prop="callerName" />
      <el-table-column label="坐席名称" align="center" prop="agentName" />
      <el-table-column label="咨询分类" align="center" prop="consultationCategory" />
      <el-table-column label="咨询内容" align="center" prop="consultationContent" :show-overflow-tooltip="true" />
      <el-table-column label="来电状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通话时长(秒)" align="center" prop="callDuration" />
      <el-table-column label="来电时间" align="center" prop="callTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.callTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:call:record:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:call:record:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:call:record:remove']"
          >删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-tickets"
            @click="handleCreateTicket(scope.row)"
            v-hasPermi="['lawyers:call:ticket:add']"
          >生成工单</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog :title="title" :visible.sync="open" width="700px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="来电号码" prop="callerNumber">
          <el-input v-model="form.callerNumber" placeholder="请输入来电号码" />
        </el-form-item>
        <el-form-item label="来电姓名" prop="callerName">
          <el-input v-model="form.callerName" placeholder="请输入来电姓名" />
        </el-form-item>
        <el-form-item label="坐席ID" prop="agentId">
          <el-input v-model="form.agentId" placeholder="请输入坐席ID" />
        </el-form-item>
        <el-form-item label="咨询分类" prop="consultationCategory">
          <el-input v-model="form.consultationCategory" placeholder="请输入咨询分类" />
        </el-form-item>
        <el-form-item label="咨询内容" prop="consultationContent">
          <el-input v-model="form.consultationContent" type="textarea" placeholder="请输入咨询内容" />
        </el-form-item>
        <el-form-item label="来电状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">未接</el-radio>
            <el-radio label="1">已接</el-radio>
            <el-radio label="2">已转接</el-radio>
            <el-radio label="3">已结束</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="通话时长" prop="callDuration">
          <el-input v-model="form.callDuration" type="number" placeholder="请输入通话时长(秒)" />
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

    <el-dialog title="来电详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="记录ID">{{ detailForm.recordId }}</el-descriptions-item>
        <el-descriptions-item label="来电号码">{{ detailForm.callerNumber }}</el-descriptions-item>
        <el-descriptions-item label="来电姓名">{{ detailForm.callerName }}</el-descriptions-item>
        <el-descriptions-item label="坐席ID">{{ detailForm.agentId }}</el-descriptions-item>
        <el-descriptions-item label="坐席名称">{{ detailForm.agentName }}</el-descriptions-item>
        <el-descriptions-item label="咨询分类">{{ detailForm.consultationCategory }}</el-descriptions-item>
        <el-descriptions-item label="咨询内容">{{ detailForm.consultationContent }}</el-descriptions-item>
        <el-descriptions-item label="来电状态">
          <el-tag :type="getStatusType(detailForm.status)">{{ getStatusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="通话时长">{{ detailForm.callDuration }}秒</el-descriptions-item>
        <el-descriptions-item label="来电时间">{{ parseTime(detailForm.callTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <el-dialog title="话务统计分析" :visible.sync="statisticsOpen" width="900px" append-to-body>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>总来电数</span></div>
            <div class="text item"><h2>{{ statistics.totalCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>今日来电</span></div>
            <div class="text item"><h2>{{ statistics.todayCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>本周来电</span></div>
            <div class="text item"><h2>{{ statistics.weekCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>本月来电</span></div>
            <div class="text item"><h2>{{ statistics.monthCount }}</h2></div>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>已接来电</span></div>
            <div class="text item"><h2>{{ statistics.answeredCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>未接来电</span></div>
            <div class="text item"><h2>{{ statistics.unansweredCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>转接来电</span></div>
            <div class="text item"><h2>{{ statistics.transferredCount }}</h2></div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>平均通话时长</span></div>
            <div class="text item"><h2>{{ statistics.avgDuration }}秒</h2></div>
          </el-card>
        </el-col>
      </el-row>
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>按分类统计</span></div>
            <div class="text item"><div ref="categoryChart" style="height: 300px;"></div></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix"><span>按日期统计（最近7天）</span></div>
            <div class="text item"><div ref="dateChart" style="height: 300px;"></div></div>
          </el-card>
        </el-col>
      </el-row>
      <div slot="footer" class="dialog-footer">
        <el-button @click="statisticsOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listRecord, getRecord, addRecord, updateRecord, delRecord, getCallStatistics, getCallStatisticsByCategory, getCallStatisticsByDate } from "@/api/lawyers/callCenter"
import * as echarts from 'echarts'

export default {
  name: "CallRecord",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      recordList: [],
      title: "",
      open: false,
      detailOpen: false,
      statisticsOpen: false,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        agentName: undefined,
        status: undefined,
        consultationCategory: undefined
      },
      form: {},
      detailForm: {},
      statistics: {
        totalCount: 0,
        todayCount: 0,
        weekCount: 0,
        monthCount: 0,
        answeredCount: 0,
        unansweredCount: 0,
        transferredCount: 0,
        avgDuration: 0
      },
      rules: {
        callerNumber: [
          { required: true, message: "来电号码不能为空", trigger: "blur" }
        ],
        agentId: [
          { required: true, message: "坐席ID不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange)
      }
      listRecord(params).then(response => {
        this.recordList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    getStatusType(status) {
      const types = { '0': 'danger', '1': 'success', '2': 'warning', '3': 'info' }
      return types[status] || 'info'
    },
    getStatusLabel(status) {
      const labels = { '0': '未接', '1': '已接', '2': '已转接', '3': '已结束' }
      return labels[status] || '未知'
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        recordId: undefined,
        callerNumber: undefined,
        callerName: undefined,
        agentId: undefined,
        consultationCategory: undefined,
        consultationContent: undefined,
        status: '0',
        callDuration: 0,
        remark: undefined
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "新增来电记录"
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.recordId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    handleUpdate(row) {
      this.reset()
      const recordId = row.recordId || this.ids
      getRecord(recordId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改来电记录"
      })
    },
    handleDetail(row) {
      const recordId = row.recordId
      getRecord(recordId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    handleCreateTicket(row) {
      this.$modal.msgSuccess("请在工单管理模块创建工单")
    },
    handleStatistics() {
      this.statisticsOpen = true
      this.getStatisticsData()
    },
    getStatisticsData() {
      getCallStatistics().then(response => {
        this.statistics = response.data
      })
      getCallStatisticsByCategory().then(response => {
        this.drawCategoryChart(response.data)
      })
      getCallStatisticsByDate(7).then(response => {
        this.drawDateChart(response.data)
      })
    },
    drawCategoryChart(data) {
      this.$nextTick(() => {
        const chartDom = this.$refs.categoryChart
        if (!chartDom) return
        const myChart = echarts.init(chartDom)
        const option = {
          tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
          legend: { orient: 'vertical', left: 10, data: data.map(item => item.category_name) },
          series: [{
            name: '咨询分类',
            type: 'pie',
            radius: ['50%', '70%'],
            data: data.map(item => ({ value: item.count, name: item.category_name }))
          }]
        }
        myChart.setOption(option)
        window.addEventListener('resize', () => myChart.resize())
      })
    },
    drawDateChart(data) {
      this.$nextTick(() => {
        const chartDom = this.$refs.dateChart
        if (!chartDom) return
        const myChart = echarts.init(chartDom)
        const option = {
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: data.map(item => item.date) },
          yAxis: { type: 'value' },
          series: [{
            name: '来电数',
            data: data.map(item => item.count),
            type: 'line',
            smooth: true,
            areaStyle: {}
          }]
        }
        myChart.setOption(option)
        window.addEventListener('resize', () => myChart.resize())
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.recordId != undefined) {
            updateRecord(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addRecord(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      const recordIds = row.recordId || this.ids
      this.$modal.confirm('是否确认删除来电记录编号为"' + recordIds + '"的数据项？').then(function() {
        return delRecord(recordIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>
