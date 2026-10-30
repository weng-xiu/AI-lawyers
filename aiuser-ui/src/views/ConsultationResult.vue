<template>
  <div class="consultation-result">
    <el-card class="result-card" v-loading="loading">
      <div slot="header" class="card-header">
        <span>咨询结果</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="goBack">返回</el-button>
      </div>
      
      <div v-if="consultationResult">
        <!-- 问题信息 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <div class="question-info">
              <h3>您的问题</h3>
              <div class="category-tag">
                <el-tag :type="getCategoryType(consultationResult.category)">
                  {{ getCategoryName(consultationResult.category) }}
                </el-tag>
              </div>
              <p class="question-content">{{ consultationResult.content }}</p>
              <p class="question-time">提问时间: {{ formatDate(consultationResult.createTime) }}</p>
            </div>
          </el-col>
        </el-row>
        
        <el-divider></el-divider>
        
        <!-- AI解答 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <div class="ai-answer">
              <h3>AI法律解答</h3>
              <div class="confidence-info">
                <span>置信度: </span>
                <el-progress
                  :percentage="confidencePercent"
                  :color="getConfidenceColor(confidencePercent)"
                  :show-text="true"
                  aria-label="AI 解答置信度"
                  :format="formatConfidence">
                </el-progress>
              </div>
              <div class="answer-content">{{ consultationResult.aiAnswer }}</div>
            </div>
          </el-col>
        </el-row>
        
        <!-- 相关法条 -->
        <el-row :gutter="20" v-if="lawLines.length > 0">
          <el-col :span="24">
            <div class="related-laws">
              <h3>相关法律条文</h3>
              <el-collapse v-model="activeLawNames">
                <el-collapse-item
                  v-for="(law, index) in lawLines"
                  :key="index"
                  :title="law"
                  :name="index">
                  <div class="law-content">本条文为系统根据问题分类匹配的参考依据，具体以现行有效法律文本为准。</div>
                </el-collapse-item>
              </el-collapse>
            </div>
          </el-col>
        </el-row>

        <!-- 相关案例 -->
        <el-row :gutter="20" v-if="caseLines.length > 0">
          <el-col :span="24">
            <div class="related-cases">
              <h3>相关案例参考</h3>
              <el-card
                v-for="(caseItem, index) in caseLines"
                :key="index"
                class="case-card"
                shadow="hover">
                <div slot="header" class="case-header">
                  <span>{{ caseItem }}</span>
                </div>
                <div class="case-summary">案例为同类纠纷参考，具体个案请以司法机关裁判为准。</div>
              </el-card>
            </div>
          </el-col>
        </el-row>
        
        <el-divider></el-divider>
        
        <!-- F10 评价引导 + 材料补正：已完成咨询可评价/补充材料；已评价展示感谢 -->
        <el-row :gutter="20" v-if="consultationResult.status === 'COMPLETED'">
          <el-col :span="24">
            <div class="evaluation-section">
              <div v-if="evaluated" class="evaluated-tip" role="status">
                <i class="el-icon-circle-check" aria-hidden="true"></i>
                <span>感谢您的评价，我们会持续改进服务。</span>
              </div>
              <div v-else>
                <h3>满意度评价</h3>
                <div class="rating-container">
                  <span>请对本次咨询结果进行评价:</span>
                  <el-rate
                    v-model="rating"
                    :colors="colors"
                    aria-label="咨询结果满意度评分"
                    show-text>
                  </el-rate>
                </div>

                <div class="feedback-container">
                  <el-input
                    type="textarea"
                    :rows="3"
                    placeholder="请输入您的反馈意见（可选）"
                    aria-label="反馈意见，可选"
                    v-model="feedback">
                  </el-input>
                </div>

                <div class="submit-feedback">
                  <el-button type="primary" @click="submitEvaluation" :loading="submitting">提交评价</el-button>
                </div>
              </div>

              <div class="supplement-entry">
                <el-button type="text" icon="el-icon-paperclip" @click="openSupplement">
                  材料不全？点此补充材料
                </el-button>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- F10 常见问题推荐：匿名只读知识库，帮助用户先自助排查 -->
        <el-row :gutter="20" v-if="faqs.length > 0">
          <el-col :span="24">
            <div class="faq-section">
              <h3>相关问题推荐</h3>
              <ul class="faq-list">
                <li v-for="faq in faqs" :key="faq.knowledgeId" class="faq-item">
                  <router-link class="faq-title" :to="{ path: '/consultation/submit', query: { category: 'other', ref: 'faq' } }">
                    {{ faq.title }}
                  </router-link>
                  <p class="faq-summary">{{ faq.summary }}</p>
                </li>
              </ul>
            </div>
          </el-col>
        </el-row>
      </div>
      
      <div v-else-if="!loading" class="no-result">
        <el-empty description="未找到咨询结果"></el-empty>
        <el-button type="primary" @click="goToSubmit">提交新咨询</el-button>
      </div>
    </el-card>

    <!-- F10 补充材料弹窗：追加附件与说明，不覆盖原附件 -->
    <el-dialog
      title="补充材料"
      :visible.sync="supplementVisible"
      width="40%">
      <div class="supplement-form">
        <p class="supplement-tip">可补充证明材料的照片或文档，并说明补充内容（原材料会保留）。</p>
        <el-upload
          action=""
          :auto-upload="false"
          :on-change="handleSupplementFileChange"
          :on-remove="handleSupplementFileRemove"
          :file-list="supplementFiles"
          multiple>
          <el-button size="small" type="primary">选择文件</el-button>
          <div slot="tip" class="el-upload__tip">支持 jpg/png/pdf/doc/docx，单次最多 3 个，单个不超过 10MB</div>
        </el-upload>
        <el-input
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          aria-label="补充说明，最多500个字"
          placeholder="请说明补充的内容（可选，最多500字）"
          v-model="supplementNote">
        </el-input>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="supplementVisible = false">取消</el-button>
        <el-button type="primary" :loading="supplementSubmitting" @click="submitSupplement">提交补充</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { announce } from '@/utils/a11y'
