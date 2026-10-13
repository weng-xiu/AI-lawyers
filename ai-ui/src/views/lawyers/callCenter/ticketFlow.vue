<template>
  <div class="app-container">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable（本页为前端本地分页） -->
    <ProTable
      ref="proTable"
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="pagedList"
      :loading="loading"
      :total="filteredList.length"
      :show-search.sync="showSearch"
      label-width="80px"
      :table-attrs="{ border: true, size: 'small' }"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="paginate"
    >
      <template #toolbar-left>
        <el-button v-hasPermi="['lawyers:ticketFlow:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
      </template>
      <template #column-actionCode="{ row }">
        <div>{{ row.actionCode }}</div>
        <div class="sub-text">{{ row.actionName }}</div>
      </template>
      <template #column-status="{ row }">
        <el-tag :type="row.status === '0' ? 'success' : 'info'" size="mini">
          {{ row.status === '0' ? '启用' : '停用' }}
        </el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button v-hasPermi="['lawyers:ticketFlow:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
      </template>
    </ProTable>

    <el-dialog :title="title" :visible.sync="open" width="620px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-form-item label="流程类型" prop="flowCode">
          <el-input v-model="form.flowCode" placeholder="如 HOTLINE" maxlength="32" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="源状态" prop="statusFrom">
          <el-select v-model="form.statusFrom" placeholder="请选择" style="width: 100%" :disabled="isEdit">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作标识" prop="actionCode">
          <el-input v-model="form.actionCode" placeholder="如 start / transfer / complete / archive" maxlength="32" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="动作名称" prop="actionName">
          <el-input v-model="form.actionName" placeholder="如 受理" maxlength="32" />
        </el-form-item>
        <el-form-item label="目标状态" prop="targetStatus">
          <el-select v-model="form.targetStatus" placeholder="请选择" style="width: 100%">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许角色" prop="roleKey">
          <el-input v-model="form.roleKey" placeholder="逗号分隔，如 agent,leader，admin 恒放行" maxlength="64" />
        </el-form-item>
        <el-form-item label="排序" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="SLA 时限(小时)">
          <el-input-number v-model="form.slaHours" :min="0" :precision="2" :step="1" controls-position="right" style="width: 180px" />
          <span class="form-tip">留空不重算时限；填 0 清空到期时间</span>
        </el-form-item>
        <el-form-item label="必填表单 JSON">
          <el-input v-model="form.formSchema" type="textarea" :rows="3" placeholder='如 {"required":["orgId","remark"]}' />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTicketFlow, getTicketFlow, addTicketFlow, updateTicketFlow } from '@/api/lawyers/ticketFlow'

