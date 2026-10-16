<template>
  <div class="app-container mc-page">
    <!-- 4 统计卡 -->
    <el-row :gutter="16" class="mc-stats">
      <el-col :xs="12" :sm="6">
        <div class="mc-stat-card mc-stat-today">
          <div class="mc-stat-icon"><i class="el-icon-phone-outline"></i></div>
          <div class="mc-stat-body">
            <div class="mc-stat-label">今日未接</div>
            <div class="mc-stat-value">{{ stats.todayMissed || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="mc-stat-card mc-stat-week">
          <div class="mc-stat-icon"><i class="el-icon-date"></i></div>
          <div class="mc-stat-body">
            <div class="mc-stat-label">本周未接</div>
            <div class="mc-stat-value">{{ stats.weekMissed || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="mc-stat-card mc-stat-cb">
          <div class="mc-stat-icon"><i class="el-icon-success"></i></div>
          <div class="mc-stat-body">
            <div class="mc-stat-label">已回拨</div>
            <div class="mc-stat-value">{{ stats.callbacked || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="mc-stat-card mc-stat-rate">
          <div class="mc-stat-icon"><i class="el-icon-data-line"></i></div>
          <div class="mc-stat-body">
            <div class="mc-stat-label">回拨率</div>
            <div class="mc-stat-value">{{ callbackRate }}%</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 3 标签页 -->
    <el-tabs v-model="activeTab" class="mc-tabs" @tab-click="handleTabClick">
      <!-- 未接来电 -->
      <el-tab-pane label="未接来电" name="missed">
        <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
        <ErrorRetry v-if="loadError" @retry="getList" />
        <ProTable
          v-else
          ref="proTable"
          :columns="columns"
          :query-form-items="queryFormItems"
          :query-params="queryParams"
          :data="missedList"
          :loading="loading"
          :total="total"
          :show-search.sync="showSearch"
          label-width="80px"
          has-selection
          empty-text="暂无未接来电"
          empty-hint="未接来电会在挂机后自动登记"
          @query="handleQuery"
          @reset="resetQuery"
          @pagination="getList"
          @selection-change="handleSelectionChange"
        >
          <template #query-dateRange>
            <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
          </template>
          <template #toolbar-left>
            <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:call:missed:remove']">删除</el-button>
            <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport" v-hasPermi="['lawyers:call:missed:export']">导出</el-button>
            <el-button type="success" plain icon="el-icon-refresh" size="mini" @click="loadStats">刷新统计</el-button>
          </template>
          <template #column-status="{ row }">
            <el-tag size="mini" :type="row.status === '1' ? 'success' : 'danger'">{{ row.status === '1' ? '已回拨' : '未回拨' }}</el-tag>
          </template>
          <template #column-action="{ row }">
            <el-button size="mini" type="text" icon="el-icon-phone" v-if="row.status === '0'" @click="handleCallback(row)" v-hasPermi="['lawyers:call:missed:callback']">回拨标记</el-button>
            <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)" v-hasPermi="['lawyers:call:missed:edit']">修改</el-button>
            <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)" v-hasPermi="['lawyers:call:missed:remove']">删除</el-button>
          </template>
        </ProTable>
      </el-tab-pane>

      <!-- 语音留言 -->
      <el-tab-pane label="语音留言" name="voice">
        <div class="mc-voice-list" v-loading="loading">
          <el-empty v-if="voiceList.length === 0" description="暂无语音留言"></el-empty>
          <div v-for="(item, idx) in voiceList" :key="idx" class="mc-voice-item">
            <div class="mc-voice-avatar"><i class="el-icon-microphone"></i></div>
            <div class="mc-voice-main">
              <div class="mc-voice-top">
                <span class="mc-voice-name">{{ item.callerName || '未知来电人' }}</span>
                <span class="mc-voice-number">{{ item.callerNumber }}</span>
                <el-tag size="mini" :type="item.status === '1' ? 'success' : 'danger'">{{ item.status === '1' ? '已回拨' : '未回拨' }}</el-tag>
                <el-tag v-if="item.remark && item.remark.indexOf('已转工单') >= 0" size="mini" type="warning">{{ item.remark }}</el-tag>
              </div>
              <div class="mc-voice-content">"{{ item.voiceContent || '（无语音留言内容）' }}"</div>
              <div class="mc-voice-meta">
                <span><i class="el-icon-time"></i> {{ item.callTime }}</span>
                <span><i class="el-icon-service"></i> 留言时长 {{ item.voiceDuration || 0 }}秒</span>
                <el-button v-if="item.voiceFileUrl" size="mini" type="primary" plain
                           :icon="playingId === item.missedCallId ? 'el-icon-video-pause' : 'el-icon-video-play'"
                           :loading="voiceLoading && playingId === item.missedCallId"
                           @click="handlePlayVoice(item)">
                  {{ playingId === item.missedCallId ? '停止' : '播放' }}
                </el-button>
                <span v-else class="mc-voice-nofile">无录音文件</span>
                <el-button size="mini" type="warning" plain icon="el-icon-document" @click="handleTransfer(item)" v-hasPermi="['lawyers:call:missed:callback']">转工单</el-button>
                <el-button size="mini" type="success" plain icon="el-icon-phone" v-if="item.status === '0'" @click="handleCallback(item)">回拨</el-button>
              </div>
              <audio v-if="playingId === item.missedCallId && voiceAudioUrl" :src="voiceAudioUrl" controls autoplay
                     class="mc-voice-audio" @ended="handleVoiceEnded"></audio>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 漏话通知 -->
      <el-tab-pane label="漏话通知" name="notice">
        <!-- V3：工具条/表格/分页收敛到 ProTable（无查询表单，queryFormItems 为空时查询区自动隐藏） -->
        <ErrorRetry v-if="noticeError" @retry="getNoticeList" />
        <ProTable
          v-else
          :columns="noticeColumns"
          :query-params="noticeQuery"
          :data="noticeList"
          :loading="loading"
          :total="noticeTotal"
          :show-toolbar="false"
          persist-key="missedCall-notice"
          has-selection
          empty-text="暂无漏话通知"
          empty-hint="可选择来电记录批量发送漏话通知"
          @pagination="getNoticeList"
          @selection-change="handleSelectionChange"
        >
          <template #toolbar-left>
            <el-button type="primary" plain icon="el-icon-bell" size="mini" :disabled="multiple" @click="handleBatchNotice">批量通知</el-button>
          </template>
          <template #column-noticeStatus="{ row }">
            <el-tag size="mini" :type="row.noticeStatus === '1' ? 'success' : 'info'">{{ row.noticeStatus === '1' ? '已通知' : '未通知' }}</el-tag>
          </template>
          <template #column-status="{ row }">
            <el-tag size="mini" :type="row.status === '1' ? 'success' : 'danger'">{{ row.status === '1' ? '已回拨' : '未回拨' }}</el-tag>
          </template>
          <template #column-action="{ row }">
            <el-button size="mini" type="text" icon="el-icon-message" v-if="row.noticeStatus === '0'" @click="handleNotice(row)">发送通知</el-button>
            <el-button size="mini" type="text" icon="el-icon-phone" v-if="row.status === '0'" @click="handleCallback(row)" v-hasPermi="['lawyers:call:missed:callback']">回拨</el-button>
          </template>
        </ProTable>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加/修改对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="500px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="来电号码" prop="callerNumber">
          <el-input v-model="form.callerNumber" placeholder="请输入来电号码" />
        </el-form-item>
        <el-form-item label="来电人" prop="callerName">
          <el-input v-model="form.callerName" placeholder="请输入来电人姓名" />
        </el-form-item>
        <el-form-item label="来电时间" prop="callTime">
          <el-date-picker v-model="form.callTime" style="width: 100%" value-format="yyyy-MM-dd HH:mm:ss" type="datetime" placeholder="选择来电时间" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio label="0">未回拨</el-radio>
            <el-radio label="1">已回拨</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="语音留言" prop="voiceContent">
          <el-input v-model="form.voiceContent" type="textarea" placeholder="请输入语音留言内容" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listMissedCall, getMissedCall, addMissedCall, updateMissedCall, delMissedCall, getMissedCallStats, callbackMissedCall, fetchVoiceBlob, transferVoiceToTicket } from '@/api/lawyers/missedCall'

export default {
  name: 'MissedCall',
  data() {
    return {
      loading: false,
      loadError: false,
      noticeError: false,
      showSearch: true,
      activeTab: 'missed',
      stats: {},
      missedList: [],
      voiceList: [],
      noticeList: [],
      total: 0,
      noticeTotal: 0,
      // V3：ProTable 列定义（未接来电主表，status/action 走插槽，空值列用 formatter）
      columns: [
        { prop: 'callerNumber', label: '来电号码', width: 130 },
        { prop: 'callerName', label: '来电人', width: 100 },
        { prop: 'callTime', label: '来电时间', width: 160 },
        { prop: 'status', label: '状态', width: 90, slot: true },
        { prop: 'callbackTime', label: '回拨时间', width: 160, formatter: (row) => row.callbackTime || '-' },
        { prop: 'callbackBy', label: '回拨人', width: 100, formatter: (row) => row.callbackBy || '-' },
        { prop: 'remark', label: '备注', showOverflowTooltip: true },
        { prop: 'action', label: '操作', width: 200, align: 'center', fixed: 'right', slot: true }
      ],
      // V3：漏话通知副表列定义（noticeStatus/status/action 走插槽，通知方式用 formatter）
      noticeColumns: [
        { prop: 'callerNumber', label: '来电号码', width: 130 },
        { prop: 'callerName', label: '来电人', width: 100 },
        { prop: 'callTime', label: '来电时间', width: 160 },
        { prop: 'noticeStatus', label: '通知状态', width: 100, slot: true },
        { prop: 'noticeChannel', label: '通知方式', width: 120, formatter: (row) => this.channelText(row.noticeChannel) },
        { prop: 'status', label: '回拨状态', width: 100, slot: true },
        { prop: 'remark', label: '备注', showOverflowTooltip: true },
        { prop: 'action', label: '操作', width: 180, align: 'center', fixed: 'right', slot: true }
      ],
      open: false,
      title: '',
      ids: [],
      multiple: true,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        callerNumber: undefined,
        callerName: undefined,
        status: undefined
      },
      noticeQuery: {
        pageNum: 1,
        pageSize: 10
      },
      form: {},
      rules: {
        callerNumber: [{ required: true, message: '来电号码不能为空', trigger: 'blur' }]
      },
      // 语音留言播放状态
      playingId: null,
      voiceAudioUrl: '',
      voiceBlobUrl: '',
      voiceLoading: false
    }
  },
  computed: {
    callbackRate() {
      const total = Number(this.stats.totalCount || 0)
      const cb = Number(this.stats.callbacked || 0)
      if (total === 0) return 0
      return ((cb / total) * 100).toFixed(1)
    },
    /** V3：ProTable 查询字段（未接来电标签页；日期范围走 #query-dateRange 插槽） */
    queryFormItems() {
      return [
        { prop: 'callerNumber', label: '来电号码', placeholder: '请输入来电号码', width: '200px' },
        { prop: 'callerName', label: '来电人', placeholder: '请输入来电人', width: '200px' },
        { prop: 'status', label: '回拨状态', type: 'select', placeholder: '全部', width: '140px', options: [
          { label: '未回拨', value: '0' }, { label: '已回拨', value: '1' }
        ] },
        { prop: 'dateRange', label: '来电时间', slot: true }
      ]
    }
  },
  created() {
    this.loadStats()
    this.getList()
  },
  beforeDestroy() {
    this.stopVoice()
  },
  methods: {
    loadStats() {
      getMissedCallStats().then(res => {
        this.stats = res.data || {}
      })
    },
    getList() {
      this.loading = true
      this.loadError = false
      listMissedCall(this.addDateRange(this.queryParams, this.dateRange)).then(res => {
        this.missedList = res.rows
        this.total = res.total
      }).catch(() => {
        // V2 三态：失败给出统一错误重试态
        this.loadError = true
      }).finally(() => {
        this.loading = false
      })
    },
    getNoticeList() {
      this.loading = true
      this.noticeError = false
      listMissedCall(this.noticeQuery).then(res => {
        this.noticeList = res.rows
        this.noticeTotal = res.total
      }).catch(() => {
        this.noticeError = true
      }).finally(() => {
        this.loading = false
      })
    },
    loadVoiceList() {
      this.loading = true
      this.stopVoice()
      listMissedCall({ pageNum: 1, pageSize: 100 }).then(res => {
        this.voiceList = (res.rows || []).filter(item => item.voiceContent || item.voiceFileUrl)
      }).finally(() => {
        this.loading = false
      })
    },
    handleTabClick(tab) {
      if (tab.name === 'voice') {
        this.loadVoiceList()
      } else if (tab.name === 'notice') {
        this.noticeQuery.pageNum = 1
        this.getNoticeList()
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里补插槽字段复位后重查
      this.dateRange = []
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.missedCallId)
      this.multiple = !selection.length
    },
    handleCallback(row) {
      this.$confirm('是否确认对来电 ' + row.callerNumber + ' 进行回拨标记？', '回拨确认', {
        type: 'warning'
      }).then(() => {
        return callbackMissedCall(row.missedCallId)
      }).then(() => {
        this.$message.success('回拨标记成功')
        this.loadStats()
        this.getList()
        if (this.activeTab === 'voice') this.loadVoiceList()
        if (this.activeTab === 'notice') this.getNoticeList()
      }).catch(() => {})
    },
    // 语音留言播放：blob 带 token 拉取后本地播放，失败给出明确提示
    handlePlayVoice(row) {
      if (this.playingId === row.missedCallId) {
        this.stopVoice()
        return
      }
      this.stopVoice()
      this.playingId = row.missedCallId
      this.voiceLoading = true
      fetchVoiceBlob(row.missedCallId).then(blob => {
        if (this.playingId !== row.missedCallId) return
        this.voiceBlobUrl = URL.createObjectURL(blob)
        this.voiceAudioUrl = this.voiceBlobUrl
      }).catch(() => {
        if (this.playingId === row.missedCallId) {
          this.$message.warning('语音文件不存在或读取失败')
          this.stopVoice()
        }
      }).finally(() => {
        this.voiceLoading = false
      })
    },
    stopVoice() {
      if (this.voiceBlobUrl) {
        URL.revokeObjectURL(this.voiceBlobUrl)
      }
      this.voiceBlobUrl = ''
      this.voiceAudioUrl = ''
      this.playingId = null
      this.voiceLoading = false
    },
    handleVoiceEnded() {
      this.stopVoice()
    },
    // 语音留言一键转工单
    handleTransfer(row) {
      if (row.remark && row.remark.indexOf('已转工单') >= 0) {
        this.$message.info('该留言已转过工单（' + row.remark + '），请勿重复提交')
        return
      }
      this.$confirm('是否将来电 ' + row.callerNumber + ' 的语音留言转为工单？', '转工单确认', {
        type: 'warning'
      }).then(() => {
        return transferVoiceToTicket(row.missedCallId)
      }).then(res => {
        this.$message.success('转工单成功，工单号：' + (res.data || res.msg || ''))
        this.loadVoiceList()
        this.getList()
      }).catch(() => {})
    },
    handleNotice(row) {
      const data = { missedCallId: row.missedCallId, noticeStatus: '1', noticeChannel: '1' }
      updateMissedCall(data).then(() => {
        this.$message.success('通知已发送')
        this.getNoticeList()
      })
    },
    handleBatchNotice() {
      if (!this.ids.length) return
      this.$modal.dangerConfirm({
        actionType: 'caution',
        title: '批量通知确认',
        confirmText: '确认发送',
        content: '将向选中记录对应的来电号码发送漏话短信通知',
        count: this.ids.length,
        countNoun: '条记录',
        tip: '短信将真实下发至公众手机，请确认号码与话术无误'
      }).then(() => {
        const tasks = this.ids.map(id => updateMissedCall({ missedCallId: id, noticeStatus: '1', noticeChannel: '1' }))
        Promise.all(tasks).then(() => {
          this.$message.success('批量通知已发送')
          this.getNoticeList()
        })
      }).catch(() => {})
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '添加未接来电'
    },
    handleUpdate(row) {
      this.reset()
      getMissedCall(row.missedCallId).then(res => {
        this.form = res.data
        this.open = true
        this.title = '修改未接来电'
      })
    },
    submitForm() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.missedCallId) {
          updateMissedCall(this.form).then(() => {
            this.$message.success('修改成功')
            this.open = false
            this.getList()
          })
        } else {
          addMissedCall(this.form).then(() => {
            this.$message.success('新增成功')
            this.open = false
            this.getList()
          })
        }
      })
    },
    handleDelete(row) {
      const ids = row.missedCallId ? [row.missedCallId] : this.ids
      this.$modal.dangerConfirm({
        content: '将删除选中的未接来电记录',
        count: ids.length,
        countNoun: '条记录'
      }).then(() => {
        return delMissedCall(ids)
      }).then(() => {
        this.$message.success('删除成功')
        this.loadStats()
        this.getList()
      }).catch(() => {})
    },
    handleExport() {
      this.download('/lawyers/call/missed/export', { ...this.queryParams }, `未接来电_${new Date().getTime()}.xlsx`)
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        missedCallId: undefined,
        callerNumber: undefined,
        callerName: undefined,
        callTime: undefined,
        status: '0',
        voiceContent: undefined,
        remark: undefined
      }
      this.resetForm('form')
    },
    channelText(channel) {
      const map = { '1': '短信', '2': '微信', '3': '邮件' }
      return map[channel] || '未设置'
    }
  }
}
</script>

