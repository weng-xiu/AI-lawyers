<template>
  <div class="app-container work-order-page">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card stat-pending" @click="filterByStatus('0')">
          <div class="stat-icon">
            <i class="el-icon-time"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.pending }}</div>
            <div class="stat-label">待处理工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-processing" @click="filterByStatus('1')">
          <div class="stat-icon">
            <i class="el-icon-loading"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.processing }}</div>
            <div class="stat-label">处理中工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-completed" @click="filterByStatus('2')">
          <div class="stat-icon">
            <i class="el-icon-circle-check"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.completed }}</div>
            <div class="stat-label">已完成工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-visited" @click="filterByStatus('3')">
          <div class="stat-icon">
            <i class="el-icon-folder"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.visited }}</div>
            <div class="stat-label">已归档工单</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <el-card class="table-card" shadow="never">
      <ProTable
        ref="proTable"
        :columns="columns"
        :query-form-items="queryFormItems"
        :query-params="queryParams"
        :data="orderList"
        :loading="loading"
        :total="total"
        :show-search.sync="showSearch"
        has-selection
        persist-query
        :table-attrs="{ border: true }"
        @query="handleQuery"
        @reset="resetQuery"
        @pagination="getList"
        @selection-change="handleSelectionChange"
        @persist-restored="getList"
      >
        <template #query-assignUserId>
          <user-select
            v-model="queryParams.assignUserId"
            placeholder="全部办理人"
            clearable
            style="width: 200px"
          />
        </template>
        <template #query-dateRange>
          <el-date-picker
            v-model="dateRange"
            style="width: 240px"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          ></el-date-picker>
        </template>
        <template #toolbar-left>
          <el-checkbox v-model="isAllSelected" @change="handleSelectAll" style="margin-right: 16px">全选</el-checkbox>
          <el-button size="mini" icon="el-icon-setting" :disabled="multiple" @click="handleBatchProcess">批量处理</el-button>
          <el-button size="mini" icon="el-icon-sort" :disabled="multiple" @click="handleBatchTransfer">批量转派</el-button>
          <el-button size="mini" icon="el-icon-download" :disabled="multiple" @click="handleExport">导出</el-button>
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="handleAdd">新增工单</el-button>
        </template>
        <template #column-ticketNo="{ row }">
          <span class="order-link" @click="handleView(row)">{{ row.ticketNo }}</span>
        </template>
        <template #column-title="{ row }">
          <el-tag :type="getConsultTypeTag(row.title)" size="mini">{{ row.title }}</el-tag>
        </template>
        <template #column-priority="{ row }">
          <el-tag :type="getPriorityTag(row.priority)" size="mini">{{ getPriorityLabel(row.priority) }}</el-tag>
        </template>
        <template #column-status="{ row }">
          <el-tag :type="getStatusTag(row.status)" size="mini">{{ getStatusLabel(row.status) }}</el-tag>
        </template>
        <template #column-action="{ row }">
          <el-button type="text" size="mini" icon="el-icon-view" @click="handleView(row)">查看</el-button>
          <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="mini" icon="el-icon-delete" style="color: #C63D4A" @click="handleDelete(row)">删除</el-button>
          <el-dropdown
            v-if="actionsOf(row.status).length"
            trigger="click"
            style="margin-left: 8px"
            @command="handleFlowAction(row, $event)"
          >
            <span class="flow-trigger">流转<i class="el-icon-arrow-down el-icon--right"></i></span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item
                v-for="action in actionsOf(row.status)"
                :key="action.actionCode"
                :command="action"
              >
                {{ flowActionName(action) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </template>
      </ProTable>
    </el-card>

    <el-drawer
      title=""
      :visible.sync="drawerVisible"
      direction="rtl"
      size="60%"
      :with-header="false"
      modal-append-to-body
    >
      <div class="drawer-header">
        <div class="drawer-title">
          <span class="order-no">{{ currentOrder.ticketNo }}</span>
          <el-tag :type="getStatusTag(currentOrder.status)" size="small">{{ getStatusLabel(currentOrder.status) }}</el-tag>
        </div>
        <el-button type="text" icon="el-icon-close" class="close-btn" @click="drawerVisible = false"></el-button>
      </div>

      <el-tabs v-model="activeTab" class="drawer-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <div class="basic-info">
            <div class="info-section">
              <div class="section-title">工单基本信息</div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="工单编号">{{ currentOrder.ticketNo }}</el-descriptions-item>
                <el-descriptions-item label="咨询类型">
                  <el-tag :type="getConsultTypeTag(currentOrder.title)" size="mini">{{ currentOrder.title }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="优先级">
                  <el-tag :type="getPriorityTag(currentOrder.priority)" size="mini">{{ getPriorityLabel(currentOrder.priority) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="来源渠道">{{ currentOrder.source }}</el-descriptions-item>
                <el-descriptions-item label="是否加急">
                  <el-tag v-if="currentOrder.urgent" type="danger" size="mini">是</el-tag>
                  <el-tag v-else type="info" size="mini">否</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ currentOrder.createTime }}</el-descriptions-item>
                <el-descriptions-item label="咨询内容" :span="2">{{ currentOrder.content }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="info-section">
              <div class="section-title">来电人信息</div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="姓名">{{ currentOrder.callerName }}</el-descriptions-item>
                <el-descriptions-item label="电话">{{ currentOrder.callerNumber }}</el-descriptions-item>
                <el-descriptions-item label="归属地">{{ currentOrder.location }}</el-descriptions-item>
                <el-descriptions-item label="职业">{{ currentOrder.occupation }}</el-descriptions-item>
                <el-descriptions-item label="备注" :span="2">{{ currentOrder.remark || '无' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="流转记录" name="timeline">
          <div class="timeline-container">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in timelineList"
                :key="index"
                :timestamp="item.time"
                :color="item.color"
                :icon="item.icon"
              >
                <div class="timeline-content">
                  <div class="timeline-title">{{ item.title }}</div>
                  <div class="timeline-user">{{ item.user }}</div>
                  <div class="timeline-desc">{{ item.description }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="drawer-footer">
        <el-button size="small" icon="el-icon-s-operation" @click="handleTransfer">转派工单</el-button>
        <el-button size="small" icon="el-icon-refresh-left" @click="handleReturn">退回</el-button>
        <el-button size="small" icon="el-icon-folder" @click="handleArchive">归档</el-button>
        <el-button type="primary" size="small" icon="el-icon-phone" @click="handleVisit">回访</el-button>
        <el-button size="small" icon="el-icon-close" @click="drawerVisible = false">关闭</el-button>
      </div>
    </el-drawer>

    <el-dialog :title="isEdit ? '编辑工单' : '新增工单'" :visible.sync="formOpen" width="640px" append-to-body>
      <el-form ref="ticketForm" :model="form" :rules="rules" label-width="100px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单编号" prop="ticketNo">
              <el-input v-model="form.ticketNo" placeholder="系统自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-radio-group v-model="form.priority">
                <el-radio :label="1">紧急</el-radio>
                <el-radio :label="2">普通</el-radio>
                <el-radio :label="3">低</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入工单标题" maxlength="100" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="来电号码" prop="callerNumber">
              <el-input v-model="form.callerNumber" placeholder="请输入来电号码" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来电人" prop="callerName">
              <el-input v-model="form.callerName" placeholder="请输入来电人" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工单内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入工单内容" maxlength="500" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="办理人" prop="assignUserId">
            <user-select v-model="form.assignUserId" placeholder="请选择办理人" @change="onAssignUserChange" />
          </el-form-item></el-col>
        </el-row>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="500" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="formOpen = false">取 消</el-button>
        <el-button type="primary" size="small" :loading="draftSubmitting" @click="submitForm">确 定</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="transferIds.length ? '批量转派工单' : '转派工单'" :visible.sync="transferOpen" width="400px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="转派给">
          <user-select v-model="transferUserId" placeholder="请选择新办理人" @change="onTransferUserChange" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="confirmTransfer">确认</el-button>
        <el-button @click="transferOpen = false">取消</el-button>
      </div>
    </el-dialog>

    <!-- P1-6：外派转办——选择协同机构并提交，orgId 为状态机表单必填 -->
    <el-dialog title="外派转办" :visible.sync="transferOrgVisible" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="协同机构" required>
          <el-select v-model="transferOrgForm.orgId" placeholder="请选择外派协同机构" filterable style="width:100%">
            <el-option v-for="org in externalOrgs" :key="org.orgId"
              :label="org.orgName" :value="org.orgId" />
          </el-select>
        </el-form-item>
        <el-form-item label="转办说明">
          <el-input v-model="transferOrgForm.remark" type="textarea" :rows="3" placeholder="可选，说明转办原因" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button type="primary" @click="confirmExternalTransfer">确认外派</el-button>
        <el-button @click="transferOrgVisible = false">取消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTicket, getTicket, addTicket, updateTicket, delTicket, processTicket, completeTicket, archiveTicket, generateTicketNo, listTicketActions, transferOutTicket } from "@/api/lawyers/callCenter"
import { listExternalOrg } from "@/api/lawyers/externalOrg"
import request from '@/utils/request'
import { createDraftMixin } from '@/utils/draft'

// 查询工单流转记录（若后端接口存在）
export function getTicketTimeline(ticketId) {
  return request({
    url: '/lawyers/call/ticket/' + ticketId + '/timeline',
    method: 'get'
  })
}

export default {
  name: "WorkOrder",
  // U4 表单防丢失：新增态自动存草稿（按用户隔离），ticketNo 系统生成不入草稿
  mixins: [createDraftMixin({
    key: vm => 'workOrder:' + ((vm.$store && vm.$store.state.user.name) || 'anon'),
    formKey: 'form',
    excludes: ['ticketId', 'ticketNo'],
    when: vm => !vm.isEdit
  })],
  data() {
    return {
      loading: false,
      showSearch: true,
      total: 0,
      dateRange: [],
      isAllSelected: false,
      selectedIds: [],
      multiple: true,
      drawerVisible: false,
      activeTab: 'basic',
      currentOrder: {},
      statData: {
        pending: 0,
        processing: 0,
        completed: 0,
        visited: 0
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        title: undefined,
        assignUserId: undefined,
        status: undefined
      },
      orderList: [],
      // V3：ProTable 列定义（ticketNo/title/priority/status/action 走插槽，内容摘要列用 formatter 截断）
      columns: [
        { prop: 'ticketNo', label: '工单编号', width: 160, slot: true },
        { prop: 'callerNumber', label: '来电号码', width: 130 },
        { prop: 'title', label: '咨询类型', width: 110, slot: true },
        { prop: 'content', label: '咨询内容摘要', align: 'left', minWidth: 200, showOverflowTooltip: true, formatter: (row) => (row.content ? row.content.substring(0, 30) : '') },
        { prop: 'priority', label: '优先级', width: 80, slot: true },
        { prop: 'assignUserName', label: '当前处理人', width: 100 },
        { prop: 'status', label: '状态', width: 100, slot: true },
        { prop: 'createTime', label: '创建时间', width: 160 },
        { prop: 'action', label: '操作', width: 230, fixed: 'right', slot: true }
      ],
      // P1-6：按工单状态缓存当前用户可执行动作（同状态动作一致）
      actionMap: {},
      timelineList: [],
      formOpen: false,
      isEdit: false,
      form: {},
      transferOpen: false,
      transferUserId: null,
      transferUserName: '',
      transferRow: null,
      // U5：批量转派的工单 ID 集合（非空表示批量态）
      transferIds: [],
      // P1-6：外派转办（跨域协同机构）
      externalOrgs: [],
      transferOrgVisible: false,
      transferOrgRow: null,
      transferOrgForm: { orgId: null, remark: '' },
      rules: {
        title: [{ required: true, message: '工单标题不能为空', trigger: 'blur' }],
        content: [{ required: true, message: '工单内容不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    // U3：命令面板工单直达——预填工单号查询
    if (this.$route.query.ticketNo) {
      this.queryParams.ticketNo = this.$route.query.ticketNo
    }
    this.getList()
    this.calcStatData()
    this.loadFlowActions()
  },
  computed: {
    /** V3：ProTable 查询字段（assignUserId 为 user-select 走插槽，dateRange 走插槽，字段顺序与原查询区一致） */
    queryFormItems() {
      return [
        { prop: 'ticketNo', label: '工单编号', placeholder: '请输入工单编号', width: '200px' },
        { prop: 'title', label: '咨询类型', placeholder: '请输入咨询类型', width: '160px' },
        { prop: 'assignUserId', label: '办理人', slot: true },
        { prop: 'status', label: '状态', type: 'select', placeholder: '全部', width: '140px', options: [
          { label: '全部', value: '' },
          { label: '待处理', value: '0' },
          { label: '处理中', value: '1' },
          { label: '已完成', value: '2' },
          { label: '已归档', value: '3' }
        ] },
        { prop: 'dateRange', label: '时间范围', slot: true }
      ]
    }
  },
  methods: {
    onAssignUserChange(val, user) {
      this.$set(this.form, 'assignUserName', user ? (user.nickName || user.userName) : '')
    },
    // P1-6：加载各状态下当前用户可执行的流转动作（仅一次，动作只随角色变化）
    loadFlowActions() {
      ['0', '1', '2', '3'].forEach(st => {
        listTicketActions('HOTLINE', st).then(res => {
          this.$set(this.actionMap, st, res.data || [])
        }).catch(() => { this.$set(this.actionMap, st, []) })
      })
    },
    actionsOf(status) {
      return this.actionMap[String(status)] || []
    },
    flowActionName(action) {
      const fallback = { start: '受理', complete: '办结', archive: '归档', transfer: '转派' }
      return action.actionName || fallback[action.actionCode] || action.actionCode
    },
    // 执行状态机动作：transfer 打开转派弹窗，其余确认后调用对应接口（后端二次校验）
    handleFlowAction(row, action) {
      const code = action.actionCode
      if (code === 'transfer') {
        this.openExternalTransfer(row)
        return
      }
      const tips = { start: '确认受理该工单？', complete: '确认办结该工单？', archive: '确认归档该工单？' }
      const runners = {
        start: () => processTicket({ ticketId: row.ticketId, processContent: '工单已受理' }),
        complete: () => completeTicket({ ticketId: row.ticketId }),
        archive: () => archiveTicket({ ticketId: row.ticketId })
      }
      if (!runners[code]) return
      this.$confirm(tips[code], '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        runners[code]().then(() => {
          this.$message.success(this.flowActionName(action) + '成功')
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleRowTransfer(row) {
      this.transferRow = row
      this.transferUserId = null
      this.transferUserName = ''
      this.transferOpen = true
    },
    // P1-6：外派转办——加载协同机构并打开外派弹窗
    openExternalTransfer(row) {
      this.transferOrgRow = row
      this.transferOrgForm = { orgId: null, remark: '' }
      this.externalOrgs = []
      listExternalOrg({ pageNum: 1, pageSize: 100, status: '0' }).then(res => {
        this.externalOrgs = res.rows || []
        this.transferOrgVisible = true
      }).catch(() => {
        this.$message.error('加载协同机构失败')
      })
    },
    // 确认外派：orgId 为 form_schema 必填，后端状态机二次校验
    confirmExternalTransfer() {
      if (!this.transferOrgForm.orgId) {
        this.$message.warning('请选择外派协同机构')
        return
      }
      transferOutTicket({
        ticketId: this.transferOrgRow.ticketId,
        orgId: this.transferOrgForm.orgId,
        remark: this.transferOrgForm.remark
      }).then(() => {
        this.$message.success('外派成功')
        this.transferOrgVisible = false
        this.getList()
        this.calcStatData()
      }).catch(() => {})
    },
    getList() {
      this.loading = true
      listTicket(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.orderList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    calcStatData() {
      const params = { pageNum: 1, pageSize: 1 }
      listTicket({...params, status: '0'}).then(r => this.statData.pending = r.total).catch(() => {})
      listTicket({...params, status: '1'}).then(r => this.statData.processing = r.total).catch(() => {})
      listTicket({...params, status: '2'}).then(r => this.statData.completed = r.total).catch(() => {})
      listTicket({...params, status: '3'}).then(r => this.statData.visited = r.total).catch(() => {})
    },
    getConsultTypeTag(type) {
      return 'info'
    },
    getPriorityLabel(priority) {
      const labels = { 1: '高', 2: '中', 3: '低' }
      return labels[priority] || priority
    },
    getPriorityTag(priority) {
      const tags = { 1: 'danger', 2: 'warning', 3: 'info' }
      return tags[priority] || 'info'
    },
    getStatusLabel(status) {
      const labels = {
        '0': '待处理',
        '1': '处理中',
        '2': '已完成',
        '3': '已归档'
      }
      return labels[String(status)] || status
    },
    getStatusTag(status) {
      const tags = {
        '0': 'warning',
        '1': '',
        '2': 'success',
        '3': 'info'
      }
      return tags[String(status)] || 'info'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里复位插槽字段（dateRange/assignUserId）后重查
      this.dateRange = []
      this.queryParams.assignUserId = undefined
      this.handleQuery()
    },
    filterByStatus(status) {
      this.queryParams.status = status
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.selectedIds = selection.map(item => item.ticketId)
      this.multiple = !selection.length
      this.isAllSelected = selection.length === this.orderList.length && this.orderList.length > 0
    },
    handleSelectAll(val) {
      // V3：el-table 在 ProTable 内部，经 proTable ref 获取后全选/反选
      const table = this.$refs.proTable && this.$refs.proTable.$refs.table
      table && table.toggleAllSelection()
    },
    handleBatchProcess() {
      const ids = this.selectedIds
      if (!ids || ids.length === 0) {
        this.$message.warning('请先选择要处理的工单')
        return
      }
      this.$confirm('是否确认批量完成选中的 ' + ids.length + ' 条工单？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        Promise.all(ids.map(id => completeTicket({ ticketId: id }))).then(() => {
          this.$message.success('批量处理成功')
          this.getList()
          this.calcStatData()
        }).finally(() => { this.loading = false })
      }).catch(() => {})
    },
    handleExport() {
      const params = { ...this.queryParams }
      delete params.pageNum
      delete params.pageSize
      this.download('lawyers/call/ticket/export', { ...params }, `workOrder_${new Date().getTime()}.xlsx`)
    },
    resetForm() {
      this.form = {
        ticketId: undefined,
        ticketNo: undefined,
        title: undefined,
        callerNumber: undefined,
        callerName: undefined,
        priority: 2,
        content: undefined,
        assignUserId: undefined,
        assignUserName: undefined,
        remark: undefined
      }
      this.$nextTick(() => {
        this.$refs.ticketForm && this.$refs.ticketForm.clearValidate()
      })
    },
    handleAdd() {
      this.resetForm()
      this.isEdit = false
      generateTicketNo().then(response => {
        this.form.ticketNo = response.data
        this.formOpen = true
        // U4：弹窗打开后询问恢复未提交草稿
        this.draftRestorePrompt()
      }).catch(() => {})
    },
    handleView(row) {
      getTicket(row.ticketId).then(response => {
        this.currentOrder = response.data
        this.activeTab = 'basic'
        this.drawerVisible = true
        this.loadTimeline(row.ticketId)
      }).catch(() => {})
    },
    loadTimeline(ticketId) {
      this.timelineList = []
      getTicketTimeline(ticketId).then(res => {
        const data = res.data || res.rows || []
        if (Array.isArray(data) && data.length) {
          this.timelineList = data.map(item => ({
            time: item.operateTime || item.createTime || item.time,
            title: item.title || item.action || item.nodeName || '流转',
            user: item.operator || item.operateUserName || item.userName || item.user || '系统',
            description: item.content || item.remark || item.description || '',
            color: item.color || '#255A99',
            icon: item.icon || undefined
          }))
        } else {
          this.buildFallbackTimeline()
        }
      }).catch(() => {
        // 后端无该接口时，从工单数据自身组装时间线
        this.buildFallbackTimeline()
      })
    },
    buildFallbackTimeline() {
      const order = this.currentOrder || {}
      const list = []
      if (order.createTime) {
        list.push({
          time: order.createTime,
          title: '工单创建',
          user: order.createUserName || order.createBy || '系统',
          description: '工单已创建，等待分派处理',
          color: '#255A99',
          icon: 'el-icon-document-add'
        })
      }
      if (order.processTime || order.assignTime) {
        list.push({
          time: order.processTime || order.assignTime,
          title: '开始处理',
          user: order.assignUserName || order.processUserName || '处理人',
          description: order.processContent || '工单已分派给处理人',
          color: '#E6A23C',
          icon: 'el-icon-loading'
        })
      }
      if (order.completeTime) {
        list.push({
          time: order.completeTime,
          title: '处理完成',
          user: order.completeUserName || order.assignUserName || '处理人',
          description: order.completeContent || '工单处理已完成',
          color: '#2B8C6E',
          icon: 'el-icon-circle-check'
        })
      }
      if (order.archiveTime) {
        list.push({
          time: order.archiveTime,
          title: '工单归档',
          user: order.archiveUserName || '系统',
          description: order.archiveRemark || '工单已归档',
          color: '#7C3AED',
          icon: 'el-icon-folder'
        })
      }
      if (!list.length) {
        list.push({
          time: order.createTime || '',
          title: '工单信息',
          user: order.assignUserName || '-',
          description: order.content || '暂无流转记录',
          color: '#909399',
          icon: 'el-icon-info'
        })
      }
      this.timelineList = list
    },
    handleEdit(row) {
      this.resetForm()
      this.isEdit = true
      getTicket(row.ticketId).then(response => {
        this.form = Object.assign({}, this.form, response.data)
        if (this.form.priority === undefined || this.form.priority === null) {
          this.form.priority = 2
        }
        this.formOpen = true
      }).catch(() => {})
    },
    submitForm() {
      // U4：提交中防重复提交
      if (this.draftSubmitting) return
      this.$refs.ticketForm.validate(valid => {
        if (!valid) return
        this.lockSubmit()
        const save = this.isEdit ? updateTicket(this.form) : addTicket(this.form)
        save.then(() => {
          this.$message.success(this.isEdit ? '修改成功' : '新增成功')
          this.formOpen = false
          if (!this.isEdit) this.draftClear()
          this.getList()
          this.calcStatData()
        }).catch(() => {}).finally(() => this.unlockSubmit())
      })
    },
    handleDelete(row) {
      this.$confirm('是否确认删除工单编号为"' + row.ticketNo + '"的数据项？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        delTicket(row.ticketId).then(() => {
          this.$message.success('删除成功')
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleTransfer() {
      this.transferRow = this.currentOrder
      this.transferIds = []
      this.transferUserId = null
      this.transferUserName = ''
      this.transferOpen = true
    },
    /** U5：批量转派（勾选工单后复用转派弹窗） */
    handleBatchTransfer() {
      const ids = this.selectedIds
      if (!ids || ids.length === 0) {
        this.$message.warning('请先选择要转派的工单')
        return
      }
      this.transferIds = ids
      this.transferRow = null
      this.transferUserId = null
      this.transferUserName = ''
      this.transferOpen = true
    },
    onTransferUserChange(val, user) {
      this.transferUserName = user ? (user.nickName || user.userName) : ''
    },
    confirmTransfer() {
      if (!this.transferUserId) {
        this.$message.warning('请选择新办理人')
        return
      }
      const name = this.transferUserName
      // U5：批量态循环复用单条转派接口（与批量处理同模式），单条态走原逻辑
      if (this.transferIds.length) {
        const ids = this.transferIds
        this.loading = true
        Promise.all(ids.map(id => processTicket({
          ticketId: id,
          processContent: '工单批量转派给：' + (name || this.transferUserId),
          assignUserId: this.transferUserId,
          assignUserName: name
        }))).then(() => {
          this.$message.success('已成功转派 ' + ids.length + ' 条工单')
          this.transferOpen = false
          this.getList()
          this.calcStatData()
        }).finally(() => { this.loading = false })
        return
      }
      processTicket({
        ticketId: this.transferRow.ticketId,
        processContent: '工单转派给：' + (name || this.transferUserId),
        assignUserId: this.transferUserId,
        assignUserName: name
      }).then(() => {
        this.$message.success('工单已转派')
        this.transferOpen = false
        this.drawerVisible = false
        this.getList()
        this.calcStatData()
      }).catch(() => {})
    },
    handleReturn() {
      this.$confirm('是否确认将该工单退回待处理？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        updateTicket({
          ticketId: this.currentOrder.ticketId,
          status: '0',
          processContent: '工单退回待处理'
        }).then(() => {
          this.$message.success('工单已退回')
          this.drawerVisible = false
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleArchive() {
      archiveTicket({ ticketId: this.currentOrder.ticketId }).then(() => {
        this.$message.success('工单已归档')
        this.drawerVisible = false
        this.getList()
        this.calcStatData()
      }).catch(() => {})
    },
    handleVisit() {
      this.$router.push({
        path: '/business/callback',
        query: {
          phone: this.currentOrder.callerNumber,
          ticketNo: this.currentOrder.ticketNo
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.work-order-page {
  min-height: calc(100vh - 84px);
  background: #F5F7FA;
  padding: 24px;

  .stat-cards {
    margin-bottom: 20px;

    .stat-card {
      background: #fff;
      border-radius: 8px;
      padding: 24px;
      display: flex;
      align-items: center;
      cursor: pointer;
      transition: all 0.3s;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      .stat-icon {
        width: 56px;
        height: 56px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 28px;
        color: #fff;
        margin-right: 18px;
      }

      .stat-info {
        flex: 1;

        .stat-num {
          font-size: 28px;
          font-weight: 700;
          line-height: 1.2;
          color: #1F2A3A;
        }

        .stat-label {
          font-size: 14px;
          color: #5A6A7E;
          margin-top: 6px;
        }
      }

      &.stat-pending {
        .stat-icon {
          background: linear-gradient(135deg, #E8923A 0%, #d97706 100%);
        }
      }

      &.stat-processing {
        .stat-icon {
          background: linear-gradient(135deg, #255A99 0%, #1A3C6E 100%);
        }
      }

      &.stat-completed {
        .stat-icon {
          background: linear-gradient(135deg, #2B8C6E 0%, #2B8C6E 100%);
        }
      }

      &.stat-visited {
        .stat-icon {
          background: linear-gradient(135deg, #7C3AED 0%, #7c3aed 100%);
        }
      }
    }
  }

  .table-card {
    background: #fff;
    border-radius: 8px;
    border: none;

    ::v-deep .el-card__body {
      padding: 20px 24px 24px;
    }

    .order-link {
      color: #255A99;
      cursor: pointer;

      &:hover {
        color: #1A3C6E;
        text-decoration: underline;
      }
    }

    .flow-trigger {
      color: #255A99;
      font-size: 13px;
      cursor: pointer;
      outline: none;

      &:hover {
        color: #1A3C6E;
      }
    }
  }

  ::v-deep .el-drawer {
    background: #F5F7FA;

    .el-drawer__body {
      padding: 0;
      display: flex;
      flex-direction: column;
      height: 100%;
    }
  }

  .drawer-header {
    background: #fff;
    padding: 20px 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #DCE2EB;

    .drawer-title {
      display: flex;
      align-items: center;
      gap: 14px;

      .order-no {
        font-size: 18px;
        font-weight: 700;
        color: #1F2A3A;
      }
    }

    .close-btn {
      font-size: 20px;
      color: #5A6A7E;
      padding: 8px;

      &:hover {
        color: #1F2A3A;
      }
    }
  }

  .drawer-tabs {
    flex: 1;
    background: #fff;
    margin-top: 16px;

    ::v-deep .el-tabs__header {
      margin: 0;
      padding: 0 24px;
      border-bottom: 1px solid #DCE2EB;
    }

    ::v-deep .el-tabs__item {
      height: 48px;
      line-height: 48px;
      font-size: 14px;
    }

    ::v-deep .el-tabs__content {
      padding: 24px;
      overflow-y: auto;
      height: calc(100% - 49px);
    }
  }

  .basic-info {
    .info-section {
      margin-bottom: 24px;

      &:last-child {
        margin-bottom: 0;
      }

      .section-title {
        font-size: 15px;
        font-weight: 600;
        color: #1F2A3A;
        margin-bottom: 16px;
        padding-left: 10px;
        border-left: 3px solid #255A99;
      }
    }
  }

  .timeline-container {
    padding: 12px 0;

    ::v-deep .el-timeline-item__wrapper {
      padding-left: 24px;
    }

    ::v-deep .el-timeline-item__timestamp {
      color: #5A6A7E;
      font-size: 12px;
    }

    .timeline-content {
      background: #F5F7FA;
      border-radius: 8px;
      padding: 16px 20px;

      .timeline-title {
        font-size: 14px;
        font-weight: 600;
        color: #1F2A3A;
        margin-bottom: 6px;
      }

      .timeline-user {
        font-size: 12px;
        color: #255A99;
        margin-bottom: 8px;
      }

      .timeline-desc {
        font-size: 13px;
        color: #5A6A7E;
        line-height: 1.6;
      }
    }
  }

  .drawer-footer {
    background: #fff;
    padding: 16px 24px;
    border-top: 1px solid #DCE2EB;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}
</style>
