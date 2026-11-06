<template>
  <div class="app-container">
    <!-- 4 渠道统计卡 -->
    <el-row :gutter="16" class="cm-stats">
      <el-col :xs="12" :sm="6">
        <div class="cm-stat-card cm-stat-voice">
          <div class="cm-stat-icon"><i class="el-icon-phone"></i></div>
          <div class="cm-stat-body">
            <div class="cm-stat-label">今日语音咨询</div>
            <div class="cm-stat-value">{{ channelStats.todayVoice || 0 }}</div>
            <div class="cm-stat-sub">累计 {{ channelStats.voiceTotal || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cm-stat-card cm-stat-img">
          <div class="cm-stat-icon"><i class="el-icon-chat-dot-square"></i></div>
          <div class="cm-stat-body">
            <div class="cm-stat-label">今日图文咨询</div>
            <div class="cm-stat-value">{{ channelStats.todayImageText || 0 }}</div>
            <div class="cm-stat-sub">累计 {{ channelStats.imageTextTotal || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cm-stat-card cm-stat-video">
          <div class="cm-stat-icon"><i class="el-icon-video-camera"></i></div>
          <div class="cm-stat-body">
            <div class="cm-stat-label">今日视频咨询</div>
            <div class="cm-stat-value">{{ channelStats.todayVideo || 0 }}</div>
            <div class="cm-stat-sub">累计 {{ channelStats.videoTotal || 0 }}</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="cm-stat-card cm-stat-total">
          <div class="cm-stat-icon"><i class="el-icon-data-line"></i></div>
          <div class="cm-stat-body">
            <div class="cm-stat-label">今日总咨询</div>
            <div class="cm-stat-value">{{ channelStats.todayTotal || 0 }}</div>
            <div class="cm-stat-sub">累计 {{ channelStats.totalCount || 0 }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="consultationList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      has-selection
      label-width="68px"
      empty-text="暂无咨询记录"
      empty-hint="公众端发起咨询后在此查看，可按渠道/状态/评分区间筛选"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:consultation:add']">新增</el-button>
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:consultation:edit']">修改</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:consultation:remove']">删除</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:consultation:export']">导出</el-button>
        <el-button type="info" plain icon="el-icon-data-line" size="mini" @click="handleStatistics"
          v-hasPermi="['lawyers:consultation:list']">统计分析</el-button>
      </template>
      <template #query-extra>
        <el-button type="success" icon="el-icon-search" size="mini" @click="toggleAdvancedSearch">高级搜索</el-button>
      </template>
      <!-- 评分区间为 slider，无法由 ProTable 内建控件表达，走查询插槽 -->
      <template #query-ratingRange>
        <el-slider v-model="queryParams.ratingRange" range :max="5" :step="0.5"
          :marks="{ 0: '0', 1: '1', 2: '2', 3: '3', 4: '4', 5: '5' }" style="width: 240px"
          @change="handleQuery" />
      </template>
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #query-updateDateRange>
        <el-date-picker v-model="updateDateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-consultationChannel="{ row }">
        <el-tag size="mini" :type="channelTagType(row.consultationChannel)">{{ channelText(row.consultationChannel) }}</el-tag>
      </template>
      <template #column-status="{ row }">
        <dict-tag :options="dict.type.consultation_status" :value="row.status"/>
      </template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)"
          v-hasPermi="['lawyers:consultation:query']">详情</el-button>
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:consultation:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:consultation:remove']">删除</el-button>
      </template>
    </ProTable>

    <!-- 添加或修改法律咨询记录对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="800px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户ID" prop="userId">
          <el-input v-model="form.userId" placeholder="请输入用户ID" />
        </el-form-item>
        <el-form-item label="咨询分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择咨询分类">
            <el-option
              v-for="item in categoryOptions"
              :key="item.categoryId"
              :label="item.categoryName"
              :value="item.categoryId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="咨询渠道" prop="consultationChannel">
          <el-radio-group v-model="form.consultationChannel">
            <el-radio label="1">语音</el-radio>
            <el-radio label="2">图文</el-radio>
            <el-radio label="3">视频</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="咨询问题" prop="question">
          <el-input v-model="form.question" type="textarea" placeholder="请输入咨询问题" />
        </el-form-item>
        <el-form-item label="AI回答" prop="answer">
          <el-input v-model="form.answer" type="textarea" placeholder="AI将自动生成回答" :rows="5" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio
              v-for="dict in dict.type.consultation_status"
              :key="dict.value"
              :label="dict.value"
            >{{dict.label}}</el-radio>
          </el-radio-group>
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
    <el-dialog title="咨询详情" :visible.sync="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="咨询ID">{{ detailForm.consultationId }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detailForm.userId }}</el-descriptions-item>
        <el-descriptions-item label="咨询分类">{{ detailForm.categoryName }}</el-descriptions-item>
        <el-descriptions-item label="咨询渠道">
          <el-tag size="mini" :type="channelTagType(detailForm.consultationChannel)">{{ channelText(detailForm.consultationChannel) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="咨询问题">{{ detailForm.question }}</el-descriptions-item>
        <el-descriptions-item label="AI回答">{{ detailForm.answer }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <dict-tag :options="dict.type.consultation_status" :value="detailForm.status"/>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ parseTime(detailForm.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>

    <!-- 统计分析对话框 -->
    <el-dialog title="咨询统计分析" :visible.sync="statisticsOpen" width="900px" append-to-body>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>总咨询数</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.totalCount }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>今日咨询数</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.todayCount }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>本周咨询数</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.weekCount }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>本月咨询数</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.monthCount }}</h2>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="8">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>已完成咨询</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.completedCount }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>处理中咨询</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.processingCount }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>失败咨询</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.failedCount }}</h2>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>平均评分</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.avgRating.toFixed(1) }}</h2>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>完成率</span>
            </div>
            <div class="text item">
              <h2>{{ statistics.totalCount > 0 ? ((statistics.completedCount / statistics.totalCount) * 100).toFixed(1) : 0 }}%</h2>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>按分类统计</span>
            </div>
            <div class="text item">
              <div ref="categoryChart" style="height: 300px;"></div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="box-card">
            <div slot="header" class="clearfix">
              <span>按日期统计（最近7天）</span>
            </div>
            <div class="text item">
              <div ref="dateChart" style="height: 300px;"></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <div slot="footer" class="dialog-footer">
        <el-button @click="statisticsOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listConsultation, getConsultation, delConsultation, addConsultation, updateConsultation, exportConsultation, getConsultationStatistics, getConsultationByCategory, getConsultationByDate, getConsultationChannelStats } from "@/api/lawyers/consultation"
