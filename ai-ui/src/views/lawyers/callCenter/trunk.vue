<template>
  <div class="trunk-container">
    <!-- V3：查询区/工具条/表格骨架收敛到 ProTable（本页无分页） -->
    <ProTable
      ref="proTable"
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="trunkList"
      :loading="loading"
      :total="trunkList.length"
      :show-search.sync="showSearch"
      label-width="80px"
      :table-attrs="{ border: true, size: 'small' }"
      hide-pagination
      @query="handleQuery"
      @reset="resetQuery"
    >
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增线路</el-button>
        <el-button type="success" plain icon="el-icon-refresh" size="mini" @click="refreshSegment">刷新号段缓存</el-button>
      </template>
      <template #column-carrier="{ row }">
        <el-tag :type="carrierTagType(row.carrier)" size="mini">{{ carrierLabel(row.carrier) }}</el-tag>
      </template>
      <template #column-isPrimary="{ row }">
        <el-tag :type="row.isPrimary === '1' ? 'warning' : 'info'" size="mini">{{ row.isPrimary === '1' ? '主用' : '备用' }}</el-tag>
      </template>
      <template #column-healthStatus="{ row }">
        <el-tag :type="healthTagType(row.healthStatus)" size="mini">{{ healthLabel(row.healthStatus) }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <el-switch v-model="row.status" active-value="0" inactive-value="1" @change="handleStatusChange(row)" />
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)">编辑</el-button>
        <el-button size="mini" type="text" icon="el-icon-camera" @click="handleTest(row)">测试</el-button>
        <el-button size="mini" type="text" icon="el-icon-phone" @click="handleCallTest(row)">拨测</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)">删除</el-button>
      </template>
    </ProTable>

    <!-- 线路编辑对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="640px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="线路编码" prop="trunkCode">
              <el-input v-model="form.trunkCode" placeholder="如 CM-SIP-01" :disabled="form.trunkId != null" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线路名称" prop="trunkName">
              <el-input v-model="form.trunkName" placeholder="请输入线路名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="运营商" prop="carrier">
              <el-select v-model="form.carrier" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in carrierOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="线路类型" prop="trunkType">
              <el-select v-model="form.trunkType" placeholder="请选择" style="width: 100%">
                <el-option label="SIP" value="SIP" />
                <el-option label="PSTN" value="PSTN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网关地址" prop="gatewayHost">
              <el-input v-model="form.gatewayHost" placeholder="IP 或域名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网关端口" prop="gatewayPort">
              <el-input v-model="form.gatewayPort" placeholder="如 8021" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大并发" prop="maxConcurrent">
              <el-input-number v-model="form.maxConcurrent" :min="1" :max="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :min="0" :max="999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权重" prop="weight">
              <el-input-number v-model="form.weight" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主备" prop="isPrimary">
              <el-select v-model="form.isPrimary" placeholder="请选择" style="width: 100%">
                <el-option label="主用" value="1" />
                <el-option label="备用" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="网关厂商" prop="gatewayVendor">
              <el-select v-model="form.gatewayVendor" placeholder="请选择" style="width: 100%">
                <el-option label="FreeSWITCH" value="FREESWITCH" />
                <el-option label="Asterisk" value="ASTERISK" />
                <el-option label="HTTP OpenAPI" value="HTTP" />
                <el-option label="模拟网关" value="SIMULATOR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主叫显号" prop="callerDisplay">
              <el-input v-model="form.callerDisplay" placeholder="外呼显号" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
        <el-button size="small" @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 号码识别测试 -->
    <el-card shadow="never" class="test-card">
      <div slot="header" class="clearfix">
        <span>号码归属识别测试</span>
      </div>
      <el-input v-model="testNumber" placeholder="输入被叫号码，自动识别归属运营商并推荐线路" style="width: 320px" />
      <el-button type="primary" size="small" icon="el-icon-search" @click="handleRecognize" style="margin-left: 10px">识别</el-button>
      <div v-if="recognizeResult" class="recognize-result">
        <el-tag :type="carrierTagType(recognizeResult.carrier)" size="small">{{ carrierLabel(recognizeResult.carrier) }}</el-tag>
        <span class="result-text">推荐线路：{{ recognizeResult.trunkName || '无可用线路' }}</span>
      </div>
    </el-card>

    <!-- 线路拨测 -->
    <el-dialog title="线路拨测" :visible.sync="callTestOpen" width="480px" append-to-body>
      <el-form label-width="90px" size="small">
        <el-form-item label="线路">
          <span>{{ callTestForm.trunkName }}</span>
        </el-form-item>
        <el-form-item label="拨测号码">
          <el-input v-model="callTestForm.calleeNumber" placeholder="请输入被叫号码，如 13800138000" clearable />
        </el-form-item>
        <div v-if="callTestResult" class="call-test-result">
          <el-alert
            :title="callTestResult.success ? '拨测已下发' : '拨测失败'"
            :type="callTestResult.success ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <p v-if="callTestResult.callUuid">呼叫ID：{{ callTestResult.callUuid }}</p>
          <p v-if="callTestResult.dialStatus">拨号状态：{{ callTestResult.dialStatus }}</p>
          <p v-if="callTestResult.message">信息：{{ callTestResult.message }}</p>
        </div>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="callTestOpen = false">关闭</el-button>
        <el-button type="primary" size="small" :loading="callTestLoading" @click="submitCallTest">开始拨测</el-button>
      </div>
    </el-dialog>

    <!-- 线路连通性测试结果 -->
    <el-dialog title="线路连通性测试" :visible.sync="healthOpen" width="480px" append-to-body>
      <el-form label-width="100px" size="small">
        <el-form-item label="可达">
          <el-tag :type="healthResult && healthResult.reachable ? 'success' : 'danger'">{{ healthResult && healthResult.reachable ? '是' : '否' }}</el-tag>
        </el-form-item>
        <el-form-item label="延迟"><span>{{ healthResult ? healthResult.latencyMs : '-' }} ms</span></el-form-item>
        <el-form-item label="注册状态"><span>{{ healthResult ? healthResult.registerState : '-' }}</span></el-form-item>
        <el-form-item label="说明"><span>{{ healthResult ? healthResult.message : '-' }}</span></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="healthOpen = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listTrunk, addTrunk, updateTrunk, changeTrunkStatus, delTrunk, testTrunk, testCallTrunk,
  recognizeNumber, refreshSegment
} from '@/api/lawyers/trunk'

