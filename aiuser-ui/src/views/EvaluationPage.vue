<template>
  <div class="evaluation-page">
    <el-card class="evaluation-card" v-loading="loading">
      <div slot="header" class="card-header">
        <span>咨询评价</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="goBack">返回</el-button>
      </div>
      
      <div v-if="consultationInfo">
        <!-- 咨询信息展示 -->
        <div class="consultation-info">
          <h3>咨询信息</h3>
          <el-row :gutter="20">
            <el-col :span="12">
              <div class="info-item">
                <span class="label">问题分类:</span>
                <el-tag :type="getCategoryType(consultationInfo.category)" size="small">
                  {{ getCategoryName(consultationInfo.category) }}
                </el-tag>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="info-item">
                <span class="label">咨询时间:</span>
                <span>{{ formatDate(consultationInfo.createTime) }}</span>
              </div>
            </el-col>
          </el-row>
          <div class="question-content">
            <span class="label">问题描述:</span>
            <p>{{ consultationInfo.question }}</p>
          </div>
          <div class="answer-content" v-if="consultationInfo.answer">
            <span class="label">AI解答:</span>
            <p>{{ consultationInfo.answer.substring(0, 200) }}{{ consultationInfo.answer.length > 200 ? '...' : '' }}</p>
          </div>
        </div>
        
        <el-divider></el-divider>
        
        <!-- 评价表单 -->
        <div class="evaluation-form">
          <h3>满意度评价</h3>
          
          <el-form :model="evaluationForm" :rules="rules" ref="evaluationForm" label-width="100px">
            <el-form-item label="总体评价" prop="rating">
              <div class="rating-container">
                <el-rate 
                  v-model="evaluationForm.rating" 
                  :colors="colors"
                  show-text
                  :texts="ratingTexts">
                </el-rate>
              </div>
            </el-form-item>
            
            <el-form-item label="专业度" prop="professionalism">
              <el-rate 
                v-model="evaluationForm.professionalism" 
                :colors="colors"
                show-text>
              </el-rate>
            </el-form-item>
            
            <el-form-item label="响应速度" prop="responseSpeed">
              <el-rate 
                v-model="evaluationForm.responseSpeed" 
                :colors="colors"
                show-text>
              </el-rate>
            </el-form-item>
            
            <el-form-item label="解答质量" prop="answerQuality">
              <el-rate 
                v-model="evaluationForm.answerQuality" 
                :colors="colors"
                show-text>
              </el-rate>
            </el-form-item>
            
            <el-form-item label="文字反馈" prop="feedback">
              <el-input
                type="textarea"
                :rows="4"
                placeholder="请输入您的反馈意见，您的意见对我们非常重要"
                v-model="evaluationForm.feedback"
                maxlength="500"
                show-word-limit>
              </el-input>
            </el-form-item>
            
            <el-form-item label="改进建议" prop="suggestions">
              <el-input
                type="textarea"
                :rows="3"
                placeholder="您认为我们有哪些地方可以改进？（可选）"
                v-model="evaluationForm.suggestions"
                maxlength="300"
                show-word-limit>
              </el-input>
            </el-form-item>
            
            <el-form-item>
              <el-button type="primary" @click="submitEvaluation" :loading="submitting">提交评价</el-button>
              <el-button @click="goBack">取消</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
      
      <div v-else-if="!loading" class="no-consultation">
        <el-empty description="未找到相关咨询信息"></el-empty>
        <el-button type="primary" @click="goToHistory">查看历史记录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script>
export default {
  name: 'EvaluationPage',
  data() {
    return {
      loading: true,
      consultationInfo: null,
      evaluationForm: {
        consultationId: null,
        rating: 0,
        professionalism: 0,
        responseSpeed: 0,
        answerQuality: 0,
        feedback: '',
        suggestions: ''
      },
      submitting: false,
      colors: ['#99A9BF', '#F7BA2A', '#FF9900'],
      ratingTexts: ['非常不满意', '不满意', '一般', '满意', '非常满意'],
      rules: {
        rating: [
          { required: true, message: '请选择总体评价', trigger: 'change', validator: this.validateRating }
        ],
        professionalism: [
          { required: true, message: '请评价专业度', trigger: 'change', validator: this.validateRating }
        ],
        responseSpeed: [
          { required: true, message: '请评价响应速度', trigger: 'change', validator: this.validateRating }
        ],
        answerQuality: [
          { required: true, message: '请评价解答质量', trigger: 'change', validator: this.validateRating }
        ],
        feedback: [
          { required: true, message: '请输入反馈意见', trigger: 'blur' },
          { min: 10, message: '反馈意见至少需要10个字符', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    const consultationId = this.$route.query.id
    if (consultationId) {
      this.evaluationForm.consultationId = consultationId
      this.fetchConsultationInfo(consultationId)
    } else {
      this.loading = false
    }
  },
  methods: {
    fetchConsultationInfo(id) {
      this.loading = true
      this.$http.get(`/aiuser/consultation/info/${id}`)
        .then(response => {
          this.consultationInfo = response.data
          this.loading = false
        })
        .catch(error => {
          this.$message.error('获取咨询信息失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
          this.loading = false
        })
    },
    validateRating(rule, value, callback) {
      if (value === 0) {
        callback(new Error(rule.message))
      } else {
        callback()
      }
    },
    submitEvaluation() {
      this.$refs.evaluationForm.validate((valid) => {
        if (valid) {
          this.submitting = true
          
          this.$http.post('/aiuser/consultation/evaluation', this.evaluationForm)
            .then(() => {
              this.$message.success('评价提交成功，感谢您的反馈！')
              this.submitting = false
              
              // 跳转到历史记录页面
              setTimeout(() => {
                this.$router.push('/consultation/history')
              }, 1500)
            })
            .catch(error => {
              this.$message.error('评价提交失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
              this.submitting = false
            })
        }
      })
    },
    getCategoryType(category) {
      const typeMap = {
        'marriage': 'danger',
        'labor': 'warning',
        'contract': 'primary',
        'property': 'success',
        'tort': 'info',
        'criminal': 'danger',
        'other': ''
      }
      return typeMap[category] || ''
    },
    getCategoryName(category) {
      const nameMap = {
        'marriage': '婚姻家庭',
        'labor': '劳动纠纷',
        'contract': '合同纠纷',
        'property': '房产纠纷',
        'tort': '侵权责任',
        'criminal': '刑事辩护',
        'other': '其他'
      }
      return nameMap[category] || '其他'
    },
    formatDate(dateString) {
      const date = new Date(dateString)
      return date.toLocaleString()
    },
    goBack() {
      this.$router.go(-1)
    },
    goToHistory() {
      this.$router.push('/consultation/history')
    }
  }
}
</script>

<style scoped>
.evaluation-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.evaluation-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.consultation-info {
  margin-bottom: 20px;
}

.consultation-info h3 {
  margin-bottom: 15px;
  color: #303133;
}

.info-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.label {
  font-weight: bold;
  margin-right: 10px;
  min-width: 80px;
  color: #606266;
}

.question-content, .answer-content {
  margin-top: 15px;
}

.question-content p, .answer-content p {
  margin-top: 5px;
  line-height: 1.6;
  color: #303133;
  background-color: #f8f9fa;
  padding: 10px;
  border-radius: 4px;
}

.evaluation-form {
  margin-top: 20px;
}

.evaluation-form h3 {
  margin-bottom: 20px;
  color: #303133;
}

.rating-container {
  display: flex;
  align-items: center;
}

.no-consultation {
  text-align: center;
  padding: 40px 0;
}

.no-consultation .el-button {
  margin-top: 20px;
}
</style>