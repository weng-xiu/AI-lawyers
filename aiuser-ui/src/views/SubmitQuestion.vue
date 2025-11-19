<template>
  <div class="submit-question">
    <el-card class="question-card">
      <div slot="header" class="card-header">
        <span>提交法律咨询</span>
      </div>
      
      <el-form :model="questionForm" :rules="rules" ref="questionForm" label-width="100px">
        <el-form-item label="问题分类" prop="category">
          <el-select v-model="questionForm.category" placeholder="请选择问题分类" style="width: 100%">
            <el-option label="婚姻家庭" value="marriage"></el-option>
            <el-option label="劳动纠纷" value="labor"></el-option>
            <el-option label="合同纠纷" value="contract"></el-option>
            <el-option label="房产纠纷" value="property"></el-option>
            <el-option label="侵权责任" value="tort"></el-option>
            <el-option label="刑事辩护" value="criminal"></el-option>
            <el-option label="其他" value="other"></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="问题描述" prop="content">
          <el-input 
            type="textarea" 
            v-model="questionForm.content" 
            placeholder="请详细描述您的法律问题"
            :rows="8"
            maxlength="1000"
            show-word-limit>
          </el-input>
        </el-form-item>
        
        <el-form-item label="相关文档">
          <el-upload
            class="upload-demo"
            action=""
            :on-change="handleFileChange"
            :file-list="fileList"
            :auto-upload="false"
            multiple>
            <el-button size="small" type="primary">点击上传</el-button>
            <div slot="tip" class="el-upload__tip">支持jpg/png/pdf/doc/docx文件，单个文件不超过10MB</div>
          </el-upload>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitQuestion" :loading="submitting">提交咨询</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 处理中状态对话框 -->
    <el-dialog
      title="处理中"
      :visible.sync="processingDialogVisible"
      width="30%"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false">
      <div style="text-align: center;">
        <el-progress :percentage="processingPercentage" :status="processingStatus || undefined"></el-progress>
        <p style="margin-top: 15px;">{{ processingMessage }}</p>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'SubmitQuestion',
  data() {
    return {
      questionForm: {
        category: '',
        content: ''
      },
      fileList: [],
      submitting: false,
      processingDialogVisible: false,
      processingPercentage: 0,
      processingStatus: '',
      processingMessage: '正在分析您的问题...',
      rules: {
        category: [
          { required: true, message: '请选择问题分类', trigger: 'change' }
        ],
        content: [
          { required: true, message: '请输入问题描述', trigger: 'blur' },
          { min: 10, message: '问题描述至少需要10个字符', trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    handleFileChange(file, fileList) {
      this.fileList = fileList
    },
    submitQuestion() {
      this.$refs.questionForm.validate((valid) => {
        if (valid) {
          this.submitting = true
          this.processingDialogVisible = true
          this.processingPercentage = 0
          this.processingStatus = ''
          this.processingMessage = '正在分析您的问题...'
          
          // 模拟处理进度
          this.simulateProcessing()
          
          // 准备表单数据
          const formData = new FormData()
          formData.append('category', this.questionForm.category)
          formData.append('content', this.questionForm.content)
          
          // 添加文件
          this.fileList.forEach(file => {
            if (file.raw) {
              formData.append('files', file.raw)
            }
          })
          
          // 提交到后端
          this.$http.post('/aiuser/consultation/submit', formData, {
            headers: {
              'Content-Type': 'multipart/form-data'
            }
          }).then(response => {
            this.processingPercentage = 100
            this.processingStatus = 'success'
            this.processingMessage = '问题提交成功！'
            
            setTimeout(() => {
              this.processingDialogVisible = false
              this.submitting = false
              
              // 跳转到结果页面
              this.$router.push({
                path: '/consultation/result',
                query: { id: response.data.consultationId }
              })
            }, 1500)
          }).catch(error => {
            this.processingStatus = 'exception'
            this.processingMessage = '提交失败，请重试'
            this.submitting = false
            
            setTimeout(() => {
              this.processingDialogVisible = false
            }, 2000)
            
            this.$message.error('提交失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
          })
        }
      })
    },
    simulateProcessing() {
      const interval = setInterval(() => {
        if (this.processingPercentage < 90) {
          this.processingPercentage += Math.floor(Math.random() * 10) + 5
          
          if (this.processingPercentage < 30) {
            this.processingMessage = '正在分析您的问题...'
          } else if (this.processingPercentage < 60) {
            this.processingMessage = '正在匹配相关法律条文...'
          } else if (this.processingPercentage < 90) {
            this.processingMessage = '正在生成法律建议...'
          }
        } else {
          clearInterval(interval)
        }
      }, 500)
    },
    resetForm() {
      this.$refs.questionForm.resetFields()
      this.fileList = []
    }
  }
}
</script>

<style scoped>
.submit-question {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.question-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.upload-demo {
  width: 100%;
}
</style>