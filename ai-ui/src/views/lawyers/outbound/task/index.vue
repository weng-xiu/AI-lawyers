<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="taskList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      empty-text="还没有外呼任务"
      empty-hint="新建任务并导入号码名单后，即可按调度策略自动外呼"
      empty-action-text="新建外呼任务"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @empty-action="handleAdd">
      <template #toolbar-left>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:outbound:task:add']"
        >新增</el-button>
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:outbound:task:export']"
        >导出</el-button>
      </template>
      <template #column-taskType="{ row }">
        <el-tag v-if="row.taskType === '1'" type="primary">批量外呼</el-tag>
        <el-tag v-else-if="row.taskType === '2'" type="success">回访</el-tag>
        <el-tag v-else type="warning">通知</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-tag v-if="row.status === '0'" type="info">待开始</el-tag>
        <el-tag v-else-if="row.status === '1'" type="success">进行中</el-tag>
        <el-tag v-else-if="row.status === '2'" type="primary">已完成</el-tag>
        <el-tag v-else-if="row.status === '3'" type="warning">已暂停</el-tag>
        <el-tag v-else type="danger">已停止</el-tag>
      </template>
      <template #column-createTime="{ row }">
        <span>{{ parseTime(row.createTime) }}</span>
      </template>
      <template #column-action="{ row }">
        <el-button
          size="mini"
          type="text"
          icon="el-icon-user"
          @click="handleCallee(row)"
          v-hasPermi="['lawyers:outbound:callee:list']"
        >号码</el-button>
        <el-button
          v-if="row.status === '0'"
          size="mini"
          type="text"
          icon="el-icon-video-play"
          @click="handleStart(row)"
          v-hasPermi="['lawyers:outbound:task:start']"
        >启动</el-button>
        <el-button
          v-if="row.status === '1'"
          size="mini"
          type="text"
          icon="el-icon-video-play"
          @click="handleExecute(row)"
          v-hasPermi="['lawyers:outbound:task:start']"
        >执行</el-button>
        <el-button
          v-if="row.status === '1'"
          size="mini"
          type="text"
          icon="el-icon-video-pause"
          @click="handlePause(row)"
          v-hasPermi="['lawyers:outbound:task:pause']"
        >暂停</el-button>
        <el-button
          v-if="row.status === '1' || row.status === '3'"
          size="mini"
          type="text"
          icon="el-icon-circle-close"
          @click="handleStop(row)"
          v-hasPermi="['lawyers:outbound:task:stop']"
        >停止</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-edit"
          @click="handleUpdate(row)"
          v-hasPermi="['lawyers:outbound:task:edit']"
        >修改</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(row)"
          v-hasPermi="['lawyers:outbound:task:remove']"
        >删除</el-button>
      </template>
    </ProTable>

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
import { createPoller } from "@/utils/polling"

export default {
  name: "OutboundTask",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      taskList: [],
      // V3：ProTable 列定义（任务类型/状态/创建时间/操作走插槽）
      columns: [
        { prop: 'taskName', label: '任务名称', minWidth: 180, align: 'left', showOverflowTooltip: true },
        { prop: 'taskNo', label: '任务编号', width: 160 },
        { prop: 'taskType', label: '任务类型', width: 100, align: 'center', slot: true },
        { prop: 'totalCount', label: '总号码数', width: 100, align: 'center' },
        { prop: 'completedCount', label: '已完成', width: 100, align: 'center' },
        { prop: 'answeredCount', label: '已接听', width: 100, align: 'center' },
        { prop: 'status', label: '状态', width: 100, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 360, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    queryFormItems() {
      return [
        { prop: 'taskName', label: '任务名称', placeholder: '请输入任务名称', width: 240 },
        { prop: 'taskNo', label: '任务编号', placeholder: '请输入任务编号', width: 200 },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '待开始', value: '0' },
            { label: '进行中', value: '1' },
            { label: '已暂停', value: '3' },
            { label: '已停止', value: '4' },
            { label: '已完成', value: '5' }
          ]
        }
      ]
    }
  },
  created() {
    this.getList()
    this.loadFlows()
  },
  beforeDestroy() {
    this.stopAutoRefresh()
  },
  methods: {
    getList() {
      this.loading = true
      return listTask(this.queryParams).then(response => {
        this.applyRows(response)
      }).finally(() => {
        this.loading = false
      })
    },
    // U8：列表数据统一落地；对比上次状态，进行中(1) 变为已完成(2)/已停止(4) 时发完成/停止通知
    applyRows(response) {
      const rows = response.rows || []
      const prev = this.statusSnapshot
      const next = {}
      rows.forEach(r => {
        next[r.taskId] = r.status
        if (!prev) return
        if (prev[r.taskId] === '1' && r.status === '2') {
          this.$modal.notifyTaskDone('外呼任务完成', `任务「${r.taskName}」已完成`)
        } else if (prev[r.taskId] === '1' && r.status === '4') {
          this.$modal.notifyTaskFail('外呼任务已停止', `任务「${r.taskName}」已停止`)
        }
      })
      this.statusSnapshot = next
      this.taskList = rows
      this.total = response.total
      this.syncAutoRefresh()
    },
    // U8：有进行中任务时每 5 秒静默刷新列表，任务结束后自动停止轮询
    syncAutoRefresh() {
      const hasRunning = this.taskList.some(r => r.status === '1')
      if (!hasRunning) {
        this.stopAutoRefresh()
        return
      }
      if (this.poller) return
      this.poller = createPoller(() => this.refreshSilently(), 5000)
      this.poller.start()
    },
    refreshSilently() {
      return listTask(this.queryParams).then(response => {
        this.applyRows(response)
      })
    },
    stopAutoRefresh() {
      if (this.poller) {
        this.poller.stop()
        this.poller = null
      }
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        taskName: undefined,
        taskNo: undefined,
        status: undefined
      }
      this.getList()
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认启动任务「' + row.taskName + '」吗？',
        tip: '启动后将按并发策略开始外呼，可能产生线路费用'
      }).then(function() {
        return startTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.notifyTaskStart('外呼任务已启动', `任务「${row.taskName}」执行中，完成后将通知你`)
      }).catch(() => {})
    },
    handleExecute(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        title: '立即执行确认',
        confirmText: '确认立即执行',
        content: '将立即执行外呼任务「' + row.taskName + '」，拨出一批号码并生成话单/结果',
        count: 1,
        countNoun: '个任务',
        tip: '外呼将真实接通公众电话，请确认任务名单与线路状态'
      }).then(function() {
        return executeTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.notifyTaskStart('外呼任务执行中', `任务「${row.taskName}」已下发执行，完成后将通知你`)
      }).catch(() => {})
    },
    handlePause(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认暂停任务「' + row.taskName + '」吗？',
        tip: '已拨出的通话不受影响，剩余号码将不再外呼；可稍后继续启动'
      }).then(function() {
        return pauseTask(row.taskId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("暂停成功")
      }).catch(() => {})
    },
    handleStop(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认停止任务「' + row.taskName + '」吗？',
        tip: '停止后任务不可继续，未拨号码将不再外呼'
      }).then(function() {
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
      this.$modal.dangerConfirm({
        content: '将删除外呼任务「' + row.taskName + '」及其执行结果',
        count: 1,
        countNoun: '个任务'
      }).then(function() {
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
