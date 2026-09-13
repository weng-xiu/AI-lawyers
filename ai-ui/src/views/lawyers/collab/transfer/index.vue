<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="90px">
        <el-form-item label="工单号" prop="ticketNo">
          <el-input v-model="queryParams.ticketNo" placeholder="请输入工单号" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 110px">
            <el-option label="转出" value="OUT" />
            <el-option label="转入" value="IN" />
          </el-select>
        </el-form-item>
        <el-form-item label="条线" prop="externalType">
          <el-select v-model="queryParams.externalType" placeholder="全部" clearable style="width: 150px">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流水状态" prop="transferStatus">
          <el-select v-model="queryParams.transferStatus" placeholder="全部" clearable style="width: 120px">
            <el-option label="处理中" value="0" />
            <el-option label="成功" value="1" />
            <el-option label="失败" value="2" />
            <el-option label="人工处理" value="3" />
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
          <el-button v-hasPermi="['lawyers:ticketTransfer:add']" type="primary" plain icon="el-icon-s-promotion" size="mini" @click="openTransfer">发起转办</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="el-icon-time" size="mini" @click="timelineOpen = true">跨渠道时间线</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="transferList" border size="small">
        <el-table-column label="流水ID" align="center" prop="transferId" width="80" />
        <el-table-column label="工单号" align="center" prop="ticketNo" width="200" show-overflow-tooltip />
        <el-table-column label="方向" align="center" width="80">
          <template slot-scope="scope">
            <el-tag size="mini" :type="scope.row.direction === 'OUT' ? 'warning' : 'success'">{{ scope.row.direction === 'OUT' ? '转出' : '转入' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="条线" align="center" width="110">
          <template slot-scope="scope">{{ typeText(scope.row.externalType) }}</template>
        </el-table-column>
        <el-table-column label="协同机构" align="center" prop="orgName" min-width="180" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.orgName || '-' }}</template>
        </el-table-column>
        <el-table-column label="外部工单号" align="center" prop="externalTicketNo" width="170" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.externalTicketNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="外部状态" align="center" width="110">
          <template slot-scope="scope">
            <el-tag size="mini" :type="extStatusTag(scope.row.externalStatus)">{{ scope.row.externalStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="流水状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="flowTag(scope.row.transferStatus)">{{ flowText(scope.row.transferStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="重试" align="center" prop="retryCount" width="70" />
        <el-table-column label="发起时间" align="center" prop="transferTime" width="160">
          <template slot-scope="scope">{{ scope.row.transferTime ? parseTime(scope.row.transferTime) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="170" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-view" @click="showDetail(scope.row)">详情</el-button>
            <el-button
              v-if="scope.row.direction === 'OUT' && (scope.row.transferStatus === '0' || scope.row.transferStatus === '2')"
              v-hasPermi="['lawyers:ticketTransfer:retry']"
              type="text" size="mini" icon="el-icon-refresh-right" @click="handleRetry(scope.row)">重试</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 发起转办 -->
    <el-dialog title="发起工单转办" :visible.sync="transferOpen" width="520px" append-to-body>
      <el-form ref="transferForm" :model="transferForm" :rules="transferRules" label-width="100px" size="small">
        <el-form-item label="工单ID" prop="ticketId">
          <el-input-number v-model="transferForm.ticketId" :min="1" controls-position="right" placeholder="工单ID" style="width:100%" />
        </el-form-item>
        <el-form-item label="协同机构" prop="orgId">
          <el-select v-model="transferForm.orgId" filterable placeholder="请选择机构" style="width:100%" @change="onOrgChange">
            <el-option v-for="o in orgOptions" :key="o.orgId" :label="typeText(o.externalType) + '｜' + o.orgName + '（' + (o.city || '') + '）'" :value="o.orgId" />
          </el-select>
        </el-form-item>
        <el-form-item label="转办备注">
          <el-input v-model="transferForm.remark" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="最小必要信息，勿填写身份证号等敏感信息" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" size="small" :loading="submitLoading" @click="submitTransfer">确认转办</el-button>
        <el-button size="small" @click="transferOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog title="转办流水详情" :visible.sync="detailOpen" width="720px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="流水ID">{{ detail.transferId }}</el-descriptions-item>
        <el-descriptions-item label="幂等键">{{ detail.idempotentKey }}</el-descriptions-item>
        <el-descriptions-item label="工单号">{{ detail.ticketNo }}</el-descriptions-item>
        <el-descriptions-item label="方向">{{ detail.direction === 'OUT' ? '转出' : '转入' }}</el-descriptions-item>
        <el-descriptions-item label="协同机构">{{ detail.orgName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="外部工单号">{{ detail.externalTicketNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="外部状态">{{ detail.externalStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="流水状态">{{ flowText(detail.transferStatus) }}</el-descriptions-item>
        <el-descriptions-item label="失败原因" :span="2">{{ detail.failReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次重试">{{ detail.nextRetryTime ? parseTime(detail.nextRetryTime) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="回调时间">{{ detail.callbackTime ? parseTime(detail.callbackTime) : '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs v-if="detail" style="margin-top:10px">
        <el-tab-pane label="请求报文">
          <pre class="payload-box">{{ formatPayload(detail.requestPayload) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="回调报文">
          <pre class="payload-box">{{ formatPayload(detail.callbackPayload) }}</pre>
        </el-tab-pane>
      </el-tabs>
      <div slot="footer"><el-button size="small" @click="detailOpen = false">关 闭</el-button></div>
    </el-dialog>

    <!-- 跨渠道时间线 -->
    <el-dialog title="跨渠道咨询时间线（F6）" :visible.sync="timelineOpen" width="720px" append-to-body>
      <el-form :inline="true" size="small">
        <el-form-item label="手机号">
          <el-input v-model="timelineQuery.callerNumber" placeholder="来电手机号" clearable style="width:180px" />
        </el-form-item>
        <el-form-item label="档案ID">
          <el-input-number v-model="timelineQuery.profileId" :min="1" controls-position="right" style="width:150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="mini" icon="el-icon-search" @click="loadTimeline">查询</el-button>
        </el-form-item>
      </el-form>
      <el-timeline>
        <el-timeline-item v-for="s in timeline" :key="s.sessionId" :timestamp="(s.startTime ? parseTime(s.startTime) : '') + (s.endTime ? ' ~ ' + parseTime(s.endTime) : '')" placement="top" :type="bizColor(s.bizType)">
          <el-card shadow="never">
            <div><el-tag size="mini">{{ channelText(s.channelType) }}</el-tag>
              <el-tag size="mini" type="info" style="margin-left:6px">{{ bizText(s.bizType) }}</el-tag>
              <span style="margin-left:8px">{{ s.bizTitle || s.bizId }}</span>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!timeline.length" description="暂无会话记录" />
      <div slot="footer"><el-button size="small" @click="timelineOpen = false">关 闭</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { listTransfer, getTransfer, createTransfer, retryTransfer, callerTimeline } from '@/api/lawyers/ticketTransfer'
import { listExternalOrg } from '@/api/lawyers/externalOrg'

export default {
  name: 'CollabTicketTransfer',
  data() {
    return {
      loading: false,
      total: 0,
      transferList: [],
      queryParams: { pageNum: 1, pageSize: 10, ticketNo: undefined, direction: undefined, externalType: undefined, transferStatus: undefined },
      typeOptions: [
        { value: 'LEGAL_AID', label: '法律援助' },
        { value: 'MEDIATION', label: '人民调解' },
        { value: 'NOTARY', label: '公证' },
        { value: 'FORENSIC', label: '司法鉴定' },
        { value: 'ARBITRATION', label: '仲裁' },
        { value: 'HOTLINE_12345', label: '12345政务热线' }
      ],
      channelOptions: [
        { value: 'PHONE', label: '电话' }, { value: 'WECHAT_MP', label: '微信公众号' },
        { value: 'WECHAT_MINI', label: '微信小程序' }, { value: 'H5', label: 'H5' }, { value: 'WEB', label: '网页' }
      ],
      transferOpen: false,
      submitLoading: false,
      transferForm: { ticketId: undefined, orgId: undefined, remark: undefined },
      transferRules: {
        ticketId: [{ required: true, message: '请输入工单ID', trigger: 'blur' }],
        orgId: [{ required: true, message: '请选择协同机构', trigger: 'change' }]
      },
      orgOptions: [],
      detailOpen: false,
      detail: null,
      timelineOpen: false,
      timelineQuery: { callerNumber: undefined, profileId: undefined },
      timeline: []
    }
  },
  created() { this.getList() },
  methods: {
    typeText(v) { const hit = this.typeOptions.find(i => i.value === v); return hit ? hit.label : (v || '-') },
    channelText(v) { const hit = this.channelOptions.find(i => i.value === v); return hit ? hit.label : (v || '-') },
    bizText(v) { const map = { CALL: '电话咨询', CHAT: '图文咨询', VIDEO: '视频咨询', IVR: 'IVR', MESSAGE: '留言', TICKET: '工单' }; return map[v] || v || '-' },
    bizColor(v) { const map = { CALL: 'primary', CHAT: 'success', VIDEO: 'warning', IVR: 'info', MESSAGE: 'info', TICKET: 'danger' }; return map[v] || 'primary' },
    flowText(s) { return { '0': '处理中', '1': '成功', '2': '失败', '3': '人工处理' }[String(s)] || '未知' },
    flowTag(s) { return { '0': 'warning', '1': 'success', '2': 'danger', '3': 'info' }[String(s)] || 'info' },
    extStatusTag(s) {
      return { PENDING: 'info', ACCEPTED: '', PROCESSING: 'warning', DONE: 'success', REJECTED: 'danger', FAILED: 'danger' }[s] || 'info'
    },
    getList() {
      this.loading = true
      listTransfer(this.queryParams).then(res => {
        this.transferList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, ticketNo: undefined, direction: undefined, externalType: undefined, transferStatus: undefined }
      this.handleQuery()
    },
    openTransfer() {
      this.transferForm = { ticketId: undefined, orgId: undefined, remark: undefined }
      this.$nextTick(() => { this.$refs.transferForm && this.$refs.transferForm.clearValidate() })
      if (!this.orgOptions.length) {
        listExternalOrg({ pageNum: 1, pageSize: 200, status: '0' }).then(res => { this.orgOptions = res.rows || [] })
      }
      this.transferOpen = true
    },
    onOrgChange() {},
    submitTransfer() {
      this.$refs.transferForm.validate(valid => {
        if (!valid) return
        this.submitLoading = true
        createTransfer(this.transferForm).then(res => {
          this.$modal.msgSuccess(res.msg || '转办已发起')
          this.transferOpen = false
          this.getList()
        }).finally(() => { this.submitLoading = false })
      })
    },
    showDetail(row) {
      getTransfer(row.transferId).then(res => { this.detail = res.data; this.detailOpen = true })
    },
    formatPayload(p) {
      if (!p) return '无'
      try { return JSON.stringify(JSON.parse(p), null, 2) } catch (e) { return p }
    },
    handleRetry(row) {
      this.$modal.confirm('确认重新推送该转办单吗？').then(() => retryTransfer(row.transferId)).then(() => {
        this.$modal.msgSuccess('已重新推送')
        this.getList()
      }).catch(() => {})
    },
    loadTimeline() {
      if (!this.timelineQuery.callerNumber && !this.timelineQuery.profileId) {
        this.$modal.msgWarning('请输入手机号或档案ID')
        return
      }
      callerTimeline(this.timelineQuery).then(res => { this.timeline = res.data || [] })
    }
  }
}
</script>

<style scoped>
.payload-box {
  max-height: 260px;
  overflow: auto;
  margin: 0;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
