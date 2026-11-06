<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ErrorRetry v-if="loadError" @retry="getList" />
    <ProTable
      v-else
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="qualityList"
      :loading="loading"
      :total="total"
      :stripe="false"
      :table-attrs="{ border: true }"
      label-width="80px"
      persist-key="quality:inspection"
      empty-text="暂无质检记录"
      empty-hint="通话完成并转写后将自动进入质检"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList">
      <template #query-qualityDateRange>
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="yyyy-MM-dd HH:mm:ss"
          :default-time="['00:00:00', '23:59:59']"
        />
      </template>
      <template #toolbar-left>
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:quality:export']"
        >导出</el-button>
        <el-button
          type="primary"
          plain
          icon="el-icon-set-up"
          size="mini"
          @click="handleTemplate"
          v-hasPermi="['lawyers:quality:review']"
        >质检模板</el-button>
      </template>
      <template #column-agentName="{ row }">{{ row.agentName || ('坐席' + row.agentId) }}</template>
      <template #column-totalScore="{ row }">
        <el-tag v-if="row.totalScore != null" :type="scoreTagType(row.totalScore)" size="small">{{ row.totalScore }}</el-tag>
        <span v-else>-</span>
      </template>
      <template #column-aiStatus="{ row }">
        <el-tag v-if="row.aiStatus == '0'" type="info" size="small">待检</el-tag>
        <el-tag v-else-if="row.aiStatus == '1'" type="warning" size="small">检中</el-tag>
        <el-tag v-else-if="row.aiStatus == '2'" type="success" size="small">完成</el-tag>
        <el-tag v-else-if="row.aiStatus == '3'" type="danger" size="small">失败</el-tag>
      </template>
      <template #column-riskWarningId="{ row }">
        <el-tag v-if="row.riskWarningId != null" type="danger" size="small">已联动</el-tag>
        <span v-else>-</span>
      </template>
      <template #column-reviewStatus="{ row }">
        <el-tag v-if="row.reviewStatus == '1'" type="success" size="small">通过</el-tag>
        <el-tag v-else-if="row.reviewStatus == '2'" type="danger" size="small">驳回整改</el-tag>
        <el-tag v-else type="info" size="small">未复核</el-tag>
      </template>
      <template #column-appealStatus="{ row }">
        <el-tag v-if="row.appealStatus == '1'" type="warning" size="small">待复核</el-tag>
        <el-tag v-else-if="row.appealStatus == '2'" type="info" size="small">维持</el-tag>
        <el-tag v-else-if="row.appealStatus == '3'" type="success" size="small">已改分</el-tag>
        <span v-else>-</span>
      </template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)" v-hasPermi="['lawyers:quality:query']">详情</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-finished"
          :disabled="row.aiStatus != '2'"
          @click="handleReview(row)"
          v-hasPermi="['lawyers:quality:review']"
        >复核</el-button>
        <el-button size="mini" type="text" icon="el-icon-refresh-right" @click="handleInspect(row)" v-hasPermi="['lawyers:quality:inspect']">重检</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-warning-outline"
          :disabled="row.aiStatus != '2' || row.appealStatus == '1'"
          @click="handleAppeal(row)"
        >申诉</el-button>
        <el-button
          size="mini"
          type="text"
          icon="el-icon-s-check"
          :disabled="row.appealStatus != '1'"
          @click="handleAppealReview(row)"
          v-hasPermi="['lawyers:quality:review']"
        >申诉复核</el-button>
      </template>
    </ProTable>

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
      <ProTable
        :columns="violationColumns"
        :query-params="violationQuery"
        :data="violations"
        :show-search="false"
        :show-toolbar="false"
        :stripe="false"
        :table-attrs="{ border: true, maxHeight: '180px' }"
        hide-pagination
        persist-key="quality:violation"
        empty-text="未命中违禁/敏感/情绪问题"
      />

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

    <!-- ==================== 坐席申诉对话框 ==================== -->
    <el-dialog title="发起质检申诉" :visible.sync="appealOpen" width="520px" append-to-body>
      <el-form ref="appealForm" :model="appealForm" :rules="appealRules" label-width="90px">
        <el-form-item label="质检ID">
          <span>{{ appealForm.inspectionId }}（AI总分：{{ appealForm.totalScore }}）</span>
        </el-form-item>
        <el-form-item label="申诉理由" prop="appealReason">
          <el-input v-model="appealForm.appealReason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请说明对评分有异议的具体原因（仅本人被检记录可申诉）" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitAppeal">提 交</el-button>
        <el-button @click="appealOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- ==================== 班组长申诉复核对话框 ==================== -->
    <el-dialog title="申诉复核" :visible.sync="appealReviewOpen" width="560px" append-to-body>
      <el-form ref="appealReviewForm" :model="appealReviewForm" :rules="appealReviewRules" label-width="100px">
        <el-form-item label="质检ID">
          <span>{{ appealReviewForm.inspectionId }}（坐席：{{ appealReviewForm.agentName || appealReviewForm.agentId }}）</span>
        </el-form-item>
        <el-form-item label="原始AI总分">
          <el-tag :type="scoreTagType(appealReviewForm.totalScore)" size="small">{{ appealReviewForm.totalScore }}</el-tag>
        </el-form-item>
        <el-form-item label="申诉理由">
          <div class="transcript-box">{{ appealReviewForm.appealReason }}</div>
        </el-form-item>
        <el-form-item label="复核结果" prop="appealStatus">
          <el-radio-group v-model="appealReviewForm.appealStatus">
            <el-radio label="2">维持原分</el-radio>
            <el-radio label="3">改分</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="appealReviewForm.appealStatus === '3'" label="调整后分数" prop="adjustedScore">
          <el-input-number v-model="appealReviewForm.adjustedScore" :min="0" :max="100" :precision="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="复核意见" prop="appealReviewRemark">
          <el-input v-model="appealReviewForm.appealReviewRemark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请输入申诉复核意见" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitAppealReview">确 定</el-button>
        <el-button @click="appealReviewOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- ==================== 质检模板管理对话框 ==================== -->
    <el-dialog title="质检模板管理" :visible.sync="templateOpen" width="900px" append-to-body>
      <ProTable
        :columns="templateColumns"
        :query-params="templateQuery"
        :data="templateList"
        :loading="templateLoading"
        :show-search="false"
        :show-toolbar="false"
        :stripe="false"
        :table-attrs="{ border: true, maxHeight: '360px' }"
        hide-pagination
        persist-key="quality:template"
        empty-text="暂无质检模板"
        empty-hint="新增模板后可设为默认质检模板">
        <template #toolbar-left>
          <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAddTemplate">新增模板</el-button>
        </template>
        <template #column-dimensions="{ row }">
          <el-tag v-for="d in safeJsonArray(row.dimensions)" :key="d.key" size="mini" class="dim-tag">
            {{ dimName(d.key) }} {{ d.weight }}
          </el-tag>
        </template>
        <template #column-isDefault="{ row }">
          <el-tag v-if="row.isDefault === '1'" type="success" size="mini">默认</el-tag>
          <span v-else>-</span>
        </template>
        <template #column-status="{ row }">
          <el-tag v-if="row.status == '1'" type="success" size="mini">启用</el-tag>
          <el-tag v-else type="info" size="mini">停用</el-tag>
        </template>
        <template #column-action="{ row }">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEditTemplate(row)">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-star-off" :disabled="row.isDefault === '1'" @click="handleSetDefault(row)">设默认</el-button>
        </template>
      </ProTable>

      <!-- 模板新增/编辑 -->
      <el-dialog
        :title="templateForm.templateId ? '编辑质检模板' : '新增质检模板'"
        :visible.sync="templateFormOpen"
        width="640px"
        append-to-body
      >
        <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-width="100px">
          <el-form-item label="模板名称" prop="templateName">
            <el-input v-model="templateForm.templateName" placeholder="请输入模板名称" />
          </el-form-item>
          <el-form-item label="维度权重" prop="dimensions">
            <div v-for="(item, idx) in templateForm.dimensions" :key="idx" class="dim-row">
              <el-select v-model="item.key" placeholder="选择维度" style="width: 180px">
                <el-option label="服务规范" value="serviceNorm" />
                <el-option label="答复准确" value="answerAccuracy" />
                <el-option label="情绪态度" value="emotionAttitude" />
                <el-option label="合规话术" value="compliance" />
              </el-select>
              <el-input-number v-model="item.weight" :min="1" :max="100" :precision="0" controls-position="right" class="dim-weight" />
              <el-button type="danger" icon="el-icon-delete" circle size="mini" @click="removeDimension(idx)"></el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addDimension">添加维度</el-button>
            <div class="dim-tip">权重为各维度相对分值，维度不可重复</div>
          </el-form-item>
          <el-form-item label="评分提示词" prop="scorePrompt">
            <el-input v-model="templateForm.scorePrompt" type="textarea" :rows="4" placeholder="供 LLM 评分参考的提示词（可空）" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="templateForm.status">
              <el-radio label="1">启用</el-radio>
              <el-radio label="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" @click="submitTemplate">确 定</el-button>
          <el-button @click="templateFormOpen = false">取 消</el-button>
        </div>
      </el-dialog>

      <div slot="footer" class="dialog-footer">
        <el-button @click="templateOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listQuality,
  getQuality,
  reviewQuality,
  inspectRecord,
  exportQuality,
  appealQuality,
  reviewAppeal,
  listQualityTemplate,
  addQualityTemplate,
  updateQualityTemplate,
  setDefaultQualityTemplate
} from "@/api/lawyers/quality"

