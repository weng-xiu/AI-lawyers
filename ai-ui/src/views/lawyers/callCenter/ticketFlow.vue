<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="流程类型" prop="flowCode">
          <el-input v-model="queryParams.flowCode" placeholder="如 HOTLINE" clearable style="width: 150px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="源状态" prop="statusFrom">
          <el-select v-model="queryParams.statusFrom" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作" prop="actionCode">
          <el-input v-model="queryParams.actionCode" placeholder="如 start" clearable style="width: 140px" @keyup.enter.native="handleQuery" />
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
          <el-button v-hasPermi="['lawyers:ticketFlow:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="pagedList" border size="small">
        <el-table-column label="ID" align="center" prop="flowId" width="70" />
        <el-table-column label="流程类型" align="center" prop="flowCode" width="110" />
        <el-table-column label="源状态" align="center" width="100">
          <template slot-scope="scope">{{ statusText(scope.row.statusFrom) }}</template>
        </el-table-column>
        <el-table-column label="动作" align="center" width="110">
          <template slot-scope="scope">
            <div>{{ scope.row.actionCode }}</div>
            <div class="sub-text">{{ scope.row.actionName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="目标状态" align="center" width="100">
          <template slot-scope="scope">{{ statusText(scope.row.targetStatus) }}</template>
        </el-table-column>
        <el-table-column label="允许角色" align="center" prop="roleKey" min-width="140" show-overflow-tooltip />
        <el-table-column label="排序" align="center" prop="sortNo" width="70" />
        <el-table-column label="SLA(小时)" align="center" width="90">
          <template slot-scope="scope">
            <span v-if="scope.row.slaHours == null">—</span>
            <span v-else-if="scope.row.slaHours === 0">清时限</span>
            <span v-else>{{ scope.row.slaHours }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="90">
          <template slot-scope="scope">
            <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="mini">
              {{ scope.row.status === '0' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="120" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['lawyers:ticketFlow:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="filteredList.length > 0"
        :total="filteredList.length"
        :page.sync="pageNum"
        :limit.sync="pageSize"
      />
    </el-card>

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
      allList: [],
      filteredList: [],
      pagedList: [],
      pageNum: 1,
      pageSize: 10,
      open: false,
      title: '',
      isEdit: false,
      queryParams: {
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
  methods: {
    statusText(v) {
      const hit = this.statusOptions.find(s => s.value === String(v))
      return hit ? hit.label : v
    },
    getList() {
      this.loading = true
      listTicketFlow(this.queryParams).then(res => {
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
      this.pageNum = 1
      this.paginate()
    },
    paginate() {
      const start = (this.pageNum - 1) * this.pageSize
      this.pagedList = this.filteredList.slice(start, start + this.pageSize)
    },
    handleQuery() {
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { flowCode: undefined, statusFrom: undefined, actionCode: undefined, status: undefined }
      this.getList()
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
    pageNum() { this.paginate() },
    pageSize() { this.pageNum = 1; this.paginate() }
  }
}
</script>

<style scoped>
.sub-text { color: #909399; font-size: 12px; line-height: 1.2; }
.form-tip { margin-left: 10px; color: #909399; font-size: 12px; }
</style>
