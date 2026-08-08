<template>
  <div class="ai-assist-panel">
    <el-card shadow="never" class="ai-card">
      <div slot="header" class="ai-card-header">
        <span class="ai-title"><span class="ai-badge">AI</span> 律师实时辅助</span>
        <el-tag size="mini" :type="statusType" effect="dark">{{ statusText }}</el-tag>
      </div>

      <!-- 未关联人工通话：独立提示，不干扰人工链路 -->
      <div v-if="!recordId" class="ai-empty">
        <i class="el-icon-cpu"></i>
        <p>AI 辅助待命。人工坐席接听后自动激活，不影响接听流程。</p>
      </div>

      <template v-else>
        <div v-if="session" class="ai-body">
          <!-- 意图识别 -->
          <div class="ai-section">
            <div class="ai-section-title"><i class="el-icon-discover"></i> 识别意图</div>
            <el-tag type="success" effect="plain" size="small">{{ session.intentCategory || '分析中…' }}</el-tag>
          </div>

          <!-- 推荐法条 -->
          <div class="ai-section">
            <div class="ai-section-title"><i class="el-icon-notebook-2"></i> 推荐法条</div>
            <div v-if="recommendLaws.length === 0" class="ai-hint">暂无匹配法条，可点击「智能分析」刷新</div>
            <div v-for="(law, idx) in recommendLaws" :key="idx" class="ai-law-item">
              <div class="ai-law-title">{{ law.title }}</div>
            </div>
          </div>

          <!-- 话术建议 -->
          <div class="ai-section">
            <div class="ai-section-title"><i class="el-icon-chat-line-square"></i> 话术建议</div>
            <ul class="ai-script-list">
              <li v-for="(s, idx) in recommendScripts" :key="idx">{{ s }}</li>
            </ul>
          </div>

          <!-- 通话小结 -->
          <div class="ai-section" v-if="session.callSummary">
            <div class="ai-section-title"><i class="el-icon-document"></i> 自动小结</div>
            <pre class="ai-summary">{{ session.callSummary }}</pre>
          </div>
        </div>

        <div v-else class="ai-empty">
          <i class="el-icon-loading"></i>
          <p>AI 辅助会话初始化中…</p>
        </div>

        <!-- 独立操作按钮：仅驱动 AI 辅助会话自身状态机 -->
        <div class="ai-actions">
          <el-button type="primary" size="small" icon="el-icon-magic-stick"
                     :disabled="!session || analyzing"
                     @click="handleAnalyze">智能分析</el-button>
          <el-button type="success" size="small" icon="el-icon-finished"
                     :disabled="!session || summarizing"
                     @click="handleSummarize">生成小结</el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script>
import { getAiAssistByRecord, analyzeAiAssist, summarizeAiAssist } from '@/api/lawyers/aiAssist'

export default {
  name: 'AiAssistPanel',
  // recordId 为人工通话记录ID，是人工链路与AI辅助链路之间唯一的关联点
  props: {
    recordId: {
      type: [Number, String],
      default: null
    }
  },
  data() {
    return {
      session: null,
      analyzing: false,
      summarizing: false
    }
  },
  computed: {
    statusText() {
      if (!this.session) return '待命'
      const map = { '0': '初始化', '1': '分析中', '2': '推荐中', '3': '已小结', '4': '已结束', '9': '异常' }
      return map[this.session.sessionStatus] || '未知'
    },
    statusType() {
      if (!this.session) return 'info'
      const map = { '0': 'info', '1': 'warning', '2': 'primary', '3': 'success', '4': 'info', '9': 'danger' }
      return map[this.session.sessionStatus] || 'info'
    },
    recommendLaws() {
      if (!this.session || !this.session.recommendLaws) return []
      try { return JSON.parse(this.session.recommendLaws) } catch (e) { return [] }
    },
    recommendScripts() {
      if (!this.session || !this.session.recommendScripts) return []
      try { return JSON.parse(this.session.recommendScripts) } catch (e) { return [] }
    }
  },
  watch: {
    // 人工接听建立通话（recordId 变化）后，独立加载 AI 辅助会话，不干预人工状态
    recordId: {
      immediate: true,
      handler(val) {
        if (val) this.loadSession(val)
      }
    }
  },
  methods: {
    // 独立查询：按人工记录ID拉取关联的AI辅助会话
    loadSession(recordId) {
      getAiAssistByRecord(recordId).then(res => {
        this.session = res.data || res.code === 200 ? res.data : null
        if (res && res.data) this.session = res.data
      }).catch(() => { this.session = null })
    },
    // 独立处理函数：触发AI分析+推荐
    handleAnalyze() {
      if (!this.session) return
      this.analyzing = true
      analyzeAiAssist({ sessionId: this.session.sessionId, callSummary: '' })
        .then(res => {
          if (res && res.data) this.session = res.data
          this.$message.success('AI 分析完成')
        })
        .catch(() => {})
        .finally(() => { this.analyzing = false })
    },
    // 独立处理函数：生成小结+结束
    handleSummarize() {
      if (!this.session) return
      this.summarizing = true
      summarizeAiAssist({ sessionId: this.session.sessionId, callSummary: '' })
        .then(res => {
          if (res && res.data) this.session = res.data
          this.$message.success('AI 小结已生成')
        })
        .catch(() => {})
        .finally(() => { this.summarizing = false })
    }
  }
}
</script>

<style scoped>
.ai-assist-panel { margin-top: 16px; }
.ai-card-header { display: flex; align-items: center; justify-content: space-between; }
.ai-title { font-weight: 600; }
.ai-badge {
  display: inline-block; background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff; font-size: 11px; padding: 1px 6px; border-radius: 4px; margin-right: 6px;
}
.ai-empty { text-align: center; color: #909399; padding: 24px 0; }
.ai-empty i { font-size: 28px; margin-bottom: 8px; }
.ai-section { margin-bottom: 14px; }
.ai-section-title { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.ai-law-item { background: #f5f7fa; border-left: 3px solid #409eff; padding: 6px 10px; margin-bottom: 6px; border-radius: 0 4px 4px 0; font-size: 13px; }
.ai-hint { color: #c0c4cc; font-size: 12px; }
.ai-script-list { margin: 0; padding-left: 18px; color: #606266; font-size: 13px; line-height: 1.8; }
.ai-summary { background: #f0f9eb; border: 1px solid #e1f3d8; padding: 10px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; color: #67c23a; }
.ai-actions { display: flex; gap: 10px; margin-top: 12px; }
</style>
