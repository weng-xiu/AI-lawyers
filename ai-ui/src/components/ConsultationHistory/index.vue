<template>
  <div class="consultation-history">
    <el-card class="history-card">
      <div slot="header" class="card-header">
        <span class="title">
          <i class="el-icon-time"></i>
          咨询历史
        </span>
        <el-button type="text" @click="refreshHistory">
          <i class="el-icon-refresh"></i>
          刷新
        </el-button>
      </div>
      
      <div v-if="historyList.length === 0" class="empty-history">
        <i class="el-icon-document-remove"></i>
        <p>暂无咨询历史</p>
      </div>
      
      <el-timeline v-else>
        <el-timeline-item
          v-for="(item, index) in historyList"
          :key="item.consultationId"
          :timestamp="formatTime(item.createTime)"
          placement="top">
          <el-card class="history-item">
            <div class="item-header">
              <span class="category-tag">
                <el-tag size="small" type="info">{{ item.categoryName }}</el-tag>
              </span>
              <span class="status-tag">
                <el-tag 
                  size="small" 
                  :type="item.status === '1' ? 'success' : 'warning'">
                  {{ item.status === '1' ? '已完成' : '处理中' }}
                </el-tag>
              </span>
            </div>
            
            <div class="item-content">
              <div class="question">
                <h4>问题：</h4>
                <p>{{ item.question }}</p>
              </div>
              
              <div v-if="item.answer" class="answer">
                <h4>回答：</h4>
                <p>{{ item.answer }}</p>
              </div>
            </div>
            
            <div class="item-actions">
              <el-button 
                type="text" 
                size="small" 
                @click="viewDetail(item)">
                查看详情
              </el-button>
              <el-button 
                type="text" 
                size="small" 
                @click="deleteRecord(item, index)">
                删除记录
              </el-button>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      
      <!-- 分页 -->
      <div v-if="total > 0" class="pagination">
        <el-pagination
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="queryParams.pageNum"
          :page-sizes="[10, 20, 30, 50]"
          :page-size="queryParams.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total">
        </el-pagination>
      </div>
    </el-card>
    
    <!-- 咨询详情对话框 -->
    <el-dialog title="咨询详情" :visible.sync="dialogVisible" width="60%">
      <div v-if="currentRecord" class="detail-content">
        <div class="detail-item">
          <h4>咨询ID：</h4>
          <p>{{ currentRecord.consultationId }}</p>
        </div>
        
        <div class="detail-item">
          <h4>问题分类：</h4>
          <p>{{ currentRecord.categoryName }}</p>
        </div>
        
        <div class="detail-item">
          <h4>咨询时间：</h4>
          <p>{{ formatTime(currentRecord.createTime) }}</p>
        </div>
        
        <div class="detail-item">
          <h4>问题描述：</h4>
          <p>{{ currentRecord.question }}</p>
        </div>
        
        <div class="detail-item">
          <h4>法律建议：</h4>
          <div class="answer-content">{{ currentRecord.answer }}</div>
        </div>
        
        <div class="detail-item">
          <h4>处理状态：</h4>
          <el-tag :type="currentRecord.status === '1' ? 'success' : 'warning'">
            {{ currentRecord.status === '1' ? '已完成' : '处理中' }}
          </el-tag>
        </div>
      </div>
      
      <span slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getUserConsultations, delConsultation } from "@/api/lawyers/consultation";
import { mapGetters } from 'vuex';

export default {
  name: "ConsultationHistory",
  data() {
    return {
      // 历史记录列表
      historyList: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10
      },
      // 总记录数
      total: 0,
      // 对话框可见性
      dialogVisible: false,
      // 当前查看的记录
      currentRecord: null
    };
  },
  computed: {
    ...mapGetters([
      'userId'
    ])
  },
  created() {
    this.getHistory();
  },
  methods: {
    // 获取咨询历史
    getHistory() {
      if (!this.userId) {
        this.$message.error("用户未登录");
        return;
      }
      
      getUserConsultations(this.userId).then(response => {
        this.historyList = response.data || [];
        this.total = this.historyList.length;
      });
    },
    
    // 刷新历史记录
    refreshHistory() {
      this.getHistory();
    },
    
    // 格式化时间
    formatTime(time) {
      if (!time) return "";
      const date = new Date(time);
      return date.toLocaleString();
    },
    
    // 查看详情
    viewDetail(record) {
      this.currentRecord = record;
      this.dialogVisible = true;
    },
    
    // 删除记录
    deleteRecord(record, index) {
      this.$confirm('确认删除该咨询记录吗？', "系统提示", {
        type: "warning"
      }).then(() => {
        delConsultation(record.consultationId).then(() => {
          this.historyList.splice(index, 1);
          this.total--;
          this.$message.success("删除成功");
        });
      }).catch(() => {});
    },
    
    // 分页大小变化
    handleSizeChange(val) {
      this.queryParams.pageSize = val;
      this.getHistory();
    },
    
    // 当前页变化
    handleCurrentChange(val) {
      this.queryParams.pageNum = val;
      this.getHistory();
    }
  }
};
</script>

<style scoped>
.consultation-history {
  margin: 20px;
}

.history-card {
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

.empty-history {
  text-align: center;
  padding: 40px 0;
  color: #909399;
}

.empty-history i {
  font-size: 48px;
  margin-bottom: 10px;
  display: block;
}

.history-item {
  margin-bottom: 10px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.item-content {
  margin-bottom: 10px;
}

.item-content h4 {
  margin: 0 0 5px 0;
  color: #606266;
  font-size: 14px;
}

.item-content p {
  margin: 0;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.item-actions {
  text-align: right;
}

.pagination {
  margin-top: 20px;
  text-align: center;
}

.detail-content {
  line-height: 1.6;
}

.detail-item {
  margin-bottom: 15px;
}

.detail-item h4 {
  margin: 0 0 5px 0;
  color: #606266;
}

.detail-item p {
  margin: 0;
  color: #303133;
}

.answer-content {
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>