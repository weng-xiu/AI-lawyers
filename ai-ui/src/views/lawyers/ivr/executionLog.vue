<template>
  <div class="app-container ivr-exec-log-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="会话ID" prop="sessionId">
          <el-input v-model="queryParams.sessionId" placeholder="请输入会话ID" clearable style="width:220px" @keyup.enter.native="handleQuery" />
        </el-form-item>
        <el-form-item label="节点类型" prop="nodeType">
          <el-select v-model="queryParams.nodeType" placeholder="全部" clearable style="width:160px">
            <el-option v-for="t in nodeTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行结果" prop="execResult">
          <el-select v-model="queryParams.execResult" placeholder="全部" clearable style="width:140px">
            <el-option label="成功" value="1" />
            <el-option label="失败" value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行时间">
          <el-date-picker
            v-model="dateRange"
            style="width: 260px"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          ></el-date-picker>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="logList" border size="small">
        <el-table-column label="日志ID" align="center" prop="execId" width="90" />
        <el-table-column label="会话ID" align="center" prop="sessionId" width="220" show-overflow-tooltip />
        <el-table-column label="节点类型" align="center" width="120">
          <template slot-scope="scope">
            <el-tag size="mini" :type="nodeTypeTag(scope.row.nodeType)">{{ nodeTypeLabel(scope.row.nodeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="节点名称" align="center" prop="nodeName" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.nodeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行动作" align="center" prop="execAction" min-width="140" show-overflow-tooltip>
          <template slot-scope="scope">{{ scope.row.execAction || '-' }}</template>
        </el-table-column>
        <el-table-column label="执行结果" align="center" width="90">
          <template slot-scope="scope">
            <el-tag size="mini" :type="String(scope.row.execResult) === '1' ? 'success' : 'danger'">
              {{ String(scope.row.execResult) === '1' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时(ms)" align="center" prop="costMs" width="100">
          <template slot-scope="scope">{{ scope.row.costMs != null ? scope.row.costMs : '-' }}</template>
        </el-table-column>
        <el-table-column label="执行时间" align="center" prop="createTime" width="170">
          <template slot-scope="scope">{{ parseTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="100" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-view" @click="handleDetail(scope.row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

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
      total: 0,
      logList: [],
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
      this.dateRange = []
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, sessionId: undefined, nodeType: undefined, execResult: undefined }
      this.handleQuery()
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
