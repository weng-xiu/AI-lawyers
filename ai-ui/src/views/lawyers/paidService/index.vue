<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="paidServiceList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      has-selection
      label-width="80px"
      empty-text="暂无有偿法律服务记录"
      empty-hint="新增服务订单后，支付与服务进度将在此跟踪"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd"
          v-hasPermi="['lawyers:paidService:add']">新增</el-button>
        <el-button type="success" plain icon="el-icon-edit" size="mini" :disabled="single" @click="handleUpdate"
          v-hasPermi="['lawyers:paidService:edit']">修改</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:paidService:remove']">删除</el-button>
        <el-button type="warning" plain icon="el-icon-download" size="mini" @click="handleExport"
          v-hasPermi="['lawyers:paidService:export']">导出</el-button>
      </template>
      <!-- 创建时间沿用独立 dateRange + addDateRange，走自定义查询插槽 -->
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-serviceType="{ row }">
        <el-tag size="mini" :type="serviceTypeTagType(row.serviceType)">{{ row.serviceType }}</el-tag>
      </template>
      <template #column-amount="{ row }">{{ row.amount != null ? '¥' + row.amount.toFixed(2) : '-' }}</template>
      <template #column-paymentStatus="{ row }">
        <el-tag size="mini" :type="paymentStatusTagType(row.paymentStatus)">{{ paymentStatusText(row.paymentStatus) }}</el-tag>
      </template>
      <template #column-serviceStatus="{ row }">
        <el-tag size="mini" :type="serviceStatusTagType(row.serviceStatus)">{{ serviceStatusText(row.serviceStatus) }}</el-tag>
      </template>
      <template #column-createTime="{ row }">{{ parseTime(row.createTime) }}</template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)"
          v-hasPermi="['lawyers:paidService:query']">详情</el-button>
        <el-button size="mini" type="text" icon="el-icon-edit" @click="handleUpdate(row)"
          v-hasPermi="['lawyers:paidService:edit']">修改</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:paidService:remove']">删除</el-button>
      </template>
    </ProTable>

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
        <el-form-item label="处理人" prop="handlerId">
          <user-select v-model="form.handlerId" lawyer placeholder="请选择处理人" @change="onHandlerChange" />
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
      // V3：ProTable 列定义（服务类型/金额/支付状态/服务状态/时间/操作走插槽）
      columns: [
        { prop: 'serviceNo', label: '服务编号', minWidth: 140, align: 'center', showOverflowTooltip: true },
        { prop: 'customerName', label: '客户姓名', minWidth: 110, align: 'center' },
        { prop: 'customerPhone', label: '客户电话', minWidth: 130, align: 'center' },
        { prop: 'serviceType', label: '服务类型', width: 100, align: 'center', slot: true },
        { prop: 'amount', label: '服务金额', width: 120, align: 'center', slot: true },
        { prop: 'paymentStatus', label: '支付状态', width: 90, align: 'center', slot: true },
        { prop: 'serviceStatus', label: '服务状态', width: 90, align: 'center', slot: true },
        { prop: 'handlerName', label: '处理人', minWidth: 100, align: 'center' },
        { prop: 'createTime', label: '创建时间', width: 180, align: 'center', slot: true },
        { prop: 'action', label: '操作', minWidth: 170, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    queryFormItems() {
      return [
        { prop: 'serviceNo', label: '服务编号', placeholder: '请输入服务编号', width: 200 },
        { prop: 'customerName', label: '客户姓名', placeholder: '请输入客户姓名', width: 200 },
        { prop: 'customerPhone', label: '客户电话', placeholder: '请输入客户电话', width: 200 },
        {
          prop: 'serviceType',
          label: '服务类型',
          type: 'select',
          placeholder: '请选择服务类型',
          width: 160,
          options: [
            { label: '代书', value: '代书' },
            { label: '调解', value: '调解' },
            { label: '诉讼代理', value: '诉讼代理' },
            { label: '法律顾问', value: '法律顾问' }
          ]
        },
        {
          prop: 'paymentStatus',
          label: '支付状态',
          type: 'select',
          placeholder: '请选择支付状态',
          width: 140,
          options: [
            { label: '未付', value: '0' },
            { label: '已付', value: '1' },
            { label: '部分付', value: '2' }
          ]
        },
        {
          prop: 'serviceStatus',
          label: '服务状态',
          type: 'select',
          placeholder: '请选择服务状态',
          width: 140,
          options: [
            { label: '待受理', value: '0' },
            { label: '处理中', value: '1' },
            { label: '已完成', value: '2' },
            { label: '已关闭', value: '3' }
          ]
        },
        { prop: 'dateRange', label: '创建时间', slot: true }
      ]
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
    onHandlerChange(val, user) {
      this.$set(this.form, 'handlerName', user ? (user.nickName || user.userName) : '')
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
        handlerId: undefined,
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值（含独立日期区间）并重查
      this.dateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        serviceNo: undefined,
        customerName: undefined,
        customerPhone: undefined,
        serviceType: undefined,
        paymentStatus: undefined,
        serviceStatus: undefined
      }
      this.getList()
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
      const count = row.paidId ? 1 : (this.ids || []).length
      this.$modal.dangerConfirm({
        content: '将删除选中的有偿法律服务记录',
        count: count,
        countNoun: '条记录',
        tip: '删除可能影响服务订单与计费留痕，请确认'
      }).then(function() {
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
