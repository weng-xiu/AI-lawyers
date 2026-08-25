<template>
  <div class="app-container rvt-page">
    <!-- 4 统计卡 -->
    <el-row :gutter="16" class="rvt-stats">
      <el-col :xs="12" :sm="6">
        <div class="rvt-stat-card rvt-stat-total">
          <div class="rvt-stat-icon"><i class="el-icon-document"></i></div>
          <div class="rvt-stat-body">
            <div class="rvt-stat-label">任务总数</div>
            <div class="rvt-stat-value">{{ stats.totalCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="rvt-stat-card rvt-stat-pending">
          <div class="rvt-stat-icon"><i class="el-icon-time"></i></div>
          <div class="rvt-stat-body">
            <div class="rvt-stat-label">待回访</div>
            <div class="rvt-stat-value">{{ stats.pendingCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="rvt-stat-card rvt-stat-done">
          <div class="rvt-stat-icon"><i class="el-icon-success"></i></div>
          <div class="rvt-stat-body">
            <div class="rvt-stat-label">已完成</div>
            <div class="rvt-stat-value">{{ stats.completedCount || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="rvt-stat-card rvt-stat-overdue">
          <div class="rvt-stat-icon"><i class="el-icon-warning"></i></div>
          <div class="rvt-stat-body">
            <div class="rvt-stat-label">已逾期</div>
            <div class="rvt-stat-value">{{ stats.overdueCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 查询 -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="任务编号" prop="taskNo">
        <el-input v-model="queryParams.taskNo" placeholder="请输入任务编号" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="来电号码" prop="callerNumber">
        <el-input v-model="queryParams.callerNumber" placeholder="请输入来电号码" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="来电人" prop="callerName">
        <el-input v-model="queryParams.callerName" placeholder="请输入来电人" clearable style="width: 160px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="受理人" prop="assigneeId">
        <user-select v-model="queryParams.assigneeId" placeholder="全部受理人" clearable style="width: 160px" />
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="全部" clearable style="width: 120px">
          <el-option label="高" value="1" />
          <el-option label="中" value="2" />
          <el-option label="低" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="待回访" value="0" />
          <el-option label="已完成" value="1" />
          <el-option label="已逾期" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="计划时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['lawyers:returnVisitTask:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:returnVisitTask:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['lawyers:returnVisitTask:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="任务编号" prop="taskNo" width="160" />
      <el-table-column label="来电号码" prop="callerNumber" width="130" />
      <el-table-column label="来电人" prop="callerName" width="100" />
      <el-table-column label="优先级" prop="priority" width="80" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="priorityTagType(scope.row.priority)">{{ priorityText(scope.row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="计划回访时间" prop="planTime" width="160">
        <template slot-scope="scope">{{ scope.row.planTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="实际回访时间" prop="actualTime" width="160">
        <template slot-scope="scope">{{ scope.row.actualTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="受理人" prop="assignee" width="100">
        <template slot-scope="scope">{{ scope.row.assignee || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template slot-scope="scope">
          <el-tag size="mini" :type="statusTagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="回访结果" prop="visitResult" show-overflow-tooltip />
      <el-table-column label="操作" align="center" width="200" fixed="right">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['lawyers:returnVisitTask:query']">详情</el-button>
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)" v-hasPermi="['lawyers:returnVisitTask:edit']">修改</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['lawyers:returnVisitTask:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />

    <!-- 新增/修改 -->
    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="任务编号" prop="taskNo">
              <el-input v-model="form.taskNo" placeholder="自动生成可手填" />
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="受理人" prop="assigneeId">
            <user-select v-model="form.assigneeId" placeholder="请选择受理人" @change="onAssigneeChange" />
          </el-form-item></el-col>
        </el-row>
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
            <el-form-item label="优先级" prop="priority">
              <el-select v-model="form.priority" placeholder="请选择优先级" style="width: 100%">
                <el-option label="高" value="1" />
                <el-option label="中" value="2" />
                <el-option label="低" value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option label="待回访" value="0" />
                <el-option label="已完成" value="1" />
                <el-option label="已逾期" value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="计划回访时间" prop="planTime">
              <el-date-picker v-model="form.planTime" style="width: 100%" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" placeholder="选择计划回访时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实际回访时间" prop="actualTime">
              <el-date-picker v-model="form.actualTime" style="width: 100%" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" placeholder="选择实际回访时间" />
            </el-form-item>
          </el-col>
        </el-row>
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
    <el-dialog title="任务详情" :visible.sync="detailOpen" width="640px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务编号">{{ detailForm.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="受理人">{{ detailForm.assignee }}</el-descriptions-item>
        <el-descriptions-item label="来电号码">{{ detailForm.callerNumber }}</el-descriptions-item>
        <el-descriptions-item label="来电人">{{ detailForm.callerName }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag size="mini" :type="priorityTagType(detailForm.priority)">{{ priorityText(detailForm.priority) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="mini" :type="statusTagType(detailForm.status)">{{ statusText(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="计划回访时间">{{ detailForm.planTime }}</el-descriptions-item>
        <el-descriptions-item label="实际回访时间">{{ detailForm.actualTime }}</el-descriptions-item>
        <el-descriptions-item label="回访结果" :span="2">{{ detailForm.visitResult }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listReturnVisitTask, getReturnVisitTask, addReturnVisitTask, updateReturnVisitTask, delReturnVisitTask, getReturnVisitTaskStats } from '@/api/lawyers/returnVisitTask'

export default {
  name: 'ReturnVisitTask',
  data() {
    return {
      loading: false,
      showSearch: true,
      stats: {},
      taskList: [],
      total: 0,
      open: false,
      detailOpen: false,
      title: '',
      ids: [],
      multiple: true,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        taskNo: undefined,
        callerNumber: undefined,
        callerName: undefined,
        assignee: undefined,
        assigneeId: undefined,
        priority: undefined,
        status: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        callerNumber: [{ required: true, message: '来电号码不能为空', trigger: 'blur' }],
        planTime: [{ required: true, message: '计划回访时间不能为空', trigger: 'change' }]
      }
    }
  },
  created() {
    this.loadStats()
    this.getList()
  },
  methods: {
    loadStats() {
      getReturnVisitTaskStats().then(res => { this.stats = res.data || {} })
    },
    getList() {
      this.loading = true
      listReturnVisitTask(this.addDateRange(this.queryParams, this.dateRange)).then(res => {
        this.taskList = res.rows
        this.total = res.total
      }).finally(() => { this.loading = false })
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
      this.ids = selection.map(i => i.taskId)
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增回访任务'
    },
    handleUpdate(row) {
      this.reset()
      getReturnVisitTask(row.taskId).then(res => {
        this.form = res.data
        this.open = true
        this.title = '修改回访任务'
      })
    },
    handleDetail(row) {
      getReturnVisitTask(row.taskId).then(res => {
        this.detailForm = res.data
        this.detailOpen = true
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.taskId) {
          updateReturnVisitTask(this.form).then(() => {
            this.$message.success('修改成功')
            this.open = false
            this.getList()
            this.loadStats()
          })
        } else {
          addReturnVisitTask(this.form).then(() => {
            this.$message.success('新增成功')
            this.open = false
            this.getList()
            this.loadStats()
          })
        }
      })
    },
    handleDelete(row) {
      const ids = row.taskId ? [row.taskId] : this.ids
      this.$confirm('是否确认删除选中的回访任务？', '删除确认', { type: 'warning' }).then(() => {
        return delReturnVisitTask(ids)
      }).then(() => {
        this.$message.success('删除成功')
        this.loadStats()
        this.getList()
      }).catch(() => {})
    },
    handleExport() {
      this.download('/lawyers/returnVisitTask/export', { ...this.queryParams }, `回访任务_${new Date().getTime()}.xlsx`)
    },
    cancel() {
      this.open = false
      this.reset()
    },
    onAssigneeChange(val, user) {
      this.$set(this.form, 'assignee', user ? (user.nickName || user.userName) : '')
    },
    reset() {
      this.form = {
        taskId: undefined,
        taskNo: undefined,
        callerNumber: undefined,
        callerName: undefined,
        planTime: undefined,
        actualTime: undefined,
        assignee: undefined,
        assigneeId: undefined,
        status: '0',
        priority: '2',
        visitResult: undefined,
        remark: undefined
      }
      this.resetForm('form')
    },
    priorityText(p) {
      const map = { '1': '高', '2': '中', '3': '低' }
      return map[p] || '-'
    },
    priorityTagType(p) {
      const map = { '1': 'danger', '2': 'warning', '3': 'info' }
      return map[p] || 'info'
    },
    statusText(s) {
      const map = { '0': '待回访', '1': '已完成', '2': '已逾期' }
      return map[s] || '-'
    },
    statusTagType(s) {
      const map = { '0': 'warning', '1': 'success', '2': 'danger' }
      return map[s] || 'info'
    }
  }
}
</script>

<style scoped>
.rvt-stats { margin-bottom: 16px; }
.rvt-stat-card {
  display: flex;
  align-items: center;
  padding: 18px 20px;
  border-radius: 8px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.rvt-stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: rgba(255,255,255,0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 14px;
  font-size: 22px;
}
.rvt-stat-label { font-size: 13px; opacity: 0.9; }
.rvt-stat-value { font-size: 26px; font-weight: 600; line-height: 1.2; }
.rvt-stat-total { background: linear-gradient(135deg, #1A3C6E, #255A99); }
.rvt-stat-pending { background: linear-gradient(135deg, #E8923A, #EFC89A); }
.rvt-stat-done { background: linear-gradient(135deg, #2B8C6E, #54A68B); }
.rvt-stat-overdue { background: linear-gradient(135deg, #C63D4A, #D98089); }
</style>
