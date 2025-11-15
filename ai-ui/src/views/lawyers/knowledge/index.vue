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
        <el-button type="primary" @click="submitForm">确 定</el-button>
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
  </div>
</template>

<script>
import { listKnowledge, getKnowledge, delKnowledge, addKnowledge, updateKnowledge, exportKnowledge, importTemplate, importKnowledge, auditKnowledge } from "@/api/lawyers/knowledge"
import { getValidCategories } from "@/api/lawyers/category"

export default {
  name: "Knowledge",
  dicts: ['lawyers_audit_status'],
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
    this.getList()
    this.getCategoryOptions()
  },
  methods: {
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
          if (this.form.knowledgeId != undefined) {
            updateKnowledge(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addKnowledge(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const knowledgeIds = row.knowledgeId || this.ids
      this.$modal.confirm('是否确认删除法律知识库编号为"' + knowledgeIds + '"的数据项？').then(function() {
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