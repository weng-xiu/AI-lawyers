<template>
  <div class="app-container cc-page">
    <el-alert
      title="坐席即系统用户的呼叫中心身份"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px;">
      <template slot="default">
        坐席人员的工号、SIP分机、应答模式等配置已统一在
        <el-link type="primary" @click="goUserManage">【系统管理 → 用户管理】</el-link>
        中维护；本页面仅用于实时监控坐席在线/通话状态及执行强制下线等运维操作。
      </template>
    </el-alert>

    <!-- V3：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="agentList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
    >
      <template #column-bindUser="{ row }">
        <span v-if="row.userId">
          {{ row.nickName || '-' }}
          <span style="color:#909399">（{{ row.userName }}）</span>
        </span>
        <span v-else style="color:#c0c4cc">未绑定</span>
      </template>
      <template #column-sipExtension="{ row }">
        <el-tag size="mini" type="info" v-if="row.sipExtension">{{ row.sipExtension }}</el-tag>
        <span v-else style="color:#c0c4cc">--</span>
      </template>
      <template #column-status="{ row }">
        <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
      </template>
      <template #column-callStatus="{ row }">
        <el-tag size="mini" :type="getCallStatusType(row.callStatus)">{{ getCallStatusLabel(row.callStatus) }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)"
          v-hasPermi="['lawyers:call:agent:query']">详情</el-button>
        <el-button v-if="row.status == '1' || row.status == '2'" size="mini" type="text"
          icon="el-icon-switch-button" @click="handleLogout(row)"
          v-hasPermi="['lawyers:call:agent:logout']">强制下线</el-button>
      </template>
    </ProTable>

    <el-dialog title="坐席详情" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="坐席工号">{{ detailForm.agentId }}</el-descriptions-item>
        <el-descriptions-item label="坐席名称">{{ detailForm.agentName }}</el-descriptions-item>
        <el-descriptions-item label="绑定用户">
          {{ detailForm.nickName || '-' }}<span v-if="detailForm.userName">（{{ detailForm.userName }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ detailForm.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailForm.phonenumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="SIP分机号">{{ detailForm.sipExtension || '-' }}</el-descriptions-item>
        <el-descriptions-item label="应答模式">{{ detailForm.callMode === '1' ? '手动应答' : '自动应答' }}</el-descriptions-item>
        <el-descriptions-item label="坐席状态">
          <el-tag :type="getStatusType(detailForm.status)">{{ getStatusLabel(detailForm.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="通话状态">
          <el-tag size="mini" :type="getCallStatusType(detailForm.callStatus)">{{ getCallStatusLabel(detailForm.callStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="登录时间">{{ parseTime(detailForm.loginTime) }}</el-descriptions-item>
        <el-descriptions-item label="注销时间">{{ parseTime(detailForm.logoutTime) }}</el-descriptions-item>
        <el-descriptions-item label="最后登录IP">{{ detailForm.lastLoginIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listAgent, getAgent, agentLogout } from "@/api/lawyers/callCenter"

export default {
  name: "CallAgent",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      agentList: [],
      // V3：ProTable 列定义（bindUser/sipExtension/status/callStatus/action 走插槽，时间/应答模式列用 formatter）
      columns: [
        { prop: 'agentId', label: '坐席工号', width: 90 },
        { prop: 'agentName', label: '坐席名称', minWidth: 100 },
        { prop: 'bindUser', label: '绑定用户', minWidth: 140, slot: true },
        { prop: 'deptName', label: '所属部门', minWidth: 120 },
        { prop: 'phonenumber', label: '手机号', width: 120 },
        { prop: 'sipExtension', label: 'SIP分机', width: 90, slot: true },
        { prop: 'status', label: '坐席状态', width: 90, slot: true },
        { prop: 'callStatus', label: '通话状态', width: 90, slot: true },
        { prop: 'callMode', label: '应答模式', width: 90, formatter: (row) => (row.callMode === '1' ? '手动' : '自动') },
        { prop: 'loginTime', label: '登录时间', width: 160, formatter: (row) => this.parseTime(row.loginTime) },
        { prop: 'action', label: '操作', width: 160, slot: true, className: 'small-padding fixed-width' }
      ],
      detailOpen: false,
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentName: undefined,
        status: undefined
      },
      detailForm: {}
    }
  },
  created() {
    this.getList()
  },
  computed: {
    /** V3：ProTable 查询字段（静态配置，保持 computed 形式与全站一致） */
    queryFormItems() {
      return [
        { prop: 'agentName', label: '坐席名称', placeholder: '请输入坐席名称', width: '200px' },
        { prop: 'status', label: '坐席状态', type: 'select', placeholder: '请选择状态', width: '140px', options: [
          { label: '离线', value: '0' }, { label: '在线', value: '1' },
          { label: '忙碌', value: '2' }, { label: '休息', value: '3' }
        ] }
      ]
    }
  },
  methods: {
    getList() {
      this.loading = true
      listAgent(this.queryParams).then(response => {
        this.agentList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    goUserManage() {
      this.$router.push("/system/user")
    },
    getStatusType(status) {
      const types = { '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }
      return types[status] || 'info'
    },
    getStatusLabel(status) {
      const labels = { '0': '离线', '1': '在线', '2': '忙碌', '3': '休息' }
      return labels[status] || '未知'
    },
    getCallStatusType(callStatus) {
      const types = { '0': 'info', '1': 'success', '2': 'warning', '3': 'warning', '4': 'success', '5': 'info' }
      return types[callStatus] || 'info'
    },
    getCallStatusLabel(callStatus) {
      const labels = { '0': '空闲', '1': '通话中', '2': '保持', '3': '咨询中', '4': '三方', '5': '话后整理' }
      return labels[callStatus] || '空闲'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      // V3：ProTable 内部已对表单字段 resetFields，这里复位插槽字段后重查
      this.handleQuery()
    },
    handleDetail(row) {
      getAgent(row.agentId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    handleLogout(row) {
      this.$modal.dangerConfirm({
        actionType: 'caution',
        title: '强制下线确认',
        confirmText: '确认强制下线',
        content: '将强制坐席 "' + row.agentName + '" 退出签入状态',
        count: 1,
        countNoun: '名坐席',
        tip: '若该坐席正在通话，强制下线可能影响其话务台操作，请谨慎'
      }).then(function() {
        return agentLogout({ agentId: row.agentId })
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("操作成功")
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
@import '~@/assets/styles/call-center-light.scss';
</style>