export default {
  name: "QualityInspection",
  data() {
    return {
      loading: true,
      loadError: false,
      showSearch: true,
      total: 0,
      qualityList: [],
      // V3：质检列表列定义（坐席/总分/AI状态/风险预警/复核/申诉/时间/操作走插槽，移除原 fixed="right"）
      columns: [
        { prop: 'inspectionId', label: '质检ID', width: 80 },
        { prop: 'agentName', label: '坐席', minWidth: 100, slot: true },
        { prop: 'callerNumber', label: '主叫号码', width: 130, showOverflowTooltip: true },
        { prop: 'recordId', label: '话单ID', width: 90 },
        { prop: 'totalScore', label: 'AI总分', width: 90, slot: true },
        { prop: 'aiStatus', label: 'AI状态', width: 90, slot: true },
        { prop: 'riskWarningId', label: '风险预警', width: 90, slot: true },
        { prop: 'reviewStatus', label: '复核状态', width: 100, slot: true },
        { prop: 'appealStatus', label: '申诉状态', width: 100, slot: true },
        { prop: 'reviewerName', label: '复核人', width: 90 },
        { prop: 'createTime', label: '质检时间', width: 160, slot: true },
        { prop: 'action', label: '操作', width: 280, slot: true, className: 'small-padding fixed-width' }
      ],
      // 详情弹窗「命中问题」表列定义
      violationColumns: [
        { prop: 'keyword', label: '命中关键词/问题', minWidth: 160, showOverflowTooltip: true },
        { prop: 'reason', label: '原因说明', minWidth: 260, showOverflowTooltip: true }
      ],
      // 质检模板表列定义（维度权重/默认/状态/操作走插槽）
      templateColumns: [
        { prop: 'templateId', label: 'ID', width: 60 },
        { prop: 'templateName', label: '模板名称', minWidth: 150, showOverflowTooltip: true },
        { prop: 'dimensions', label: '维度权重', minWidth: 240, slot: true },
        { prop: 'isDefault', label: '默认', width: 70, slot: true },
        { prop: 'status', label: '状态', width: 80, slot: true },
        { prop: 'action', label: '操作', width: 170, slot: true, className: 'small-padding fixed-width' }
      ],
      // 弹窗内小表无查询/分页，仅为满足 ProTable 的 queryParams 契约
      violationQuery: { pageNum: 1, pageSize: 10 },
      templateQuery: { pageNum: 1, pageSize: 10 },
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
      },
      // 坐席申诉
      appealOpen: false,
      appealForm: {},
      appealRules: {
        appealReason: [{ required: true, message: "请输入申诉理由", trigger: "blur" }]
      },
      // 申诉复核
      appealReviewOpen: false,
      appealReviewForm: {},
      appealReviewRules: {
        appealStatus: [{ required: true, message: "请选择复核结果", trigger: "change" }],
        adjustedScore: [{ required: true, message: "请输入调整后分数", trigger: "blur" }]
      },
      // 质检模板
      templateOpen: false,
      templateLoading: false,
      templateList: [],
      templateFormOpen: false,
      templateForm: { dimensions: [] },
      templateRules: {
        templateName: [{ required: true, message: "请输入模板名称", trigger: "blur" }],
        dimensions: [{ required: true, type: "array", validator: this.validateDimensions, trigger: "change" }],
        status: [{ required: true, message: "请选择状态", trigger: "change" }]
      }
    }
  },
  computed: {
    /** V3：ProTable 查询项（AI状态/复核状态字典化下拉，质检时间走查询插槽） */
    queryFormItems() {
      return [
        { prop: 'agentId', label: '坐席ID', type: 'input', placeholder: '请输入坐席ID', width: 150 },
        { prop: 'callerNumber', label: '主叫号码', type: 'input', placeholder: '请输入主叫号码', width: 180 },
        {
          prop: 'aiStatus', label: 'AI状态', type: 'select', placeholder: '全部', width: 140,
          options: [
            { label: '待检', value: '0' },
            { label: '检中', value: '1' },
            { label: '完成', value: '2' },
            { label: '失败', value: '3' }
          ]
        },
        {
          prop: 'reviewStatus', label: '复核状态', type: 'select', placeholder: '全部', width: 140,
          options: [
            { label: '未复核', value: '0' },
            { label: '通过', value: '1' },
            { label: '驳回整改', value: '2' }
          ]
        },
        { prop: 'qualityDateRange', label: '质检时间', slot: true }
      ]
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询质检列表 */
    getList() {
      this.loading = true
      this.loadError = false
      if (this.dateRange && this.dateRange.length === 2) {
        this.queryParams.params = { beginTime: this.dateRange[0], endTime: this.dateRange[1] }
      } else {
        this.queryParams.params = {}
      }
      listQuality(this.queryParams).then(response => {
        this.qualityList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => {
        // V2 三态：失败不再永久卡 loading，给出统一错误重试态
        this.loading = false
        this.loadError = true
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
      this.$modal.dangerConfirm({
        actionType: 'caution',
        content: '将对话单（recordId=' + row.recordId + '）重新执行 AI 质检',
        tip: '将覆盖该话单已有质检结果，并产生模型调用费用'
      }).then(() => {
        return inspectRecord(row.recordId)
      }).then(() => {
        this.$modal.msgSuccess("已触发质检，稍后刷新查看结果")
        this.getList()
      }).catch(() => {})
    },
    /** 坐席发起申诉 */
    handleAppeal(row) {
      this.appealForm = {
        inspectionId: row.inspectionId,
        totalScore: row.totalScore,
        appealReason: undefined
      }
      this.appealOpen = true
      this.$nextTick(() => this.$refs.appealForm && this.$refs.appealForm.clearValidate())
    },
    submitAppeal() {
      this.$refs.appealForm.validate(valid => {
        if (!valid) return
        appealQuality(this.appealForm.inspectionId, this.appealForm.appealReason).then(() => {
          this.$modal.msgSuccess("申诉已提交，等待班组长复核")
          this.appealOpen = false
          this.getList()
        })
      })
    },
    /** 班组长申诉复核 */
    handleAppealReview(row) {
      this.getQualityFull(row.inspectionId).then(data => {
        this.appealReviewForm = {
          inspectionId: data.inspectionId,
          agentId: data.agentId,
          agentName: data.agentName,
          totalScore: data.totalScore,
          appealReason: data.appealReason,
          appealStatus: '2',
          adjustedScore: data.adjustedScore != null ? data.adjustedScore : data.totalScore,
          appealReviewRemark: data.appealReviewRemark
        }
        this.appealReviewOpen = true
        this.$nextTick(() => this.$refs.appealReviewForm && this.$refs.appealReviewForm.clearValidate())
      })
    },
    getQualityFull(id) {
      return getQuality(id).then(response => response.data || {})
    },
    submitAppealReview() {
      this.$refs.appealReviewForm.validate(valid => {
        if (!valid) return
        const form = { ...this.appealReviewForm }
        if (form.appealStatus === '2') {
          form.adjustedScore = null
        }
        reviewAppeal(form).then(() => {
          this.$modal.msgSuccess("申诉复核完成")
          this.appealReviewOpen = false
          this.getList()
        })
      })
    },
    /** 模板管理 */
    handleTemplate() {
      this.templateOpen = true
      this.getTemplateList()
    },
    getTemplateList() {
      this.templateLoading = true
      listQualityTemplate({ pageNum: 1, pageSize: 100 }).then(response => {
        this.templateList = response.data || []
        this.templateLoading = false
      }).catch(() => { this.templateLoading = false })
    },
    resetTemplateForm() {
      return { templateId: undefined, templateName: undefined, dimensions: [], scorePrompt: undefined, status: '1' }
    },
    handleAddTemplate() {
      this.templateForm = this.resetTemplateForm()
      this.addDimension()
      this.templateFormOpen = true
      this.$nextTick(() => this.$refs.templateFormRef && this.$refs.templateFormRef.clearValidate())
    },
    handleEditTemplate(row) {
      const dims = this.safeJsonArray(row.dimensions).map(d => ({ key: d.key, weight: Number(d.weight) }))
      this.templateForm = {
        templateId: row.templateId,
        templateName: row.templateName,
        dimensions: dims,
        scorePrompt: row.scorePrompt,
        status: row.status
      }
      this.templateFormOpen = true
      this.$nextTick(() => this.$refs.templateFormRef && this.$refs.templateFormRef.clearValidate())
    },
    addDimension() {
      this.templateForm.dimensions.push({ key: undefined, weight: 100 })
    },
    removeDimension(idx) {
      this.templateForm.dimensions.splice(idx, 1)
    },
    validateDimensions(rule, value, callback) {
      if (!value || !value.length) {
        return callback(new Error("请至少添加一个维度"))
      }
      const keys = value.map(d => d.key)
      if (keys.some(k => !k)) {
        return callback(new Error("请选择维度"))
      }
      if (new Set(keys).size !== keys.length) {
        return callback(new Error("维度不可重复"))
      }
      if (value.some(d => !Number(d.weight) || Number(d.weight) <= 0)) {
        return callback(new Error("权重须大于 0"))
      }
      callback()
    },
    submitTemplate() {
      this.$refs.templateFormRef.validate(valid => {
        if (!valid) return
        const data = {
          ...this.templateForm,
          dimensions: this.templateForm.dimensions.map(d => ({
            key: d.key,
            name: this.dimName(d.key),
            weight: Number(d.weight)
          }))
        }
        const action = data.templateId ? updateQualityTemplate(data) : addQualityTemplate(data)
        action.then(() => {
          this.$modal.msgSuccess(data.templateId ? "修改成功" : "新增成功")
          this.templateFormOpen = false
          this.getTemplateList()
        })
      })
    },
    handleSetDefault(row) {
      this.$modal.dangerConfirm({
        actionType: 'publish',
        content: '将模板「' + row.templateName + '」设为默认质检模板',
        tip: '后续质检默认使用该模板，进行中的质检任务不受影响'
      }).then(() => {
        return setDefaultQualityTemplate(row.templateId)
      }).then(() => {
        this.$modal.msgSuccess("设置成功")
        this.getTemplateList()
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
      // ProTable 已复位查询表单，这里只清空日期区间并重设查询参数后重查（避免重复 resetForm）
      this.dateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        agentId: undefined,
        callerNumber: undefined,
        aiStatus: undefined,
        reviewStatus: undefined,
        params: {}
      }
      this.getList()
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
.dim-tag { margin: 2px 4px 2px 0; }
.dim-row { margin-bottom: 8px; display: flex; align-items: center; }
.dim-weight { margin: 0 10px; }
.dim-tip { color: #909399; font-size: 12px; line-height: 1.4; }
</style>
