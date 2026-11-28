<template>
  <div class="sim-page">
    <!-- 提交栏 -->
    <el-card shadow="never" class="sim-filter">
      <el-form :inline="true" size="small" @submit.native.prevent>
        <el-form-item label="评测类型">
          <el-radio-group v-model="kind" @change="handleKindChange">
            <el-radio-button label="copilot">坐席辅助 Copilot</el-radio-button>
            <el-radio-button label="robot">语音机器人</el-radio-button>
            <el-radio-button label="asr">ASR 转写质量</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            icon="el-icon-video-play"
            :loading="submitting"
            @click="handleSubmit"
            v-hasPermi="['lawyers:modelConfig:test']"
          >提交评测（默认用例集）</el-button>
        </el-form-item>
      </el-form>
      <el-form label-position="top" size="small">
        <el-form-item label="自定义用例集 JSON（留空跑内置默认用例集，格式见报告调研报告契约）">
          <el-input
            v-model="casesJson"
            type="textarea"
            :rows="4"
            placeholder='{"cases":[{"caseId":"c1","name":"...","text/user/turns...","checks":{...}}]}'
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 进度条 -->
    <el-card v-if="task" shadow="never" class="sim-progress">
      <div class="sim-progress-line">
        <span class="sim-task-id">任务ID：{{ task.taskId }}</span>
        <el-tag :type="statusTagType" size="small" class="sim-status-tag">{{ statusText }}</el-tag>
      </div>
      <el-progress
        :percentage="task.percent"
        :status="task.status === 'FAILED' ? 'exception' : (task.status === 'SUCCESS' ? 'success' : undefined)"
        :stroke-width="14"
        :text-inside="true"
      />
      <div v-if="task.status === 'FAILED'" class="sim-error">
        <i class="el-icon-warning"></i> 任务失败：{{ task.errorMsg }}
      </div>
    </el-card>

    <!-- 报告总览 -->
    <el-card v-if="report" shadow="never" class="sim-report">
      <div slot="header" class="sim-report-header">
        <span>评测报告（{{ kindLabel }}）</span>
        <el-button type="text" icon="el-icon-refresh" @click="loadProgress">刷新</el-button>
      </div>

      <el-row :gutter="12" class="sim-cards">
        <el-col :xs="12" :sm="6" v-for="c in summaryCards" :key="c.label">
          <div class="sim-card" :style="{ borderTopColor: c.color }">
            <div class="sim-card-num" :style="{ color: c.color }">{{ c.value }}</div>
            <div class="sim-card-label">{{ c.label }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 上线门禁（仅报告含 gate 块时渲染，ASR 的 CER 门槛 direction=down） -->
      <div v-if="report.gate" class="sim-gate">
        <div class="sim-gate-head">
          <span class="sim-gate-title">上线门禁</span>
          <el-tag :type="report.gate.passed ? 'success' : 'danger'" size="small">
            {{ report.gate.passed ? '门禁通过' : '门禁未通过' }}
          </el-tag>
        </div>
        <div v-for="(g, i) in report.gate.items" :key="i" class="sim-gate-item">
          <el-tag
            :type="g.status === 'skipped' ? 'info' : (g.passed ? 'success' : 'danger')"
            size="mini"
          >{{ g.status === 'skipped' ? '跳过' : (g.passed ? '通过' : '未达') }}</el-tag>
          <span class="sim-gate-name">{{ g.name }}（{{ g.stage }}）</span>
          <template v-if="g.status === 'skipped'">
            <span class="sim-gate-meta">原因：{{ g.reason || '—' }}</span>
          </template>
          <template v-else>
            <span class="sim-gate-meta">
              实际 {{ fmtRate(g.actualRate) }} / 门槛 {{ fmtRate(g.thresholdRate) }}
              （{{ g.direction === 'down' ? '≤ 门槛，越低越好' : '≥ 门槛' }}）
            </span>
          </template>
        </div>
      </div>

      <!-- Copilot：按 case / check 展开 -->
      <el-table
        v-if="kind === 'copilot'"
        :data="report.details"
        size="small"
        border
        class="sim-table"
      >
        <el-table-column label="用例" prop="caseId" width="100" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="检查点" width="110" align="center">
          <template slot-scope="s">
            {{ s.row.passedChecks }}/{{ s.row.totalChecks }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template slot-scope="s">
            <el-tag :type="s.row.passed ? 'success' : 'danger'" size="mini">
              {{ s.row.passed ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检查点明细" min-width="320">
          <template slot-scope="s">
            <div v-for="(chk, i) in s.row.checks" :key="i" class="sim-check">
              <i :class="chk.passed ? 'el-icon-success sim-ok' : 'el-icon-error sim-fail'"></i>
              <span class="sim-check-name">{{ chk.check }}</span>
              <span v-if="chk.mode" class="sim-check-mode">[{{ modeText(chk.mode) }} {{ fmtScore(chk.score) }}]</span>
              <span v-if="chk.matched && chk.matched.length" class="sim-check-meta">命中：{{ chk.matched.join('、') }}</span>
              <span v-if="chk.forbiddenHit && chk.forbiddenHit.length" class="sim-check-meta sim-fail-text">
                违禁：{{ chk.forbiddenHit.join('、') }}
              </span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- Robot：按 case / turn 展开 -->
      <el-table
        v-else-if="kind === 'robot'"
        :data="robotCaseRows"
        size="small"
        border
        class="sim-table"
      >
        <el-table-column label="用例" prop="caseId" width="100" align="center" />
        <el-table-column label="名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="轮次" width="90" align="center">
          <template slot-scope="s">{{ turnSummary(s.row) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template slot-scope="s">
            <el-tag :type="s.row.passed ? 'success' : 'danger'" size="mini">
              {{ s.row.passed ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="轮次明细" min-width="360">
          <template slot-scope="s">
            <div v-for="(t, i) in s.row.turns" :key="i" class="sim-turn">
              <div class="sim-turn-line">
                <i :class="t.passed ? 'el-icon-success sim-ok' : 'el-icon-error sim-fail'"></i>
                <span class="sim-turn-user">问：{{ t.user }}</span>
                <span class="sim-check-mode">[{{ modeText(t.mode) }} {{ fmtScore(t.score) }}]</span>
                <span v-if="t.degradedReason" class="sim-check-meta sim-fail-text">降级：{{ t.degradedReason }}</span>
              </div>
              <div class="sim-turn-answer">答：{{ t.answer }}</div>
              <div v-if="t.missing && t.missing.length" class="sim-check-meta sim-fail-text">
                缺失：{{ t.missing.join('、') }}
              </div>
              <div v-if="t.matched && t.matched.length" class="sim-check-meta">命中：{{ t.matched.join('、') }}</div>
              <div v-if="t.forbiddenHit && t.forbiddenHit.length" class="sim-check-meta sim-fail-text">
                违禁：{{ t.forbiddenHit.join('、') }}
              </div>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- ASR：按样本展开（字错率 CER，越低越好；skipped 样本展示原因） -->
      <el-table
        v-else
        :data="asrSampleRows"
        size="small"
        border
        class="sim-table"
      >
        <el-table-column label="样本" prop="sampleId" width="90" align="center" />
        <el-table-column label="名称" prop="name" min-width="120" show-overflow-tooltip />
        <el-table-column label="字错率 CER" width="110" align="center">
          <template slot-scope="s">
            <span v-if="!s.row.skipped" :class="s.row.passed ? 'sim-ok' : 'sim-fail-text'">
              {{ fmtRate(s.row.cer) }}
            </span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="编辑距离/参考长度" width="140" align="center">
          <template slot-scope="s">
            <span v-if="!s.row.skipped">{{ s.row.editDistance }}/{{ s.row.refLength }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="100" align="center">
          <template slot-scope="s">
            <el-tag v-if="s.row.skipped" type="info" size="mini">跳过</el-tag>
            <el-tag v-else :type="s.row.passed ? 'success' : 'danger'" size="mini">
              {{ s.row.passed ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金标准 / 识别文本" min-width="320">
          <template slot-scope="s">
            <div v-if="s.row.skipped" class="sim-check-meta sim-fail-text">原因：{{ s.row.reason || '—' }}</div>
            <div v-else>
              <div class="sim-asr-line"><span class="sim-turn-user">金标准：</span>{{ s.row.referenceText }}</div>
              <div class="sim-asr-line"><span class="sim-turn-user">识别：</span>{{ s.row.hypothesisText }}</div>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import {
  submitCopilotSimulation,
  getCopilotSimulationProgress,
  submitRobotSimulation,
  getRobotSimulationProgress,
  submitAsrSimulation,
  getAsrSimulationProgress
} from '@/api/lawyers/simulation'
import { createLongTask } from '@/utils/longTask'

// 三类评测的提交/进度接口与展示名映射
const SUBMITTERS = {
  copilot: submitCopilotSimulation,
  robot: submitRobotSimulation,
  asr: submitAsrSimulation
}
const PROGRESS_GETTERS = {
  copilot: getCopilotSimulationProgress,
  robot: getRobotSimulationProgress,
  asr: getAsrSimulationProgress
}
const KIND_LABELS = {
  copilot: '坐席辅助',
  robot: '语音机器人',
  asr: 'ASR 转写质量'
}

export default {
  name: 'Simulation',
  data() {
    return {
      kind: 'copilot',
      casesJson: '',
      submitting: false,
      task: null,
      report: null
    }
  },
  computed: {
    kindLabel() {
      return KIND_LABELS[this.kind] || this.kind
    },
    statusTagType() {
      if (this.task.status === 'SUCCESS') return 'success'
      if (this.task.status === 'FAILED') return 'danger'
      return 'warning'
    },
    statusText() {
      return { RUNNING: '执行中', SUCCESS: '已完成', FAILED: '失败' }[this.task.status] || this.task.status
    },
    summaryCards() {
      const r = this.report
      if (this.kind === 'asr') {
        return [
          { label: '样本通过率', value: (r.samplePassRate || 0) + '%', color: '#409EFF' },
          { label: '平均字错率 CER', value: this.fmtRate(r.avgCerRate), color: '#F56C6C' },
          { label: '样本通过', value: (r.passedSamples || 0) + '/' + (r.totalSamples || 0), color: '#67C23A' },
          { label: '跳过样本', value: r.skippedSamples || 0, color: '#909399' }
        ]
      }
      if (this.kind === 'copilot') {
        return [
          { label: '用例通过率', value: r.casePassRate + '%', color: '#409EFF' },
          { label: '检查点通过率', value: r.checkPassRate + '%', color: '#67C23A' },
          { label: '用例通过', value: r.passedCases + '/' + r.totalCases, color: '#E6A23C' },
          { label: '检查点通过', value: r.passedChecks + '/' + r.totalChecks, color: '#909399' }
        ]
      }
      const turnRate = r.totalTurns
        ? Math.round(r.passedTurns * 1000 / r.totalTurns) / 10
        : 0
      return [
        { label: '用例通过率', value: r.casePassRate + '%', color: '#409EFF' },
        { label: '轮次通过率', value: turnRate + '%', color: '#67C23A' },
        { label: '用例通过', value: r.passedCases + '/' + r.totalCases, color: '#E6A23C' },
        { label: '轮次通过', value: r.passedTurns + '/' + r.totalTurns, color: '#909399' }
      ]
    },
    robotCaseRows() {
      return this.report ? this.report.details : []
    },
    asrSampleRows() {
      return this.report ? this.report.details : []
    }
  },
  beforeDestroy() {
    this.stopPolling()
  },
  methods: {
    handleKindChange() {
      this.task = null
      this.report = null
      this.stopPolling()
    },
    handleSubmit() {
      let payload = this.casesJson ? this.casesJson.trim() : ''
      if (payload) {
        try {
          JSON.parse(payload)
        } catch (e) {
          this.$modal.msgError('用例集不是合法 JSON：' + e.message)
          return
        }
      } else {
        payload = ''
      }
      this.submitting = true
      this.report = null
      this.task = null
      const submit = SUBMITTERS[this.kind] || submitCopilotSimulation
      submit(payload).then(res => {
        this.task = { taskId: res.taskId, status: 'RUNNING', percent: 0 }
        this.$modal.notifyTaskStart('评测任务已提交', res.msg || '后台执行中，完成后将通知你')
        this.startPolling()
      }).finally(() => {
        this.submitting = false
      })
    },
    startPolling() {
      this.stopPolling()
      // U8：统一长任务封装——复用 P6 轮询（不可见暂停/在途防重入）+ 终态只回调一次
      const get = PROGRESS_GETTERS[this.kind] || getCopilotSimulationProgress
      const taskId = this.task.taskId
      const label = this.kindLabel
      this.longTask = createLongTask({
        interval: 2000,
        fetchProgress: () => get(taskId).then(res => res.data || {}),
        onUpdate: d => {
          this.task = d
          if (d.status === 'SUCCESS') {
            this.report = d.report
          }
        },
        onDone: d => {
          const r = d.report || {}
          const summary = this.kind === 'asr'
            ? `平均字错率 ${this.fmtRate(r.avgCerRate)}（通过 ${r.passedSamples || 0}/${r.totalSamples || 0}）`
            : `用例通过率 ${r.casePassRate || 0}%（${r.passedCases || 0}/${r.totalCases || 0}）`
          this.$modal.notifyTaskDone(`${label}评测完成`, summary)
        },
        onFail: d => {
          this.$modal.notifyTaskFail(`${label}评测失败`, d.errorMsg || '任务执行失败，请稍后重试')
        }
      })
      this.longTask.start()
    },
    stopPolling() {
      if (this.longTask) {
        this.longTask.stop()
        this.longTask = null
      }
    },
    // 报告页“刷新”：手动拉取一次最新进度/报告（不触发终态通知）
    loadProgress() {
      if (!this.task || !this.task.taskId) return
      const get = PROGRESS_GETTERS[this.kind] || getCopilotSimulationProgress
      return get(this.task.taskId).then(res => {
        this.task = res.data || {}
        if (this.task.status === 'SUCCESS') {
          this.report = this.task.report
        }
      })
    },
    // 0~1 比率格式化为百分比（CER / 门禁 actualRate 等），null/undefined 安全
    fmtRate(rate) {
      if (rate === undefined || rate === null) return '—'
      return (Number(rate) * 100).toFixed(1) + '%'
    },
    turnSummary(row) {
      const total = row.turns ? row.turns.length : 0
      const passed = row.turns ? row.turns.filter(t => t.passed).length : 0
      return passed + '/' + total
    },
    modeText(mode) {
      return { keyword: '关键词', semantic: '语义' }[mode] || mode
    },
    fmtScore(score) {
      return score === undefined || score === null ? '' : Number(score).toFixed(2)
    }
  }
}
</script>

<style lang="scss" scoped>
.sim-page { padding: 0; }

.sim-filter { margin-bottom: 10px; }

.sim-progress { margin-bottom: 10px; }
.sim-progress-line { margin-bottom: 6px; }
.sim-task-id { color: #606266; font-size: 13px; margin-right: 10px; }
.sim-error { color: #f56c6c; font-size: 13px; margin-top: 6px; }

.sim-report-header { display: flex; justify-content: space-between; align-items: center; }

.sim-cards { margin-bottom: 12px; }
.sim-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-top: 3px solid #409eff;
  border-radius: 4px;
  padding: 14px 10px;
  text-align: center;
}
.sim-card-num { font-size: 24px; font-weight: 600; line-height: 1.2; }
.sim-card-label { color: #909399; font-size: 13px; margin-top: 4px; }

.sim-table { margin-top: 4px; }

.sim-gate {
  border: 1px solid #ebeef5;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 12px;
  background: #f8fafc;
}
.sim-gate-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.sim-gate-title { font-weight: 600; color: #303133; }
.sim-gate-item { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 12px; padding: 3px 0; }
.sim-gate-name { color: #303133; font-weight: 500; }
.sim-gate-meta { color: #909399; }
.sim-asr-line { font-size: 12px; line-height: 1.6; color: #606266; }

.sim-check, .sim-turn { padding: 2px 0; font-size: 12px; line-height: 1.6; }
.sim-turn { margin-bottom: 6px; }
.sim-turn-line { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.sim-turn-answer { color: #606266; padding-left: 20px; }
.sim-check-name { margin-right: 4px; }
.sim-check-mode { color: #909399; margin-right: 4px; }
.sim-check-meta { color: #909399; margin-right: 8px; }
.sim-ok { color: #67c23a; margin-right: 4px; }
.sim-fail { color: #f56c6c; margin-right: 4px; }
.sim-fail-text { color: #f56c6c; }
.sim-turn-user { font-weight: 500; }
</style>
