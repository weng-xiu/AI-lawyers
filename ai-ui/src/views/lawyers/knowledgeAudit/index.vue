<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-click="handleTabChange">
        <!-- 知识审核 -->
        <el-tab-pane label="知识审核" name="knowledge">
          <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable；同页两张表各自 persist-key -->
          <ProTable
            :columns="columns"
            :query-form-items="queryFormItems"
            :query-params="queryParams"
            :data="knowledgeList"
            :loading="loading"
            :total="total"
            :show-search.sync="showSearch"
            :table-attrs="{ border: true }"
            persist-key="knowledgeAudit:knowledge"
            label-width="90px"
            empty-text="暂无待审核知识"
            empty-hint="知识提交审核后将在此排队，通过后才对 AI 回答生效"
            @query="handleQuery"
            @reset="resetQuery"
            @pagination="getList">
            <template #column-lawName="{ row }">{{ row.lawName || '-' }}</template>
            <template #column-articleNo="{ row }">{{ row.articleNo || '-' }}</template>
            <template #column-publishVersion="{ row }">v{{ row.publishVersion || 1 }}</template>
            <template #column-auditStatus="{ row }">
              <el-tag size="mini" :type="auditTag(row.auditStatus)">{{ auditText(row.auditStatus) }}</el-tag>
            </template>
            <template #column-status="{ row }">
              <el-tag size="mini" :type="row.status === '0' ? 'success' : 'info'">{{ row.status === '0' ? '上架' : '下架' }}</el-tag>
            </template>
            <template #column-validStatus="{ row }">
              <el-tag size="mini" :type="validTag(row.validStatus)">{{ validText(row.validStatus) }}</el-tag>
            </template>
            <template #column-effectiveDate="{ row }">{{ row.effectiveDate ? row.effectiveDate.substring(0, 10) : '-' }}</template>
            <template #column-action="{ row }">
              <el-button v-if="row.auditStatus !== '0'" v-hasPermi="['lawyers:knowledge:audit:submit']" type="text" size="mini" @click="handleSubmit(row)">提交审核</el-button>
              <template v-if="row.auditStatus === '0'">
                <el-button v-hasPermi="['lawyers:knowledge:audit:approve']" type="text" size="mini" style="color:#2B8C6E" @click="openOpinion('approve', row)">通过</el-button>
                <el-button v-hasPermi="['lawyers:knowledge:audit:approve']" type="text" size="mini" style="color:#C63D4A" @click="openOpinion('reject', row)">驳回</el-button>
              </template>
              <el-button
                v-if="row.auditStatus === '1' && row.status !== '0'"
                v-hasPermi="['lawyers:knowledge:audit:approve']"
                type="text" size="mini" @click="openOpinion('publish', row)">发布</el-button>
              <el-button
                v-if="row.status === '0'"
                v-hasPermi="['lawyers:knowledge:audit:offline']"
                type="text" size="mini" @click="openOpinion('offline', row)">下线</el-button>
              <el-button
                v-hasPermi="['lawyers:knowledge:edit']"
                type="text" size="mini" @click="openValid(row)">标记效力</el-button>
            </template>
          </ProTable>
        </el-tab-pane>

        <!-- 审核流水 -->
        <el-tab-pane label="审核发布流水" name="log">
          <ProTable
            :columns="logColumns"
            :query-form-items="logQueryFormItems"
            :query-params="logQuery"
            :data="logList"
            :loading="logLoading"
            :total="logTotal"
            :show-search.sync="logShowSearch"
            :table-attrs="{ border: true }"
            persist-key="knowledgeAudit:log"
            label-width="90px"
            empty-text="暂无审核流水"
            empty-hint="提交/通过/驳回/发布/下线操作都会在此留痕"
            @query="getLogs"
            @reset="resetLogQuery"
            @pagination="getLogs">
            <template #column-action="{ row }">
              <el-tag size="mini" :type="actionTag(row.action)">{{ actionText(row.action) }}</el-tag>
            </template>
            <template #column-statusChange="{ row }">{{ row.fromStatus || '-' }} → {{ row.toStatus || '-' }}</template>
            <template #column-auditOpinion="{ row }">{{ row.auditOpinion || '-' }}</template>
            <template #column-auditTime="{ row }">{{ parseTime(row.auditTime) }}</template>
          </ProTable>
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

    <!-- 效力标记弹窗 -->
    <el-dialog title="标记法律效力" :visible.sync="validOpen" width="460px" append-to-body>
      <el-form ref="validForm" :model="validForm" :rules="validRules" label-width="90px" size="small">
        <el-form-item label="知识标题">
          <span>{{ validRow && validRow.title }}</span>
        </el-form-item>
        <el-form-item label="效力状态" prop="validStatus">
          <el-select v-model="validForm.validStatus" placeholder="请选择效力状态" style="width: 100%">
            <el-option label="现行有效" value="1" />
            <el-option label="已修订" value="2" />
            <el-option label="已失效" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" prop="reason">
          <el-input v-model="validForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请说明效力变更原因，如新法替代、条款修订、废止等" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" size="small" @click="submitValid">确 定</el-button>
        <el-button size="small" @click="validOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listKnowledge, markValidStatus } from '@/api/lawyers/knowledge'
