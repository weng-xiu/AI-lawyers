<template>
  <div class="legal-consultation">
    <el-card class="consultation-card">
      <div slot="header" class="card-header">
        <span class="title">
          <i class="el-icon-chat-line-square"></i>
          智能法律咨询
        </span>
      </div>
      
      <el-form ref="consultationForm" :model="consultationForm" :rules="rules" label-width="80px">
        <el-form-item label="问题分类" prop="categoryId">
          <el-select v-model="consultationForm.categoryId" placeholder="请选择问题分类" style="width: 100%">
            <el-option
              v-for="category in categories"
              :key="category.categoryId"
              :label="category.categoryName"
              :value="category.categoryId">
            </el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item label="问题描述" prop="question">
          <el-input
            type="textarea"
            v-model="consultationForm.question"
            placeholder="请详细描述您遇到的法律问题"
            :rows="4"
            maxlength="500"
            show-word-limit>
          </el-input>
        </el-form-item>
        
        <el-form-item>
          <el-button type="primary" @click="submitConsultation" :loading="loading">
            <i class="el-icon-s-promotion"></i>
            提交咨询
          </el-button>
          <el-button @click="resetForm">
            <i class="el-icon-refresh"></i>
            重置
          </el-button>
        </el-form-item>
      </el-form>
      
      <!-- 咨询结果展示 -->
      <div v-if="resultVisible" class="consultation-result">
        <el-divider content-position="left">
          <span class="result-title">
            <i class="el-icon-s-opportunity"></i>
            咨询结果
          </span>
        </el-divider>
        <div class="result-content" v-html="consultationResult"></div>
        
        <div class="result-actions">
          <el-button type="success" size="small" @click="saveRecord">
            <i class="el-icon-document-checked"></i>
            保存记录
          </el-button>
          <el-button type="info" size="small" @click="viewHistory">
            <i class="el-icon-time"></i>
            查看历史
          </el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { getValidCategories } from "@/api/lawyers/category";
import { addConsultation } from "@/api/lawyers/consultation";
import { mapGetters } from 'vuex';

export default {
  name: "LegalConsultation",
  data() {
    return {
      // 表单数据
      consultationForm: {
        categoryId: undefined,
        question: ""
      },
      // 表单验证规则
      rules: {
        categoryId: [
          { required: true, message: "请选择问题分类", trigger: "change" }
        ],
        question: [
          { required: true, message: "请输入问题描述", trigger: "blur" },
          { min: 10, message: "问题描述至少需要10个字符", trigger: "blur" }
        ]
      },
      // 分类列表
      categories: [],
      // 加载状态
      loading: false,
      // 结果可见性
      resultVisible: false,
      // 咨询结果
      consultationResult: "",
      // 当前咨询记录ID
      currentConsultationId: null
    };
  },
  computed: {
    ...mapGetters([
      'userId',
      'name'
    ])
  },
  created() {
    this.getCategories();
  },
  methods: {
    // 获取分类列表
    getCategories() {
      getValidCategories().then(response => {
        this.categories = response.data || [];
      });
    },
    
    // 提交咨询
    submitConsultation() {
      this.$refs["consultationForm"].validate(valid => {
        if (valid) {
          this.loading = true;
          
          // 构建咨询记录数据
          const consultationData = {
            userId: this.userId,
            question: this.consultationForm.question,
            categoryId: this.consultationForm.categoryId,
            status: "0" // 处理中
          };
          
          // 调用后端API生成回答
          addConsultation(consultationData).then(response => {
            this.currentConsultationId = response.data.consultationId;
            this.consultationResult = response.data.answer;
            this.resultVisible = true;
            this.$message.success("咨询提交成功");
          }).catch(() => {
            this.$message.error("咨询提交失败，请稍后重试");
          }).finally(() => {
            this.loading = false;
          });
        }
      });
    },
    
    // 重置表单
    resetForm() {
      this.$refs["consultationForm"].resetFields();
      this.resultVisible = false;
      this.consultationResult = "";
      this.currentConsultationId = null;
    },
    
    // 保存记录
    saveRecord() {
      // 记录已经在提交时保存，这里只是提示用户
      this.$message.success("咨询记录已保存");
    },
    
    // 查看历史
    viewHistory() {
      this.$router.push("/lawyers/consultation/history");
    }
  }
};
</script>

<style scoped>
.legal-consultation {
  margin: 20px;
}

.consultation-card {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.title i {
  margin-right: 8px;
  color: #409EFF;
}

.consultation-result {
  margin-top: 20px;
}

.result-title {
  font-weight: bold;
  color: #409EFF;
}

.result-title i {
  margin-right: 5px;
}

.result-content {
  background-color: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  line-height: 1.6;
  margin-bottom: 15px;
  white-space: pre-wrap;
}

.result-actions {
  text-align: right;
}

.result-actions .el-button {
  margin-left: 10px;
}
</style>