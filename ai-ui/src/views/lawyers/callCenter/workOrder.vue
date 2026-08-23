<template>
  <div class="app-container work-order-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="工单编号" prop="ticketNo">
          <el-input
            v-model="queryParams.ticketNo"
            placeholder="请输入工单编号"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="咨询类型" prop="title">
          <el-input
            v-model="queryParams.title"
            placeholder="请输入咨询类型"
            clearable
            style="width: 160px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="办理人" prop="assignUserName">
          <el-input
            v-model="queryParams.assignUserName"
            placeholder="请输入办理人"
            clearable
            style="width: 160px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="0" />
            <el-option label="处理中" value="1" />
            <el-option label="已完成" value="2" />
            <el-option label="已归档" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
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
        <el-form-item class="search-btns">
          <el-button icon="el-icon-refresh-left" size="mini" @click="resetQuery">重置</el-button>
          <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card stat-pending" @click="filterByStatus('0')">
          <div class="stat-icon">
            <i class="el-icon-time"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.pending }}</div>
            <div class="stat-label">待处理工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-processing" @click="filterByStatus('1')">
          <div class="stat-icon">
            <i class="el-icon-loading"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.processing }}</div>
            <div class="stat-label">处理中工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-completed" @click="filterByStatus('2')">
          <div class="stat-icon">
            <i class="el-icon-circle-check"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.completed }}</div>
            <div class="stat-label">已完成工单</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card stat-visited" @click="filterByStatus('3')">
          <div class="stat-icon">
            <i class="el-icon-folder"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.visited }}</div>
            <div class="stat-label">已归档工单</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card class="table-card" shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-checkbox v-model="isAllSelected" @change="handleSelectAll" style="margin-right: 16px">全选</el-checkbox>
          <el-button size="mini" icon="el-icon-setting" :disabled="multiple" @click="handleBatchProcess">批量处理</el-button>
          <el-button size="mini" icon="el-icon-download" :disabled="multiple" @click="handleExport">导出</el-button>
        </div>
        <div class="toolbar-right">
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="handleAdd">新增工单</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="orderList"
        @selection-change="handleSelectionChange"
        border
        style="width: 100%"
      >
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="工单编号" align="center" prop="ticketNo" width="160">
          <template slot-scope="scope">
            <span class="order-link" @click="handleView(scope.row)">{{ scope.row.ticketNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来电号码" align="center" prop="callerNumber" width="130" />
        <el-table-column label="咨询类型" align="center" prop="title" width="110">
          <template slot-scope="scope">
            <el-tag :type="getConsultTypeTag(scope.row.title)" size="mini">{{ scope.row.title }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="咨询内容摘要" align="left" prop="content" min-width="200" show-overflow-tooltip>
          <template slot-scope="scope">
            {{ scope.row.content ? scope.row.content.substring(0, 30) : '' }}
          </template>
        </el-table-column>
        <el-table-column label="优先级" align="center" prop="priority" width="80">
          <template slot-scope="scope">
            <el-tag :type="getPriorityTag(scope.row.priority)" size="mini">{{ getPriorityLabel(scope.row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前处理人" align="center" prop="assignUserName" width="100" />
        <el-table-column label="状态" align="center" prop="status" width="100">
          <template slot-scope="scope">
            <el-tag :type="getStatusTag(scope.row.status)" size="mini">{{ getStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
        <el-table-column label="操作" align="center" width="180" fixed="right">
          <template slot-scope="scope">
            <el-button type="text" size="mini" icon="el-icon-view" @click="handleView(scope.row)">查看</el-button>
            <el-button type="text" size="mini" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button type="text" size="mini" icon="el-icon-delete" style="color: #C63D4A" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; text-align: right"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="queryParams.pageNum"
        :page-sizes="[10, 20, 50, 100]"
        :page-size="queryParams.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
      />
    </el-card>

    <el-drawer
      title=""
      :visible.sync="drawerVisible"
      direction="rtl"
      size="60%"
      :with-header="false"
      modal-append-to-body
    >
      <div class="drawer-header">
        <div class="drawer-title">
          <span class="order-no">{{ currentOrder.ticketNo }}</span>
          <el-tag :type="getStatusTag(currentOrder.status)" size="small">{{ getStatusLabel(currentOrder.status) }}</el-tag>
        </div>
        <el-button type="text" icon="el-icon-close" class="close-btn" @click="drawerVisible = false"></el-button>
      </div>

      <el-tabs v-model="activeTab" class="drawer-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <div class="basic-info">
            <div class="info-section">
              <div class="section-title">工单基本信息</div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="工单编号">{{ currentOrder.ticketNo }}</el-descriptions-item>
                <el-descriptions-item label="咨询类型">
                  <el-tag :type="getConsultTypeTag(currentOrder.title)" size="mini">{{ currentOrder.title }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="优先级">
                  <el-tag :type="getPriorityTag(currentOrder.priority)" size="mini">{{ getPriorityLabel(currentOrder.priority) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="来源渠道">{{ currentOrder.source }}</el-descriptions-item>
                <el-descriptions-item label="是否加急">
                  <el-tag v-if="currentOrder.urgent" type="danger" size="mini">是</el-tag>
                  <el-tag v-else type="info" size="mini">否</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ currentOrder.createTime }}</el-descriptions-item>
                <el-descriptions-item label="咨询内容" :span="2">{{ currentOrder.content }}</el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="info-section">
              <div class="section-title">来电人信息</div>
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="姓名">{{ currentOrder.callerName }}</el-descriptions-item>
                <el-descriptions-item label="电话">{{ currentOrder.callerNumber }}</el-descriptions-item>
                <el-descriptions-item label="归属地">{{ currentOrder.location }}</el-descriptions-item>
                <el-descriptions-item label="职业">{{ currentOrder.occupation }}</el-descriptions-item>
                <el-descriptions-item label="备注" :span="2">{{ currentOrder.remark || '无' }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="流转记录" name="timeline">
          <div class="timeline-container">
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in timelineList"
                :key="index"
                :timestamp="item.time"
                :color="item.color"
                :icon="item.icon"
              >
                <div class="timeline-content">
                  <div class="timeline-title">{{ item.title }}</div>
                  <div class="timeline-user">{{ item.user }}</div>
                  <div class="timeline-desc">{{ item.description }}</div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </el-tab-pane>
      </el-tabs>

      <div class="drawer-footer">
        <el-button size="small" icon="el-icon-s-operation" @click="handleTransfer">转派工单</el-button>
        <el-button size="small" icon="el-icon-refresh-left" @click="handleReturn">退回</el-button>
        <el-button size="small" icon="el-icon-folder" @click="handleArchive">归档</el-button>
        <el-button type="primary" size="small" icon="el-icon-phone" @click="handleVisit">回访</el-button>
        <el-button size="small" icon="el-icon-close" @click="drawerVisible = false">关闭</el-button>
      </div>
    </el-drawer>

    <el-dialog :title="isEdit ? '编辑工单' : '新增工单'" :visible.sync="formOpen" width="640px" append-to-body>
      <el-form ref="ticketForm" :model="form" :rules="rules" label-width="100px" size="small">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工单编号" prop="ticketNo">
              <el-input v-model="form.ticketNo" placeholder="系统自动生成" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority">
              <el-radio-group v-model="form.priority">
                <el-radio :label="1">紧急</el-radio>
                <el-radio :label="2">普通</el-radio>
                <el-radio :label="3">低</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入工单标题" maxlength="100" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="来电号码" prop="callerNumber">
              <el-input v-model="form.callerNumber" placeholder="请输入来电号码" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="来电人" prop="callerName">
              <el-input v-model="form.callerName" placeholder="请输入来电人" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="工单内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入工单内容" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="处理人姓名" prop="assignUserName">
          <el-input v-model="form.assignUserName" placeholder="请输入处理人姓名" maxlength="50" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" maxlength="500" />
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="formOpen = false">取 消</el-button>
        <el-button type="primary" size="small" @click="submitForm">确 定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listTicket, getTicket, addTicket, updateTicket, delTicket, processTicket, completeTicket, archiveTicket, generateTicketNo } from "@/api/lawyers/callCenter"
import request from '@/utils/request'

// 查询工单流转记录（若后端接口存在）
export function getTicketTimeline(ticketId) {
  return request({
    url: '/lawyers/call/ticket/' + ticketId + '/timeline',
    method: 'get'
  })
}

export default {
  name: "WorkOrder",
  data() {
    return {
      loading: false,
      total: 0,
      dateRange: [],
      isAllSelected: false,
      selectedIds: [],
      multiple: true,
      drawerVisible: false,
      activeTab: 'basic',
      currentOrder: {},
      statData: {
        pending: 0,
        processing: 0,
        completed: 0,
        visited: 0
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        title: undefined,
        assignUserName: undefined,
        status: undefined
      },
      orderList: [],
      timelineList: [],
      formOpen: false,
      isEdit: false,
      form: {},
      rules: {
        title: [{ required: true, message: '工单标题不能为空', trigger: 'blur' }],
        content: [{ required: true, message: '工单内容不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
    this.calcStatData()
  },
  methods: {
    getList() {
      this.loading = true
      listTicket(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.orderList = response.rows
        this.total = response.total
        this.loading = false
      }).catch(() => { this.loading = false })
    },
    calcStatData() {
      const params = { pageNum: 1, pageSize: 1 }
      listTicket({...params, status: '0'}).then(r => this.statData.pending = r.total).catch(() => {})
      listTicket({...params, status: '1'}).then(r => this.statData.processing = r.total).catch(() => {})
      listTicket({...params, status: '2'}).then(r => this.statData.completed = r.total).catch(() => {})
      listTicket({...params, status: '3'}).then(r => this.statData.visited = r.total).catch(() => {})
    },
    getConsultTypeTag(type) {
      return 'info'
    },
    getPriorityLabel(priority) {
      const labels = { 1: '高', 2: '中', 3: '低' }
      return labels[priority] || priority
    },
    getPriorityTag(priority) {
      const tags = { 1: 'danger', 2: 'warning', 3: 'info' }
      return tags[priority] || 'info'
    },
    getStatusLabel(status) {
      const labels = {
        '0': '待处理',
        '1': '处理中',
        '2': '已完成',
        '3': '已归档'
      }
      return labels[String(status)] || status
    },
    getStatusTag(status) {
      const tags = {
        '0': 'warning',
        '1': '',
        '2': 'success',
        '3': 'info'
      }
      return tags[String(status)] || 'info'
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.dateRange = []
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        ticketNo: undefined,
        title: undefined,
        assignUserName: undefined,
        status: undefined
      }
      this.handleQuery()
    },
    filterByStatus(status) {
      this.queryParams.status = status
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.selectedIds = selection.map(item => item.ticketId)
      this.multiple = !selection.length
      this.isAllSelected = selection.length === this.orderList.length && this.orderList.length > 0
    },
    handleSelectAll(val) {
      this.$refs.table && this.$refs.table.toggleAllSelection()
    },
    handleSizeChange(val) {
      this.queryParams.pageSize = val
      this.getList()
    },
    handleCurrentChange(val) {
      this.queryParams.pageNum = val
      this.getList()
    },
    handleBatchProcess() {
      const ids = this.selectedIds
      if (!ids || ids.length === 0) {
        this.$message.warning('请先选择要处理的工单')
        return
      }
      this.$confirm('是否确认批量完成选中的 ' + ids.length + ' 条工单？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.loading = true
        Promise.all(ids.map(id => completeTicket({ ticketId: id }))).then(() => {
          this.$message.success('批量处理成功')
          this.getList()
          this.calcStatData()
        }).finally(() => { this.loading = false })
      }).catch(() => {})
    },
    handleExport() {
      const params = { ...this.queryParams }
      delete params.pageNum
      delete params.pageSize
      this.download('lawyers/call/ticket/export', { ...params }, `workOrder_${new Date().getTime()}.xlsx`)
    },
    resetForm() {
      this.form = {
        ticketId: undefined,
        ticketNo: undefined,
        title: undefined,
        callerNumber: undefined,
        callerName: undefined,
        priority: 2,
        content: undefined,
        assignUserName: undefined,
        remark: undefined
      }
      this.$nextTick(() => {
        this.$refs.ticketForm && this.$refs.ticketForm.clearValidate()
      })
    },
    handleAdd() {
      this.resetForm()
      this.isEdit = false
      generateTicketNo().then(response => {
        this.form.ticketNo = response.data
        this.formOpen = true
      }).catch(() => {})
    },
    handleView(row) {
      getTicket(row.ticketId).then(response => {
        this.currentOrder = response.data
        this.activeTab = 'basic'
        this.drawerVisible = true
        this.loadTimeline(row.ticketId)
      }).catch(() => {})
    },
    loadTimeline(ticketId) {
      this.timelineList = []
      getTicketTimeline(ticketId).then(res => {
        const data = res.data || res.rows || []
        if (Array.isArray(data) && data.length) {
          this.timelineList = data.map(item => ({
            time: item.operateTime || item.createTime || item.time,
            title: item.title || item.action || item.nodeName || '流转',
            user: item.operator || item.operateUserName || item.userName || item.user || '系统',
            description: item.content || item.remark || item.description || '',
            color: item.color || '#255A99',
            icon: item.icon || undefined
          }))
        } else {
          this.buildFallbackTimeline()
        }
      }).catch(() => {
        // 后端无该接口时，从工单数据自身组装时间线
        this.buildFallbackTimeline()
      })
    },
    buildFallbackTimeline() {
      const order = this.currentOrder || {}
      const list = []
      if (order.createTime) {
        list.push({
          time: order.createTime,
          title: '工单创建',
          user: order.createUserName || order.createBy || '系统',
          description: '工单已创建，等待分派处理',
          color: '#255A99',
          icon: 'el-icon-document-add'
        })
      }
      if (order.processTime || order.assignTime) {
        list.push({
          time: order.processTime || order.assignTime,
          title: '开始处理',
          user: order.assignUserName || order.processUserName || '处理人',
          description: order.processContent || '工单已分派给处理人',
          color: '#E6A23C',
          icon: 'el-icon-loading'
        })
      }
      if (order.completeTime) {
        list.push({
          time: order.completeTime,
          title: '处理完成',
          user: order.completeUserName || order.assignUserName || '处理人',
          description: order.completeContent || '工单处理已完成',
          color: '#2B8C6E',
          icon: 'el-icon-circle-check'
        })
      }
      if (order.archiveTime) {
        list.push({
          time: order.archiveTime,
          title: '工单归档',
          user: order.archiveUserName || '系统',
          description: order.archiveRemark || '工单已归档',
          color: '#7C3AED',
          icon: 'el-icon-folder'
        })
      }
      if (!list.length) {
        list.push({
          time: order.createTime || '',
          title: '工单信息',
          user: order.assignUserName || '-',
          description: order.content || '暂无流转记录',
          color: '#909399',
          icon: 'el-icon-info'
        })
      }
      this.timelineList = list
    },
    handleEdit(row) {
      this.resetForm()
      this.isEdit = true
      getTicket(row.ticketId).then(response => {
        this.form = Object.assign({}, this.form, response.data)
        if (this.form.priority === undefined || this.form.priority === null) {
          this.form.priority = 2
        }
        this.formOpen = true
      }).catch(() => {})
    },
    submitForm() {
      this.$refs.ticketForm.validate(valid => {
        if (!valid) return
        const save = this.isEdit ? updateTicket(this.form) : addTicket(this.form)
        save.then(() => {
          this.$message.success(this.isEdit ? '修改成功' : '新增成功')
          this.formOpen = false
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      })
    },
    handleDelete(row) {
      this.$confirm('是否确认删除工单编号为"' + row.ticketNo + '"的数据项？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        delTicket(row.ticketId).then(() => {
          this.$message.success('删除成功')
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleTransfer() {
      this.$prompt('请输入转派处理人姓名', '工单转派', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '处理人姓名不能为空'
      }).then(({ value }) => {
        const name = (value || '').trim()
        processTicket({
          ticketId: this.currentOrder.ticketId,
          processContent: '工单转派给：' + name,
          assignUserName: name,
          assignUserId: null
        }).then(() => {
          this.$message.success('工单已转派给：' + name)
          this.drawerVisible = false
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleReturn() {
      this.$confirm('是否确认将该工单退回待处理？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        updateTicket({
          ticketId: this.currentOrder.ticketId,
          status: '0',
          processContent: '工单退回待处理'
        }).then(() => {
          this.$message.success('工单已退回')
          this.drawerVisible = false
          this.getList()
          this.calcStatData()
        }).catch(() => {})
      }).catch(() => {})
    },
    handleArchive() {
      archiveTicket({ ticketId: this.currentOrder.ticketId }).then(() => {
        this.$message.success('工单已归档')
        this.drawerVisible = false
        this.getList()
        this.calcStatData()
      }).catch(() => {})
    },
    handleVisit() {
      this.$router.push({
        path: '/business/callback',
        query: {
          phone: this.currentOrder.callerNumber,
          ticketNo: this.currentOrder.ticketNo
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.work-order-page {
  min-height: calc(100vh - 84px);
  background: #F5F7FA;
  padding: 24px;

  .search-card {
    background: #fff;
    border-radius: 8px;
    margin-bottom: 20px;
    border: none;

    ::v-deep .el-card__body {
      padding: 20px 24px;
    }

    .search-btns {
      margin-left: auto;

      .el-button + .el-button {
        margin-left: 12px;
      }
    }
  }

  .stat-cards {
    margin-bottom: 20px;

    .stat-card {
      background: #fff;
      border-radius: 8px;
      padding: 24px;
      display: flex;
      align-items: center;
      cursor: pointer;
      transition: all 0.3s;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      .stat-icon {
        width: 56px;
        height: 56px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 28px;
        color: #fff;
        margin-right: 18px;
      }

      .stat-info {
        flex: 1;

        .stat-num {
          font-size: 28px;
          font-weight: 700;
          line-height: 1.2;
          color: #1F2A3A;
        }

        .stat-label {
          font-size: 14px;
          color: #5A6A7E;
          margin-top: 6px;
        }
      }

      &.stat-pending {
        .stat-icon {
          background: linear-gradient(135deg, #E8923A 0%, #d97706 100%);
        }
      }

      &.stat-processing {
        .stat-icon {
          background: linear-gradient(135deg, #255A99 0%, #1A3C6E 100%);
        }
      }

      &.stat-completed {
        .stat-icon {
          background: linear-gradient(135deg, #2B8C6E 0%, #2B8C6E 100%);
        }
      }

      &.stat-visited {
        .stat-icon {
          background: linear-gradient(135deg, #7C3AED 0%, #7c3aed 100%);
        }
      }
    }
  }

  .table-card {
    background: #fff;
    border-radius: 8px;
    border: none;

    ::v-deep .el-card__body {
      padding: 20px 24px 24px;
    }

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;

      .toolbar-left {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }

    .order-link {
      color: #255A99;
      cursor: pointer;

      &:hover {
        color: #1A3C6E;
        text-decoration: underline;
      }
    }
  }

  ::v-deep .el-drawer {
    background: #F5F7FA;

    .el-drawer__body {
      padding: 0;
      display: flex;
      flex-direction: column;
      height: 100%;
    }
  }

  .drawer-header {
    background: #fff;
    padding: 20px 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #DCE2EB;

    .drawer-title {
      display: flex;
      align-items: center;
      gap: 14px;

      .order-no {
        font-size: 18px;
        font-weight: 700;
        color: #1F2A3A;
      }
    }

    .close-btn {
      font-size: 20px;
      color: #5A6A7E;
      padding: 8px;

      &:hover {
        color: #1F2A3A;
      }
    }
  }

  .drawer-tabs {
    flex: 1;
    background: #fff;
    margin-top: 16px;

    ::v-deep .el-tabs__header {
      margin: 0;
      padding: 0 24px;
      border-bottom: 1px solid #DCE2EB;
    }

    ::v-deep .el-tabs__item {
      height: 48px;
      line-height: 48px;
      font-size: 14px;
    }

    ::v-deep .el-tabs__content {
      padding: 24px;
      overflow-y: auto;
      height: calc(100% - 49px);
    }
  }

  .basic-info {
    .info-section {
      margin-bottom: 24px;

      &:last-child {
        margin-bottom: 0;
      }

      .section-title {
        font-size: 15px;
        font-weight: 600;
        color: #1F2A3A;
        margin-bottom: 16px;
        padding-left: 10px;
        border-left: 3px solid #255A99;
      }
    }
  }

  .timeline-container {
    padding: 12px 0;

    ::v-deep .el-timeline-item__wrapper {
      padding-left: 24px;
    }

    ::v-deep .el-timeline-item__timestamp {
      color: #5A6A7E;
      font-size: 12px;
    }

    .timeline-content {
      background: #F5F7FA;
      border-radius: 8px;
      padding: 16px 20px;

      .timeline-title {
        font-size: 14px;
        font-weight: 600;
        color: #1F2A3A;
        margin-bottom: 6px;
      }

      .timeline-user {
        font-size: 12px;
        color: #255A99;
        margin-bottom: 8px;
      }

      .timeline-desc {
        font-size: 13px;
        color: #5A6A7E;
        line-height: 1.6;
      }
    }
  }

  .drawer-footer {
    background: #fff;
    padding: 16px 24px;
    border-top: 1px solid #DCE2EB;
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
}
</style>
