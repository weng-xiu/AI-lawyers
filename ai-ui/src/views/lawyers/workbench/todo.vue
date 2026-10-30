<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="待办标题" prop="todoTitle">
        <el-input
          v-model="queryParams.todoTitle"
          placeholder="请输入待办标题"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="优先级" prop="priority">
        <el-select v-model="queryParams.priority" placeholder="请选择优先级" clearable>
          <el-option
            v-for="item in priorityOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
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
          v-hasPermi="['lawyers:workbench:todo:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:workbench:todo:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="todoList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="待办标题" prop="todoTitle" :show-overflow-tooltip="true" />
      <el-table-column label="内容" prop="todoContent" :show-overflow-tooltip="true" />
      <el-table-column label="关联工单" align="center" width="120">
        <template slot-scope="scope">
          <el-button
            v-if="scope.row.bizType === 'ticket' && scope.row.bizId"
            type="text"
            size="mini"
            icon="el-icon-tickets"
            @click="handleOpenTicket(scope.row)"
          >工单 #{{ scope.row.bizId }}</el-button>
          <span v-else class="todo-no-link">—</span>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="90">
        <template slot-scope="scope">
          <el-tag :type="priorityTagType(scope.row.priority)">{{ priorityLabel(scope.row.priority) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template slot-scope="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="到期日期" align="center" prop="dueDate" width="120" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="280">
        <template slot-scope="scope">
          <el-button
            v-if="scope.row.status === 0"
            size="mini"
            type="text"
            icon="el-icon-check"
            @click="handleProcess(scope.row)"
          >处理</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-time"
            @click="handleDefer(scope.row)"
          >延后</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-remove-outline"
            @click="handleIgnore(scope.row)"
          >忽略</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:workbench:todo:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:workbench:todo:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改待办事项对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="todoTitle">
          <el-input v-model="form.todoTitle" placeholder="请输入待办标题" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio :label="1">紧急</el-radio>
            <el-radio :label="2">普通</el-radio>
            <el-radio :label="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="到期日期" prop="dueDate">
          <el-date-picker
            v-model="form.dueDate"
            type="date"
            value-format="yyyy-MM-dd"
            placeholder="请选择到期日期"
          />
        </el-form-item>
        <el-form-item label="内容" prop="todoContent">
          <el-input v-model="form.todoContent" type="textarea" :rows="4" placeholder="请输入待办内容" />
        </el-form-item>
        <el-form-item label="关联工单" prop="bizId">
          <el-input-number
            v-model="form.bizId"
            :min="1"
            :controls="false"
            placeholder="可选：填写工单ID"
            style="width: 200px"
          />
          <span class="todo-form-tip">填写后可在待办卡片内直接受理/转办/办结工单</span>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- F7：关联工单内嵌处理抽屉 -->
    <el-drawer
      :title="ticketDrawer.todoTitle ? '工单处理 · ' + ticketDrawer.todoTitle : '工单处理'"
      :visible.sync="ticketDrawer.open"
      size="520px"
      append-to-body
    >
      <div v-loading="ticketDrawer.loading" class="ticket-drawer">
        <template v-if="ticketDrawer.ticket">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="工单号">{{ ticketDrawer.ticket.ticketNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工单标题">{{ ticketDrawer.ticket.title || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="ticketStatusType(ticketDrawer.ticket.status)">
                {{ ticketStatusLabel(ticketDrawer.ticket.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="优先级">{{ ticketPriorityLabel(ticketDrawer.ticket.priority) }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ ticketDrawer.ticket.assignUserName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="SLA截止">{{ ticketDrawer.ticket.dueTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="处理内容">{{ ticketDrawer.ticket.processContent || '-' }}</el-descriptions-item>
            <el-descriptions-item label="工单内容">{{ ticketDrawer.ticket.content || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="ticket-actions">
            <el-button
              v-if="ticketDrawer.ticket.status === '0'"
              type="primary"
              size="mini"
              icon="el-icon-check"
              v-hasPermi="['lawyers:call:ticket:process']"
              @click="openAccept"
            >受理</el-button>
            <el-button
              v-if="ticketDrawer.ticket.status !== '3'"
              type="warning"
              size="mini"
              icon="el-icon-share"
              v-hasPermi="['lawyers:ticketTransfer:add']"
              @click="openTransfer"
            >转办</el-button>
            <el-button
              v-if="ticketDrawer.ticket.status === '0' || ticketDrawer.ticket.status === '1'"
              type="success"
              size="mini"
              icon="el-icon-circle-check"
              v-hasPermi="['lawyers:call:ticket:complete']"
              @click="handleCompleteTicket"
            >办结</el-button>
            <el-button size="mini" @click="ticketDrawer.open = false">关闭</el-button>
          </div>
        </template>
        <el-empty v-else-if="!ticketDrawer.loading" description="该待办未关联工单，或工单已删除" />
      </div>
    </el-drawer>

    <!-- F7：受理工单（填写处理内容） -->
    <el-dialog title="受理工单" :visible.sync="acceptOpen" width="480px" append-to-body>
      <el-form ref="acceptForm" :model="acceptForm" :rules="acceptRules" label-width="90px">
        <el-form-item label="处理内容" prop="processContent">
          <el-input
            v-model="acceptForm.processContent"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="请输入受理说明/下一步办理计划"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="acceptSubmitting" @click="submitAccept">确认受理</el-button>
        <el-button @click="acceptOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- F7：转办工单（选择协同机构） -->
    <el-dialog title="转办工单" :visible.sync="transferOpen" width="480px" append-to-body>
      <el-form ref="transferForm" :model="transferForm" :rules="transferRules" label-width="90px">
        <el-form-item label="协同机构" prop="orgId">
          <el-select v-model="transferForm.orgId" filterable placeholder="请选择转办机构" style="width: 100%">
            <el-option
              v-for="org in orgOptions"
              :key="org.orgId"
              :label="org.orgName"
              :value="org.orgId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="transferForm.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="最小必要说明，请勿填写敏感信息"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">确认转办</el-button>
        <el-button @click="transferOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTodo, getTodo, getTodoTicket, addTodo, updateTodo, delTodo, processTodo, deferTodo, ignoreTodo } from '@/api/lawyers/workbench'
import { processTicket, completeTicket } from '@/api/lawyers/callCenter'
import { createTransfer } from '@/api/lawyers/ticketTransfer'
import { listExternalOrg } from '@/api/lawyers/externalOrg'

export default {
  name: 'Todo',
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 待办表格数据
      todoList: [],
      // 弹出层标题
      title: '',
      // 是否显示弹出层
      open: false,
      // 状态选项
      statusOptions: [
        { value: 0, label: '待办' },
        { value: 1, label: '已完成' },
        { value: 2, label: '延后' },
        { value: 3, label: '忽略' }
      ],
      // 优先级选项
      priorityOptions: [
        { value: 1, label: '紧急' },
        { value: 2, label: '普通' },
        { value: 3, label: '低' }
      ],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        todoTitle: undefined,
        status: undefined,
        priority: undefined
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        todoTitle: [
          { required: true, message: '待办标题不能为空', trigger: 'blur' }
        ]
      },
      // F7：关联工单内嵌处理
      ticketDrawer: {
        open: false,
        loading: false,
        todoId: null,
        todoTitle: '',
        todoStatus: '',
        ticket: null
      },
      acceptOpen: false,
      acceptSubmitting: false,
      acceptForm: {
        processContent: ''
      },
      acceptRules: {
        processContent: [
          { required: true, message: '请填写处理内容', trigger: 'blur' },
          { min: 2, message: '处理内容至少 2 个字', trigger: 'blur' }
        ]
      },
      transferOpen: false,
      transferSubmitting: false,
      transferForm: {
        orgId: undefined,
        remark: ''
      },
      transferRules: {
        orgId: [
          { required: true, message: '请选择转办机构', trigger: 'change' }
        ]
      },
      orgOptions: []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询待办列表 */
    getList() {
      this.loading = true
      listTodo(this.queryParams).then(response => {
        this.todoList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        todoId: null,
        todoTitle: null,
        todoContent: null,
        status: 0,
        priority: 2,
        dueDate: null,
        bizType: null,
        bizId: null
      }
      this.resetForm('form')
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm('queryForm')
      this.handleQuery()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.todoId)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增待办事项'
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const todoId = row.todoId || this.ids
      getTodo(todoId).then(response => {
        this.form = response.data
        this.open = true
        this.title = '修改待办事项'
      })
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs['form'].validate(valid => {
        if (valid) {
          const payload = Object.assign({}, this.form)
          // F7：填写工单ID 即建立待办-工单关联（当前仅支持工单业务）
          if (payload.bizId) {
            payload.bizType = 'ticket'
          }
          if (payload.todoId != null) {
            updateTodo(payload).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addTodo(payload).then(() => {
              this.$modal.msgSuccess('新增成功')
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const todoIds = row.todoId || this.ids
      const count = row.todoId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的待办事项',
        count: count,
        countNoun: '条待办'
      }).then(function() {
        return delTodo(todoIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    /** 处理按钮 */
    handleProcess(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认处理该待办事项吗？',
        tip: '处理后该待办将关闭，可在工单详情继续跟进'
      }).then(function() {
        return processTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('处理成功')
      }).catch(() => {})
    },
    /** 延后按钮 */
    handleDefer(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认延后该待办事项吗？',
        tip: '延后将顺延提醒时间，不会关闭待办'
      }).then(function() {
        return deferTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('已延后')
      }).catch(() => {})
    },
    /** 忽略按钮 */
    handleIgnore(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认忽略该待办事项吗？',
        tip: '忽略后该待办不再提醒，且不会自动恢复'
      }).then(function() {
        return ignoreTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('已忽略')
      }).catch(() => {})
    },
    // ==================== F7：关联工单内嵌处理 ====================
    /** 打开关联工单抽屉 */
    handleOpenTicket(row) {
      this.ticketDrawer.open = true
      this.ticketDrawer.todoId = row.todoId
      this.ticketDrawer.todoTitle = row.todoTitle
      this.ticketDrawer.todoStatus = row.status
      this.ticketDrawer.ticket = null
      this.loadLinkedTicket()
    },
    /** 加载待办关联工单 */
    loadLinkedTicket() {
      if (!this.ticketDrawer.todoId) return
      this.ticketDrawer.loading = true
      getTodoTicket(this.ticketDrawer.todoId).then(res => {
        this.ticketDrawer.ticket = res.data || null
        this.ticketDrawer.loading = false
      }).catch(() => {
        this.ticketDrawer.ticket = null
        this.ticketDrawer.loading = false
      })
    },
    /** 打开受理弹窗（默认处理人为当前登录用户） */
    openAccept() {
      this.acceptForm.processContent = ''
      this.acceptOpen = true
      this.$nextTick(() => {
        if (this.$refs.acceptForm) this.$refs.acceptForm.clearValidate()
      })
    },
    /** 提交受理（0→1，经工单状态机校验） */
    submitAccept() {
      this.$refs.acceptForm.validate(valid => {
        if (!valid) return
        this.acceptSubmitting = true
        const ticketId = this.ticketDrawer.ticket.ticketId
        processTicket({
          ticketId: ticketId,
          processContent: this.acceptForm.processContent,
          assignUserId: this.$store.state.user.id,
          assignUserName: this.$store.getters.nickName || this.$store.getters.name
        }).then(() => {
          this.acceptSubmitting = false
          this.acceptOpen = false
          this.$modal.msgSuccess('工单已受理')
          this.loadLinkedTicket()
          this.getList()
        }).catch(() => {
          this.acceptSubmitting = false
        })
      })
    },
    /** 打开转办弹窗 */
    openTransfer() {
      this.transferForm.orgId = undefined
      this.transferForm.remark = ''
      this.transferOpen = true
      this.loadOrgOptions()
      this.$nextTick(() => {
        if (this.$refs.transferForm) this.$refs.transferForm.clearValidate()
      })
    },
    /** 加载可转办的协同机构（一次加载后复用） */
    loadOrgOptions() {
      if (this.orgOptions.length > 0) return
      listExternalOrg({ pageNum: 1, pageSize: 200 }).then(res => {
        this.orgOptions = res.rows || []
      }).catch(() => {})
    },
    /** 提交跨域转办 */
    submitTransfer() {
      this.$refs.transferForm.validate(valid => {
        if (!valid) return
        this.transferSubmitting = true
        createTransfer({
          ticketId: this.ticketDrawer.ticket.ticketId,
          orgId: this.transferForm.orgId,
          remark: this.transferForm.remark
        }).then(() => {
          this.transferSubmitting = false
          this.transferOpen = false
          this.$modal.msgSuccess('转办已发起')
          this.loadLinkedTicket()
          this.getList()
        }).catch(() => {
          this.transferSubmitting = false
        })
      })
    },
    /** 办结工单，并同步关闭关联待办 */
    handleCompleteTicket() {
      const ticketId = this.ticketDrawer.ticket.ticketId
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认办结该工单吗？',
        tip: '办结后工单进入已完成，可继续归档；关联待办将同步关闭'
      }).then(() => {
        return completeTicket({ ticketId: ticketId })
      }).then(() => {
        this.$modal.msgSuccess('工单已办结')
        if (String(this.ticketDrawer.todoStatus) === '0') {
          return processTodo(this.ticketDrawer.todoId).catch(() => {})
        }
      }).then(() => {
        this.loadLinkedTicket()
        this.getList()
      }).catch(() => {})
    },
    // 工单状态标签类型
    ticketStatusType(status) {
      const map = { 0: 'warning', 1: '', 2: 'success', 3: 'info' }
      if (status === undefined || status === null) return 'info'
      return map[String(status)] || 'info'
    },
    // 工单状态文本
    ticketStatusLabel(status) {
      const map = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '已归档' }
      return map[String(status)] || '未知'
    },
    // 工单优先级文本
    ticketPriorityLabel(priority) {
      const map = { 1: '紧急', 2: '普通', 3: '低' }
      return map[String(priority)] || (priority || '-')
    },
    // 优先级标签类型
    priorityTagType(priority) {
      const map = { 1: 'danger', 2: 'warning', 3: 'info' }
      return map[priority] || 'info'
    },
    // 优先级文本
    priorityLabel(priority) {
      const item = this.priorityOptions.find(i => i.value === priority)
      return item ? item.label : '未知'
    },
    // 状态标签类型
    statusTagType(status) {
      const map = { 0: 'warning', 1: 'success', 2: 'info', 3: '' }
      return map[status] || ''
    },
    // 状态文本
    statusLabel(status) {
      const item = this.statusOptions.find(i => i.value === status)
      return item ? item.label : '未知'
    }
  }
}
</script>

<style scoped>
.todo-no-link {
  color: #c0c4cc;
}

.todo-form-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

.ticket-drawer {
  padding: 0 20px 20px;
  min-height: 200px;
}

.ticket-actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
