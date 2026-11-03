<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable；从任务跳转时隐藏任务ID筛选 -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="calleeList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      has-selection
      label-width="80px"
      empty-text="暂无外呼号码"
      empty-hint="通过「导入」批量粘贴号码，或新增单个号码"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:outbound:callee:add']"
        >新增</el-button>
        <el-button
          type="success"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="handleImport"
          v-hasPermi="['lawyers:outbound:callee:add']"
        >导入</el-button>
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:outbound:callee:export']"
        >导出</el-button>
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          @click="handleClear"
          v-hasPermi="['lawyers:outbound:callee:remove']"
          :disabled="!taskId"
        >清空号码</el-button>
      </template>
      <template #column-gender="{ row }">
        <span v-if="row.gender === '0'">男</span>
        <span v-else-if="row.gender === '1'">女</span>
        <span v-else>未知</span>
      </template>
      <template #column-callStatus="{ row }">
        <el-tag v-if="row.callStatus === '0'" type="info">待呼叫</el-tag>
        <el-tag v-else-if="row.callStatus === '1'" type="success">已接听</el-tag>
        <el-tag v-else-if="row.callStatus === '2'" type="warning">未接听</el-tag>
        <el-tag v-else-if="row.callStatus === '3'" type="danger">呼叫失败</el-tag>
        <el-tag v-else type="info">已取消</el-tag>
      </template>
      <template #column-lastCallTime="{ row }">
        <span>{{ parseTime(row.lastCallTime) }}</span>
      </template>
      <template #column-action="{ row }">
        <el-button
          size="mini"
          type="text"
          icon="el-icon-edit"
          @click="handleUpdate(row)"
          v-hasPermi="['lawyers:outbound:callee:edit']"
        >修改</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(row)"
          v-hasPermi="['lawyers:outbound:callee:remove']"
        >删除</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="calleeName">
          <el-input v-model="form.calleeName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phoneNumber">
          <el-input v-model="form.phoneNumber" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio label="0">男</el-radio>
            <el-radio label="1">女</el-radio>
            <el-radio label="2">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属地区" prop="area">
          <el-input v-model="form.area" placeholder="请输入所属地区" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <el-dialog title="批量导入号码" :visible.sync="importOpen" width="500px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="任务ID" prop="taskId">
          <el-input v-model="importTaskId" placeholder="请输入任务ID" />
        </el-form-item>
        <el-form-item label="号码数据" prop="calleeData">
          <el-input
            v-model="calleeData"
            type="textarea"
            :rows="8"
            placeholder="每行一条，格式：姓名,手机号,性别,地区&#10;例如：张三,13800138000,男,北京&#10;或者只输入手机号，每行一个"
          />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitImport">导 入</el-button>
        <el-button @click="importOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listCallee, getCallee, delCallee, addCallee, updateCallee, delCalleeByTaskId, batchAddCallee } from "@/api/lawyers/outboundCallee"

