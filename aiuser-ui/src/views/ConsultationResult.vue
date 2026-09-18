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
        
        <!-- 评价区域 -->
        <el-row :gutter="20">
          <el-col :span="24">
            <div class="evaluation-section">
              <h3>满意度评价</h3>
              <div class="rating-container">
                <span>请对本次咨询结果进行评价:</span>
                <el-rate 
                  v-model="rating" 
                  :colors="colors"
                  show-text>
                </el-rate>
              </div>
              
              <div class="feedback-container">
                <el-input
                  type="textarea"
                  :rows="3"
                  placeholder="请输入您的反馈意见（可选）"
                  v-model="feedback">
                </el-input>
              </div>
              
              <div class="submit-feedback">
                <el-button type="primary" @click="submitEvaluation" :loading="submitting">提交评价</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
      
      <div v-else-if="!loading" class="no-result">
        <el-empty description="未找到咨询结果"></el-empty>
        <el-button type="primary" @click="goToSubmit">提交新咨询</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
import { announce } from '@/utils/a11y'

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
      colors: ['#99A9BF', '#F7BA2A', '#FF9900']
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
        })
        .catch(error => {
          this.$message.error('获取咨询结果失败: ' + (error.response && error.response.data && error.response.data.msg || '未知错误'))
          this.loading = false
          announce('获取咨询结果失败，请稍后重试', 'assertive')
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
  color: #909399;
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
  border-left: 4px solid #409EFF;
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

.no-result {
  text-align: center;
  padding: 40px 0;
}

.no-result .el-button {
  margin-top: 20px;
}
</style>