<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="72px">
      <el-form-item label="消息类型" prop="msgType">
        <el-select v-model="queryParams.msgType" placeholder="全部类型" clearable style="width: 150px">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="阅读状态" prop="isRead">
        <el-select v-model="queryParams.isRead" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="未读" value="0" />
          <el-option label="已读" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题" prop="title">
        <el-input v-model="queryParams.title" placeholder="请输入标题关键字" clearable style="width: 200px" @keyup.enter.native="handleQuery" />
      </el-form-item>
      <el-form-item label="发送时间">
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="success" plain icon="el-icon-check" size="mini" @click="handleReadAll" v-hasPermi="['lawyers:message:read']">全部已读</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete" v-hasPermi="['lawyers:message:remove']">删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="messageList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="类型" align="center" prop="msgType" width="90">
        <template slot-scope="scope">
          <el-tag size="mini" :type="msgTagType(scope.row.msgType)">{{ msgTypeText(scope.row.msgType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标题" align="left" prop="title" show-overflow-tooltip>
        <template slot-scope="scope">
          <el-link :underline="false" @click="handleDetail(scope.row)"
            :style="{ fontWeight: scope.row.isRead === '0' ? 'bold' : 'normal', color: scope.row.isRead === '0' ? '#303133' : '#909399' }">
            <i v-if="scope.row.isRead === '0'" class="el-icon-chat-dot-round" style="color:#F56C6C; margin-right:4px"></i>{{ scope.row.title }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="优先级" align="center" prop="priority" width="90">
        <template slot-scope="scope">
          <el-tag v-if="scope.row.priority === '1'" size="mini" type="danger">紧急</el-tag>
          <el-tag v-else-if="scope.row.priority === '2'" size="mini" type="warning">普通</el-tag>
          <el-tag v-else size="mini" type="info">低</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发送方" align="center" prop="sender" width="110" />
      <el-table-column label="状态" align="center" prop="isRead" width="80">
        <template slot-scope="scope">
          <el-tag size="mini" :type="scope.row.isRead === '0' ? 'danger' : 'info'">{{ scope.row.isRead === '0' ? '未读' : '已读' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发送时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(scope.row)" v-hasPermi="['lawyers:message:query']">查看</el-button>
          <el-button v-if="scope.row.isRead === '0'" size="mini" type="text" icon="el-icon-check" @click="handleRead(scope.row)" v-hasPermi="['lawyers:message:read']">已读</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)" v-hasPermi="['lawyers:message:remove']">删除</el-button>
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

    <!-- 消息详情对话框（打开即标记已读） -->
    <el-dialog :title="detailForm.title || '消息详情'" :visible.sync="detailOpen" width="600px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="消息类型">
          <el-tag size="mini" :type="msgTagType(detailForm.msgType)">{{ msgTypeText(detailForm.msgType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="阅读状态">{{ detailForm.isRead === '0' ? '未读' : '已读' }}</el-descriptions-item>
        <el-descriptions-item label="发送方">{{ detailForm.sender }}</el-descriptions-item>
        <el-descriptions-item label="发送时间">{{ detailForm.createTime }}</el-descriptions-item>
        <el-descriptions-item label="阅读时间">{{ detailForm.readTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联业务">{{ bizTypeText(detailForm.bizType) }}</el-descriptions-item>
        <el-descriptions-item label="消息内容" :span="2">
          <div style="white-space: pre-wrap; line-height: 1.7;">{{ detailForm.content }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button v-if="bizRoute(detailForm.bizType)" type="primary" @click="goBusiness(detailForm)">前往处理</el-button>
        <el-button @click="detailOpen = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listMessage, getMessage, readMessage, readAllMessage, delMessage } from "@/api/lawyers/message";

export default {
  name: "Message",
  data() {
    return {
      loading: true,
      ids: [],
      single: true,
      multiple: true,
      showSearch: true,
      total: 0,
      messageList: [],
      detailOpen: false,
      detailForm: {},
      dateRange: [],
      typeOptions: [
        { value: '1', label: '系统通知' },
        { value: '2', label: '待办提醒' },
        { value: '3', label: '风险预警' },
        { value: '4', label: '质检通知' },
        { value: '5', label: '工单消息' },
        { value: '9', label: '其他' }
      ],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        msgType: undefined,
        isRead: undefined,
        title: undefined
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    getList() {
      this.loading = true;
      listMessage(this.addDateRange(this.queryParams, this.dateRange)).then(response => {
        this.messageList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    resetQuery() {
      this.dateRange = [];
      this.resetForm("queryForm");
      this.handleQuery();
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.messageId);
      this.single = selection.length !== 1;
      this.multiple = !selection.length;
    },
    // 查看详情：后端返回详情的同时已标记已读
    handleDetail(row) {
      getMessage(row.messageId).then(response => {
        this.detailForm = response.data;
        this.detailOpen = true;
        if (row.isRead === '0') {
          this.getList();
        }
      });
    },
    handleRead(row) {
      readMessage(row.messageId).then(() => {
        this.$modal.msgSuccess("已标记为已读");
        this.getList();
      });
    },
    handleReadAll() {
      readAllMessage().then(response => {
        this.$modal.msgSuccess(response.msg || "已全部标记为已读");
        this.getList();
      });
    },
    handleDelete(row) {
      const messageIds = row.messageId || this.ids;
      this.$modal.confirm('是否确认删除选中的站内消息？').then(function() {
        return delMessage(messageIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    msgTypeText(type) {
      const item = this.typeOptions.find(t => t.value === type);
      return item ? item.label : '其他';
    },
    msgTagType(type) {
      const map = { '1': 'info', '2': 'primary', '3': 'danger', '4': 'warning', '5': 'success', '9': 'info' };
      return map[type] || 'info';
    },
    // 业务类型 → 页面路由（与菜单一致：质检顶级 /quality；工单/风险预警/外呼挂话务系统目录）
    bizRoute(bizType) {
      const map = {
        'quality': '/quality',
        'ticket': '/lawyers/callCenter/callTicket',
        'warning': '/lawyers/callCenter/riskWarning',
        'outbound': '/lawyers/callCenter/outbound/task'
      };
      return map[bizType] || '';
    },
    bizTypeText(bizType) {
      const map = { 'quality': '智能质检', 'ticket': '工单', 'warning': '风险预警', 'outbound': '外呼任务' };
      return map[bizType] || bizType || '-';
    },
    // 跳转业务页面处理
    goBusiness(row) {
      const route = this.bizRoute(row.bizType);
      this.detailOpen = false;
      if (route) {
        this.$router.push(route);
      }
    }
  }
};
</script>
