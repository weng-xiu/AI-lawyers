<template>
  <div class="consultation-history">
    <el-card class="history-card">
      <div slot="header" class="card-header">
        <span>咨询历史记录</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="goToSubmit">提交新咨询</el-button>
      </div>
      
      <!-- 搜索和筛选区域 -->
      <div class="filter-container">
        <el-row :gutter="20">
          <el-col :span="10">
            <el-input
              v-model="searchQuery"
              placeholder="搜索问题内容"
              prefix-icon="el-icon-search"
              clearable
              @keyup.enter.native="searchConsultations">
            </el-input>
          </el-col>
          <el-col :span="8">
            <el-select v-model="selectedCategory" placeholder="问题分类" clearable>
              <el-option label="全部分类" value=""></el-option>
              <el-option label="婚姻家庭" value="marriage_family"></el-option>
              <el-option label="劳动纠纷" value="labor_dispute"></el-option>
              <el-option label="合同纠纷" value="contract_dispute"></el-option>
              <el-option label="财产权益" value="property_dispute"></el-option>
              <el-option label="刑事案件" value="criminal_case"></el-option>
              <el-option label="知识产权" value="intellectual_property"></el-option>
              <el-option label="消费维权" value="consumer_rights"></el-option>
              <el-option label="其他" value="other"></el-option>
            </el-select>
          </el-col>
          <el-col :span="6">
            <el-button type="primary" @click="searchConsultations">搜索</el-button>
          </el-col>
        </el-row>
      </div>
      
      <!-- 历史记录列表 -->
      <div class="history-list" v-loading="loading">
        <el-table
          :data="consultationList"
          style="width: 100%"
          @row-click="viewDetail">
          <el-table-column
            prop="content"
            label="问题内容"
            min-width="300"
            show-overflow-tooltip>
          </el-table-column>
          <el-table-column
            prop="category"
            label="分类"
            width="120">
            <template slot-scope="scope">
              <el-tag :type="getCategoryType(scope.row.category)" size="small">
                {{ getCategoryName(scope.row.category) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="status"
            label="状态"
            width="100">
            <template slot-scope="scope">
              <el-tag :type="getStatusType(scope.row.status)" size="small">
                {{ getStatusName(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="createTime"
            label="提交时间"
            width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column
            label="操作"
            width="180">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="primary"
                @click.stop="viewDetail(scope.row)">查看详情</el-button>
              <el-button
                v-if="scope.row.status === 'COMPLETED'"
                size="mini"
                type="success"
                @click.stop="evaluateConsultation(scope.row)">评价</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <el-pagination
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            :current-page="pagination.currentPage"
            :page-sizes="[10, 20, 50, 100]"
            :page-size="pagination.pageSize"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total">
          </el-pagination>
        </div>
      </div>
      
      <!-- 空状态 -->
      <div v-if="!loading && consultationList.length === 0" class="empty-state">
        <el-empty description="暂无咨询记录"></el-empty>
        <el-button type="primary" @click="goToSubmit">提交新咨询</el-button>
      </div>
    </el-card>
    
    <!-- 评价对话框 -->
    <el-dialog
      title="咨询评价"
      :visible.sync="evaluationDialogVisible"
      width="40%">
      <div class="evaluation-form">
        <div class="rating-container">
          <span>请对本次咨询结果进行评价:</span>
          <el-rate 
            v-model="evaluationForm.overallRating" 
            :colors="colors"
            show-text>
          </el-rate>
        </div>
        
        <div class="feedback-container">
          <el-input
            type="textarea"
            :rows="4"
            placeholder="请输入您的反馈意见（可选）"
            v-model="evaluationForm.feedback">
          </el-input>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="evaluationDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEvaluation" :loading="submitting">提交评价</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'ConsultationHistory',
  data() {
    return {
      loading: false,
      consultationList: [],
      searchQuery: '',
      selectedCategory: '',
      pagination: {
        currentPage: 1,
        pageSize: 10,
        total: 0
      },
      evaluationDialogVisible: false,
      evaluationForm: {
        consultationId: null,
        overallRating: 0,
        feedback: ''
      },
      submitting: false,
      colors: ['#99A9BF', '#F7BA2A', '#FF9900']
    }
  },
  created() {
    this.fetchConsultationHistory()
  },
  methods: {
    fetchConsultationHistory() {
      this.loading = true
      
      const params = {
        pageNum: this.pagination.currentPage,
        pageSize: this.pagination.pageSize,
        content: this.searchQuery,
        category: this.selectedCategory
      }
      
      this.$http.get('/aiuser/consultation/history', { params })
        .then(response => {
          this.consultationList = response.rows || []
          this.pagination.total = response.total || 0
          this.loading = false
        })
        .catch(error => {
          this.$message.error('获取历史记录失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
          this.loading = false
        })
    },
    searchConsultations() {
      this.pagination.currentPage = 1
      this.fetchConsultationHistory()
    },
    handleSizeChange(val) {
      this.pagination.pageSize = val
      this.fetchConsultationHistory()
    },
    handleCurrentChange(val) {
      this.pagination.currentPage = val
      this.fetchConsultationHistory()
    },
    viewDetail(row) {
      this.$router.push({
        path: '/consultation/result',
        query: { id: row.consultationId }
      })
    },
    evaluateConsultation(row) {
      this.evaluationForm.consultationId = row.consultationId
      this.evaluationForm.overallRating = 0
      this.evaluationForm.feedback = ''
      this.evaluationDialogVisible = true
    },
    submitEvaluation() {
      if (this.evaluationForm.overallRating === 0) {
        this.$message.warning('请先选择评分')
        return
      }
      
      this.submitting = true
      
      this.$http.post('/aiuser/consultation/evaluation', this.evaluationForm)
        .then(() => {
          this.$message.success('评价提交成功')
          this.evaluationDialogVisible = false
          this.submitting = false
          // 更新列表中的评价状态
          this.fetchConsultationHistory()
        })
        .catch(error => {
          this.$message.error('评价提交失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
          this.submitting = false
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
    getStatusType(status) {
      const typeMap = {
        PROCESSING: 'warning',
        COMPLETED: 'success',
        FAILED: 'danger'
      }
      return typeMap[status] || ''
    },
    getStatusName(status) {
      const nameMap = {
        PROCESSING: '处理中',
        COMPLETED: '已完成',
        FAILED: '处理失败'
      }
      return nameMap[status] || '未知'
    },
    formatDate(dateString) {
      const date = new Date(dateString)
      return date.toLocaleString()
    },
    goToSubmit() {
      this.$router.push('/consultation/submit')
    }
  }
}
</script>

<style scoped>
.consultation-history {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.history-card {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.filter-container {
  margin-bottom: 20px;
}

.history-list {
  min-height: 400px;
}

.pagination-container {
  margin-top: 20px;
  text-align: center;
}

.empty-state {
  text-align: center;
  padding: 40px 0;
}

.empty-state .el-button {
  margin-top: 20px;
}

.evaluation-form {
  padding: 10px 0;
}

.rating-container {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.rating-container span {
  margin-right: 15px;
  min-width: 160px;
}

.feedback-container {
  margin-bottom: 10px;
}
</style>