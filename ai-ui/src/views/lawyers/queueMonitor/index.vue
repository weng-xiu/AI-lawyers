<template>
  <div class="app-container queue-monitor-page">
    <!-- V3/U：队列水位表收敛到 ProTable（无查询区、无分页，保留 10 秒轮询与手动刷新） -->
    <ProTable
      :columns="queueColumns"
      :query-params="queueQuery"
      :data="queueList"
      :loading="loading"
      :show-search="false"
      :show-toolbar="false"
      :stripe="false"
      :table-attrs="{ border: true }"
      hide-pagination
      persist-key="queueMonitor:queue"
      empty-text="暂无队列水位数据"
      empty-hint="后端 Stream 队列无积压时此处为空"
      @query="getList">
      <template #toolbar-left>
        <span class="queue-refresh-hint">Stream 队列水位（每 10 秒自动刷新）</span>
        <el-button type="text" icon="el-icon-refresh" @click="getList()">刷新</el-button>
      </template>
      <template #column-queue="{ row }">
        <el-tag size="mini">{{ row.queue }}</el-tag>
      </template>
      <template #column-streamLen="{ row }">{{ formatCount(row.streamLen) }}</template>
      <template #column-pending="{ row }">
        <span :style="{ color: row.pending > 0 ? '#E6A23C' : 'inherit', fontWeight: row.pending > 0 ? 600 : 400 }">
          {{ formatCount(row.pending) }}
        </span>
      </template>
      <template #column-dead="{ row }">
        <span :style="{ color: row.dead > 0 ? '#C63D4A' : 'inherit', fontWeight: row.dead > 0 ? 600 : 400 }">
          {{ formatCount(row.dead) }}
        </span>
      </template>
      <template #column-action="{ row }">
        <el-button type="text" size="mini" icon="el-icon-warning-outline" @click="openDeadDialog(row)">死信管理</el-button>
      </template>
    </ProTable>

    <!-- 死信管理对话框 -->
    <el-dialog :title="'死信管理 - ' + currentQueue" :visible.sync="deadOpen" width="860px" append-to-body>
      <!-- 死信表同样收敛到 ProTable（独立 persist-key，弹窗内固定高度） -->
      <ProTable
        :columns="deadColumns"
        :query-params="deadQuery"
        :data="deadList"
        :loading="deadLoading"
        :total="deadTotal"
        :show-search="false"
        :show-toolbar="false"
        :stripe="false"
        :table-attrs="{ border: true, maxHeight: '420px' }"
        persist-key="queueMonitor:dead"
        empty-text="该队列暂无死信消息"
        empty-hint="消费失败进入死信后可在此重投或删除"
        @pagination="getDeadList">
        <template #column-payload="{ row }">
          <el-popover trigger="hover" placement="left" width="500">
            <pre class="payload-preview">{{ prettyPayload(row) }}</pre>
            <span slot="reference" class="payload-cell">{{ row.payload || row.raw }}</span>
          </el-popover>
        </template>
        <template #column-action="{ row }">
          <el-button v-hasPermi="['lawyers:queueMonitor:replay']" type="text" size="mini" icon="el-icon-refresh-left" @click="handleReplay(row)">重投</el-button>
          <el-button v-hasPermi="['lawyers:queueMonitor:replay']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleRemove(row)">删除</el-button>
        </template>
      </ProTable>
      <div slot="footer">
        <el-button size="small" @click="deadOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listQueue, listDeadLetter, replayDeadLetter, removeDeadLetter } from '@/api/lawyers/queueMonitor'
import { createPoller } from '@/utils/polling'

