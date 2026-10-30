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
            width="260">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="primary"
                @click.stop="viewDetail(scope.row)">查看详情</el-button>
              <el-button
                v-if="scope.row.status === 'COMPLETED'"
                size="mini"
                type="success"
                :disabled="isEvaluated(scope.row)"
                @click.stop="evaluateConsultation(scope.row)">
                {{ isEvaluated(scope.row) ? '已评价' : '评价' }}
              </el-button>
              <el-button
                v-if="scope.row.status === 'COMPLETED'"
                size="mini"
                @click.stop="openSupplement(scope.row)">补充材料</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="pagination-container">
          <!-- 无障碍：prev/next 用文字替代纯图标按钮（button-name）；
               移除 jumper 避免未标注的 number 输入框（label 规则） -->
          <el-pagination
            prev-text="上一页"
            next-text="下一页"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
            :current-page="pagination.currentPage"
            :page-sizes="[10, 20, 50, 100]"
            :page-size="pagination.pageSize"
            layout="total, sizes, prev, pager, next"
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
            aria-label="咨询结果满意度评分"
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

    <!-- F10 补充材料对话框：追加附件与说明，不覆盖原附件 -->
    <el-dialog
      title="补充材料"
      :visible.sync="supplementDialogVisible"
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
        <el-button @click="supplementDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="supplementSubmitting" @click="submitSupplement">提交补充</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { isConsultationEvaluated, supplementConsultation } from '@/api/consultation'
import { announce } from '@/utils/a11y'

/** F10 补充材料限制（与后端保持一致） */
const MAX_SUPPLEMENT_FILES = 3
const MAX_SUPPLEMENT_BYTES = 10 * 1024 * 1024
const ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'png', 'pdf', 'doc', 'docx']

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
      colors: ['#99A9BF', '#F7BA2A', '#FF9900'],
      // F10 评价引导：consultationId → 是否已评价
      evaluatedIds: {},
      // F10 材料补正
      supplementDialogVisible: false,
      supplementConsultationId: null,
      supplementNote: '',
      supplementFiles: [],
      supplementSubmitting: false
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
          this.loadEvaluatedStates()
        })
        .catch(error => {
          this.$message.error('获取历史记录失败: ' + (error.response && error.response.data ? error.response.data.msg : '未知错误'))
          this.loading = false
        })
    },
    /** F10 评价引导：仅对已完成且未记录的咨询查询已评价状态（失败静默，不阻断列表） */
    loadEvaluatedStates() {
      const targets = this.consultationList.filter(row =>
        row.status === 'COMPLETED' && this.evaluatedIds[row.consultationId] === undefined)
      targets.forEach(row => {
        isConsultationEvaluated(row.consultationId).then(res => {
          this.$set(this.evaluatedIds, row.consultationId, !!res.data)
        }).catch(() => {
          this.$set(this.evaluatedIds, row.consultationId, false)
        })
      })
    },
    isEvaluated(row) {
      return this.evaluatedIds[row.consultationId] === true
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
      if (this.isEvaluated(row)) {
        this.$message.info('该咨询已评价，无需重复提交')
        return
      }
      this.evaluationForm.consultationId = row.consultationId
      this.evaluationForm.overallRating = 0
      this.evaluationForm.feedback = ''
      this.evaluationDialogVisible = true
    },
    /** F10 材料补正：打开补充材料对话框 */
    openSupplement(row) {
      this.supplementConsultationId = row.consultationId
      this.supplementNote = ''
      this.supplementFiles = []
      this.supplementDialogVisible = true
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
      const files = (this.supplementFiles || []).map(item => item.raw).filter(Boolean)
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
      supplementConsultation(this.supplementConsultationId, formData)
        .then(() => {
          this.supplementDialogVisible = false
          this.supplementFiles = []
          this.supplementNote = ''
          this.$message.success('材料补充成功')
          announce('材料补充成功，工作人员会尽快查看', 'polite')
        })
        .catch(error => {
          const msg = (error.response && error.response.data && error.response.data.msg)
            || (error.message || '未知错误')
          this.$message.error('材料补充失败: ' + msg)
        })
        .finally(() => {
          this.supplementSubmitting = false
        })
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
          this.$set(this.evaluatedIds, this.evaluationForm.consultationId, true)
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

/* F10 补充材料对话框 */
.supplement-tip { margin: 0 0 14px; color: #5b6b84; font-size: 14px; line-height: 1.7; }
.supplement-form .el-upload { width: 100%; margin-bottom: 12px; }

/* U6 移动端：收窄留白，筛选列/弹窗由 App.vue 全局层处理 */
@media (max-width: 768px) {
  .consultation-history { padding: 12px; }
  .rating-container { flex-wrap: wrap; gap: 8px; }
  .rating-container span { min-width: 0; margin-right: 0; }
  .pagination-container { overflow-x: auto; }
}
</style>