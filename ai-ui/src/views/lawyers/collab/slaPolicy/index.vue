<template>
  <div class="app-container">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="policyList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :table-attrs="{ border: true }"
      has-selection
      label-width="90px"
      empty-text="暂无 SLA 策略"
      empty-hint="新增策略后，转办工单将按业务类型自动匹配响应与办结时限"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button v-hasPermi="['lawyers:slaPolicy:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增策略</el-button>
        <el-button v-hasPermi="['lawyers:slaPolicy:remove']" type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
      </template>
      <template #column-bizType="{ row }">{{ bizText(row.bizType) }}</template>
      <template #column-priority="{ row }">
        <el-tag size="mini" :type="row.priority === '1' ? 'danger' : (row.priority === '3' ? 'info' : '')">{{ priorityText(row.priority) }}</el-tag>
      </template>
      <template #column-respondMinutes="{ row }">{{ row.respondMinutes }} 分钟</template>
      <template #column-resolveMinutes="{ row }">{{ row.resolveMinutes }} 分钟</template>
      <template #column-warnThreshold="{ row }">{{ row.warnThreshold }}%</template>
      <template #column-escalateRoles="{ row }">
        <el-tag v-for="(r, i) in (row.escalateRoles || '').split(',').filter(Boolean)" :key="i" size="mini" style="margin-right:4px">{{ r }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-tag size="mini" :type="row.status === '0' ? 'success' : 'danger'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button v-hasPermi="['lawyers:slaPolicy:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
        <el-button v-hasPermi="['lawyers:slaPolicy:remove']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(row)">删除</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="600px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="130px" size="small">
        <el-form-item label="策略名称" prop="policyName">
          <el-input v-model="form.policyName" placeholder="如 普通工单SLA" maxlength="100" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="form.bizType" placeholder="请选择" style="width:100%">
            <el-option v-for="item in bizOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio label="1">紧急</el-radio>
            <el-radio label="2">普通</el-radio>
            <el-radio label="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="响应时限（分钟）" prop="respondMinutes">
          <el-input-number v-model="form.respondMinutes" :min="1" :max="100000" controls-position="right" />
        </el-form-item>
        <el-form-item label="办结时限（分钟）" prop="resolveMinutes">
          <el-input-number v-model="form.resolveMinutes" :min="1" :max="100000" controls-position="right" />
        </el-form-item>
        <el-form-item label="预警阈值（%）" prop="warnThreshold">
          <el-input-number v-model="form.warnThreshold" :min="1" :max="100" controls-position="right" />
        </el-form-item>
        <el-form-item label="逐级升级角色链">
          <el-input v-model="form.escalateRoles" placeholder="逗号分隔角色key，如 ai_team_leader,ai_manager,ai_director" maxlength="300" />
          <div class="form-tip">超期后按办结时限周期逐级升级，每个角色一级，经 WebSocket 广播 TICKET_ESCALATE</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listSlaPolicy, getSlaPolicy, addSlaPolicy, updateSlaPolicy, delSlaPolicy } from '@/api/lawyers/slaPolicy'

export default {
  name: 'CollabSlaPolicy',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      policyList: [],
      // V3：ProTable 列定义（业务类型/优先级/时限/角色链/状态/操作走插槽）
      columns: [
        { prop: 'policyId', label: 'ID', width: 70 },
        { prop: 'policyName', label: '策略名称', minWidth: 180, showOverflowTooltip: true },
        { prop: 'bizType', label: '业务类型', width: 130, slot: true },
        { prop: 'priority', label: '优先级', width: 90, slot: true },
        { prop: 'respondMinutes', label: '响应时限', width: 110, slot: true },
        { prop: 'resolveMinutes', label: '办结时限', width: 110, slot: true },
        { prop: 'warnThreshold', label: '预警阈值', width: 100, slot: true },
        { prop: 'escalateRoles', label: '逐级升级角色链', minWidth: 200, slot: true },
        { prop: 'status', label: '状态', width: 80, slot: true },
        { prop: 'action', label: '操作', width: 150, slot: true, className: 'small-padding fixed-width' }
      ],
      open: false,
      title: '',
      ids: [],
      multiple: true,
      bizOptions: [
        { value: 'TICKET', label: '热线工单' },
        { value: 'LEGAL_AID', label: '法律援助' },
        { value: 'MEDIATION', label: '人民调解' },
        { value: 'NOTARY', label: '公证' },
        { value: 'FORENSIC', label: '司法鉴定' },
        { value: 'ARBITRATION', label: '仲裁' },
        { value: 'HOTLINE_12345', label: '12345转入' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, policyName: undefined, bizType: undefined, status: undefined },
      form: {},
      rules: {
        policyName: [{ required: true, message: '策略名称不能为空', trigger: 'blur' }],
        bizType: [{ required: true, message: '请选择业务类型', trigger: 'change' }],
        priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
        respondMinutes: [{ required: true, message: '响应时限不能为空', trigger: 'blur' }],
        resolveMinutes: [{ required: true, message: '办结时限不能为空', trigger: 'blur' }]
      }
    }
  },
  created() { this.getList() },
  computed: {
    /** V3：ProTable 查询字段 */
    queryFormItems() {
      return [
        { prop: 'policyName', label: '策略名称', placeholder: '请输入策略名称', width: '170px' },
        { prop: 'bizType', label: '业务类型', type: 'select', placeholder: '全部', width: '150px', options: this.bizOptions },
        { prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: '110px', options: [
          { label: '启用', value: '0' }, { label: '停用', value: '1' }
        ] }
      ]
    }
  },
  methods: {
    bizText(v) { const hit = this.bizOptions.find(i => i.value === v); return hit ? hit.label : (v || '-') },
    priorityText(v) { return { '1': '紧急', '2': '普通', '3': '低' }[String(v)] || v },
    getList() {
      this.loading = true
      listSlaPolicy(this.queryParams).then(res => {
        this.policyList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, policyName: undefined, bizType: undefined, status: undefined }
      this.getList()
    },
    reset() {
      this.form = { policyId: undefined, policyName: undefined, bizType: 'TICKET', priority: '2', respondMinutes: 30, resolveMinutes: 480, warnThreshold: 80, escalateRoles: undefined, status: '0', remark: undefined }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() { this.reset(); this.open = true; this.title = '新增SLA策略' },
    handleUpdate(row) {
      this.reset()
      getSlaPolicy(row.policyId).then(res => { this.form = res.data || {}; this.open = true; this.title = '编辑SLA策略' })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.resolveMinutes < this.form.respondMinutes) {
          this.$modal.msgError('办结时限不能小于响应时限')
          return
        }
        const api = this.form.policyId != null ? updateSlaPolicy : addSlaPolicy
        api(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() { this.open = false; this.reset() },
    handleSelectionChange(sel) { this.ids = sel.map(i => i.policyId); this.multiple = !sel.length },
    handleDelete(row) {
      const ids = row.policyId ? [row.policyId] : this.ids
      this.$modal.dangerConfirm({
        content: '将删除选中的 SLA 服务时效策略',
        count: ids.length,
        countNoun: '条策略',
        tip: '删除后新转办单不再适用该时效要求'
      }).then(() => delSlaPolicy(ids)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.form-tip { font-size: 12px; color: #909399; line-height: 1.5; }
</style>
