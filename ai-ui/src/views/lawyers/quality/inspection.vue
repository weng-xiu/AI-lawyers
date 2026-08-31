<template>
  <div class="app-container">
    <!-- ==================== 查询条件 ==================== -->
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="坐席ID" prop="agentId">
        <el-input v-model="queryParams.agentId" placeholder="请输入坐席ID" clearable style="width: 150px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="主叫号码" prop="callerNumber">
        <el-input v-model="queryParams.callerNumber" placeholder="请输入主叫号码" clearable style="width: 180px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="AI状态" prop="aiStatus">
        <el-select v-model="queryParams.aiStatus" placeholder="请选择AI状态" clearable style="width: 140px">
          <el-option label="待检" value="0" />
          <el-option label="检中" value="1" />
          <el-option label="完成" value="2" />
          <el-option label="失败" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="复核状态" prop="reviewStatus">
        <el-select v-model="queryParams.reviewStatus" placeholder="请选择复核状态" clearable style="width: 140px">
          <el-option label="未复核" value="0" />
          <el-option label="通过" value="1" />
          <el-option label="驳回整改" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="质检时间">
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="yyyy-MM-dd HH:mm:ss"
          :default-time="['00:00:00', '23:59:59']"
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:quality:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- ==================== 质检列表 ==================== -->
    <el-table v-loading="loading" :data="qualityList">
      <el-table-column label="质检ID" align="center" prop="inspectionId" width="80" />
      <el-table-column label="坐席" align="center" min-width="100">
        <template slot-scope="scope">
          {{ scope.row.agentName || ('坐席' + scope.row.agentId) }}
        </template>
      </el-table-column>
      <el-table-column label="主叫号码" align="center" prop="callerNumber" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="话单ID" align="center" prop="recordId" width="90" />
      <el-table-column label="AI总分" align="center" prop="totalScore" width="90">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.totalScore != null" :type="scoreTagType(scope.row.totalScore)" size="small">
            {{ scope.row.totalScore }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="AI状态" align="center" prop="aiStatus" width="90">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.aiStatus == '0'" type="info" size="small">待检</el-tag>
          <el-tag v-else-if="scope.row.aiStatus == '1'" type="warning" size="small">检中</el-tag>
          <el-tag v-else-if="scope.row.aiStatus == '2'" type="success" size="small">完成</el-tag>
          <el-tag v-else-if="scope.row.aiStatus == '3'" type="danger" size="small">失败</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="风险预警" align="center" width="90">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.riskWarningId != null" type="danger" size="small">已联动</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="复核状态" align="center" prop="reviewStatus" width="100">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.reviewStatus == '1'" type="success" size="small">通过</el-tag>
          <el-tag v-else-if="scope.row.reviewStatus == '2'" type="danger" size="small">驳回整改</el-tag>
          <el-tag v-else type="info" size="small">未复核</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="复核人" align="center" prop="reviewerName" width="90" />
      <el-table-column label="质检时间" align="center" prop="createTime" width="160">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="220">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['lawyers:quality:query']">详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-finished"
            :disabled="scope.row.aiStatus != '2'"
            @click="handleReview(scope.row)"
            v-hasPermi="['lawyers:quality:review']"
          >复核</el-button>
          <el-button size="mini" type="text" icon="el-icon-refresh-right" @click="handleInspect(scope.row)" v-hasPermi="['lawyers:quality:inspect']">重检</el-button>
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

    <!-- ==================== 详情对话框（转写/评分/违禁明细） ==================== -->
    <el-dialog title="质检详情" :visible.sync="detailOpen" width="860px" append-to-body>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="质检ID">{{ detail.inspectionId }}</el-descriptions-item>
        <el-descriptions-item label="坐席">{{ detail.agentName || detail.agentId }}</el-descriptions-item>
        <el-descriptions-item label="主叫号码">{{ detail.callerNumber }}</el-descriptions-item>
        <el-descriptions-item label="AI总分">
          <el-tag v-if="detail.totalScore != null" :type="scoreTagType(detail.totalScore)" size="small">{{ detail.totalScore }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI状态">
          <el-tag v-if="detail.aiStatus == '2'" type="success" size="small">完成</el-tag>
          <el-tag v-else-if="detail.aiStatus == '3'" type="danger" size="small">失败</el-tag>
          <el-tag v-else type="warning" size="small">处理中/待检</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="风险预警">
          <el-tag v-if="detail.riskWarningId != null" type="danger" size="small">预警#{{ detail.riskWarningId }}</el-tag>
          <span v-else>无</span>
        </el-descriptions-item>
        <el-descriptions-item label="失败原因/评语" :span="3">{{ detail.aiRemark }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">维度评分</el-divider>
      <el-row :gutter="12">
        <el-col :span="6" v-for="(val, key) in dimensions" :key="key">
          <el-card shadow="never" class="dim-card">
            <div class="dim-label">{{ dimName(key) }}</div>
            <div class="dim-value" :style="{ color: dimColor(val) }">{{ val }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-divider content-position="left">命中问题</el-divider>
      <el-table :data="violations" size="small" border max-height="180" v-if="violations.length">
        <el-table-column label="命中关键词/问题" prop="keyword" min-width="160" :show-overflow-tooltip="true" />
        <el-table-column label="原因说明" prop="reason" min-width="260" :show-overflow-tooltip="true" />
      </el-table>
      <el-empty v-else description="未命中违禁/敏感/情绪问题" :image-size="60"></el-empty>

      <el-divider content-position="left">通话转写</el-divider>
      <div class="transcript-box">{{ detail.transcript || '暂无转写文本（录音缺失或 ASR 未配置）' }}</div>

      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- ==================== 复核对话框 ==================== -->
    <el-dialog title="人工复核" :visible.sync="reviewOpen" width="560px" append-to-body>
      <el-form ref="reviewForm" :model="reviewForm" :rules="reviewRules" label-width="90px">
        <el-form-item label="质检ID">
          <span>{{ reviewForm.inspectionId }}（坐席：{{ reviewForm.agentName || reviewForm.agentId }}，AI总分：{{ reviewForm.totalScore }}）</span>
        </el-form-item>
        <el-form-item label="复核结果" prop="reviewStatus">
          <el-radio-group v-model="reviewForm.reviewStatus">
            <el-radio label="1">通过</el-radio>
            <el-radio label="2">驳回整改</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核评语" prop="reviewRemark">
          <el-input v-model="reviewForm.reviewRemark" type="textarea" :rows="4" placeholder="请输入复核意见（整改要求/表扬说明）" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitReview">确 定</el-button>
        <el-button @click="reviewOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listQuality, getQuality, reviewQuality, inspectRecord, exportQuality } from "@/api/lawyers/quality"

export default {
  name: "QualityInspection",
  data() {
    return {
      loading: true,
      showSearch: true,
      total: 0,
      qualityList: [],
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        agentId: undefined,
        callerNumber: undefined,
        aiStatus: undefined,
        reviewStatus: undefined
      },
      // 详情
      detailOpen: false,
      detail: {},
      dimensions: {},
      violations: [],
      // 复核
      reviewOpen: false,
      reviewForm: {},
      reviewRules: {
        reviewStatus: [{ required: true, message: "请选择复核结果", trigger: "change" }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询质检列表 */
    getList() {
      this.loading = true
      if (this.dateRange && this.dateRange.length === 2) {
        this.queryParams.params = { beginTime: this.dateRange[0], endTime: this.dateRange[1] }
      } else {
        this.queryParams.params = {}
      }
      listQuality(this.queryParams).then(response => {
        this.qualityList = response.rows
        this.total = response.total
        this.loading = false
      })
    },
    /** 详情 */
    handleDetail(row) {
      getQuality(row.inspectionId).then(response => {
        this.detail = response.data || {}
        this.dimensions = this.safeJson(this.detail.dimensionJson)
        this.violations = this.safeJsonArray(this.detail.violationJson)
        this.detailOpen = true
      })
    },
    /** 复核 */
    handleReview(row) {
      this.reviewForm = {
        inspectionId: row.inspectionId,
        agentId: row.agentId,
        agentName: row.agentName,
        totalScore: row.totalScore,
        reviewStatus: row.reviewStatus === '2' ? '2' : '1',
        reviewRemark: row.reviewRemark
      }
      this.reviewOpen = true
      this.$nextTick(() => this.$refs.reviewForm && this.$refs.reviewForm.clearValidate())
    },
    submitReview() {
      this.$refs.reviewForm.validate(valid => {
        if (!valid) return
        reviewQuality(this.reviewForm).then(() => {
          this.$modal.msgSuccess("复核完成")
          this.reviewOpen = false
          this.getList()
        })
      })
    },
    /** 手动触发重检 */
    handleInspect(row) {
      this.$modal.confirm('确认对该话单（recordId=' + row.recordId + '）重新执行 AI 质检？').then(() => {
        return inspectRecord(row.recordId)
      }).then(() => {
        this.$modal.msgSuccess("已触发质检，稍后刷新查看结果")
        this.getList()
      }).catch(() => {})
    },
    /** 导出 */
    handleExport() {
      this.download('lawyers/quality/export', { ...this.queryParams }, `quality_${new Date().getTime()}.xlsx`)
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    safeJson(str) {
      try { return str ? JSON.parse(str) : {} } catch (e) { return {} }
    },
    safeJsonArray(str) {
      try { const v = str ? JSON.parse(str) : []; return Array.isArray(v) ? v : [] } catch (e) { return [] }
    },
    dimName(key) {
      return { serviceNorm: '服务规范', answerAccuracy: '答复准确', emotionAttitude: '情绪态度', compliance: '合规话术' }[key] || key
    },
    scoreTagType(score) {
      const v = Number(score)
      if (v >= 85) return 'success'
      if (v >= 60) return 'warning'
      return 'danger'
    },
    dimColor(val) {
      const v = Number(val)
      if (v >= 85) return '#67c23a'
      if (v >= 60) return '#e6a23c'
      return '#f56c6c'
    }
  }
}
</script>

<style scoped>
.dim-card { text-align: center; margin-bottom: 8px; }
.dim-label { color: #909399; font-size: 13px; margin-bottom: 6px; }
.dim-value { font-size: 26px; font-weight: 700; }
.transcript-box {
  max-height: 240px; overflow-y: auto; background: #f5f7fa; border-radius: 4px;
  padding: 12px; line-height: 1.8; font-size: 13px; white-space: pre-wrap; color: #303133;
}
</style>
