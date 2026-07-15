<template>
  <div class="app-container cc-page">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="工单号" prop="ticketNo">
        <el-input
          v-model="queryParams.ticketNo"
          placeholder="请输入工单号"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工单标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入工单标题"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工单状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="待处理" value="0" />
          <el-option label="处理中" value="1" />
          <el-option label="已完成" value="2" />
          <el-option label="已归档" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="请选择优先级" clearable>
          <el-option label="低" value="0" />
          <el-option label="中" value="1" />
          <el-option label="高" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建时间">
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
          v-hasPermi="['lawyers:call:ticket:add']"
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
          v-hasPermi="['lawyers:call:ticket:edit']"
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
          v-hasPermi="['lawyers:call:ticket:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="ticketList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工单ID" align="center" prop="ticketId" />
      <el-table-column label="工单号" align="center" prop="ticketNo" />
      <el-table-column label="工单标题" align="center" prop="title" />
      <el-table-column label="工单状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="80">
        <template slot-scope="scope">
          <el-tag :type="getPriorityType(scope.row.priority)">{{ getPriorityLabel(scope.row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" align="center" prop="assignUserName" />
      <el-table-column label="关联来电" align="center" prop="recordId" />
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
            v-hasPermi="['lawyers:call:ticket:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:call:ticket:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:call:ticket:remove']"
          >删除</el-button>
          <el-button
            v-if="scope.row.status == '0'"
            size="mini"
            type="text"
            icon="el-icon-setting"
            @click="handleProcess(scope.row)"
            v-hasPermi="['lawyers:call:ticket:process']"
          >开始处理</el-button>
          <el-button
            v-if="scope.row.status == '1'"
            size="mini"
            type="text"
            icon="el-icon-check"
            @click="handleComplete(scope.row)"
            v-hasPermi="['lawyers:call:ticket:complete']"
          >完成工单</el-button>
          <el-button
            v-if="scope.row.status == '2'"
            size="mini"
            type="text"
            icon="el-icon-folder-add"
            @click="handleArchive(scope.row)"
            v-hasPermi="['lawyers:call:ticket:archive']"
          >归档</el-button>
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
        <el-form-item label="处理人ID" prop="assignUserId">
          <el-input v-model="form.assignUserId" type="number" placeholder="请输入处理人用户ID" />
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
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      ticketList: [],
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
  methods: {
    getList() {
      this.loading = true
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange)
      }
      listTicket(params).then(response => {
        this.ticketList = response.rows
        this.total = response.total
        this.loading = false
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
      this.dateRange = []
      this.resetForm("queryForm")
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
      this.$modal.confirm('是否确认删除工单编号为"' + ticketIds + '"的数据项？').then(function() {
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
