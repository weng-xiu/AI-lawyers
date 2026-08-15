<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="88px">
      <el-form-item label="任务名称" prop="taskName">
        <el-input
          v-model="queryParams.taskName"
          placeholder="请输入任务名称"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="任务编号" prop="taskNo">
        <el-input
          v-model="queryParams.taskNo"
          placeholder="请输入任务编号"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option label="待开始" value="0" />
          <el-option label="进行中" value="1" />
          <el-option label="已暂停" value="3" />
          <el-option label="已停止" value="4" />
          <el-option label="已完成" value="5" />
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
          v-hasPermi="['lawyers:outbound:task:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:outbound:task:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="taskList">
      <el-table-column label="任务名称" prop="taskName" :show-overflow-tooltip="true" />
      <el-table-column label="任务编号" prop="taskNo" width="160" />
      <el-table-column label="任务类型" prop="taskType" width="100" align="center">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.taskType === '1'" type="primary">批量外呼</el-tag>
          <el-tag v-else-if="scope.row.taskType === '2'" type="success">回访</el-tag>
          <el-tag v-else type="warning">通知</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="总号码数" prop="totalCount" width="100" align="center" />
      <el-table-column label="已完成" prop="completedCount" width="100" align="center" />
      <el-table-column label="已接听" prop="answeredCount" width="100" align="center" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.status === '0'" type="info">待开始</el-tag>
          <el-tag v-else-if="scope.row.status === '1'" type="success">进行中</el-tag>
          <el-tag v-else-if="scope.row.status === '2'" type="primary">已完成</el-tag>
          <el-tag v-else-if="scope.row.status === '3'" type="warning">已暂停</el-tag>
          <el-tag v-else type="danger">已停止</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="360">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-user"
            @click="handleCallee(scope.row)"
            v-hasPermi="['lawyers:outbound:callee:list']"
          >号码</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            size="mini"
            type="text"
            icon="el-icon-video-play"
            @click="handleStart(scope.row)"
            v-hasPermi="['lawyers:outbound:task:start']"
          >启动</el-button>
          <el-button
            v-if="scope.row.status === '1'"
            size="mini"
            type="text"
            icon="el-icon-video-play"
            @click="handleExecute(scope.row)"
            v-hasPermi="['lawyers:outbound:task:start']"
          >执行</el-button>
          <el-button
            v-if="scope.row.status === '1'"
            size="mini"
            type="text"
            icon="el-icon-video-pause"
            @click="handlePause(scope.row)"
            v-hasPermi="['lawyers:outbound:task:pause']"
          >暂停</el-button>
          <el-button
            v-if="scope.row.status === '1' || scope.row.status === '3'"
            size="mini"
            type="text"
            icon="el-icon-circle-close"
            @click="handleStop(scope.row)"
            v-hasPermi="['lawyers:outbound:task:stop']"
          >停止</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:outbound:task:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:outbound:task:remove']"
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

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务编号" prop="taskNo">
          <el-input v-model="form.taskNo" placeholder="自动生成" disabled>
            <el-button slot="append" icon="el-icon-refresh" @click="genTaskNo">生成</el-button>
          </el-input>
        </el-form-item>
        <el-form-item label="任务类型" prop="taskType">
          <el-radio-group v-model="form.taskType">
            <el-radio label="1">批量外呼</el-radio>
            <el-radio label="2">回访</el-radio>
            <el-radio label="3">通知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="IVR流程" prop="ivrFlowId">
          <el-select v-model="form.ivrFlowId" placeholder="请选择IVR流程（智能外呼自动进入该流程）" clearable style="width: 100%">
            <el-option
              v-for="item in flowOptions"
              :key="item.flowId"
              :label="item.flowName"
              :value="item.flowId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主叫号码" prop="callerNumber">
          <el-input v-model="form.callerNumber" placeholder="如 12348" />
        </el-form-item>
        <el-form-item label="呼叫时段" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="开始时间"
            value-format="yyyy-MM-dd HH:mm:ss"
            style="width: 46%"
          />
          <span style="margin: 0 8px;">至</span>
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="结束时间"
            value-format="yyyy-MM-dd HH:mm:ss"
            style="width: 46%"
          />
        </el-form-item>
        <el-form-item label="最大并发" prop="maxConcurrent">
          <el-input-number v-model="form.maxConcurrent" controls-position="right" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="最大重试" prop="retryCount">
          <el-input-number v-model="form.retryCount" controls-position="right" :min="0" :max="5" />
        </el-form-item>
        <el-form-item label="重试间隔(分)" prop="retryInterval">
          <el-input-number v-model="form.retryInterval" controls-position="right" :min="1" :max="1440" />
        </el-form-item>
        <el-form-item label="任务说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入任务说明" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">待开始</el-radio>
          </el-radio-group>
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
import { listTask, getTask, delTask, addTask, updateTask, generateTaskNo, startTask, pauseTask, stopTask, executeTask } from "@/api/lawyers/outboundTask"
import { getPublishedFlows } from "@/api/lawyers/ivrFlow"

export default {
  name: "OutboundTask",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      taskList: [],
      flowOptions: [],
      title: "",
      open: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        taskName: undefined,
        taskNo: undefined,
        status: undefined
      },
      form: {},
      rules: {
        taskName: [
          { required: true, message: "任务名称不能为空", trigger: "blur" }
        ],
        taskType: [
          { required: true, message: "任务类型不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.loadFlows()
  },
  methods: {
    getList() {
      this.loading = true
      listTask(this.queryParams).then(response => {
        this.taskList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    loadFlows() {
      getPublishedFlows().then(response => {
        this.flowOptions = response.data || []
      })
    },
    genTaskNo() {
      generateTaskNo().then(response => {
        this.form.taskNo = response.data
      })
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        taskId: null,
        taskName: null,
        taskNo: null,
        taskType: "1",
        ivrFlowId: null,
        callerNumber: "12348",
        startTime: null,
        endTime: null,
        maxConcurrent: 10,
        retryCount: 1,
        retryInterval: 30,
        description: null,
        status: "0"
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
      this.genTaskNo()
      this.open = true
      this.title = "添加外呼任务"
    },
    handleCallee(row) {
      this.$router.push({ path: '/outbound/callee', query: { taskId: row.taskId }})
    },
    handleStart(row) {
      this.$modal.confirm('是否确认启动任务"' + row.taskName + '"？').then(function() {
        return startTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("启动成功")
      }).catch(() => {})
    },
    handleExecute(row) {
      this.$modal.confirm('是否立即执行任务"' + row.taskName + '"（将拨出一批号码并生成话单/结果）？').then(function() {
        return executeTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("执行完成")
      }).catch(() => {})
    },
    handlePause(row) {
      this.$modal.confirm('是否确认暂停任务"' + row.taskName + '"？').then(function() {
        return pauseTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("暂停成功")
      }).catch(() => {})
    },
    handleStop(row) {
      this.$modal.confirm('是否确认停止任务"' + row.taskName + '"？').then(function() {
        return stopTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("停止成功")
      }).catch(() => {})
    },
    handleUpdate(row) {
      this.reset()
      getTask(row.taskId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改外呼任务"
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.taskId != null) {
            updateTask(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addTask(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    handleDelete(row) {
      this.$modal.confirm('是否确认删除任务"' + row.taskName + '"？').then(function() {
        return delTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/outbound/task/export', {
        ...this.queryParams
      }, `outbound_task_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
