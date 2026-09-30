<template>
  <div class="monitor-container">
    <el-row :gutter="12" class="overview-row">
      <el-col :span="4"><div class="ov-card ov-total"><div class="ov-num">{{ overview.totalCalls || 0 }}</div><div class="ov-label">今日呼叫</div></div></el-col>
      <el-col :span="4"><div class="ov-card ov-success"><div class="ov-num">{{ rate(overview.connectRate) }}</div><div class="ov-label">接通率</div></div></el-col>
      <el-col :span="4"><div class="ov-card ov-info"><div class="ov-num">{{ overview.activeTrunks || 0 }}/{{ overview.totalTrunks || 0 }}</div><div class="ov-label">健康线路</div></div></el-col>
      <el-col :span="4"><div class="ov-card ov-warn"><div class="ov-num">{{ overview.faultTrunks || 0 }}</div><div class="ov-label">故障/熔断</div></div></el-col>
      <el-col :span="4"><div class="ov-card ov-primary"><div class="ov-num">{{ overview.globalConcurrent || 0 }}</div><div class="ov-label">当前并发</div></div></el-col>
      <el-col :span="4"><div class="ov-card ov-danger"><div class="ov-num">{{ overview.activeAlarms || 0 }}</div><div class="ov-label">未处理告警</div></div></el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix">
            <span>实时线路状态</span>
            <el-button style="float: right; padding: 3px 0" type="text" icon="el-icon-refresh" @click="loadTrunkStatus">刷新</el-button>
          </div>
          <el-table :data="trunkStatus" size="small" border max-height="360">
            <el-table-column label="线路" prop="trunkName" min-width="130" />
            <el-table-column label="运营商" width="90" align="center">
              <template slot-scope="s"><el-tag :type="carrierTag(s.row.carrier)" size="mini">{{ carrierName(s.row.carrier) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="类型" prop="trunkType" width="70" align="center" />
            <el-table-column label="健康" width="80" align="center">
              <template slot-scope="s"><el-tag :type="healthTag(s.row.healthStatus)" size="mini">{{ healthName(s.row.healthStatus) }}</el-tag></template>
            </el-table-column>
            <el-table-column label="并发" width="100" align="center">
              <template slot-scope="s">
                <el-progress :percentage="pct(s.row.currentConcurrent, s.row.maxConcurrent)" :stroke-width="10" :text-inside="true" />
              </template>
            </el-table-column>
            <el-table-column label="接通率" width="90" align="center">
              <template slot-scope="s">{{ rate(s.row.successRate) }}</template>
            </el-table-column>
            <el-table-column label="平均时延" width="90" align="center">
              <template slot-scope="s">{{ s.row.avgLatency != null ? s.row.avgLatency + 'ms' : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix"><span>运营商维度统计</span></div>
          <el-table :data="carrierStat" size="small" border>
            <el-table-column label="运营商" prop="carrierName" min-width="100" />
            <el-table-column label="呼叫数" prop="totalCalls" width="70" align="center" />
            <el-table-column label="接通率" width="80" align="center">
              <template slot-scope="s">{{ rate(s.row.successRate) }}</template>
            </el-table-column>
            <el-table-column label="平均时长" width="90" align="center">
              <template slot-scope="s">{{ s.row.avgDuration != null ? s.row.avgDuration + 's' : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix">
            <span>线路质量趋势</span>
            <el-select v-model="trendTrunkId" placeholder="全部线路" clearable size="mini" style="width: 160px; margin-left: 10px" @change="loadTrend">
              <el-option v-for="t in trunkStatus" :key="t.trunkId" :label="t.trunkName" :value="t.trunkId" />
            </el-select>
          </div>
          <div ref="trendChart" style="height: 260px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix">
            <span>告警列表</span>
            <el-button style="float: right; padding: 3px 0" type="text" icon="el-icon-refresh" @click="loadAlarms">刷新</el-button>
          </div>
          <el-table :data="alarms" size="small" border max-height="260">
            <el-table-column label="级别" width="70" align="center">
              <template slot-scope="s"><el-tag :type="alarmTag(s.row.severity)" size="mini">{{ s.row.severity }}</el-tag></template>
            </el-table-column>
            <el-table-column label="内容" prop="alarmText" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="60" align="center">
              <template slot-scope="s">
                <el-button size="mini" type="text" @click="handleAlarm(s.row)">处理</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="24">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="clearfix">
            <span>PJSIP 端点 / 呼叫队列（只读）</span>
            <span class="pjsip-hint">{{ pjsipHint }}</span>
          </div>
          <el-tabs v-model="pjsipTab">
            <el-tab-pane name="endpoints">
              <span slot="label">端点 <el-badge :value="pjsipEndpoints.length" type="primary" /></span>
              <el-table :data="pjsipEndpoints" size="mini" border max-height="300">
                <el-table-column label="端点" prop="name" width="140" />
                <el-table-column label="上下文" prop="context" width="120" />
                <el-table-column label="主叫标识" prop="callerId" min-width="120" show-overflow-tooltip />
                <el-table-column label="AOR" prop="aors" width="120" />
                <el-table-column label="静态 Contact" min-width="200" show-overflow-tooltip>
                  <template slot-scope="s">{{ (s.row.contacts || []).join('，') || '-' }}</template>
                </el-table-column>
                <el-table-column label="编解码" prop="allow" min-width="130" show-overflow-tooltip />
                <el-table-column label="Transport" prop="transport" width="100" />
                <el-table-column label="直连媒体" prop="directMedia" width="80" align="center" />
                <el-table-column label="rport" prop="forceRport" width="70" align="center" />
                <el-table-column label="重写Contact" prop="rewriteContact" width="90" align="center" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane name="queues">
              <span slot="label">队列 <el-badge :value="pjsipQueues.length" type="primary" /></span>
              <el-table :data="pjsipQueues" size="mini" border max-height="300">
                <el-table-column label="队列" prop="name" width="150" />
                <el-table-column label="策略" prop="strategy" width="110" />
                <el-table-column label="振铃超时(s)" prop="timeout" width="100" align="center" />
                <el-table-column label="最大人数" prop="maxlen" width="80" align="center" />
                <el-table-column label="服务水平(s)" prop="serviceLevel" width="100" align="center" />
                <el-table-column label="保持音乐" prop="musicClass" width="100" />
                <el-table-column label="成员（静态配置）" min-width="220">
                  <template slot-scope="s">
                    <div v-if="s.row.members && s.row.members.length">
                      <div v-for="(m, i) in s.row.members" :key="i" class="member-line">{{ m }}</div>
                    </div>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane name="registrations">
              <span slot="label">注册态 <el-badge :value="pjsipRegCount" type="primary" /></span>
              <div v-if="!pjsipRegInfo.available" class="pjsip-hint" style="padding: 4px 0">
                AMI 实时查询不可用：{{ pjsipRegInfo.error || '未知原因' }}
              </div>
              <el-table v-else :data="pjsipRegs" size="mini" border max-height="300">
                <el-table-column label="端点" prop="name" width="140" />
                <el-table-column label="设备状态" width="120" align="center">
                  <template slot-scope="s"><el-tag :type="regStateTag(s.row.deviceState)" size="mini">{{ s.row.deviceState || '-' }}</el-tag></template>
                </el-table-column>
                <el-table-column label="活动通道" prop="activeChannels" width="80" align="center" />
                <el-table-column label="实时 Contact 状态" min-width="320">
                  <template slot-scope="s">
                    <div v-if="s.row.contacts && s.row.contacts.length">
                      <div v-for="(c, i) in s.row.contacts" :key="i" class="member-line">
                        <el-tag :type="contactTag(c.status)" size="mini" style="margin-right: 6px">{{ c.status || '未知' }}</el-tag>{{ c.uri }}
                      </div>
                    </div>
                    <span v-else>无注册</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import {
  trunkOverview, trunkStatusList, carrierStat as carrierStatApi, trunkTrend,
  trunkAlarmList, handleTrunkAlarm, trunkHealthCheck,
  pjsipEndpoints, pjsipQueues, pjsipSummary, pjsipRegistrations
} from '@/api/lawyers/trunk'

export default {
  name: 'TrunkMonitor',
  data() {
    return {
      overview: {},
      trunkStatus: [],
      carrierStat: [],
      alarms: [],
      trendTrunkId: undefined,
      timer: null,
      chart: null,
      // P3-B5：PJSIP/队列只读模型
      pjsipTab: 'endpoints',
      pjsipEndpoints: [],
      pjsipQueues: [],
      pjsipSummary: {},
      // V2.53：端点实时注册态
      pjsipRegInfo: {},
      pjsipRegs: []
    }
  },
  computed: {
    pjsipHint() {
      const s = this.pjsipSummary || {}
      if (s.error) return '配置解析异常：' + s.error
      const parts = [s.configDir || '']
      parts.push('pjsip.conf ' + (s.pjsipExists ? '已找到' : '未找到'))
      parts.push('queues.conf ' + (s.queuesExists ? '已找到' : '未找到'))
      return parts.join(' · ')
    },
    pjsipRegCount() {
      return this.pjsipRegInfo.available ? this.pjsipRegs.length : 0
    }
  },
  created() {
    this.refreshAll()
    this.timer = setInterval(this.refreshAll, 15000)
  },
  beforeDestroy() {
    if (this.timer) clearInterval(this.timer)
    if (this.chart && this.chart.dispose) this.chart.dispose()
  },
  methods: {
    refreshAll() {
      this.loadOverview()
      this.loadTrunkStatus()
      this.loadCarrier()
      this.loadTrend()
      this.loadAlarms()
      this.loadPjsip()
    },
    loadPjsip() {
      pjsipSummary().then(res => { this.pjsipSummary = res.data || {} })
      pjsipEndpoints().then(res => { this.pjsipEndpoints = res.data || [] })
      pjsipQueues().then(res => { this.pjsipQueues = res.data || [] })
      pjsipRegistrations().then(res => {
        this.pjsipRegInfo = res.data || {}
        this.pjsipRegs = this.pjsipRegInfo.registrations || []
      })
    },
    loadOverview() {
      trunkOverview().then(res => { this.overview = res.data || {} })
    },
    loadTrunkStatus() {
      trunkStatusList().then(res => { this.trunkStatus = res.data || [] })
    },
    loadCarrier() {
      carrierStatApi().then(res => { this.carrierStat = res.data || [] })
    },
    loadTrend() {
      trunkTrend({ trunkId: this.trendTrunkId, dimension: 'CONNECT_RATE', limit: 30 }).then(res => {
        this.renderTrend(res.data || [])
      })
    },
    loadAlarms() {
      trunkAlarmList({ status: '0', pageSize: 20 }).then(res => {
        this.alarms = (res.rows || res.data || [])
      })
    },
    renderTrend(list) {
      if (!this.$echarts) return
      if (!this.chart) this.chart = this.$echarts.init(this.$refs.trendChart)
      const x = list.map(i => i.statMinute)
      const y = list.map(i => i.connectRate != null ? (i.connectRate * 100).toFixed(1) : 0)
      this.chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 20, bottom: 30 },
        xAxis: { type: 'category', data: x, axisLabel: { rotate: 40, fontSize: 10 } },
        yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
        series: [{ type: 'line', smooth: true, data: y, areaStyle: {}, itemStyle: { color: '#1A3C6E' } }]
      })
    },
    handleAlarm(row) {
      this.$prompt('处理说明', '处理告警', { inputType: 'textarea' }).then(({ value }) => {
        handleTrunkAlarm({ alarmId: row.alarmId, handleRemark: value, status: '1' }).then(() => {
          this.$modal.msgSuccess('已处理')
          this.loadAlarms()
        })
      }).catch(() => {})
    },
    rate(v) {
      if (v == null) return '-'
      return (v * 100).toFixed(1) + '%'
    },
    pct(cur, max) {
      if (!max || max <= 0) return 0
      return Math.min(100, Math.round((cur || 0) / max * 100))
    },
    carrierName(code) {
      const m = { CM: '移动', CU: '联通', CT: '电信', CB: '广电', VI: '虚商', '00': '未知' }
      return m[code] || code
    },
    carrierTag(code) {
      const m = { CM: 'success', CU: 'warning', CT: 'danger', CB: 'info', VI: 'info', '00': '' }
      return m[code] || ''
    },
    healthName(s) {
      const m = { 1: '正常', 2: '亚健康', 3: '故障', 4: '熔断' }
      return m[s] || '未知'
    },
    healthTag(s) {
      const m = { 1: 'success', 2: 'warning', 3: 'danger', 4: 'info' }
      return m[s] || ''
    },
    alarmTag(sev) {
      const m = { HIGH: 'danger', MIDDLE: 'warning', LOW: 'info' }
      return m[sev] || 'info'
    },
    regStateTag(state) {
      const s = state || ''
      if (s.indexOf('Not in use') === 0) return 'success'
      if (s.indexOf('In use') === 0 || s === 'Busy' || s === 'Ringing') return 'primary'
      if (s === 'Unavailable' || s === 'Invalid' || s === 'Uninitialized') return 'danger'
      return 'info'
    },
    contactTag(status) {
      const m = { Avail: 'success', Unavail: 'info', NonQualified: 'warning', Rejected: 'danger' }
      return m[status] || 'info'
    }
  }
}
</script>

<style scoped>
.monitor-container { padding: 24px; }
.overview-row { margin-bottom: 16px; }
.ov-card { border-radius: 6px; padding: 16px; color: #fff; text-align: center; }
.ov-num { font-size: 24px; font-weight: bold; }
.ov-label { font-size: 12px; margin-top: 6px; opacity: 0.9; }
.ov-total { background: #1A3C6E; }
.ov-success { background: #2B8C6E; }
.ov-info { background: #8C8C8C; }
.ov-warn { background: #E8923A; }
.ov-primary { background: #3B73B3; }
.ov-danger { background: #C63D4A; }
.panel-card { margin-bottom: 0; }
.pjsip-hint { margin-left: 12px; font-size: 12px; color: #909399; font-weight: normal; }
.member-line { font-size: 12px; line-height: 1.6; word-break: break-all; }
</style>
