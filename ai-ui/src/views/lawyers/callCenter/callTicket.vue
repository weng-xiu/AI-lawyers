<template>
  <div class="app-container cc-page">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ErrorRetry v-if="loadError" @retry="getList" />
    <ProTable
      v-else
      ref="proTable"
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="ticketList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      empty-text="暂无工单"
      empty-hint="调整筛选条件或时间范围后重试"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange"
    >
      <template #query-dateRange>
        <el-date-picker
          v-model="dateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </template>
      <template #toolbar-left>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:call:ticket:add']"
        >新增</el-button>
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['lawyers:call:ticket:edit']"
        >修改</el-button>
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:call:ticket:remove']"
        >删除</el-button>
      </template>
      <template #column-status="{ row }">
        <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
      </template>
      <template #column-priority="{ row }">
        <el-tag :type="getPriorityType(row.priority)">{{ getPriorityLabel(row.priority) }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button
          size="mini"
          type="text"
          icon="el-icon-view"
          @click="handleDetail(row)"
          v-hasPermi="['lawyers:call:ticket:query']"
        >详情</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-edit"
          @click="handleUpdate(row)"
          v-hasPermi="['lawyers:call:ticket:edit']"
        >修改</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(row)"
          v-hasPermi="['lawyers:call:ticket:remove']"
        >删除</el-button>
        <el-button
          v-if="row.status == '0'"
          size="mini"
          type="text"
          icon="el-icon-setting"
          @click="handleProcess(row)"
          v-hasPermi="['lawyers:call:ticket:process']"
        >开始处理</el-button>
        <el-button
          v-if="row.status == '1'"
          size="mini"
          type="text"
          icon="el-icon-check"
          @click="handleComplete(row)"
          v-hasPermi="['lawyers:call:ticket:complete']"
        >完成工单</el-button>
        <el-button
          v-if="row.status == '2'"
          size="mini"
          type="text"
          icon="el-icon-folder-add"
          @click="handleArchive(row)"
          v-hasPermi="['lawyers:call:ticket:archive']"
        >归档</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="700px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入工单标题" />
        </el-form-item>
        <el-form-item label="关联来电ID" prop="recordId">
          <el-input v-model="form.recordId" type="number" placeholder="请输入关联来电记录ID" />
        </el-form-item>
        <el-form-item label="工单内容" prop="content">
          <el-input v-model="form.content" type="textarea" placeholder="请输入工单内容" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio label="0">低</el-radio>
            <el-radio label="1">中</el-radio>
            <el-radio label="2">高</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="处理人" prop="assignUserId">
            <user-select v-model="form.assignUserId" placeholder="请选择处理人" @change="onAssignUserChange" />
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="工单详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="工单ID">{{ detailForm.ticketId }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ detailForm.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="工单标题">{{ detailForm.title }}</el-descriptions-item>
        <el-descriptions-item label="工单状态">
          <el-tag :type="getStatusType(detailForm.status)">{{ getStatusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="getPriorityType(detailForm.priority)">{{ getPriorityLabel(detailForm.priority) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联来电ID">{{ detailForm.recordId }}</el-descriptions-item>
        <el-descriptions-item label="工单内容">{{ detailForm.content }}</el-descriptions-item>
        <el-descriptions-item label="处理人ID">{{ detailForm.assignUserId }}</el-descriptions-item>
        <el-descriptions-item label="处理人姓名">{{ detailForm.assignUserName }}</el-descriptions-item>
        <el-descriptions-item label="处理结果">{{ detailForm.processResult }}</el-descriptions-item>
        <el-descriptions-item label="处理时间">{{ parseTime(detailForm.processTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <el-dialog title="处理工单" :visible.sync="processOpen" width="600px" append-to-body>
      <el-form ref="processForm" :model="processForm" :rules="processRules" label-width="80px">
        <el-form-item label="处理结果" prop="processResult">
          <el-input v-model="processForm.processResult" type="textarea" placeholder="请输入处理结果" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitProcess">确 定</el-button>
        <el-button @click="processOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTicket, getTicket, addTicket, updateTicket, delTicket, processTicket, completeTicket, archiveTicket } from "@/api/lawyers/callCenter"

export default {
  name: "CallTicket",
  data() {
    return {
      loading: true,
      loadError: false,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      ticketList: [],
      // V3：ProTable 列定义（status/priority/action 走插槽，时间列用 formatter）
      columns: [
        { prop: 'ticketId', label: '工单ID' },
        { prop: 'ticketNo', label: '工单号' },
        { prop: 'title', label: '工单标题' },
        { prop: 'status', label: '工单状态', width: 100, slot: true },
        { prop: 'priority', label: '优先级', width: 80, slot: true },
        { prop: 'assignUserName', label: '处理人' },
        { prop: 'recordId', label: '关联来电' },
        { prop: 'createTime', label: '创建时间', width: 180, formatter: (row) => this.parseTime(row.createTime) },
        { prop: 'action', label: '操作', slot: true, className: 'small-padding fixed-width' }
      ],
      title: "",
      open: false,
      detailOpen: false,
      processOpen: false,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        title: undefined,
        status: undefined,
        priority: undefined
      },
      form: {},
      detailForm: {},
      processForm: {
        ticketId: undefined,
        processResult: undefined
      },
      rules: {
        title: [
          { required: true, message: "工单标题不能为空", trigger: "blur" }
        ],
        content: [
          { required: true, message: "工单内容不能为空", trigger: "blur" }
        ]
      },
      processRules: {
        processResult: [
          { required: true, message: "处理结果不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段（日期范围走 #query-dateRange 插槽） */
    queryFormItems() {
      return [
        { prop: 'ticketNo', label: '工单号', placeholder: '请输入工单号' },
        { prop: 'title', label: '工单标题', placeholder: '请输入工单标题' },
        { prop: 'status', label: '工单状态', type: 'select', placeholder: '请选择状态', options: [
          { label: '待处理', value: '0' }, { label: '处理中', value: '1' },
          { label: '已完成', value: '2' }, { label: '已归档', value: '3' }
        ] },
        { prop: 'priority', label: '优先级', type: 'select', placeholder: '请选择优先级', options: [
          { label: '低', value: '0' }, { label: '中', value: '1' }, { label: '高', value: '2' }
        ] },
        { prop: 'dateRange', label: '创建时间', slot: true }
      ]
    }
  },
  methods: {
    onAssignUserChange(val, user) {
      this.$set(this.form, 'assignUserName', user ? (user.nickName || user.userName) : '')
    },
    getList() {
      this.loading = true
      this.loadError = false
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange)
      }
      listTicket(params).then(response => {
        this.ticketList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        // V2 三态：失败不再永久卡 loading，给出统一错误重试态
        this.loading = false
        this.loadError = true
      })
    },
    getStatusType(status) {
      const types = { '0': 'danger', '1': 'warning', '2': 'success', '3': 'info' }
      return types[status] || 'info'
    },
    getStatusLabel(status) {
      const labels = { '0': '待处理', '1': '处理中', '2': '已完成', '3': '已归档' }
      return labels[status] || '未知'
    },
    getPriorityType(priority) {
      const types = { '0': 'info', '1': 'warning', '2': 'danger' }
      return types[priority] || 'info'
    },
    getPriorityLabel(priority) {
      const labels = { '0': '低', '1': '中', '2': '高' }
      return labels[priority] || '未知'
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        ticketId: undefined,
        ticketNo: undefined,
        title: undefined,
        content: undefined,
        status: '0',
        priority: '0',
        recordId: undefined,
        assignUserId: undefined,
        assignUserName: undefined,
        processResult: undefined,
        remark: undefined
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里补插槽字段复位后重查
      this.dateRange = []
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "新增工单"
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.ticketId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    handleUpdate(row) {
      this.reset()
      const ticketId = row.ticketId || this.ids
      getTicket(ticketId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改工单"
      })
    },
    handleDetail(row) {
      const ticketId = row.ticketId
      getTicket(ticketId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    handleProcess(row) {
      processTicket({ ticketId: row.ticketId }).then(response => {
        this.$modal.msgSuccess("工单开始处理")
        this.getList()
      })
    },
    handleComplete(row) {
      this.processForm.ticketId = row.ticketId
      this.processForm.processResult = undefined
      this.processOpen = true
    },
    submitProcess() {
      this.$refs["processForm"].validate(valid => {
        if (valid) {
          completeTicket(this.processForm).then(response => {
            this.$modal.msgSuccess("工单已完成")
            this.processOpen = false
            this.getList()
          })
        }
      })
    },
    handleArchive(row) {
      archiveTicket({ ticketId: row.ticketId }).then(response => {
        this.$modal.msgSuccess("工单已归档")
        this.getList()
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.ticketId != undefined) {
            updateTicket(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addTicket(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      const ticketIds = row.ticketId || this.ids
      const count = row.ticketId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的工单',
        count: count,
        countNoun: '条工单'
      }).then(function() {
        return delTicket(ticketIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';
</style>
