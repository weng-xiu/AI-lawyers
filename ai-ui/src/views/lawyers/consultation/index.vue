<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="用户ID" prop="userId">
        <el-input
          v-model="queryParams.userId"
          placeholder="请输入用户ID"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="咨询分类" prop="categoryId">
        <el-select v-model="queryParams.categoryId" placeholder="请选择咨询分类" clearable>
          <el-option
            v-for="item in categoryOptions"
            :key="item.categoryId"
            :label="item.categoryName"
            :value="item.categoryId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
          <el-option
            v-for="dict in dict.type.consultation_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="问题关键词" prop="questionKeyword">
        <el-input
          v-model="queryParams.questionKeyword"
          placeholder="请输入问题关键词"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="回答关键词" prop="answerKeyword">
        <el-input
          v-model="queryParams.answerKeyword"
          placeholder="请输入回答关键词"
          clearable
          style="width: 240px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="评分范围" prop="ratingRange">
        <el-slider
          v-model="queryParams.ratingRange"
          range
          :max="5"
          :step="0.5"
          :marks="{ 0: '0', 1: '1', 2: '2', 3: '3', 4: '4', 5: '5' }"
          style="width: 240px"
          @change="handleQuery"
        ></el-slider>
      </el-form-item>
      <el-form-item label="创建时间">
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
      <el-form-item label="更新时间">
        <el-date-picker
          v-model="updateDateRange"
          style="width: 240px"
          value-format="yyyy-MM-dd"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
        <el-button type="success" icon="el-icon-search" size="mini" @click="toggleAdvancedSearch">高级搜索</el-button>
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
          v-hasPermi="['lawyers:consultation:add']"
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
          v-hasPermi="['lawyers:consultation:edit']"
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
          v-hasPermi="['lawyers:consultation:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:consultation:export']"
        >导出</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="el-icon-data-line"
          size="mini"
          @click="handleStatistics"
          v-hasPermi="['lawyers:consultation:list']"
        >统计分析</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="consultationList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="咨询ID" align="center" prop="consultationId" />
      <el-table-column label="用户ID" align="center" prop="userId" />
      <el-table-column label="咨询分类" align="center" prop="categoryName" />
      <el-table-column label="咨询问题" align="center" prop="question" :show-overflow-tooltip="true" />
      <el-table-column label="AI回答" align="center" prop="answer" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.consultation_status" :value="scope.row.status"/>
        </template>
      </el-table-column>
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
            v-hasPermi="['lawyers:consultation:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:consultation:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:consultation:remove']"
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
import { listConsultation, getConsultation, delConsultation, addConsultation, updateConsultation, exportConsultation, getConsultationStatistics, getConsultationByCategory, getConsultationByDate } from "@/api/lawyers/consultation"
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
      // 咨询分类选项
      categoryOptions: [],
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
    // 初始化评分范围
    this.queryParams.ratingRange = [0, 5]
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
      this.dateRange = []
      this.updateDateRange = []
      this.queryParams.ratingRange = [0, 5]
      this.resetForm("queryForm")
      this.handleQuery()
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
        
        const myChart = echarts.init(chartDom)
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
        
        const myChart = echarts.init(chartDom)
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
            })
          } else {
            addConsultation(this.form).then(response => {
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
      const consultationIds = row.consultationId || this.ids
      this.$modal.confirm('是否确认删除法律咨询记录编号为"' + consultationIds + '"的数据项？').then(function() {
        return delConsultation(consultationIds)
      }).then(() => {
        this.getList()
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