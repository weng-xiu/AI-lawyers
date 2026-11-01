<template>
  <div class="app-container">
    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="transferList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :table-attrs="{ border: true }"
      label-width="90px"
      empty-text="暂无转办流水"
      empty-hint="发起转办后，可按工单号、方向与处理结果追踪跨机构流转进度"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button v-hasPermi="['lawyers:ticketTransfer:add']" type="primary" plain icon="el-icon-s-promotion" size="mini" @click="openTransfer">发起转办</el-button>
        <el-button type="warning" plain icon="el-icon-time" size="mini" @click="timelineOpen = true">跨渠道时间线</el-button>
      </template>
      <template #column-direction="{ row }">
        <el-tag size="mini" :type="row.direction === 'OUT' ? 'warning' : 'success'">{{ row.direction === 'OUT' ? '转出' : '转入' }}</el-tag>
      </template>
      <template #column-externalType="{ row }">{{ typeText(row.externalType) }}</template>
      <template #column-orgName="{ row }">{{ row.orgName || '-' }}</template>
      <template #column-externalTicketNo="{ row }">{{ row.externalTicketNo || '-' }}</template>
      <template #column-externalStatus="{ row }">
        <el-tag size="mini" :type="extStatusTag(row.externalStatus)">{{ row.externalStatus || '-' }}</el-tag>
      </template>
      <template #column-transferStatus="{ row }">
        <el-tag size="mini" :type="flowTag(row.transferStatus)">{{ flowText(row.transferStatus) }}</el-tag>
      </template>
      <template #column-transferTime="{ row }">{{ row.transferTime ? parseTime(row.transferTime) : '-' }}</template>
      <template #column-action="{ row }">
        <el-button type="text" size="mini" icon="el-icon-view" @click="showDetail(row)">详情</el-button>
        <el-button
          v-if="row.direction === 'OUT' && (row.transferStatus === '0' || row.transferStatus === '2')"
          v-hasPermi="['lawyers:ticketTransfer:retry']"
          type="text" size="mini" icon="el-icon-refresh-right" @click="handleRetry(row)">重试</el-button>
      </template>
    </ProTable>

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
        <el-button class="el-button--cta" size="small" :loading="submitLoading" @click="submitTransfer">确认转办</el-button>
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
      showSearch: true,
      queryParams: { pageNum: 1, pageSize: 10, ticketNo: undefined, direction: undefined, externalType: undefined, transferStatus: undefined },
      columns: [
        { prop: 'transferId', label: '流水ID', width: 80, align: 'center' },
        { prop: 'ticketNo', label: '工单号', width: 200, align: 'center', showOverflowTooltip: true },
        { prop: 'direction', label: '方向', width: 80, align: 'center', slot: true },
        { prop: 'externalType', label: '条线', width: 110, align: 'center', slot: true },
        { prop: 'orgName', label: '协同机构', minWidth: 180, align: 'center', showOverflowTooltip: true, slot: true },
        { prop: 'externalTicketNo', label: '外部工单号', width: 170, align: 'center', showOverflowTooltip: true, slot: true },
        { prop: 'externalStatus', label: '外部状态', width: 110, align: 'center', slot: true },
        { prop: 'transferStatus', label: '流水状态', width: 100, align: 'center', slot: true },
        { prop: 'retryCount', label: '重试', width: 70, align: 'center' },
        { prop: 'transferTime', label: '发起时间', width: 160, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 170, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    queryFormItems() {
      return [
        { prop: 'ticketNo', label: '工单号', placeholder: '请输入工单号', width: 180 },
        {
          prop: 'direction',
          label: '方向',
          type: 'select',
          placeholder: '全部',
          width: 110,
          options: [{ label: '转出', value: 'OUT' }, { label: '转入', value: 'IN' }]
        },
        { prop: 'externalType', label: '条线', type: 'select', placeholder: '全部', width: 150, options: this.typeOptions },
        {
          prop: 'transferStatus',
          label: '流水状态',
          type: 'select',
          placeholder: '全部',
          width: 120,
          options: [
            { label: '处理中', value: '0' },
            { label: '成功', value: '1' },
            { label: '失败', value: '2' },
            { label: '人工处理', value: '3' }
          ]
        }
      ]
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, ticketNo: undefined, direction: undefined, externalType: undefined, transferStatus: undefined }
      this.getList()
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认重新推送该转办单吗？',
        tip: '将再次推送至接收方系统，若对方已完成处理可能产生重复提醒（按转办单号幂等）'
      }).then(() => retryTransfer(row.transferId)).then(() => {
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
