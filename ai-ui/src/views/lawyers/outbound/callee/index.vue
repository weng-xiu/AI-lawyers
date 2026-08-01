<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="任务ID" prop="taskId" v-if="!taskId">
        <el-input
          v-model="queryParams.taskId"
          placeholder="请输入任务ID"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="姓名" prop="calleeName">
        <el-input
          v-model="queryParams.calleeName"
          placeholder="请输入姓名"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="手机号" prop="phoneNumber">
        <el-input
          v-model="queryParams.phoneNumber"
          placeholder="请输入手机号"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="呼叫状态" prop="callStatus">
        <el-select v-model="queryParams.callStatus" placeholder="请选择状态" clearable>
          <el-option label="待呼叫" value="0" />
          <el-option label="已接听" value="1" />
          <el-option label="未接听" value="2" />
          <el-option label="呼叫失败" value="3" />
          <el-option label="已取消" value="4" />
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
          v-hasPermi="['lawyers:outbound:callee:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="handleImport"
          v-hasPermi="['lawyers:outbound:callee:add']"
        >导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:outbound:callee:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          @click="handleClear"
          v-hasPermi="['lawyers:outbound:callee:remove']"
          :disabled="!taskId"
        >清空号码</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="calleeList">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="姓名" prop="calleeName" :show-overflow-tooltip="true" />
      <el-table-column label="手机号" prop="phoneNumber" width="140" />
      <el-table-column label="性别" prop="gender" width="80" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.gender === '0'">男</span>
          <span v-else-if="scope.row.gender === '1'">女</span>
          <span v-else>未知</span>
        </template>
      </el-table-column>
      <el-table-column label="所属地区" prop="area" :show-overflow-tooltip="true" width="140" />
      <el-table-column label="呼叫次数" prop="callCount" width="90" align="center" />
      <el-table-column label="呼叫状态" align="center" prop="callStatus" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.callStatus === '0'" type="info">待呼叫</el-tag>
          <el-tag v-else-if="scope.row.callStatus === '1'" type="success">已接听</el-tag>
          <el-tag v-else-if="scope.row.callStatus === '2'" type="warning">未接听</el-tag>
          <el-tag v-else-if="scope.row.callStatus === '3'" type="danger">呼叫失败</el-tag>
          <el-tag v-else type="info">已取消</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最近呼叫时间" align="center" prop="lastCallTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.lastCallTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:outbound:callee:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:outbound:callee:remove']"
          >删除</el-button>
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
      taskId: null,
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
      this.resetForm("queryForm")
      this.handleQuery()
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
      this.$modal.confirm('是否确认删除号码"' + row.phoneNumber + '"？').then(function() {
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
      this.$modal.confirm('是否确认清空该任务的所有号码？').then(function() {
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
