<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="机构名称" prop="orgName">
          <el-input v-model="queryParams.orgName" placeholder="请输入机构名称" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="条线类型" prop="externalType">
          <el-select v-model="queryParams.externalType" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="地市" prop="city">
          <el-input v-model="queryParams.city" placeholder="请输入地市" clearable style="width: 140px" @keyup.enter.native="handleQuery" />
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
          <el-button v-hasPermi="['lawyers:externalOrg:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增机构</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['lawyers:externalOrg:remove']" type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="orgList" border size="small" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" align="center" />
        <el-table-column label="机构ID" align="center" prop="orgId" width="80" />
        <el-table-column label="机构名称" align="center" prop="orgName" min-width="200" show-overflow-tooltip />
        <el-table-column label="条线类型" align="center" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="typeTag(scope.row.externalType)">{{ typeText(scope.row.externalType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="属地" align="center" width="170">
          <template slot-scope="scope">{{ [scope.row.province, scope.row.city, scope.row.district].filter(Boolean).join(' / ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="联系电话" align="center" prop="contactPhone" width="140" />
        <el-table-column label="对接方式" align="center" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.accessMode === 'API' ? 'success' : 'info'">{{ scope.row.accessMode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务时间" align="center" prop="serviceHours" width="160" show-overflow-tooltip />
        <el-table-column label="状态" align="center" width="80">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'danger'">{{ scope.row.status === '0' ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['lawyers:externalOrg:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button v-hasPermi="['lawyers:externalOrg:remove']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="title" :visible.sync="open" width="680px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="机构名称" prop="orgName">
              <el-input v-model="form.orgName" placeholder="请输入机构名称" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="条线类型" prop="externalType">
              <el-select v-model="form.externalType" placeholder="请选择" style="width:100%">
                <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="省" prop="province"><el-input v-model="form.province" placeholder="如 广东省" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="市" prop="city"><el-input v-model="form.city" placeholder="如 广州市" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区县" prop="district"><el-input v-model="form.district" placeholder="如 越秀区" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="机构地址"><el-input v-model="form.address" placeholder="请输入机构地址" maxlength="200" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话"><el-input v-model="form.contactPhone" placeholder="公开联系电话" maxlength="30" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人"><el-input v-model="form.contactPerson" maxlength="30" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="对接方式" prop="accessMode">
              <el-radio-group v-model="form.accessMode">
                <el-radio label="API">API</el-radio>
                <el-radio label="FILE">文件</el-radio>
                <el-radio label="MANUAL">人工</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" active-value="0" inactive-value="1" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.accessMode === 'API'" :span="24">
            <el-form-item label="协同接口地址" prop="apiUrl">
              <el-input v-model="form.apiUrl" placeholder="https://example.gov.cn/api/ticket" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.accessMode === 'API'" :span="12">
            <el-form-item label="AppId"><el-input v-model="form.appId" maxlength="100" /></el-form-item>
          </el-col>
          <el-col v-if="form.accessMode === 'API'" :span="12">
            <el-form-item label="AppSecret">
              <el-input v-model="form.appSecret" type="password" show-password placeholder="留空表示不修改" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="服务时间"><el-input v-model="form.serviceHours" placeholder="如 工作日 9:00-17:30" maxlength="100" /></el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="申请材料">
              <el-input v-model="form.applyMaterials" type="textarea" :rows="2" placeholder="转介指引展示，如：身份证、经济困难证明……" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listExternalOrg, getExternalOrg, addExternalOrg, updateExternalOrg, delExternalOrg } from '@/api/lawyers/externalOrg'

export default {
  name: 'CollabExternalOrg',
  data() {
    return {
      loading: false,
      total: 0,
      orgList: [],
      open: false,
      title: '',
      ids: [],
      multiple: true,
      typeOptions: [
        { value: 'LEGAL_AID', label: '法律援助' },
        { value: 'MEDIATION', label: '人民调解' },
        { value: 'NOTARY', label: '公证' },
        { value: 'FORENSIC', label: '司法鉴定' },
        { value: 'ARBITRATION', label: '仲裁' },
        { value: 'HOTLINE_12345', label: '12345政务热线' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, orgName: undefined, externalType: undefined, city: undefined, status: undefined },
      form: {},
      rules: {
        orgName: [{ required: true, message: '机构名称不能为空', trigger: 'blur' }],
        externalType: [{ required: true, message: '请选择条线类型', trigger: 'change' }],
        apiUrl: [{ required: true, message: 'API 对接必须填写接口地址', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    typeText(v) {
      const hit = this.typeOptions.find(i => i.value === v)
      return hit ? hit.label : (v || '-')
    },
    typeTag(v) {
      const map = { LEGAL_AID: '', MEDIATION: 'success', NOTARY: 'warning', FORENSIC: 'danger', ARBITRATION: 'info', HOTLINE_12345: 'success' }
      return map[v] || 'info'
    },
    getList() {
      this.loading = true
      listExternalOrg(this.queryParams).then(res => {
        this.orgList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, orgName: undefined, externalType: undefined, city: undefined, status: undefined }
      this.handleQuery()
    },
    reset() {
      this.form = { orgId: undefined, orgName: undefined, externalType: undefined, province: undefined, city: undefined, district: undefined, address: undefined, contactPhone: undefined, contactPerson: undefined, accessMode: 'MANUAL', apiUrl: undefined, appId: undefined, appSecret: undefined, serviceHours: undefined, applyMaterials: undefined, status: '0', remark: undefined }
      this.$nextTick(() => { this.$refs.form && this.$refs.form.clearValidate() })
    },
    handleAdd() { this.reset(); this.open = true; this.title = '新增协同机构' },
    handleUpdate(row) {
      this.reset()
      getExternalOrg(row.orgId).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '编辑协同机构'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const api = this.form.orgId != null ? updateExternalOrg : addExternalOrg
        api(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.open = false
          this.getList()
        })
      })
    },
    cancel() { this.open = false; this.reset() },
    handleSelectionChange(sel) { this.ids = sel.map(i => i.orgId); this.multiple = !sel.length },
    handleDelete(row) {
      const ids = row.orgId ? [row.orgId] : this.ids
      this.$modal.confirm('确认删除选中的 ' + ids.length + ' 家机构吗？删除后历史转办流水仍保留。').then(() => delExternalOrg(ids)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>
