<template>
  <div class="app-container queue-monitor-page">
    <el-card shadow="never" class="table-card">
      <div slot="header" class="card-header">
        <span>Stream 队列水位（每 10 秒自动刷新）</span>
        <el-button style="float: right; padding: 3px 0" type="text" icon="el-icon-refresh" @click="getList">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="queueList" border size="small">
        <el-table-column label="队列" align="center" prop="queue" min-width="140">
          <template slot-scope="scope">
            <el-tag size="mini">{{ scope.row.queue }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Stream Key" align="center" prop="streamKey" min-width="180" show-overflow-tooltip />
        <el-table-column label="Stream 长度" align="center" width="120">
          <template slot-scope="scope">{{ formatCount(scope.row.streamLen) }}</template>
        </el-table-column>
        <el-table-column label="积压(未ACK)" align="center" width="120">
          <template slot-scope="scope">
            <span :style="{ color: scope.row.pending > 0 ? '#E6A23C' : 'inherit', fontWeight: scope.row.pending > 0 ? 600 : 400 }">
              {{ formatCount(scope.row.pending) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="死信数" align="center" width="100">
          <template slot-scope="scope">
            <span :style="{ color: scope.row.dead > 0 ? '#C63D4A' : 'inherit', fontWeight: scope.row.dead > 0 ? 600 : 400 }">
              {{ formatCount(scope.row.dead) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-warning-outline" @click="openDeadDialog(scope.row)">死信管理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 死信管理对话框 -->
    <el-dialog :title="'死信管理 - ' + currentQueue" :visible.sync="deadOpen" width="860px" append-to-body>
      <el-table v-loading="deadLoading" :data="deadList" border size="mini" max-height="420">
        <el-table-column label="死信ID" align="center" prop="id" width="160" show-overflow-tooltip />
        <el-table-column label="原始消息ID" align="center" prop="originId" width="160" show-overflow-tooltip />
        <el-table-column label="失败原因" align="center" prop="error" min-width="160" show-overflow-tooltip />
        <el-table-column label="消息体" align="center" min-width="220">
          <template slot-scope="scope">
            <el-popover trigger="hover" placement="left" width="500">
              <pre class="payload-preview">{{ prettyPayload(scope.row) }}</pre>
              <span slot="reference" class="payload-cell">{{ scope.row.payload || scope.row.raw }}</span>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="130" fixed="right">
          <template slot-scope="scope">
            <el-button v-hasPermi="['lawyers:queueMonitor:replay']" type="text" size="mini" icon="el-icon-refresh-left" @click="handleReplay(scope.row)">重投</el-button>
            <el-button v-hasPermi="['lawyers:queueMonitor:replay']" type="text" size="mini" icon="el-icon-delete" style="color:#C63D4A" @click="handleRemove(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="deadTotal > 0"
        :total="deadTotal"
        :page.sync="deadQuery.pageNum"
        :limit.sync="deadQuery.pageSize"
        @pagination="getDeadList"
      />
      <div slot="footer">
        <el-button size="small" @click="deadOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listQueue, listDeadLetter, replayDeadLetter, removeDeadLetter } from '@/api/lawyers/queueMonitor'

export default {
  name: 'QueueMonitor',
  data() {
    return {
      loading: false,
      queueList: [],
      timer: null,
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
    this.timer = setInterval(() => {
      if (!this.deadOpen) {
        this.getList(true)
      }
    }, 10000)
  },
  beforeDestroy() {
    if (this.timer) {
      clearInterval(this.timer)
    }
  },
  methods: {
    getList(silent) {
      if (!silent) {
        this.loading = true
      }
      listQueue().then(response => {
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
      this.$modal.confirm('确认将该死信回投到原队列重新消费？消费端幂等，不会重复生效。').then(() => {
        return replayDeadLetter(this.currentQueue, row.id)
      }).then(() => {
        this.$modal.msgSuccess('重投成功')
        this.getDeadList()
        this.getList(true)
      }).catch(() => {})
    },
    handleRemove(row) {
      this.$modal.confirm('确认删除该死信记录？删除后不可恢复。').then(() => {
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
.card-header {
  font-weight: 600;
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
</style>