export default {
  name: 'TrunkManage',
  data() {
    return {
      loading: false,
      showSearch: true,
      open: false,
      title: '',
      trunkList: [],
      // V3：ProTable 列定义（运营商/主备/健康状态/启用/操作走插槽，纯文本列用 formatter）
      columns: [
        { prop: 'trunkCode', label: '线路编码', width: 130 },
        { prop: 'trunkName', label: '线路名称', minWidth: 140 },
        { prop: 'carrier', label: '运营商', width: 90, slot: true },
        { prop: 'trunkType', label: '线路类型', width: 90, formatter: (row) => row.trunkType === 'SIP' ? 'SIP' : row.trunkType === 'PSTN' ? 'PSTN' : row.trunkType },
        { prop: 'gatewayHost', label: '网关地址', width: 140 },
        { prop: 'isPrimary', label: '主用/备用', width: 80, slot: true },
        { prop: 'currentConcurrent', label: '并发', width: 110, formatter: (row) => row.currentConcurrent + '/' + row.maxConcurrent },
        { prop: 'healthStatus', label: '健康状态', width: 90, slot: true },
        { prop: 'successRate', label: '接通率', width: 90, formatter: (row) => row.successRate != null ? (row.successRate * 100).toFixed(1) + '%' : '-' },
        { prop: 'status', label: '启用', width: 70, slot: true },
        { prop: 'action', label: '操作', width: 200, fixed: 'right', slot: true }
      ],
      testNumber: '',
      recognizeResult: null,
      callTestOpen: false,
      callTestLoading: false,
      callTestForm: { trunkId: null, trunkName: '', calleeNumber: '' },
      callTestResult: null,
      healthOpen: false,
      healthResult: null,
      queryParams: {
        trunkName: undefined,
        carrier: undefined,
        healthStatus: undefined
      },
      carrierOptions: [
        { value: 'CM', label: '中国移动' },
        { value: 'CU', label: '中国联通' },
        { value: 'CT', label: '中国电信' },
        { value: 'CB', label: '中国广电' },
        { value: 'VI', label: '虚拟运营商' },
        { value: '00', label: '未知' }
      ],
      form: {
        trunkId: undefined, trunkCode: undefined, trunkName: undefined,
        carrier: 'CM', trunkType: 'SIP', gatewayHost: undefined, gatewayPort: undefined,
        maxConcurrent: 30, priority: 100, weight: 100, isPrimary: '1',
        gatewayVendor: 'FREESWITCH', callerDisplay: undefined
      },
      rules: {
        trunkCode: [{ required: true, message: '线路编码不能为空', trigger: 'blur' }],
        trunkName: [{ required: true, message: '线路名称不能为空', trigger: 'blur' }],
        carrier: [{ required: true, message: '请选择运营商', trigger: 'change' }],
        trunkType: [{ required: true, message: '请选择线路类型', trigger: 'change' }],
        gatewayHost: [{ required: true, message: '网关地址不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段（运营商下拉依赖 carrierOptions，用 computed 保证响应式） */
    queryFormItems() {
      return [
        { prop: 'trunkName', label: '线路名称', placeholder: '请输入线路名称', width: '160px' },
        { prop: 'carrier', label: '运营商', type: 'select', placeholder: '请选择', width: '140px', options: this.carrierOptions },
        { prop: 'healthStatus', label: '健康状态', type: 'select', placeholder: '请选择', width: '140px', options: [
          { label: '正常', value: '1' }, { label: '亚健康', value: '2' },
          { label: '故障', value: '3' }, { label: '熔断', value: '4' }
        ] }
      ]
    }
  },
  methods: {
    getList() {
      this.loading = true
      listTrunk(this.queryParams).then(res => {
        this.trunkList = (res.rows || res.data || []).map(t => ({ ...t, status: t.status || '0' }))
      }).finally(() => {
        this.loading = false
      })
    },
    handleQuery() {
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里只需重新查询
      this.handleQuery()
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增线路'
    },
    handleUpdate(row) {
      this.reset()
      this.form = { ...row }
      this.open = true
      this.title = '编辑线路'
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const api = this.form.trunkId != null ? updateTrunk : addTrunk
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
    },
    reset() {
      this.form = {
        trunkId: undefined, trunkCode: undefined, trunkName: undefined,
        carrier: 'CM', trunkType: 'SIP', gatewayHost: undefined, gatewayPort: undefined,
        maxConcurrent: 30, priority: 100, weight: 100, isPrimary: '1',
        gatewayVendor: 'FREESWITCH', callerDisplay: undefined
      }
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    handleStatusChange(row) {
      const oldStatus = row.status === '0' ? '1' : '0'
      const text = row.status === '0' ? '启用' : '停用'
      changeTrunkStatus({ trunkId: row.trunkId, status: row.status }).then(() => {
        // U2：30 秒内可撤销；失败立即回退开关，避免界面与库状态不一致
        this.$modal.undo({
          message: '线路已' + text,
          undoSuccessText: '已撤销，线路已' + (oldStatus === '0' ? '启用' : '停用'),
          onUndo: () => changeTrunkStatus({ trunkId: row.trunkId, status: oldStatus }).then(() => {
            row.status = oldStatus
          })
        })
      }).catch(() => {
        row.status = oldStatus
        this.$modal.msgError('状态更新失败，已恢复原状态')
      })
    },
    handleTest(row) {
      testTrunk(row.trunkId).then(res => {
        this.healthResult = res
        this.healthOpen = true
      })
    },
    handleCallTest(row) {
      this.callTestForm = { trunkId: row.trunkId, trunkName: row.trunkName, calleeNumber: '' }
      this.callTestResult = null
      this.callTestOpen = true
    },
    submitCallTest() {
      if (!this.callTestForm.calleeNumber) {
        this.$modal.msgWarning('请输入拨测号码')
        return
      }
      this.callTestLoading = true
      testCallTrunk(this.callTestForm.trunkId, this.callTestForm.calleeNumber).then(res => {
        this.callTestResult = res
        this.callTestLoading = false
      }).catch(() => {
        this.callTestLoading = false
      })
    },
    handleDelete(row) {
      this.$modal.dangerConfirm({
        content: '将删除中继线路「' + row.trunkName + '」',
        count: 1,
        countNoun: '条线路',
        tip: '删除后该线路将无法继续接入/拨出呼叫，请确认已无在用话务'
      }).then(() => {
        return delTrunk(row.trunkId)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    refreshSegment() {
      refreshSegment().then(() => {
        this.$modal.msgSuccess('号段缓存已刷新')
      })
    },
    handleRecognize() {
      if (!this.testNumber) {
        this.$modal.msgWarning('请输入号码')
        return
      }
      recognizeNumber(this.testNumber).then(res => {
        this.recognizeResult = res.data || null
      })
    },
    carrierLabel(code) {
      const map = { CM: '中国移动', CU: '中国联通', CT: '中国电信', CB: '中国广电', VI: '虚拟运营商', '00': '未知' }
      return map[code] || code
    },
    carrierTagType(code) {
      const map = { CM: 'success', CU: 'warning', CT: 'danger', CB: 'info', VI: 'info', '00': '' }
      return map[code] || ''
    },
    healthLabel(s) {
      const map = { 1: '正常', 2: '亚健康', 3: '故障', 4: '熔断' }
      return map[s] || '未知'
    },
    healthTagType(s) {
      const map = { 1: 'success', 2: 'warning', 3: 'danger', 4: 'info' }
      return map[s] || ''
    }
  }
}
</script>

<style scoped>
.trunk-container { padding: 24px; }
.test-card { margin-top: 16px; }
.recognize-result { margin-top: 16px; display: flex; align-items: center; gap: 12px; }
.result-text { color: #5A6A7E; font-size: 13px; }
.call-test-result { margin-top: 12px; }
.call-test-result p { margin: 8px 0 0; font-size: 13px; color: #5A6A7E; }
</style>
