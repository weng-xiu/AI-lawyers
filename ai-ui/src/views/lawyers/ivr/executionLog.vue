<template>
  <div class="app-container ivr-exec-log-page">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="logList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      label-width="80px"
      empty-text="暂无 IVR 执行日志"
      empty-hint="流程执行后，每个节点的执行轨迹会在此留痕"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <!-- 日期区间沿用独立 dateRange + addDateRange，走自定义查询插槽 -->
      <template #query-dateRange>
        <el-date-picker
          v-model="dateRange"
          style="width: 260px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </template>
      <template #column-nodeType="{ row }">
        <el-tag size="mini" :type="nodeTypeTag(row.nodeType)">{{ nodeTypeLabel(row.nodeType) }}</el-tag>
      </template>
      <template #column-nodeName="{ row }">{{ row.nodeName || '-' }}</template>
      <template #column-execAction="{ row }">{{ row.execAction || '-' }}</template>
      <template #column-execResult="{ row }">
        <el-tag size="mini" :type="String(row.execResult) === '1' ? 'success' : 'danger'">
          {{ String(row.execResult) === '1' ? '成功' : '失败' }}
        </el-tag>
      </template>
      <template #column-costMs="{ row }">{{ row.costMs != null ? row.costMs : '-' }}</template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button type="text" size="mini" icon="el-icon-view" @click="handleDetail(row)">详情</el-button>
      </template>
    </ProTable>

    <!-- 详情对话框：显示完整日志 JSON -->
    <el-dialog title="执行日志详情" :visible.sync="detailOpen" width="720px" append-to-body>
      <div v-loading="detailLoading">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="日志ID">{{ detailForm.execId }}</el-descriptions-item>
          <el-descriptions-item label="会话ID">{{ detailForm.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="节点类型">{{ nodeTypeLabel(detailForm.nodeType) }}</el-descriptions-item>
          <el-descriptions-item label="节点名称">{{ detailForm.nodeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行动作">{{ detailForm.execAction || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行结果">
            <el-tag size="mini" :type="String(detailForm.execResult) === '1' ? 'success' : 'danger'">
              {{ String(detailForm.execResult) === '1' ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="耗时(ms)">{{ detailForm.costMs != null ? detailForm.costMs : '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ detailForm.errorMsg || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="json-section">
          <div class="json-title">完整日志数据（JSON）</div>
          <pre class="json-box">{{ detailJson }}</pre>
        </div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listExecutionLog, getExecutionLog } from '@/api/lawyers/ivrExecutionLog'

export default {
  name: 'IvrExecutionLog',
  data() {
    return {
      loading: false,
      detailLoading: false,
      showSearch: true,
      total: 0,
      logList: [],
      // V3：ProTable 列定义（节点类型/结果/耗时/时间/操作走插槽）
      columns: [
        { prop: 'execId', label: '日志ID', width: 90, align: 'center' },
        { prop: 'sessionId', label: '会话ID', width: 220, align: 'center', showOverflowTooltip: true },
        { prop: 'nodeType', label: '节点类型', width: 120, align: 'center', slot: true },
        { prop: 'nodeName', label: '节点名称', minWidth: 140, align: 'center', slot: true, showOverflowTooltip: true },
        { prop: 'execAction', label: '执行动作', minWidth: 140, align: 'center', slot: true, showOverflowTooltip: true },
        { prop: 'execResult', label: '执行结果', width: 90, align: 'center', slot: true },
        { prop: 'costMs', label: '耗时(ms)', width: 100, align: 'center', slot: true },
        { prop: 'createTime', label: '执行时间', width: 170, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 100, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      dateRange: [],
      detailOpen: false,
      detailForm: {},
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        sessionId: undefined,
        nodeType: undefined,
        execResult: undefined
      },
      nodeTypeOptions: [
        { value: 'start', label: '开始' },
        { value: 'playback', label: '放音' },
        { value: 'menu', label: '菜单' },
        { value: 'asr', label: 'ASR识别' },
        { value: 'condition', label: '条件判断' },
        { value: 'transfer', label: '转接' },
        { value: 'queue', label: '排队' },
        { value: 'voicemail', label: '留言' },
        { value: 'hangup', label: '挂断' },
        { value: 'http', label: 'HTTP请求' },
        { value: 'ai', label: 'AI对话' }
      ]
    }
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'sessionId', label: '会话ID', placeholder: '请输入会话ID', width: 220 },
        { prop: 'nodeType', label: '节点类型', type: 'select', placeholder: '全部', width: 160, options: this.nodeTypeOptions },
        {
          prop: 'execResult',
          label: '执行结果',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '成功', value: '1' },
            { label: '失败', value: '0' }
          ]
        },
        { prop: 'dateRange', label: '执行时间', slot: true }
      ]
    },
    detailJson() {
      try {
        return JSON.stringify(this.detailForm, null, 2)
      } catch (e) {
        return String(this.detailForm)
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange)
      }
      listExecutionLog(params).then(res => {
        this.logList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // ProTable 已复位查询表单，页面只需恢复筛选初值（含独立日期区间）并重查
      this.dateRange = []
      this.queryParams = { pageNum: 1, pageSize: 10, sessionId: undefined, nodeType: undefined, execResult: undefined }
      this.getList()
    },
    nodeTypeLabel(t) {
      if (!t) return '-'
      const item = this.nodeTypeOptions.find(o => o.value === t)
      return item ? item.label : t
    },
    nodeTypeTag(t) {
      const map = {
        start: '', playback: 'success', menu: 'warning', asr: 'primary',
        condition: 'info', transfer: 'danger', queue: 'warning',
        voicemail: 'info', hangup: 'danger', http: '', ai: 'success'
      }
      return map[t] || 'info'
    },
    handleDetail(row) {
      this.detailOpen = true
      this.detailLoading = true
      this.detailForm = { ...row }
      getExecutionLog(row.execId).then(res => {
        this.detailForm = res.data || row
        this.detailLoading = false
      }).catch(() => { this.detailLoading = false })
    }
  }
}
</script>

<style scoped>
.ivr-exec-log-page { padding: 16px; }
.json-section { margin-top: 16px; }
.json-title { font-size: 14px; font-weight: 600; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #255A99; }
.json-box {
  background: #1E1E1E;
  color: #CE9178;
  padding: 16px;
  border-radius: 6px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
