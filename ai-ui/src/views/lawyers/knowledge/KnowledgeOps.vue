<template>
  <el-dialog
    title="知识库运营分析"
    :visible="visible"
    width="920px"
    append-to-body
    custom-class="knowledge-ops-dialog"
    @open="handleOpen"
    @close="handleClose"
  >
    <div v-loading="overviewLoading">
      <el-form :inline="true" size="small" class="ops-filter">
        <el-form-item label="统计区间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            unlink-panels
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="yyyy-MM-dd"
            @change="fetchAll"
          />
        </el-form-item>
        <el-form-item>
          <el-button icon="el-icon-refresh" @click="fetchAll">刷新</el-button>
        </el-form-item>
        <span v-if="overview.beginTime" class="ops-range-hint">
          统计口径：{{ overview.beginTime }} ~ {{ overview.endTime }}
        </span>
      </el-form>

      <el-alert
        v-if="overview.available === false"
        type="warning"
        show-icon
        :closable="false"
        title="查询日志表尚未创建，运营数据暂不可用"
        style="margin-bottom: 12px;"
      >
        <span>请先在数据库执行 sql/ai_system_knowledge_query_log_20261030.sql；建表后新的检索才会开始记录，历史区间不会有数据。</span>
      </el-alert>

      <el-row :gutter="12" class="ops-cards">
        <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="8" :md="4">
          <div class="ops-card" :class="card.tone">
            <div class="ops-card-value">{{ card.value }}</div>
            <div class="ops-card-label">{{ card.label }}</div>
          </div>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab" class="ops-tabs">
        <el-tab-pane label="未命中问句排行" name="unmatched">
          <el-table v-loading="unmatchedLoading" :data="unmatchedList" size="small">
            <el-table-column label="问句" prop="queryText" min-width="260" show-overflow-tooltip />
            <el-table-column label="次数" prop="askCount" width="70" align="center" />
            <el-table-column label="首次出现" width="150" align="center">
              <template slot-scope="scope">{{ parseTime(scope.row.firstTime) }}</template>
            </el-table-column>
            <el-table-column label="最近出现" width="150" align="center">
              <template slot-scope="scope">{{ parseTime(scope.row.lastTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="96" align="center">
              <template slot-scope="scope">
                <el-tag v-if="scope.row.converted === 1" type="success" size="mini">已转草稿</el-tag>
                <el-tag v-else type="warning" size="mini">待处理</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template slot-scope="scope">
                <el-button
                  type="text"
                  size="mini"
                  v-hasPermi="['lawyers:knowledge:add']"
                  :disabled="scope.row.converted === 1 || draftingHash === scope.row.queryHash"
                  :loading="draftingHash === scope.row.queryHash"
                  @click="handleDraft(scope.row)"
                >转草稿</el-button>
              </template>
            </el-table-column>
            <template slot="empty">
              <span class="ops-empty">所选区间暂无未命中问句</span>
            </template>
          </el-table>
          <pagination
            v-show="unmatchedTotal > 0"
            :total="unmatchedTotal"
            :page.sync="query.pageNum"
            :limit.sync="query.pageSize"
            @pagination="fetchUnmatched"
          />
        </el-tab-pane>

        <el-tab-pane label="高频命中知识" name="top">
          <el-table v-loading="topLoading" :data="topList" size="small">
            <el-table-column label="排名" type="index" width="60" align="center" />
            <el-table-column label="知识标题" min-width="220" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.title || '（知识已删除）' }}</template>
            </el-table-column>
            <el-table-column label="知识ID" prop="knowledgeId" width="90" align="center" />
            <el-table-column label="作为首条命中次数" prop="hitCount" width="150" align="center" />
            <el-table-column label="最近命中" width="150" align="center">
              <template slot-scope="scope">{{ parseTime(scope.row.lastTime) }}</template>
            </el-table-column>
            <template slot="empty">
              <span class="ops-empty">所选区间暂无命中记录</span>
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
    <div slot="footer" class="dialog-footer">
      <el-button @click="handleClose">关 闭</el-button>
    </div>
  </el-dialog>
</template>

<script>
import { getKnowledgeOpsOverview, listUnmatchedQuestions, listTopKnowledge, draftFromQuestion } from '@/api/lawyers/knowledge'

export default {
  name: 'KnowledgeOps',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      overview: {},
      overviewLoading: false,
      dateRange: [],
      activeTab: 'unmatched',
      unmatchedList: [],
      unmatchedLoading: false,
      unmatchedTotal: 0,
      query: {
        pageNum: 1,
        pageSize: 10
      },
      topList: [],
      topLoading: false,
      draftingHash: null
    }
  },
  computed: {
    cards() {
      const o = this.overview || {}
      const num = v => (v === undefined || v === null ? 0 : v)
      return [
        { label: '总问句', value: num(o.total), tone: 'primary' },
        { label: '命中问句', value: num(o.hitCount), tone: 'success' },
        { label: '未命中', value: num(o.missCount), tone: 'warning' },
        { label: '待处理', value: num(o.pendingCount), tone: 'danger' },
        { label: '命中率', value: num(o.hitRate) + '%', tone: 'info' },
        { label: '平均耗时', value: num(o.avgLatencyMs) + ' ms', tone: 'info' }
      ]
    }
  },
  methods: {
    handleOpen() {
      if (!this.dateRange || this.dateRange.length === 0) {
        this.dateRange = this.defaultRange()
      }
      this.query.pageNum = 1
      this.fetchAll()
    },
    handleClose() {
      this.$emit('update:visible', false)
    },
    defaultRange() {
      const end = new Date()
      const start = new Date()
      start.setDate(end.getDate() - 6)
      return [this.formatDate(start), this.formatDate(end)]
    },
    formatDate(date) {
      const pad = n => (n < 10 ? '0' + n : '' + n)
      return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
    },
    buildParams() {
      const range = this.dateRange || []
      return {
        beginTime: range[0] ? range[0] + ' 00:00:00' : undefined,
        endTime: range[1] ? range[1] + ' 23:59:59' : undefined
      }
    },
    fetchAll() {
      this.fetchOverview().then(available => {
        if (available) {
          this.fetchUnmatched()
          this.fetchTop()
        } else {
          this.unmatchedList = []
          this.unmatchedTotal = 0
          this.topList = []
        }
      })
    },
    fetchOverview() {
      this.overviewLoading = true
      return getKnowledgeOpsOverview(this.buildParams()).then(response => {
        this.overview = response.data || {}
        return this.overview.available !== false
      }).catch(() => {
        this.overview = { available: false }
        return false
      }).finally(() => {
        this.overviewLoading = false
      })
    },
    fetchUnmatched() {
      this.unmatchedLoading = true
      const params = Object.assign({}, this.buildParams(), {
        pageNum: this.query.pageNum,
        pageSize: this.query.pageSize
      })
      listUnmatchedQuestions(params).then(response => {
        this.unmatchedList = response.rows || []
        this.unmatchedTotal = response.total || 0
      }).finally(() => {
        this.unmatchedLoading = false
      })
    },
    fetchTop() {
      this.topLoading = true
      listTopKnowledge(Object.assign({}, this.buildParams(), { limit: 10 })).then(response => {
        this.topList = response.data || []
      }).finally(() => {
        this.topLoading = false
      })
    },
    handleDraft(row) {
      if (!row || !row.queryText) {
        return
      }
      const text = row.queryText.length > 120
        ? row.queryText.substring(0, 120) + '…' : row.queryText
      this.$confirm(`将根据该问句生成一条待审核知识草稿，需人工审核后才会上架：\n${text}`, '转知识草稿', {
        confirmButtonText: '生成草稿',
        cancelButtonText: '取 消',
        type: 'info'
      }).then(() => {
        this.draftingHash = row.queryHash
        return draftFromQuestion({ question: row.queryText, queryHash: row.queryHash })
      }).then(response => {
        this.$modal.msgSuccess(response.msg || '已生成待审核知识草稿')
        this.fetchAll()
      }).catch(() => {}).finally(() => {
        this.draftingHash = null
      })
    }
  }
}
</script>

<style>
.knowledge-ops-dialog {
  max-width: 94vw;
}
</style>

<style scoped>
.ops-filter {
  margin-bottom: 4px;
}
.ops-range-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.ops-cards {
  margin-bottom: 4px;
}
.ops-card {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
  background: #fff;
  text-align: center;
}
.ops-card-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.3;
  color: #303133;
}
.ops-card-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.ops-card.primary .ops-card-value {
  color: #409EFF;
}
.ops-card.success .ops-card-value {
  color: #67C23A;
}
.ops-card.warning .ops-card-value {
  color: #E6A23C;
}
.ops-card.danger .ops-card-value {
  color: #F56C6C;
}
.ops-card.info .ops-card-value {
  color: #606266;
}
.ops-empty {
  color: #909399;
  font-size: 13px;
}
@media (max-width: 768px) {
  .ops-card-value {
    font-size: 18px;
  }
}
</style>
