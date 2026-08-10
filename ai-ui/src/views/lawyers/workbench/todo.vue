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
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTodo, getTodo, addTodo, updateTodo, delTodo, processTodo, deferTodo, ignoreTodo } from '@/api/lawyers/workbench'

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
      }
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
        dueDate: null
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
          if (this.form.todoId != null) {
            updateTodo(this.form).then(() => {
              this.$modal.msgSuccess('修改成功')
              this.open = false
              this.getList()
            })
          } else {
            addTodo(this.form).then(() => {
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
      this.$modal.confirm('是否确认删除待办编号为"' + todoIds + '"的数据项？').then(function() {
        return delTodo(todoIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('删除成功')
      }).catch(() => {})
    },
    /** 处理按钮 */
    handleProcess(row) {
      this.$modal.confirm('是否确认处理该待办事项？').then(function() {
        return processTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('处理成功')
      }).catch(() => {})
    },
    /** 延后按钮 */
    handleDefer(row) {
      this.$modal.confirm('是否确认延后该待办事项？').then(function() {
        return deferTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('已延后')
      }).catch(() => {})
    },
    /** 忽略按钮 */
    handleIgnore(row) {
      this.$modal.confirm('是否确认忽略该待办事项？').then(function() {
        return ignoreTodo(row.todoId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess('已忽略')
      }).catch(() => {})
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
