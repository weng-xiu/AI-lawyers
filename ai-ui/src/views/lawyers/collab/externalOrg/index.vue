<template>
  <div class="app-container">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="orgList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :table-attrs="{ border: true }"
      has-selection
      label-width="80px"
      empty-text="暂无协同机构"
      empty-hint="新增法律援助、人民调解、公证、司法鉴定等外部机构后可发起转办"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button v-hasPermi="['lawyers:externalOrg:add']" type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增机构</el-button>
        <el-button v-hasPermi="['lawyers:externalOrg:remove']" type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete">删除</el-button>
      </template>
      <template #column-externalType="{ row }">
        <el-tag size="mini" :type="typeTag(row.externalType)">{{ typeText(row.externalType) }}</el-tag>
      </template>
      <template #column-region="{ row }">
        {{ [row.province, row.city, row.district].filter(Boolean).join(' / ') || '-' }}
      </template>
      <template #column-accessMode="{ row }">
        <el-tag size="mini" :type="row.accessMode === 'API' ? 'success' : 'info'">{{ row.accessMode }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-tag size="mini" :type="row.status === '0' ? 'success' : 'danger'">{{ row.status === '0' ? '启用' : '停用' }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button v-hasPermi="['lawyers:externalOrg:edit']" type="text" size="mini" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
        <el-button v-hasPermi="['lawyers:externalOrg:remove']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleDelete(row)">删除</el-button>
      </template>
    </ProTable>

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
      showSearch: true,
      total: 0,
      orgList: [],
      // V3：ProTable 列定义（条线/属地/对接方式/状态/操作走插槽）
      columns: [
        { prop: 'orgId', label: '机构ID', width: 80 },
        { prop: 'orgName', label: '机构名称', minWidth: 200, showOverflowTooltip: true },
        { prop: 'externalType', label: '条线类型', width: 120, slot: true },
        { prop: 'region', label: '属地', width: 170, slot: true },
        { prop: 'contactPhone', label: '联系电话', width: 140 },
        { prop: 'accessMode', label: '对接方式', width: 90, slot: true },
        { prop: 'serviceHours', label: '服务时间', width: 160, showOverflowTooltip: true },
        { prop: 'status', label: '状态', width: 80, slot: true },
        { prop: 'action', label: '操作', width: 150, slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    /** V3：ProTable 查询字段 */
    queryFormItems() {
      return [
        { prop: 'orgName', label: '机构名称', placeholder: '请输入机构名称', width: '180px' },
        { prop: 'externalType', label: '条线类型', type: 'select', placeholder: '全部', width: '160px', options: this.typeOptions },
        { prop: 'city', label: '地市', placeholder: '请输入地市', width: '140px' },
        { prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: '110px', options: [
          { label: '启用', value: '0' }, { label: '停用', value: '1' }
        ] }
      ]
    }
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, orgName: undefined, externalType: undefined, city: undefined, status: undefined }
      this.getList()
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
      this.$modal.dangerConfirm({
        content: '将删除选中的外部协作机构',
        count: ids.length,
        countNoun: '家机构',
        tip: '删除后历史转办流水仍保留'
      }).then(() => delExternalOrg(ids)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>