import { isConsultationEvaluated, supplementConsultation } from '@/api/consultation'
import { getFaq } from '@/api/portal'

/** F10 补充材料限制（与后端保持一致） */
const MAX_SUPPLEMENT_FILES = 3
const MAX_SUPPLEMENT_BYTES = 10 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'pdf', 'doc', 'docx']

export default {
  name: 'ConsultationResult',
  data() {
    return {
      loading: true,
      consultationResult: null,
      activeLawNames: [],
      rating: 0,
      feedback: '',
      submitting: false,
      colors: ['#99A9BF', '#F7BA2A', '#FF9900'],
      // F10 评价引导
      evaluated: false,
      // F10 材料补正
      supplementVisible: false,
      supplementNote: '',
      supplementFiles: [],
      supplementSubmitting: false,
      // F10 常见问题推荐
      faqs: []
    }
  },
  computed: {
    // 后端置信度为 0~1 的小数，前端进度条按百分比展示
    confidencePercent() {
      const c = this.consultationResult && this.consultationResult.confidence
      if (c == null) return 0
      return Math.round(c <= 1 ? c * 100 : c)
    },
    // 后端相关法条为换行分隔字符串，按行拆分为列表
    lawLines() {
      return this.splitLines(this.consultationResult && this.consultationResult.relatedLaws)
    },
    caseLines() {
      return this.splitLines(this.consultationResult && this.consultationResult.relatedCases)
    }
  },
  created() {
    const consultationId = this.$route.query.id
    if (consultationId) {
      this.fetchConsultationResult(consultationId)
    } else {
      this.loading = false
    }
  },
  methods: {
    splitLines(text) {
      if (!text) return []
      return String(text).split(/\r?\n/).map(s => s.trim()).filter(Boolean)
    },
    fetchConsultationResult(id) {
      this.loading = true
      announce('正在获取咨询结果，请稍候', 'polite')
      this.$http.get(`/aiuser/consultation/result/${id}`)
        .then(response => {
          this.consultationResult = response.data
          this.loading = false
          const r = response.data || {}
          const lawCount = this.splitLines(r.relatedLaws).length
          announce(`咨询结果已生成，分类${this.getCategoryName(r.category)}，`
            + `置信度${r.confidence == null ? '未知' : this.confidencePercent + '%'}`
            + (lawCount > 0 ? `，相关法律条文 ${lawCount} 条` : ''), 'polite')
          if (r.status === 'COMPLETED') {
            this.loadEvaluated(r.consultationId)
          }
        })
        .catch(error => {
          this.$message.error('获取咨询结果失败: ' + (error.response && error.response.data && error.response.data.msg || '未知错误'))
          this.loading = false
          announce('获取咨询结果失败，请稍后重试', 'assertive')
        })
      this.loadFaq()
    },
    /** F10 评价引导态：已评价展示感谢，未评价展示评分入口 */
    loadEvaluated(id) {
      if (!id) return
      isConsultationEvaluated(id).then(res => {
        this.evaluated = !!res.data
      }).catch(() => {
        // 读取失败按未评价处理，不阻断结果页
      })
    },
    /** F10 常见问题推荐（匿名只读，失败静默） */
    loadFaq() {
      getFaq({ limit: 5 }).then(res => {
        this.faqs = res.data || []
      }).catch(() => {
        this.faqs = []
      })
    },
    /** F10 材料补正入口 */
    openSupplement() {
      this.supplementNote = ''
      this.supplementFiles = []
      this.supplementVisible = true
    },
    handleSupplementFileChange(file, fileList) {
      if (fileList.length > MAX_SUPPLEMENT_FILES) {
        this.$message.warning(`单次最多补充 ${MAX_SUPPLEMENT_FILES} 个材料`)
        this.supplementFiles = fileList.slice(0, MAX_SUPPLEMENT_FILES)
        return
      }
      this.supplementFiles = fileList
    },
    handleSupplementFileRemove(file, fileList) {
      this.supplementFiles = fileList
    },
    submitSupplement() {
      const files = (this.supplementFiles || [])
        .map(item => item.raw)
        .filter(Boolean)
      const note = this.supplementNote.trim()

      if (files.length === 0 && !note) {
        this.$message.warning('请上传材料或填写补充说明')
        return
      }
      for (const file of files) {
        const ext = (file.name || '').split('.').pop().toLowerCase()
        if (!ALLOWED_EXTENSIONS.includes(ext)) {
          this.$message.warning('仅支持 jpg/png/pdf/doc/docx 格式材料')
          return
        }
        if (file.size > MAX_SUPPLEMENT_BYTES) {
          this.$message.warning('单个附件不能超过 10MB')
          return
        }
      }

      const formData = new FormData()
      if (note) formData.append('note', note)
      files.forEach(file => formData.append('files', file))

      this.supplementSubmitting = true
      supplementConsultation(this.consultationResult.consultationId, formData)
        .then(response => {
          this.supplementVisible = false
          this.supplementFiles = []
          this.supplementNote = ''
          this.consultationResult = response.data || this.consultationResult
          this.$message.success('材料补充成功')
          announce('材料补充成功，工作人员会尽快查看', 'polite')
        })
        .catch(error => {
          const msg = (error.response && error.response.data && error.response.data.msg)
            || (error.message || '未知错误')
          this.$message.error('材料补充失败: ' + msg)
          announce('材料补充失败，请稍后重试', 'assertive')
        })
        .finally(() => {
          this.supplementSubmitting = false
        })
    },
    getCategoryType(category) {
      const typeMap = {
        marriage_family: 'danger',
        labor_dispute: 'warning',
        contract_dispute: 'primary',
        property_dispute: 'success',
        criminal_case: 'danger',
        intellectual_property: 'info',
        consumer_rights: 'warning',
        other: ''
      }
      return typeMap[category] || ''
    },
    getCategoryName(category) {
      const nameMap = {
        marriage_family: '婚姻家庭',
        labor_dispute: '劳动纠纷',
        contract_dispute: '合同纠纷',
        property_dispute: '财产纠纷',
        criminal_case: '刑事案件',
        intellectual_property: '知识产权',
        consumer_rights: '消费者权益',
        other: '其他'
      }
      return nameMap[category] || '其他'
    },
    getConfidenceColor(confidence) {
      if (confidence >= 80) return '#67C23A'
      if (confidence >= 60) return '#E6A23C'
      return '#F56C6C'
    },
    formatConfidence(percentage) {
      return `${percentage}%`
    },
    formatDate(dateString) {
      const date = new Date(dateString)
      return date.toLocaleString()
    },
    submitEvaluation() {
      if (this.rating === 0) {
        this.$message.warning('请先选择评分')
        announce('请先选择评分', 'assertive')
        return
      }

      this.submitting = true
      const evaluationData = {
        consultationId: this.consultationResult.consultationId,
        overallRating: this.rating,
        feedback: this.feedback
      }

      this.$http.post('/aiuser/consultation/evaluation', evaluationData)
        .then(() => {
          this.evaluated = true
          this.$message.success('评价提交成功')
          announce('评价提交成功，感谢您的反馈', 'polite')
          this.submitting = false
        })
        .catch(error => {
          this.$message.error('评价提交失败: ' + (error.response && error.response.data && error.response.data.msg || '未知错误'))
          announce('评价提交失败，请稍后重试', 'assertive')
          this.submitting = false
        })
    },
    goBack() {
      this.$router.go(-1)
    },
    goToSubmit() {
      this.$router.push('/consultation/submit')
    }
  }
}
</script>