<style lang="scss" scoped>
.mc-page {
  padding: 24px;
}
.mc-stats {
  margin-bottom: 20px;
}
.mc-stat-card {
  display: flex;
  align-items: center;
  padding: 20px 24px;
  border-radius: 8px;
  color: #fff;
  min-height: 84px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  .mc-stat-icon {
    font-size: 32px;
    margin-right: 16px;
    opacity: 0.9;
  }
  .mc-stat-label {
    font-size: 13px;
    opacity: 0.9;
  }
  .mc-stat-value {
    font-size: 26px;
    font-weight: 600;
    line-height: 1.2;
    margin-top: 6px;
  }
}
.mc-stat-today { background: linear-gradient(135deg, #C63D4A, #D98089); }
.mc-stat-week { background: linear-gradient(135deg, #E8923A, #EFA960); }
.mc-stat-cb { background: linear-gradient(135deg, #2B8C6E, #54A68B); }
.mc-stat-rate { background: linear-gradient(135deg, #1A3C6E, #255A99); }

.mc-tabs {
  background: #fff;
  padding: 0 24px 24px;
  border-radius: 4px;
}

.mc-voice-list {
  padding: 12px 8px;
}
.mc-voice-item {
  display: flex;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 16px;
  background: #fafafa;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
  .mc-voice-avatar {
    width: 48px; height: 48px;
    border-radius: 50%;
    background: linear-gradient(135deg, #1A3C6E, #255A99);
    color: #fff;
    display: flex; align-items: center; justify-content: center;
    font-size: 22px;
    margin-right: 16px;
    flex-shrink: 0;
  }
  .mc-voice-main { flex: 1; }
  .mc-voice-top {
    display: flex; align-items: center; gap: 12px;
    margin-bottom: 8px;
    .mc-voice-name { font-weight: 600; font-size: 14px; color: #1F2A3A; }
    .mc-voice-number { color: #8C8C8C; font-size: 13px; }
  }
  .mc-voice-content {
    color: #5A6A7E;
    font-size: 13px;
    padding: 10px 14px;
    background: #fff;
    border-radius: 4px;
    margin: 8px 0;
    border-left: 3px solid #1A3C6E;
    line-height: 1.6;
  }
  .mc-voice-meta {
    display: flex; align-items: center; gap: 16px;
    color: #8C8C8C; font-size: 12px;
    margin-top: 8px;
  }
  .mc-voice-nofile {
    color: #C0C4CC; font-size: 12px;
  }
  .mc-voice-audio {
    width: 100%;
    margin-top: 10px;
    height: 32px;
    display: block;
  }
}
</style>
