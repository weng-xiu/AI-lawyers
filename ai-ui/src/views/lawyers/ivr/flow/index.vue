<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="flowList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      label-width="88px"
      empty-text="还没有 IVR 流程"
      empty-hint="新增流程后可在设计器中编排节点与连线，发布后按新流程接通来电"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:ivr:flow:add']">新增</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:ivr:flow:export']">导出</el-button>
      </template>
      <template #column-status="{ row }">
        <el-tag v-if="row.status === '0'" type="info">草稿</el-tag>
        <el-tag v-else-if="row.status === '1'" type="success">已发布</el-tag>
        <el-tag v-else type="danger">已停用</el-tag>
      </template>
      <template #column-isDefault="{ row }">
        <el-tag v-if="row.isDefault === '1'" type="success">是</el-tag>
        <span v-else>否</span>
      </template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-edit-outline" @click="handleDesign(row)"
          v-hasPermi="['lawyers:ivr:flow:edit']">设计</el-button>
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:ivr:flow:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-video-play" @click="handleTest(row)"
          v-hasPermi="['lawyers:ivr:flow:list']">测试</el-button>
        <el-button v-if="row.status === '0'" size="mini" type="text" icon="el-icon-check" @click="handlePublish(row)"
          v-hasPermi="['lawyers:ivr:flow:publish']">发布</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:ivr:flow:remove']">删除</el-button>
      </template>
    </ProTable>

    <el-dialog title="IVR流程测试运行" :visible.sync="testOpen" width="760px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="流程">
          <el-input :value="testFlowName" disabled />
        </el-form-item>
        <el-form-item label="模拟输入">
          <el-input
            v-model="testInput"
            type="textarea"
            :rows="4"
            placeholder="每行一条输入：菜单节点取首个字符（如 1），意图节点取整段文本（如 我想咨询合同纠纷）"
          />
        </el-form-item>
        <el-form-item label="执行结果">
          <template v-if="testResult">
            <el-tag v-if="testResult.success" type="success">执行成功</el-tag>
            <el-tag v-else type="danger">执行失败：{{ testResult.message }}</el-tag>
            <el-tag v-if="testResult.matchedIntentionName" style="margin-left: 8px" type="primary">
              意图：{{ testResult.matchedIntentionName }}（{{ testResult.matchedIntention }}）
            </el-tag>
            <el-tag v-if="testResult.categoryName" style="margin-left: 8px" type="warning">
              分类：{{ testResult.categoryName }}
            </el-tag>
            <el-tag v-if="testResult.transferTarget" style="margin-left: 8px" type="danger">
              转接：{{ testResult.transferTarget }}
            </el-tag>
            <el-table :data="testResult.steps || []" size="mini" border style="margin-top: 12px">
              <el-table-column label="#" type="index" width="45" align="center" />
              <el-table-column prop="nodeName" label="节点" min-width="120" />
              <el-table-column prop="nodeType" label="类型" width="110" align="center" />
              <el-table-column prop="detail" label="执行内容" min-width="280" />
            </el-table>
          </template>
          <el-alert v-else title="尚未执行，点击下方按钮开始测试" type="info" :closable="false" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="testLoading" @click="runTest">开始测试</el-button>
        <el-button @click="testOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="流程名称" prop="flowName">
          <el-input v-model="form.flowName" placeholder="请输入流程名称" />
        </el-form-item>
        <el-form-item label="流程编码" prop="flowCode">
          <el-input v-model="form.flowCode" placeholder="请输入流程编码" />
        </el-form-item>
        <el-form-item label="流程描述" prop="flowDesc">
          <el-input v-model="form.flowDesc" type="textarea" :rows="3" placeholder="请输入流程描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">草稿</el-radio>
            <el-radio label="2">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设为默认" prop="isDefault">
          <el-radio-group v-model="form.isDefault">
            <el-radio label="0">否</el-radio>
            <el-radio label="1">是</el-radio>
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
import { listFlow, getFlow, delFlow, addFlow, updateFlow, publishFlow } from "@/api/lawyers/ivrFlow"
import { executeFlow } from "@/api/lawyers/ivrEngine"

export default {
  name: "IvrFlow",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      flowList: [],
      // V3：ProTable 列定义（状态/默认/时间/操作走插槽）
      columns: [
        { prop: 'flowName', label: '流程名称', minWidth: 180, align: 'center', showOverflowTooltip: true },
        { prop: 'flowCode', label: '流程编码', width: 140, align: 'center', showOverflowTooltip: true },
        { prop: 'version', label: '版本号', width: 80, align: 'center' },
        { prop: 'status', label: '状态', width: 100, align: 'center', slot: true },
        { prop: 'isDefault', label: '是否默认', width: 100, align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 280, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      title: "",
      open: false,
      testOpen: false,
      testLoading: false,
      testInput: "",
      testResult: null,
      testFlowId: null,
      testFlowName: "",
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        flowName: undefined,
        flowCode: undefined,
        status: undefined
      },
      form: {},
      rules: {
        flowName: [
          { required: true, message: "流程名称不能为空", trigger: "blur" }
        ],
        flowCode: [
          { required: true, message: "流程编码不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'flowName', label: '流程名称', placeholder: '请输入流程名称', width: 240 },
        { prop: 'flowCode', label: '流程编码', placeholder: '请输入流程编码', width: 200 },
        {
          prop: 'status',
          label: '状态',
          type: 'select',
          placeholder: '请选择状态',
          width: 120,
          options: [
            { label: '草稿', value: '0' },
            { label: '已发布', value: '1' },
            { label: '已停用', value: '2' }
          ]
        }
      ]
    }
  },
  methods: {
    getList() {
      this.loading = true
      listFlow(this.queryParams).then(response => {
        this.flowList = response.rows
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
        flowId: null,
        flowName: null,
        flowCode: null,
        flowDesc: null,
        status: "0",
        isDefault: "0"
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, flowName: undefined, flowCode: undefined, status: undefined }
      this.getList()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加IVR流程"
    },
    handleDesign(row) {
      this.$router.push({ path: '/lawyers/ivr/flow/design/index', query: { flowId: row.flowId } })
    },
    handleTest(row) {
      this.testFlowId = row.flowId
      this.testFlowName = row.flowName
      this.testInput = ""
      this.testResult = null
      this.testOpen = true
    },
    runTest() {
      this.testLoading = true
      const inputs = (this.testInput || "").split("\n").map(s => s.trim()).filter(s => s !== "")
      executeFlow({ flowId: this.testFlowId, inputs: inputs }).then(response => {
        this.testResult = response.data
        this.testLoading = false
      }).catch(() => {
        this.testLoading = false
      })
    },
    handleUpdate(row) {
      this.reset()
      getFlow(row.flowId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改IVR流程"
      })
    },
    handlePublish(row) {
      this.$modal.dangerConfirm({
        actionType: 'publish',
        content: '将发布 IVR 流程「' + row.flowName + '」，发布后来电路由按新流程执行',
        count: 1,
        countNoun: '个流程'
      }).then(function() {
        return publishFlow(row.flowId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("发布成功")
      }).catch(() => {})
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.flowId != null) {
            updateFlow(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addFlow(this.form).then(response => {
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
        content: '将删除 IVR 流程「' + row.flowName + '」及其节点连线配置',
        count: 1,
        countNoun: '个流程',
        tip: '若该流程仍被来电路由引用，删除后相关呼叫将走默认兜底流程'
      }).then(function() {
        return delFlow(row.flowId)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/ivr/flow/export', {
        ...this.queryParams
      }, `ivr_flow_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
