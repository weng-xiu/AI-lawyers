<template>
  <div class="shift-change">
    <el-card shadow="never" class="search-card">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="审批视角">
          <el-radio-group v-model="scope" size="small" aria-label="审批视角" @change="handleScopeChange">
            <el-radio-button label="all">全部申请</el-radio-button>
            <el-radio-button label="mine">我的申请</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width:130px">
            <el-option label="待审批" value="0" />
            <el-option label="已通过" value="1" />
            <el-option label="已驳回" value="2" />
            <el-option label="已撤销" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.changeType" placeholder="全部" clearable style="width:130px">
            <el-option label="互换换班" value="SWAP" />
            <el-option label="转班" value="TRANSFER" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请时间">
          <el-date-picker
            v-model="dateRange"
            style="width:250px"
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
          <el-button type="success" plain icon="el-icon-plus" size="mini" @click="openApply">申请换班</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="requestList" border size="small">
        <el-table-column label="申请ID" align="center" prop="requestId" width="80" />
        <el-table-column label="申请人 / 原班次" align="center" min-width="180">
          <template slot-scope="scope">
            <div>{{ scope.row.applicantAgentName || '-' }}</div>
            <div class="sc-sub">
              {{ scope.row.applicantScheduleDate || '-' }}
              <span v-if="scope.row.applicantShiftName"> · {{ scope.row.applicantShiftName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="typeTag(scope.row.changeType)">{{ typeLabel(scope.row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接收坐席 / 目标班次" align="center" min-width="180">
          <template slot-scope="scope">
            <div>{{ scope.row.targetAgentName || '-' }}</div>
            <div class="sc-sub">
              <span v-if="scope.row.targetShiftName">{{ scope.row.targetScheduleDate }} · {{ scope.row.targetShiftName }}</span>
              <span v-else-if="scope.row.changeType === 'TRANSFER'">接收当日无排班</span>
              <span v-else>-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="申请原因" align="center" prop="reason" show-overflow-tooltip min-width="150">
          <template slot-scope="scope">{{ scope.row.reason || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center" width="100">
          <template slot-scope="scope">
            <el-tag size="mini" :type="statusTag(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批信息" align="center" min-width="160">
          <template slot-scope="scope">
            <div v-if="scope.row.approver">{{ scope.row.approver }}</div>
            <div class="sc-sub">{{ scope.row.approveTime || scope.row.approveRemark || '待审批' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" align="center" prop="createTime" width="160">
          <template slot-scope="scope">{{ scope.row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="200">
          <template slot-scope="scope">
            <template v-if="isPending(scope.row.status)">
              <el-button type="text" size="mini" icon="el-icon-check" @click="openApprove(scope.row, 'approve')">通过</el-button>
              <el-button type="text" size="mini" icon="el-icon-close" style="color:#C63D4A" @click="openApprove(scope.row, 'reject')">驳回</el-button>
              <el-button type="text" size="mini" icon="el-icon-refresh-left" @click="handleCancel(scope.row)">撤销</el-button>
            </template>
            <span v-else class="sc-sub">已处理</span>
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

    <!-- 申请换班 -->
    <el-dialog title="申请换班" :visible.sync="applyOpen" width="560px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="申请阶段不改动排班，班组长审批通过后才生效；同一排班同时只能有一条待审批申请。"
        class="sc-alert"
      ></el-alert>
      <el-form ref="applyForm" :model="applyForm" :rules="applyRules" label-width="100px" size="small">
        <el-form-item label="变更类型" prop="changeType">
          <el-radio-group v-model="applyForm.changeType" @change="handleTypeChange">
            <el-radio label="SWAP">互换换班（与对方换班次）</el-radio>
            <el-radio label="TRANSFER">转班（整条排班转给他人）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="我的排班" prop="applicantScheduleId">
          <el-select
            v-model="applyForm.applicantScheduleId"
            placeholder="请选择需变更的本人排班"
            filterable
            style="width:100%"
            @change="handleApplicantScheduleChange"
          >
            <el-option v-for="row in mySchedules" :key="row.scheduleId" :label="myScheduleLabel(row)" :value="row.scheduleId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="applyForm.changeType === 'SWAP'" label="互换对象" prop="targetScheduleId">
          <el-select v-model="applyForm.targetScheduleId" placeholder="请选择对方同日排班" filterable style="width:100%">
            <el-option v-for="row in swapTargets" :key="row.scheduleId" :label="swapTargetLabel(row)" :value="row.scheduleId" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="applyForm.changeType === 'TRANSFER'" label="接收坐席" prop="targetAgentId">
          <el-select v-model="applyForm.targetAgentId" placeholder="请选择接收坐席（当日需无排班）" filterable style="width:100%">
            <el-option v-for="agent in agentList" :key="agent.agentId" :label="agent.agentName + '（' + agent.agentId + '）'" :value="agent.agentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请原因" prop="reason">
          <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="如临时外出、家中有事等" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" :loading="submitting" @click="submitApply">提 交</el-button>
        <el-button size="small" @click="applyOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 审批通过/驳回 -->
    <el-dialog :title="approveTitle" :visible.sync="approveOpen" width="480px" append-to-body>
      <el-alert
        v-if="approveForm.action === 'approve'"
        type="warning"
        :closable="false"
        show-icon
        title="审批通过后立即改写排班（互换换班次 / 转班换坐席），请确认双方当日状态无误。"
        class="sc-alert"
      ></el-alert>
      <el-form ref="approveForm" :model="approveForm" label-width="90px" size="small">
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.approveRemark" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="approveForm.action === 'approve' ? '可选，如「已与双方确认」' : '建议填写驳回原因'" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button :type="approveForm.action === 'approve' ? 'primary' : 'danger'" size="small" :loading="submitting" @click="submitApprove">
          {{ approveForm.action === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
        <el-button size="small" @click="approveOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listShiftChange, listMyShiftChange, listMySchedules, listSwapTargets,
  applyShiftChange, approveShiftChange, rejectShiftChange, cancelShiftChange
} from '@/api/lawyers/shiftChange'
import { listAgent } from '@/api/lawyers/callCenter'

const STATUS_TEXT = { '0': '待审批', '1': '已通过', '2': '已驳回', '3': '已撤销' }
const STATUS_TAG = { '0': 'warning', '1': 'success', '2': 'danger', '3': 'info' }
const TYPE_TEXT = { SWAP: '互换换班', TRANSFER: '转班' }

export default {
  name: 'ShiftChange',
  data() {
    return {
      scope: 'all',
      loading: false,
      submitting: false,
      requestList: [],
      total: 0,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        status: undefined,
        changeType: undefined
      },
      applyOpen: false,
      approveOpen: false,
      approveTitle: '审批换班申请',
      applyForm: {},
      approveForm: {},
      applyRules: {
        changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
        applicantScheduleId: [{ required: true, message: '请选择本人排班', trigger: 'change' }],
        targetScheduleId: [{ required: true, message: '请选择互换对象', trigger: 'change' }],
        targetAgentId: [{ required: true, message: '请选择接收坐席', trigger: 'change' }],
        reason: [{ required: true, message: '请填写申请原因', trigger: 'blur' }]
      },
      mySchedules: [],
      swapTargets: [],
      agentList: []
    }
  },
  created() {
    this.getList()
  },
  methods: {
    // ==================== 列表 ====================
    getList() {
      this.loading = true
      const query = this.buildQuery()
      const api = this.scope === 'mine' ? listMyShiftChange : listShiftChange
      api(query).then(res => {
        this.requestList = res.rows || []
        this.total = res.total || 0
        this.loading = false
      }).catch(() => {
        this.loading = false
      })
    },
    buildQuery() {
      const query = Object.assign({}, this.queryParams, this.addDateRange(this.queryParams, this.dateRange))
      if (this.scope === 'mine') {
        // 我的申请由服务端按登录账号绑定的坐席过滤，忽略前端传入的申请人
        delete query.applicantAgentId
      }
      return query
    },
    handleScopeChange() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.queryParams.status = undefined
      this.queryParams.changeType = undefined
      this.queryParams.pageNum = 1
      this.getList()
    },
    isPending(status) {
      return String(status) === '0'
    },
    statusLabel(status) {
      return STATUS_TEXT[String(status)] || '-'
    },
    statusTag(status) {
      return STATUS_TAG[String(status)] || 'info'
    },
    typeLabel(type) {
      return TYPE_TEXT[type] || '-'
    },
    typeTag(type) {
      return type === 'TRANSFER' ? 'warning' : ''
    },

    // ==================== 申请 ====================
    openApply() {
      this.applyForm = {
        changeType: 'SWAP',
        applicantScheduleId: undefined,
        targetScheduleId: undefined,
        targetAgentId: undefined,
        reason: ''
      }
      this.swapTargets = []
      this.applyOpen = true
      this.loadMySchedules()
      this.loadAgents()
      this.$nextTick(() => {
        this.$refs.applyForm && this.$refs.applyForm.clearValidate()
      })
    },
    loadMySchedules() {
      listMySchedules().then(res => {
        this.mySchedules = (res && res.data) || []
      })
    },
    loadAgents() {
      listAgent({ pageSize: 999, status: '1' }).then(res => {
        this.agentList = res.rows || []
      })
    },
    handleTypeChange() {
      // 互换/转班的目标对象语义不同，切换后清空避免脏值提交
      this.applyForm.targetScheduleId = undefined
      this.applyForm.targetAgentId = undefined
      this.swapTargets = []
      if (this.applyForm.changeType === 'SWAP') {
        this.loadSwapTargets()
      }
    },
    handleApplicantScheduleChange() {
      this.applyForm.targetScheduleId = undefined
      this.swapTargets = []
      if (this.applyForm.changeType === 'SWAP') {
        this.loadSwapTargets()
      }
    },
    loadSwapTargets() {
      if (!this.applyForm.applicantScheduleId) return
      listSwapTargets(this.applyForm.applicantScheduleId).then(res => {
        this.swapTargets = (res && res.data) || []
      })
    },
    currentApplicantSchedule() {
      return this.mySchedules.find(row => row.scheduleId === this.applyForm.applicantScheduleId) || null
    },
    myScheduleLabel(row) {
      const shift = row.shiftName || '-'
      const range = row.startTime && row.endTime ? '（' + row.startTime + '-' + row.endTime + '）' : ''
      return (row.scheduleDate || '') + ' ' + shift + range
    },
    swapTargetLabel(row) {
      const shift = row.shiftName || '-'
      const range = row.startTime && row.endTime ? '（' + row.startTime + '-' + row.endTime + '）' : ''
      return (row.agentName || ('坐席' + row.agentId)) + ' ' + shift + range
    },
    buildApplyPayload() {
      const payload = {
        changeType: this.applyForm.changeType,
        applicantScheduleId: this.applyForm.applicantScheduleId,
        reason: this.applyForm.reason
      }
      if (this.applyForm.changeType === 'SWAP') {
        payload.targetScheduleId = this.applyForm.targetScheduleId
      } else {
        payload.targetAgentId = this.applyForm.targetAgentId
      }
      return payload
    },
    submitApply() {
      this.$refs.applyForm.validate(valid => {
        if (!valid) return
        this.submitting = true
        applyShiftChange(this.buildApplyPayload()).then(() => {
          this.submitting = false
          this.$modal.msgSuccess('申请已提交，等待班组长审批')
          this.applyOpen = false
          this.getList()
        }).catch(() => {
          this.submitting = false
        })
      })
    },

    // ==================== 审批 ====================
    openApprove(row, action) {
      this.approveForm = {
        requestId: row.requestId,
        action: action === 'reject' ? 'reject' : 'approve',
        approveRemark: ''
      }
      this.approveTitle = this.approveForm.action === 'approve' ? '审批通过' : '审批驳回'
      this.approveOpen = true
    },
    submitApprove() {
      this.submitting = true
      const api = this.approveForm.action === 'approve' ? approveShiftChange : rejectShiftChange
      api(this.approveForm.requestId, this.approveForm.approveRemark).then(() => {
        this.submitting = false
        this.approveOpen = false
        this.$modal.msgSuccess(this.approveForm.action === 'approve' ? '已通过，排班已同步生效' : '已驳回')
        this.getList()
      }).catch(() => {
        this.submitting = false
      })
    },
    handleCancel(row) {
      this.$modal.dangerConfirm({
        title: '撤销申请确认',
        confirmText: '确认撤销',
        actionType: 'caution',
        content: '将撤销对 ' + (row.applicantScheduleDate || '该排班') + ' 的换班申请',
        tip: '撤销后需重新提交申请'
      }).then(() => {
        return cancelShiftChange(row.requestId)
      }).then(() => {
        this.$modal.msgSuccess('已撤销')
        this.getList()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.shift-change { padding: 4px 0; }
.search-card { margin-bottom: 12px; }
.sc-sub { color: #909399; font-size: 12px; }
.sc-alert { margin-bottom: 12px; }
</style>
