<template>
  <div class="sim-page">
    <!-- 提交栏 -->
    <el-card shadow="never" class="sim-filter">
      <el-form :inline="true" size="small" @submit.native.prevent>
        <el-form-item label="评测类型">
          <el-radio-group v-model="kind" @change="handleKindChange">
            <el-radio-button label="copilot">坐席辅助 Copilot</el-radio-button>
            <el-radio-button label="robot">语音机器人</el-radio-button>
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
        <span>评测报告（{{ kind === 'copilot' ? '坐席辅助' : '语音机器人' }}）</span>
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
        v-else
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
    </el-card>
  </div>
</template>

<script>
import {
  submitCopilotSimulation,
  getCopilotSimulationProgress,
  submitRobotSimulation,
  getRobotSimulationProgress
} from '@/api/lawyers/simulation'

export default {
  name: 'Simulation',
  data() {
    return {
      kind: 'copilot',
      casesJson: '',
      submitting: false,
      task: null,
      report: null,
      pollTimer: null
    }
  },
  computed: {
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
          this.$message.error('用例集不是合法 JSON：' + e.message)
          return
        }
      } else {
        payload = ''
      }
      this.submitting = true
      this.report = null
      this.task = null
      const submit = this.kind === 'copilot' ? submitCopilotSimulation : submitRobotSimulation
      submit(payload).then(res => {
        this.task = { taskId: res.taskId, status: 'RUNNING', percent: 0 }
        this.$message.success(res.msg || '评测任务已提交')
        this.startPolling()
      }).finally(() => {
        this.submitting = false
      })
    },
    startPolling() {
      this.stopPolling()
      this.pollTimer = setInterval(this.loadProgress, 2000)
    },
    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },
    loadProgress() {
      if (!this.task || !this.task.taskId) {
        return
      }
      const get = this.kind === 'copilot'
        ? getCopilotSimulationProgress
        : getRobotSimulationProgress
      get(this.task.taskId).then(res => {
        this.task = res.data
        if (this.task.status === 'SUCCESS') {
          this.report = this.task.report
          this.stopPolling()
        } else if (this.task.status === 'FAILED') {
          this.stopPolling()
        }
      })
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
