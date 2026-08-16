<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="坐席名称" prop="agentName">
        <el-input v-model="queryParams.agentName" placeholder="请输入坐席名称" clearable style="width: 180px"
          @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="开始时间" prop="beginTime">
        <el-date-picker v-model="queryParams.beginTime" type="datetime" placeholder="请选择开始时间" value-format="yyyy-MM-dd HH:mm:ss"
          style="width: 180px" />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker v-model="queryParams.endTime" type="datetime" placeholder="请选择结束时间" value-format="yyyy-MM-dd HH:mm:ss"
          style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:performance:agent:export']">导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="list" border>
      <el-table-column label="坐席名称" prop="agentName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="状态" prop="status" width="80" align="center">
        <template slot-scope="scope">
          <el-tag :type="statusTag(scope.row.status)" size="small">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="通话状态" prop="callStatus" width="100" align="center">
        <template slot-scope="scope">{{ callStatusLabel(scope.row.callStatus) }}</template>
      </el-table-column>
      <el-table-column label="签入时间" prop="loginTime" width="160" align="center" />
      <el-table-column label="签入时长(分)" prop="signedInMinutes" width="110" align="center" />
      <el-table-column label="呼入接听" prop="inboundCalls" width="90" align="center" />
      <el-table-column label="呼出" prop="outboundCalls" width="70" align="center" />
      <el-table-column label="转接" prop="transferredOut" width="70" align="center" />
      <el-table-column label="通话时长(秒)" prop="talkDuration" width="110" align="center" />
      <el-table-column label="均通话(秒)" prop="avgTalkDuration" width="100" align="center" />
      <el-table-column label="AI协访" prop="aiAssistCount" width="80" align="center" />
      <el-table-column label="满意度均值" prop="avgSatisfaction" width="100" align="center">
        <template slot-scope="scope">
          <span v-if="scope.row.avgSatisfaction > 0">{{ scope.row.avgSatisfaction }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize"
      @pagination="getList" />
  </div>
</template>

<script>
import { listAgentPerformance } from "@/api/lawyers/stat";

export default {
  name: "AgentPerformance",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      list: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentName: undefined,
        beginTime: undefined,
        endTime: undefined
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      listAgentPerformance(this.queryParams).then(response => {
        this.list = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    statusLabel(s) {
      return { '0': "离线", '1': "在线", '2': "忙碌", '3': "休息" }[s] || s;
    },
    statusTag(s) {
      return { '0': "info", '1': "success", '2': "warning", '3': "danger" }[s] || "";
    },
    callStatusLabel(s) {
      return { '0': "空闲", '1': "通话中", '2': "保持", '3': "咨询中", '4': "三方", '5': "话后整理" }[s] || s;
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.$refs["queryForm"].resetFields();
      this.handleQuery();
    },
    handleExport() {
      this.download('lawyers/performance/agent/export', {
        ...this.queryParams,
        pageNum: undefined,
        pageSize: undefined
      }, `坐席效能报表_${new Date().getTime()}.xlsx`);
    }
  }
};
</script>
