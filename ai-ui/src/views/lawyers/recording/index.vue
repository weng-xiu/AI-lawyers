<template>
  <div class="app-container recording-page">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="recordingList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="80px"
      empty-text="暂无录音记录"
      empty-hint="通话结束后，录音文件与 ASR 转写结果将在此留存"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <!-- 通话日期沿用独立 dateRange + addDateRange，走自定义查询插槽 -->
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-calleeNumber="{ row }">{{ row.calleeNumber || '-' }}</template>
      <template #column-agentName="{ row }">{{ row.agentName || '-' }}</template>
      <template #column-callDuration="{ row }">{{ formatDuration(row.callDuration) }}</template>
      <template #column-recordDuration="{ row }">{{ formatDuration(row.recordDuration) }}</template>
      <template #column-asrStatus="{ row }">
        <el-tag :type="asrTagType(row.asrStatus)" size="mini">{{ asrLabel(row.asrStatus) }}</el-tag>
      </template>
      <template #column-callTime="{ row }">{{ parseTime(row.callTime) }}</template>
      <template #column-audio="{ row }">
        <div class="audio-cell">
          <el-button
            v-if="!playingId || playingId !== row.recordId"
            type="primary"
            size="mini"
            icon="el-icon-video-play"
            @click="handlePlay(row)"
          >播放</el-button>
          <el-button
            v-else
            type="danger"
            size="mini"
            icon="el-icon-video-pause"
            @click="handleStop"
          >停止</el-button>
          <audio
            v-if="playingId === row.recordId"
            ref="audioPlayer"
            :src="audioUrl"
            controls
            autoplay
            style="height: 28px; margin-left: 8px; vertical-align: middle;"
            @ended="handleStop"
          ></audio>
        </div>
      </template>
      <template #column-action="{ row }">
        <el-button type="text" size="mini" icon="el-icon-download" @click="handleDownload(row)">下载</el-button>
        <el-button
          type="text"
          size="mini"
          icon="el-icon-document"
          :disabled="row.asrStatus !== '1' || !row.transcript"
          @click="handleViewTranscript(row)"
        >查看转写</el-button>
      </template>
    </ProTable>

    <!-- ASR 转写详情对话框 -->
    <el-dialog title="ASR转写内容" :visible.sync="transcriptOpen" width="680px" append-to-body>
      <div v-loading="transcriptLoading" class="transcript-box">
        <div class="transcript-meta">
          <el-tag size="mini" type="info">记录ID：{{ currentRow.recordId }}</el-tag>
          <span class="meta-text">{{ currentRow.callerNumber }} → {{ currentRow.calleeNumber || '-' }}</span>
          <span class="meta-text">{{ parseTime(currentRow.callTime) }}</span>
        </div>
        <el-divider></el-divider>
        <div class="transcript-content">{{ currentRow.transcript || '暂无转写内容' }}</div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="transcriptOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listRecord, getRecord, playUrl, downloadUrl, fetchAudioBlob } from '@/api/lawyers/recording'
import { getToken } from '@/utils/auth'

