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

    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="坐席名称" prop="agentName">
        <el-input
          v-model="queryParams.agentName"
          placeholder="请输入坐席名称"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="坐席状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable style="width: 140px">
          <el-option label="离线" value="0" />
          <el-option label="在线" value="1" />
          <el-option label="忙碌" value="2" />
          <el-option label="休息" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="agentList">
      <el-table-column label="坐席工号" align="center" prop="agentId" width="90" />
      <el-table-column label="坐席名称" align="center" prop="agentName" min-width="100" />
      <el-table-column label="绑定用户" align="center" min-width="140">
        <template slot-scope="scope">
          <span v-if="scope.row.userId">
            {{ scope.row.nickName || '-' }}
            <span style="color:#909399">（{{ scope.row.userName }}）</span>
          </span>
          <span v-else style="color:#c0c4cc">未绑定</span>
        </template>
      </el-table-column>
      <el-table-column label="所属部门" align="center" prop="deptName" min-width="120" />
      <el-table-column label="手机号" align="center" prop="phonenumber" width="120" />
      <el-table-column label="SIP分机" align="center" prop="sipExtension" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" type="info" v-if="scope.row.sipExtension">{{ scope.row.sipExtension }}</el-tag>
          <span v-else style="color:#c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="坐席状态" align="center" prop="status" width="90">
        <template slot-scope="scope">
          <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通话状态" align="center" prop="callStatus" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="getCallStatusType(scope.row.callStatus)">{{ getCallStatusLabel(scope.row.callStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="应答模式" align="center" width="90">
        <template slot-scope="scope">
          <span>{{ scope.row.callMode === '1' ? '手动' : '自动' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="登录时间" align="center" prop="loginTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.loginTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="160" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:call:agent:query']">详情</el-button>
          <el-button v-if="scope.row.status == '1' || scope.row.status == '2'" size="mini" type="text"
            icon="el-icon-switch-button" @click="handleLogout(scope.row)"
            v-hasPermi="['lawyers:call:agent:logout']">强制下线</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize" @pagination="getList" />

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
      this.resetForm("queryForm")
      this.handleQuery()
    },
    handleDetail(row) {
      getAgent(row.agentId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    handleLogout(row) {
      this.$modal.confirm('是否确认强制下线坐席 "' + row.agentName + '"？').then(function() {
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
