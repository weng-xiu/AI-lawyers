<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="list"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="80px"
      empty-text="暂无坐席效能数据"
      empty-hint="选择坐席与时间区间后查询，未产生通话的坐席指标为 0"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #toolbar-left>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:performance:agent:export']">导出</el-button>
      </template>
      <!-- 开始/结束时间为独立 datetime 选择器，走自定义查询插槽 -->
      <template #query-beginTime>
        <el-date-picker v-model="queryParams.beginTime" type="datetime" placeholder="请选择开始时间"
          value-format="yyyy-MM-dd HH:mm:ss" style="width: 180px" />
      </template>
      <template #query-endTime>
        <el-date-picker v-model="queryParams.endTime" type="datetime" placeholder="请选择结束时间"
          value-format="yyyy-MM-dd HH:mm:ss" style="width: 180px" />
      </template>
      <template #column-status="{ row }">
        <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
      </template>
      <template #column-callStatus="{ row }">{{ callStatusLabel(row.callStatus) }}</template>
      <template #column-avgSatisfaction="{ row }">
        <span v-if="row.avgSatisfaction > 0">{{ row.avgSatisfaction }}</span>
        <span v-else>-</span>
      </template>
    </ProTable>
  </div>
</template>

<script>
import { listAgentPerformance } from "@/api/lawyers/stat";
import { listAgent } from "@/api/lawyers/callCenter";

export default {
  name: "AgentPerformance",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      list: [],
      // V3：ProTable 列定义（状态/通话状态/满意度走插槽）
      columns: [
        { prop: 'agentName', label: '坐席名称', minWidth: 120, align: 'center', showOverflowTooltip: true },
        { prop: 'status', label: '状态', width: 80, align: 'center', slot: true },
        { prop: 'callStatus', label: '通话状态', width: 100, align: 'center', slot: true },
        { prop: 'loginTime', label: '签入时间', width: 160, align: 'center' },
        { prop: 'signedInMinutes', label: '签入时长(分)', width: 110, align: 'center' },
        { prop: 'inboundCalls', label: '呼入接听', width: 90, align: 'center' },
        { prop: 'outboundCalls', label: '呼出', width: 70, align: 'center' },
        { prop: 'transferredOut', label: '转接', width: 70, align: 'center' },
        { prop: 'talkDuration', label: '通话时长(秒)', width: 110, align: 'center' },
        { prop: 'avgTalkDuration', label: '均通话(秒)', width: 100, align: 'center' },
        { prop: 'aiAssistCount', label: 'AI协访', width: 80, align: 'center' },
        { prop: 'avgSatisfaction', label: '满意度均值', width: 100, align: 'center', slot: true }
      ],
      agentOptions: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentId: undefined,
        beginTime: undefined,
        endTime: undefined
      }
    };
  },
  computed: {
    queryFormItems() {
      const agentSelectOptions = (this.agentOptions || []).map(a => ({
        label: a.agentName + '(' + a.agentId + ')',
        value: a.agentId
      }))
      return [
        { prop: 'agentId', label: '坐席名称', type: 'select', placeholder: '全部坐席', width: 180, filterable: true, options: agentSelectOptions },
        // 独立 datetime 选择器无法由 ProTable 内建控件表达，走查询插槽
        { prop: 'beginTime', label: '开始时间', slot: true },
        { prop: 'endTime', label: '结束时间', slot: true }
      ]
    }
  },
  created() {
    this.loadAgents();
    this.getList();
  },
  methods: {
    loadAgents() {
      listAgent({ pageSize: 999, status: '1' }).then(response => {
        this.agentOptions = response.rows || [];
      });
    },
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值（含插槽 datetime）并重查
      this.queryParams = { pageNum: 1, pageSize: 10, agentId: undefined, beginTime: undefined, endTime: undefined }
      this.getList()
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
