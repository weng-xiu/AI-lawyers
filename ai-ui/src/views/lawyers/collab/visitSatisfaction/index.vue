<template>
  <div class="dashboard-container vs-board">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="title">智能回访 · 满意度归因看板</span>
      <el-date-picker v-model="rangeTime" type="daterange" size="small"
        value-format="yyyy-MM-dd" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
        style="width: 280px" />
      <el-button type="primary" size="small" icon="el-icon-refresh" @click="loadData">刷新</el-button>
      <span class="auto-tip">统计口径：区间内已回访/已评价；回访任务与逾期分级为当前全量；每 60 秒自动刷新</span>
    </div>

    <!-- 核心指标卡 -->
    <el-row :gutter="12" class="stat-row" v-loading="loading">
      <el-col :span="3" v-for="c in statCards" :key="c.label">
        <div class="stat-card" :style="{ background: c.color }">
          <div class="stat-num">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势 + 情绪 -->
    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>电话回访满意度趋势（四档堆叠）</span></div>
          <div ref="trendChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>情绪极性分布（电话+图文）</span></div>
          <div ref="sentimentChart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 原因归因 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="14">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>不满意原因 TOP（规则归因 · 电话+图文，可多因叠加）</span></div>
          <div ref="reasonChart" style="height: 300px"></div>
          <div v-if="!reasonTop.length" class="empty-tip">区间内暂无负向文本可归因</div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>图文咨询评价（1-5 分）</span></div>
          <el-table :data="evalDimRows" size="small" border>
            <el-table-column label="维度" prop="dim" min-width="120" />
            <el-table-column label="平均分" prop="score" width="90" align="center" />
            <el-table-column label="水平" min-width="180">
              <template slot-scope="s">
                <el-progress :percentage="scorePct(s.row.score)" :color="scoreColor(s.row.score)"
                  :stroke-width="12" :text-inside="true" />
              </template>
            </el-table-column>
          </el-table>
          <div class="eval-rate">
            <span>好评率(≥4分)：<b class="green">{{ num(evaluation.positiveRate) }}%</b></span>
            <span>差评率(≤2分)：<b class="red">{{ num(evaluation.negativeRate) }}%</b></span>
            <span>评价数：{{ num(evaluation.evalCount) }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 回访员排行 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="24">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>回访员满意度排行（区间内已回访）</span></div>
          <el-table :data="visitorRanking" size="small" border max-height="280">
            <el-table-column type="index" label="#" width="50" align="center" />
            <el-table-column label="回访员" prop="userName" min-width="140" :show-overflow-tooltip="true" />
            <el-table-column label="回访量" prop="visitCount" width="100" align="center" />
            <el-table-column label="满意(1/2档)" prop="satisfiedCount" width="120" align="center" />
            <el-table-column label="不满意(4档)" prop="unsatisfiedCount" width="120" align="center">
              <template slot-scope="s">
                <span :class="{ 'red-text': num(s.row.unsatisfiedCount) > 0 }">{{ num(s.row.unsatisfiedCount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="平均满意度(满分4)" min-width="200">
              <template slot-scope="s">
                <el-progress :percentage="scorePct(num(s.row.avgScore) / 4 * 5)" :color="scoreColor(s.row.avgScore * 1.25)"
                  :stroke-width="12" :text-inside="true" />
                <span class="dim-score">{{ s.row.avgScore != null ? s.row.avgScore : '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!visitorRanking.length" class="empty-tip">区间内暂无回访员数据</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务运营 + 逾期升级 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header"><span>回访任务运营（当前全量）</span></div>
          <div class="task-grid">
            <div class="task-item"><span>任务总数</span><b>{{ num(taskOverview.totalCount) }}</b></div>
            <div class="task-item"><span>待回访</span><b class="amber">{{ num(taskOverview.pendingCount) }}</b></div>
            <div class="task-item"><span>已完成</span><b class="green">{{ num(taskOverview.completedCount) }}</b></div>
            <div class="task-item"><span>已逾期</span><b class="red">{{ num(taskOverview.overdueCount) }}</b></div>
            <div class="task-item"><span>待访高优</span><b class="red">{{ num(taskOverview.pendingHighCount) }}</b></div>
            <div class="task-item"><span>完成率</span><b>{{ taskCompleteRate }}%</b></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header">
            <span class="red-text">逾期任务分级监控（自动逐级升级）</span>
            <el-tooltip placement="top" content="L1 刚逾期提醒受理人；L2 逾期≥1天升级班组长；L3 逾期≥3天升级分管领导。每级仅广播一次。">
              <i class="el-icon-question tip-icon"></i>
            </el-tooltip>
          </div>
          <div class="level-cards">
            <div class="level-card l1">
              <div class="lv-name">L1 刚逾期</div>
              <div class="lv-num">{{ num(overdueSummary.level1Count) }}</div>
              <div class="lv-desc">超期 &lt; 1 天</div>
            </div>
            <div class="level-card l2">
              <div class="lv-name">L2 逾期≥1天</div>
              <div class="lv-num">{{ num(overdueSummary.level2Count) }}</div>
              <div class="lv-desc">升级班组长</div>
            </div>
            <div class="level-card l3">
              <div class="lv-name">L3 逾期≥3天</div>
              <div class="lv-num">{{ num(overdueSummary.level3Count) }}</div>
              <div class="lv-desc">升级分管领导</div>
            </div>
          </div>
          <el-table :data="overdueTasks" size="small" border max-height="260" style="margin-top: 10px">
            <el-table-column label="任务号" prop="taskNo" min-width="120" :show-overflow-tooltip="true" />
            <el-table-column label="来电人" prop="callerName" min-width="100">
              <template slot-scope="s">{{ s.row.callerName || '-' }}</template>
            </el-table-column>
            <el-table-column label="受理人" prop="assignee" min-width="100">
              <template slot-scope="s">{{ s.row.assignee || '未分配' }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="s.row.priority === '1' ? 'danger' : (s.row.priority === '3' ? 'info' : '')">
                  {{ priorityText(s.row.priority) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="超期(小时)" width="100" align="center">
              <template slot-scope="s">{{ Math.round(num(s.row.overdueMinutes) / 60 * 10) / 10 }}</template>
            </el-table-column>
            <el-table-column label="级别" width="80" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="s.row.level === 3 ? 'danger' : (s.row.level === 2 ? 'warning' : 'info')">
                  L{{ s.row.level }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!overdueTasks.length" class="empty-tip">当前无逾期任务</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts';
import { visitSatisfactionBoard } from '@/api/lawyers/visitSatisfaction';

export default {
  name: 'CollabVisitSatisfactionBoard',
  data() {
    return {
      loading: false,
      rangeTime: [],
      callbackOverview: {},
      callbackTrend: [],
      visitorRanking: [],
      evaluation: {},
      reasonTop: [],
      sentiment: {},
      taskOverview: {},
      overdueSummary: {},
      overdueTasks: [],
      timer: null,
      charts: { trend: null, sentiment: null, reason: null }
    };
  },
  computed: {
    statCards() {
      const cb = this.callbackOverview;
      const ev = this.evaluation;
      const st = this.sentiment;
      return [
        { label: '电话回访量', value: this.num(cb.visitCount), color: '#1A3C6E' },
        { label: '回访满意率%', value: this.num(cb.satisfiedRate), color: '#2B8C6E' },
        { label: '回访不满意数', value: this.num(cb.unsatisfiedCount), color: '#C63D4A' },
        { label: '图文均分', value: this.num(ev.avgOverall), color: '#3B73B3' },
        { label: '图文好评率%', value: this.num(ev.positiveRate), color: '#54A68B' },
        { label: '正向情绪率%', value: this.num(st.positiveRate), color: '#3E8E7E' },
        { label: '回访已逾期', value: this.num(this.taskOverview.overdueCount), color: '#E8923A' },
        { label: '待回访', value: this.num(this.taskOverview.pendingCount), color: '#C9A96E' }
      ];
    },
    evalDimRows() {
      const ev = this.evaluation;
      return [
        { dim: '总体评价', score: ev.avgOverall },
        { dim: '专业度', score: ev.avgProfessionalism },
        { dim: '响应速度', score: ev.avgResponsiveness },
        { dim: '解答质量', score: ev.avgQuality }
      ];
    },
    taskCompleteRate() {
      const total = this.num(this.taskOverview.totalCount);
      const done = this.num(this.taskOverview.completedCount);
      return total > 0 ? Math.round(done / total * 1000) / 10 : 0;
    }
  },
  created() {
    this.loadData();
    this.timer = setInterval(this.loadData, 60000);
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer);
    Object.values(this.charts).forEach(c => c && c.dispose && c.dispose());
    window.removeEventListener('resize', this.resizeCharts);
  },
  methods: {
    loadData() {
      this.loading = true;
      const params = {};
      if (this.rangeTime && this.rangeTime.length === 2) {
        params.beginTime = this.rangeTime[0] + ' 00:00:00';
        params.endTime = this.rangeTime[1] + ' 23:59:59';
      }
      visitSatisfactionBoard(params).then(res => {
        const d = res.data || {};
        this.callbackOverview = d.callbackOverview || {};
        this.callbackTrend = d.callbackTrend || [];
        this.visitorRanking = d.visitorRanking || [];
        this.evaluation = d.evaluation || {};
        this.reasonTop = d.reasonTop || [];
        this.sentiment = d.sentiment || {};
        this.taskOverview = d.taskOverview || {};
        this.overdueSummary = d.overdueSummary || {};
        this.overdueTasks = d.overdueTasks || [];
        this.$nextTick(() => {
          this.renderTrend();
          this.renderSentiment();
          this.renderReason();
          window.addEventListener('resize', this.resizeCharts);
        });
      }).finally(() => { this.loading = false; });
    },
    renderTrend() {
      if (!this.$refs.trendChart) return;
      if (!this.charts.trend) this.charts.trend = echarts.init(this.$refs.trendChart);
      const rows = this.callbackTrend;
      this.charts.trend.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { data: ['非常满意', '满意', '一般', '不满意'], top: 0 },
        grid: { left: 40, right: 20, top: 40, bottom: 40 },
        xAxis: { type: 'category', data: rows.map(r => this.fmtDate(r.statDate)) },
        yAxis: { type: 'value', minInterval: 1 },
        color: ['#2B8C6E', '#54A68B', '#E8B64A', '#C63D4A'],
        series: [
          { name: '非常满意', type: 'bar', stack: 's', data: rows.map(r => this.num(r.verySatisfied)) },
          { name: '满意', type: 'bar', stack: 's', data: rows.map(r => this.num(r.satisfied)) },
          { name: '一般', type: 'bar', stack: 's', data: rows.map(r => this.num(r.normal)) },
          { name: '不满意', type: 'bar', stack: 's', data: rows.map(r => this.num(r.unsatisfied)) }
        ]
      });
    },
    renderSentiment() {
      if (!this.$refs.sentimentChart) return;
      if (!this.charts.sentiment) this.charts.sentiment = echarts.init(this.$refs.sentimentChart);
      this.charts.sentiment.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0 },
        color: ['#2B8C6E', '#E8B64A', '#C63D4A'],
        series: [{
          type: 'pie', radius: ['42%', '68%'], center: ['50%', '46%'],
          label: { formatter: '{b}\n{d}%', fontSize: 12 },
          data: [
            { name: '正向', value: this.num(this.sentiment.positive) },
            { name: '中性', value: this.num(this.sentiment.neutral) },
            { name: '负向', value: this.num(this.sentiment.negative) }
          ]
        }]
      });
    },
    renderReason() {
      if (!this.$refs.reasonChart) return;
      if (!this.charts.reason) this.charts.reason = echarts.init(this.$refs.reasonChart);
      const rows = [...this.reasonTop].reverse();
      this.charts.reason.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 130, right: 40, top: 10, bottom: 30 },
        xAxis: { type: 'value', minInterval: 1 },
        yAxis: { type: 'category', data: rows.map(r => r.reason), axisLabel: { fontSize: 12 } },
        series: [{
          type: 'bar', barMaxWidth: 22, data: rows.map(r => this.num(r.count)),
          itemStyle: { color: '#C63D4A' },
          label: { show: true, position: 'right', fontSize: 11 }
        }]
      });
    },
    resizeCharts() {
      Object.values(this.charts).forEach(c => c && c.resize && c.resize());
    },
    num(v) {
      const n = Number(v);
      return Number.isFinite(n) ? n : 0;
    },
    scorePct(score) {
      const s = this.num(score);
      return Math.max(0, Math.min(100, Math.round(s / 5 * 100)));
    },
    scoreColor(score) {
      const s = this.num(score);
      if (s >= 4) return '#2B8C6E';
      if (s >= 3) return '#E8B64A';
      return '#C63D4A';
    },
    fmtDate(d) {
      if (!d) return '';
      return String(d).substring(5, 10);
    },
    priorityText(p) {
      return { '1': '高', '2': '中', '3': '低' }[String(p)] || '-';
    }
  }
};
</script>

<style scoped>
.vs-board .filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.vs-board .title { font-size: 18px; font-weight: 600; color: #1A3C6E; margin-right: 8px; }
.vs-board .auto-tip { font-size: 12px; color: #909399; }
.vs-board .stat-row { margin-bottom: 12px; }
.vs-board .stat-card { border-radius: 6px; padding: 14px 8px; color: #fff; text-align: center; }
.vs-board .stat-num { font-size: 24px; font-weight: 700; line-height: 1.2; }
.vs-board .stat-label { font-size: 12px; margin-top: 6px; opacity: 0.92; }
.vs-board .panel-card { border-radius: 6px; }
.vs-board .empty-tip { text-align: center; color: #909399; font-size: 13px; padding: 20px 0; }
.vs-board .eval-rate { display: flex; gap: 18px; margin-top: 12px; font-size: 13px; color: #606266; flex-wrap: wrap; }
.green { color: #2B8C6E; }
.red, .red-text { color: #C63D4A; }
.amber { color: #E8923A; }
.tip-icon { color: #909399; margin-left: 6px; }
.vs-board .task-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.vs-board .task-item {
  display: flex; justify-content: space-between; align-items: center;
  background: #f6f8fb; border-radius: 6px; padding: 12px 14px; font-size: 13px; color: #606266;
}
.vs-board .task-item b { font-size: 18px; color: #1A3C6E; }
.vs-board .level-cards { display: flex; gap: 10px; }
.vs-board .level-card { flex: 1; border-radius: 6px; padding: 14px; text-align: center; color: #fff; }
.vs-board .level-card.l1 { background: #E8B64A; }
.vs-board .level-card.l2 { background: #E8923A; }
.vs-board .level-card.l3 { background: #C63D4A; }
.vs-board .lv-name { font-size: 13px; opacity: 0.95; }
.vs-board .lv-num { font-size: 28px; font-weight: 700; margin: 4px 0; }
.vs-board .lv-desc { font-size: 12px; opacity: 0.9; }
.dim-score { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
