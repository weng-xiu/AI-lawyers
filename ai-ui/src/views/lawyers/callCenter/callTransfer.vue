<template>
  <div class="app-container cc-page">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="transferList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      label-width="90px"
      has-selection
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-left>
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:call:transfer:add']"
        >新增</el-button>
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:call:transfer:remove']"
        >删除</el-button>
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:call:transfer:export']"
        >导出</el-button>
      </template>
      <template #column-action="{ row }">
        <el-button
          size="mini"
          type="text"
          icon="el-icon-view"
          @click="handleDetail(row)"
          v-hasPermi="['lawyers:call:transfer:query']"
        >详情</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-delete"
          @click="handleDelete(row)"
          v-hasPermi="['lawyers:call:transfer:remove']"
        >删除</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="来电记录ID" prop="recordId">
          <el-input v-model="form.recordId" placeholder="请输入来电记录ID" />
        </el-form-item>
        <el-row :gutter="10">
        <el-col :span="12"><el-form-item label="转出坐席" prop="fromAgentId">
          <el-select v-model="form.fromAgentId" placeholder="请选择" filterable clearable style="width:100%" @change="onFromAgentChange">
            <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
          </el-select>
        </el-form-item></el-col>
        <el-col :span="12"><el-form-item label="转入坐席" prop="toAgentId">
          <el-select v-model="form.toAgentId" placeholder="请选择" filterable clearable style="width:100%" @change="onToAgentChange">
            <el-option v-for="a in agentOptions" :key="a.agentId" :label="a.agentName+'('+a.agentId+')'" :value="a.agentId" />
          </el-select>
        </el-form-item></el-col>
        </el-row>
        <el-form-item label="转接原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" placeholder="请输入转接原因" />
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

    <el-dialog title="转接详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="转接ID">{{ detailForm.transferId }}</el-descriptions-item>
        <el-descriptions-item label="来电记录ID">{{ detailForm.recordId }}</el-descriptions-item>
        <el-descriptions-item label="转出坐席">{{ detailForm.fromAgentName }}</el-descriptions-item>
        <el-descriptions-item label="转入坐席">{{ detailForm.toAgentName }}</el-descriptions-item>
        <el-descriptions-item label="转接原因">{{ detailForm.reason }}</el-descriptions-item>
        <el-descriptions-item label="转接时间">{{ parseTime(detailForm.transferTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailForm.createBy }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTransfer, getTransfer, addTransfer, delTransfer } from "@/api/lawyers/callTransfer"
import { listAgent } from "@/api/lawyers/callCenter"

export default {
  name: "CallTransfer",
  data() {
    return {
      loading: true,
      ids: [],
      multiple: true,
      showSearch: true,
      total: 0,
      transferList: [],
      // V3：ProTable 列定义（action 走插槽，时间列用 formatter）
      columns: [
        { prop: 'transferId', label: '转接ID', width: 90 },
        { prop: 'recordId', label: '来电记录ID', width: 110 },
        { prop: 'fromAgentName', label: '转出坐席' },
        { prop: 'toAgentName', label: '转入坐席' },
        { prop: 'reason', label: '转接原因', showOverflowTooltip: true },
        { prop: 'transferTime', label: '转接时间', width: 180, formatter: (row) => this.parseTime(row.transferTime) },
        { prop: 'remark', label: '备注', showOverflowTooltip: true },
        { prop: 'action', label: '操作', width: 160, slot: true, className: 'small-padding fixed-width' }
      ],
      agentOptions: [],
      title: "",
      open: false,
      detailOpen: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        recordId: undefined,
        fromAgentId: undefined,
        toAgentId: undefined
      },
      form: {},
      detailForm: {},
      rules: {
        recordId: [
          { required: true, message: "来电记录ID不能为空", trigger: "blur" }
        ],
        fromAgentId: [
          { required: true, message: "转出坐席不能为空", trigger: "change" }
        ],
        toAgentId: [
          { required: true, message: "转入坐席不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.loadAgents()
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段（坐席下拉依赖异步 agentOptions，用 computed 保证响应式） */
    queryFormItems() {
      const agentOpts = this.agentOptions.map(a => ({ label: a.agentName + '(' + a.agentId + ')', value: a.agentId }))
      return [
        { prop: 'recordId', label: '来电记录ID', placeholder: '请输入来电记录ID', width: '200px' },
        { prop: 'fromAgentId', label: '转出坐席', type: 'select', placeholder: '全部坐席', filterable: true, width: '200px', options: agentOpts },
        { prop: 'toAgentId', label: '转入坐席', type: 'select', placeholder: '全部坐席', filterable: true, width: '200px', options: agentOpts }
      ]
    }
  },
  methods: {
    loadAgents() {
      listAgent({ pageSize: 999, status: '1' }).then(response => {
        this.agentOptions = response.rows || []
      })
    },
    onFromAgentChange(val) {
      const a = this.agentOptions.find(x => x.agentId === val)
      this.$set(this.form, 'fromAgentName', a ? a.agentName : '')
    },
    onToAgentChange(val) {
      const a = this.agentOptions.find(x => x.agentId === val)
      this.$set(this.form, 'toAgentName', a ? a.agentName : '')
    },
    getList() {
      this.loading = true
      listTransfer(this.queryParams).then(response => {
        this.transferList = response.rows
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
        transferId: undefined,
        recordId: undefined,
        fromAgentId: undefined,
        fromAgentName: undefined,
        toAgentId: undefined,
        toAgentName: undefined,
        transferTime: undefined,
        reason: undefined,
        remark: undefined
      }
      this.resetForm("form")
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里复位插槽字段后重查
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "新增转接记录"
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.transferId)
      this.multiple = !selection.length
    },
    handleDetail(row) {
      getTransfer(row.transferId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          addTransfer(this.form).then(response => {
            this.$modal.msgSuccess("新增成功")
            this.open = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const transferIds = row.transferId || this.ids
      const count = row.transferId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的转接记录',
        count: count,
        countNoun: '条转接记录'
      }).then(function() {
        return delTransfer(transferIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/call/transfer/export', {
        ...this.queryParams
      }, `transfer_${Date.now()}.xlsx`)
    }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';
</style>