<style scoped>
.consultation-result {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.result-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.question-info {
  margin-bottom: 20px;
}

.category-tag {
  margin: 10px 0;
}

.question-content {
  font-size: 16px;
  line-height: 1.6;
  margin: 10px 0;
  color: #303133;
}

.question-time {
  /* 无障碍：#909399 在白底仅 3.07:1，改为满足 4.5:1 的深灰蓝 */
  color: #5b6b84;
  font-size: 14px;
}

.ai-answer {
  margin-bottom: 20px;
}

.confidence-info {
  margin: 15px 0;
  display: flex;
  align-items: center;
}

.confidence-info span {
  margin-right: 10px;
  min-width: 80px;
}

.answer-content {
  font-size: 16px;
  line-height: 1.8;
  margin: 15px 0;
  padding: 15px;
  background-color: #f8f9fa;
  border-radius: 6px;
  border-left: 4px solid var(--color-brand-deep, #1A3C6E);
}

.related-laws {
  margin-bottom: 20px;
}

.law-content {
  margin-bottom: 10px;
  line-height: 1.6;
}

.law-source {
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.related-cases {
  margin-bottom: 20px;
}

.case-card {
  margin-bottom: 15px;
}

.case-header {
  font-weight: bold;
}

.case-summary {
  margin-bottom: 10px;
  line-height: 1.6;
}

.case-detail {
  text-align: right;
}

.evaluation-section {
  margin-top: 20px;
}

.rating-container {
  display: flex;
  align-items: center;
  margin: 15px 0;
}

.rating-container span {
  margin-right: 15px;
}

.feedback-container {
  margin: 15px 0;
}

.submit-feedback {
  text-align: center;
  margin-top: 15px;
}

/* F10 评价引导：已评价态 / 补充材料入口 */
.evaluated-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 6px;
  background: #f0f9eb;
  color: #3f6b2c;
  font-size: 15px;
}
.evaluated-tip i { color: #67C23A; font-size: 20px; }
.supplement-entry { margin-top: 12px; text-align: right; }

/* F10 常见问题推荐 */
.faq-section { margin-bottom: 20px; }
.faq-list { list-style: none; margin: 12px 0 0; padding: 0; }
.faq-item { padding: 12px 0; border-bottom: 1px solid #ebeef5; }
.faq-item:last-child { border-bottom: none; }
.faq-title { color: var(--color-brand-deep, #1A3C6E); font-size: 15px; font-weight: 600; text-decoration: none; }
.faq-title:hover { text-decoration: underline; }
.faq-summary { margin: 6px 0 0; color: #5b6b84; font-size: 14px; line-height: 1.7; }

/* F10 补充材料弹窗 */
.supplement-tip { margin: 0 0 14px; color: #5b6b84; font-size: 14px; line-height: 1.7; }
.supplement-form .el-upload { width: 100%; margin-bottom: 12px; }

.no-result {
  text-align: center;
  padding: 40px 0;
}

.no-result .el-button {
  margin-top: 20px;
}

/* U6 移动端：收窄留白，评分控件折行避免溢出 */
@media (max-width: 768px) {
  .consultation-result { padding: 12px; }
  .card-header { font-size: 17px; }
  .answer-content { padding: 12px; font-size: 15px; }
  .rating-container { flex-wrap: wrap; gap: 8px; }
  .rating-container span { margin-right: 0; }
}
</style>