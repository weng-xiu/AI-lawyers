<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="服务编号" prop="serviceNo">
        <el-input
          v-model="queryParams.serviceNo"
          placeholder="请输入服务编号"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户姓名" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户姓名"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户电话" prop="customerPhone">
        <el-input
          v-model="queryParams.customerPhone"
          placeholder="请输入客户电话"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="服务类型" prop="serviceType">
        <el-select v-model="queryParams.serviceType" placeholder="请选择服务类型" clearable style="width: 160px">
          <el-option label="代书" value="代书" />
          <el-option label="调解" value="调解" />
          <el-option label="诉讼代理" value="诉讼代理" />
          <el-option label="法律顾问" value="法律顾问" />
        </el-select>
      </el-form-item>
      <el-form-item label="支付状态" prop="paymentStatus">
        <el-select v-model="queryParams.paymentStatus" placeholder="请选择支付状态" clearable style="width: 140px">
          <el-option label="未付" value="0" />
          <el-option label="已付" value="1" />
          <el-option label="部分付" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="服务状态" prop="serviceStatus">
        <el-select v-model="queryParams.serviceStatus" placeholder="请选择服务状态" clearable style="width: 140px">
          <el-option label="待受理" value="0" />
          <el-option label="处理中" value="1" />
          <el-option label="已完成" value="2" />
          <el-option label="已关闭" value="3" />
        </el-select>
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
          v-hasPermi="['lawyers:paidService:add']"
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
          v-hasPermi="['lawyers:paidService:edit']"
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
          v-hasPermi="['lawyers:paidService:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['lawyers:paidService:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="paidServiceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="服务编号" align="center" prop="serviceNo" />
      <el-table-column label="客户姓名" align="center" prop="customerName" />
      <el-table-column label="客户电话" align="center" prop="customerPhone" />
      <el-table-column label="服务类型" align="center" prop="serviceType" width="100">
        <template slot-scope="scope">
          <el-tag size="mini" :type="serviceTypeTagType(scope.row.serviceType)">{{ scope.row.serviceType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="服务金额" align="center" prop="amount" width="120">
        <template slot-scope="scope">
          <span>{{ scope.row.amount != null ? '¥' + scope.row.amount.toFixed(2) : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="支付状态" align="center" prop="paymentStatus" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="paymentStatusTagType(scope.row.paymentStatus)">{{ paymentStatusText(scope.row.paymentStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="服务状态" align="center" prop="serviceStatus" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="serviceStatusTagType(scope.row.serviceStatus)">{{ serviceStatusText(scope.row.serviceStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理人" align="center" prop="handlerName" />
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
            v-hasPermi="['lawyers:paidService:query']"
          >详情</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['lawyers:paidService:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['lawyers:paidService:remove']"
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

    <!-- 添加或修改有偿法律服务对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="650px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="服务编号" prop="serviceNo">
          <el-input v-model="form.serviceNo" placeholder="请输入服务编号" />
        </el-form-item>
        <el-form-item label="客户姓名" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户姓名" />
        </el-form-item>
        <el-form-item label="客户电话" prop="customerPhone">
          <el-input v-model="form.customerPhone" placeholder="请输入客户电话" />
        </el-form-item>
        <el-form-item label="服务类型" prop="serviceType">
          <el-radio-group v-model="form.serviceType">
            <el-radio label="代书">代书</el-radio>
            <el-radio label="调解">调解</el-radio>
            <el-radio label="诉讼代理">诉讼代理</el-radio>
            <el-radio label="法律顾问">法律顾问</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="服务金额" prop="amount">
          <el-input v-model="form.amount" placeholder="请输入服务金额">
            <template slot="prepend">¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="支付状态" prop="paymentStatus">
          <el-radio-group v-model="form.paymentStatus">
            <el-radio label="0">未付</el-radio>
            <el-radio label="1">已付</el-radio>
            <el-radio label="2">部分付</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="服务状态" prop="serviceStatus">
          <el-radio-group v-model="form.serviceStatus">
            <el-radio label="0">待受理</el-radio>
            <el-radio label="1">处理中</el-radio>
            <el-radio label="2">已完成</el-radio>
            <el-radio label="3">已关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理人" prop="handlerName">
          <el-input v-model="form.handlerName" placeholder="请输入处理人" />
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
    <el-dialog title="有偿法律服务详情" :visible.sync="detailOpen" width="700px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="服务编号">{{ detailForm.serviceNo }}</el-descriptions-item>
        <el-descriptions-item label="客户姓名">{{ detailForm.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户电话">{{ detailForm.customerPhone }}</el-descriptions-item>
        <el-descriptions-item label="服务类型">
          <el-tag size="mini" :type="serviceTypeTagType(detailForm.serviceType)">{{ detailForm.serviceType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="服务金额">{{ detailForm.amount != null ? '¥' + detailForm.amount.toFixed(2) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付状态">
          <el-tag size="mini" :type="paymentStatusTagType(detailForm.paymentStatus)">{{ paymentStatusText(detailForm.paymentStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="服务状态">
          <el-tag size="mini" :type="serviceStatusTagType(detailForm.serviceStatus)">{{ serviceStatusText(detailForm.serviceStatus) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理人">{{ detailForm.handlerName }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ parseTime(detailForm.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ parseTime(detailForm.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="创建者">{{ detailForm.createBy }}</el-descriptions-item>
        <el-descriptions-item label="更新者">{{ detailForm.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailForm.remark }}</el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listPaidService, getPaidService, delPaidService, addPaidService, updatePaidService, exportPaidService } from "@/api/lawyers/paidService"

export default {
  name: "PaidService",
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
      // 有偿法律服务表格数据
      paidServiceList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 是否显示详情弹出层
      detailOpen: false,
      // 日期范围
      dateRange: [],
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        serviceNo: undefined,
        customerName: undefined,
        customerPhone: undefined,
        serviceType: undefined,
        paymentStatus: undefined,
        serviceStatus: undefined
      },
      // 表单参数
      form: {},
      // 详情表单
      detailForm: {},
      // 表单校验
      rules: {
        customerName: [
          { required: true, message: "客户姓名不能为空", trigger: "blur" }
        ],
        serviceType: [
          { required: true, message: "服务类型不能为空", trigger: "change" }
        ]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    /** 查询有偿法律服务列表 */
    getList() {
      this.loading = true
      // 合并日期范围参数
      const params = {
        ...this.queryParams,
        ...this.addDateRange(this.queryParams, this.dateRange)
      }
      listPaidService(params).then(response => {
          this.paidServiceList = response.rows
          this.total = response.total
          this.loading = false
        }
      )
    },
    /** 服务类型标签类型 */
    serviceTypeTagType(type) {
      const map = { '代书': 'primary', '调解': 'warning', '诉讼代理': 'danger', '法律顾问': 'success' }
      return map[type] || 'info'
    },
    /** 支付状态文本 */
    paymentStatusText(status) {
      const map = { '0': '未付', '1': '已付', '2': '部分付' }
      return map[status] || status
    },
    /** 支付状态标签类型 */
    paymentStatusTagType(status) {
      const map = { '0': 'warning', '1': 'success', '2': 'info' }
      return map[status] || 'info'
    },
    /** 服务状态文本 */
    serviceStatusText(status) {
      const map = { '0': '待受理', '1': '处理中', '2': '已完成', '3': '已关闭' }
      return map[status] || status
    },
    /** 服务状态标签类型 */
    serviceStatusTagType(status) {
      const map = { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }
      return map[status] || 'info'
    },
    // 取消按钮
    cancel() {
      this.open = false
      this.reset()
    },
    // 表单重置
    reset() {
      this.form = {
        paidId: undefined,
        serviceNo: undefined,
        customerName: undefined,
        customerPhone: undefined,
        serviceType: undefined,
        amount: undefined,
        paymentStatus: "0",
        serviceStatus: "0",
        handlerName: undefined,
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
      this.resetForm("queryForm")
      this.handleQuery()
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset()
      this.open = true
      this.title = "添加有偿法律服务"
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.paidId)
      this.single = selection.length != 1
      this.multiple = !selection.length
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset()
      const paidId = row.paidId || this.ids
      getPaidService(paidId).then(response => {
        this.form = response.data
        this.open = true
        this.title = "修改有偿法律服务"
      })
    },
    /** 查看详情按钮操作 */
    handleDetail(row) {
      const paidId = row.paidId
      getPaidService(paidId).then(response => {
        this.detailForm = response.data
        this.detailOpen = true
      })
    },
    /** 提交按钮 */
    submitForm: function() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.paidId != undefined) {
            updatePaidService(this.form).then(response => {
              this.$modal.msgSuccess("修改成功")
              this.open = false
              this.getList()
            })
          } else {
            addPaidService(this.form).then(response => {
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
      const paidIds = row.paidId || this.ids
      this.$modal.confirm('是否确认删除有偿法律服务编号为"' + paidIds + '"的数据项？').then(function() {
        return delPaidService(paidIds)
      }).then(() => {
        this.getList()
        this.$modal.msgSuccess("删除成功")
      }).catch(() => {})
    },
    /** 导出按钮操作 */
    handleExport() {
      // 处理查询参数
      let params = { ...this.queryParams }
      
      // 处理日期范围
      if (this.dateRange && this.dateRange.length === 2) {
        params.beginTime = this.dateRange[0]
        params.endTime = this.dateRange[1]
      }
      
      this.download('lawyers/paidLegalService/export', params, `paidService_${new Date().getTime()}.xlsx`)
    }
  }
}
</script>
