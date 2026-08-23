<template>
  <div class="app-container recording-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="主叫号码" prop="callerNumber">
          <el-input
            v-model="queryParams.callerNumber"
            placeholder="请输入主叫号码"
            clearable
            style="width: 180px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="被叫号码" prop="calleeNumber">
          <el-input
            v-model="queryParams.calleeNumber"
            placeholder="请输入被叫号码"
            clearable
            style="width: 180px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="ASR转写" prop="asrStatus">
          <el-select v-model="queryParams.asrStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="已转写" value="1" />
            <el-option label="未转写" value="0" />
            <el-option label="转写中" value="2" />
            <el-option label="转写失败" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="通话日期">
          <el-date-picker
            v-model="dateRange"
            style="width: 240px"
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
      <el-table v-loading="loading" :data="recordingList" border size="small">
        <el-table-column label="录音ID" align="center" prop="recordId" width="90" />
        <el-table-column label="主叫号码" align="center" prop="callerNumber" width="130" />
        <el-table-column label="被叫号码" align="center" prop="calleeNumber" width="130">
          <template slot-scope="scope">{{ scope.row.calleeNumber || '-' }}</template>
        </el-table-column>
        <el-table-column label="坐席" align="center" prop="agentName" width="110">
          <template slot-scope="scope">{{ scope.row.agentName || '-' }}</template>
        </el-table-column>
        <el-table-column label="通话时长" align="center" width="100">
          <template slot-scope="scope">{{ formatDuration(scope.row.callDuration) }}</template>
        </el-table-column>
        <el-table-column label="录音时长" align="center" width="100">
          <template slot-scope="scope">{{ formatDuration(scope.row.recordDuration) }}</template>
        </el-table-column>
        <el-table-column label="ASR状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag :type="asrTagType(scope.row.asrStatus)" size="mini">{{ asrLabel(scope.row.asrStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通话时间" align="center" prop="callTime" width="160">
          <template slot-scope="scope">{{ parseTime(scope.row.callTime) }}</template>
        </el-table-column>
        <el-table-column label="录音文件" align="center" width="280">
          <template slot-scope="scope">
            <div class="audio-cell">
              <el-button
                v-if="!playingId || playingId !== scope.row.recordId"
                type="primary"
                size="mini"
                icon="el-icon-video-play"
                @click="handlePlay(scope.row)"
              >播放</el-button>
              <el-button
                v-else
                type="danger"
                size="mini"
                icon="el-icon-video-pause"
                @click="handleStop"
              >停止</el-button>
              <audio
                v-if="playingId === scope.row.recordId"
                ref="audioPlayer"
                :src="audioUrl"
                controls
                autoplay
                style="height: 28px; margin-left: 8px; vertical-align: middle;"
                @ended="handleStop"
              ></audio>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-download" @click="handleDownload(scope.row)">下载</el-button>
            <el-button
              type="text"
              size="mini"
              icon="el-icon-document"
              :disabled="scope.row.asrStatus !== '1' || !scope.row.transcript"
              @click="handleViewTranscript(scope.row)"
            >查看转写</el-button>
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
      total: 0,
      recordingList: [],
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
      this.dateRange = []
      this.resetForm('queryForm')
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
