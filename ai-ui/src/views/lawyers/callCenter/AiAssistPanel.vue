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
          <!-- F4：实时 Copilot（来自 /ws/voice 节流回合，辅助参考，不直接写工单） -->
          <div v-if="copilot && copilot.seq > 0" class="ai-section ai-copilot">
            <div class="ai-section-title"><i class="el-icon-cpu"></i> 实时案情要素</div>

            <!-- 要素标签 -->
            <div v-if="copilot.element" class="cp-block">
              <div class="cp-tags">
                <el-tag size="small" type="primary" effect="dark">
                  {{ copilot.element.disputeType || '类型待定' }}
                </el-tag>
                <el-tag size="small" :type="copilot.element.urgency === 'urgent' ? 'danger' : 'success'"
                        effect="plain">
                  {{ copilot.element.urgency === 'urgent' ? '紧急' : '一般' }}
                </el-tag>
                <el-tag v-for="(c, i) in copilot.element.claims" :key="'c'+i" size="small"
                        type="info" effect="plain">诉求：{{ c }}</el-tag>
              </div>
              <ul v-if="copilot.element.keyFacts && copilot.element.keyFacts.length" class="cp-facts">
                <li v-for="(f, i) in copilot.element.keyFacts" :key="'f'+i">{{ f }}</li>
              </ul>
              <div v-if="copilot.element.degraded" class="cp-degraded">要素暂不可用（模型未响应），以下为历史推荐</div>
              <div class="cp-actions">
                <el-button size="mini" type="success"
                           @click="sendFeedback('ELEMENT', 'element', 'ADOPT')">采纳标签</el-button>
                <el-button size="mini"
                           @click="sendFeedback('ELEMENT', 'element', 'MODIFY')">参考调整</el-button>
                <el-button size="mini" type="info"
                           @click="sendFeedback('ELEMENT', 'element', 'IGNORE')">忽略</el-button>
              </div>
            </div>

            <!-- 推荐法条（点击溯源原文） -->
            <div v-if="copilot.laws && copilot.laws.length" class="cp-block">
              <div class="cp-sub-title"><i class="el-icon-notebook-2"></i> 推荐法条</div>
              <div v-for="law in copilot.laws" :key="law.chunkId" class="cp-law">
                <a class="cp-law-link" @click="openLaw(law)">
                  {{ law.title }}<span v-if="law.lawArticle">（{{ law.lawArticle }}）</span>
                  <i class="el-icon-view cp-view-icon"></i>
                </a>
                <span class="cp-law-actions">
                  <el-button size="mini" type="text"
                             @click="sendFeedback('LAW', String(law.chunkId), 'ADOPT')">采用</el-button>
                  <el-button size="mini" type="text"
                             @click="sendFeedback('LAW', String(law.chunkId), 'IGNORE')">忽略</el-button>
                </span>
              </div>
            </div>

            <!-- 相似工单 -->
            <div v-if="copilot.tickets && copilot.tickets.length" class="cp-block">
              <div class="cp-sub-title"><i class="el-icon-tickets"></i> 相似工单（已办结）</div>
              <div v-for="ticket in copilot.tickets" :key="ticket.ticketId" class="cp-ticket">
                <div class="cp-ticket-head">
                  <el-tag size="mini" :type="ticketStatusTag(ticket.status)" effect="plain">
                    {{ ticketStatusLabel(ticket.status) }}
                  </el-tag>
                  <span class="cp-ticket-title">{{ ticket.title || ticket.ticketNo }}</span>
                </div>
                <div v-if="ticket.contentSnippet" class="cp-ticket-snippet">{{ ticket.contentSnippet }}</div>
                <span class="cp-law-actions">
                  <el-button size="mini" type="text"
                             @click="sendFeedback('TICKET', String(ticket.ticketId), 'ADOPT')">参考处理思路</el-button>
                  <el-button size="mini" type="text"
                             @click="sendFeedback('TICKET', String(ticket.ticketId), 'IGNORE')">忽略</el-button>
                </span>
              </div>
            </div>
          </div>

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

    <!-- F4：法条溯源弹窗（法规名称/条号/出处/原文；效力状态随 F7 元数据补） -->
    <el-dialog title="法条溯源" :visible.sync="lawDialogVisible" width="640px"
               append-to-body custom-class="cp-law-dialog">
      <div v-loading="lawLoading">
        <template v-if="lawDetail">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="法规名称">{{ lawDetail.title || '-' }}</el-descriptions-item>
            <el-descriptions-item label="条号">{{ lawDetail.lawArticle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出处">{{ lawDetail.source || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="cp-law-content">{{ lawDetail.chunkContent }}</div>
        </template>
      </div>
      <span slot="footer">
        <el-button @click="lawDialogVisible = false">关 闭</el-button>
        <el-button type="primary"
                   @click="adoptOpenedLaw">采用并关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getAiAssistByRecord, analyzeAiAssist, summarizeAiAssist } from '@/api/lawyers/aiAssist'
import { copilotFeedback, getCopilotChunk } from '@/api/lawyers/copilot'

export default {
  name: 'AiAssistPanel',
  // recordId 为人工通话记录ID，是人工链路与AI辅助链路之间唯一的关联点
  props: {
    recordId: {
      type: [Number, String],
      default: null
    },
    // P3-A6：VoiceCaption 实时字幕累计文本，分析/小结时作为通话内容上送
    liveText: {
      type: String,
      default: ''
    },
    // F4：实时 Copilot 数据 {seq, element, laws[], tickets[]}（callPopup 桥接 WS 帧）
    copilot: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      session: null,
      analyzing: false,
      summarizing: false,
      // F4：法条溯源弹窗
      lawDialogVisible: false,
      lawLoading: false,
      lawDetail: null,
      openedChunkId: null
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
        // F4：切换通话复位溯源弹窗
        this.lawDialogVisible = false
        this.lawDetail = null
        this.openedChunkId = null
      }
    }
  },
  methods: {
    // 独立查询：按人工记录ID拉取关联的AI辅助会话
    loadSession(recordId) {
      getAiAssistByRecord(recordId).then(res => {
        this.session = (res.data || (res.code === 200 ? res.data : null))
        if (res && res.data) this.session = res.data
      }).catch(() => { this.session = null })
    },
    // 独立处理函数：触发AI分析+推荐
    handleAnalyze() {
      if (!this.session) return
      this.analyzing = true
      analyzeAiAssist({ sessionId: this.session.sessionId, callSummary: this.liveText || '' })
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
      summarizeAiAssist({ sessionId: this.session.sessionId, callSummary: this.liveText || '' })
        .then(res => {
          if (res && res.data) this.session = res.data
          this.$message.success('AI 小结已生成')
        })
        .catch(() => {})
        .finally(() => { this.summarizing = false })
    },

    /* ================= F4：采纳埋点 / 法条溯源 ================= */

    /**
     * 发送建议行为埋点（best-effort，不打扰坐席操作）。
     * @param type ELEMENT/LAW/TICKET
     * @param ref  chunkId/ticketId/element
     * @param action ADOPT/MODIFY/IGNORE
     */
    sendFeedback(type, ref, action) {
      copilotFeedback({
        recordId: this.recordId ? Number(this.recordId) : null,
        suggestionType: type,
        suggestionRef: ref,
        action: action
      }).catch(() => {})
    },

    /** 打开法条溯源弹窗：按 chunkId 拉原文 */
    openLaw(law) {
      if (!law || !law.chunkId) {
        this.$message.warning('该法条暂无溯源内容')
        return
      }
      this.openedChunkId = law.chunkId
      this.lawDetail = null
      this.lawDialogVisible = true
      this.lawLoading = true
      getCopilotChunk(law.chunkId).then(res => {
        this.lawDetail = res.data || null
      }).catch(() => {
        this.lawDetail = null
      }).finally(() => { this.lawLoading = false })
    },

    /** 弹窗内"采用并关闭"：记 LAW 采纳后关闭 */
    adoptOpenedLaw() {
      if (this.openedChunkId) {
        this.sendFeedback('LAW', String(this.openedChunkId), 'ADOPT')
      }
      this.lawDialogVisible = false
    },

    ticketStatusLabel(s) {
      return { '0': '待处理', '1': '处理中', '2': '已完成', '3': '已归档' }[s] || '待处理'
    },
    ticketStatusTag(s) {
      return { '0': 'info', '1': 'warning', '2': 'success', '3': '' }[s] || 'info'
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
.ai-empty { text-align: center; color: #8C8C8C; padding: 24px 0; }
.ai-empty i { font-size: 28px; margin-bottom: 8px; }
.ai-section { margin-bottom: 14px; }
.ai-section-title { font-size: 13px; font-weight: 600; color: #1F2A3A; margin-bottom: 8px; }
.ai-law-item { background: #f5f7fa; border-left: 3px solid #1A3C6E; padding: 6px 10px; margin-bottom: 6px; border-radius: 0 4px 4px 0; font-size: 13px; }
.ai-hint { color: #B0BCCA; font-size: 12px; }
.ai-script-list { margin: 0; padding-left: 18px; color: #5A6A7E; font-size: 13px; line-height: 1.8; }
.ai-summary { background: #EDF5EF; border: 1px solid #D6E9DC; padding: 10px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; color: #2B8C6E; }
.ai-actions { display: flex; gap: 10px; margin-top: 12px; }

/* F4：实时 Copilot */
.ai-copilot { background: #f8faff; border: 1px solid #e4ecfb; border-radius: 6px; padding: 10px; }
.cp-block { margin-bottom: 10px; }
.cp-block:last-child { margin-bottom: 0; }
.cp-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.cp-facts { margin: 0 0 6px; padding-left: 18px; color: #5A6A7E; font-size: 12px; line-height: 1.7; }
.cp-degraded { color: #C0504D; font-size: 12px; margin-bottom: 6px; }
.cp-sub-title { font-size: 12px; font-weight: 600; color: #1F2A3A; margin-bottom: 6px; }
.cp-law { display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-left: 3px solid #1A3C6E; padding: 4px 8px; margin-bottom: 5px; border-radius: 0 4px 4px 0; }
.cp-law-link { color: #1A3C6E; font-size: 12px; cursor: pointer; }
.cp-law-link:hover { text-decoration: underline; }
.cp-view-icon { font-size: 12px; margin-left: 4px; }
.cp-law-actions { white-space: nowrap; margin-left: 8px; }
.cp-ticket { background: #fff; border: 1px solid #e8eef7; border-radius: 4px; padding: 6px 8px; margin-bottom: 6px; }
.cp-ticket-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.cp-ticket-title { font-size: 12px; font-weight: 600; color: #1F2A3A; }
.cp-ticket-snippet { color: #5A6A7E; font-size: 12px; line-height: 1.6; margin-bottom: 2px; }
.cp-law-content { margin-top: 12px; max-height: 320px; overflow-y: auto;
  background: #f7f9fc; border-radius: 4px; padding: 10px; font-size: 13px; line-height: 1.8; color: #33415c; white-space: pre-wrap; }
</style>
