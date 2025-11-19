<template>
  <div class="app-container">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>法律咨询</span>
      </div>
      
      <!-- 咨询表单 -->
      <el-form ref="consultationForm" :model="consultationForm" :rules="rules" label-width="100px">
        <el-form-item label="问题分类" prop="category">
          <el-select v-model="consultationForm.category" placeholder="请选择问题分类" style="width: 100%">
            <el-option
              v-for="item in categories"
              :key="item.value"
              :label="item.label"
              :value="item.value">
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="问题描述" prop="content">
          <el-input
            type="textarea"
            :rows="6"
            placeholder="请详细描述您的法律问题"
            v-model="consultationForm.content">
          </el-input>
        </el-form-item>
        
        <el-form-item label="相关附件">
          <el-upload
            class="upload-demo"
            action=""
            :on-preview="handlePreview"
            :on-remove="handleRemove"
            :before-remove="beforeRemove"
            :on-change="handleChange"
            :file-list="fileList"
            :auto-upload="false">
            <el-button size="small" type="primary">点击上传</el-button>
            <div slot="tip" class="el-upload__tip">上传相关法律文件，如合同、证据等</div>
          </el-upload>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitForm">提交咨询</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 咨询历史 -->
    <el-card class="box-card" style="margin-top: 20px;">
      <div slot="header" class="clearfix">
        <span>咨询历史</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="refreshHistory">刷新</el-button>
      </div>
      
      <el-table
        v-loading="loading"
        :data="consultationList"
        style="width: 100%">
        <el-table-column
          prop="consultationId"
          label="咨询ID"
          width="80">
        </el-table-column>
        <el-table-column
          prop="category"
          label="问题分类"
          width="120">
        </el-table-column>
        <el-table-column
          prop="content"
          label="问题描述"
          show-overflow-tooltip>
        </el-table-column>
        <el-table-column
          prop="status"
          label="状态"
          width="100">
          <template slot-scope="scope">
            <el-tag :type="getStatusType(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="咨询时间"
          width="160">
        </el-table-column>
        <el-table-column
          label="操作"
          width="200">
          <template slot-scope="scope">
            <el-button
              size="mini"
              type="primary"
              @click="viewDetail(scope.row)">查看详情</el-button>
            <el-button
              v-if="scope.row.status === 'completed'"
              size="mini"
              type="success"
              @click="showEvaluationDialog(scope.row)">评价</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <pagination
        v-show="total>0"
        :total="total"
        :page.sync="queryParams.pageNum"
        :limit.sync="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
    
    <!-- 咨询详情对话框 -->
    <el-dialog title="咨询详情" :visible.sync="detailDialogVisible" width="60%">
      <div v-if="currentConsultation">
        <p><strong>咨询ID：</strong>{{ currentConsultation.consultationId }}</p>
        <p><strong>问题分类：</strong>{{ currentConsultation.category }}</p>
        <p><strong>问题描述：</strong>{{ currentConsultation.content }}</p>
        <p v-if="currentConsultation.attachments"><strong>相关附件：</strong>{{ currentConsultation.attachments }}</p>
        <p v-if="currentConsultation.aiAnswer"><strong>AI回答：</strong>{{ currentConsultation.aiAnswer }}</p>
        <p v-if="currentConsultation.relatedLaws"><strong>相关法条：</strong>{{ currentConsultation.relatedLaws }}</p>
        <p v-if="currentConsultation.relatedCases"><strong>相关案例：</strong>{{ currentConsultation.relatedCases }}</p>
        <p><strong>置信度：</strong>{{ currentConsultation.confidence }}%</p>
        <p><strong>状态：</strong>{{ getStatusText(currentConsultation.status) }}</p>
        <p><strong>处理时间：</strong>{{ currentConsultation.processTime }}</p>
      </div>
    </el-dialog>
    
    <!-- 评价对话框 -->
    <el-dialog title="咨询评价" :visible.sync="evaluationDialogVisible" width="50%">
      <el-form ref="evaluationForm" :model="evaluationForm" :rules="evaluationRules" label-width="100px">
        <el-form-item label="评分" prop="rating">
          <el-rate v-model="evaluationForm.rating" :max="5" show-text></el-rate>
        </el-form-item>
        
        <el-form-item label="反馈意见" prop="feedback">
          <el-input
            type="textarea"
            :rows="4"
            placeholder="请输入您的反馈意见"
            v-model="evaluationForm.feedback">
          </el-input>
        </el-form-item>
        
        <el-form-item label="改进建议" prop="suggestions">
          <el-input
            type="textarea"
            :rows="3"
            placeholder="请输入您的改进建议"
            v-model="evaluationForm.suggestions">
          </el-input>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitEvaluation">提交评价</el-button>
          <el-button @click="evaluationDialogVisible = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>
  </div>
</template>