import { listAuditLog, submitAudit, approveKnowledge, rejectKnowledge, publishKnowledge, offlineKnowledge } from '@/api/lawyers/knowledgeAudit'

export default {
  name: 'KnowledgeAudit',
  data() {
    return {
      activeTab: 'knowledge',
      loading: false,
      showSearch: true,
      total: 0,
      knowledgeList: [],
      // V3：ProTable 列定义（审核/上架/效力/生效日期/操作走插槽）
      columns: [
        { prop: 'knowledgeId', label: 'ID', width: 70, align: 'center' },
        { prop: 'title', label: '标题', minWidth: 200, align: 'center', showOverflowTooltip: true },
        { prop: 'lawName', label: '法律法规', width: 160, align: 'center', slot: true, showOverflowTooltip: true },
        { prop: 'articleNo', label: '条款号', width: 100, align: 'center', slot: true },
        { prop: 'publishVersion', label: '版本', width: 70, align: 'center', slot: true },
        { prop: 'auditStatus', label: '审核状态', width: 100, align: 'center', slot: true },
        { prop: 'status', label: '上架', width: 80, align: 'center', slot: true },
        { prop: 'validStatus', label: '效力', width: 90, align: 'center', slot: true },
        { prop: 'effectiveDate', label: '生效日期', width: 110, align: 'center', slot: true },
        { prop: 'action', label: '操作', width: 320, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      queryParams: { pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined },
      logLoading: false,
      logShowSearch: true,
      logTotal: 0,
      logList: [],
      // V3：审核流水列定义（动作/状态变化/意见/时间走插槽）
      logColumns: [
        { prop: 'auditId', label: 'ID', width: 80, align: 'center' },
        { prop: 'knowledgeId', label: '知识ID', width: 90, align: 'center' },
        { prop: 'title', label: '标题', minWidth: 180, align: 'center', showOverflowTooltip: true },
        { prop: 'action', label: '动作', width: 90, align: 'center', slot: true },
        { prop: 'publishVersion', label: '版本', width: 70, align: 'center' },
        { prop: 'statusChange', label: '状态变化', width: 120, align: 'center', slot: true },
        { prop: 'auditOpinion', label: '审核意见', minWidth: 180, align: 'center', slot: true, showOverflowTooltip: true },
        { prop: 'auditor', label: '审核人', width: 120, align: 'center' },
        { prop: 'auditTime', label: '审核时间', width: 160, align: 'center', slot: true }
      ],
      logQuery: { pageNum: 1, pageSize: 10, knowledgeId: undefined, action: undefined },
      opinionOpen: false,
      opinionAction: '',
      opinionText: '',
      opinionRow: null,
      validOpen: false,
      validRow: null,
      validForm: { validStatus: '1', reason: '' },
      validRules: {
        validStatus: [{ required: true, message: '请选择效力状态', trigger: 'change' }],
        reason: [{ required: true, message: '请填写变更原因', trigger: 'blur' }]
      }
    }
  },
  created() { this.getList() },
  computed: {
    queryFormItems() {
      return [
        { prop: 'title', label: '标题', placeholder: '知识标题', width: 200 },
        {
          prop: 'auditStatus',
          label: '审核状态',
          type: 'select',
          placeholder: '全部',
          width: 130,
          options: [
            { label: '待审核', value: '0' },
            { label: '审核通过', value: '1' },
            { label: '审核不通过', value: '2' }
          ]
        },
        {
          prop: 'validStatus',
          label: '效力状态',
          type: 'select',
          placeholder: '全部',
          width: 130,
          options: [
            { label: '现行有效', value: '1' },
            { label: '已修订', value: '2' },
            { label: '已失效', value: '3' }
          ]
        }
      ]
    },
    logQueryFormItems() {
      return [
        { prop: 'knowledgeId', label: '知识ID', placeholder: '知识ID', width: 130 },
        {
          prop: 'action',
          label: '动作',
          type: 'select',
          placeholder: '全部',
          width: 130,
          options: [
            { label: '提交', value: 'SUBMIT' },
            { label: '通过', value: 'APPROVE' },
            { label: '驳回', value: 'REJECT' },
            { label: '发布', value: 'PUBLISH' },
            { label: '下线', value: 'OFFLINE' }
          ]
        }
      ]
    },
    opinionTitle() {
      return { approve: '审核通过', reject: '审核驳回', publish: '发布上架', offline: '知识下线' }[this.opinionAction] || '审核'
    }
  },
  methods: {
    auditText(s) { return { '0': '待审核', '1': '通过', '2': '不通过' }[String(s)] || '未提交' },
    auditTag(s) { return { '0': 'warning', '1': 'success', '2': 'danger' }[String(s)] || 'info' },
    validText(s) { return { '1': '现行有效', '2': '已修订', '3': '已失效' }[String(s || '1')] },
    validTag(s) { return { '1': 'success', '2': 'warning', '3': 'danger' }[String(s || '1')] },
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值并重查
      this.queryParams = { pageNum: 1, pageSize: 10, title: undefined, auditStatus: '0', validStatus: undefined }
      this.getList()
    },
    resetLogQuery() {
      this.logQuery = { pageNum: 1, pageSize: 10, knowledgeId: undefined, action: undefined }
      this.getLogs()
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '确认提交该知识条目进入审核流程吗？',
        tip: '提交后进入审核队列，审核通过前不会对 AI 回答生效'
      }).then(() => submitAudit(row.knowledgeId)).then(() => {
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
        this.opinionOpen = false
        this.getList()
        // U2：发布/下线互为逆操作，30 秒内可撤销
        const reverse = {
          publish: { fn: offlineKnowledge, done: '知识已重新下线' },
          offline: { fn: publishKnowledge, done: '知识已重新发布' }
        }[this.opinionAction]
        if (!reverse) {
          this.$modal.msgSuccess('操作成功')
          return
        }
        this.$modal.undo({
          message: this.opinionAction === 'publish' ? '知识已发布' : '知识已下线',
          undoSuccessText: reverse.done,
          onUndo: () => reverse.fn(id, '撤销操作').then(() => this.getList())
        })
      })
    },
    openValid(row) {
      this.validRow = row
      this.validForm = { validStatus: String(row.validStatus || '1'), reason: '' }
      this.validOpen = true
      this.$nextTick(() => { this.$refs.validForm && this.$refs.validForm.clearValidate() })
    },
    submitValid() {
      this.$refs.validForm.validate(valid => {
        if (!valid) { return }
        markValidStatus(this.validRow.knowledgeId, this.validForm.validStatus, this.validForm.reason).then(() => {
          this.$modal.msgSuccess('效力标记成功')
          this.validOpen = false
          this.getList()
        })
      })
    }
  }
}
</script>
