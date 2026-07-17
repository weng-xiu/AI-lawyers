<template>
  <div class="app-container work-order-page">
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" label-width="80px">
        <el-form-item label="工单编号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入工单编号"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="咨询类型" prop="consultType">
          <el-select v-model="queryParams.consultType" placeholder="请选择咨询类型" clearable style="width: 160px">
            <el-option label="民商事" value="civil" />
            <el-option label="劳动纠纷" value="labor" />
            <el-option label="婚姻家庭" value="marriage" />
            <el-option label="刑事辩护" value="criminal" />
            <el-option label="行政纠纷" value="administrative" />
            <el-option label="知识产权" value="ip" />
            <el-option label="房产纠纷" value="property" />
            <el-option label="合同纠纷" value="contract" />
          </el-select>
        </el-form-item>
        <el-form-item label="咨询内容" prop="content">
          <el-input
            v-model="queryParams.content"
            placeholder="请输入咨询内容"
            clearable
            style="width: 200px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="办理人" prop="handler">
          <el-input
            v-model="queryParams.handler"
            placeholder="请输入办理人"
            clearable
            style="width: 160px"
            @keyup.enter.native="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="待处理" value="pending" />
            <el-option label="处理中" value="processing" />
            <el-option label="已完成" value="completed" />
            <el-option label="已回访" value="visited" />
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
        <div class="stat-card stat-pending" @click="filterByStatus('pending')">
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
        <div class="stat-card stat-processing" @click="filterByStatus('processing')">
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
        <div class="stat-card stat-completed" @click="filterByStatus('completed')">
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
        <div class="stat-card stat-visited" @click="filterByStatus('visited')">
          <div class="stat-icon">
            <i class="el-icon-phone"></i>
          </div>
          <div class="stat-info">
            <div class="stat-num">{{ statData.visited }}</div>
            <div class="stat-label">已回访工单</div>
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
        <el-table-column label="工单编号" align="center" prop="orderNo" width="160">
          <template slot-scope="scope">
            <span class="order-link" @click="handleView(scope.row)">{{ scope.row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="来电号码" align="center" prop="phone" width="130" />
        <el-table-column label="咨询类型" align="center" prop="consultType" width="110">
          <template slot-scope="scope">
            <el-tag :type="getConsultTypeTag(scope.row.consultType)" size="mini">{{ getConsultTypeLabel(scope.row.consultType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="咨询内容摘要" align="left" prop="contentSummary" min-width="200" show-overflow-tooltip />
        <el-table-column label="优先级" align="center" prop="priority" width="80">
          <template slot-scope="scope">
            <el-tag :type="getPriorityTag(scope.row.priority)" size="mini">{{ getPriorityLabel(scope.row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前处理人" align="center" prop="handler" width="100" />
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
            <el-button type="text" size="mini" icon="el-icon-delete" style="color: #ef4444" @click="handleDelete(scope.row)">删除</el-button>
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
          <span class="order-no">{{ currentOrder.orderNo }}</span>
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
                <el-descriptions-item label="工单编号">{{ currentOrder.orderNo }}</el-descriptions-item>
                <el-descriptions-item label="咨询类型">
                  <el-tag :type="getConsultTypeTag(currentOrder.consultType)" size="mini">{{ getConsultTypeLabel(currentOrder.consultType) }}</el-tag>
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
                <el-descriptions-item label="电话">{{ currentOrder.phone }}</el-descriptions-item>
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
  </div>
</template>

<script>
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
        pending: 12,
        processing: 28,
        completed: 156,
        visited: 134
      },
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        orderNo: undefined,
        consultType: undefined,
        content: undefined,
        handler: undefined,
        status: undefined
      },
      orderList: [],
      timelineList: [
        {
          title: '创建工单',
          time: '2025-03-14 14:32',
          user: '张律师',
          description: '创建工单',
          color: '#10b981',
          icon: 'el-icon-document'
        },
        {
          title: '分派工单',
          time: '2025-03-14 14:35',
          user: '班长',
          description: '分派给李律师处理',
          color: '#3b82f6',
          icon: 'el-icon-s-promotion'
        },
        {
          title: '开始处理',
          time: '2025-03-14 14:40',
          user: '李律师',
          description: '开始处理，预计2小时内完成',
          color: '#f59e0b',
          icon: 'el-icon-loading'
        },
        {
          title: '处理完成',
          time: '2025-03-14 16:20',
          user: '李律师',
          description: '已完成处理，解答用户关于离婚财产分割的问题',
          color: '#10b981',
          icon: 'el-icon-circle-check'
        },
        {
          title: '回访记录',
          time: '2025-03-15 09:30',
          user: '王助理',
          description: '电话回访，用户表示非常满意',
          color: '#8b5cf6',
          icon: 'el-icon-phone'
        }
      ]
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      setTimeout(() => {
        const allData = this.generateMockData()
        let filteredData = [...allData]

        if (this.queryParams.orderNo) {
          filteredData = filteredData.filter(item => item.orderNo.includes(this.queryParams.orderNo))
        }
        if (this.queryParams.consultType) {
          filteredData = filteredData.filter(item => item.consultType === this.queryParams.consultType)
        }
        if (this.queryParams.content) {
          filteredData = filteredData.filter(item => item.contentSummary.includes(this.queryParams.content))
        }
        if (this.queryParams.handler) {
          filteredData = filteredData.filter(item => item.handler.includes(this.queryParams.handler))
        }
        if (this.queryParams.status) {
          filteredData = filteredData.filter(item => item.status === this.queryParams.status)
        }

        const start = (this.queryParams.pageNum - 1) * this.queryParams.pageSize
        const end = start + this.queryParams.pageSize
        this.orderList = filteredData.slice(start, end)
        this.total = filteredData.length
        this.loading = false
      }, 300)
    },
    generateMockData() {
      const consultTypes = ['civil', 'labor', 'marriage', 'criminal', 'administrative', 'ip', 'property', 'contract']
      const priorities = ['high', 'medium', 'low']
      const statuses = ['pending', 'processing', 'completed', 'visited']
      const handlers = ['李律师', '张律师', '王律师', '刘律师', '陈律师']
      const names = ['张三', '李四', '王五', '赵六', '钱七', '孙八', '周九', '吴十']
      const locations = ['广州市天河区', '深圳市南山区', '珠海市香洲区', '佛山市禅城区', '东莞市莞城区']
      const occupations = ['企业员工', '自由职业', '公务员', '教师', '医生', '学生', '退休人员']
      const sources = ['12348热线', '微信公众号', '网站咨询', 'APP咨询', '现场咨询']
      const contents = [
        '咨询离婚财产分割问题，夫妻共同房产如何分配',
        '公司拖欠工资三个月，如何维权',
        '交通事故赔偿标准及处理流程咨询',
        '租房合同纠纷，房东不退押金',
        '遗产继承顺序及比例问题',
        '工伤认定及赔偿标准咨询',
        '民间借贷纠纷，借款人不还款怎么办',
        '消费者权益保护，商品质量问题如何索赔',
        '劳动合同解除补偿问题咨询',
        '房产买卖合同纠纷，卖方违约如何处理'
      ]

      const data = []
      for (let i = 1; i <= 56; i++) {
        const status = statuses[i % 4]
        data.push({
          id: i,
          orderNo: `GD12348${String(202503000 + i).padStart(9, '0')}`,
          phone: `138${String(Math.floor(Math.random() * 100000000)).padStart(8, '0')}`,
          consultType: consultTypes[i % consultTypes.length],
          contentSummary: contents[i % contents.length],
          content: contents[i % contents.length] + '。用户详细描述了自己的情况，希望得到专业的法律建议和指导。',
          priority: priorities[i % 3],
          handler: handlers[i % handlers.length],
          status: status,
          createTime: `2025-03-${String(10 + (i % 20)).padStart(2, '0')} ${String(9 + (i % 10)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}:${String(Math.floor(Math.random() * 60)).padStart(2, '0')}`,
          callerName: names[i % names.length],
          location: locations[i % locations.length],
          occupation: occupations[i % occupations.length],
          source: sources[i % sources.length],
          urgent: i % 7 === 0,
          remark: i % 5 === 0 ? '用户情绪较为激动，需耐心解答' : ''
        })
      }
      return data
    },
    getConsultTypeLabel(type) {
      const labels = {
        civil: '民商事',
        labor: '劳动纠纷',
        marriage: '婚姻家庭',
        criminal: '刑事辩护',
        administrative: '行政纠纷',
        ip: '知识产权',
        property: '房产纠纷',
        contract: '合同纠纷'
      }
      return labels[type] || type
    },
    getConsultTypeTag(type) {
      const tags = {
        civil: '',
        labor: 'warning',
        marriage: 'danger',
        criminal: 'info',
        administrative: 'success',
        ip: '',
        property: 'warning',
        contract: 'danger'
      }
      return tags[type] || 'info'
    },
    getPriorityLabel(priority) {
      const labels = { high: '高', medium: '中', low: '低' }
      return labels[priority] || priority
    },
    getPriorityTag(priority) {
      const tags = { high: 'danger', medium: 'warning', low: 'info' }
      return tags[priority] || 'info'
    },
    getStatusLabel(status) {
      const labels = {
        pending: '待处理',
        processing: '处理中',
        completed: '已完成',
        visited: '已回访'
      }
      return labels[status] || status
    },
    getStatusTag(status) {
      const tags = {
        pending: 'warning',
        processing: '',
        completed: 'success',
        visited: 'info'
      }
      return tags[status] || 'info'
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
        orderNo: undefined,
        consultType: undefined,
        content: undefined,
        handler: undefined,
        status: undefined
      }
      this.handleQuery()
    },
    filterByStatus(status) {
      this.queryParams.status = status
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.selectedIds = selection.map(item => item.id)
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
      this.$message.success(`批量处理 ${this.selectedIds.length} 条工单`)
    },
    handleExport() {
      this.$message.success(`导出 ${this.selectedIds.length} 条工单`)
    },
    handleAdd() {
      this.$message.info('新增工单功能')
    },
    handleView(row) {
      this.currentOrder = row
      this.activeTab = 'basic'
      this.drawerVisible = true
    },
    handleEdit(row) {
      this.$message.info(`编辑工单：${row.orderNo}`)
    },
    handleDelete(row) {
      this.$confirm('是否确认删除工单编号为"' + row.orderNo + '"的数据项？', '警告', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$message.success('删除成功')
        this.getList()
      }).catch(() => {})
    },
    handleTransfer() {
      this.$message.info('转派工单功能')
    },
    handleReturn() {
      this.$message.info('退回工单功能')
    },
    handleArchive() {
      this.$message.success('工单已归档')
      this.drawerVisible = false
      this.getList()
    },
    handleVisit() {
      this.$message.info('回访功能')
    }
  }
}
</script>

<style lang="scss" scoped>
.work-order-page {
  min-height: calc(100vh - 84px);
  background: #f1f5f9;
  padding: 16px;

  .search-card {
    background: #fff;
    border-radius: 8px;
    margin-bottom: 16px;
    border: none;

    ::v-deep .el-card__body {
      padding: 16px 20px;
    }

    .search-btns {
      margin-left: auto;

      .el-button + .el-button {
        margin-left: 8px;
      }
    }
  }

  .stat-cards {
    margin-bottom: 16px;

    .stat-card {
      background: #fff;
      border-radius: 8px;
      padding: 20px;
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
        margin-right: 16px;
      }

      .stat-info {
        flex: 1;

        .stat-num {
          font-size: 28px;
          font-weight: 700;
          line-height: 1.2;
          color: #1e293b;
        }

        .stat-label {
          font-size: 14px;
          color: #64748b;
          margin-top: 4px;
        }
      }

      &.stat-pending {
        .stat-icon {
          background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
        }
      }

      &.stat-processing {
        .stat-icon {
          background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
        }
      }

      &.stat-completed {
        .stat-icon {
          background: linear-gradient(135deg, #10b981 0%, #059669 100%);
        }
      }

      &.stat-visited {
        .stat-icon {
          background: linear-gradient(135deg, #8b5cf6 0%, #7c3aed 100%);
        }
      }
    }
  }

  .table-card {
    background: #fff;
    border-radius: 8px;
    border: none;

    ::v-deep .el-card__body {
      padding: 16px 20px 20px;
    }

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      .toolbar-left {
        display: flex;
        align-items: center;
        gap: 8px;
      }
    }

    .order-link {
      color: #3b82f6;
      cursor: pointer;

      &:hover {
        color: #1d4ed8;
        text-decoration: underline;
      }
    }
  }

  ::v-deep .el-drawer {
    background: #f1f5f9;

    .el-drawer__body {
      padding: 0;
      display: flex;
      flex-direction: column;
      height: 100%;
    }
  }

  .drawer-header {
    background: #fff;
    padding: 16px 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #e2e8f0;

    .drawer-title {
      display: flex;
      align-items: center;
      gap: 12px;

      .order-no {
        font-size: 18px;
        font-weight: 700;
        color: #1e293b;
      }
    }

    .close-btn {
      font-size: 20px;
      color: #64748b;
      padding: 4px;

      &:hover {
        color: #1e293b;
      }
    }
  }

  .drawer-tabs {
    flex: 1;
    background: #fff;
    margin-top: 12px;

    ::v-deep .el-tabs__header {
      margin: 0;
      padding: 0 20px;
      border-bottom: 1px solid #e2e8f0;
    }

    ::v-deep .el-tabs__item {
      height: 48px;
      line-height: 48px;
      font-size: 14px;
    }

    ::v-deep .el-tabs__content {
      padding: 20px;
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
        color: #1e293b;
        margin-bottom: 12px;
        padding-left: 8px;
        border-left: 3px solid #3b82f6;
      }
    }
  }

  .timeline-container {
    padding: 10px 0;

    ::v-deep .el-timeline-item__wrapper {
      padding-left: 24px;
    }

    ::v-deep .el-timeline-item__timestamp {
      color: #64748b;
      font-size: 12px;
    }

    .timeline-content {
      background: #f8fafc;
      border-radius: 8px;
      padding: 12px 16px;

      .timeline-title {
        font-size: 14px;
        font-weight: 600;
        color: #1e293b;
        margin-bottom: 4px;
      }

      .timeline-user {
        font-size: 12px;
        color: #3b82f6;
        margin-bottom: 6px;
      }

      .timeline-desc {
        font-size: 13px;
        color: #64748b;
        line-height: 1.6;
      }
    }
  }

  .drawer-footer {
    background: #fff;
    padding: 12px 20px;
    border-top: 1px solid #e2e8f0;
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
}
</style>
