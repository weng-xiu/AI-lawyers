<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="90px">
        <el-form-item label="策略名称" prop="policyName">
          <el-input v-model="queryParams.policyName" placeholder="请输入策略名称" clearable style="width: 170px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="queryParams.bizType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in bizOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 110px">
            <el-option label="启用" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button v-hasPermi="['lawyers:slaPolicy:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增策略</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['lawyers:slaPolicy:remove']" type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="policyList" border size="small" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column label="ID" align="center" prop="policyId" width="70" />
        <el-table-column label="策略名称" align="center" prop="policyName" min-width="180" show-overflow-tooltip />
        <el-table-column label="业务类型" align="center" width="130">
          <template slot-scope="scope">{{ bizText(scope.row.bizType) }}</template>
        </el-table-column>
        <el-table-column label="优先级" align="center" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.priority === '1' ? 'danger' : (scope.row.priority === '3' ? 'info' : '')">
              {{ priorityText(scope.row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="响应时限" align="center" width="110">
          <template slot-scope="scope">{{ scope.row.respondMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="办结时限" align="center" width="110">
          <template slot-scope="scope">{{ scope.row.resolveMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="预警阈值" align="center" width="100">
          <template slot-scope="scope">{{ scope.row.warnThreshold }}%</template>
        </el-table-column>
        <el-table-column label="逐级升级角色链" align="center" prop="escalateRoles" min-width="200" show-overflow-tooltip>
          <template slot-scope="scope">
            <el-tag v-for="(r, i) in (scope.row.escalateRoles || '').split(',').filter(Boolean)" :key="i" size="mini" style="margin-right:4px">{{ r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="80">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'danger'">{{ scope.row.status === '0' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['lawyers:slaPolicy:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['lawyers:slaPolicy:remove']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </el-card>

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
      total: 0,
      policyList: [],
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
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, policyName: undefined, bizType: undefined, status: undefined }
      this.handleQuery()
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
      this.$modal.confirm('确认删除选中的 ' + ids.length + ' 条 SLA 策略吗？').then(() => delSlaPolicy(ids)).then(() => {
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
