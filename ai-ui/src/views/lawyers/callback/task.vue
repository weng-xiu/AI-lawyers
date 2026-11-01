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

    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="taskList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      label-width="80px"
      empty-text="暂无回访任务"
      empty-hint="新增回访任务或由工单自动生成后，将按计划时间提醒受理人回访"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd" v-hasPermi="['lawyers:returnVisitTask:add']">新增</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:returnVisitTask:remove']">删除</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['lawyers:returnVisitTask:export']">导出</el-button>
      </template>
      <!-- 受理人选择器与计划时间区间为复杂控件，走自定义查询插槽 -->
      <template #query-assigneeId>
        <user-select v-model="queryParams.assigneeId" placeholder="全部受理人" clearable style="width: 160px" />
      </template>
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-priority="{ row }">
        <el-tag size="mini" :type="priorityTagType(row.priority)">{{ priorityText(row.priority) }}</el-tag>
      </template>
      <template #column-planTime="{ row }">{{ row.planTime || '-' }}</template>
      <template #column-actualTime="{ row }">{{ row.actualTime || '-' }}</template>
      <template #column-assignee="{ row }">{{ row.assignee || '-' }}</template>
      <template #column-status="{ row }">
        <el-tag size="mini" :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)" v-hasPermi="['lawyers:returnVisitTask:query']">详情</el-button>
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)" v-hasPermi="['lawyers:returnVisitTask:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)" v-hasPermi="['lawyers:returnVisitTask:remove']">删除</el-button>
      </template>
    </ProTable>

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
      // V3：ProTable 列定义（优先级/时间/受理人/状态/操作走插槽）
      columns: [
        { prop: 'taskNo', label: '任务编号', width: 160 },
        { prop: 'callerNumber', label: '来电号码', width: 130 },
        { prop: 'callerName', label: '来电人', width: 100 },
        { prop: 'priority', label: '优先级', width: 80, align: 'center', slot: true },
        { prop: 'planTime', label: '计划回访时间', width: 160, slot: true },
        { prop: 'actualTime', label: '实际回访时间', width: 160, slot: true },
        { prop: 'assignee', label: '受理人', width: 100, slot: true },
        { prop: 'status', label: '状态', width: 90, align: 'center', slot: true },
        { prop: 'visitResult', label: '回访结果', minWidth: 200, showOverflowTooltip: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    /** V3：ProTable 查询字段（受理人走 #query-assigneeId 插槽，计划时间区间走 #query-dateRange 插槽） */
    queryFormItems() {
      return [
        { prop: 'taskNo', label: '任务编号', placeholder: '请输入任务编号', width: 180 },
        { prop: 'callerNumber', label: '来电号码', placeholder: '请输入来电号码', width: 180 },
        { prop: 'callerName', label: '来电人', placeholder: '请输入来电人', width: 160 },
        { prop: 'assigneeId', label: '受理人', slot: true },
        {
          prop: 'priority',
          label: '优先级',
          type: 'select',
          placeholder: '全部',
          width: 120,
          options: [
            { label: '高', value: '1' },
            { label: '中', value: '2' },
            { label: '低', value: '3' }
          ]
        },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '全部',
          width: 120,
          options: [
            { label: '待回访', value: '0' },
            { label: '已完成', value: '1' },
            { label: '已逾期', value: '2' }
          ]
        },
        { prop: 'dateRange', label: '计划时间', slot: true }
      ]
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值与时间区间并重查
      this.dateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        taskNo: undefined,
        callerNumber: undefined,
        callerName: undefined,
        assignee: undefined,
        assigneeId: undefined,
        priority: undefined,
        status: undefined
      }
      this.getList()
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
      this.$modal.dangerConfirm({
        content: '将删除选中的回访任务',
        count: ids.length,
        countNoun: '个任务'
      }).then(() => {
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