export default {
  name: 'QueueMonitor',
  data() {
    return {
      loading: false,
      queueList: [],
      // 无查询条件/分页，仅为满足 ProTable 的 queryParams 契约
      queueQuery: { pageNum: 1, pageSize: 10 },
      // V3：队列水位列定义（队列/长度/积压/死信/操作走插槽，移除原 fixed="right"）
      queueColumns: [
        { prop: 'queue', label: '队列', minWidth: 140, slot: true },
        { prop: 'streamKey', label: 'Stream Key', minWidth: 180, showOverflowTooltip: true },
        { prop: 'streamLen', label: 'Stream 长度', width: 120, slot: true },
        { prop: 'pending', label: '积压(未ACK)', width: 120, slot: true },
        { prop: 'dead', label: '死信数', width: 100, slot: true },
        { prop: 'action', label: '操作', width: 140, slot: true }
      ],
      // 死信表列定义（消息体/操作走插槽）
      deadColumns: [
        { prop: 'id', label: '死信ID', width: 160, showOverflowTooltip: true },
        { prop: 'originId', label: '原始消息ID', width: 160, showOverflowTooltip: true },
        { prop: 'error', label: '失败原因', minWidth: 160, showOverflowTooltip: true },
        { prop: 'payload', label: '消息体', minWidth: 220, slot: true },
        { prop: 'action', label: '操作', width: 130, slot: true }
      ],
      deadOpen: false,
      deadLoading: false,
      currentQueue: '',
      deadList: [],
      deadTotal: 0,
      deadQuery: { pageNum: 1, pageSize: 10 }
    }
  },
  created() {
    this.getList()
    // P6：统一轮询——页面不可见暂停、在途防重入、恢复可见立即补拉；死信弹窗打开期间跳过
    this.poller = createPoller(() => {
      if (!this.deadOpen) {
        this.getList(true)
      }
    }, 10000)
    this.poller.start()
  },
  beforeDestroy() {
    if (this.poller) {
      this.poller.stop()
    }
  },
  methods: {
    getList(silent) {
      if (!silent) {
        this.loading = true
      }
      return listQueue().then(response => {
        this.queueList = response.data || []
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    formatCount(v) {
      return v === null || v === undefined || v < 0 ? '查询失败' : v
    },
    openDeadDialog(row) {
      this.currentQueue = row.queue
      this.deadQuery.pageNum = 1
      this.deadOpen = true
      this.getDeadList()
    },
    getDeadList() {
      this.deadLoading = true
      listDeadLetter({ queue: this.currentQueue, ...this.deadQuery }).then(response => {
        this.deadList = response.rows || []
        this.deadTotal = response.total || 0
        this.deadLoading = false
      }).catch(() => {
        this.deadLoading = false
      })
    },
    prettyPayload(row) {
      const text = row.payload || row.raw || ''
      try {
        return JSON.stringify(JSON.parse(text), null, 2)
      } catch (e) {
        return text
      }
    },
    handleReplay(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认将该死信回投到原队列重新消费？',
        tip: '消费端幂等，不会重复生效；若消费持续失败可能再次进入死信'
      }).then(() => {
        return replayDeadLetter(this.currentQueue, row.id)
      }).then(() => {
        this.$modal.msgSuccess('重投成功')
        this.getDeadList()
        this.getList(true)
      }).catch(() => {})
    },
    handleRemove(row) {
      this.$modal.dangerConfirm({
        content: '将删除队列「' + this.currentQueue + '」中的死信消息',
        count: 1,
        countNoun: '条消息'
      }).then(() => {
        return removeDeadLetter(this.currentQueue, row.id)
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getDeadList()
        this.getList(true)
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.queue-refresh-hint {
  margin-right: 8px;
  font-weight: 600;
  color: #606266;
}
.payload-cell {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  cursor: pointer;
  color: #3a5cb8;
}
.payload-preview {
  max-height: 320px;
  overflow: auto;
  font-size: 12px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

/* U6 平板只读巡场：收紧表格字号与行高，一屏展示更多队列 */
@media (max-width: 1024px) {
  .queue-monitor-page .el-table { font-size: 12px; }
  .queue-monitor-page .el-table th,
  .queue-monitor-page .el-table td { padding: 5px 0; }
}
</style>
