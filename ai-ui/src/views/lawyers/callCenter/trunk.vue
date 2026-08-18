<template>
  <div class="trunk-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="线路名称" prop="trunkName">
          <el-input v-model="queryParams.trunkName" placeholder="请输入线路名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="运营商" prop="carrier">
          <el-select v-model="queryParams.carrier" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="item in carrierOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="健康状态" prop="healthStatus">
          <el-select v-model="queryParams.healthStatus" placeholder="请选择" clearable style="width: 140px">
            <el-option label="正常" value="1" />
            <el-option label="亚健康" value="2" />
            <el-option label="故障" value="3" />
            <el-option label="熔断" value="4" />
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
          <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增线路</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="el-icon-refresh" size="mini" @click="refreshSegment">刷新号段缓存</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="trunkList" size="small" border>
        <el-table-column label="线路编码" prop="trunkCode" width="130" />
        <el-table-column label="线路名称" prop="trunkName" min-width="140" />
        <el-table-column label="运营商" prop="carrier" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="carrierTagType(scope.row.carrier)" size="mini">{{ carrierLabel(scope.row.carrier) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="线路类型" prop="trunkType" width="90" align="center">
          <template slot-scope="scope">{{ scope.row.trunkType === 'SIP' ? 'SIP' : scope.row.trunkType === 'PSTN' ? 'PSTN' : scope.row.trunkType }}</template>
        </el-table-column>
        <el-table-column label="网关地址" prop="gatewayHost" width="140" />
        <el-table-column label="主用/备用" prop="isPrimary" width="80" align="center">
          <template slot-scope="scope">
            <el-tag :type="scope.row.isPrimary === '1' ? 'warning' : 'info'" size="mini">{{ scope.row.isPrimary === '1' ? '主用' : '备用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="并发" width="110" align="center">
          <template slot-scope="scope">{{ scope.row.currentConcurrent }}/{{ scope.row.maxConcurrent }}</template>
        </el-table-column>
        <el-table-column label="健康状态" prop="healthStatus" width="90" align="center">
          <template slot-scope="scope">
            <el-tag :type="healthTagType(scope.row.healthStatus)" size="mini">{{ healthLabel(scope.row.healthStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接通率" width="90" align="center">
          <template slot-scope="scope">{{ scope.row.successRate != null ? (scope.row.successRate * 100).toFixed(1) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="启用" prop="status" width="70" align="center">
          <template slot-scope="scope">
            <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" @change="handleStatusChange(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template slot-scope="scope">
            <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(scope.row)">编辑</el-button>
            <el-button size="mini" type="text" icon="el-icon-camera" @click="handleTest(scope.row)">测试</el-button>
            <el-button size="mini" type="text" icon="el-icon-phone" @click="handleCallTest(scope.row)">拨测</el-button>
            <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
      open: false,
      title: '',
      trunkList: [],
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
      this.resetForm('queryForm')
      this.queryParams = { trunkName: undefined, carrier: undefined, healthStatus: undefined }
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
      changeTrunkStatus({ trunkId: row.trunkId, status: row.status }).then(() => {
        this.$modal.msgSuccess('状态已更新')
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
      this.$modal.confirm('确认删除线路「' + row.trunkName + '」？').then(() => {
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