export default {
  name: 'Recording',
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      recordingList: [],
      // V3：ProTable 列定义（被叫/坐席/时长/ASR/时间/录音文件/操作走插槽）
      columns: [
        { prop: 'recordId', label: '录音ID', width: 90, align: 'center' },
        { prop: 'callerNumber', label: '主叫号码', width: 130, align: 'center' },
        { prop: 'calleeNumber', label: '被叫号码', width: 130, align: 'center', slot: true },
        { prop: 'agentName', label: '坐席', width: 110, align: 'center', slot: true },
        { prop: 'callDuration', label: '通话时长', width: 100, align: 'center', slot: true },
        { prop: 'recordDuration', label: '录音时长', width: 100, align: 'center', slot: true },
        { prop: 'asrStatus', label: 'ASR状态', width: 100, align: 'center', slot: true },
        { prop: 'callTime', label: '通话时间', width: 160, align: 'center', slot: true },
        { prop: 'audio', label: '录音文件', width: 280, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 180, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      dateRange: [],
      onlyRecording: true,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        calleeNumber: undefined,
        asrStatus: undefined
      },
      playingId: null,
      audioUrl: '',
      audioBlobUrl: '',
      transcriptOpen: false,
      transcriptLoading: false,
      currentRow: {}
    }
  },
  computed: {
    queryFormItems() {
      return [
        { prop: 'callerNumber', label: '主叫号码', placeholder: '请输入主叫号码', width: 180 },
        { prop: 'calleeNumber', label: '被叫号码', placeholder: '请输入被叫号码', width: 180 },
        {
          prop: 'asrStatus',
          label: 'ASR转写',
          type: 'select',
          placeholder: '全部',
          width: 140,
          options: [
            { label: '已转写', value: '1' },
            { label: '未转写', value: '0' },
            { label: '转写中', value: '2' },
            { label: '转写失败', value: '3' }
          ]
        },
        { prop: 'dateRange', label: '通话日期', slot: true }
      ]
    }
  },
  created() {
    this.getList()
  },
  beforeDestroy() {
    this.revokeAudioUrl()
  },
  methods: {
    getList() {
      this.loading = true
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange),
        onlyRecording: true
      }
      listRecord(params).then(response => {
        let rows = response.rows || []
        // 如果后端未做 onlyRecording 过滤，则前端兜底过滤有录音文件的记录
        rows = rows.filter(r => r.recordFile || r.recordingUrl || r.recordDuration)
        this.recordingList = rows
        this.total = response.total != null ? response.total : rows.length
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
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        calleeNumber: undefined,
        asrStatus: undefined
      }
      this.handleQuery()
    },
    asrLabel(s) {
      return { '0': '未转写', '1': '已转写', '2': '转写中', '3': '转写失败' }[s] || '未转写'
    },
    asrTagType(s) {
      return { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }[s] || 'info'
    },
    formatDuration(sec) {
      if (sec == null || sec === '' || isNaN(sec)) return '-'
      const s = Number(sec)
      const m = Math.floor(s / 60)
      const r = s % 60
      return (m < 10 ? '0' + m : m) + ':' + (r < 10 ? '0' + r : r)
    },
    revokeAudioUrl() {
      if (this.audioBlobUrl) {
        URL.revokeObjectURL(this.audioBlobUrl)
        this.audioBlobUrl = ''
      }
    },
    handlePlay(row) {
      // 先停止当前播放
      if (this.playingId && this.playingId !== row.recordId) {
        this.handleStop()
      }
      this.playingId = row.recordId
      this.audioUrl = ''
      this.revokeAudioUrl()
      // 带 token 的 blob 播放，兼容后端 Authorization 头校验
      fetchAudioBlob(row.recordId).then(blob => {
        if (this.playingId !== row.recordId) return
        this.audioBlobUrl = URL.createObjectURL(blob)
        this.audioUrl = this.audioBlobUrl
      }).catch(() => {
        // blob 失败时回退到 URL 拼接 token 方式
        if (this.playingId === row.recordId) {
          this.audioUrl = playUrl(row.recordId) + '?Authorization=Bearer ' + getToken()
        }
      })
    },
    handleStop() {
      this.playingId = null
      this.audioUrl = ''
      this.revokeAudioUrl()
    },
    handleDownload(row) {
      // 直接通过 window.open 触发下载，携带 token 用 query 兜底
      const url = downloadUrl(row.recordId) + '?Authorization=Bearer ' + encodeURIComponent(getToken())
      window.open(url, '_blank')
    },
    handleViewTranscript(row) {
      this.currentRow = { ...row }
      this.transcriptOpen = true
      // 如果当前行没有 transcript，主动拉详情
      if (!row.transcript) {
        this.transcriptLoading = true
        getRecord(row.recordId).then(res => {
          const d = res.data || {}
          this.currentRow = { ...row, ...d }
          this.transcriptLoading = false
        }).catch(() => { this.transcriptLoading = false })
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.recording-page {
  padding: 16px;
}
.audio-cell {
  display: flex;
  align-items: center;
  justify-content: center;
}
.transcript-box {
  min-height: 240px;
}
.transcript-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  .meta-text {
    color: #5A6A7E;
    font-size: 13px;
  }
}
.transcript-content {
  white-space: pre-wrap;
  line-height: 1.8;
  font-size: 14px;
  color: #1F2A3A;
  max-height: 420px;
  overflow-y: auto;
  background: #F5F7FA;
  border-radius: 6px;
  padding: 16px;
}
</style>
