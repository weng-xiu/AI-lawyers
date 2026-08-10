<template>
  <div class="app-container">
    <el-tabs v-model="activeTab" type="card" @tab-click="handleTabClick">
      <!-- ==================== 预警记录 Tab ==================== -->
      <el-tab-pane label="预警记录" name="warning">
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="预警类型" prop="warningType">
            <el-select v-model="queryParams.warningType" placeholder="请选择预警类型" clearable style="width: 200px">
              <el-option label="敏感词" value="敏感词" />
              <el-option label="情绪异常" value="情绪异常" />
              <el-option label="异常行为" value="异常行为" />
              <el-option label="合规风险" value="合规风险" />
            </el-select>
          </el-form-item>
          <el-form-item label="预警级别" prop="warningLevel">
            <el-select v-model="queryParams.warningLevel" placeholder="请选择预警级别" clearable style="width: 200px">
              <el-option label="高" value="1" />
              <el-option label="中" value="2" />
              <el-option label="低" value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="请选择处理状态" clearable style="width: 200px">
              <el-option label="待处理" value="0" />
              <el-option label="处理中" value="1" />
              <el-option label="已处理" value="2" />
              <el-option label="已忽略" value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="触发时间">
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
              type="primary"
              plain
              icon="el-icon-plus"
              size="mini"
              @click="handleAdd"
              v-hasPermi="['lawyers:riskWarning:add']"
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
              v-hasPermi="['lawyers:riskWarning:edit']"
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
              v-hasPermi="['lawyers:riskWarning:remove']"
            >删除</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="warning"
              plain
              icon="el-icon-download"
              size="mini"
              @click="handleExport"
              v-hasPermi="['lawyers:riskWarning:export']"
            >导出</el-button>
          </el-col>
          <right-toolbar :showSearch.sync="showSearch" @queryTable="getWarningList"></right-toolbar>
        </el-row>

        <el-table v-loading="loading" :data="warningList" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="预警ID" align="center" prop="warningId" width="80" />
          <el-table-column label="预警类型" align="center" prop="warningType" width="100">
            <template slot-scope="scope">
              <el-tag :type="scope.row.warningType === '敏感词' ? 'danger' : scope.row.warningType === '情绪异常' ? 'warning' : scope.row.warningType === '异常行为' ? '' : 'success'" size="small">{{ scope.row.warningType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="预警级别" align="center" prop="warningLevel" width="80">
            <template slot-scope="scope">
              <el-tag :type="scope.row.warningLevel == 1 ? 'danger' : scope.row.warningLevel == 2 ? 'warning' : 'info'" size="small">{{ scope.row.warningLevel == 1 ? '高' : scope.row.warningLevel == 2 ? '中' : '低' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源类型" align="center" prop="sourceType" width="90" />
          <el-table-column label="客户姓名" align="center" prop="customerName" width="100" />
          <el-table-column label="触发内容" align="center" prop="content" min-width="200" :show-overflow-tooltip="true" />
          <el-table-column label="处理状态" align="center" prop="status" width="90">
            <template slot-scope="scope">
              <el-tag v-if="scope.row.status == 0" type="danger" size="small">待处理</el-tag>
              <el-tag v-else-if="scope.row.status == 1" type="warning" size="small">处理中</el-tag>
              <el-tag v-else-if="scope.row.status == 2" type="success" size="small">已处理</el-tag>
              <el-tag v-else-if="scope.row.status == 3" type="info" size="small">已忽略</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理人" align="center" prop="handlerName" width="90" />
          <el-table-column label="触发时间" align="center" prop="triggerTime" width="180">
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.triggerTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="200">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-view"
                @click="handleDetail(scope.row)"
                v-hasPermi="['lawyers:riskWarning:query']"
              >详情</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-edit"
                @click="handleUpdate(scope.row)"
                v-hasPermi="['lawyers:riskWarning:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleDelete(scope.row)"
                v-hasPermi="['lawyers:riskWarning:remove']"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="total>0"
          :total="total"
          :page.sync="queryParams.pageNum"
          :limit.sync="queryParams.pageSize"
          @pagination="getWarningList"
        />

        <!-- 添加或修改预警记录对话框 -->
        <el-dialog :title="title" :visible.sync="open" width="700px" append-to-body>
          <el-form ref="form" :model="form" :rules="rules" label-width="80px">
            <el-form-item label="预警类型" prop="warningType">
              <el-select v-model="form.warningType" placeholder="请选择预警类型">
                <el-option label="敏感词" value="敏感词" />
                <el-option label="情绪异常" value="情绪异常" />
                <el-option label="异常行为" value="异常行为" />
                <el-option label="合规风险" value="合规风险" />
              </el-select>
            </el-form-item>
            <el-form-item label="预警级别" prop="warningLevel">
              <el-select v-model="form.warningLevel" placeholder="请选择预警级别">
                <el-option label="高" value="1" />
                <el-option label="中" value="2" />
                <el-option label="低" value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源类型" prop="sourceType">
              <el-select v-model="form.sourceType" placeholder="请选择来源类型">
                <el-option label="通话" value="通话" />
                <el-option label="图文" value="图文" />
                <el-option label="视频" value="视频" />
              </el-select>
            </el-form-item>
            <el-form-item label="来源ID" prop="sourceId">
              <el-input v-model="form.sourceId" placeholder="请输入来源ID" />
            </el-form-item>
            <el-form-item label="客户姓名" prop="customerName">
              <el-input v-model="form.customerName" placeholder="请输入客户姓名" />
            </el-form-item>
            <el-form-item label="触发内容" prop="content">
              <el-input v-model="form.content" type="textarea" placeholder="请输入触发内容" :rows="4" />
            </el-form-item>
            <el-form-item label="处理状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择处理状态">
                <el-option label="待处理" value="0" />
                <el-option label="处理中" value="1" />
                <el-option label="已处理" value="2" />
                <el-option label="已忽略" value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理人" prop="handlerName">
              <el-input v-model="form.handlerName" placeholder="请输入处理人姓名" />
            </el-form-item>
            <el-form-item label="处理结果" prop="handleResult">
              <el-input v-model="form.handleResult" type="textarea" placeholder="请输入处理结果" />
            </el-form-item>
            <el-form-item label="处理时间" prop="handleTime">
              <el-date-picker clearable
                v-model="form.handleTime"
                type="datetime"
                value-format="yyyy-MM-dd HH:mm:ss"
                placeholder="请选择处理时间">
              </el-date-picker>
            </el-form-item>
            <el-form-item label="触发时间" prop="triggerTime">
              <el-date-picker clearable
                v-model="form.triggerTime"
                type="datetime"
                value-format="yyyy-MM-dd HH:mm:ss"
                placeholder="请选择触发时间">
              </el-date-picker>
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
        <el-dialog title="预警详情" :visible.sync="detailOpen" width="800px" append-to-body>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="预警ID">{{ detailForm.warningId }}</el-descriptions-item>
            <el-descriptions-item label="预警类型">
              <el-tag :type="detailForm.warningType === '敏感词' ? 'danger' : detailForm.warningType === '情绪异常' ? 'warning' : detailForm.warningType === '异常行为' ? '' : 'success'" size="small">{{ detailForm.warningType }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="预警级别">
              <el-tag :type="detailForm.warningLevel == 1 ? 'danger' : detailForm.warningLevel == 2 ? 'warning' : 'info'" size="small">{{ detailForm.warningLevel == 1 ? '高' : detailForm.warningLevel == 2 ? '中' : '低' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="来源类型">{{ detailForm.sourceType }}</el-descriptions-item>
            <el-descriptions-item label="来源ID">{{ detailForm.sourceId }}</el-descriptions-item>
            <el-descriptions-item label="客户姓名">{{ detailForm.customerName }}</el-descriptions-item>
            <el-descriptions-item label="处理状态">
              <el-tag v-if="detailForm.status == 0" type="danger" size="small">待处理</el-tag>
              <el-tag v-else-if="detailForm.status == 1" type="warning" size="small">处理中</el-tag>
              <el-tag v-else-if="detailForm.status == 2" type="success" size="small">已处理</el-tag>
              <el-tag v-else-if="detailForm.status == 3" type="info" size="small">已忽略</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detailForm.handlerName }}</el-descriptions-item>
            <el-descriptions-item label="触发时间">{{ parseTime(detailForm.triggerTime) }}</el-descriptions-item>
            <el-descriptions-item label="处理时间">{{ parseTime(detailForm.handleTime) }}</el-descriptions-item>
            <el-descriptions-item label="触发内容" :span="2">
              <div style="max-height: 200px; overflow-y: auto;">{{ detailForm.content }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="处理结果" :span="2">{{ detailForm.handleResult }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
          </el-descriptions>
          <div slot="footer" class="dialog-footer">
            <el-button @click="detailOpen = false">关 闭</el-button>
          </div>
        </el-dialog>
      </el-tab-pane>

      <!-- ==================== 预警规则 Tab ==================== -->
      <el-tab-pane label="预警规则" name="rule">
        <el-form :model="ruleQueryParams" ref="ruleQueryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input
              v-model="ruleQueryParams.ruleName"
              placeholder="请输入规则名称"
              clearable
              style="width: 200px"
              @keyup.enter.native="handleRuleQuery"
            />
          </el-form-item>
          <el-form-item label="规则类型" prop="ruleType">
            <el-select v-model="ruleQueryParams.ruleType" placeholder="请选择规则类型" clearable style="width: 200px">
              <el-option label="敏感词" value="敏感词" />
              <el-option label="情绪异常" value="情绪异常" />
              <el-option label="异常行为" value="异常行为" />
              <el-option label="合规风险" value="合规风险" />
            </el-select>
          </el-form-item>
          <el-form-item label="规则级别" prop="ruleLevel">
            <el-select v-model="ruleQueryParams.ruleLevel" placeholder="请选择规则级别" clearable style="width: 200px">
              <el-option label="高" value="1" />
              <el-option label="中" value="2" />
              <el-option label="低" value="3" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="el-icon-search" size="mini" @click="handleRuleQuery">搜索</el-button>
            <el-button icon="el-icon-refresh" size="mini" @click="resetRuleQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button
              type="primary"
              plain
              icon="el-icon-plus"
              size="mini"
              @click="handleRuleAdd"
              v-hasPermi="['lawyers:riskWarning:add']"
            >新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="success"
              plain
              icon="el-icon-edit"
              size="mini"
              :disabled="ruleSingle"
              @click="handleRuleUpdate"
              v-hasPermi="['lawyers:riskWarning:edit']"
            >修改</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button
              type="danger"
              plain
              icon="el-icon-delete"
              size="mini"
              :disabled="ruleMultiple"
              @click="handleRuleDelete"
              v-hasPermi="['lawyers:riskWarning:remove']"
            >删除</el-button>
          </el-col>
          <right-toolbar :showSearch.sync="showSearch" @queryTable="getRuleList"></right-toolbar>
        </el-row>

        <el-table v-loading="ruleLoading" :data="ruleList" @selection-change="handleRuleSelectionChange">
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="规则ID" align="center" prop="ruleId" width="80" />
          <el-table-column label="规则名称" align="center" prop="ruleName" min-width="150" :show-overflow-tooltip="true" />
          <el-table-column label="规则类型" align="center" prop="ruleType" width="100">
            <template slot-scope="scope">
              <el-tag :type="scope.row.ruleType === '敏感词' ? 'danger' : scope.row.ruleType === '情绪异常' ? 'warning' : scope.row.ruleType === '异常行为' ? '' : 'success'" size="small">{{ scope.row.ruleType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="规则级别" align="center" prop="ruleLevel" width="80">
            <template slot-scope="scope">
              <el-tag :type="scope.row.ruleLevel == 1 ? 'danger' : scope.row.ruleLevel == 2 ? 'warning' : 'info'" size="small">{{ scope.row.ruleLevel == 1 ? '高' : scope.row.ruleLevel == 2 ? '中' : '低' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关键词" align="center" prop="keywords" min-width="200" :show-overflow-tooltip="true" />
          <el-table-column label="是否启用" align="center" prop="isEnabled" width="90">
            <template slot-scope="scope">
              <el-tag :type="scope.row.isEnabled == 1 ? 'success' : 'info'" size="small">{{ scope.row.isEnabled == 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" align="center" prop="createTime" width="180">
            <template slot-scope="scope">
              <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="150">
            <template slot-scope="scope">
              <el-button
                size="mini"
                type="text"
                icon="el-icon-edit"
                @click="handleRuleUpdate(scope.row)"
                v-hasPermi="['lawyers:riskWarning:edit']"
              >修改</el-button>
              <el-button
                size="mini"
                type="text"
                icon="el-icon-delete"
                @click="handleRuleDelete(scope.row)"
                v-hasPermi="['lawyers:riskWarning:remove']"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="ruleTotal>0"
          :total="ruleTotal"
          :page.sync="ruleQueryParams.pageNum"
          :limit.sync="ruleQueryParams.pageSize"
          @pagination="getRuleList"
        />

        <!-- 添加或修改预警规则对话框 -->
        <el-dialog :title="ruleTitle" :visible.sync="ruleOpen" width="600px" append-to-body>
          <el-form ref="ruleForm" :model="ruleForm" :rules="ruleRules" label-width="80px">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="ruleForm.ruleName" placeholder="请输入规则名称" />
            </el-form-item>
            <el-form-item label="规则类型" prop="ruleType">
              <el-select v-model="ruleForm.ruleType" placeholder="请选择规则类型">
                <el-option label="敏感词" value="敏感词" />
                <el-option label="情绪异常" value="情绪异常" />
                <el-option label="异常行为" value="异常行为" />
                <el-option label="合规风险" value="合规风险" />
              </el-select>
            </el-form-item>
            <el-form-item label="规则级别" prop="ruleLevel">
              <el-select v-model="ruleForm.ruleLevel" placeholder="请选择规则级别">
                <el-option label="高" value="1" />
                <el-option label="中" value="2" />
                <el-option label="低" value="3" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词" prop="keywords">
              <el-input v-model="ruleForm.keywords" type="textarea" placeholder="请输入关键词，多个关键词用逗号分隔" :rows="3" />
            </el-form-item>
            <el-form-item label="是否启用" prop="isEnabled">
              <el-radio-group v-model="ruleForm.isEnabled">
                <el-radio label="1">启用</el-radio>
                <el-radio label="0">禁用</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input v-model="ruleForm.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-form>
          <div slot="footer" class="dialog-footer">
            <el-button type="primary" @click="submitRuleForm">确 定</el-button>
            <el-button @click="cancelRule">取 消</el-button>
          </div>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import { listRiskWarning, getRiskWarning, addRiskWarning, updateRiskWarning, delRiskWarning, exportRiskWarning } from "@/api/lawyers/riskWarning"
import { listRiskWarningRule, getRiskWarningRule, addRiskWarningRule, updateRiskWarningRule, delRiskWarningRule } from "@/api/lawyers/riskWarning"

export default {
  name: "RiskWarning",
  data() {
    return {
      // 当前激活Tab
      activeTab: "warning",
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
      // 风险预警记录表格数据
      warningList: [],
      // 日期范围
      dateRange: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详情弹出层
      detailOpen: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        warningType: undefined,
        warningLevel: undefined,
        sourceType: undefined,
        status: undefined,
        customerName: undefined
      },
      // 表单参数
      form: {},
      // 详情表单
      detailForm: {},
      // 表单校验
      rules: {
        warningType: [
          { required: true, message: "预警类型不能为空", trigger: "change" }
        ],
        warningLevel: [
          { required: true, message: "预警级别不能为空", trigger: "change" }
        ],
        status: [
          { required: true, message: "处理状态不能为空", trigger: "change" }
        ]
      },

      // ===== 预警规则 =====
      ruleLoading: true,
      ruleIds: [],
      ruleSingle: true,
      ruleMultiple: true,
      ruleTotal: 0,
      ruleList: [],
      ruleTitle: "",
      ruleOpen: false,
      ruleQueryParams: {
        pageNum: 1,
        pageSize: 10,
        ruleName: undefined,
        ruleType: undefined,
        ruleLevel: undefined
      },
      ruleForm: {},
      ruleRules: {
        ruleName: [
          { required: true, message: "规则名称不能为空", trigger: "blur" }
        ],
        ruleType: [
          { required: true, message: "规则类型不能为空", trigger: "change" }
        ],
        ruleLevel: [
          { required: true, message: "规则级别不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getWarningList()
  },
  methods: {
    /** Tab切换 */
    handleTabClick(tab) {
      if (tab.name === 'rule') {
        this.getRuleList()
      } else {
        this.getWarningList()
      }
    },

    // ==================== 预警记录方法 ====================

    /** 查询风险预警记录列表 */
    getWarningList() {
      this.loading = true
      if (this.dateRange && this.dateRange.length === 2) {
        this.queryParams.params = {
          beginTime: this.dateRange[0],
          endTime: this.dateRange[1]
        }
      } else {
        this.queryParams.params = {}
      }
      listRiskWarning(this.queryParams).then(response => {
        this.warningList = response.rows
        this.total = response.total
        this.loading = false
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
        warningId: undefined,
        warningType: undefined,
        warningLevel: undefined,
        sourceType: undefined,
        sourceId: undefined,
        content: undefined,
        customerName: undefined,
        status: undefined,
        handlerName: undefined,
        handleResult: undefined,
        handleTime: undefined,
        triggerTime: undefined,
        remark: undefined
      }
      this.resetForm("form")
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getWarningList()
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.dateRange = []
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加风险预警记录"
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.warningId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const warningId = row.warningId || this.ids
      getRiskWarning(warningId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改风险预警记录"
      })
    },
    /** 查看详情按钮操作 */
    handleDetail(row) {
      getRiskWarning(row.warningId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.warningId != undefined) {
            updateRiskWarning(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getWarningList()
            })
          } else {
            addRiskWarning(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getWarningList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const warningIds = row.warningId || this.ids
      this.$modal.confirm('是否确认删除风险预警记录编号为"' + warningIds + '"的数据项？').then(function() {
        return delRiskWarning(warningIds)
      }).then(() => {
        this.getWarningList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      if (this.dateRange && this.dateRange.length === 2) {
        this.queryParams.params = {
          beginTime: this.dateRange[0],
          endTime: this.dateRange[1]
        }
      }
      this.download('lawyers/riskWarning/export', {
        ...this.queryParams
      }, `riskWarning_${new Date().getTime()}.xlsx`)
    },

    // ==================== 预警规则方法 ====================

    /** 查询预警规则列表 */
    getRuleList() {
      this.ruleLoading = true
      listRiskWarningRule(this.ruleQueryParams).then(response => {
        this.ruleList = response.rows
        this.ruleTotal = response.total
        this.ruleLoading = false
      })
    },
    // 取消按钮
    cancelRule() {
      this.ruleOpen = false
      this.resetRule()
    },
    // 表单重置
    resetRule() {
      this.ruleForm = {
        ruleId: undefined,
        ruleName: undefined,
        ruleType: undefined,
        ruleLevel: undefined,
        keywords: undefined,
        isEnabled: '1',
        remark: undefined
      }
      this.resetForm("ruleForm")
    },
    /** 搜索按钮操作 */
    handleRuleQuery() {
      this.ruleQueryParams.pageNum = 1
      this.getRuleList()
    },
    /** 重置按钮操作 */
    resetRuleQuery() {
      this.resetForm("ruleQueryForm")
      this.handleRuleQuery()
    },
    /** 新增按钮操作 */
    handleRuleAdd() {
      this.resetRule()
      this.ruleOpen = true
      this.ruleTitle = "添加预警规则"
    },
    // 多选框选中数据
    handleRuleSelectionChange(selection) {
      this.ruleIds = selection.map(item => item.ruleId)
      this.ruleSingle = selection.length != 1
      this.ruleMultiple = !selection.length
    },
    /** 修改按钮操作 */
    handleRuleUpdate(row) {
      this.resetRule()
      const ruleId = row.ruleId || this.ruleIds
      getRiskWarningRule(ruleId).then(response => {
        this.ruleForm = response.data
        this.ruleOpen = true
        this.ruleTitle = "修改预警规则"
      })
    },
    /** 提交按钮 */
    submitRuleForm: function() {
      this.$refs["ruleForm"].validate(valid => {
        if (valid) {
          if (this.ruleForm.ruleId != undefined) {
            updateRiskWarningRule(this.ruleForm).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.ruleOpen = false
              this.getRuleList()
            })
          } else {
            addRiskWarningRule(this.ruleForm).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.ruleOpen = false
              this.getRuleList()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleRuleDelete(row) {
      const ruleIds = row.ruleId || this.ruleIds
      this.$modal.confirm('是否确认删除预警规则编号为"' + ruleIds + '"的数据项？').then(function() {
        return delRiskWarningRule(ruleIds)
      }).then(() => {
        this.getRuleList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    }
  }
}
</script>