export default {
  name: "OutboundCallee",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      calleeList: [],
      // V3：ProTable 列定义（性别/呼叫状态/最近呼叫时间/操作走插槽）
      columns: [
        { prop: 'calleeName', label: '姓名', minWidth: 140, align: 'left', showOverflowTooltip: true },
        { prop: 'phoneNumber', label: '手机号', width: 140 },
        { prop: 'gender', label: '性别', width: 80, align: 'center', slot: true },
        { prop: 'area', label: '所属地区', width: 140, showOverflowTooltip: true },
        { prop: 'callCount', label: '呼叫次数', width: 90, align: 'center' },
        { prop: 'callStatus', label: '呼叫状态', width: 100, align: 'center', slot: true },
        { prop: 'lastCallTime', label: '最近呼叫时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      taskId: null,
      selectedRows: [],
      title: "",
      open: false,
      importOpen: false,
      importTaskId: null,
      calleeData: "",
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        taskId: undefined,
        calleeName: undefined,
        phoneNumber: undefined,
        callStatus: undefined
      },
      form: {},
      rules: {
        phoneNumber: [
          { required: true, message: "手机号不能为空", trigger: "blur" },
          { pattern: /^1[3|4|5|6|7|8|9][0-9]\d{8}$/, message: "请输入正确的手机号码", trigger: "blur" }
        ]
      }
    }
  },
  computed: {
    queryFormItems() {
      const items = []
      // 从任务页跳转时任务 ID 由路由带入且固定，不再暴露筛选
      if (!this.taskId) {
        items.push({ prop: 'taskId', label: '任务ID', placeholder: '请输入任务ID', width: 180 })
      }
      items.push(
        { prop: 'calleeName', label: '姓名', placeholder: '请输入姓名', width: 200 },
        { prop: 'phoneNumber', label: '手机号', placeholder: '请输入手机号', width: 200 },
        {
          prop: 'callStatus',
          label: '呼叫状态',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '待呼叫', value: '0' },
            { label: '已接听', value: '1' },
            { label: '未接听', value: '2' },
            { label: '呼叫失败', value: '3' },
            { label: '已取消', value: '4' }
          ]
        }
      )
      return items
    }
  },
  created() {
    const taskId = this.$route.query.taskId
    if (taskId) {
      this.taskId = taskId
      this.queryParams.taskId = taskId
      this.importTaskId = taskId
    }
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listCallee(this.queryParams).then(response => {
        this.calleeList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        calleeId: null,
        taskId: this.taskId,
        calleeName: null,
        phoneNumber: null,
        gender: "2",
        area: null,
        callStatus: "0",
        remark: null
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查（保留路由带入的任务 ID）
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        taskId: this.taskId || undefined,
        calleeName: undefined,
        phoneNumber: undefined,
        callStatus: undefined
      }
      this.getList()
    },
    handleSelectionChange(selection) {
      this.selectedRows = selection
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加外呼号码"
    },
    handleImport() {
      this.calleeData = ""
      this.importOpen = true
    },
    submitImport() {
      if (!this.importTaskId) {
        this.$modal.msgError("请输入任务ID")
        return
      }
      if (!this.calleeData.trim()) {
        this.$modal.msgError("请输入号码数据")
        return
      }
      const lines = this.calleeData.trim().split('\n').filter(l => l.trim())
      const callees = []
      for (const line of lines) {
        const parts = line.split(/[,，\t]/).map(s => s.trim())
        if (parts.length >= 1 && parts[0]) {
          callees.push({
            calleeName: parts[0] && parts[0].match(/^1[3-9]\d{9}$/) ? '' : parts[0],
            phoneNumber: parts[1] || (parts[0] && parts[0].match(/^1[3-9]\d{9}$/) ? parts[0] : ''),
            gender: parts[2] === '男' ? '0' : (parts[2] === '女' ? '1' : '2'),
            area: parts[3] || ''
          })
        }
      }
      if (callees.length === 0) {
        this.$modal.msgError("未解析到有效号码")
        return
      }
      batchAddCallee(this.importTaskId, callees).then(() => {
        this.$modal.msgSuccess("导入成功，共导入" + callees.length + "条")
        this.importOpen = false
        this.getList()
      })
    },
    handleUpdate(row) {
      this.reset()
      getCallee(row.calleeId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改外呼号码"
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.calleeId != null) {
            updateCallee(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            this.form.taskId = this.taskId
            addCallee(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      this.$modal.dangerConfirm({
        content: '将删除外呼号码「' + row.phoneNumber + '」',
        count: 1,
        countNoun: '个号码'
      }).then(function() {
        return delCallee(row.calleeId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleClear() {
      if (!this.taskId) {
        this.$modal.msgError("请先选择任务")
        return
      }
      this.$modal.dangerConfirm({
        actionType: 'clear',
        content: '将清空当前任务下导入的全部外呼号码',
        tip: '清空后号码名单不可恢复，请确认已导出留存'
      }).then(function() {
        return delCalleeByTaskId(this.taskId)
      }.bind(this)).then(() => {
        this.getList()
        this.$modal.msgSuccess("清空成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/outbound/callee/export', {
        ...this.queryParams
      }, `outbound_callee_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
