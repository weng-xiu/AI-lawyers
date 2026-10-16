<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="知识标题" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入知识标题"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="知识分类" prop="categoryId">
        <el-select v-model="queryParams.categoryId" placeholder="请选择知识分类" clearable>
          <el-option
            v-for="item in categoryOptions"
            :key="item.categoryId"
            :label="item.categoryName"
            :value="item.categoryId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="关键词" prop="keywords">
        <el-input
          v-model="queryParams.keywords"
          placeholder="请输入关键词"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['lawyers:knowledge:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['lawyers:knowledge:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['lawyers:knowledge:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:knowledge:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-upload2"
          size="mini"
          @click="handleImport"
          v-hasPermi="['lawyers:knowledge:import']"
        >导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-check"
          size="mini"
          :disabled="multiple"
          @click="handleAudit"
          v-hasPermi="['lawyers:knowledge:audit']"
        >审核</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-dropdown trigger="click" @command="handleDraftCommand">
          <el-button type="primary" plain icon="el-icon-magic-stick" size="mini" v-hasPermi="['lawyers:knowledge:add']">
            AI 沉淀<i class="el-icon-arrow-down el-icon--right"></i>
          </el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item icon="el-icon-phone-outline" command="call">从通话记录沉淀</el-dropdown-item>
            <el-dropdown-item icon="el-icon-tickets" command="ticket">从工单沉淀</el-dropdown-item>
            <el-dropdown-item icon="el-icon-files" command="scan">扫描高分通话批量沉淀</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </el-col>
      <el-col :span="1.5">
        <el-dropdown trigger="click" @command="handleRagCommand">
          <el-button type="warning" plain icon="el-icon-cpu" size="mini">
            RAG 运维<i class="el-icon-arrow-down el-icon--right"></i>
          </el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item icon="el-icon-data-line" command="status">
              索引状态：{{ ragInfo.vectorCount === undefined ? '加载中' : ragInfo.vectorCount + ' 条/' + ragInfo.backend + (ragInfo.rerankEnabled ? '/rerank开' : '') }}
            </el-dropdown-item>
            <el-dropdown-item icon="el-icon-refresh" command="rebuild" v-hasPermi="['lawyers:knowledge:edit']">全量重建索引</el-dropdown-item>
            <el-dropdown-item icon="el-icon-connection" command="rerankTest" v-hasPermi="['lawyers:knowledge:edit']">rerank 连通测试</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="knowledgeList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="知识ID" align="center" prop="knowledgeId" />
      <el-table-column label="知识标题" align="center" prop="title" :show-overflow-tooltip="true" />
      <el-table-column label="知识分类" align="center" prop="categoryName" />
      <el-table-column label="关键词" align="center" prop="keywords" :show-overflow-tooltip="true" />
      <el-table-column label="审核状态" align="center" prop="auditStatus">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.lawyers_audit_status" :value="scope.row.auditStatus"/>
        </template>
      </el-table-column>
      <el-table-column label="浏览次数" align="center" prop="viewCount" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-view"
            @click="handleDetail(scope.row)"
            v-hasPermi="['lawyers:knowledge:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:knowledge:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-check"
            @click="handleSingleAudit(scope.row)"
            v-hasPermi="['lawyers:knowledge:audit']"
            v-if="scope.row.auditStatus == 0"
          >审核</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:knowledge:remove']"
          >删除</el-button>
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

    <!-- 添加或修改法律知识库对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="知识标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入知识标题" />
        </el-form-item>
        <el-form-item label="知识分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择知识分类">
            <el-option
              v-for="item in categoryOptions"
              :key="item.categoryId"
              :label="item.categoryName"
              :value="item.categoryId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="请输入关键词，多个关键词用逗号分隔" />
        </el-form-item>
        <el-form-item label="知识内容" prop="content">
          <el-input v-model="form.content" type="textarea" placeholder="请输入知识内容" :rows="10" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="draftSubmitting" @click="submitForm">确 定</el-button>
        <el-button @click="cancel">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog title="知识详情" :visible.sync="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="知识ID">{{ detailForm.knowledgeId }}</el-descriptions-item>
        <el-descriptions-item label="知识标题">{{ detailForm.title }}</el-descriptions-item>
        <el-descriptions-item label="知识分类">{{ detailForm.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="关键词">{{ detailForm.keywords }}</el-descriptions-item>
        <el-descriptions-item label="审核状态">
          <dict-tag :options="dict.type.lawyers_audit_status" :value="detailForm.auditStatus"/>
        </el-descriptions-item>
        <el-descriptions-item label="浏览次数">{{ detailForm.viewCount }}</el-descriptions-item>
        <el-descriptions-item label="知识内容">
          <div v-html="detailForm.content"></div>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ parseTime(detailForm.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 知识导入对话框 -->
    <el-dialog :title="upload.title" :visible.sync="upload.open" width="400px" append-to-body>
      <el-upload
        ref="upload"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url + '?updateSupport=' + upload.updateSupport"
        :disabled="upload.isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleFileSuccess"
        :auto-upload="false"
        drag
      >
        <i class="el-icon-upload"></i>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <div class="el-upload__tip text-center" slot="tip">
          <div class="el-upload__tip" slot="tip">
            <el-checkbox v-model="upload.updateSupport" /> 是否更新已经存在的知识数据
          </div>
          <span>仅允许导入xls、xlsx格式文件。</span>
          <el-link type="primary" :underline="false" style="font-size:12px;vertical-align: baseline;" @click="importTemplate">下载模板</el-link>
        </div>
      </el-upload>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitFileForm">确 定</el-button>
        <el-button @click="upload.open = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog title="知识审核" :visible.sync="auditOpen" width="500px" append-to-body>
      <el-form ref="auditForm" :model="auditForm" :rules="auditRules" label-width="80px">
        <el-form-item label="审核状态" prop="auditStatus">
          <el-radio-group v-model="auditForm.auditStatus">
            <el-radio :label="1">通过</el-radio>
            <el-radio :label="2">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核备注" prop="auditRemark">
          <el-input v-model="auditForm.auditRemark" type="textarea" placeholder="请输入审核备注" :rows="4" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitAuditForm">确 定</el-button>
        <el-button @click="auditOpen = false">取 消</el-button>
      </div>
    </el-dialog>

    <!-- P3-E1 RAG rerank 连通性测试对话框 -->
    <el-dialog title="RAG rerank 连通性测试" :visible.sync="rerankOpen" width="640px" append-to-body>
      <div v-loading="rerankLoading">
        <el-alert
          v-if="rerankResult"
          :title="rerankResult.success ? 'rerank 服务连通正常' : 'rerank 联调失败'"
          :type="rerankResult.success ? 'success' : 'error'"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />
        <el-descriptions v-if="rerankResult" :column="1" border size="small">
          <el-descriptions-item label="启用状态">{{ rerankResult.enabled ? '已启用' : '未启用' }}</el-descriptions-item>
          <el-descriptions-item label="服务地址">{{ rerankResult.url || '（未配置）' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ rerankResult.model }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ rerankResult.latencyMs !== undefined ? rerankResult.latencyMs + ' ms' : '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="rerankResult.httpStatus !== undefined" label="HTTP 状态">{{ rerankResult.httpStatus }}</el-descriptions-item>
          <el-descriptions-item v-if="rerankResult.ranking" label="重排结果">
            <div v-for="(item, idx) in rerankResult.ranking" :key="idx" class="rerank-line">{{ idx + 1 }}. {{ item }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="rerankResult.error" label="错误信息">
            <span class="rerank-error">{{ rerankResult.error }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="rerankResult.raw" label="原始响应（截断）">
            <pre class="rerank-raw">{{ rerankResult.raw }}</pre>
          </el-descriptions-item>
        </el-descriptions>
        <div v-else-if="!rerankLoading" style="color:#909399;font-size:13px;">点击"开始测试"将以固定样例（劳动合同问句）请求一次 rerank 端点。</div>
      </div>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" size="small" :loading="rerankLoading" @click="handleRerankTest">开始测试</el-button>
        <el-button size="small" @click="rerankOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- AI 沉淀：扫描高分通话对话框 -->
    <el-dialog title="扫描高分通话批量沉淀" :visible.sync="scanOpen" width="480px" append-to-body>
      <el-form ref="scanForm" :model="scanForm" :rules="scanRules" label-width="100px">
        <el-form-item label="扫描天数" prop="days">
          <el-input-number v-model="scanForm.days" :min="1" :max="30" controls-position="right" />
          <span style="color:#909399;font-size:12px;margin-left:8px;">近 N 天（1-30）</span>
        </el-form-item>
        <el-form-item label="质量分阈值" prop="minScore">
          <el-input-number v-model="scanForm.minScore" :min="0" :max="100" controls-position="right" />
          <span style="color:#909399;font-size:12px;margin-left:8px;">仅沉淀不低于该分的通话</span>
        </el-form-item>
        <el-form-item label="数量上限" prop="limit">
          <el-input-number v-model="scanForm.limit" :min="1" :max="50" controls-position="right" />
          <span style="color:#909399;font-size:12px;margin-left:8px;">1-50 条</span>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
          title="生成的条目为待审核草稿（创建人 ai-draft），需在知识审核页审核后才会上架。已沉淀过的通话会自动去重。" />
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" :loading="scanLoading" @click="submitScan">开始扫描</el-button>
        <el-button @click="scanOpen = false">取 消</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listKnowledge, getKnowledge, delKnowledge, addKnowledge, updateKnowledge, exportKnowledge, importTemplate, importKnowledge, auditKnowledge, getRagIndexInfo, rebuildRagIndex, testRagRerank, draftFromCall, draftFromTicket, scanDraft } from "@/api/lawyers/knowledge"
import { getValidCategories } from "@/api/lawyers/category"
import { createDraftMixin } from '@/utils/draft'

export default {
  name: "Knowledge",
  dicts: ['lawyers_audit_status'],
  // U4 表单防丢失：新增态自动存草稿（按用户隔离），knowledgeId/viewCount 不入草稿
  mixins: [createDraftMixin({
    key: vm => 'knowledge:' + ((vm.$store && vm.$store.state.user.name) || 'anon'),
    formKey: 'form',
    excludes: ['knowledgeId', 'viewCount'],
    when: vm => vm.form.knowledgeId == undefined
  })],
  data() {
    return {
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 法律知识库表格数据
      knowledgeList: [],
      // 咨询分类选项
      categoryOptions: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详情弹出层
      detailOpen: false,
      // 是否显示审核弹出层
      auditOpen: false,
      // 知识导入参数
      upload: {
        // 是否显示弹出层（知识导入）
        open: false,
        // 弹出层标题（知识导入）
        title: "",
        // 是否禁用上传
        isUploading: false,
        // 是否更新已经存在的知识数据
        updateSupport: 0,
        // 设置上传的请求头部
        headers: { Authorization: "Bearer " + this.$store.state.user.token },
        // 上传的地址
        url: process.env.VUE_APP_BASE_API + "/lawyers/knowledge/importData"
      },
      // 审核表单
      auditForm: {},
      // P3-E1 RAG 运维
      ragInfo: {},
      rerankOpen: false,
      rerankLoading: false,
      rerankResult: null,
      // AI 沉淀
      scanOpen: false,
      scanLoading: false,
      scanForm: { days: 7, minScore: 90, limit: 20 },
      scanRules: {
        days: [{ required: true, message: "扫描天数不能为空", trigger: "blur" }],
        minScore: [{ required: true, message: "质量分阈值不能为空", trigger: "blur" }],
        limit: [{ required: true, message: "数量上限不能为空", trigger: "blur" }]
      },
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        title: undefined,
        categoryId: undefined,
        keywords: undefined
      },
      // 表单参数
      form: {},
      // 详情表单
      detailForm: {},
      // 表单校验
      rules: {
        title: [
          { required: true, message: "知识标题不能为空", trigger: "blur" }
        ],
        categoryId: [
          { required: true, message: "知识分类不能为空", trigger: "change" }
        ],
        content: [
          { required: true, message: "知识内容不能为空", trigger: "blur" }
        ]
      },
      // 审核表单校验
      auditRules: {
        auditStatus: [
          { required: true, message: "审核状态不能为空", trigger: "change" }
        ],
        auditRemark: [
          { required: true, message: "审核备注不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    // U3：命令面板知识直达——预填标题查询
    if (this.$route.query.title) {
      this.queryParams.title = this.$route.query.title
    }
    this.getList()
    this.getCategoryOptions()
    this.fetchRagInfo()
  },
  methods: {
    /** P3-E1 查询 RAG 索引/rerank 状态 */
    fetchRagInfo() {
      getRagIndexInfo().then(response => {
        this.ragInfo = response.data || {}
      }).catch(() => {})
    },
    /** RAG 运维下拉命令分发 */
    handleRagCommand(command) {
      if (command === 'status') {
        this.fetchRagInfo()
        this.$modal.msgSuccess('索引状态已刷新')
      } else if (command === 'rebuild') {
        this.handleRagRebuild()
      } else if (command === 'rerankTest') {
        this.rerankOpen = true
        this.rerankResult = null
      }
    },
    /** 全量重建 RAG 索引（异步，完成后刷新状态） */
    handleRagRebuild() {
      this.$modal.confirm('将在后台全量重建分块与向量索引（embedding 调用可能耗时数分钟），确认提交？').then(() => {
        return rebuildRagIndex()
      }).then(() => {
        this.$modal.msgSuccess('重建任务已提交，可稍后通过"索引状态"确认条目数')
        setTimeout(() => this.fetchRagInfo(), 8000)
      }).catch(() => {})
    },
    /** rerank 连通性测试 */
    handleRerankTest() {
      this.rerankLoading = true
      testRagRerank().then(response => {
        this.rerankResult = response.data
        this.fetchRagInfo()
      }).finally(() => {
        this.rerankLoading = false
      })
    },
    /** AI 沉淀命令分发 */
    handleDraftCommand(command) {
      if (command === 'call') {
        this.handleDraftById('call')
      } else if (command === 'ticket') {
        this.handleDraftById('ticket')
      } else if (command === 'scan') {
        this.scanForm = { days: 7, minScore: 90, limit: 20 }
        this.scanOpen = true
      }
    },
    /** 按通话/工单 ID 沉淀 */
    handleDraftById(type) {
      const isCall = type === 'call'
      this.$prompt(isCall ? '请输入通话记录 ID' : '请输入工单 ID', isCall ? '从通话记录沉淀' : '从工单沉淀', {
        confirmButtonText: '确 定',
        cancelButtonText: '取 消',
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入正确的数字 ID'
      }).then(({ value }) => {
        const run = isCall ? draftFromCall(value) : draftFromTicket(value)
        return run.then(res => {
          this.$modal.msgSuccess(res.msg || '已生成知识草稿，待审核')
          this.getList()
        })
      }).catch(() => {})
    },
    /** 提交批量扫描 */
    submitScan() {
      this.$refs.scanForm.validate(valid => {
        if (!valid) { return }
        this.scanLoading = true
        scanDraft(this.scanForm).then(res => {
          const count = (res.data !== undefined && res.data !== null) ? res.data : ''
          this.$modal.msgSuccess(count !== '' ? `扫描完成，新生成 ${count} 条草稿` : (res.msg || '扫描完成'))
          this.scanOpen = false
          this.getList()
        }).finally(() => { this.scanLoading = false })
      })
    },
    /** 查询法律知识库列表 */
    getList() {
      this.loading = true
      listKnowledge(this.queryParams).then(response => {
          this.knowledgeList = response.rows
          this.total = response.total
          this.loading = false
        }
      )
    },
    /** 查询咨询分类选项 */
    getCategoryOptions() {
      getValidCategories().then(response => {
        this.categoryOptions = response.data
      })
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        knowledgeId: undefined,
        title: undefined,
        categoryId: undefined,
        keywords: undefined,
        content: undefined,
        viewCount: 0,
        remark: undefined
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加法律知识"
      // U4：弹窗打开后询问恢复未提交草稿
      this.draftRestorePrompt()
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.knowledgeId)
      this.single = selection.length!=1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const knowledgeId = row.knowledgeId || this.ids
      getKnowledge(knowledgeId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改法律知识"
      })
    },
    /** 查看详情按钮操作 */
    handleDetail(row) {
      const knowledgeId = row.knowledgeId
      getKnowledge(knowledgeId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          // U4：提交中防重复提交
          if (this.draftSubmitting) return
          this.lockSubmit()
          if (this.form.knowledgeId != undefined) {
            updateKnowledge(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            }).finally(() => this.unlockSubmit())
          } else {
            addKnowledge(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.draftClear()
              this.getList()
            }).finally(() => this.unlockSubmit())
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const knowledgeIds = row.knowledgeId || this.ids
      const count = row.knowledgeId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的法律知识条目',
        count: count,
        countNoun: '条知识',
        tip: '删除后向量索引同步失效，需重建索引才能完全回收检索空间'
      }).then(function() {
        return delKnowledge(knowledgeIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('lawyers/knowledge/export', {
        ...this.queryParams
      }, `knowledge_${new Date().getTime()}.xlsx`)
    },
    /** 导入按钮操作 */
    handleImport() {
      this.upload.title = "知识导入";
      this.upload.open = true;
    },
    /** 下载模板操作 */
    importTemplate() {
      this.download('lawyers/knowledge/importTemplate', {}, `knowledge_template_${new Date().getTime()}.xlsx`)
    },
    // 文件上传中处理
    handleFileUploadProgress(event, file, fileList) {
      this.upload.isUploading = true;
    },
    // 文件上传成功处理
    handleFileSuccess(response, file, fileList) {
      this.upload.open = false;
      this.upload.isUploading = false;
      this.$refs.upload.clearFiles();
      this.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + response.msg + "</div>", "导入结果", { dangerouslyUseHTMLString: true });
      this.getList();
    },
    // 提交上传文件
    submitFileForm() {
      this.$refs.upload.submit();
    },
    /** 单个审核按钮操作 */
    handleSingleAudit(row) {
      this.auditForm = {
        knowledgeId: row.knowledgeId,
        auditStatus: undefined,
        auditRemark: undefined
      };
      this.auditOpen = true;
    },
    /** 批量审核按钮操作 */
    handleAudit() {
      this.auditForm = {
        knowledgeIds: this.ids,
        auditStatus: undefined,
        auditRemark: undefined
      };
      this.auditOpen = true;
    },
    /** 提交审核表单 */
    submitAuditForm() {
      this.$refs["auditForm"].validate(valid => {
        if (valid) {
          auditKnowledge(this.auditForm).then(response => {
            this.$modal.msgSuccess("审核成功");
            this.auditOpen = false;
            this.getList();
          });
        }
      });
    }
  }
}
</script>

<style scoped>
.rerank-line {
  line-height: 1.8;
  font-size: 13px;
}
.rerank-error {
  color: #C63D4A;
  font-size: 13px;
  word-break: break-all;
}
.rerank-raw {
  margin: 0;
  max-height: 180px;
  overflow: auto;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
}
</style>