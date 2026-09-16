<template>
  <div class="dashboard-container sla-board">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="title">工单 SLA 看板</span>
      <el-date-picker v-model="rangeTime" type="datetimerange" size="small"
        value-format="yyyy-MM-dd HH:mm:ss" range-separator="至" start-placeholder="建单开始" end-placeholder="建单结束"
        style="width: 360px" />
      <el-select v-model="warnMinutes" size="small" style="width: 150px" @change="loadData">
        <el-option label="预警窗口 30 分钟" :value="30" />
        <el-option label="预警窗口 60 分钟" :value="60" />
        <el-option label="预警窗口 2 小时" :value="120" />
        <el-option label="预警窗口 4 小时" :value="240" />
      </el-select>
      <el-button type="primary" size="small" icon="el-icon-refresh" @click="loadData">刷新</el-button>
      <span class="auto-tip">统计口径：区间内建单；临近超时为当前进行中工单，每 60 秒自动刷新</span>
    </div>

    <!-- 指标卡 -->
    <el-row :gutter="12" class="stat-row" v-loading="loading">
      <el-col :span="3" v-for="c in statCards" :key="c.label">
        <div class="stat-card" :style="{ background: c.color }">
          <div class="stat-num">{{ c.value }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </el-col>
    </el-row>

    <!-- 条线达成率 + 跨域流转 -->
    <el-row :gutter="12">
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>各业务条线 SLA 达成率</span></div>
          <div ref="bizChart" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>跨域/12345 转办量与办结率</span></div>
          <el-table :data="transferStat" size="small" border max-height="300">
            <el-table-column label="条线" min-width="120">
              <template slot-scope="s">{{ bizText(s.row.bizType) }}</template>
            </el-table-column>
            <el-table-column label="方向" width="90" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="s.row.direction === 'OUT' ? 'warning' : 'success'">
                  {{ s.row.direction === 'OUT' ? '转出' : '转入' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="转办量" prop="transferCount" width="80" align="center" />
            <el-table-column label="已办结" prop="doneCount" width="80" align="center" />
            <el-table-column label="办结率" width="160">
              <template slot-scope="s">
                <el-progress :percentage="rate(s.row.doneCount, s.row.transferCount)" :stroke-width="10" :text-inside="true" />
              </template>
            </el-table-column>
            <el-table-column label="均办结(分)" prop="avgHandleMinutes" width="100" align="center">
              <template slot-scope="s">{{ s.row.avgHandleMinutes != null ? s.row.avgHandleMinutes : '-' }}</template>
            </el-table-column>
          </el-table>
          <div v-if="!transferStat.length" class="empty-tip">区间内暂无转办数据</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 人员达成率 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="24">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>处理人办结达成（区间内已办结工单）</span></div>
          <el-table :data="achieveByAssignee" size="small" border max-height="280">
            <el-table-column label="处理人" prop="userName" min-width="140" :show-overflow-tooltip="true" />
            <el-table-column label="已办结" prop="closedCount" width="100" align="center" />
            <el-table-column label="准时办结" prop="onTimeCount" width="100" align="center" />
            <el-table-column label="准时率" min-width="200">
              <template slot-scope="s">
                <el-progress :percentage="rate(s.row.onTimeCount, s.row.closedCount)" :stroke-width="12"
                  :color="rateColor(rate(s.row.onTimeCount, s.row.closedCount))" :text-inside="true" />
              </template>
            </el-table-column>
            <el-table-column label="平均处理(分)" prop="avgHandleMinutes" width="120" align="center" />
          </el-table>
          <div v-if="!achieveByAssignee.length" class="empty-tip">区间内暂无已办结工单</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 超时 TOP + 临近超时 -->
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span class="danger-text">超时工单 TOP{{ overtimeTop.length ? ' ' + overtimeTop.length : '' }}</span></div>
          <el-table :data="overtimeTop" size="small" border max-height="320">
            <el-table-column label="工单号" prop="ticketNo" min-width="130" :show-overflow-tooltip="true" />
            <el-table-column label="标题" prop="title" min-width="150" :show-overflow-tooltip="true" />
            <el-table-column label="条线" width="100" align="center">
              <template slot-scope="s">{{ bizText(s.row.bizType) }}</template>
            </el-table-column>
            <el-table-column label="优先级" width="80" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="s.row.priority === '1' ? 'danger' : (s.row.priority === '3' ? 'info' : '')">
                  {{ priorityText(s.row.priority) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="isOpen(s.row.status) ? 'danger' : 'warning'">
                  {{ isOpen(s.row.status) ? '超期未结' : '办结超时' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="超期(分)" width="90" align="center">
              <template slot-scope="s">
                <span class="danger-text">{{ num(s.row.overdueMinutes) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!overtimeTop.length" class="empty-tip">区间内暂无超时工单</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span class="warn-text">临近超时（剩余 ≤ {{ warnMinutes }} 分钟）</span></div>
          <el-table :data="dueSoon" size="small" border max-height="320">
            <el-table-column label="工单号" prop="ticketNo" min-width="130" :show-overflow-tooltip="true" />
            <el-table-column label="标题" prop="title" min-width="150" :show-overflow-tooltip="true" />
            <el-table-column label="处理人" prop="assignUserName" min-width="100">
              <template slot-scope="s">{{ s.row.assignUserName || '未分配' }}</template>
            </el-table-column>
            <el-table-column label="截止时间" prop="dueTime" min-width="150" />
            <el-table-column label="剩余" width="100" align="center">
              <template slot-scope="s">
                <el-tag size="mini" :type="remainType(s.row.remainSeconds)">{{ remainText(s.row.remainSeconds) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!dueSoon.length" class="empty-tip">当前无临近超时工单</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts';
import { slaBoardData } from '@/api/lawyers/slaPolicy';

export default {
  name: 'CollabSlaBoard',
  data() {
    return {
      loading: false,
      rangeTime: [],
      warnMinutes: 60,
      overview: {},
      achieveByBizType: [],
      achieveByAssignee: [],
      overtimeTop: [],
      dueSoon: [],
      transferStat: [],
      timer: null,
      bizChart: null
    };
  },
  computed: {
    statCards() {
      const o = this.overview;
      return [
        { label: '工单总量', value: this.num(o.totalCount), color: '#1A3C6E' },
        { label: '已办结', value: this.num(o.closedCount), color: '#2B8C6E' },
        { label: '准时率(%)', value: this.num(o.achievementRate), color: '#3B73B3' },
        { label: '办结率(%)', value: this.num(o.closeRate), color: '#54A68B' },
        { label: '进行中', value: this.num(o.openCount), color: '#C9A96E' },
        { label: '进行中超期', value: this.num(o.openOvertimeCount), color: '#C63D4A' },
        { label: '临近超时', value: this.num(o.dueSoonCount), color: '#E8923A' },
        { label: '超时合计', value: this.num(o.totalOvertimeCount), color: '#8C4A55' }
      ];
    }
  },
  created() {
    this.loadData();
    this.timer = setInterval(this.loadData, 60000);
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer);
    if (this.bizChart && this.bizChart.dispose) this.bizChart.dispose();
    window.removeEventListener('resize', this.resizeChart);
  },
  methods: {
    loadData() {
      this.loading = true;
      const params = { warnMinutes: this.warnMinutes };
      if (this.rangeTime && this.rangeTime.length === 2) {
        params.beginTime = this.rangeTime[0];
        params.endTime = this.rangeTime[1];
      }
      slaBoardData(params).then(res => {
        const d = res.data || {};
        this.overview = d.overview || {};
        this.achieveByBizType = d.achieveByBizType || [];
        this.achieveByAssignee = d.achieveByAssignee || [];
        this.overtimeTop = d.overtimeTop || [];
        this.dueSoon = d.dueSoon || [];
        this.transferStat = d.transferStat || [];
        this.$nextTick(() => {
          this.renderBiz();
          window.addEventListener('resize', this.resizeChart);
        });
      }).finally(() => { this.loading = false; });
    },
    renderBiz() {
      if (!this.$refs.bizChart) return;
      if (!this.bizChart) this.bizChart = echarts.init(this.$refs.bizChart);
      const rows = this.achieveByBizType;
      this.bizChart.setOption({
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          formatter: params => {
            const i = params[0].dataIndex;
            const r = rows[i] || {};
            return `${params[0].name}<br/>总量: ${this.num(r.totalCount)}`
              + `<br/>已办结: ${this.num(r.closedCount)}`
              + `<br/>准时: ${this.num(r.onTimeCount)}`
              + `<br/>进行中超期: ${this.num(r.openOvertimeCount)}`
              + `<br/>准时率: ${this.rate(r.onTimeCount, r.closedCount)}%`;
          }
        },
        legend: { data: ['准时率', '办结率'], top: 0 },
        grid: { left: 40, right: 20, top: 40, bottom: 60 },
        xAxis: { type: 'category', data: rows.map(r => this.bizText(r.bizType)), axisLabel: { rotate: 30, fontSize: 11 } },
        yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
        series: [
          {
            name: '准时率', type: 'bar', barMaxWidth: 26,
            data: rows.map(r => this.rate(r.onTimeCount, r.closedCount)),
            itemStyle: { color: '#2B8C6E' },
            label: { show: true, position: 'top', formatter: '{c}%', fontSize: 10 }
          },
          {
            name: '办结率', type: 'bar', barMaxWidth: 26,
            data: rows.map(r => this.rate(r.closedCount, r.totalCount)),
            itemStyle: { color: '#3B73B3' },
            label: { show: true, position: 'top', formatter: '{c}%', fontSize: 10 }
          }
        ]
      });
    },
    resizeChart() {
      if (this.bizChart && this.bizChart.resize) this.bizChart.resize();
    },
    rate(part, total) {
      const p = this.num(part);
      const t = this.num(total);
      if (t <= 0) return 0;
      return Math.round(p / t * 1000) / 10;
    },
    rateColor(r) {
      if (r >= 90) return '#2B8C6E';
      if (r >= 70) return '#E8923A';
      return '#C63D4A';
    },
    num(v) {
      const n = Number(v);
      return Number.isFinite(n) ? n : 0;
    },
    isOpen(status) {
      return status === '0' || status === '1';
    },
    priorityText(v) {
      return { '1': '紧急', '2': '普通', '3': '低' }[String(v)] || v;
    },
    bizText(v) {
      const map = {
        INTERNAL: '热线工单',
        LEGAL_AID: '法律援助',
        MEDIATION: '人民调解',
        NOTARY: '公证',
        FORENSIC: '司法鉴定',
        ARBITRATION: '仲裁',
        HOTLINE_12345: '12345协同'
      };
      return map[v] || (v || '-');
    },
    remainText(seconds) {
      const s = this.num(seconds);
      if (s <= 0) return '已超时';
      if (s < 3600) return Math.ceil(s / 60) + ' 分钟';
      return Math.round(s / 3600 * 10) / 10 + ' 小时';
    },
    remainType(seconds) {
      const s = this.num(seconds);
      if (s <= 600) return 'danger';
      if (s <= 1800) return 'warning';
      return '';
    }
  }
};
</script>

<style scoped>
.sla-board .filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.sla-board .title {
  font-size: 18px;
  font-weight: 600;
  color: #1A3C6E;
  margin-right: 8px;
}
.sla-board .auto-tip {
  font-size: 12px;
  color: #909399;
}
.sla-board .stat-row {
  margin-bottom: 12px;
}
.sla-board .stat-card {
  border-radius: 6px;
  padding: 16px 10px;
  color: #fff;
  text-align: center;
}
.sla-board .stat-num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
}
.sla-board .stat-label {
  font-size: 13px;
  margin-top: 6px;
  opacity: 0.92;
}
.sla-board .panel-card {
  border-radius: 6px;
}
.sla-board .empty-tip {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
}
.danger-text { color: #C63D4A; font-weight: 600; }
.warn-text { color: #E8923A; font-weight: 600; }
</style>
