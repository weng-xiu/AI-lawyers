<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-click="handleTabChange">
        <!-- 知识审核 -->
        <el-tab-pane label="知识审核" name="knowledge">
          <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="90px">
            <el-form-item label="标题" prop="title">
              <el-input v-model="queryParams.title" placeholder="知识标题" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
            </el-form-item>
            <el-form-item label="审核状态" prop="auditStatus">
              <el-select v-model="queryParams.auditStatus" placeholder="全部" clearable style="width: 130px">
                <el-option label="待审核" value="0" />
                <el-option label="审核通过" value="1" />
                <el-option label="审核不通过" value="2" />
              </el-select>
            </el-form-item>
            <el-form-item label="效力状态" prop="validStatus">
              <el-select v-model="queryParams.validStatus" placeholder="全部" clearable style="width: 130px">
                <el-option label="现行有效" value="1" />
                <el-option label="已失效/废止" value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
              <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loading" :data="knowledgeList" border size="small">
            <el-table-column label="ID" align="center" prop="knowledgeId" width="70" />
            <el-table-column label="标题" align="center" prop="title" min-width="200" show-overflow-tooltip />
            <el-table-column label="法律法规" align="center" prop="lawName" width="160" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.lawName || '-' }}</template>
            </el-table-column>
            <el-table-column label="条款号" align="center" prop="articleNo" width="100">
              <template slot-scope="scope">{{ scope.row.articleNo || '-' }}</template>
            </el-table-column>
            <el-table-column label="版本" align="center" prop="publishVersion" width="70">
              <template slot-scope="scope">v{{ scope.row.publishVersion || 1 }}</template>
            </el-table-column>
            <el-table-column label="审核状态" align="center" width="100">
              <template slot-scope="scope">
                <el-tag size="mini" :type="auditTag(scope.row.auditStatus)">{{ auditText(scope.row.auditStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="上架" align="center" width="80">
              <template slot-scope="scope">
                <el-tag size="mini" :type="scope.row.status === '0' ? 'success' : 'info'">{{ scope.row.status === '0' ? '上架' : '下架' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="效力" align="center" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="(scope.row.validStatus || '1') === '1' ? 'success' : 'danger'">
                  {{ (scope.row.validStatus || '1') === '1' ? '有效' : '失效' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="生效日期" align="center" prop="effectiveDate" width="110">
              <template slot-scope="scope">{{ scope.row.effectiveDate ? scope.row.effectiveDate.substring(0, 10) : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="250" fixed="right">
              <template slot-scope="scope">
                <el-button v-if="scope.row.auditStatus !== '0'" v-hasPermi="['lawyers:knowledge:audit:submit']" type="text" size="mini" @click="handleSubmit(scope.row)">提交审核</el-button>
                <template v-if="scope.row.auditStatus === '0'">
                  <el-button v-hasPermi="['lawyers:knowledge:audit:approve']" type="text" size="mini" style="color:#2B8C6E" @click="openOpinion('approve', scope.row)">通过</el-button>
                  <el-button v-hasPermi="['lawyers:knowledge:audit:approve']" type="text" size="mini" style="color:#C63D4A" @click="openOpinion('reject', scope.row)">驳回</el-button>
                </template>
                <el-button
                  v-if="scope.row.auditStatus === '1' && scope.row.status !== '0'"
                  v-hasPermi="['lawyers:knowledge:audit:approve']"
                  type="text" size="mini" @click="openOpinion('publish', scope.row)">发布</el-button>
                <el-button
                  v-if="scope.row.status === '0'"
                  v-hasPermi="['lawyers:knowledge:audit:offline']"
                  type="text" size="mini" @click="openOpinion('offline', scope.row)">下线</el-button>
              </template>
            </el-table-column>
          </el-table>

          <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="getList" />
        </el-tab-pane>

        <!-- 审核流水 -->
        <el-tab-pane label="审核发布流水" name="log">
          <el-form size="small" :inline="true">
            <el-form-item label="知识ID">
              <el-input v-model="logQuery.knowledgeId" placeholder="知识ID" clearable style="width: 130px" @keyup.enter.native="getLogs" />
            </el-form-item>
            <el-form-item label="动作">
              <el-select v-model="logQuery.action" placeholder="全部" clearable style="width: 130px">
                <el-option label="提交" value="SUBMIT" />
                <el-option label="通过" value="APPROVE" />
                <el-option label="驳回" value="REJECT" />
                <el-option label="发布" value="PUBLISH" />
                <el-option label="下线" value="OFFLINE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" size="mini" icon="el-icon-search" @click="getLogs">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table v-loading="logLoading" :data="logList" border size="small">
            <el-table-column label="ID" align="center" prop="auditId" width="80" />
            <el-table-column label="知识ID" align="center" prop="knowledgeId" width="90" />
            <el-table-column label="标题" align="center" prop="title" min-width="180" show-overflow-tooltip />
            <el-table-column label="动作" align="center" width="90">
              <template slot-scope="scope">
                <el-tag size="mini" :type="actionTag(scope.row.action)">{{ actionText(scope.row.action) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="版本" align="center" prop="publishVersion" width="70" />
            <el-table-column label="状态变化" align="center" width="120">
              <template slot-scope="scope">{{ scope.row.fromStatus || '-' }} → {{ scope.row.toStatus || '-' }}</template>
            </el-table-column>
            <el-table-column label="审核意见" align="center" prop="auditOpinion" min-width="180" show-overflow-tooltip>
              <template slot-scope="scope">{{ scope.row.auditOpinion || '-' }}</template>
            </el-table-column>
            <el-table-column label="审核人" align="center" prop="auditor" width="120" />
            <el-table-column label="审核时间" align="center" prop="auditTime" width="160">
              <template slot-scope="scope">{{ parseTime(scope.row.auditTime) }}</template>
            </el-table-column>
          </el-table>
          <pagination v-show="logTotal > 0" :total="logTotal" :page.sync="logQuery.pageNum" :limit.sync="logQuery.pageSize" @pagination="getLogs" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 审核意见弹窗 -->
    <el-dialog :title="opinionTitle" :visible.sync="opinionOpen" width="460px" append-to-body>
      <el-form label-width="90px" size="small">
        <el-form-item label="知识标题">
          <span>{{ opinionRow && opinionRow.title }}</span>
        </el-form-item>
        <el-form-item :label="opinionAction === 'reject' ? '驳回原因' : '审核意见'">
          <el-input v-model="opinionText" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" size="small" @click="submitOpinion">确 定</el-button>
        <el-button size="small" @click="opinionOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listKnowledge } from '@/api/lawyers/knowledge'
import { listAuditLog, submitAudit, approveKnowledge, rejectKnowledge, publishKnowledge, offlineKnowledge } from '@/api/lawyers/knowledgeAudit'

export default {
  name: 'KnowledgeAudit',
  data() {
    return {
      activeTab: 'knowledge',
      loading: false,
      total: 0,
      knowledgeList: [],
      queryParams: { pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined },
      logLoading: false,
      logTotal: 0,
      logList: [],
      logQuery: { pageNum: 1, pageSize: 10, knowledgeId: undefined, action: undefined },
      opinionOpen: false,
      opinionAction: '',
      opinionText: '',
      opinionRow: null
    }
  },
  created() { this.getList() },
  computed: {
    opinionTitle() {
      return { approve: '审核通过', reject: '审核驳回', publish: '发布上架', offline: '知识下线' }[this.opinionAction] || '审核'
    }
  },
  methods: {
    auditText(s) { return { '0': '待审核', '1': '通过', '2': '不通过' }[String(s)] || '未提交' },
    auditTag(s) { return { '0': 'warning', '1': 'success', '2': 'danger' }[String(s)] || 'info' },
    actionText(a) { return { SUBMIT: '提交', APPROVE: '通过', REJECT: '驳回', PUBLISH: '发布', OFFLINE: '下线' }[a] || a },
    actionTag(a) { return { SUBMIT: 'info', APPROVE: 'success', REJECT: 'danger', PUBLISH: '', OFFLINE: 'warning' }[a] || 'info' },
    getList() {
      this.loading = true
      listKnowledge(this.queryParams).then(res => {
        this.knowledgeList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    handleQuery() { this.queryParams.pageNum = 1; this.getList() },
    resetQuery() {
      this.resetForm('queryForm')
      this.queryParams = { pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined }
      this.handleQuery()
    },
    handleTabChange(tab) {
      if (tab.name === 'log' && !this.logList.length) { this.getLogs() }
    },
    getLogs() {
      this.logLoading = true
      listAuditLog(this.logQuery).then(res => {
        this.logList = res.rows || []
        this.logTotal = res.total || 0
        this.logLoading = false
      }).catch(() => { this.logLoading = false })
    },
    handleSubmit(row) {
      this.$modal.confirm('确认提交该知识条目进入审核流程吗？').then(() => submitAudit(row.knowledgeId)).then(() => {
        this.$modal.msgSuccess('已提交审核')
        this.getList()
      }).catch(() => {})
    },
    openOpinion(action, row) {
      this.opinionAction = action
      this.opinionRow = row
      this.opinionText = ''
      this.opinionOpen = true
    },
    submitOpinion() {
      const id = this.opinionRow.knowledgeId
      const opinion = this.opinionText
      const run = {
        approve: approveKnowledge,
        reject: rejectKnowledge,
        publish: publishKnowledge,
        offline: offlineKnowledge
      }[this.opinionAction]
      if (this.opinionAction === 'reject' && !opinion) {
        this.$modal.msgWarning('请填写驳回原因')
        return
      }
      run(id, opinion).then(() => {
        this.$modal.msgSuccess('操作成功')
        this.opinionOpen = false
        this.getList()
      })
    }
  }
}
</script>