import { getValidCategories } from "@/api/lawyers/category"
import * as echarts from 'echarts'

export default {
  name: "Consultation",
  dicts: ['consultation_status'],
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
      // 法律咨询记录表格数据
      consultationList: [],
      // V3：ProTable 列定义（渠道/状态/创建时间/操作走插槽）
      columns: [
        { prop: 'consultationId', label: '咨询ID', align: 'center' },
        { prop: 'userId', label: '用户ID', align: 'center' },
        { prop: 'categoryName', label: '咨询分类', align: 'center' },
        { prop: 'consultationChannel', label: '咨询渠道', width: 90, align: 'center', slot: true },
        { prop: 'question', label: '咨询问题', align: 'center', showOverflowTooltip: true },
        { prop: 'answer', label: 'AI回答', align: 'center', showOverflowTooltip: true },
        { prop: 'status', label: '状态', align: 'center', slot: true },
        { prop: 'createTime', label: '创建时间', width: 180, align: 'center', slot: true },
        { prop: 'action', label: '操作', align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
      // 咨询分类选项
      categoryOptions: [],
      // 渠道统计数据
      channelStats: {},
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详情弹出层
      detailOpen: false,
      // 是否显示统计分析弹出层
      statisticsOpen: false,
      // 日期范围
      dateRange: [],
      // 更新日期范围
      updateDateRange: [],
      // 是否显示高级搜索
      showAdvancedSearch: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        userId: undefined,
        categoryId: undefined,
        status: undefined,
        consultationChannel: undefined,
        questionKeyword: undefined,
        answerKeyword: undefined,
        minRating: undefined,
        maxRating: undefined
      },
      // 表单参数
      form: {},
      // 详情表单
      detailForm: {},
      // 统计数据
      statistics: {
        totalCount: 0,
        todayCount: 0,
        weekCount: 0,
        monthCount: 0,
        completedCount: 0,
        processingCount: 0,
        failedCount: 0,
        avgRating: 0
      },
      // 表单校验
      rules: {
        userId: [
          { required: true, message: "用户ID不能为空", trigger: "blur" }
        ],
        categoryId: [
          { required: true, message: "咨询分类不能为空", trigger: "change" }
        ],
        question: [
          { required: true, message: "咨询问题不能为空", trigger: "blur" }
        ]
      }
    }
  },
  created() {
    this.getList()
    this.getCategoryOptions()
    this.loadChannelStats()
    // 初始化评分范围
    this.queryParams.ratingRange = [0, 5]
  },
  computed: {
    queryFormItems() {
      const categorySelectOptions = (this.categoryOptions || []).map(c => ({
        label: c.categoryName,
        value: c.categoryId
      }))
      const statusOptions = (this.dict.type.consultation_status || []).map(d => ({ label: d.label, value: d.value }))
      return [
        { prop: 'userId', label: '用户ID', type: 'input', placeholder: '请输入用户ID', width: 240 },
        { prop: 'categoryId', label: '咨询分类', type: 'select', placeholder: '请选择咨询分类', width: 180, options: categorySelectOptions },
        {
          prop: 'consultationChannel',
          label: '咨询渠道',
          type: 'select',
          placeholder: '请选择渠道',
          width: 140,
          options: [
            { label: '语音', value: '1' },
            { label: '图文', value: '2' },
            { label: '视频', value: '3' }
          ]
        },
        { prop: 'status', label: '状态', type: 'select', placeholder: '请选择状态', width: 140, options: statusOptions },
        { prop: 'questionKeyword', label: '问题关键词', type: 'input', placeholder: '请输入问题关键词', width: 240 },
        { prop: 'answerKeyword', label: '回答关键词', type: 'input', placeholder: '请输入回答关键词', width: 240 },
        // 评分区间/创建时间/更新时间使用自定义控件，走查询插槽
        { prop: 'ratingRange', label: '评分范围', slot: true },
        { prop: 'dateRange', label: '创建时间', slot: true },
        { prop: 'updateDateRange', label: '更新时间', slot: true }
      ]
    }
  },
  methods: {
    /** 查询法律咨询记录列表 */
    getList() {
      this.loading = true
      
      // 处理评分范围
      if (this.queryParams.ratingRange && this.queryParams.ratingRange.length === 2) {
        this.queryParams.minRating = this.queryParams.ratingRange[0]
        this.queryParams.maxRating = this.queryParams.ratingRange[1]
      } else {
        this.queryParams.minRating = undefined
        this.queryParams.maxRating = undefined
      }
      
      // 合并日期范围参数
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange),
        ...this.addDateRange({ updateDateRange: this.updateDateRange }, this.updateDateRange, 'updateTime')
      }
      
      listConsultation(params).then(response => {
          this.consultationList = response.rows
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
    /** 加载渠道统计 */
    loadChannelStats() {
      getConsultationChannelStats().then(response => {
        this.channelStats = response.data || {}
      })
    },
    /** 渠道文本 */
    channelText(channel) {
      const map = { '1': '语音', '2': '图文', '3': '视频' }
      return map[channel] || '图文'
    },
    /** 渠道标签类型 */
    channelTagType(channel) {
      const map = { '1': 'success', '2': 'primary', '3': 'warning' }
      return map[channel] || 'info'
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        consultationId: undefined,
        userId: undefined,
        categoryId: undefined,
        consultationChannel: "2",
        question: undefined,
        answer: undefined,
        status: "0",
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
      // ProTable 已复位查询表单，页面只需恢复筛选/评分区间/两个日期区间并重查
      this.dateRange = []
      this.updateDateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        userId: undefined,
        categoryId: undefined,
        status: undefined,
        consultationChannel: undefined,
        questionKeyword: undefined,
        answerKeyword: undefined,
        minRating: undefined,
        maxRating: undefined,
        ratingRange: [0, 5]
      }
      this.getList()
    },
    /** 切换高级搜索 */
    toggleAdvancedSearch() {
      this.showAdvancedSearch = !this.showAdvancedSearch
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加法律咨询记录"
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.consultationId)
      this.single = selection.length!=1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const consultationId = row.consultationId || this.ids
      getConsultation(consultationId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改法律咨询记录"
      })
    },
    /** 查看详情按钮操作 */
    handleDetail(row) {
      const consultationId = row.consultationId
      getConsultation(consultationId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    /** 统计分析按钮操作 */
    handleStatistics() {
      this.statisticsOpen = true
      this.getStatisticsData()
    },
    /** 获取统计数据 */
    getStatisticsData() {
      // 获取基本统计数据
      getConsultationStatistics().then(response => {
        this.statistics = response.data
      })
      
      // 获取按分类统计数据并绘制图表
      getConsultationByCategory().then(response => {
        this.drawCategoryChart(response.data)
      })
      
      // 获取按日期统计数据并绘制图表
      getConsultationByDate(7).then(response => {
        this.drawDateChart(response.data)
      })
    },
    /** 绘制分类统计图表 */
    drawCategoryChart(data) {
      this.$nextTick(() => {
        const chartDom = this.$refs.categoryChart
        if (!chartDom) return
        
        const myChart = echarts.init(chartDom, 'judicial') // V4：统一司法蓝主题
        const option = {
          tooltip: {
            trigger: 'item',
            formatter: '{a} <br/>{b}: {c} ({d}%)'
          },
          legend: {
            orient: 'vertical',
            left: 10,
            data: data.map(item => item.category_name)
          },
          series: [
            {
              name: '咨询分类',
              type: 'pie',
              radius: ['50%', '70%'],
              avoidLabelOverlap: false,
              label: {
                show: false,
                position: 'center'
              },
              emphasis: {
                label: {
                  show: true,
                  fontSize: '18',
                  fontWeight: 'bold'
                }
              },
              labelLine: {
                show: false
              },
              data: data.map(item => ({
                value: item.count,
                name: item.category_name
              }))
            }
          ]
        }
        
        myChart.setOption(option)
        window.addEventListener('resize', () => {
          myChart.resize()
        })
      })
    },
    /** 绘制日期统计图表 */
    drawDateChart(data) {
      this.$nextTick(() => {
        const chartDom = this.$refs.dateChart
        if (!chartDom) return
        
        const myChart = echarts.init(chartDom, 'judicial') // V4：统一司法蓝主题
        const option = {
          tooltip: {
            trigger: 'axis'
          },
          xAxis: {
            type: 'category',
            data: data.map(item => item.date)
          },
          yAxis: {
            type: 'value'
          },
          series: [
            {
              name: '咨询数',
              data: data.map(item => item.count),
              type: 'line',
              smooth: true,
              areaStyle: {}
            }
          ]
        }
        
        myChart.setOption(option)
        window.addEventListener('resize', () => {
          myChart.resize()
        })
      })
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.consultationId != undefined) {
            updateConsultation(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
              this.loadChannelStats()
            })
          } else {
            addConsultation(this.form).then(response => {
              this.$modal.msgSuccess("新增成功")
              this.open = false
              this.getList()
              this.loadChannelStats()
            })
          }
        }
      })
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const consultationIds = row.consultationId || this.ids
      const count = row.consultationId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的法律咨询记录',
        count: count,
        countNoun: '条记录'
      }).then(function() {
        return delConsultation(consultationIds)
      }).then(() => {
        this.getList()
        this.loadChannelStats()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      // 处理查询参数
      let params = { ...this.queryParams }
      
      // 处理评分范围
      if (params.ratingRange && params.ratingRange.length === 2) {
        params.minRating = params.ratingRange[0]
        params.maxRating = params.ratingRange[1]
        delete params.ratingRange
      }
      
      // 处理日期范围
      if (this.dateRange && this.dateRange.length === 2) {
        params.beginTime = this.dateRange[0]
        params.endTime = this.dateRange[1]
      }
      
      // 处理更新时间范围
      if (this.updateDateRange && this.updateDateRange.length === 2) {
        params.updateBeginTime = this.updateDateRange[0]
        params.updateEndTime = this.updateDateRange[1]
      }
      
      this.download('lawyers/consultation/export', params, `consultation_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>

<style lang="scss" scoped>
.cm-stats {
  margin-bottom: 16px;
}
.cm-stat-card {
  display: flex;
  align-items: center;
  padding: 18px 20px;
  border-radius: 8px;
  color: #fff;
  min-height: 92px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  .cm-stat-icon {
    font-size: 32px;
    margin-right: 14px;
    opacity: 0.9;
  }
  .cm-stat-label {
    font-size: 13px;
    opacity: 0.9;
  }
  .cm-stat-value {
    font-size: 26px;
    font-weight: 600;
    line-height: 1.2;
    margin-top: 4px;
  }
  .cm-stat-sub {
    font-size: 12px;
    opacity: 0.85;
    margin-top: 4px;
  }
}
.cm-stat-voice { background: linear-gradient(135deg, #2B8C6E, #54A68B); }
.cm-stat-img { background: linear-gradient(135deg, #1A3C6E, #255A99); }
.cm-stat-video { background: linear-gradient(135deg, #E8923A, #EFA960); }
.cm-stat-total { background: linear-gradient(135deg, #8C8C8C, #b1b3b8); }
</style>