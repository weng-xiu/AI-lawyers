<template>
  <div class="call-ledger">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="登记时间">
          <el-date-picker
            v-model="dateRange"
            style="width: 240px"
            value-format="yyyy-MM-dd"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          ></el-date-picker>
        </el-form-item>
        <el-form-item label="咨询类型" prop="categoryName">
          <el-input v-model="queryParams.categoryName" placeholder="请输入咨询类型" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="咨询人" prop="callerName">
          <el-input v-model="queryParams.callerName" placeholder="请输入咨询人" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="联系电话" prop="callerPhone">
          <el-input v-model="queryParams.callerPhone" placeholder="请输入联系电话" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="承办律师" prop="lawyerName">
          <el-input v-model="queryParams.lawyerName" placeholder="请输入承办律师" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="服务方式" prop="serviceType">
          <el-select v-model="queryParams.serviceType" placeholder="请选择" clearable style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="电话咨询" value="1" />
            <el-option label="现场咨询" value="2" />
            <el-option label="网络咨询" value="3" />
            <el-option label="视频咨询" value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="满意度" prop="satisfaction">
          <el-select v-model="queryParams.satisfaction" placeholder="请选择" clearable style="width: 150px">
            <el-option label="全部" value="" />
            <el-option label="非常满意" value="1" />
            <el-option label="满意" value="2" />
            <el-option label="一般" value="3" />
            <el-option label="不满意" value="4" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="4">
        <div class="stat-card stat-total">
          <div class="stat-icon"><i class="el-icon-document"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.totalCount }}</div>
            <div class="stat-label">总登记数（条）</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card stat-phone">
          <div class="stat-icon"><i class="el-icon-phone"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.phoneCount }}</div>
            <div class="stat-label">电话咨询（条）</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card stat-site">
          <div class="stat-icon"><i class="el-icon-user"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.siteCount }}</div>
            <div class="stat-label">现场咨询（条）</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card stat-online">
          <div class="stat-icon"><i class="el-icon-monitor"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.onlineCount }}</div>
            <div class="stat-label">网络咨询（条）</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card stat-video">
          <div class="stat-icon"><i class="el-icon-video-camera"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.videoCount }}</div>
            <div class="stat-label">视频咨询（条）</div>
          </div>
        </div>
      </el-col>
      <el-col :span="4">
        <div class="stat-card stat-satisfaction">
          <div class="stat-icon"><i class="el-icon-star-on"></i></div>
          <div class="stat-info">
            <div class="stat-value">{{ statistics.avgSatisfaction }}%</div>
            <div class="stat-label">平均满意度</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-button type="success" icon="el-icon-download" size="mini" @click="handleExport">导出Excel</el-button>
          <el-button type="warning" icon="el-icon-printer" size="mini" @click="handleBatchPrint">批量打印</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" icon="el-icon-plus" size="mini" @click="handleAdd">新增登记</el-button>
        </div>
      </div>

      <!-- 台账模板快捷区 -->
      <div class="template-bar">
        <span class="template-label">台账模板：</span>
        <el-button
          v-for="tpl in templates"
          :key="tpl.templateId"
          size="mini"
          plain
          class="tpl-btn"
          @click="handleApplyTemplate(tpl)"
        >
          <i class="el-icon-document-copy"></i>{{ tpl.templateName }}
        </el-button>
      </div>

      <el-table v-loading="loading" :data="ledgerList" @selection-change="handleSelectionChange" border>
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="登记编号" align="center" prop="ledgerNo" width="160">
          <template slot-scope="scope">
            <span class="link-blue" @click="handleDetail(scope.row)">{{ scope.row.ledgerNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" align="center" prop="createTime" width="170" />
        <el-table-column label="咨询人" align="center" prop="callerName" width="100" />
        <el-table-column label="联系电话" align="center" prop="callerPhone" width="130" />
        <el-table-column label="身份证号" align="center" prop="callerIdCard" width="180" />
        <el-table-column label="咨询类型" align="center" prop="categoryName" width="100">
          <template slot-scope="scope">
            <el-tag :type="getConsultTypeTag(scope.row.categoryName)">{{ scope.row.categoryName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="服务方式" align="center" prop="serviceType" width="100">
          <template slot-scope="scope">
            <el-tag :type="getServiceTypeTag(scope.row.serviceType)" effect="plain">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="承办律师" align="center" prop="lawyerName" width="100" />
        <el-table-column label="咨询时长" align="center" prop="consultDuration" width="100">
          <template slot-scope="scope">{{ scope.row.consultDuration }}分钟</template>
        </el-table-column>
        <el-table-column label="满意度" align="center" prop="satisfaction" width="130">
          <template slot-scope="scope">
            <span :class="['satisfaction-text', 'satisfaction-' + scope.row.satisfaction]">
              <i v-for="n in getSatisfactionStars(scope.row.satisfaction)" :key="n" class="el-icon-star-on"></i>
              {{ getSatisfactionLabel(scope.row.satisfaction) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="工单" align="center" width="90">
          <template slot-scope="scope">
            <el-tag v-if="scope.row.ticketId" type="success" size="mini">已转单</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-view" @click="handleDetail(scope.row)">查看详情</el-button>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color: #f56c6c" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="queryParams.pageSize"
        :current-page="queryParams.pageNum"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog title="咨询登记详情" :visible.sync="detailOpen" width="900px" append-to-body class="detail-dialog">
      <div class="detail-section">
        <div class="section-title"><span class="title-bar"></span><span>基本信息</span></div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="登记编号">{{ detailForm.ledgerNo }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ detailForm.createTime }}</el-descriptions-item>
          <el-descriptions-item label="服务方式">
            <el-tag :type="getServiceTypeTag(detailForm.serviceType)" effect="plain">{{ getServiceTypeLabel(detailForm.serviceType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="来源渠道">{{ detailForm.sourceChannel }}</el-descriptions-item>
          <el-descriptions-item label="承办律师">{{ detailForm.lawyerName }}</el-descriptions-item>
          <el-descriptions-item label="咨询时长">{{ detailForm.consultDuration }}分钟</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="detail-section">
        <div class="section-title"><span class="title-bar"></span><span>咨询人信息</span></div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="姓名">{{ detailForm.callerName }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ getGenderLabel(detailForm.callerGender) }}</el-descriptions-item>
          <el-descriptions-item label="年龄">{{ detailForm.callerAge }}岁</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detailForm.callerPhone }}</el-descriptions-item>
          <el-descriptions-item label="身份证号">{{ detailForm.callerIdCard }}</el-descriptions-item>
          <el-descriptions-item label="职业">{{ detailForm.callerJob }}</el-descriptions-item>
          <el-descriptions-item label="工作单位">{{ detailForm.callerCompany }}</el-descriptions-item>
          <el-descriptions-item label="联系地址" :span="2">{{ detailForm.callerAddress }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="detail-section">
        <div class="section-title"><span class="title-bar"></span><span>咨询内容</span></div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="咨询类型">
            <el-tag :type="getConsultTypeTag(detailForm.categoryName)">{{ detailForm.categoryName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="问题分类">{{ detailForm.subCategory }}</el-descriptions-item>
          <el-descriptions-item label="涉及金额" v-if="detailForm.involveAmount">
            <span style="color: #f56c6c; font-weight: bold;">¥{{ detailForm.involveAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="咨询摘要" :span="2">
            <div class="content-text">{{ detailForm.consultContent }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="detail-section">
        <div class="section-title"><span class="title-bar"></span><span>律师回复/解答意见</span></div>
        <div class="reply-content">{{ detailForm.lawyerAnswer }}</div>
      </div>

      <div class="detail-section">
        <div class="section-title"><span class="title-bar"></span><span>回访信息</span></div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="是否回访">{{ detailForm.isVisit === '1' ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="回访时间">{{ detailForm.visitTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="回访人">{{ detailForm.visitBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="满意度">
            <span :class="['satisfaction-text', 'satisfaction-' + detailForm.satisfaction]">
              <i v-for="n in getSatisfactionStars(detailForm.satisfaction)" :key="n" class="el-icon-star-on"></i>
              {{ getSatisfactionLabel(detailForm.satisfaction) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="回访意见" :span="2">
            <div class="content-text">{{ detailForm.visitOpinion || '-' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <div slot="footer" class="dialog-footer">
        <el-button icon="el-icon-printer" @click="handlePrint">打印</el-button>
        <el-button @click="detailOpen = false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="formTitle" :visible.sync="formOpen" width="860px" append-to-body class="form-dialog" :close-on-click-modal="false">
      <el-form ref="ledgerForm" :model="form" :rules="rules" label-width="100px" size="small">
        <!-- 自动填充区 -->
        <div class="form-section">
          <div class="section-title"><span class="title-bar"></span><span>自动填充</span></div>
          <el-form-item label="来电记录ID">
            <el-input v-model="autoFillRecordId" placeholder="输入来电记录ID，点击自动填充" style="width: 240px" clearable />
            <el-button type="primary" icon="el-icon-magic-stick" size="mini" @click="handleAutoFill" style="margin-left: 8px">自动填充</el-button>
            <span class="form-tip">从来电记录拉取咨询人/电话/咨询内容等信息</span>
          </el-form-item>
        </div>

        <!-- 咨询人信息 -->
        <div class="form-section">
          <div class="section-title"><span class="title-bar"></span><span>咨询人信息</span></div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="咨询人" prop="callerName">
                <el-input v-model="form.callerName" placeholder="请输入咨询人" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="联系电话" prop="callerPhone">
                <el-input v-model="form.callerPhone" placeholder="请输入联系电话" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="身份证号">
                <el-input v-model="form.callerIdCard" placeholder="请输入身份证号" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="性别">
                <el-select v-model="form.callerGender" placeholder="请选择" style="width: 100%">
                  <el-option label="男" value="0" />
                  <el-option label="女" value="1" />
                  <el-option label="未知" value="2" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="年龄">
                <el-input-number v-model="form.callerAge" :min="0" :max="150" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="职业">
                <el-input v-model="form.callerJob" placeholder="请输入职业" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="工作单位">
                <el-input v-model="form.callerCompany" placeholder="请输入工作单位" />
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="联系地址">
                <el-input v-model="form.callerAddress" placeholder="请输入联系地址" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 咨询信息 -->
        <div class="form-section">
          <div class="section-title"><span class="title-bar"></span><span>咨询信息</span></div>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="咨询类型" prop="categoryName">
                <el-input v-model="form.categoryName" placeholder="如：民事咨询" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="问题分类">
                <el-input v-model="form.subCategory" placeholder="如：婚姻家庭" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="服务方式" prop="serviceType">
                <el-select v-model="form.serviceType" placeholder="请选择" style="width: 100%">
                  <el-option label="电话咨询" value="1" />
                  <el-option label="现场咨询" value="2" />
                  <el-option label="网络咨询" value="3" />
                  <el-option label="视频咨询" value="4" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="来源渠道">
                <el-input v-model="form.sourceChannel" placeholder="如：电话咨询" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="承办律师">
                <el-input v-model="form.lawyerName" placeholder="请输入承办律师" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="咨询时长">
                <el-input-number v-model="form.consultDuration" :min="0" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="涉及金额">
                <el-input-number v-model="form.involveAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="咨询摘要" prop="consultContent">
                <el-input v-model="form.consultContent" type="textarea" :rows="3" placeholder="请输入咨询摘要" />
              </el-form-item>
            </el-col>
          </el-row>
        </div>

        <!-- 处理结果 -->
        <div class="form-section">
          <div class="section-title"><span class="title-bar"></span><span>处理结果</span></div>
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="处理结果">
                <el-input v-model="form.lawyerAnswer" type="textarea" :rows="3" placeholder="请输入律师解答/处理结果" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="满意度">
                <el-select v-model="form.satisfaction" placeholder="请选择" style="width: 100%">
                  <el-option label="非常满意" value="1" />
                  <el-option label="满意" value="2" />
                  <el-option label="一般" value="3" />
                  <el-option label="不满意" value="4" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16" v-if="form.ledgerId">
              <el-form-item label="转工单">
                <el-switch
                  v-model="transferTicketFlag"
                  active-text="转为工单"
                  inactive-text="不转单"
                  :disabled="form.ticketId != null"
                />
                <span v-if="form.ticketId" class="form-tip" style="margin-left: 8px">已关联工单 #{{ form.ticketId }}</span>
              </el-form-item>
            </el-col>
          </el-row>
        </div>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button @click="formOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  listLedger, getLedger, addLedger, updateLedger, delLedger, generateLedgerNo,
  getLedgerTemplates, autoFillLedger, transferLedgerToTicket
} from "@/api/lawyers/callCenter"

export default {
  name: "CallLedger",
  data() {
    return {
      loading: true,
      submitting: false,
      ids: [],
      total: 0,
      ledgerList: [],
      detailOpen: false,
      formOpen: false,
      formTitle: '',
      templates: [],
      autoFillRecordId: '',
      transferTicketFlag: false,
      dateRange: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        categoryName: '',
        callerName: '',
        callerPhone: '',
        lawyerName: '',
        serviceType: '',
        satisfaction: ''
      },
      statistics: {
        totalCount: 0,
        phoneCount: 0,
        siteCount: 0,
        onlineCount: 0,
        videoCount: 0,
        avgSatisfaction: 0
      },
      detailForm: {},
      form: {},
      rules: {
        callerName: [{ required: true, message: '请输入咨询人', trigger: 'blur' }],
        callerPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
        categoryName: [{ required: true, message: '请输入咨询类型', trigger: 'blur' }],
        serviceType: [{ required: true, message: '请选择服务方式', trigger: 'change' }],
        consultContent: [{ required: true, message: '请输入咨询摘要', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
    this.loadTemplates()
  },
  methods: {
    getList() {
      this.loading = true
      listLedger(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.ledgerList = response.rows
        this.total = response.total
        this.loading = false
        this.calcStatistics()
      }).catch(() => { this.loading = false })
    },
    loadTemplates() {
      getLedgerTemplates().then(response => {
        this.templates = response.data || []
      }).catch(() => {})
    },
    calcStatistics() {
      this.statistics.totalCount = this.total
      this.statistics.phoneCount = this.ledgerList.filter(item => item.serviceType === '1').length
      this.statistics.siteCount = this.ledgerList.filter(item => item.serviceType === '2').length
      this.statistics.onlineCount = this.ledgerList.filter(item => item.serviceType === '3').length
      this.statistics.videoCount = this.ledgerList.filter(item => item.serviceType === '4').length
      const satisfactionMap = { '1': 100, '2': 80, '3': 60, '4': 20 }
      const satisfiedItems = this.ledgerList.filter(item => item.satisfaction)
      if (satisfiedItems.length > 0) {
        const sum = satisfiedItems.reduce((acc, item) => acc + (satisfactionMap[item.satisfaction] || 0), 0)
        this.statistics.avgSatisfaction = (sum / satisfiedItems.length).toFixed(1)
      } else {
        this.statistics.avgSatisfaction = 0
      }
    },
    getConsultTypeTag(type) { return 'info' },
    getServiceTypeLabel(type) {
      const labels = { '1': '电话咨询', '2': '现场咨询', '3': '网络咨询', '4': '视频咨询' }
      return labels[type] || '未知'
    },
    getServiceTypeTag(type) {
      const types = { '1': 'primary', '2': 'success', '3': 'warning', '4': 'danger' }
      return types[type] || 'info'
    },
    getSatisfactionLabel(satisfaction) {
      const labels = { '1': '非常满意', '2': '满意', '3': '一般', '4': '不满意' }
      return labels[satisfaction] || '未知'
    },
    getSatisfactionStars(satisfaction) {
      const stars = { '1': 5, '2': 4, '3': 3, '4': 1 }
      return stars[satisfaction] || 0
    },
    getGenderLabel(gender) {
      const labels = { '0': '男', '1': '女', '2': '未知' }
      return labels[String(gender)] || '未知'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.queryParams = {
        pageNum: 1, pageSize: 10,
        categoryName: '', callerName: '', callerPhone: '',
        lawyerName: '', serviceType: '', satisfaction: ''
      }
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.ledgerId)
    },
    handleDetail(row) {
      getLedger(row.ledgerId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      }).catch(() => {})
    },
    resetForm() {
      this.form = {
        ledgerId: null, ledgerNo: null, recordId: null, ticketId: null,
        callerName: '', callerPhone: '', callerIdCard: '', callerGender: '2',
        callerAge: null, callerJob: '', callerCompany: '', callerAddress: '',
        categoryId: null, categoryName: '', subCategory: '',
        serviceType: '1', sourceChannel: '电话咨询',
        lawyerId: null, lawyerName: '', consultContent: '',
        involveAmount: null, lawyerAnswer: '', consultDuration: null,
        satisfaction: '2', isVisit: '0', remark: ''
      }
      this.autoFillRecordId = ''
      this.transferTicketFlag = false
    },
    handleAdd() {
      this.resetForm()
      generateLedgerNo().then(response => {
        this.form.ledgerNo = response.data
        this.formTitle = '新增咨询登记'
        this.formOpen = true
      }).catch(() => {})
    },
    handleApplyTemplate(tpl) {
      this.resetForm()
      generateLedgerNo().then(response => {
        this.form.ledgerNo = response.data
        this.form.categoryName = tpl.categoryName
        this.form.subCategory = tpl.subCategory
        this.form.serviceType = tpl.serviceType
        this.form.sourceChannel = tpl.sourceChannel
        this.form.consultContent = tpl.contentTemplate
        this.formTitle = '新增登记（模板：' + tpl.templateName + '）'
        this.formOpen = true
      }).catch(() => {})
    },
    handleAutoFill() {
      if (!this.autoFillRecordId) {
        this.$message.warning('请输入来电记录ID')
        return
      }
      autoFillLedger(this.autoFillRecordId).then(response => {
        const data = response.data || {}
        this.form.recordId = data.recordId
        this.form.callerName = data.callerName || this.form.callerName
        this.form.callerPhone = data.callerPhone || this.form.callerPhone
        this.form.callerAddress = data.callerAddress || this.form.callerAddress
        this.form.categoryId = data.categoryId
        this.form.categoryName = data.categoryName || this.form.categoryName
        this.form.consultContent = data.consultContent || this.form.consultContent
        this.form.lawyerAnswer = data.lawyerAnswer || this.form.lawyerAnswer
        this.form.lawyerName = data.lawyerName || this.form.lawyerName
        this.form.serviceType = data.serviceType || this.form.serviceType
        this.form.sourceChannel = data.sourceChannel || this.form.sourceChannel
        if (data.consultDuration) {
          this.form.consultDuration = data.consultDuration
        }
        this.$message.success('已从来电记录自动填充信息')
      }).catch(() => {})
    },
    handleEdit(row) {
      this.resetForm()
      getLedger(row.ledgerId).then(response => {
        this.form = response.data
        this.autoFillRecordId = this.form.recordId ? String(this.form.recordId) : ''
        this.transferTicketFlag = false
        this.formTitle = '编辑咨询登记'
        this.formOpen = true
      }).catch(() => {})
    },
    submitForm() {
      this.$refs.ledgerForm.validate(valid => {
        if (!valid) return
        this.submitting = true
        // 编辑模式下勾选了转工单，则先转工单再保存
        const doTransfer = this.form.ledgerId && this.transferTicketFlag && !this.form.ticketId
        const finish = () => {
          if (this.form.ledgerId != null) {
            updateLedger(this.form).then(() => {
              this.$message.success('修改成功')
              this.formOpen = false
              this.getList()
              this.submitting = false
            }).catch(() => { this.submitting = false })
          } else {
            addLedger(this.form).then(() => {
              this.$message.success('新增成功')
              this.formOpen = false
              this.getList()
              this.submitting = false
            }).catch(() => { this.submitting = false })
          }
        }
        if (doTransfer) {
          transferLedgerToTicket(this.form.ledgerId).then(response => {
            this.form.ticketId = response.data.ticketId
            this.$message.success('已转为工单 #' + this.form.ticketId)
            finish()
          }).catch(() => { this.submitting = false })
        } else {
          finish()
        }
      })
    },
    handleDelete(row) {
      this.$confirm('是否确认删除登记编号为"' + row.ledgerNo + '"的数据项？', '警告', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
      }).then(() => {
        delLedger(row.ledgerId).then(() => {
          this.$message.success('删除成功')
          this.getList()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleExport() {
      this.download('lawyers/call/ledger/export', { ...this.queryParams }, `ledger_${new Date().getTime()}.xlsx`)
    },
    handleBatchPrint() {
      if (this.ids.length === 0) {
        this.$message.warning('请先选择要打印的记录')
        return
      }
      this.$message.success('批量打印任务已提交')
    },
    handlePrint() {
      this.$message.success('打印任务已提交')
    },
    handleSizeChange(val) {
      this.queryParams.pageSize = val
      this.getList()
    },
    handleCurrentChange(val) {
      this.queryParams.pageNum = val
      this.getList()
    }
  }
}
</script>

<style lang="scss" scoped>
.call-ledger {
  padding: 16px;
  background-color: #f1f5f9;
  min-height: calc(100vh - 84px);
}

.search-card {
  border-radius: 8px;
  margin-bottom: 16px;
  ::v-deep .el-card__body { padding: 16px 20px; }
}

.stat-row {
  margin-bottom: 16px;
  .stat-card {
    display: flex;
    align-items: center;
    padding: 20px;
    background: #fff;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    .stat-icon {
      width: 48px; height: 48px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center;
      margin-right: 14px; font-size: 24px; color: #fff;
    }
    .stat-info {
      flex: 1;
      .stat-value { font-size: 24px; font-weight: 600; color: #1e293b; line-height: 1.2; }
      .stat-label { font-size: 13px; color: #64748b; margin-top: 4px; }
    }
    &.stat-total .stat-icon { background: linear-gradient(135deg, #3b82f6, #1d4ed8); }
    &.stat-phone .stat-icon { background: linear-gradient(135deg, #3b82f6, #2563eb); }
    &.stat-site .stat-icon { background: linear-gradient(135deg, #10b981, #059669); }
    &.stat-online .stat-icon { background: linear-gradient(135deg, #f59e0b, #d97706); }
    &.stat-video .stat-icon { background: linear-gradient(135deg, #8b5cf6, #7c3aed); }
    &.stat-satisfaction .stat-icon { background: linear-gradient(135deg, #06b6d4, #0891b2); }
  }
}

.table-card {
  border-radius: 8px;
  ::v-deep .el-card__body { padding: 16px 20px; }
  .toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
}

.template-bar {
  padding: 12px 0;
  margin-bottom: 12px;
  border-top: 1px dashed #e2e8f0;
  border-bottom: 1px dashed #e2e8f0;
  .template-label {
    font-size: 13px;
    color: #64748b;
    margin-right: 8px;
    font-weight: 600;
  }
  .tpl-btn {
    margin: 4px 6px 4px 0;
    i { margin-right: 4px; }
  }
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.link-blue {
  color: #3b82f6;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.text-muted { color: #cbd5e1; }

.satisfaction-text {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  i { font-size: 12px; }
  &.satisfaction-1 { color: #10b981; i { color: #10b981; } }
  &.satisfaction-2 { color: #3b82f6; i { color: #3b82f6; } }
  &.satisfaction-3 { color: #f59e0b; i { color: #f59e0b; } }
  &.satisfaction-4 { color: #ef4444; i { color: #ef4444; } }
}

.detail-dialog, .form-dialog {
  ::v-deep .el-dialog__body { padding: 0 20px 20px; }
}

.detail-section, .form-section {
  margin-bottom: 20px;
  &:last-of-type { margin-bottom: 0; }
  .section-title {
    display: flex;
    align-items: center;
    font-size: 15px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 12px;
    padding-left: 4px;
    .title-bar {
      width: 4px; height: 16px; background: #3b82f6;
      border-radius: 2px; margin-right: 10px;
    }
  }
  .content-text {
    line-height: 1.8;
    color: #334155;
    white-space: pre-wrap;
  }
}

.reply-content {
  padding: 16px;
  background: #f8fafc;
  border-radius: 6px;
  line-height: 1.8;
  color: #334155;
  border-left: 3px solid #3b82f6;
  white-space: pre-wrap;
}

.form-tip {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}
</style>
