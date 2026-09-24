<template>
  <div class="dashboard-container">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="title">运营大屏</span>
      <el-date-picker v-model="rangeTime" type="datetimerange" size="small"
        value-format="yyyy-MM-dd HH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间"
        style="width: 380px" />
      <el-button type="primary" size="small" icon="el-icon-refresh" @click="loadData">刷新</el-button>
      <span class="auto-tip">每 30 秒自动刷新</span>
    </div>

    <!-- 指标卡 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="3" v-for="c in statCards" :key="c.label">
        <div class="stat-card" :style="{ background: c.color }">
          <div class="stat-num">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>呼叫趋势</span></div>
          <div ref="trendChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>咨询分类占比</span></div>
          <div ref="categoryChart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>AI 独立解决</span></div>
          <div ref="aiChart" style="height: 260px"></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>坐席负载</span></div>
          <el-table :data="agentLoad" size="small" border max-height="260">
            <el-table-column label="坐席" prop="agentName" min-width="120" :show-overflow-tooltip="true" />
            <el-table-column label="状态" width="80" align="center">
              <template slot-scope="s"><el-tag :type="statusTag(s.row.status)" size="mini">{{ statusLabel(s.row.status) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="通话状态" width="100" align="center">
              <template slot-scope="s">{{ callStatusLabel(s.row.callStatus) }}</template>
            </el-table-column>
            <el-table-column label="今日通话" prop="todayCalls" width="90" align="center" />
            <el-table-column label="通话时长(秒)" prop="talkDuration" width="110" align="center" />
            <el-table-column label="均长(秒)" prop="avgDuration" width="90" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>当前排队</span></div>
          <el-table :data="queueNow" size="small" border max-height="260">
            <el-table-column label="主叫号码" prop="callerNumber" min-width="120" />
            <el-table-column label="技能组" prop="groupName" min-width="100" />
            <el-table-column label="优先级" prop="priority" width="80" align="center" />
            <el-table-column label="等待时长(秒)" prop="waitDuration" width="110" align="center" />
            <el-table-column label="入队时间" prop="enqueueTime" min-width="150" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>外呼任务进度</span></div>
          <el-table :data="outboundProgress" size="small" border max-height="260">
            <el-table-column label="任务" prop="taskName" min-width="140" :show-overflow-tooltip="true" />
            <el-table-column label="进度" min-width="140">
              <template slot-scope="s">
                <el-progress :percentage="progressPct(s.row)" :stroke-width="10" :text-inside="true" />
              </template>
            </el-table-column>
            <el-table-column label="接通" prop="answeredCount" width="70" align="center" />
            <el-table-column label="失败" prop="failedCount" width="70" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- F10 公共法律服务业务指标 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>语种分布</span></div>
          <div ref="langChart" style="height: 260px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>业务条线转办</span></div>
          <el-table :data="bizMetrics.transferLines || []" size="small" border max-height="260">
            <el-table-column label="条线" prop="lineName" min-width="90" :show-overflow-tooltip="true" />
            <el-table-column label="转办量" prop="transferCount" width="80" align="center" />
            <el-table-column label="办结率" width="90" align="center">
              <template slot-scope="s">{{ num(s.row.closeRate) }}%</template>
            </el-table-column>
            <el-table-column label="平均办结(分)" prop="avgCloseMinutes" width="100" align="center" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>公众端渠道活跃/绑定</span></div>
          <div ref="channelChart" style="height: 260px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>关怀模式使用率</span></div>
          <div class="care-box">
            <div class="care-item">
              <div class="care-num">{{ num(bizCare.careCallRate) }}%</div>
              <div class="care-label">关怀号码通话占比（{{ num(bizCare.careCalls) }}/{{ num(bizCare.totalCalls) }}）</div>
            </div>
            <div class="care-item">
              <div class="care-num">{{ num(bizCare.careCallerRate) }}%</div>
              <div class="care-label">关怀号码占独立来电比（{{ num(bizCare.careCallers) }}/{{ num(bizCare.totalCallers) }}）</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>公众端满意度（图文评价）</span></div>
          <div class="care-box">
            <div class="care-item">
              <div class="care-num">{{ num(bizSatis.avgOverall) }}</div>
              <div class="care-label">总体均分（共 {{ num(bizSatis.evalCount) }} 条评价）</div>
            </div>
            <div class="care-item">
              <div class="care-sub">专业 {{ num(bizSatis.avgProfessionalism) }} / 响应 {{ num(bizSatis.avgResponsiveness) }} / 质量 {{ num(bizSatis.avgQuality) }}</div>
              <div class="care-label">四维均分</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts';
import { dashboardData } from "@/api/lawyers/stat";

export default {
  name: "LawyerDashboard",
  data() {
    return {
      rangeTime: [],
      callSummary: {},
      agentStatusSummary: {},
      satisfactionSummary: {},
      aiRatio: {},
      callTrend: [],
      categoryPie: [],
      agentLoad: [],
      queueNow: [],
      outboundProgress: [],
      bizMetrics: {},
      timer: null,
      trendChart: null,
      categoryChart: null,
      aiChart: null,
      langChart: null,
      channelChart: null
    };
  },
  computed: {
    statCards() {
      const aiSolved = Math.max(0, (Number(this.aiRatio.totalSessions) || 0) - (Number(this.aiRatio.handoffSessions) || 0));
      return [
        { label: "今日呼入", value: this.num(this.callSummary.totalCalls), color: "#1A3C6E" },
        { label: "已接通", value: this.num(this.callSummary.answeredCalls), color: "#2B8C6E" },
        { label: "未接", value: this.num(this.callSummary.missedCalls), color: "#C63D4A" },
        { label: "转接", value: this.num(this.callSummary.transferredCalls), color: "#E8923A" },
        { label: "均通话时长(秒)", value: this.num(this.callSummary.avgDuration), color: "#3B73B3" },
        { label: "坐席在线/总数", value: `${this.num(this.agentStatusSummary.onlineAgents)}/${this.num(this.agentStatusSummary.totalAgents)}`, color: "#8C8C8C" },
        { label: "平均满意度", value: this.num(this.satisfactionSummary.avgSatisfaction), color: "#9B6EAA" },
        { label: "AI独立解决", value: aiSolved, color: "#54A68B" }
      ];
    },
    bizCare() {
      return this.bizMetrics.careUsage || {};
    },
    bizSatis() {
      return this.bizMetrics.portalSatisfaction || {};
    }
  },
  created() {
    this.loadData();
    this.timer = setInterval(this.loadData, 30000);
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer);
    [this.trendChart, this.categoryChart, this.aiChart, this.langChart, this.channelChart].forEach(c => {
      if (c && c.dispose) c.dispose();
    });
  },
  methods: {
    loadData() {
      const params = {};
      if (this.rangeTime && this.rangeTime.length === 2) {
        params.beginTime = this.rangeTime[0];
        params.endTime = this.rangeTime[1];
      }
      dashboardData(params).then(res => {
        const d = res.data || {};
        this.callSummary = d.callSummary || {};
        this.agentStatusSummary = d.agentStatusSummary || {};
        this.satisfactionSummary = d.satisfactionSummary || {};
        this.aiRatio = d.aiRatio || {};
        this.callTrend = d.callTrend || [];
        this.categoryPie = d.categoryPie || [];
        this.agentLoad = d.agentLoad || [];
        this.queueNow = d.queueNow || [];
        this.outboundProgress = d.outboundProgress || [];
        this.bizMetrics = d.bizMetrics || {};
        this.$nextTick(() => {
          this.renderTrend();
          this.renderCategory();
          this.renderAi();
          this.renderLang();
          this.renderChannel();
        });
      });
    },
    renderTrend() {
      if (!this.trendChart) this.trendChart = echarts.init(this.$refs.trendChart);
      const x = this.callTrend.map(i => i.timeLabel);
      const total = this.callTrend.map(i => this.num(i.totalCalls));
      const answered = this.callTrend.map(i => this.num(i.answeredCalls));
      const missed = this.callTrend.map(i => this.num(i.missedCalls));
      const transferred = this.callTrend.map(i => this.num(i.transferredCalls));
      this.trendChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['呼入', '接通', '未接', '转接'] },
        grid: { left: 40, right: 20, top: 40, bottom: 30 },
        xAxis: { type: 'category', data: x, axisLabel: { rotate: 40, fontSize: 10 } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '呼入', type: 'line', smooth: true, data: total, itemStyle: { color: '#1A3C6E' } },
          { name: '接通', type: 'bar', barMaxWidth: 12, data: answered, itemStyle: { color: '#2B8C6E' } },
          { name: '未接', type: 'bar', barMaxWidth: 12, data: missed, itemStyle: { color: '#C63D4A' } },
          { name: '转接', type: 'bar', barMaxWidth: 12, data: transferred, itemStyle: { color: '#E8923A' } }
        ]
      });
    },
    renderCategory() {
      if (!this.categoryChart) this.categoryChart = echarts.init(this.$refs.categoryChart);
      this.categoryChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { orient: 'vertical', left: 'left' },
        series: [{
          type: 'pie',
          radius: ['40%', '68%'],
          center: ['60%', '50%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}' },
          data: this.categoryPie.map(i => ({ name: i.name, value: this.num(i.value) }))
        }]
      });
    },
    renderAi() {
      if (!this.aiChart) this.aiChart = echarts.init(this.$refs.aiChart);
      const total = this.num(this.aiRatio.totalSessions);
      const handoff = this.num(this.aiRatio.handoffSessions);
      const solved = Math.max(0, total - handoff);
      this.aiChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0 },
        series: [{
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '45%'],
          label: { formatter: '{b}\n{c}' },
          data: [
            { name: 'AI独立解决', value: solved, itemStyle: { color: '#2B8C6E' } },
            { name: '转人工', value: handoff, itemStyle: { color: '#C9A96E' } }
          ]
        }]
      });
    },
    renderLang() {
      if (!this.langChart) this.langChart = echarts.init(this.$refs.langChart);
      const colors = { 'zh-CN': '#1A3C6E', 'yue-CN': '#C9A96E' };
      this.langChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0 },
        series: [{
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '45%'],
          label: { formatter: '{b}\n{c}' },
          data: (this.bizMetrics.languageDist || []).map(i => ({
            name: i.langName,
            value: this.num(i.value),
            itemStyle: { color: colors[i.lang] || '#3B73B3' }
          }))
        }]
      });
    },
    renderChannel() {
      if (!this.channelChart) this.channelChart = echarts.init(this.$refs.channelChart);
      const sessions = this.bizMetrics.channelSessions || [];
      const binds = this.bizMetrics.channelBinds || [];
      const nameSet = [];
      sessions.concat(binds).forEach(i => {
        if (nameSet.indexOf(i.channelName) < 0) nameSet.push(i.channelName);
      });
      const pick = (list, name) => {
        const hit = list.find(i => i.channelName === name);
        return hit ? this.num(hit.value) : 0;
      };
      this.channelChart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0 },
        grid: { left: 50, right: 20, top: 30, bottom: 50 },
        xAxis: { type: 'category', data: nameSet, axisLabel: { fontSize: 10, interval: 0, rotate: 20 } },
        yAxis: { type: 'value', minInterval: 1 },
        series: [
          { name: '活跃会话', type: 'bar', barMaxWidth: 18, data: nameSet.map(n => pick(sessions, n)), itemStyle: { color: '#3B73B3' } },
          { name: '累计绑定', type: 'bar', barMaxWidth: 18, data: nameSet.map(n => pick(binds, n)), itemStyle: { color: '#2B8C6E' } }
        ]
      });
    },
    num(v) {
      const n = Number(v);
      return Number.isFinite(n) ? n : 0;
    },
    progressPct(row) {
      const total = this.num(row.totalCount);
      if (total <= 0) return 0;
      return Math.min(100, Math.round(this.num(row.completedCount) / total * 100));
    },
    statusLabel(s) {
      return { '0': "离线", '1': "在线", '2': "忙碌", '3': "休息" }[s] || s;
    },
    statusTag(s) {
      return { '0': "info", '1': "success", '2': "warning", '3': "danger" }[s] || "";
    },
    callStatusLabel(s) {
      return { '0': "空闲", '1': "通话中", '2': "保持", '3': "咨询中", '4': "三方", '5': "话后整理" }[s] || s;
    }
  }
};
</script>

<style scoped>
.dashboard-container { padding: 16px 20px; }
.filter-bar { display: flex; align-items: center; margin-bottom: 14px; }
.filter-bar .title { font-size: 18px; font-weight: 600; color: #1F2A3A; margin-right: 20px; }
.filter-bar .auto-tip { font-size: 12px; color: #8C8C8C; margin-left: 12px; }
.stat-row { margin-bottom: 12px; }
.stat-card { border-radius: 6px; padding: 16px 10px; color: #fff; text-align: center; }
.stat-num { font-size: 24px; font-weight: bold; }
.stat-label { font-size: 12px; margin-top: 6px; opacity: 0.92; }
.panel-card { margin-bottom: 0; }
.care-box { display: flex; align-items: center; justify-content: space-around; height: 260px; }
.care-item { text-align: center; }
.care-num { font-size: 34px; font-weight: bold; color: #1A3C6E; }
.care-sub { font-size: 16px; font-weight: 600; color: #1F2A3A; margin-bottom: 6px; }
.care-label { font-size: 12px; color: #8C8C8C; margin-top: 8px; }
</style>
