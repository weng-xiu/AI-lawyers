<template>
  <div class="app-container">
    <!-- V3/U：查询区/工具条/表格/分页四段式骨架收敛到 ProTable -->
    <ProTable
      :columns="columns"
      :query-form-items="queryFormItems"
      :query-params="queryParams"
      :data="messageList"
      :loading="loading"
      :total="total"
      :show-search.sync="showSearch"
      :stripe="false"
      has-selection
      label-width="72px"
      empty-text="暂无站内消息"
      empty-hint="系统通知、待办提醒与风险预警将在此汇总"
      @query="handleQuery"
      @reset="resetQuery"
      @pagination="getList"
      @selection-change="handleSelectionChange">
      <template #toolbar-left>
        <el-button type="success" plain icon="el-icon-check" size="mini" @click="handleReadAll"
          v-hasPermi="['lawyers:message:read']">全部已读</el-button>
        <el-button type="danger" plain icon="el-icon-delete" size="mini" :disabled="multiple" @click="handleDelete"
          v-hasPermi="['lawyers:message:remove']">删除</el-button>
      </template>
      <!-- 发送时间沿用独立 dateRange + addDateRange，走自定义查询插槽 -->
      <template #query-dateRange>
        <el-date-picker v-model="dateRange" style="width: 240px" value-format="yyyy-MM-dd" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </template>
      <template #column-msgType="{ row }">
        <el-tag size="mini" :type="msgTagType(row.msgType)">{{ msgTypeText(row.msgType) }}</el-tag>
      </template>
      <template #column-title="{ row }">
        <el-link :underline="false" @click="handleDetail(row)"
          :style="{ fontWeight: row.isRead === '0' ? 'bold' : 'normal', color: row.isRead === '0' ? '#303133' : '#909399' }">
          <i v-if="row.isRead === '0'" class="el-icon-chat-dot-round" style="color:#F56C6C; margin-right:4px"></i>{{ row.title }}
        </el-link>
      </template>
      <template #column-priority="{ row }">
        <el-tag v-if="row.priority === '1'" size="mini" type="danger">紧急</el-tag>
        <el-tag v-else-if="row.priority === '2'" size="mini" type="warning">普通</el-tag>
        <el-tag v-else size="mini" type="info">低</el-tag>
      </template>
      <template #column-isRead="{ row }">
        <el-tag size="mini" :type="row.isRead === '0' ? 'danger' : 'info'">{{ row.isRead === '0' ? '未读' : '已读' }}</el-tag>
      </template>
      <template #column-action="{ row }">
        <el-button size="mini" type="text" icon="el-icon-view" @click="handleDetail(row)"
          v-hasPermi="['lawyers:message:query']">查看</el-button>
        <el-button v-if="row.isRead === '0'" size="mini" type="text" icon="el-icon-check" @click="handleRead(row)"
          v-hasPermi="['lawyers:message:read']">已读</el-button>
        <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(row)"
          v-hasPermi="['lawyers:message:remove']">删除</el-button>
      </template>
    </ProTable>

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
      // V3：ProTable 列定义（类型/标题/优先级/状态/操作走插槽）
      columns: [
        { prop: 'msgType', label: '类型', width: 90, align: 'center', slot: true },
        { prop: 'title', label: '标题', minWidth: 200, align: 'left', slot: true, showOverflowTooltip: true },
        { prop: 'priority', label: '优先级', width: 90, align: 'center', slot: true },
        { prop: 'sender', label: '发送方', width: 110, align: 'center' },
        { prop: 'isRead', label: '状态', width: 80, align: 'center', slot: true },
        { prop: 'createTime', label: '发送时间', width: 160, align: 'center' },
        { prop: 'action', label: '操作', width: 150, align: 'center', slot: true, className: 'small-padding fixed-width' }
      ],
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
  computed: {
    queryFormItems() {
      return [
        { prop: 'msgType', label: '消息类型', type: 'select', placeholder: '全部类型', width: 150, options: this.typeOptions },
        {
          prop: 'isRead',
          label: '阅读状态',
          type: 'select',
          placeholder: '全部状态',
          width: 140,
          options: [
            { label: '未读', value: '0' },
            { label: '已读', value: '1' }
          ]
        },
        { prop: 'title', label: '标题', placeholder: '请输入标题关键字', width: 200 },
        { prop: 'dateRange', label: '发送时间', slot: true }
      ];
    }
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
      // ProTable 已复位查询表单，页面只需恢复筛选初值（含独立日期区间）并重查
      this.dateRange = [];
      this.queryParams = {
        pageNum: 1,
        pageSize: 10,
        msgType: undefined,
        isRead: undefined,
        title: undefined
      };
      this.getList();
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
      const count = row.messageId ? 1 : (this.ids || []).length;
      this.$modal.dangerConfirm({
        content: '将删除选中的站内消息',
        count: count,
        countNoun: '条消息'
      }).then(function() {
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
