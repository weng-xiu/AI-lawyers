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
            <p>{{ consultationInfo.content }}</p>
          </div>
          <div class="answer-content" v-if="consultationInfo.aiAnswer">
            <span class="label">AI解答:</span>
            <p>{{ consultationInfo.aiAnswer.length > 200 ? consultationInfo.aiAnswer.substring(0, 200) + '...' : consultationInfo.aiAnswer }}</p>
          </div>
        </div>
        
        <el-divider></el-divider>
        
        <!-- 评价表单 -->
        <div class="evaluation-form">
          <h3>满意度评价</h3>
          
          <el-form :model="evaluationForm" :rules="rules" ref="evaluationForm" label-width="100px">
            <el-form-item label="总体评价" prop="overallRating">
              <div class="rating-container">
                <el-rate 
                  v-model="evaluationForm.overallRating" 
                  :colors="colors"
                  show-text
                  :texts="ratingTexts">
                </el-rate>
              </div>
            </el-form-item>
            
            <el-form-item label="文字反馈" prop="feedback">
              <el-input
                type="textarea"
                :rows="4"
                placeholder="请输入您的反馈意见（选填），您的意见对我们非常重要"
                v-model="evaluationForm.feedback"
                maxlength="500"
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
        overallRating: 0,
        feedback: ''
      },
      submitting: false,
      colors: ['#99A9BF', '#F7BA2A', '#FF9900'],
      ratingTexts: ['非常不满意', '不满意', '一般', '满意', '非常满意'],
      rules: {
        overallRating: [
          { required: true, message: '请选择总体评价', trigger: 'change', validator: this.validateRating }
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
        property_dispute: '财产权益',
        criminal_case: '刑事案件',
        intellectual_property: '知识产权',
        consumer_rights: '消费维权',
        other: '其他'
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