<script>
import { submitConsultation, getUserHistory, getConsultationDetail, submitEvaluation, getQuestionCategories } from "@/api/lawyers/userConsultation";
import Pagination from "@/components/Pagination";

export default {
  name: "UserConsultation",
  components: { Pagination },
  data() {
    return {
      // 咨询表单数据
      consultationForm: {
        category: "",
        content: "",
        attachments: ""
      },
      // 表单校验
      rules: {
        category: [
          { required: true, message: "请选择问题分类", trigger: "change" }
        ],
        content: [
          { required: true, message: "请输入问题描述", trigger: "blur" },
          { min: 10, message: "问题描述至少10个字符", trigger: "blur" }
        ]
      },
      // 文件列表
      fileList: [],
      // 加载状态
      loading: false,
      // 咨询列表
      consultationList: [],
      // 总条数
      total: 0,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: null
      },
      // 问题分类
      categories: [],
      // 详情对话框可见性
      detailDialogVisible: false,
      // 当前咨询详情
      currentConsultation: null,
      // 评价对话框可见性
      evaluationDialogVisible: false,
      // 评价表单
      evaluationForm: {
        consultationId: null,
        rating: 5,
        feedback: "",
        suggestions: ""
      },
      // 评价表单校验
      evaluationRules: {
        rating: [
          { required: true, message: "请选择评分", trigger: "change" }
        ],
        feedback: [
          { required: true, message: "请输入反馈意见", trigger: "blur" }
        ]
      }
    };
  },
  created() {
    this.getCategories();
    this.getList();
    // 获取当前用户ID
    this.queryParams.userId = this.$store.state.user.userId;
  },
  methods: {
    // 获取问题分类
    getCategories() {
      getQuestionCategories().then(response => {
        this.categories = response.data;
      });
    },
    // 提交咨询
    submitForm() {
      this.$refs["consultationForm"].validate(valid => {
        if (valid) {
          // 处理附件
          if (this.fileList.length > 0) {
            this.consultationForm.attachments = JSON.stringify(this.fileList);
          }
          
          submitConsultation(this.consultationForm).then(response => {
            this.$modal.msgSuccess("咨询提交成功，正在处理中...");
            this.resetForm();
            this.getList();
          });
        }
      });
    },
    // 重置表单
    resetForm() {
      this.consultationForm = {
        category: "",
        content: "",
        attachments: ""
      };
      this.fileList = [];
      this.$refs["consultationForm"].resetFields();
    },
    // 获取咨询列表
    getList() {
      this.loading = true;
      getUserHistory(this.queryParams).then(response => {
        this.consultationList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 刷新历史记录
    refreshHistory() {
      this.getList();
    },
    // 查看详情
    viewDetail(row) {
      this.currentConsultation = row;
      // 如果没有AI回答，尝试获取最新结果
      if (!row.aiAnswer && row.status === 'processing') {
        getConsultationResult(row.consultationId).then(response => {
          this.currentConsultation = response.data;
          // 更新列表中的数据
          const index = this.consultationList.findIndex(item => item.consultationId === row.consultationId);
          if (index !== -1) {
            this.consultationList.splice(index, 1, response.data);
          }
        });
      }
      this.detailDialogVisible = true;
    },
    // 显示评价对话框
    showEvaluationDialog(row) {
      this.evaluationForm.consultationId = row.consultationId;
      this.evaluationDialogVisible = true;
    },
    // 提交评价
    submitEvaluation() {
      this.$refs["evaluationForm"].validate(valid => {
        if (valid) {
          submitEvaluation(this.evaluationForm).then(response => {
            this.$modal.msgSuccess("评价提交成功");
            this.evaluationDialogVisible = false;
            this.resetEvaluationForm();
            this.getList();
          });
        }
      });
    },
    // 重置评价表单
    resetEvaluationForm() {
      this.evaluationForm = {
        consultationId: null,
        rating: 5,
        feedback: "",
        suggestions: ""
      };
      this.$refs["evaluationForm"].resetFields();
    },
    // 获取状态类型
    getStatusType(status) {
      const statusMap = {
        'pending': 'info',
        'processing': 'warning',
        'completed': 'success',
        'failed': 'danger'
      };
      return statusMap[status] || 'info';
    },
    // 获取状态文本
    getStatusText(status) {
      const statusMap = {
        'pending': '待处理',
        'processing': '处理中',
        'completed': '已完成',
        'failed': '处理失败'
      };
      return statusMap[status] || '未知';
    },
    // 文件上传相关方法
    handlePreview(file) {
      console.log(file);
    },
    handleRemove(file, fileList) {
      console.log(file, fileList);
    },
    beforeRemove(file) {
      return this.$confirm(`确定移除 ${ file.name }？`);
    },
    handleChange(file, fileList) {
      this.fileList = fileList;
    }
  }
};
</script>

<style scoped>
.box-card {
  margin-bottom: 20px;
}
</style>