export default {
  name: 'TicketFlow',
  data() {
    return {
      loading: false,
      showSearch: true,
      allList: [],
      filteredList: [],
      pagedList: [],
      // V3：ProTable 列定义（源状态/目标状态/SLA 用 formatter，动作/状态/操作走插槽）
      columns: [
        { prop: 'flowId', label: 'ID', width: 70 },
        { prop: 'flowCode', label: '流程类型', width: 110 },
        { prop: 'statusFrom', label: '源状态', width: 100, formatter: (row) => this.statusText(row.statusFrom) },
        { prop: 'actionCode', label: '动作', width: 110, slot: true },
        { prop: 'targetStatus', label: '目标状态', width: 100, formatter: (row) => this.statusText(row.targetStatus) },
        { prop: 'roleKey', label: '允许角色', minWidth: 140, showOverflowTooltip: true },
        { prop: 'sortNo', label: '排序', width: 70 },
        { prop: 'slaHours', label: 'SLA(小时)', width: 90, formatter: (row) => row.slaHours == null ? '—' : (row.slaHours === 0 ? '清时限' : row.slaHours) },
        { prop: 'status', label: '状态', width: 90, slot: true },
        { prop: 'action', label: '操作', width: 120, fixed: 'right', slot: true }
      ],
      open: false,
      title: '',
      isEdit: false,
      // V3：pageNum/pageSize 仅供 ProTable 本地分页使用，查询时不下发后端
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        flowCode: undefined,
        statusFrom: undefined,
        actionCode: undefined,
        status: undefined
      },
      statusOptions: [
        { value: '0', label: '待处理' },
        { value: '1', label: '处理中' },
        { value: '2', label: '已完成' },
        { value: '3', label: '已归档' }
      ],
      form: {},
      rules: {
        flowCode: [{ required: true, message: '流程类型不能为空', trigger: 'blur' }],
        statusFrom: [{ required: true, message: '请选择源状态', trigger: 'change' }],
        actionCode: [{ required: true, message: '动作标识不能为空', trigger: 'blur' }],
        actionName: [{ required: true, message: '动作名称不能为空', trigger: 'blur' }],
        targetStatus: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
        roleKey: [{ required: true, message: '允许角色不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段（源状态下拉依赖 statusOptions，用 computed 保证响应式） */
    queryFormItems() {
      return [
        { prop: 'flowCode', label: '流程类型', placeholder: '如 HOTLINE', width: '150px' },
        { prop: 'statusFrom', label: '源状态', type: 'select', placeholder: '全部', width: '140px', options: this.statusOptions },
        { prop: 'actionCode', label: '动作', placeholder: '如 start', width: '140px' },
        { prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: '110px', options: [
          { label: '启用', value: '0' }, { label: '停用', value: '1' }
        ] }
      ]
    }
  },
  methods: {
    statusText(v) {
      const hit = this.statusOptions.find(s => s.value === String(v))
      return hit ? hit.label : v
    },
    getList() {
      this.loading = true
      const { pageNum, pageSize, ...filters } = this.queryParams
      listTicketFlow(filters).then(res => {
        this.allList = res.data || []
        this.applyLocalFilter()
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    // 后端按等值条件过滤；此处再做一次本地过滤以兼容空入参全量返回
    applyLocalFilter() {
      const q = this.queryParams
      this.filteredList = this.allList.filter(r => {
        if (q.flowCode && !(r.flowCode || '').toLowerCase().includes(q.flowCode.trim().toLowerCase())) return false
        if (q.statusFrom != null && q.statusFrom !== '' && r.statusFrom !== q.statusFrom) return false
        if (q.actionCode && !(r.actionCode || '').toLowerCase().includes(q.actionCode.trim().toLowerCase())) return false
        if (q.status != null && q.status !== '' && r.status !== q.status) return false
        return true
      })
      q.pageNum = 1
      this.paginate()
    },
    paginate() {
      const start = (this.queryParams.pageNum - 1) * this.queryParams.pageSize
      this.pagedList = this.filteredList.slice(start, start + this.queryParams.pageSize)
    },
    handleQuery() {
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里只需重新查询（本地过滤会重置页码）
      this.handleQuery()
    },
    reset() {
      this.form = {
        flowId: undefined,
        flowCode: 'HOTLINE',
        statusFrom: undefined,
        actionCode: undefined,
        actionName: undefined,
        targetStatus: undefined,
        roleKey: 'agent',
        sortNo: 0,
        slaHours: undefined,
        formSchema: undefined,
        status: '0'
      }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() {
      this.reset()
      this.isEdit = false
      this.open = true
      this.title = '新增流程规则'
    },
    handleUpdate(row) {
      this.reset()
      getTicketFlow(row.flowId).then(res => {
        this.form = res.data || {}
        this.isEdit = true
        this.open = true
        this.title = '编辑流程规则'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.formSchema) {
          try {
            JSON.parse(this.form.formSchema)
          } catch (e) {
            this.$modal.msgError('必填表单必须是合法 JSON')
            return
          }
        }
        const api = this.form.flowId != null ? updateTicketFlow : addTicketFlow
        api(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() {
      this.open = false
      this.reset()
    }
  },
  watch: {
    'queryParams.pageNum'() { this.paginate() },
    'queryParams.pageSize'() { this.queryParams.pageNum = 1; this.paginate() }
  }
}
</script>

<style scoped>
.sub-text { color: #909399; font-size: 12px; line-height: 1.2; }
.form-tip { margin-left: 10px; color: #909399; font-size: 12px; }
</style>
